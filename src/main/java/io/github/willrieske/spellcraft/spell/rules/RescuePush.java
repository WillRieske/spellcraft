package io.github.willrieske.spellcraft.spell.rules;

/**
 * The velocities a rescuing Bound Vex gives (Parts 4–5 spec §6.2, §6.3). Every push adds to what the player is
 * already doing, so they keep steering; none ever lowers the upward speed.
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

    /** Lengths at or under this count as zero: no direction can be taken from them. */
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
     * One tick of the lava pull toward a shore: adds {@link #PULL} sideways toward the spot, keeps the sideways speed
     * at or under the larger of {@link #PULL_CAP} and what it already was, and raises the upward speed to at least
     * {@link #PULL_UP}. A spot straight above or below adds nothing sideways.
     *
     * @param v the player's velocity
     * @param toSpotX blocks east from the player to the spot
     * @param toSpotZ blocks south from the player to the spot
     * @return the pushed velocity
     */
    public static Vector lavaPull(Vector v, double toSpotX, double toSpotZ) {
        double distance = Math.hypot(toSpotX, toSpotZ);
        double x = v.x();
        double z = v.z();
        if (distance > TINY) {
            x += PULL * toSpotX / distance;
            z += PULL * toSpotZ / distance;
            double limit = Math.max(PULL_CAP, Math.hypot(v.x(), v.z()));
            double speed = Math.hypot(x, z);
            if (speed > limit) {
                x *= limit / speed;
                z *= limit / speed;
            }
        }
        return new Vector(x, Math.max(v.y(), PULL_UP), z);
    }

    /**
     * One tick of the lava pull with no shore in reach: only the upward speed, raised to at least {@link #LIFT}.
     *
     * @param v the player's velocity
     * @return the pushed velocity
     */
    public static Vector lavaLift(Vector v) {
        return new Vector(v.x(), Math.max(v.y(), LIFT), v.z());
    }

    /**
     * One tick of the drowning push: the upward speed raised to at least {@link #LIFT}, the rest kept.
     *
     * @param v the player's velocity
     * @return the pushed velocity
     */
    public static Vector drowningPush(Vector v) {
        return new Vector(v.x(), Math.max(v.y(), LIFT), v.z());
    }

    /**
     * A returned fireball's velocity: its old speed along the new heading; with no heading (zero length), straight
     * back the way it came.
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
