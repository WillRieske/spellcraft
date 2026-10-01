package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpellDeclarationsTest {
    @Test
    void declaresBothSpellsWithTheirTiers() {
        assertThat(SpellDeclarations.ALL).containsExactly(SpellDeclarations.UPSIDE_DOWN, SpellDeclarations.INFINITY);
        assertThat(SpellDeclarations.UPSIDE_DOWN.tier()).isEqualTo(SpellTier.STARTER);
        assertThat(SpellDeclarations.INFINITY.tier()).isEqualTo(SpellTier.EXTREME);
        assertThat(SpellDeclarations.INFINITY.tier().isTop()).isTrue();
        assertThat(SpellDeclarations.UPSIDE_DOWN.tier().isTop()).isFalse();
    }

    @Test
    void namesAreUniqueResourcePaths() {
        assertThat(SpellDeclarations.ALL).extracting(SpellRules::name).doesNotHaveDuplicates();
        assertThat(SpellDeclarations.ALL)
                .allSatisfy(rules -> assertThat(rules.name()).matches("[a-z0-9_]+"));
    }
}
