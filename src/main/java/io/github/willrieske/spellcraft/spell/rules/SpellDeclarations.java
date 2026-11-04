package io.github.willrieske.spellcraft.spell.rules;

import java.util.List;

/** Every spell's business rules, in declaration order. Adding a spell starts here. */
public final class SpellDeclarations {
    /** Scroll of Upside-Down: starter tier, refreshes on recast, dispellable by sneaking. */
    public static final SpellRules UPSIDE_DOWN =
            new SpellRules("upside_down", SpellTier.STARTER, SpellRules.Recast.REFRESH, true, false);

    /** Scroll of Infinity: extreme tier, refuses a recast, redundant in creative. */
    public static final SpellRules INFINITY =
            new SpellRules("infinity", SpellTier.EXTREME, SpellRules.Recast.REFUSE, false, true);

    /** Scroll of Trailing: good tier, refreshes on recast, dispellable by sneaking, cast in creative too. */
    public static final SpellRules TRAILING =
            new SpellRules("trailing", SpellTier.GOOD, SpellRules.Recast.REFRESH, true, false);

    /** Every declaration, in declaration order. */
    public static final List<SpellRules> ALL = List.of(UPSIDE_DOWN, INFINITY, TRAILING);

    private SpellDeclarations() {}
}
