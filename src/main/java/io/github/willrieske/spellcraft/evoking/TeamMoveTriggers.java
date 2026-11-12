package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import io.github.willrieske.spellcraft.spell.rules.TeamMoveRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The automatic triggers of the team moves (Parts 6-7 spec §2.3). One scan, every
 * {@link TeamMoves#SCAN_INTERVAL} ticks per summoner, while no move runs. Candidates are hostiles that are huntable and
 * prey for the summoner and whose feet are inside the guard area of at least one following vex. In order the first move
 * that can start wins: Raid, Shield, Multishot (each against the nearest candidate that earns it), then Evader.
 * Multishot is detected here, from {@code getTarget()}, not from the target event: a brain mob (a piglin brute, the
 * warden) writes its target without always firing it.
 *
 * <p>Functional spec §11.4.
 */
final class TeamMoveTriggers {
    /** The enemy-side moves in trigger order. */
    private static final List<TeamMove> ENEMY_MOVES = List.of(TeamMove.RAID, TeamMove.SHIELD, TeamMove.MULTISHOT);

    private TeamMoveTriggers() {}

    /**
     * Looks for a trigger and starts the first move that can start.
     *
     * @param owner the summoner, present
     * @param record their record, or null when they have none yet; no move runs
     * @param now the game time
     */
    static void scan(ServerPlayer owner, TeamMoves.@Nullable Move record, long now) {
        List<BoundVex> followers = owner.level()
                .getEntitiesOfClass(
                        BoundVex.class,
                        owner.getBoundingBox().inflate(TeamMoves.FREE_RANGE),
                        vex -> vex.isAlive() && vex.isSummonedBy(owner.getUUID()) && !vex.isStaying());
        if (followers.isEmpty()) {
            return;
        }
        List<BoundVex> free = TeamMoves.freeVexes(owner, followers, false);
        List<BoundVex> raidFree = TeamMoves.freeVexes(owner, followers, true);
        boolean raidReady = ready(record, TeamMove.RAID, raidFree.size(), now);
        boolean shieldReady = ready(record, TeamMove.SHIELD, free.size(), now);
        boolean multishotReady = ready(record, TeamMove.MULTISHOT, free.size(), now);
        List<LivingEntity> candidates = candidates(owner, followers);
        for (TeamMove move : ENEMY_MOVES) {
            for (LivingEntity candidate : candidates) {
                boolean targetsFreeVex = candidate instanceof Mob mob
                        && mob.getTarget() instanceof BoundVex hunted
                        && free.contains(hunted);
                TeamMove picked = TeamMoveRules.pick(
                        candidate.getMaxHealth(),
                        TeamMoves.attackDamage(candidate),
                        targetsFreeVex,
                        raidReady,
                        shieldReady,
                        multishotReady);
                if (picked == move && TeamMoves.start(owner, move, candidate)) {
                    return;
                }
            }
        }
        if (ready(record, TeamMove.EVADER, free.size(), now)) {
            LivingEntity ward = TeamMoves.wardFor(owner);
            if (ward != null) {
                TeamMoves.start(owner, TeamMove.EVADER, ward);
            }
        }
    }

    /** Whether {@code move} could start: not cooling and enough free vexes. */
    private static boolean ready(TeamMoves.@Nullable Move record, TeamMove move, int free, long now) {
        return (record == null || !record.cooling(move, now)) && TeamMoveRules.rosterSize(move, free) > 0;
    }

    /** The hostiles in any follower's guard area, nearest the summoner first, each once. */
    private static List<LivingEntity> candidates(ServerPlayer owner, List<BoundVex> followers) {
        ServerLevel level = owner.level();
        // A follower is within FREE_RANGE of the summoner's box, and its guard area reaches HALF_WIDTH beyond it (the
        // extra block covers the follower's own box).
        AABB reach = owner.getBoundingBox().inflate(TeamMoves.FREE_RANGE + GuardArea.HALF_WIDTH + 1.0);
        return level
                .getEntitiesOfClass(
                        LivingEntity.class,
                        reach,
                        candidate -> BoundVex.isSubject(level, owner.getUUID(), candidate)
                                && followers.stream().anyMatch(vex -> vex.inGuardArea(candidate)))
                .stream()
                .sorted(Comparator.comparingDouble(owner::distanceToSqr))
                .toList();
    }
}
