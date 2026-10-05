package io.github.willrieske.spellcraft.spell.rules;

/** What using a scroll does. Every outcome except {@link #REFUSE} uses the scroll up. */
public enum CastOutcome {
    CAST,
    REFRESH,
    DISPEL,
    REFUSE;

    /** Whether the scroll is used up by this outcome. */
    public boolean consumesScroll() {
        return this != REFUSE;
    }
}
