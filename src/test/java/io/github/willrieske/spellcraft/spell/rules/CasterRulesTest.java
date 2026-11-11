package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CasterRulesTest {
    @Nested
    class ApproachTo {
        /**
         * When the distance to the target is under 4, 4 to 6 or over 6, approach should answer back off, hold and
         * close in; the edges 4 and 6 hold.
         *
         * <p>Method under test: {@link CasterRules#approach(double)}
         */
        @ParameterizedTest
        @CsvSource({"0,BACK_OFF", "3.99,BACK_OFF", "4,HOLD", "5,HOLD", "6,HOLD", "6.01,CLOSE_IN", "30,CLOSE_IN"})
        void followsTheSpec(double distance, CasterRules.Approach expected) {
            assertThat(CasterRules.approach(distance)).isEqualTo(expected);
        }
    }

    @Nested
    class WantsMirror {
        /**
         * When two or more hostiles are in the guard area, wantsMirror should be true; with one or none, false.
         *
         * <p>Method under test: {@link CasterRules#wantsMirror(int)}
         */
        @ParameterizedTest
        @CsvSource({"0,false", "1,false", "2,true", "9,true"})
        void needsTwo(int hostiles, boolean expected) {
            assertThat(CasterRules.wantsMirror(hostiles)).isEqualTo(expected);
        }
    }

    @Nested
    class WantsGhostrail {
        /**
         * When the subject is below a quarter of its health, wantsGhostrail should be true for anyone; in a wall, only
         * for the summoner (a vex clips through blocks all the time).
         *
         * <p>Method under test: {@link CasterRules#wantsGhostrail(float, float, boolean, boolean)}
         */
        @ParameterizedTest
        @CsvSource({
            "4,20,false,false,true",
            "4,20,false,true,true",
            "5,20,false,true,false",
            "20,20,true,true,true",
            "20,20,true,false,false",
            "20,20,false,true,false"
        })
        void followsTheSpec(float health, float max, boolean inWall, boolean summoner, boolean expected) {
            assertThat(CasterRules.wantsGhostrail(health, max, inWall, summoner))
                    .isEqualTo(expected);
        }
    }

    @Nested
    class Ready {
        /**
         * When the game time has reached the ready time, ready should be true; one tick before it, false.
         *
         * <p>Method under test: {@link CasterRules#ready(long, long)}
         */
        @ParameterizedTest
        @CsvSource({"99,100,false", "100,100,true", "101,100,true", "0,0,true"})
        void comparesGameTimes(long now, long readyAt, boolean expected) {
            assertThat(CasterRules.ready(now, readyAt)).isEqualTo(expected);
        }
    }

    /**
     * When the constants are read, they should be the spec's numbers.
     *
     * <p>Method under test: the constants of {@link CasterRules}
     */
    @Test
    void constantsAreTheSpecsNumbers() {
        assertThat(CasterRules.KEEP_MIN).isEqualTo(4.0);
        assertThat(CasterRules.KEEP_MAX).isEqualTo(6.0);
        assertThat(CasterRules.WIND_UP_TICKS).isEqualTo(20);
        assertThat(CasterRules.MIRROR_COOLDOWN_TICKS).isEqualTo(7200);
        assertThat(CasterRules.GHOSTRAIL_COOLDOWN_TICKS).isEqualTo(4800);
        assertThat(CasterRules.RANGE).isEqualTo(16.0);
        assertThat(CasterRules.MIRROR_HOSTILES).isEqualTo(2);
    }
}
