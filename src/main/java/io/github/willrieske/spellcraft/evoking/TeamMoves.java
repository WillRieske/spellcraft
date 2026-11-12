package io.github.willrieske.spellcraft.evoking;

import com.mojang.logging.LogUtils;
import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.config.TeamMoveMode;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Team moves of a summoner's Bound Vexes (Parts 6-7 spec §2): the free-vex test, the per-summoner record, the
 * start and tick of a move (with the automatic trigger scan on the tick), and the manual request with its answers.
 * The Shield, Multishot, Evader and Raid parts run through {@code TeamMoveGoal} and one class per move. {@code Member}
 * carries the per-move fields of all of them (the Shield's stage and lunge clock, the Raid's cell and role), so a new
 * move adds its own there rather than a second state class.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class TeamMoves {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** A vex farther than this from its summoner is not free (spec §2.2). */
    public static final double FREE_RANGE = 32.0;

    /** The scan for triggers runs once every this many ticks (spec §2.3). */
    public static final int SCAN_INTERVAL = 10;

    /** How far from the summoner an ally may be and still be warded (spec §2.3). */
    private static final double WARD_RANGE = 16.0;

    private TeamMoves() {}

    /**
     * Whether {@code vex} may join a move of {@code owner} (spec §2.2): alive, theirs, in their level, within
     * {@link #FREE_RANGE} blocks of them, following (not staying), in no move, in no job ({@link Jobs#busy}) and not
     * the player's rescuer.
     *
     * @param vex a Bound Vex
     * @param owner the summoner
     * @return true if it is free
     */
    public static boolean isFree(BoundVex vex, ServerPlayer owner) {
        return isFree(vex, owner, false);
    }

    /**
     * {@link #isFree(BoundVex, ServerPlayer)}, with Casters counted when {@code forRaid}: a Caster is free only for a
     * Raid (Parts 6-7 spec §2.2, §6.2).
     *
     * @param vex a Bound Vex
     * @param owner the summoner
     * @param forRaid whether the move is a Raid
     * @return true if it is free
     */
    public static boolean isFree(BoundVex vex, ServerPlayer owner, boolean forRaid) {
        return vex.isAlive()
                && (forRaid || !vex.isCaster())
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
        return freeVexes(owner, false);
    }

    /**
     * The free vexes of {@code owner} in their level, Casters included when {@code forRaid}.
     *
     * @param owner the summoner
     * @param forRaid whether the move is a Raid
     * @return the free vexes, in query order
     */
    public static List<BoundVex> freeVexes(ServerPlayer owner, boolean forRaid) {
        return owner.level()
                .getEntitiesOfClass(
                        BoundVex.class, owner.getBoundingBox().inflate(FREE_RANGE), vex -> isFree(vex, owner, forRaid));
    }

    /**
     * The free vexes among {@code vexes}, a list the caller already fetched within {@link #FREE_RANGE} of
     * {@code owner}: the same test as {@link #freeVexes(ServerPlayer, boolean)}, without a second query.
     *
     * @param owner the summoner
     * @param vexes Bound Vexes found near the summoner
     * @param forRaid whether Casters count
     * @return those of them that are free, in the order given
     */
    static List<BoundVex> freeVexes(ServerPlayer owner, List<BoundVex> vexes, boolean forRaid) {
        return vexes.stream().filter(vex -> isFree(vex, owner, forRaid)).toList();
    }

    /** Lang key prefix of the overlay message each start shows: {@code ...team_move_shield} and so on. */
    public static final String MESSAGE_PREFIX = "message.spellcraft.team_move_";

    /** The Raid's two phases (spec §4.4). */
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

        /** Whether the kit's main-hand item is a crossbow: a Raid rocketeer, fixed at the start. */
        final boolean rocketeer;

        /** The game time of the member's next bash, shot or attack; 0 means ready. */
        long nextAction;

        /** The game time the member's current lunge began. */
        long lungeStart;

        /** The member's Shield stage. */
        Stage stage = Stage.READY;

        /** Whether a Caster member has already done its one Raid cast (or skipped it). */
        boolean casterDone;

        Member(int cell, Map<EquipmentSlot, ItemStack> kit) {
            this.cell = cell;
            this.kit = kit;
            this.rocketeer = kit.get(EquipmentSlot.MAINHAND) != null
                    && kit.get(EquipmentSlot.MAINHAND).getItem() instanceof CrossbowItem;
        }
    }

    /**
     * One summoner's state: the running move and the memory that outlives it (the cooldowns). Named
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
     * Per summoner, the game time of the next automatic trigger scan (spec §2.3). Kept apart from the record so a
     * present summoner who never starts a move gets no record.
     */
    private static final Map<UUID, Long> NEXT_SCAN = new HashMap<>();

    /**
     * Starts {@code move} for {@code owner} against {@code subject} (the enemy; for an Evader, the ward), if the
     * summoner is here (alive, not a spectator), the subject is alive in the summoner's level, no move runs, the kind
     * is not cooling and the free vexes fill a roster (spec §§2.2, 2.4). Every kit is built before anything is
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
        Move existing = STATES.get(owner.getUUID());
        if (existing != null && (existing.active() || existing.cooling(move, now))) {
            return false;
        }
        List<BoundVex> roster = chooseRoster(move, freeVexes(owner, move == TeamMove.RAID), subject);
        if (roster.isEmpty()) {
            return false;
        }
        // Build every kit first: a roll that throws (Dice.below rejects a scripted roll out of range) must leave
        // nothing half started. The roll order is the roster order, so Task 12's script lines up.
        List<Map<EquipmentSlot, ItemStack>> kits = roster.stream()
                .map(vex -> TeamMoveKits.kit(move, level, vex.getRandom(), vex.isCaster()))
                .toList();
        // A refused start leaves no record behind: it is created only now that the move really starts.
        Move record = STATES.computeIfAbsent(owner.getUUID(), id -> new Move());
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
        LOGGER.debug(
                "Team move {} started for {}: {} members",
                move.id(),
                owner.getName().getString(),
                roster.size());
        return true;
    }

    /**
     * The roster of a move (spec §2.2): Shield and Evader take the free vexes nearest the enemy or ward,
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

    /** A vex's place in a running move: the record and its own member state. */
    record Membership(Move record, Member member) {}

    /**
     * Runs {@link #tick} after each server player's tick (a dead player is still ticked until it respawns).
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /**
     * One step for {@code owner} (spec §2.5): drops roster members that are gone or no longer marked, and ends
     * the move when its roster is empty, the summoner is absent, the enemy or ward is gone or beyond 48 blocks, the
     * Evader's ward is healed, or the time limit is up. A summoner in another level ends the move without a check of
     * its own: the members are in the old level (pruned and released) or are new copies marked NONE (pruned), and the
     * enemy is not found in the summoner's level. Public so GameTests can step a mock player, which gets no player
     * tick. A summoner with no record is left alone: stepping never creates one.
     *
     * <p>Then, in AUTOMATIC mode with no move running, runs the trigger scan on its 10-tick clock (spec §2.3).
     * The scan clock is kept apart from the record and {@link #start} creates the record, so a summoner who only
     * waits for a trigger (or is in manual mode, or absent) gets none.
     *
     * @param owner the summoner
     */
    public static void tick(ServerPlayer owner) {
        long now = owner.level().getGameTime();
        Move record = STATES.get(owner.getUUID());
        if (record != null && record.active()) {
            prune(owner, record);
            String reason = endReason(owner, record, now);
            if (reason != null) {
                end(owner, record, now, reason);
            } else {
                advance(owner, record, now);
            }
        }
        if ((record == null || !record.active())
                && ServerConfig.teamMoveMode() == TeamMoveMode.AUTOMATIC
                && isPresent(owner)) {
            if (now >= NEXT_SCAN.getOrDefault(owner.getUUID(), 0L)) {
                NEXT_SCAN.put(owner.getUUID(), now + SCAN_INTERVAL);
                TeamMoveTriggers.scan(owner, record, now);
            }
        }
    }

    /** The record-level part of a running move: what no single member does (the Evader's shoves and deflections). */
    private static void advance(ServerPlayer owner, Move record, long now) {
        switch (record.move) {
            case EVADER -> EvaderMove.guard(owner, record, now);
            case RAID -> RaidMove.advance(record, now);
            case SHIELD, MULTISHOT, NONE -> {
                // nothing at record level
            }
        }
    }

    /** Lang key of the answer in automatic mode. */
    public static final String AUTOMATIC_KEY = "message.spellcraft.team_moves_automatic";

    /** Lang key of the answer while a move runs. */
    public static final String RUNNING_KEY = "message.spellcraft.team_move_running";

    /** Lang key of the answer while the kind is cooling down. */
    public static final String COOLING_KEY = "message.spellcraft.team_move_cooling";

    /** Lang key of the answer when no roster or no subject fits. */
    public static final String UNAVAILABLE_KEY = "message.spellcraft.team_move_unavailable";

    /** The manual request looks for an enemy this far from the summoner (spec §3.4). */
    public static final double REQUEST_RANGE = 16.0;

    /**
     * The fewest ticks between two handled requests of one summoner: a request scans 16 blocks for an enemy and may
     * answer on the action bar, so a modified client that floods the packet is ignored in between (spec §3.4).
     */
    public static final int REQUEST_INTERVAL = 10;

    /** The game time of each summoner's last handled request, by UUID. Transient, like {@link #STATES}. */
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    /** What a team-move request did. Only {@code STARTED} starts a move. */
    public enum Answer {
        /** A dead or spectating sender, or no real move. */
        IGNORED,
        /** The setting is automatic. */
        AUTOMATIC,
        /** A move already runs. */
        RUNNING,
        /** The kind is cooling down. */
        COOLING,
        /** No roster or no subject. */
        UNAVAILABLE,
        /** The move started. */
        STARTED
    }

    /**
     * Handles one team-move request from the K key (spec §3.4). It ignores a dead or spectating sender, and a
     * request fewer than {@link #REQUEST_INTERVAL} ticks after the sender's last handled one (silently), and does
     * not need the Evoking effect (the vexes outlive it, and automatic moves do not need it either). Then the first
     * that applies is the answer, shown on the action bar: automatic mode; a move already running; the kind cooling;
     * no roster or no subject (the nearest hostile within {@link #REQUEST_RANGE} blocks that is huntable, prey and fits
     * the move; for an Evader the ward of {@link #wardFor}); otherwise the move starts with its usual message.
     *
     * @param owner the sender
     * @param move the requested move
     * @return what the request did
     */
    public static Answer request(ServerPlayer owner, TeamMove move) {
        if (move == TeamMove.NONE || !isPresent(owner)) {
            return Answer.IGNORED;
        }
        long now = owner.level().getGameTime();
        Long last = LAST_REQUEST.get(owner.getUUID());
        if (last != null && now >= last && now - last < REQUEST_INTERVAL) {
            return Answer.IGNORED;
        }
        LAST_REQUEST.put(owner.getUUID(), now);
        if (ServerConfig.teamMoveMode() == TeamMoveMode.AUTOMATIC) {
            return refuse(owner, Answer.AUTOMATIC, AUTOMATIC_KEY);
        }
        Move record = STATES.get(owner.getUUID());
        if (record != null && record.active()) {
            return refuse(owner, Answer.RUNNING, RUNNING_KEY);
        }
        if (record != null && record.cooling(move, now)) {
            return refuse(owner, Answer.COOLING, COOLING_KEY);
        }
        LivingEntity subject = subjectFor(owner, move);
        if (subject == null || !start(owner, move, subject)) {
            return refuse(owner, Answer.UNAVAILABLE, UNAVAILABLE_KEY);
        }
        return Answer.STARTED;
    }

    /** Answers {@code answer} on the action bar with {@code key} and returns it. */
    private static Answer refuse(ServerPlayer owner, Answer answer, String key) {
        owner.sendOverlayMessage(Component.translatable(key));
        return answer;
    }

    /** The enemy a manual move is fought against, or the ward of an Evader. */
    private static @Nullable LivingEntity subjectFor(ServerPlayer owner, TeamMove move) {
        if (move == TeamMove.EVADER) {
            return wardFor(owner);
        }
        ServerLevel level = owner.level();
        return level
                .getEntitiesOfClass(
                        LivingEntity.class,
                        owner.getBoundingBox().inflate(REQUEST_RANGE),
                        enemy -> enemy.distanceTo(owner) <= REQUEST_RANGE
                                && BoundVex.isSubject(level, owner.getUUID(), enemy)
                                && TeamMoveRules.fits(move, enemy.getMaxHealth(), attackDamage(enemy)))
                .stream()
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
    }

    /** An entity's attack damage, 0 for one with no such attribute (ghasts, shulkers, the dragon). */
    static double attackDamage(LivingEntity entity) {
        AttributeInstance damage = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return damage == null ? 0.0 : damage.getValue();
    }

    /**
     * The one an Evader would protect (spec §2.3): the summoner, or a living ally within 16 blocks of them,
     * below half health. Never a mob carrying a summoner mark (a Bound Vex or an image). The lowest health fraction
     * wins; the summoner wins ties.
     */
    static @Nullable LivingEntity wardFor(ServerPlayer owner) {
        List<LivingEntity> hurt = new ArrayList<>();
        if (isPresent(owner) && TeamMoveRules.needsGuard(owner.getHealth(), owner.getMaxHealth())) {
            hurt.add(owner);
        }
        hurt.addAll(owner.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        owner.getBoundingBox().inflate(WARD_RANGE),
                        ally -> !ally.equals(owner)
                                && ally.isAlive()
                                && ally.distanceTo(owner) <= WARD_RANGE
                                && ally.getExistingData(AllyAttachments.SUMMONER)
                                        .isEmpty()
                                && Allies.isAllyOf(owner, ally)
                                && TeamMoveRules.needsGuard(ally.getHealth(), ally.getMaxHealth())));
        return hurt.stream()
                .min(Comparator.comparingDouble((LivingEntity ally) -> ally.getHealth() / ally.getMaxHealth())
                        .thenComparingInt(ally -> ally.equals(owner) ? 0 : 1))
                .orElse(null);
    }

    /** Whether the summoner can lead a move: alive and not a spectator (spec §2.3). */
    static boolean isPresent(ServerPlayer owner) {
        return owner.isAlive() && !owner.isSpectator();
    }

    /**
     * Drops the members that are dead, removed, in another level or no longer marked with this move. A member found
     * alive and still marked in another level has its kit put away now.
     */
    private static void prune(ServerPlayer owner, Move record) {
        ServerLevel level = owner.level();
        record.roster.removeIf(id -> {
            Entity found = level.getEntityInAnyDimension(id);
            if (found instanceof BoundVex vex && vex.isAlive() && vex.teamMove() == record.move) {
                if (vex.level().equals(level)) {
                    return false;
                }
                release(vex);
            }
            record.members.remove(id);
            return true;
        });
    }

    /**
     * The enemy or ward of a running move, found in {@code level}.
     *
     * @param level the summoner's level
     * @param record the summoner's state
     * @return the living subject, or null when it is gone, not living or in another level
     */
    static @Nullable LivingEntity subjectOf(ServerLevel level, Move record) {
        Entity found = record.subject == null ? null : level.getEntity(record.subject);
        return found instanceof LivingEntity living ? living : null;
    }

    /**
     * Why the move is over (spec §2.5), or null while it goes on: no members, an absent summoner, the time
     * limit, the subject gone, dead or beyond 48 blocks, or an Evader's ward healed to half health.
     */
    private static @Nullable String endReason(ServerPlayer owner, Move record, long now) {
        if (record.roster.isEmpty()) {
            return "no members left";
        }
        if (!isPresent(owner)) {
            return "summoner absent";
        }
        if (now >= TeamMoveRules.endsAt(record.move, record.start)) {
            return "time limit";
        }
        LivingEntity living = subjectOf(owner.level(), record);
        if (living == null || !living.isAlive() || TeamMoveRules.tooFar(living.distanceToSqr(owner))) {
            return "subject gone";
        }
        return record.move == TeamMove.EVADER && !TeamMoveRules.needsGuard(living.getHealth(), living.getMaxHealth())
                ? "ward healed"
                : null;
    }

    /** Ends the move: every remaining member's kit goes, its mark clears, and the kind's 30-second cooldown starts. */
    private static void end(ServerPlayer owner, Move record, long now, String reason) {
        LOGGER.debug(
                "Team move {} ended for {}: {} ({} members)",
                record.move.id(),
                owner.getName().getString(),
                reason,
                record.roster.size());
        releaseRoster(owner.level(), record);
        record.finish(now + TeamMoveRules.COOLDOWN_TICKS);
    }

    /** Puts every listed member's kit away and clears its mark, wherever the member is (any level of the server). */
    private static void releaseRoster(ServerLevel level, Move record) {
        for (UUID id : record.roster) {
            if (level.getEntityInAnyDimension(id) instanceof BoundVex vex && vex.teamMove() == record.move) {
                release(vex);
            }
        }
    }

    /**
     * Takes a vex out of its move: the kit slots are dismissed (the stash returns) and the mark clears.
     *
     * @param vex a Bound Vex
     */
    static void release(BoundVex vex) {
        TeamMoveKits.dismiss(vex, vex.teamMove());
        vex.setTeamMove(TeamMove.NONE);
    }

    /**
     * The vex's place in the record that lists it.
     *
     * @param vex a Bound Vex
     * @return the record and member state, or empty for a vex in no running move
     */
    static Optional<Membership> membership(BoundVex vex) {
        TeamMove mark = vex.teamMove();
        if (mark == TeamMove.NONE) {
            return Optional.empty();
        }
        return vex.summonerId()
                .map(STATES::get)
                .filter(record -> record.move == mark && record.members.containsKey(vex.getUUID()))
                .map(record -> new Membership(record, record.members.get(vex.getUUID())));
    }

    /**
     * Whether a move kind is cooling down for {@code owner}.
     *
     * @param owner the summoner
     * @param move the kind
     * @return true during the 30 seconds after the kind last ended
     */
    public static boolean cooling(ServerPlayer owner, TeamMove move) {
        Move record = STATES.get(owner.getUUID());
        return record != null && record.cooling(move, owner.level().getGameTime());
    }

    /**
     * Whether {@code owner} has a record at all (a record exists once a move has started for them); for GameTests,
     * never called in play.
     *
     * @param owner the summoner
     * @return true when a record exists
     */
    public static boolean hasRecord(ServerPlayer owner) {
        return STATES.containsKey(owner.getUUID());
    }

    /**
     * Makes the running move and every cooldown {@code ticks} older and makes the next automatic scan due at once; for
     * GameTests, never called in play.
     *
     * @param owner the summoner
     * @param ticks how much older the move and the cooldowns become
     */
    public static void age(ServerPlayer owner, long ticks) {
        Move record = STATES.get(owner.getUUID());
        if (record != null) {
            record.start -= ticks;
            record.cooldownEnd.replaceAll((kind, end) -> end - ticks);
        }
        LAST_REQUEST.computeIfPresent(owner.getUUID(), (id, time) -> time - ticks);
        NEXT_SCAN.remove(owner.getUUID());
    }

    /**
     * A member's Shield clocks, as plain values, for GameTests.
     *
     * @param stage the name of the member's Shield stage
     * @param nextAction the game time of its next bash, shot or attack
     * @param lungeStart the game time its current lunge began
     */
    public record Clocks(String stage, long nextAction, long lungeStart) {}

    /**
     * The clocks of {@code vex} in its running move; for GameTests, never called in play. It is the public seam over
     * the package-private {@code Member}, so no test touches the mutable record state.
     *
     * @param vex a Bound Vex
     * @return the clocks, or empty for a vex in no running move
     */
    public static Optional<Clocks> clocksOf(BoundVex vex) {
        return membership(vex)
                .map(m -> new Clocks(m.member().stage.name(), m.member().nextAction, m.member().lungeStart));
    }

    /**
     * Drops a logged-out summoner's record and puts every member's kit away, so no vex stays marked with nobody to end
     * its move. No cooldown is remembered.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner) {
            LAST_REQUEST.remove(owner.getUUID());
            NEXT_SCAN.remove(owner.getUUID());
            Move record = STATES.remove(owner.getUUID());
            if (record != null && record.active()) {
                releaseRoster(owner.level(), record);
            }
        }
    }

    /** Forgets every record and any scripted dice (single-player loads one server per world in the same JVM). */
    static void clear() {
        STATES.clear();
        LAST_REQUEST.clear();
        NEXT_SCAN.clear();
        Dice.resetAll();
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
