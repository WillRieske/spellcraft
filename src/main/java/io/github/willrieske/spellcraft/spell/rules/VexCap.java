package io.github.willrieske.spellcraft.spell.rules;

/**
 * How many Bound Vexes one V press summons (Evoking II spec §3.2): a batch of three while there is room for a whole
 * batch, the remainder up to the cap when there is room for less, and none at the cap.
 *
 * <p>Functional spec §5.1, §5.3.
 */
public final class VexCap {
    /** The most Bound Vexes one summoner may have alive at once. Functional spec §5.3. */
    public static final int CAP = 12;

    /** How many one press summons while they all fit. */
    public static final int BATCH = VexRing.SUMMON_COUNT;

    private VexCap() {}

    /**
     * How many Bound Vexes to summon for a summoner who has {@code count} alive.
     *
     * @param count the summoner's living Bound Vexes, in every loaded level
     * @return {@link #BATCH} while {@code count + BATCH ≤ CAP}, else {@code CAP − count}, else 0
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public static int toSummon(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be at least 0, got " + count);
        }
        return Math.max(0, Math.min(BATCH, CAP - count));
    }
}
