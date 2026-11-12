package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.rules.GuardPosture;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a Bound Vex at its summoner's side (Evoking II spec §3.3, Parts 4–5 spec §6.1): while following (not
 * staying) and idle (no target), a vex outside its posture's band ({@code BoundVex.posture()}: wide by day in the
 * Overworld, close and overhead at night, in the Nether and in the End) flies to a random spot inside the band at
 * the posture's speed, and a vex farther than {@link #TELEPORT_DISTANCE} blocks teleports to an air cell beside the
 * summoner instead. In the wide posture each use makes one move; the move control flies it there and {@code canUse}
 * picks the next. In a close posture the use lasts until the vex arrives, and then {@link #stop} halts it dead: at
 * speed 0.35 a vex that coasted past its spot sank to the summoner's eye height (measured 1.5 above the feet), into
 * their clicks. A close flight ends when the vex arrives, when it gets a target, when the summoner runs more than
 * {@link #TELEPORT_DISTANCE} blocks off or is gone (the flight is dropped, so the next use can teleport), when the vex
 * is told to stay, or when a higher-priority goal takes the move control. Without a summoner in the level the goal is
 * idle. Like vanilla's random move, the goal waits for the move control to be free ({@code !hasWanted()}), so a vex
 * finishes a return flight to its post or a charge before it follows.
 *
 * <p>Functional spec §6.1, §6.3.
 */
public final class FollowSummonerGoal extends Goal {
    /** The wide posture's inner sideways edge (feet to feet); the GameTests read it. */
    public static final double ORBIT_MIN = GuardPosture.WIDE.sideMin();

    /** The wide posture's limit (feet to feet): beyond it a wide-posture vex moves. */
    public static final double ORBIT_MAX = GuardPosture.WIDE_LIMIT;

    /** Farther than this (feet to feet), the vex teleports instead of flying. Functional spec §6.3. */
    public static final double TELEPORT_DISTANCE = 12.0;

    /**
     * The teleport lands within this many blocks of the summoner's feet sideways, and 0–2 blocks up.
     * Functional spec §6.3.
     */
    public static final int TELEPORT_SPREAD = 2;

    /**
     * The farthest a teleported vex lands from the summoner's feet. The summoner stands anywhere in its block, so a
     * cell 2 blocks off on both horizontal axes and 2 up, entered at its center (2.5 from the block's far corner on
     * each axis), is at worst √(3 · 2.5²) ≈ 4.33 blocks away; 4.5 bounds that and leaves room for a few ticks of
     * hover. A teleported vex can land just outside the {@link #ORBIT_MAX} band and take one more hop.
     */
    public static final double TELEPORT_LANDING_MAX = 4.5;

    private static final int TELEPORT_TRIES = 10;

    private final BoundVex vex;

    /**
     * Whether the current use is a flight in a close posture (never a teleport): such a use runs until the vex arrives
     * and then brakes it, so it cannot coast out of its overhead band.
     */
    private boolean closeFlight;

    FollowSummonerGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (vex.isStaying() || vex.getTarget() != null || vex.getMoveControl().hasWanted()) {
            return false;
        }
        GuardPosture posture = vex.posture();
        return vex.summoner()
                .filter(owner -> {
                    Vec3 offset = vex.position().subtract(owner.position());
                    return !posture.inPlace(offset.x, offset.y, offset.z);
                })
                .isPresent();
    }

    /**
     * One move per use in the wide posture. A close-posture flight continues while the move control still has a wanted
     * position and the vex is still following, idle, and has its summoner within {@link #TELEPORT_DISTANCE}; arriving
     * clears the wanted position.
     */
    @Override
    public boolean canContinueToUse() {
        return closeFlight
                && vex.getMoveControl().hasWanted()
                && !vex.isStaying()
                && vex.getTarget() == null
                && summonerNear();
    }

    /** Whether the summoner is here and no farther than {@link #TELEPORT_DISTANCE} (feet to feet). */
    private boolean summonerNear() {
        return vex.summoner()
                .filter(owner -> vex.distanceTo(owner) <= TELEPORT_DISTANCE)
                .isPresent();
    }

    /**
     * Ends a close-posture flight. When the move control has arrived (no wanted position) the vex is stopped dead.
     * When a target appeared, the flight's wanted position is dropped so that the charge goal (which waits for a free
     * move control) can start at once; likewise when the summoner ran off or is gone, so that the next use can
     * teleport and the vex does not coast on to a stale spot. Stopped for any other reason (a stay order, a rescue)
     * the velocity and the wanted position are left alone.
     */
    @Override
    public void stop() {
        if (closeFlight) {
            if (!vex.getMoveControl().hasWanted()) {
                vex.setDeltaMovement(Vec3.ZERO);
            } else if (vex.getTarget() != null || !summonerNear()) {
                vex.getMoveControl().setWait();
            }
        }
        closeFlight = false;
    }

    @Override
    public void start() {
        closeFlight = false;
        vex.summoner().ifPresent(owner -> {
            if (vex.distanceTo(owner) > TELEPORT_DISTANCE) {
                teleportBeside(owner);
            } else {
                flyToOrbit(owner);
            }
        });
    }

    /**
     * A random spot inside the posture's band, measured from the summoner's feet as {@code canUse} measures.
     * Functional spec §6.1.
     */
    private void flyToOrbit(ServerPlayer owner) {
        RandomSource random = vex.getRandom();
        GuardPosture posture = vex.posture();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double side = posture.sideways(random.nextDouble());
        Vec3 spot = owner.position()
                .add(Math.cos(angle) * side, posture.height(random.nextDouble()), Math.sin(angle) * side);
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, posture.speed());
        closeFlight = posture.close();
    }

    /**
     * An air cell within {@link #TELEPORT_SPREAD} of the summoner's feet, 0–2 blocks up; after ten misses the cell
     * above the summoner's own (vexes clip through blocks, so any cell works). The velocity and the wanted position
     * are cleared so the vex hovers where it lands. Functional spec §6.3.
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
