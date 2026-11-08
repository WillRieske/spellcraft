package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a Bound Vex at its summoner's side (Evoking II spec §3.3): while following (not staying) and idle (no target),
 * a vex farther than the orbit band flies to a random spot inside it, and a vex farther than
 * {@link #TELEPORT_DISTANCE} blocks teleports to an air cell beside the summoner instead. Each use makes one move; the
 * move control flies it there and {@code canUse} picks the next. Without a summoner in the level the goal is idle.
 * Like vanilla's random move, the goal waits for the move control to be free ({@code !hasWanted()}), so a vex finishes
 * a wander leg or a charge before it follows.
 */
public final class FollowSummonerGoal extends Goal {
    /** The orbit band's inner edge (feet to feet): the vex is left alone inside the band. */
    public static final double ORBIT_MIN = 2.0;

    /** The orbit band's outer edge (feet to feet): beyond it the vex moves. */
    public static final double ORBIT_MAX = 4.0;

    /** Farther than this (feet to feet), the vex teleports instead of flying. */
    public static final double TELEPORT_DISTANCE = 12.0;

    /** The teleport lands within this many blocks of the summoner's feet sideways, and 0–2 blocks up. */
    public static final int TELEPORT_SPREAD = 2;

    /**
     * The farthest a teleported vex lands from the summoner's feet: a cell 2 blocks off on both horizontal axes and 2
     * up, entered at its center, is √(2² + 2.5² + 2²) ≈ 3.8 blocks away; 4.5 leaves room for a few ticks of hover.
     */
    public static final double TELEPORT_LANDING_MAX = 4.5;

    /**
     * The orbit spot's farthest horizontal distance; with the spot 0.5–1.5 blocks above the summoner's feet, every spot
     * is at most √(3.5² + 1.5²) ≈ 3.8 blocks from the feet, inside {@link #ORBIT_MAX}, so one hop lands in the band.
     */
    private static final double ORBIT_SPOT_RADIUS = 3.5;

    private static final int TELEPORT_TRIES = 10;

    /** Below vanilla's charge speed (1.0), so the vex coasts about a block past its spot instead of darting through. */
    private static final double SPEED = 0.7;

    private final BoundVex vex;

    FollowSummonerGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (vex.isStaying() || vex.getTarget() != null || vex.getMoveControl().hasWanted()) {
            return false;
        }
        return vex.summoner().filter(owner -> vex.distanceTo(owner) > ORBIT_MAX).isPresent();
    }

    /** One move per use. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        vex.summoner().ifPresent(owner -> {
            if (vex.distanceTo(owner) > TELEPORT_DISTANCE) {
                teleportBeside(owner);
            } else {
                flyToOrbit(owner);
            }
        });
    }

    /**
     * A random spot 2–3.5 blocks sideways from the summoner's feet and 0.5–1.5 blocks up: built from the same point
     * {@code canUse} measures from, so the spot is always inside the band.
     */
    private void flyToOrbit(ServerPlayer owner) {
        RandomSource random = vex.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = ORBIT_MIN + random.nextDouble() * (ORBIT_SPOT_RADIUS - ORBIT_MIN);
        Vec3 spot = owner.position().add(Math.cos(angle) * radius, 0.5 + random.nextDouble(), Math.sin(angle) * radius);
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, SPEED);
    }

    /**
     * An air cell within {@link #TELEPORT_SPREAD} of the summoner's feet, 0–2 blocks up; after ten misses the cell
     * above the summoner's own (vexes clip through blocks, so any cell works). The velocity and the wanted position
     * are cleared so the vex hovers where it lands.
     */
    private void teleportBeside(ServerPlayer owner) {
        RandomSource random = vex.getRandom();
        BlockPos feet = owner.blockPosition();
        BlockPos cell = feet.above();
        for (int attempt = 0; attempt < TELEPORT_TRIES; attempt++) {
            BlockPos candidate = feet.offset(
                    random.nextInt(2 * TELEPORT_SPREAD + 1) - TELEPORT_SPREAD,
                    random.nextInt(3),
                    random.nextInt(2 * TELEPORT_SPREAD + 1) - TELEPORT_SPREAD);
            if (vex.level().isEmptyBlock(candidate)) {
                cell = candidate;
                break;
            }
        }
        vex.teleportTo(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
        vex.setDeltaMovement(Vec3.ZERO);
        vex.getMoveControl().setWait();
    }
}
