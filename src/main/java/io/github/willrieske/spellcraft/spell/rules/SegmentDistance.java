package io.github.willrieske.spellcraft.spell.rules;

/**
 * The distance from a point to a line segment: how close a friend stands to an arrow's flight (Evoking II Part 3 spec
 * §4.3 "Friendly fire"). Coordinates are plain doubles so this class needs no Minecraft types.
 *
 * <p>Functional spec §8.7.
 */
public final class SegmentDistance {
    /** A segment whose squared length is below this counts as a single point. */
    private static final double DEGENERATE_LENGTH_SQR = 1.0E-9;

    private SegmentDistance() {}

    /**
     * The distance from the point P to the segment from A to B: to the nearest point of the segment, so beyond either
     * end it is the distance to that end. A segment of zero length is the point A.
     *
     * @param px the point's x
     * @param py the point's y
     * @param pz the point's z
     * @param ax the segment start's x
     * @param ay the segment start's y
     * @param az the segment start's z
     * @param bx the segment end's x
     * @param by the segment end's y
     * @param bz the segment end's z
     * @return the distance, never negative
     */
    public static double distanceToSegment(
            double px, double py, double pz, double ax, double ay, double az, double bx, double by, double bz) {
        double abx = bx - ax;
        double aby = by - ay;
        double abz = bz - az;
        double lengthSqr = abx * abx + aby * aby + abz * abz;
        double along = 0.0;
        if (lengthSqr >= DEGENERATE_LENGTH_SQR) {
            double dot = (px - ax) * abx + (py - ay) * aby + (pz - az) * abz;
            along = Math.clamp(dot / lengthSqr, 0.0, 1.0);
        }
        double dx = px - (ax + abx * along);
        double dy = py - (ay + aby * along);
        double dz = pz - (az + abz * along);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
