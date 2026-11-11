package io.github.willrieske.spellcraft.spell.rules;

/** How long a Ghostrail lasts for each kind of carrier (Parts 6-7 spec §7.3, "Durations"). */
public final class GhostrailDuration {
    /** What kind of thing carries the effect. */
    public enum Kind {
        /** A player (4 minutes). */
        PLAYER,
        /** A Bound Vex (4 minutes). */
        BOUND_VEX,
        /** A hostile mob that cast it on itself (2 seconds). */
        HOSTILE_MOB,
        /** A villager ghost (until the ghost is gone). */
        VILLAGER_GHOST
    }

    /** Ticks for a player or a Bound Vex (4 minutes). */
    public static final int PLAYER_TICKS = 4800;

    /** Ticks for a hostile mob that cast it on itself (2 seconds). */
    public static final int HOSTILE_TICKS = 40;

    /** The never-ending duration; the same number as {@code MobEffectInstance.INFINITE_DURATION}. */
    public static final int INFINITE = -1;

    private GhostrailDuration() {}

    /**
     * The duration for a carrier.
     *
     * @param kind who carries the effect
     * @return ticks, or {@link #INFINITE}
     */
    public static int of(Kind kind) {
        return switch (kind) {
            case PLAYER, BOUND_VEX -> PLAYER_TICKS;
            case HOSTILE_MOB -> HOSTILE_TICKS;
            case VILLAGER_GHOST -> INFINITE;
        };
    }
}
