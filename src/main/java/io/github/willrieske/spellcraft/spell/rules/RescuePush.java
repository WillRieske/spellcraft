package io.github.willrieske.spellcraft.spell.rules;

/**
 * The velocities a rescuing Bound Vex gives (Parts 4–5 spec §6.2, §6.3). Every push adds to what the player is
 * already doing (slowed by the fluid's drag, as the client's own move would), so they keep steering; none lowers the
 * upward speed below its floor.
 *
 * <p>Functional spec §10.3, §10.5, §10.6.
 */
public final class RescuePush {
    /** Sideways speed added toward the shore each tick of a lava pull. */
    public static final double PULL = 0.1;

    /** The lava pull never speeds the player past this sideways. */
    public static final double PULL_CAP = 0.35;

    /** The least upward speed during a lava pull toward a shore. */
    public static final double PULL_UP = 0.3;

    /** The least upward speed during a drowning push, and a lava pull with no shore. */
    public static final double LIFT = 0.4;

    /**
     * The factor vanilla multiplies a player's velocity by each tick in lava ({@code LivingEntity.travelInLava},
     * {@code LivingEntity.java:2650}; in shallow lava the sideways factor is the same, {@code :2646}). Shallow lava
     * uses 0.8 on {@code y} in vanilla; the floor on {@code y} in every push makes that difference moot.
     */
    public static final double LAVA_DRAG = 0.5;

    /**
     * The factor vanilla multiplies a player's velocity by each tick in water (the default of
     * {@code LivingEntity.getWaterSlowDown}, {@code LivingEntity.java:2501-2503}, used by {@code travelInWater},
     * {@code :2609-2635}). Sprint-swimming (0.9) and Depth Strider differ and are not modeled.
     */
    public static final double WATER_DRAG = 0.8;

    /** A length in blocks at or under which a direction is too short to use, so it counts as zero. */
    private static final double TINY = 1.0e-6;

    /**
     * A velocity or direction, in blocks per tick.
     *
     * @param x east
     * @param y up
     * @param z south
     */
    public record Vector(double x, double y, double z) {}

    private RescuePush() {}

    /**
     * One tick of the lava pull toward a shore: the velocity the player had, slowed by the lava's drag (the pushed
     * velocity replaces the client's own result, so the drag must be applied here), plus {@link #PULL} sideways toward
     * the spot, with the sideways speed held at or under {@link #PULL_CAP} always, and the upward speed raised to at
     * least {@link #PULL_UP}. A spot straight above or below adds nothing sideways. Functional spec §10.3.
     *
     * @param v the velocity the player had (the client's last reported movement)
     * @param toSpotX blocks east from the player to the spot
     * @param toSpotZ blocks south from the player to the spot
     * @return the pushed velocity
     */
    public static Vector lavaPull(Vector v, double toSpotX, double toSpotZ) {
        double distance = Math.hypot(toSpotX, toSpotZ);
        double x = v.x() * LAVA_DRAG;
        double z = v.z() * LAVA_DRAG;
        if (distance > TINY) {
            x += PULL * toSpotX / distance;
            z += PULL * toSpotZ / distance;
        }
        double speed = Math.hypot(x, z);
        if (speed > PULL_CAP) {
            x *= PULL_CAP / speed;
            z *= PULL_CAP / speed;
        }
        return new Vector(x, Math.max(v.y() * LAVA_DRAG, PULL_UP), z);
    }

    /**
     * One tick of the lava pull with no shore in reach: the velocity slowed by the lava's drag, the upward speed raised
     * to at least {@link #LIFT}. Functional spec §10.3.
     *
     * @param v the velocity the player had
     * @return the pushed velocity
     */
    public static Vector lavaLift(Vector v) {
        return new Vector(v.x() * LAVA_DRAG, Math.max(v.y() * LAVA_DRAG, LIFT), v.z() * LAVA_DRAG);
    }

    /**
     * One tick of the drowning push: the velocity slowed by the water's drag, the upward speed raised to at least
     * {@link #LIFT}. Functional spec §10.5.
     *
     * @param v the velocity the player had
     * @return the pushed velocity
     */
    public static Vector drowningPush(Vector v) {
        return new Vector(v.x() * WATER_DRAG, Math.max(v.y() * WATER_DRAG, LIFT), v.z() * WATER_DRAG);
    }

    /**
     * A returned fireball's velocity: its old speed along the new heading; with no heading (zero length), straight
     * back the way it came. Functional spec §10.6.
     *
     * @param old the fireball's velocity
     * @param headingX the new heading, east
     * @param headingY the new heading, up
     * @param headingZ the new heading, south
     * @return the new velocity
     */
    public static Vector returned(Vector old, double headingX, double headingY, double headingZ) {
        double length = Math.sqrt(headingX * headingX + headingY * headingY + headingZ * headingZ);
        if (length <= TINY) {
            return new Vector(-old.x(), -old.y(), -old.z());
        }
        double speed = Math.sqrt(old.x() * old.x() + old.y() * old.y() + old.z() * old.z());
        return new Vector(speed * headingX / length, speed * headingY / length, speed * headingZ / length);
    }
}
