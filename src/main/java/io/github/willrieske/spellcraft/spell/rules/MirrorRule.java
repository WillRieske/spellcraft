package io.github.willrieske.spellcraft.spell.rules;

/** The numbers of Mirror (Parts 6-7 spec §7.2). */
public final class MirrorRule {
    /** Images a Mirror makes. */
    public static final int IMAGES = 13;

    /** Sides of the confusion die: a roll of 0 to 13. */
    public static final int CONFUSION_BOUND = 14;

    /** Ticks an image lives on its own (6 minutes). */
    public static final int LIFE_TICKS = 7200;

    /** Ticks a Mirror effect lasts (6 minutes). */
    public static final int DURATION_TICKS = 7200;

    /** How far from its carrier an image wanders. */
    public static final double WANDER_RADIUS = 6.0;

    /** A mob picking a carrier is confused only by images this close to the carrier. */
    public static final double CONFUSION_RANGE = 16.0;

    private MirrorRule() {}

    /**
     * Whether a confusion roll sends the mob to an image: 13 of the 14 rolls.
     *
     * @param roll a roll of the confusion die, 0 to {@link #CONFUSION_BOUND} - 1
     * @return true if the new target is replaced by an image
     * @throws IllegalArgumentException if {@code roll} is negative or at least {@link #CONFUSION_BOUND}
     */
    public static boolean redirects(int roll) {
        if (roll < 0 || roll >= CONFUSION_BOUND) {
            throw new IllegalArgumentException("roll must be 0-" + (CONFUSION_BOUND - 1) + ", got " + roll);
        }
        return roll < IMAGES;
    }
}
