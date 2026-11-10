package io.github.willrieske.spellcraft.spell.rules;

/**
 * The boxes a Bound Vex guards and the reach it fights at. Following (Evoking II spec §3.4): 8 blocks east–west, 8
 * north–south and 7 tall, centered on the vex, faces included, and a target farther than 7.25 blocks is dropped.
 * Staying (Part 3 spec §2.3): 13 × 13 × 7 around the post point. Offsets are from
 * the vex's {@code Entity.position()} (its feet) to the other entity's {@code position()} (its feet), never eyes or
 * box centers.
 */
public final class GuardArea {
    /** Half the box's width, east–west and north–south. */
    public static final double HALF_WIDTH = 4.0;

    /** Half the box's height. */
    public static final double HALF_HEIGHT = 3.5;

    /** How far a following vex keeps fighting a target. */
    public static final double REACH = 7.25;

    /** Half the width of the box a staying vex defends around its post: 13 blocks across (Part 3 spec §2.3). */
    public static final double STAY_HALF_WIDTH = 6.5;

    /**
     * The vex's {@code FOLLOW_RANGE} attribute: vanilla's outer bound on a target's distance from the vex. A staying
     * vex and its target can be at opposite corners of the stay box, 2·√(6.5² + 6.5² + 3.5²) ≈ 19.7 blocks apart, so
     * the bound is 20; the box and the reach are the real limits (Part 3 spec §2.3).
     */
    public static final double FOLLOW_RANGE = 20.0;

    private GuardArea() {}

    /**
     * Whether a point is inside the stay box, faces included.
     *
     * @param dx blocks east of the post point
     * @param dy blocks above the post point
     * @param dz blocks south of the post point
     * @return true inside the box or on a face; false outside, and for NaN
     */
    public static boolean containsStaying(double dx, double dy, double dz) {
        return Math.abs(dx) <= STAY_HALF_WIDTH && Math.abs(dy) <= HALF_HEIGHT && Math.abs(dz) <= STAY_HALF_WIDTH;
    }

    /**
     * Whether a point is inside the guard box, faces included.
     *
     * @param dx blocks east of the vex
     * @param dy blocks above the vex
     * @param dz blocks south of the vex
     * @return true inside the box or on a face; false outside, and for NaN
     */
    public static boolean contains(double dx, double dy, double dz) {
        return Math.abs(dx) <= HALF_WIDTH && Math.abs(dy) <= HALF_HEIGHT && Math.abs(dz) <= HALF_WIDTH;
    }

    /**
     * Whether a point is within {@link #REACH} of the vex.
     *
     * @param dx blocks east of the vex
     * @param dy blocks above the vex
     * @param dz blocks south of the vex
     * @return true at or under the reach; false beyond it, and for NaN or infinity
     */
    public static boolean withinReach(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }
}
