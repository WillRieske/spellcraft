package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BribeRuleTest {
    @Nested
    class Accepts {
        /**
         * When the roll is 0 or 1, accepts should answer true (two of the three sides); when it is 2, false.
         *
         * <p>Method under test: {@link BribeRule#accepts(int)}
         */
        @ParameterizedTest(name = "roll {0} accepted = {1}")
        @CsvSource({"0, true", "1, true", "2, false"})
        void twoSidesInThreeAccept(int roll, boolean accepted) {
            // Act & Assert
            assertThat(BribeRule.accepts(roll)).isEqualTo(accepted);
        }

        /**
         * When the roll is below 0 or at {@link BribeRule#SIDES} or more, accepts should throw: such a roll is a
         * caller bug, not a refusal.
         *
         * <p>Method under test: {@link BribeRule#accepts(int)}
         */
        @ParameterizedTest(name = "roll {0} is rejected")
        @CsvSource({"-1", "3", "4", "-2147483648", "2147483647"})
        void aRollOutsideTheSidesThrows(int roll) {
            // Act & Assert
            assertThatThrownBy(() -> BribeRule.accepts(roll))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(Integer.toString(roll));
        }

        /**
         * When the rule is read, should report three sides.
         *
         * <p>Method under test: {@link BribeRule#SIDES}
         */
        @Test
        void hasThreeSides() {
            // Act & Assert
            assertThat(BribeRule.SIDES).isEqualTo(3);
        }
    }

    @Nested
    class Roll {
        /**
         * When the dice show 0 or 1, roll should answer true; when they show 2, false.
         *
         * <p>Method under test: {@link BribeRule#roll(ScrollDraw.Dice)}
         */
        @ParameterizedTest(name = "dice show {0}: accepted = {1}")
        @CsvSource({"0, true", "1, true", "2, false"})
        void followsTheSideTheDiceShow(int side, boolean accepted) {
            // Arrange
            ScriptedDice dice = new ScriptedDice(side);
            // Act
            boolean result = BribeRule.roll(dice);
            // Assert
            assertThat(result).isEqualTo(accepted);
        }

        /**
         * When rolling, should draw exactly once and ask for {@link BribeRule#SIDES} sides.
         *
         * <p>Method under test: {@link BribeRule#roll(ScrollDraw.Dice)}
         */
        @Test
        void drawsOnceAndAsksForThreeSides() {
            // Arrange
            List<Integer> bounds = new ArrayList<>();
            ScrollDraw.Dice dice = bound -> {
                bounds.add(bound);
                return 1;
            };
            // Act
            BribeRule.roll(dice);
            // Assert
            assertThat(bounds).containsExactly(BribeRule.SIDES);
        }

        /**
         * When the dice answer outside {@code [0, SIDES)}, roll should throw instead of treating it as a refusal.
         *
         * <p>Method under test: {@link BribeRule#roll(ScrollDraw.Dice)}
         */
        @Test
        void aBrokenDiceThrows() {
            // Arrange
            ScrollDraw.Dice broken = bound -> bound;
            // Act & Assert
            assertThatThrownBy(() -> BribeRule.roll(broken)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
