package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpellDeclarationsTest {
    @Test
    void declaresEverySpellWithItsTier() {
        assertThat(SpellDeclarations.ALL)
                .containsExactly(
                        SpellDeclarations.UPSIDE_DOWN,
                        SpellDeclarations.INFINITY,
                        SpellDeclarations.TRAILING,
                        SpellDeclarations.EVOKING);
        assertThat(SpellDeclarations.UPSIDE_DOWN.tier()).isEqualTo(SpellTier.STARTER);
        assertThat(SpellDeclarations.INFINITY.tier()).isEqualTo(SpellTier.EXTREME);
        assertThat(SpellDeclarations.TRAILING.tier()).isEqualTo(SpellTier.GOOD);
        assertThat(SpellDeclarations.INFINITY.tier().isTop()).isTrue();
        assertThat(SpellDeclarations.UPSIDE_DOWN.tier().isTop()).isFalse();
        assertThat(SpellDeclarations.TRAILING.tier().isTop()).isFalse();
        assertThat(SpellDeclarations.EVOKING.tier()).isEqualTo(SpellTier.AMAZING);
        assertThat(SpellDeclarations.EVOKING.tier().isTop()).isFalse();
    }

    @Test
    void trailingRefreshesIsDispellableAndCastsInCreative() {
        assertThat(SpellDeclarations.TRAILING.name()).isEqualTo("trailing");
        assertThat(SpellDeclarations.TRAILING.recast()).isEqualTo(SpellRules.Recast.REFRESH);
        assertThat(SpellDeclarations.TRAILING.dispellable()).isTrue();
        assertThat(SpellDeclarations.TRAILING.redundantInCreative()).isFalse();
    }

    /**
     * When the Evoking declaration is read, it should refuse a recast, be dispellable and not be redundant in
     * creative.
     *
     * <p>Method under test: {@link SpellDeclarations#EVOKING}
     */
    @Test
    void evokingRefusesARecastIsDispellableAndCastsInCreative() {
        assertThat(SpellDeclarations.EVOKING.name()).isEqualTo("evoking");
        assertThat(SpellDeclarations.EVOKING.recast()).isEqualTo(SpellRules.Recast.REFUSE);
        assertThat(SpellDeclarations.EVOKING.dispellable()).isTrue();
        assertThat(SpellDeclarations.EVOKING.redundantInCreative()).isFalse();
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
