package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class CastRulesPropertiesTest {
    @Property
    void neverDispelsAnUndispellableSpell(
            @ForAll SpellTier tier,
            @ForAll SpellRules.Recast recast,
            @ForAll boolean redundant,
            @ForAll boolean sneaking,
            @ForAll boolean active,
            @ForAll PlayerMode mode) {
        SpellRules rules = new SpellRules("any", tier, recast, false, redundant);
        assertThat(CastRules.decide(rules, new Caster(sneaking, active, mode))).isNotEqualTo(CastOutcome.DISPEL);
    }

    @Property
    void neverRefreshesARefuseOnRecastSpell(
            @ForAll SpellTier tier,
            @ForAll boolean dispellable,
            @ForAll boolean redundant,
            @ForAll boolean sneaking,
            @ForAll boolean active,
            @ForAll PlayerMode mode) {
        SpellRules rules = new SpellRules("any", tier, SpellRules.Recast.REFUSE, dispellable, redundant);
        assertThat(CastRules.decide(rules, new Caster(sneaking, active, mode))).isNotEqualTo(CastOutcome.REFRESH);
    }

    @Property
    void anInactiveSpellCastsUnlessRedundantInCreative(
            @ForAll SpellTier tier,
            @ForAll SpellRules.Recast recast,
            @ForAll boolean dispellable,
            @ForAll boolean redundant,
            @ForAll boolean sneaking,
            @ForAll PlayerMode mode) {
        SpellRules rules = new SpellRules("any", tier, recast, dispellable, redundant);
        CastOutcome expected = redundant && mode == PlayerMode.CREATIVE ? CastOutcome.REFUSE : CastOutcome.CAST;
        assertThat(CastRules.decide(rules, new Caster(sneaking, false, mode))).isEqualTo(expected);
    }

    @Property
    void creativeRedundancyBeatsDispel(
            @ForAll SpellTier tier, @ForAll SpellRules.Recast recast, @ForAll boolean sneaking) {
        SpellRules rules = new SpellRules("any", tier, recast, true, true);
        assertThat(CastRules.decide(rules, new Caster(sneaking, true, PlayerMode.CREATIVE)))
                .isEqualTo(CastOutcome.REFUSE);
    }

    @Property
    void aRefreshSpellIsNeverRefusedOutsideCreativeRedundancy(
            @ForAll SpellTier tier,
            @ForAll boolean dispellable,
            @ForAll boolean sneaking,
            @ForAll boolean active,
            @ForAll PlayerMode mode) {
        SpellRules rules = new SpellRules("any", tier, SpellRules.Recast.REFRESH, dispellable, false);
        assertThat(CastRules.decide(rules, new Caster(sneaking, active, mode))).isNotEqualTo(CastOutcome.REFUSE);
    }
}
