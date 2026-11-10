package io.github.willrieske.spellcraft.evoking;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flying and looking for the team moves: the vex's own move control, as the charge goal uses it (spec section 4).
 * Public so the GameTests can check {@link #boxGap} and {@link #ringPoint}; {@code flyTo}, {@code lookAt} and the
 * speed constants stay package-private.
 */
public final class TeamFlight {
    /** The charge goal's speed modifier: "normal speed". */
    static final double NORMAL = 1.0;

    /** Half of {@link #NORMAL}. */
    static final double HALF = 0.5;

    private TeamFlight() {}

    /** Orders the vex to fly to {@code spot} at {@code speed}; a vex has no gravity and clips through blocks. */
    static void flyTo(BoundVex vex, Vec3 spot, double speed) {
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, speed);
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
