package io.github.willrieske.spellcraft.evoking;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.phys.Vec3;

/**
 * The only goal a bribed vex has (Evoking II Parts 4-5 spec §3.2): each tick it flies directly away from the nearest
 * player (through blocks, as vexes do). When it is at least {@link #CLEAR_DISTANCE} blocks from every player within
 * {@link #PLAYER_RANGE} blocks and none of those players has line of sight to it ({@code LivingEntity.hasLineOfSight},
 * a geometric clip, docs/api-notes.md §23), it is discarded: no sound, no particles, no drop. After {@link #LIMIT}
 * ticks it is discarded anyway, in view if need be. A spectator does not count as a player here: it is not seen and
 * sees nothing that matters. With no player within range at all the vex is discarded at once. The goal always runs,
 * and {@link #install} gives a vex nothing else; a vex loaded from a save gets it again through
 * {@code BoundVexEvents.onJoin}, and its {@link #LIMIT} ticks start over.
 */
public final class FleeFromViewGoal extends Goal {
    /** How far from every player the vex must be before it may vanish. */
    public static final double CLEAR_DISTANCE = 12.0;

    /** Only players this near count: the vex is discarded at once when none is. */
    public static final double PLAYER_RANGE = 64.0;

    /** The most ticks the vex flees before it is discarded whether seen or not. */
    public static final int LIMIT = 200;

    /** How far past the vex along the line from the player each tick's wanted position lies. */
    private static final double STRIDE = 8.0;

    /** The charge speed of a vex's move control. */
    private static final double SPEED = 1.0;

    private final Vex vex;
    private int ticks;

    /**
     * Makes the goal for {@code vex}; {@link #install} also strips the vex's other goals.
     *
     * @param vex the bribed vex
     */
    public FleeFromViewGoal(Vex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * Turns {@code vex} into a leaving vex (spec §3.2): its target is cleared, every goal and every target goal is
     * removed, and this goal is added. Safe to call twice.
     *
     * @param vex the vex that took the bribe
     */
    public static void install(Vex vex) {
        vex.setTarget(null);
        vex.goalSelector.removeAllGoals(goal -> true);
        vex.targetSelector.removeAllGoals(goal -> true);
        vex.setIsCharging(false);
        vex.goalSelector.addGoal(0, new FleeFromViewGoal(vex));
    }

    /** Always usable: a bribed vex has nothing else to do. */
    @Override
    public boolean canUse() {
        return true;
    }

    /** Counts every tick, since a running goal is otherwise ticked every other tick. */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (!(vex.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (ticks >= LIMIT) {
            vex.discard();
            return;
        }
        List<ServerPlayer> players = level.players().stream()
                .filter(player -> !player.isSpectator() && vex.distanceToSqr(player) <= PLAYER_RANGE * PLAYER_RANGE)
                .toList();
        ServerPlayer nearest = players.stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .orElse(null);
        if (nearest == null || (vex.distanceToSqr(nearest) >= CLEAR_DISTANCE * CLEAR_DISTANCE && unseen(players))) {
            vex.discard();
            return;
        }
        Vec3 away = vex.position().subtract(nearest.position());
        Vec3 direction = away.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
        Vec3 wanted = vex.position().add(direction.scale(STRIDE));
        vex.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, SPEED);
        vex.getLookControl().setLookAt(wanted.x, wanted.y, wanted.z);
    }

    /** Whether none of {@code players} has a clear line to the vex. */
    private boolean unseen(List<ServerPlayer> players) {
        return players.stream().noneMatch(player -> player.hasLineOfSight(vex));
    }
}
