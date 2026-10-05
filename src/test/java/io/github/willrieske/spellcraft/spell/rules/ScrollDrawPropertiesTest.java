package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.ScrollDraw.Candidate;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class ScrollDrawPropertiesTest {
    private static List<Candidate<Integer>> candidates(List<SpellTier> tiers) {
        return IntStream.range(0, tiers.size())
                .mapToObj(i -> new Candidate<>(i, tiers.get(i)))
                .toList();
    }

    @Property
    void theResultIsAlwaysADeclaredSpell(
            @ForAll @Size(min = 1, max = 8) List<SpellTier> tiers,
            @ForAll @IntRange(min = 0, max = 2) int option,
            @ForAll @IntRange(min = 0, max = 100) int chance,
            @ForAll long seed) {
        int result = ScrollDraw.draw(
                option, candidates(tiers), List.of(chance, chance, chance), new SplittableRandom(seed)::nextInt);
        assertThat(result).isBetween(0, tiers.size() - 1);
    }

    @Property
    void zeroPercentDrawsTheTopTierOnlyWhenNothingElseExists(
            @ForAll @Size(min = 1, max = 8) List<SpellTier> tiers,
            @ForAll @IntRange(min = 0, max = 2) int option,
            @ForAll long seed) {
        int result = ScrollDraw.draw(option, candidates(tiers), List.of(0, 0, 0), new SplittableRandom(seed)::nextInt);
        boolean onlyTop = tiers.stream().allMatch(SpellTier::isTop);
        assertThat(tiers.get(result).isTop()).isEqualTo(onlyTop);
    }

    @Property
    void hundredPercentDrawsTheTopTierWheneverItExists(
            @ForAll @Size(min = 1, max = 8) List<SpellTier> tiers,
            @ForAll @IntRange(min = 0, max = 2) int option,
            @ForAll long seed) {
        int result =
                ScrollDraw.draw(option, candidates(tiers), List.of(100, 100, 100), new SplittableRandom(seed)::nextInt);
        boolean anyTop = tiers.stream().anyMatch(SpellTier::isTop);
        assertThat(tiers.get(result).isTop()).isEqualTo(anyTop);
    }
}
