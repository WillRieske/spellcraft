package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TeamMoveRulesTest {
    @Nested
    class RosterSize {
        /**
         * When the free count is given, rosterSize should answer the spec's table (Shield 3 or 9, Multishot 3, Evader
         * up to 3, Raid 12, nothing below the minimum).
         *
         * <p>Method under test: {@link TeamMoveRules#rosterSize(TeamMove, int)}
         */
        @ParameterizedTest
        @CsvSource({
            "SHIELD,0,0",
            "SHIELD,2,0",
            "SHIELD,3,3",
            "SHIELD,8,3",
            "SHIELD,9,9",
            "SHIELD,12,9",
            "MULTISHOT,2,0",
            "MULTISHOT,3,3",
            "MULTISHOT,12,3",
            "EVADER,0,0",
            "EVADER,1,1",
            "EVADER,2,2",
            "EVADER,3,3",
            "EVADER,12,3",
            "RAID,11,0",
            "RAID,12,12",
            "RAID,13,12",
            "NONE,12,0"
        })
        void followsTheTable(TeamMove move, int free, int expected) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.rosterSize(move, free)).isEqualTo(expected);
        }
    }

    @Nested
    class Fits {
        /**
         * When an enemy is at the edge of a move's threshold, fits should say yes at the threshold and no just under.
         *
         * <p>Method under test: {@link TeamMoveRules#fits(TeamMove, double, double)}
         */
        @ParameterizedTest
        @CsvSource({
            "SHIELD,31.0,0.0,true", "SHIELD,30.99,99.0,false",
            "RAID,99.0,0.0,true", "RAID,98.99,0.0,false",
            "MULTISHOT,0.0,6.0,true", "MULTISHOT,500.0,5.99,false",
            "EVADER,500.0,500.0,false", "NONE,500.0,500.0,false"
        })
        void edges(TeamMove move, double maxHealth, double attackDamage, boolean expected) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.fits(move, maxHealth, attackDamage)).isEqualTo(expected);
        }
    }

    @Nested
    class NeedsGuard {
        /**
         * When health is under half the maximum, needsGuard should be true; at exactly half, a zero maximum
         * or NaN, false.
         *
         * <p>Method under test: {@link TeamMoveRules#needsGuard(float, float)}
         */
        @ParameterizedTest
        @CsvSource({"9.0,20.0,true", "10.0,20.0,false", "0.5,20.0,true", "5.0,0.0,false", "NaN,20.0,false"})
        void underHalf(float health, float maxHealth, boolean expected) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.needsGuard(health, maxHealth)).isEqualTo(expected);
        }
    }

    @Nested
    class Pick {
        /**
         * When an enemy earns a Raid and one can start, pick should answer Raid; with the Raid not ready, a Shield.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Test
        void ravagerGetsARaidThenFallsThroughToAShield() {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.pick(100, 12, false, true, true, true)).isEqualTo(TeamMove.RAID);
            assertThat(TeamMoveRules.pick(100, 12, false, false, true, true)).isEqualTo(TeamMove.SHIELD);
            assertThat(TeamMoveRules.pick(100, 12, true, false, false, true)).isEqualTo(TeamMove.MULTISHOT);
        }

        /**
         * When the enemy has 40 health and hits for 7, pick should answer Shield.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Test
        void endermanGetsAShield() {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.pick(40, 7, false, true, true, true)).isEqualTo(TeamMove.SHIELD);
        }

        /**
         * When the enemy hits for 5 (a vindicator), pick should answer none even if it hunts a free vex; at 6 it
         * answers Multishot, but not when no free vex is hunted or Multishot is not ready.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Test
        void multishotNeedsSixDamageAndAHuntedVex() {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.pick(24, 5, true, true, true, true)).isEqualTo(TeamMove.NONE);
            assertThat(TeamMoveRules.pick(20, 6, true, true, true, true)).isEqualTo(TeamMove.MULTISHOT);
            assertThat(TeamMoveRules.pick(20, 6, false, true, true, true)).isEqualTo(TeamMove.NONE);
            assertThat(TeamMoveRules.pick(20, 6, true, true, true, false)).isEqualTo(TeamMove.NONE);
        }
    }

    @Nested
    class EndsAt {
        /**
         * When called with a start time, endsAt should add 2400 ticks, 1200 for an Evader and none for none.
         *
         * <p>Method under test: {@link TeamMoveRules#endsAt(TeamMove, long)}
         */
        @ParameterizedTest
        @CsvSource({"SHIELD,2500", "MULTISHOT,2500", "RAID,2500", "EVADER,1300", "NONE,100"})
        void addsTheLimit(TeamMove move, long expected) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.endsAt(move, 100)).isEqualTo(expected);
        }
    }

    @Nested
    class TooFar {
        /**
         * When the squared distance is exactly 48 squared, tooFar should be false; a hair beyond, true.
         *
         * <p>Method under test: {@link TeamMoveRules#tooFar(double)}
         */
        @Test
        void isStrictlyBeyondFortyEight() {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.tooFar(0)).isFalse();
            assertThat(TeamMoveRules.tooFar(48.0 * 48.0)).isFalse();
            assertThat(TeamMoveRules.tooFar(48.0 * 48.0 + 0.01)).isTrue();
        }
    }

    @Nested
    class Grid {
        /**
         * When the cell is 0 to 11, gridAcross should be -2.25, -0.75, 0.75, 2.25 in each row and gridDepth -1.5, 0 and
         * 1.5 by row.
         *
         * <p>Method under test: {@link TeamMoveRules#gridAcross(int)}, {@link TeamMoveRules#gridDepth(int)}
         */
        @Test
        void isThreeRowsOfFour() {
            // Arrange (row 0 is the nearest the enemy: RaidMove.cell adds GRID_DISTANCE + gridDepth)
            double[] across = {-2.25, -0.75, 0.75, 2.25};
            double[] depth = {-1.5, 0.0, 1.5};

            for (int cell = 0; cell < 12; cell++) {
                // Act & Assert
                assertThat(TeamMoveRules.gridAcross(cell)).isEqualTo(across[cell % 4]);
                assertThat(TeamMoveRules.gridDepth(cell)).isEqualTo(depth[cell / 4]);
            }
        }

        /**
         * When the cell is outside 0 to 11, both should throw.
         *
         * <p>Method under test: {@link TeamMoveRules#gridAcross(int)}, {@link TeamMoveRules#gridDepth(int)}
         */
        @Test
        void refusesOtherCells() {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> TeamMoveRules.gridAcross(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TeamMoveRules.gridDepth(12)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class OrbitDegrees {
        /**
         * When the clock runs, orbitDegrees should spread the slots evenly and add 6 degrees a tick, modulo 360.
         *
         * <p>Method under test: {@link TeamMoveRules#orbitDegrees(int, int, long)}
         */
        @ParameterizedTest
        @CsvSource({"0,3,0,0", "1,3,0,120", "2,3,0,240", "0,3,10,60", "1,3,50,60", "0,1,60,0"})
        void spreadsAndTurns(int slot, int count, long ticks, double expected) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.orbitDegrees(slot, count, ticks)).isCloseTo(expected, within(1e-9));
        }

        /**
         * When the count is zero or the slot is negative or not below the count, orbitDegrees should throw.
         *
         * <p>Method under test: {@link TeamMoveRules#orbitDegrees(int, int, long)}
         */
        @ParameterizedTest
        @CsvSource({"0,0", "-1,3", "3,3"})
        void refusesABadSlotOrCount(int slot, int count) {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> TeamMoveRules.orbitDegrees(slot, count, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
