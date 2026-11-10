package io.github.willrieske.spellcraft.spell.rules;

/**
 * Where a following Bound Vex hovers around its summoner, how fast it flies there and how often it scans (Parts 4–5
 * spec §6.1). Distances are from the summoner's feet: sideways in the horizontal plane, height straight up. In the
 * three close postures the band is above the summoner's head, out of the way of their clicks.
 *
 * @param sideMin the band's inner sideways edge
 * @param sideMax the band's outer sideways edge
 * @param heightMin the band's lowest height above the summoner's feet
 * @param heightMax the band's highest height above the summoner's feet
 * @param speed the move-control speed toward a spot
 * @param scanInterval ticks between scans; even, because the goal clock runs every second tick
 * @param close whether this is one of the three close, overhead postures
 */
public record GuardPosture(
        double sideMin,
        double sideMax,
        double heightMin,
        double heightMax,
        double speed,
        int scanInterval,
        boolean close) {
    /** The kind of dimension a vex is in; a modded dimension is {@link #OTHER}. */
    public enum Place {
        /** The Overworld. */
        OVERWORLD,
        /** The Nether. */
        NETHER,
        /** The End. */
        END,
        /** Any other dimension, including modded ones. */
        OTHER
    }

    /** The Overworld by day, and any other dimension: the geometry of Parts 1–4. */
    public static final GuardPosture WIDE = new GuardPosture(2.0, 3.5, 0.5, 1.5, 0.7, 10, false);

    /** The Overworld in the dark, and the Nether: close and overhead, scanning every fourth tick. */
    private static final GuardPosture NEAR = new GuardPosture(1.0, 2.0, 2.2, 3.0, 0.35, 4, true);

    /** The End: closest and overhead, scanning every second tick. */
    private static final GuardPosture NEAREST = new GuardPosture(0.5, 1.5, 2.2, 3.0, 0.35, 2, true);

    /** In the wide posture a vex farther than this from the summoner's feet moves (feet to feet, as before Part 5). */
    private static final double WIDE_LIMIT = 4.0;

    /** How far outside a close band a vex may hang before it moves. */
    private static final double SLACK = 0.5;

    /** Absorbs floating-point error at the band's edges (2.2 − 0.5 is not exactly 1.7 in doubles). */
    private static final double TOLERANCE = 1.0e-9;

    /**
     * The posture for a place.
     *
     * @param place the kind of dimension
     * @param dark whether it is dark outside (night or a thunderstorm); only the Overworld asks
     * @return the posture
     */
    public static GuardPosture of(Place place, boolean dark) {
        return switch (place) {
            case OVERWORLD -> dark ? NEAR : WIDE;
            case NETHER -> NEAR;
            case END -> NEAREST;
            case OTHER -> WIDE;
        };
    }

    /**
     * Whether a vex at this offset from the summoner's feet is where it should be. Wide: within 4 blocks. Close: at
     * most the outer edge plus 0.5 sideways, and within the height range widened by 0.5 each way.
     *
     * @param dx blocks east of the summoner's feet
     * @param dy blocks above the summoner's feet
     * @param dz blocks south of the summoner's feet
     * @return true when the vex need not move; false for NaN
     */
    public boolean inPlace(double dx, double dy, double dz) {
        if (!close) {
            return dx * dx + dy * dy + dz * dz <= WIDE_LIMIT * WIDE_LIMIT;
        }
        double limit = sideMax + SLACK + TOLERANCE;
        return dx * dx + dz * dz <= limit * limit
                && dy >= heightMin - SLACK - TOLERANCE
                && dy <= heightMax + SLACK + TOLERANCE;
    }

    /**
     * A sideways distance inside the band.
     *
     * @param roll a unit roll, 0 to 1
     * @return the distance, from the inner edge (0) to the outer (1)
     */
    public double sideways(double roll) {
        return sideMin + roll * (sideMax - sideMin);
    }

    /**
     * A height inside the band.
     *
     * @param roll a unit roll, 0 to 1
     * @return the height above the summoner's feet
     */
    public double height(double roll) {
        return heightMin + roll * (heightMax - heightMin);
    }
}
