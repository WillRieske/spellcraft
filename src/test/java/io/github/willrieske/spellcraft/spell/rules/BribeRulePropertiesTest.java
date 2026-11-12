package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Functional spec §9.6. */
class BribeRulePropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Accepts {
        /**
         * Over the whole range of sides, exactly two should accept: the odds are 2 in 3.
         *
         * <p>Method under test: {@link BribeRule#accepts(int)}
         */
        @Property
        void exactlyTwoOfTheThreeSidesAccept() {
            // Act
            long accepting = IntStream.range(0, BribeRule.SIDES)
                    .filter(BribeRule::accepts)
                    .count();
            // Assert
            assertThat(accepting).isEqualTo(2);
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Roll {
        /**
         * For any dice that stays in range, roll should never throw and should agree with accepts on the side the dice
         * showed.
         *
         * <p>Method under test: {@link BribeRule#roll(ScrollDraw.Dice)}
         */
        @Property
        void neverThrowsForADiceInRangeAndAgreesWithAccepts(
                @ForAll @IntRange(min = 0, max = BribeRule.SIDES - 1) int side) {
            // Arrange
            ScrollDraw.Dice dice = bound -> side % bound;
            // Act
            boolean result = BribeRule.roll(dice);
            // Assert
            assertThat(result).isEqualTo(BribeRule.accepts(side));
        }
    }
}
