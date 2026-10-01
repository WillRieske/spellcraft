package io.github.willrieske.spellcraft.spell.rules;

/** A spell's power band. Scribing draws the top tier with a chance that grows with the lapis spent. */
public enum SpellTier {
    NOOB,
    STARTER,
    GOOD,
    AMAZING,
    EXTREME;

    /** The tier scribing's top-tier roll draws from. */
    public static final SpellTier TOP = EXTREME;

    /** Whether this is the tier scribing's top-tier roll draws from. */
    public boolean isTop() {
        return this == TOP;
    }
}
