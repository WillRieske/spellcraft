package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EvokerSpellTest {
    @Nested
    class CastingTicks {
        /**
         * When asked how long the arms stay up, castingTicks should answer the evoker's values: 40 for fangs and 100
         * for vexes.
         *
         * <p>Method under test: {@link EvokerSpell#castingTicks()}
         */
        @Test
        void matchesTheEvoker() {
            // Act & Assert
            assertThat(EvokerSpell.FANGS.castingTicks()).isEqualTo(40);
            assertThat(EvokerSpell.VEXES.castingTicks()).isEqualTo(100);
        }
    }

    @Nested
    class IntervalTicks {
        /**
         * When asked how long until the same spell can be cast again, intervalTicks should answer the evoker's
         * values: 100 for fangs and 340 for vexes.
         *
         * <p>Method under test: {@link EvokerSpell#intervalTicks()}
         */
        @Test
        void matchesTheEvoker() {
            // Act & Assert
            assertThat(EvokerSpell.FANGS.intervalTicks()).isEqualTo(100);
            assertThat(EvokerSpell.VEXES.intervalTicks()).isEqualTo(340);
        }
    }

    @Nested
    class WarmupTicks {
        /**
         * When asked how long a perform waits after the press, WARMUP_TICKS should be the evoker's 20.
         *
         * <p>Method under test: {@link EvokerSpell#WARMUP_TICKS}
         */
        @Test
        void isTheEvokersWarmup() {
            assertThat(EvokerSpell.WARMUP_TICKS).isEqualTo(20);
        }
    }
}
