package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Functional spec §5.3. */
class VexCapTest {
    @Nested
    class ToSummon {
        /**
         * When the summoner has room for a whole batch (0 to 9 vexes), toSummon should answer three; at 10 and 11 it
         * should answer the remainder up to twelve; at twelve or more it should answer zero.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @ParameterizedTest(name = "{0} vexes → summon {1}")
        @CsvSource({"0, 3", "1, 3", "9, 3", "10, 2", "11, 1", "12, 0", "13, 0", "100, 0"})
        void threeThenTheRemainderThenNothing(int count, int expected) {
            assertThat(VexCap.toSummon(count)).isEqualTo(expected);
        }

        /**
         * When the count is negative, toSummon should refuse it: a negative count is a caller bug, not a summoner with
         * room for more.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Test
        void rejectsANegativeCount() {
            assertThatThrownBy(() -> VexCap.toSummon(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-1");
        }
    }

    @Nested
    class Constants {
        /**
         * When the cap is read, it should be twelve (Evoking II spec §3.2).
         *
         * <p>Field under test: {@link VexCap#CAP}
         */
        @Test
        void capIsTwelve() {
            assertThat(VexCap.CAP).isEqualTo(12);
        }

        /**
         * When the batch is read, it should be three, the ring's summon count; a change to the ring shows up here.
         *
         * <p>Field under test: {@link VexCap#BATCH}
         */
        @Test
        void batchIsThree() {
            assertThat(VexCap.BATCH).isEqualTo(3);
        }
    }
}
