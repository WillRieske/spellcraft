package io.github.willrieske.spellcraft.spell.rules;

/** When a player is "in need" and when a spell subject is in an emergency (Parts 6-7 spec §6.2, §9). */
public final class GhostNeed {
    /** This many hostiles close by make a player in need. */
    public static final int CROWD = 3;

    /** How close, in blocks, a hostile counts toward the crowd. */
    public static final double CROWD_RANGE = 8.0;

    private GhostNeed() {}

    /**
     * Whether {@code health} is below half of {@code maxHealth}.
     *
     * @param health the current health, not negative
     * @param maxHealth the maximum health, positive
     * @return true when health is below half
     */
    public static boolean belowHalf(float health, float maxHealth) {
        check(health, maxHealth);
        return health * 2.0F < maxHealth;
    }

    /**
     * Whether {@code health} is below a quarter of {@code maxHealth}.
     *
     * @param health the current health, not negative
     * @param maxHealth the maximum health, positive
     * @return true when health is below a quarter
     */
    public static boolean belowQuarter(float health, float maxHealth) {
        check(health, maxHealth);
        return health * 4.0F < maxHealth;
    }

    /**
     * Whether a player is in need: below half health, or {@link #CROWD} or more hostiles within {@link #CROWD_RANGE}.
     *
     * @param health the player's health
     * @param maxHealth the player's maximum health, positive
     * @param hostiles how many hostiles (already filtered by the caller) are close
     * @return true when a ghost should come
     */
    public static boolean inNeed(float health, float maxHealth, int hostiles) {
        if (hostiles < 0) {
            throw new IllegalArgumentException("hostiles must not be negative: " + hostiles);
        }
        return belowHalf(health, maxHealth) || hostiles >= CROWD;
    }

    /** Refuses a health that is negative or a maximum that is not positive. */
    private static void check(float health, float maxHealth) {
        if (maxHealth <= 0.0F || health < 0.0F) {
            throw new IllegalArgumentException("health " + health + " of " + maxHealth + " is not a health");
        }
    }
}
