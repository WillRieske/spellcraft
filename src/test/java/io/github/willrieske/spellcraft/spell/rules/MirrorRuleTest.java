package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Functional spec §13.1, §13.2, §13.3, §13.4, §13.6. */
class MirrorRuleTest {
    @Nested
    class Redirects {
        /**
         * When the roll is 0 to 12, redirects should be true (13 of the 14 rolls); at 13 it should be false.
         *
         * <p>Method under test: {@link MirrorRule#redirects(int)}
         */
        @ParameterizedTest
        @CsvSource({"0,true", "6,true", "12,true", "13,false"})
        void is13Of14(int roll, boolean expected) {
            assertThat(MirrorRule.redirects(roll)).isEqualTo(expected);
        }

        /**
         * When the roll is outside 0 to 13, redirects should refuse.
         *
         * <p>Method under test: {@link MirrorRule#redirects(int)}
         */
        @ParameterizedTest
        @ValueSource(ints = {-1, 14, 100, Integer.MIN_VALUE})
        void refusesOutOfRange(int roll) {
            assertThatThrownBy(() -> MirrorRule.redirects(roll)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * When the constants are read, they should be the spec's numbers: 13 images, a 7200-tick life and effect, a
     * 14-sided die.
     *
     * <p>Method under test: the constants of {@link MirrorRule}
     */
    @Test
    void constantsAreTheSpecsNumbers() {
        assertThat(MirrorRule.IMAGES).isEqualTo(13);
        assertThat(MirrorRule.CONFUSION_BOUND).isEqualTo(14);
        assertThat(MirrorRule.LIFE_TICKS).isEqualTo(7200);
        assertThat(MirrorRule.DURATION_TICKS).isEqualTo(7200);
        assertThat(MirrorRule.WANDER_RADIUS).isEqualTo(6.0);
        assertThat(MirrorRule.CONFUSION_RANGE).isEqualTo(16.0);
    }
}
