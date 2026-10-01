package io.github.willrieske.spellcraft.spell.rules;

/** The one decision every scroll use goes through. Runs on both sides; only the server acts on it. */
public final class CastRules {
    private CastRules() {}

    /**
     * Decides what a scroll use does, applying the spec's rules in priority order (first match wins):
     * creative-redundant refusal, sneak-dispel, refuse-on-recast, refresh, cast.
     *
     * @param rules the spell's business rules
     * @param caster the player's current sneak, effect and mode state
     * @return the outcome to apply and the feedback to give
     */
    public static CastOutcome decide(SpellRules rules, Caster caster) {
        if (caster.mode() == PlayerMode.CREATIVE && rules.redundantInCreative()) {
            return CastOutcome.REFUSE;
        }
        if (caster.effectActive()) {
            if (caster.sneaking() && rules.dispellable()) {
                return CastOutcome.DISPEL;
            }
            return rules.recast() == SpellRules.Recast.REFUSE ? CastOutcome.REFUSE : CastOutcome.REFRESH;
        }
        return CastOutcome.CAST;
    }
}
