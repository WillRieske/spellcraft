package io.github.willrieske.spellcraft.spell.rules;

import java.util.List;

/**
 * Every spell's business rules, in declaration order. Adding a spell starts here.
 *
 * <p>Functional spec §1.2, §1.3, §13.1, §14.1.
 */
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

    /**
     * Scroll of Evoking: amazing tier, refuses a recast (no duration to refresh), dispellable by sneaking.
     * Functional spec §1.2, §1.3.
     */
    public static final SpellRules EVOKING =
            new SpellRules("evoking", SpellTier.AMAZING, SpellRules.Recast.REFUSE, true, false);

    /**
     * Scroll of Mirror: amazing tier, refuses a recast (the images are one set), dispellable, cast in creative too.
     * Functional spec §13.1.
     */
    public static final SpellRules MIRROR =
            new SpellRules("mirror", SpellTier.AMAZING, SpellRules.Recast.REFUSE, true, false);

    /**
     * Scroll of Ghostrail: extreme tier, refuses a recast, dispellable, redundant in creative (creative flies).
     * Functional spec §14.1.
     */
    public static final SpellRules GHOSTRAIL =
            new SpellRules("ghostrail", SpellTier.EXTREME, SpellRules.Recast.REFUSE, true, true);

    /** Every declaration, in declaration order. */
    public static final List<SpellRules> ALL = List.of(UPSIDE_DOWN, INFINITY, TRAILING, EVOKING, MIRROR, GHOSTRAIL);

    private SpellDeclarations() {}
}
