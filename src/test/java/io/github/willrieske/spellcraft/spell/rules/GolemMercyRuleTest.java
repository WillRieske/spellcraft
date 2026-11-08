package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GolemMercyRuleTest {
    @Nested
    class Clamp {
        /**
         * When the hit would leave the golem at 25 or more, clamp should let it through whole.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @ParameterizedTest(name = "health {0}, damage {1} -> {2}")
        @CsvSource({"100, 6, 6", "31, 6, 6", "30, 5, 5", "30, 0, 0"})
        void aHitThatLeavesTwentyFiveOrMoreLandsWhole(float health, float damage, float expected) {
            assertThat(GolemMercyRule.clamp(health, damage)).isEqualTo(expected);
        }

        /**
         * When the hit would take the golem under 25, clamp should cut it down to what leaves exactly 25.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @ParameterizedTest(name = "health {0}, damage {1} -> {2}")
        @CsvSource({"30, 6, 5", "26, 100, 1", "100, 80, 75"})
        void aHitThatWouldGoUnderTwentyFiveStopsThere(float health, float damage, float expected) {
            assertThat(GolemMercyRule.clamp(health, damage)).isEqualTo(expected);
        }

        /**
         * When the golem is at or under 25, clamp should answer 0 whatever the damage; one float step above 25 it
         * should answer that step.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @Test
        void atOrUnderTwentyFiveNothingLands() {
            // Act & Assert
            assertThat(GolemMercyRule.clamp(25, 6)).isZero();
            assertThat(GolemMercyRule.clamp(20, 6)).isZero();
            assertThat(GolemMercyRule.clamp(0, 6)).isZero();
            assertThat(GolemMercyRule.clamp(-5, 6)).isZero();
            float justAbove = Math.nextUp(25.0F);
            assertThat(GolemMercyRule.clamp(justAbove, 6)).isEqualTo(justAbove - 25.0F);
        }

        /**
         * When the damage is negative, clamp should answer 0, never a heal.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @Test
        void negativeDamageBecomesZero() {
            assertThat(GolemMercyRule.clamp(50, -3)).isZero();
        }

        /**
         * When health or damage is NaN or infinite, clamp should still answer a finite, non-negative value: NaN means
         * nothing lands, infinite damage lands the whole room, infinite health leaves every hit whole.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @ParameterizedTest(name = "health {0}, damage {1} -> {2}")
        @CsvSource({
            "NaN, 6, 0",
            "50, NaN, 0",
            "50, Infinity, 25",
            "50, -Infinity, 0",
            "Infinity, 6, 6",
            "-Infinity, 6, 0"
        })
        void nonFiniteInputsNeverThrow(float health, float damage, float expected) {
            assertThat(GolemMercyRule.clamp(health, damage)).isEqualTo(expected);
        }
    }

    @Nested
    class Forgives {
        /**
         * When the golem's health is at or under 25, forgives should answer true; just above, false.
         *
         * <p>Method under test: {@link GolemMercyRule#forgives(float)}
         */
        @ParameterizedTest(name = "health {0} -> {1}")
        @CsvSource({"25, true", "24.5, true", "0, true", "25.5, false", "100, false", "NaN, false"})
        void forgivesAtOrUnderTwentyFive(float health, boolean expected) {
            assertThat(GolemMercyRule.forgives(health)).isEqualTo(expected);
        }

        /**
         * When the health is one float step above 25, forgives should answer false.
         *
         * <p>Method under test: {@link GolemMercyRule#forgives(float)}
         */
        @Test
        void oneStepAboveTwentyFiveIsNotForgiven() {
            assertThat(GolemMercyRule.forgives(Math.nextUp(25.0F))).isFalse();
        }
    }
}
