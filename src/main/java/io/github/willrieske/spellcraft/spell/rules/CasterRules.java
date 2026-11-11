package io.github.willrieske.spellcraft.spell.rules;

/** The Caster's numbers and tests (Parts 6-7 spec §6.2). */
public final class CasterRules {
    /** What the Caster does about its distance from its target. */
    public enum Approach {
        /** Closer than {@link #KEEP_MIN}: move away. */
        BACK_OFF,
        /** Farther than {@link #KEEP_MAX}: move closer. */
        CLOSE_IN,
        /** Between the two: stay. */
        HOLD
    }

    /** Nearest the Caster stays to its target. */
    public static final double KEEP_MIN = 4.0;

    /** Farthest the Caster stays from its target. */
    public static final double KEEP_MAX = 6.0;

    /** Ticks a cast takes. */
    public static final int WIND_UP_TICKS = 20;

    /** Ticks before the same Caster may cast Mirror again (6 minutes). */
    public static final int MIRROR_COOLDOWN_TICKS = 7200;

    /** Ticks before the same Caster may cast Ghostrail again (4 minutes). */
    public static final int GHOSTRAIL_COOLDOWN_TICKS = 4800;

    /** The summoner or sibling must be within this many blocks of the Caster. */
    public static final double RANGE = 16.0;

    /** A Mirror needs this many hostiles in the guard area. */
    public static final int MIRROR_HOSTILES = 2;

    private CasterRules() {}

    /**
     * What to do at a distance from the target: back off under 4, close in over 6, else hold.
     *
     * @param distance the distance to the target, in blocks
     * @return the approach
     */
    public static Approach approach(double distance) {
        if (distance < KEEP_MIN) {
            return Approach.BACK_OFF;
        }
        return distance > KEEP_MAX ? Approach.CLOSE_IN : Approach.HOLD;
    }

    /**
     * Whether {@code hostiles} in the guard area are enough for a Mirror.
     *
     * @param hostiles how many hostiles are in the guard area
     * @return true for {@link #MIRROR_HOSTILES} or more
     */
    public static boolean wantsMirror(int hostiles) {
        return hostiles >= MIRROR_HOSTILES;
    }

    /**
     * Whether a subject needs Ghostrail: below a quarter of its health, or (the summoner only) inside a wall.
     *
     * @param health the subject's health
     * @param maxHealth its maximum health
     * @param inWall whether it is inside a block
     * @param subjectIsSummoner true for the summoner, false for a vex
     * @return true if the Caster should cast Ghostrail on it
     * @throws IllegalArgumentException if {@code health} is negative or {@code maxHealth} is not positive
     */
    public static boolean wantsGhostrail(float health, float maxHealth, boolean inWall, boolean subjectIsSummoner) {
        return GhostNeed.belowQuarter(health, maxHealth) || (subjectIsSummoner && inWall);
    }

    /**
     * Whether a cooldown that ends at game time {@code readyAt} is over at {@code now}.
     *
     * @param now the game time
     * @param readyAt the game time the cooldown ends
     * @return true once {@code now} has reached {@code readyAt}
     */
    public static boolean ready(long now, long readyAt) {
        return now >= readyAt;
    }
}
