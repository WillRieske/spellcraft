package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.willrieske.spellcraft.spell.rules.ScrollDraw.Candidate;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScrollDrawTest {
    private static final List<Integer> DEFAULT_CHANCES = List.of(25, 50, 75);
    private static final List<Candidate<String>> BOTH =
            List.of(new Candidate<>("upside_down", SpellTier.STARTER), new Candidate<>("infinity", SpellTier.EXTREME));

    @ParameterizedTest(name = "option {0}, roll {1} -> {2}")
    @CsvSource({
        "0, 24, infinity",
        "0, 25, upside_down",
        "1, 49, infinity",
        "1, 50, upside_down",
        "2, 74, infinity",
        "2, 75, upside_down",
    })
    void theRollAgainstTheOptionsChancePicksThePool(int option, int roll, String expected) {
        ScriptedDice dice = new ScriptedDice(roll, 0);
        assertThat(ScrollDraw.draw(option, BOTH, DEFAULT_CHANCES, dice)).isEqualTo(expected);
        assertThat(dice.exhausted()).isTrue();
    }

    @Test
    void zeroPercentNeverDrawsTheTopTier() {
        assertThat(ScrollDraw.draw(2, BOTH, List.of(0, 0, 0), new ScriptedDice(0, 0)))
                .isEqualTo("upside_down");
    }

    @Test
    void hundredPercentAlwaysDrawsTheTopTier() {
        assertThat(ScrollDraw.draw(0, BOTH, List.of(100, 100, 100), new ScriptedDice(99, 0)))
                .isEqualTo("infinity");
    }

    @Test
    void anEmptyTopPoolFallsBackToTheRest() {
        List<Candidate<String>> starters = List.of(new Candidate<>("upside_down", SpellTier.STARTER));
        assertThat(ScrollDraw.draw(0, starters, DEFAULT_CHANCES, new ScriptedDice(0, 0)))
                .isEqualTo("upside_down");
    }

    @Test
    void anEmptyLowerPoolFallsBackToTheTop() {
        List<Candidate<String>> extremes = List.of(new Candidate<>("infinity", SpellTier.EXTREME));
        assertThat(ScrollDraw.draw(0, extremes, DEFAULT_CHANCES, new ScriptedDice(99, 0)))
                .isEqualTo("infinity");
    }

    @Test
    void picksWithinThePoolByTheSecondRoll() {
        List<Candidate<String>> threeLowerOneTop = List.of(
                new Candidate<>("a", SpellTier.NOOB),
                new Candidate<>("b", SpellTier.GOOD),
                new Candidate<>("c", SpellTier.AMAZING),
                new Candidate<>("top", SpellTier.EXTREME));
        assertThat(ScrollDraw.draw(0, threeLowerOneTop, DEFAULT_CHANCES, new ScriptedDice(99, 2)))
                .isEqualTo("c");
    }

    @Test
    void rejectsBadInput() {
        ScriptedDice dice = new ScriptedDice(0, 0);
        assertThatThrownBy(() -> ScrollDraw.draw(0, List.<Candidate<String>>of(), DEFAULT_CHANCES, dice))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScrollDraw.draw(-1, BOTH, DEFAULT_CHANCES, dice))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScrollDraw.draw(3, BOTH, DEFAULT_CHANCES, dice))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScrollDraw.draw(0, BOTH, List.of(25, 50), dice))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScrollDraw.draw(0, BOTH, List.of(25, 50, 101), dice))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScrollDraw.draw(0, BOTH, List.of(-1, 50, 75), dice))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Chi-square over 10,000 draws from one pool of 4, fixed seed (not flaky): 3 df, p = 0.001 → 16.27. */
    @Test
    void picksEvenlyWithinAPool() {
        List<Candidate<Integer>> pool = List.of(
                new Candidate<>(0, SpellTier.STARTER),
                new Candidate<>(1, SpellTier.STARTER),
                new Candidate<>(2, SpellTier.STARTER),
                new Candidate<>(3, SpellTier.STARTER));
        SplittableRandom random = new SplittableRandom(42);
        int draws = 10_000;
        int[] counts = new int[pool.size()];
        for (int i = 0; i < draws; i++) {
            counts[ScrollDraw.draw(0, pool, List.of(0, 0, 0), random::nextInt)]++;
        }
        double expected = (double) draws / pool.size();
        double chiSquare = 0;
        for (int count : counts) {
            chiSquare += (count - expected) * (count - expected) / expected;
        }
        assertThat(chiSquare).isLessThan(16.27);
    }

    @Test
    void theTopShareMatchesTheOptionsChance() {
        SplittableRandom random = new SplittableRandom(7);
        int draws = 10_000;
        int top = 0;
        for (int i = 0; i < draws; i++) {
            if (ScrollDraw.draw(0, BOTH, DEFAULT_CHANCES, random::nextInt).equals("infinity")) {
                top++;
            }
        }
        assertThat((double) top / draws).isBetween(0.23, 0.27);
    }
}
