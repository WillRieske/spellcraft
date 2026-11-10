package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import io.github.willrieske.spellcraft.spell.rules.TeamMoveRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Team moves of a summoner's Bound Vexes (Parts 6-7 spec section 2): the free-vex test, the per-summoner record and
 * the start of a move; the tick, the triggers and the request arrive in Tasks 5-7.
 */
public final class TeamMoves {
    /** A vex farther than this from its summoner is not free (spec section 2.2). */
    public static final double FREE_RANGE = 32.0;

    private TeamMoves() {}

    /**
     * Whether {@code vex} may join a move of {@code owner} (spec section 2.2): alive, theirs, in their level, within
     * {@link #FREE_RANGE} blocks of them, following (not staying), in no move, in no job ({@link Jobs#busy}) and not
     * the player's rescuer.
     *
     * @param vex a Bound Vex
     * @param owner the summoner
     * @return true if it is free
     */
    public static boolean isFree(BoundVex vex, ServerPlayer owner) {
        // Part 7: and not a Caster (a Caster is free only for a Raid)
        return vex.isAlive()
                && vex.isSummonedBy(owner.getUUID())
                && vex.level().equals(owner.level())
                && !vex.isStaying()
                && vex.teamMove() == TeamMove.NONE
                && vex.distanceTo(owner) <= FREE_RANGE
                && !Jobs.busy(vex)
                && !VexRescues.rescuer(owner).map(vex::equals).orElse(false);
    }

    /**
     * The free vexes of {@code owner} in their level.
     *
     * @param owner the summoner
     * @return the free vexes, in query order
     */
    public static List<BoundVex> freeVexes(ServerPlayer owner) {
        return owner.level()
                .getEntitiesOfClass(
                        BoundVex.class, owner.getBoundingBox().inflate(FREE_RANGE), vex -> isFree(vex, owner));
    }

    /** Lang key prefix of the overlay message each start shows: {@code ...team_move_shield} and so on. */
    public static final String MESSAGE_PREFIX = "message.spellcraft.team_move_";

    /** The Raid's two phases (spec section 4.4). */
    enum Phase {
        /** The members fly to their cells. */
        GATHER,
        /** The members charge the enemy. */
        CHARGE
    }

    /** One roster member's own state: its cell, its kit and its clocks. */
    static final class Member {
        /** What a Shield member is doing. */
        enum Stage {
            /** Waiting for its next bash. */
            READY,
            /** Flying at the enemy. */
            LUNGING,
            /** Flying back after a bash. */
            BACKING
        }

        /** The member's place in the roster at the start: its Raid grid cell. */
        final int cell;

        /** The kit this member conjured, kept so a put-back gives the same items. */
        final Map<EquipmentSlot, ItemStack> kit;

        /** The game time of the member's next bash, shot or attack; 0 means ready. */
        long nextAction;

        /** The game time the member's current lunge began. */
        long lungeStart;

        /** The member's Shield stage. */
        Stage stage = Stage.READY;

        Member(int cell, Map<EquipmentSlot, ItemStack> kit) {
            this.cell = cell;
            this.kit = kit;
        }
    }

    /**
     * One summoner's state: the running move and the memory that outlives it (cooldowns, the scan clock). Named
     * {@code Move}, not {@code Record}, which would hide {@code java.lang.Record} inside {@code TeamMoves}.
     */
    static final class Move {
        /** The running move, {@link TeamMove#NONE} when none runs. */
        TeamMove move = TeamMove.NONE;

        /** The members' UUIDs in roster order (see {@code TeamMoves.chooseRoster}); a member's cell is its place. */
        final List<UUID> roster = new ArrayList<>();

        /** Each member's own state, by UUID. */
        final Map<UUID, Member> members = new HashMap<>();

        /** The UUID of the enemy (for an Evader, the ward) of the running move. */
        @Nullable
        UUID subject;

        /** The game time the running move started. */
        long start;

        /** The Raid's phase; {@link Phase#GATHER} for every other move. */
        Phase phase = Phase.GATHER;

        /** Per move kind, the game time its cooldown ends. */
        final EnumMap<TeamMove, Long> cooldownEnd = new EnumMap<>(TeamMove.class);

        /** The game time of the next automatic trigger scan (Task 6). */
        long nextScan;

        /** Hostiles the Evader has shoved, by UUID, to the game time until which each may not be shoved again. */
        final Map<UUID, Long> shoved = new HashMap<>();

        /** Returns whether a move runs. */
        boolean active() {
            return move != TeamMove.NONE;
        }

        /** Returns whether {@code kind} is still cooling down at game time {@code now}. */
        boolean cooling(TeamMove kind, long now) {
            return cooldownEnd.getOrDefault(kind, 0L) > now;
        }

        /** Starts recording {@code kind} against the subject {@code subjectId} at game time {@code now}. */
        void begin(TeamMove kind, UUID subjectId, long now) {
            move = kind;
            subject = subjectId;
            start = now;
            phase = Phase.GATHER;
        }

        /** Adds a member with its kit; its cell is its place in the roster so far. Returns the new member. */
        Member add(UUID vex, Map<EquipmentSlot, ItemStack> kit) {
            Member member = new Member(roster.size(), kit);
            roster.add(vex);
            members.put(vex, member);
            return member;
        }

        /** Ends the running move: starts its kind's cooldown until {@code cooldownUntil} and clears its state. */
        void finish(long cooldownUntil) {
            cooldownEnd.put(move, cooldownUntil);
            move = TeamMove.NONE;
            roster.clear();
            members.clear();
            subject = null;
            shoved.clear();
            phase = Phase.GATHER;
        }
    }

