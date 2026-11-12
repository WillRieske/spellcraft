package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GhostrailDurationTest {
    @Nested
    class Of {
        /**
         * When called for each kind of carrier, of should answer 4800 for players and Bound Vexes, 40 for a hostile
         * mob and the infinite sentinel for a villager ghost.
         *
         * <p>Method under test: {@link GhostrailDuration#of(GhostrailDuration.Kind)}
         */
        @ParameterizedTest
        @CsvSource({"PLAYER,4800", "BOUND_VEX,4800", "HOSTILE_MOB,40", "VILLAGER_GHOST,-1"})
        void followsTheSpec(GhostrailDuration.Kind kind, int expected) {
            assertThat(GhostrailDuration.of(kind)).isEqualTo(expected);
        }
    }

    /**
     * When the constants are read, they should be the spec's numbers: 4800 ticks for a player, 40 for a hostile mob.
     *
     * <p>Method under test: the constants of {@link GhostrailDuration}
     */
    @Test
    void constantsAreTheSpecsNumbers() {
        assertThat(GhostrailDuration.PLAYER_TICKS).isEqualTo(4800);
        assertThat(GhostrailDuration.HOSTILE_TICKS).isEqualTo(40);
    }
}
