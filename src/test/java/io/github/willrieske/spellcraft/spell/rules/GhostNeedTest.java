package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GhostNeedTest {
    @Nested
    class InNeed {
        /**
         * When health is below half, or three hostiles are close, inNeed should be true; at exactly half with two
         * hostiles it should be false.
         *
         * <p>Method under test: {@link GhostNeed#inNeed(float, float, int)}
         */
        @ParameterizedTest
        @CsvSource({"9.9,20,0,true", "10,20,0,false", "20,20,2,false", "20,20,3,true", "20,20,9,true", "1,20,3,true"})
        void followsTheSpec(float health, float max, int hostiles, boolean expected) {
            assertThat(GhostNeed.inNeed(health, max, hostiles)).isEqualTo(expected);
        }

        /**
         * When the maximum health is not positive, or health or the hostile count is negative, inNeed should refuse.
         *
         * <p>Method under test: {@link GhostNeed#inNeed(float, float, int)}
         */
        @ParameterizedTest
        @CsvSource({"5,0,0", "5,-1,0", "-1,20,0", "5,20,-1"})
        void refusesNonsense(float health, float max, int hostiles) {
            assertThatThrownBy(() -> GhostNeed.inNeed(health, max, hostiles))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BelowHalf {
        /**
         * When health is below half of the maximum, belowHalf should be true; at exactly half, false.
         *
         * <p>Method under test: {@link GhostNeed#belowHalf(float, float)}
         */
        @ParameterizedTest
        @CsvSource({"9.9,20,true", "10,20,false", "0,20,true", "20,20,false"})
        void followsTheSpec(float health, float max, boolean expected) {
            assertThat(GhostNeed.belowHalf(health, max)).isEqualTo(expected);
        }

        /**
         * When the maximum health is not positive or health is negative, belowHalf should refuse.
         *
         * <p>Method under test: {@link GhostNeed#belowHalf(float, float)}
         */
        @ParameterizedTest
        @CsvSource({"5,0", "5,-1", "-1,20"})
        void refusesNonsense(float health, float max) {
            assertThatThrownBy(() -> GhostNeed.belowHalf(health, max)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BelowQuarter {
        /**
         * When health is below a quarter of the maximum, belowQuarter should be true; at exactly a quarter, false.
         *
         * <p>Method under test: {@link GhostNeed#belowQuarter(float, float)}
         */
        @ParameterizedTest
        @CsvSource({"4.9,20,true", "5,20,false", "0,20,true", "20,20,false"})
        void followsTheSpec(float health, float max, boolean expected) {
            assertThat(GhostNeed.belowQuarter(health, max)).isEqualTo(expected);
        }

        /**
         * When the maximum health is not positive or health is negative, belowQuarter should refuse.
         *
         * <p>Method under test: {@link GhostNeed#belowQuarter(float, float)}
         */
        @ParameterizedTest
        @CsvSource({"5,0", "5,-1", "-1,20"})
        void refusesNonsense(float health, float max) {
            assertThatThrownBy(() -> GhostNeed.belowQuarter(health, max)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * When the constants are read, they should be the spec's numbers: a crowd is three hostiles within 8 blocks.
     *
     * <p>Method under test: the constants of {@link GhostNeed}
     */
    @Test
    void constantsAreTheSpecsNumbers() {
        assertThat(GhostNeed.CROWD).isEqualTo(3);
        assertThat(GhostNeed.CROWD_RANGE).isEqualTo(8.0);
    }
}
