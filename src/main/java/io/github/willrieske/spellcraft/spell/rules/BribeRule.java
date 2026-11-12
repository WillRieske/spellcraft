package io.github.willrieske.spellcraft.spell.rules;

/**
 * The odds that a vanilla vex takes a Bound Vex's emerald (Parts 4-5 spec §3.1, §9): a three-sided roll, two sides
 * accept and one refuses, so the bribe works two times in three. Will asked for "1/1.5", which is 2/3. Pure: the roll
 * comes from the caller's {@link ScrollDraw.Dice}, the same small interface Scribing's draw uses, so a GameTest can
 * script it.
 *
 * <p>Functional spec §9.6.
 */
public final class BribeRule {
    /** The number of sides on the roll: the bribe is accepted on {@code 0} and {@code 1}, refused on {@code 2}. */
    public static final int SIDES = 3;

    /** The first side that refuses. Every side below it accepts. */
    private static final int FIRST_REFUSAL = 2;

    private BribeRule() {}

    /**
     * Whether a roll accepts the bribe.
     *
     * @param roll a side, {@code 0} to {@code SIDES - 1}
     * @return true for {@code 0} and {@code 1}; false for {@code 2}
     * @throws IllegalArgumentException if {@code roll} is outside {@code 0..SIDES-1}
     */
    public static boolean accepts(int roll) {
        if (roll < 0 || roll >= SIDES) {
            throw new IllegalArgumentException("bribe roll must be 0-" + (SIDES - 1) + ", got " + roll);
        }
        return roll < FIRST_REFUSAL;
    }

    /**
     * Rolls once and answers whether the bribe is accepted.
     *
     * @param dice the source of rolls; asked once for a value in {@code [0, SIDES)}
     * @return true two times in three
     * @throws IllegalArgumentException if the dice answer outside {@code [0, SIDES)}
     */
    public static boolean roll(ScrollDraw.Dice dice) {
        return accepts(dice.below(SIDES));
    }
}