    /** Every summoner's record, by UUID. Transient, like {@code EvokingCaster}'s map. */
    private static final Map<UUID, Move> STATES = new HashMap<>();

    /**
     * Starts {@code move} for {@code owner} against {@code subject} (the enemy; for an Evader, the ward), if the
     * summoner is here (alive, not a spectator), the subject is alive in the summoner's level, no move runs, the kind
     * is not cooling and the free vexes fill a roster (spec sections 2.2, 2.4). Every kit is built before anything is
     * marked. Each member gets the mark, the target (not for an Evader) and its kit (given gear goes to the stash);
     * the summoner gets the overlay message. This is the one method the scan, the request and the GameTests start a
     * move through.
     *
     * @param owner the summoner
     * @param move the move
     * @param subject the enemy or ward
     * @return whether the move started
     */
    public static boolean start(ServerPlayer owner, TeamMove move, @Nullable LivingEntity subject) {
        if (move == TeamMove.NONE
                || subject == null
                || !subject.isAlive()
                || !subject.level().equals(owner.level())
                || !owner.isAlive()
                || owner.isSpectator()) {
            return false;
        }
        ServerLevel level = owner.level();
        long now = level.getGameTime();
        Move record = STATES.computeIfAbsent(owner.getUUID(), id -> new Move());
        if (record.active() || record.cooling(move, now)) {
            return false;
        }
        List<BoundVex> roster = chooseRoster(move, freeVexes(owner), subject);
        if (roster.isEmpty()) {
            return false;
        }
        // Build every kit first: a roll that throws (Dice.below rejects a scripted roll out of range) must leave
        // nothing half started. The roll order is the roster order, so Task 12's script lines up.
        List<Map<EquipmentSlot, ItemStack>> kits = roster.stream()
                .map(vex -> TeamMoveKits.kit(move, level, vex.getRandom()))
                .toList();
        record.begin(move, subject.getUUID(), now);
        for (int i = 0; i < roster.size(); i++) {
            BoundVex vex = roster.get(i);
            Member member = record.add(vex.getUUID(), kits.get(i));
            vex.setTeamMove(move);
            if (move != TeamMove.EVADER) {
                vex.setTarget(subject);
            }
            TeamMoveKits.conjure(vex, member.kit);
        }
        owner.sendOverlayMessage(Component.translatable(MESSAGE_PREFIX + move.id()));
        return true;
    }

    /**
     * The roster of a move (spec section 2.2): Shield and Evader take the free vexes nearest the enemy or ward,
     * Multishot the vex the enemy hunts (else the free vex nearest the enemy) and the two nearest that one, Raid every
     * free vex. The list is ordered: Shield, Evader and Raid nearest the enemy or ward first, Multishot the anchor
     * first and then nearest the anchor first. {@code Move.add} gives each member the cell of its place in this order.
     */
    private static List<BoundVex> chooseRoster(TeamMove move, List<BoundVex> free, LivingEntity subject) {
        int size = TeamMoveRules.rosterSize(move, free.size());
        if (size == 0) {
            return List.of();
        }
        if (move == TeamMove.MULTISHOT) {
            BoundVex anchor =
                    subject instanceof Mob mob && mob.getTarget() instanceof BoundVex hunted && free.contains(hunted)
                            ? hunted
                            : nearest(free, subject.position());
            List<BoundVex> roster = new ArrayList<>(List.of(anchor));
            free.stream()
                    .filter(vex -> !vex.equals(anchor))
                    .sorted(Comparator.comparingDouble(vex -> vex.distanceToSqr(anchor)))
                    .limit(size - 1L)
                    .forEach(roster::add);
            return roster;
        }
        return free.stream()
                .sorted(Comparator.comparingDouble(vex -> vex.distanceToSqr(subject)))
                .limit(size)
                .toList();
    }

    /**
     * The vex nearest to {@code point}; package-private, {@code EvaderMove} uses it too.
     *
     * @param vexes the candidates, not empty
     * @param point the point
     * @return the nearest vex
     */
    static BoundVex nearest(List<BoundVex> vexes, Vec3 point) {
        return vexes.stream()
                .min(Comparator.comparingDouble(vex -> vex.distanceToSqr(point)))
                .orElseThrow();
    }

    /**
     * The move running for {@code owner}.
     *
     * @param owner the summoner
     * @return the move, or empty when none runs
     */
    public static Optional<TeamMove> running(ServerPlayer owner) {
        Move record = STATES.get(owner.getUUID());
        return record != null && record.active() ? Optional.of(record.move) : Optional.empty();
    }

    /**
     * The living roster members in the summoner's level, in roster order.
     *
     * @param owner the summoner
     * @return the vexes; empty when no move runs
     */
    public static List<BoundVex> roster(ServerPlayer owner) {
        Move record = STATES.get(owner.getUUID());
        return record == null ? List.of() : members(owner.level(), record);
    }

    /** The roster of {@code record} resolved to the entities alive in {@code level}, in roster order. */
    static List<BoundVex> members(ServerLevel level, Move record) {
        List<BoundVex> vexes = new ArrayList<>();
        for (UUID id : record.roster) {
            if (level.getEntity(id) instanceof BoundVex vex && vex.isAlive()) {
                vexes.add(vex);
            }
        }
        return vexes;
    }

    /**
     * The UUID of the enemy or ward of the running move.
     *
     * @param owner the summoner
     * @return the UUID, or empty when none runs
     */
    public static Optional<UUID> subject(ServerPlayer owner) {
        Move record = STATES.get(owner.getUUID());
        return record != null && record.active() ? Optional.ofNullable(record.subject) : Optional.empty();
    }
}
