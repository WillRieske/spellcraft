package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Functional spec §11.12. */
class TeamMoveTest {
    @Nested
    class Next {
        /**
         * When called on each of the four real moves, next should walk Shield, Multishot, Evader, Raid and back to
         * Shield (the J key's cycle).
         *
         * <p>Method under test: {@link TeamMove#next()}
         */
        @Test
        void cyclesTheFourMoves() {
            // Arrange & Act & Assert
            assertThat(TeamMove.SHIELD.next()).isEqualTo(TeamMove.MULTISHOT);
            assertThat(TeamMove.MULTISHOT.next()).isEqualTo(TeamMove.EVADER);
            assertThat(TeamMove.EVADER.next()).isEqualTo(TeamMove.RAID);
            assertThat(TeamMove.RAID.next()).isEqualTo(TeamMove.SHIELD);
        }

        /**
         * When called on NONE, next should start the cycle at Shield: a client that somehow
         * holds NONE never gets stuck.
         *
         * <p>Method under test: {@link TeamMove#next()}
         */
        @Test
        void noneStartsTheCycle() {
            // Arrange & Act & Assert
            assertThat(TeamMove.NONE.next()).isEqualTo(TeamMove.SHIELD);
        }
    }

    @Nested
    class FromArg {
        /**
         * When the argument is 0 to 3, fromArg should name the move with that ordinal.
         *
         * <p>Method under test: {@link TeamMove#fromArg(int)}
         */
        @ParameterizedTest
        @CsvSource({"0,SHIELD", "1,MULTISHOT", "2,EVADER", "3,RAID"})
        void namesTheMove(int arg, TeamMove expected) {
            // Arrange & Act & Assert
            assertThat(TeamMove.fromArg(arg)).contains(expected);
        }

        /**
         * When the argument is outside 0 to 3 (NONE's ordinal 4 included), fromArg should name nothing.
         *
         * <p>Method under test: {@link TeamMove#fromArg(int)}
         */
        @ParameterizedTest
        @ValueSource(ints = {4, 5, -1, 127, 128, Integer.MIN_VALUE})
        void refusesAnythingElse(int arg) {
            // Arrange & Act & Assert
            assertThat(TeamMove.fromArg(arg)).isEmpty();
        }
    }

    @Nested
    class Arg {
        /**
         * When called on each real move, arg should be its ordinal and fromArg should give the move back.
         *
         * <p>Method under test: {@link TeamMove#arg()}
         */
        @ParameterizedTest
        @EnumSource(
                value = TeamMove.class,
                names = {"SHIELD", "MULTISHOT", "EVADER", "RAID"})
        @SuppressWarnings("EnumOrdinal") // the test pins arg() to the ordinal, which is the packet contract
        void roundTrips(TeamMove move) {
            // Arrange & Act & Assert
            assertThat(move.arg()).isEqualTo((byte) move.ordinal());
            assertThat(TeamMove.fromArg(move.arg())).contains(move);
        }

        /**
         * When called on NONE, arg should be 4, which no request may carry.
         *
         * <p>Method under test: {@link TeamMove#arg()}
         */
        @Test
        void noneIsFour() {
            // Arrange & Act & Assert
            assertThat(TeamMove.NONE.arg()).isEqualTo((byte) 4);
        }
    }

    @Nested
    class Id {
        /**
         * When called on each move, id should be its name in lower case.
         *
         * <p>Method under test: {@link TeamMove#id()}
         */
        @ParameterizedTest
        @CsvSource({"SHIELD,shield", "MULTISHOT,multishot", "EVADER,evader", "RAID,raid", "NONE,none"})
        void isTheLowerCaseName(TeamMove move, String expected) {
            // Arrange & Act & Assert
            assertThat(move.id()).isEqualTo(expected);
        }
    }
}
