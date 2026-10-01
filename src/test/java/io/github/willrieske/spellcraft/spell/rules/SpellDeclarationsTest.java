package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpellDeclarationsTest {
    @Test
    void declaresBothSpellsWithTheirTiers() {
        assertThat(SpellDeclarations.ALL).containsExactly(SpellDeclarations.UPSIDE_DOWN, SpellDeclarations.INFINITY);
        assertThat(SpellDeclarations.UPSIDE_DOWN.tier()).isEqualTo(SpellTier.STARTER);
        assertThat(SpellDeclarations.INFINITY.tier()).isEqualTo(SpellTier.EXTREME);
        assertThat(SpellDeclarations.INFINITY.tier().isTop()).isTrue();
        assertThat(SpellDeclarations.UPSIDE_DOWN.tier().isTop()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Upside", "up side", "up-side", "a/b", "ünï"})
    void rejectsNamesThatAreNotResourcePaths(String name) {
        assertThatThrownBy(() -> new SpellRules(name, SpellTier.STARTER, SpellRules.Recast.REFRESH, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void acceptsLowercaseDigitsAndUnderscores() {
        assertThat(new SpellRules("a_1", SpellTier.STARTER, SpellRules.Recast.REFRESH, true, false).name())
                .isEqualTo("a_1");
    }

    @Test
    void namesAreUniqueResourcePaths() {
        assertThat(SpellDeclarations.ALL).extracting(SpellRules::name).doesNotHaveDuplicates();
        assertThat(SpellDeclarations.ALL)
                .allSatisfy(rules -> assertThat(rules.name()).matches("[a-z0-9_]+"));
    }
}
