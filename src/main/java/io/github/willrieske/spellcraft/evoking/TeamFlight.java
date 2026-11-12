package io.github.willrieske.spellcraft.evoking;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flying and looking for the team moves: the vex's own move control, as the charge goal uses it (spec §4).
 * Public so the GameTests can check {@link #boxGap} and {@link #ringPoint}; {@code flyTo}, {@code lookAt} and the
 * speed constants stay package-private.
 */
public final class TeamFlight {
    /** The charge goal's speed modifier: "normal speed". */
    static final double NORMAL = 1.0;

    /** Half of {@link #NORMAL}. */
    static final double HALF = 0.5;

    /** The push a vex gets toward its wanted point at speed 1.0, per tick (the move control's {@code 0.05}). */
    private static final double VEX_PUSH_PER_SPEED = 0.05;

    /** How far ahead of the vex, in the push's direction, {@link #steer} sets the wanted point (over a body's size). */
    private static final double STEER_REACH = 2.0;

    /** Within this distance of the spot a vex settles (a spring and damping) instead of flying straight at it. */
    static final double SETTLE_RANGE = 3.0;

    /** Settling: how strongly a vex is pushed toward the spot, per block of gap. */
    private static final double SETTLE_POSITION_GAIN = 0.06;

    /** Settling: how strongly a vex is pushed to match the spot's velocity, per block a tick of difference. */
    private static final double SETTLE_VELOCITY_GAIN = 0.3;

    private TeamFlight() {}

    /** Orders the vex to fly to {@code spot} at {@code speed}; a vex has no gravity and clips through blocks. */
    static void flyTo(BoundVex vex, Vec3 spot, double speed) {
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, speed);
    }

    /**
     * Pushes the vex by {@code push} blocks a tick: a vex only accelerates toward its wanted point (0.05 a tick at
     * normal speed) and coasts, so the wanted point is set {@link #STEER_REACH} blocks ahead in the push's direction,
     * at the speed whose acceleration is the push's strength (at most {@link #NORMAL}).
     */
    static void steer(BoundVex vex, Vec3 push) {
        double strength = push.length();
        Vec3 direction = strength < 1.0E-6 ? Vec3.ZERO : push.scale(1.0 / strength);
        flyTo(vex, vex.position().add(direction.scale(STEER_REACH)), Math.min(NORMAL, strength / VEX_PUSH_PER_SPEED));
    }

    /**
     * Holds the vex at {@code spot}: a spring toward the spot plus damping toward the spot's own step. A vex only
     * accelerates toward its wanted point and coasts, so a plain "fly to the spot" overshoots and circles; the one
     * place the spring and damping constants live.
     *
     * @param vex the vex
     * @param spot where to hold
     * @param spotStep how far the spot moved this tick (zero for a fixed spot)
     */
    static void settle(BoundVex vex, Vec3 spot, Vec3 spotStep) {
        Vec3 push = spot.subtract(vex.position())
                .scale(SETTLE_POSITION_GAIN)
                .add(spotStep.subtract(vex.getDeltaMovement()).scale(SETTLE_VELOCITY_GAIN));
        steer(vex, push);
    }

    /**
     * Flies to {@code spot} at normal speed while farther than {@link #SETTLE_RANGE} from it, then {@link #settle}s.
     *
     * @param vex the vex
     * @param spot where to hold
     * @param spotStep how far the spot moved this tick (zero for a fixed spot)
     */
    static void holdAt(BoundVex vex, Vec3 spot, Vec3 spotStep) {
        if (spot.subtract(vex.position()).length() > SETTLE_RANGE) {
            flyTo(vex, spot, NORMAL);
        } else {
            settle(vex, spot, spotStep);
        }
    }

    /** Turns the vex's head toward {@code target}'s eyes. */
    static void lookAt(BoundVex vex, Entity target) {
        Vec3 eye = target.getEyePosition();
        vex.getLookControl().setLookAt(eye.x, eye.y, eye.z, 30.0F, 30.0F);
    }

    /**
     * The distance between the bounding boxes of {@code a} and {@code b}; 0 when they touch or overlap.
     *
     * @param a the first entity
     * @param b the second entity
     * @return the gap in blocks
     */
    public static double boxGap(Entity a, Entity b) {
        AABB x = a.getBoundingBox();
        AABB y = b.getBoundingBox();
        double dx = Math.max(0.0, Math.max(x.minX - y.maxX, y.minX - x.maxX));
        double dy = Math.max(0.0, Math.max(x.minY - y.maxY, y.minY - x.maxY));
        double dz = Math.max(0.0, Math.max(x.minZ - y.maxZ, y.minZ - x.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * The point {@code radius} blocks from {@code center} on the horizontal line toward {@code from}, at height
     * {@code y}; east of the center when {@code from} is straight above it.
     *
     * @param center the point to measure from
     * @param from the point whose direction is wanted
     * @param radius the horizontal distance from {@code center}
     * @param y the height of the result
     * @return the point on the ring
     */
    public static Vec3 ringPoint(Vec3 center, Vec3 from, double radius, double y) {
        Vec3 away = from.subtract(center).multiply(1.0, 0.0, 1.0);
        Vec3 direction = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        return new Vec3(center.x + direction.x * radius, y, center.z + direction.z * radius);
    }
}
