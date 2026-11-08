package io.github.willrieske.spellcraft.spell.rules;

/**
 * The box a Bound Vex guards and the reach it fights at (Evoking II spec §3.4): 8 blocks east–west, 8 north–south and
 * 7 tall, centered on the vex, faces included, and a target farther than 7.25 blocks is dropped. Offsets are from the
 * vex's {@code Entity.position()} (its feet) to the other entity's {@code position()} (its feet), never eyes or box
 * centers.
 */
public final class GuardArea {
    /** Half the box's width, east–west and north–south. */
    public static final double HALF_WIDTH = 4.0;

    /** Half the box's height. */
    public static final double HALF_HEIGHT = 3.5;

    /** How far a vex keeps fighting a target; also its {@code FOLLOW_RANGE}. */
    public static final double REACH = 7.25;

    private GuardArea() {}

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
