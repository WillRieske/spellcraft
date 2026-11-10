package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class TeamMoveRulesPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class RosterSize {
        /**
         * For any move and free count, rosterSize should never exceed the free count and never go negative.
         *
         * <p>Method under test: {@link TeamMoveRules#rosterSize(TeamMove, int)}
         */
        @Property
        void neverMoreThanFree(@ForAll TeamMove move, @ForAll @IntRange(min = 0, max = 40) int free) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.rosterSize(move, free)).isBetween(0, free);
        }

        /**
         * For any free count, a Raid roster should be exactly 12 or nobody.
         *
         * <p>Method under test: {@link TeamMoveRules#rosterSize(TeamMove, int)}
         */
        @Property
        void raidIsTwelveOrNobody(@ForAll @IntRange(min = 0, max = 40) int free) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.rosterSize(TeamMove.RAID, free)).isIn(0, TeamMoveRules.RAID_SIZE);
        }

        /**
         * For any move that can start with free vexes, enough free vexes should always give a roster, and the roster
         * should not shrink as more vexes become free.
         *
         * <p>Method under test: {@link TeamMoveRules#rosterSize(TeamMove, int)}
         */
        @Property
        void growsWithTheFreeCount(@ForAll TeamMove move, @ForAll @IntRange(min = 0, max = 39) int free) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.rosterSize(move, free + 1))
                    .isGreaterThanOrEqualTo(TeamMoveRules.rosterSize(move, free));
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Pick {
        /**
         * For any enemy and readiness, pick should answer none, Raid, Shield or Multishot (never Evader), and never a
         * move whose own readiness is false.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Property
        void neverPicksAMoveThatIsNotReady(
                @ForAll @DoubleRange(min = 0, max = 2000) double maxHealth,
                @ForAll @DoubleRange(min = 0, max = 2000) double attackDamage,
                @ForAll boolean targetsFreeVex,
                @ForAll boolean raidReady,
                @ForAll boolean shieldReady,
                @ForAll boolean multishotReady) {
            // Act
            TeamMove picked =
                    TeamMoveRules.pick(maxHealth, attackDamage, targetsFreeVex, raidReady, shieldReady, multishotReady);

            // Assert
            assertThat(picked).isIn(TeamMove.NONE, TeamMove.RAID, TeamMove.SHIELD, TeamMove.MULTISHOT);
            assertThat(picked != TeamMove.RAID || raidReady).isTrue();
            assertThat(picked != TeamMove.SHIELD || shieldReady).isTrue();
            assertThat(picked != TeamMove.MULTISHOT || (multishotReady && targetsFreeVex))
                    .isTrue();
        }

        /**
         * For any enemy of 99 health or more, pick should never answer Multishot while a Shield could start.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Property
        void aGiantIsNeverAMultishotWhileAShieldCouldStart(
                @ForAll @DoubleRange(min = 99, max = 2000) double maxHealth,
                @ForAll @DoubleRange(min = 0, max = 2000) double attackDamage,
                @ForAll boolean targetsFreeVex,
                @ForAll boolean raidReady,
                @ForAll boolean multishotReady) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.pick(maxHealth, attackDamage, targetsFreeVex, raidReady, true, multishotReady))
                    .isNotEqualTo(TeamMove.MULTISHOT);
        }

        /**
         * For any enemy under 31 health, pick should never answer Shield or Raid.
         *
         * <p>Method under test: {@link TeamMoveRules#pick(double, double, boolean, boolean, boolean, boolean)}
         */
        @Property
        void aSmallEnemyIsNeverAShieldOrARaid(
                @ForAll @DoubleRange(min = 0, max = 30.99) double maxHealth,
                @ForAll @DoubleRange(min = 0, max = 2000) double attackDamage,
                @ForAll boolean targetsFreeVex) {
            // Arrange & Act & Assert
            assertThat(TeamMoveRules.pick(maxHealth, attackDamage, targetsFreeVex, true, true, true))
                    .isNotIn(TeamMove.SHIELD, TeamMove.RAID);
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Grid {
        /**
         * For two cells, gridAcross and gridDepth together should name the same place only when the cells are equal.
         *
         * <p>Method under test: {@link TeamMoveRules#gridAcross(int)}, {@link TeamMoveRules#gridDepth(int)}
         */
        @Property
        void cellsAreDistinct(@ForAll @IntRange(min = 0, max = 11) int a, @ForAll @IntRange(min = 0, max = 11) int b) {
            // Act
            boolean same = TeamMoveRules.gridAcross(a) == TeamMoveRules.gridAcross(b)
                    && TeamMoveRules.gridDepth(a) == TeamMoveRules.gridDepth(b);

            // Assert
            assertThat(same).isEqualTo(a == b);
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class OrbitDegrees {
        /**
         * For any roster, time and slot, orbitDegrees should stay in [0, 360).
         *
         * <p>Method under test: {@link TeamMoveRules#orbitDegrees(int, int, long)}
         */
        @Property
        void staysInOneTurn(
                @ForAll @IntRange(min = 1, max = 12) int count,
                @ForAll @IntRange(min = 0, max = 11) int rawSlot,
                @ForAll @LongRange(min = 0, max = 100_000) long ticks) {
            // Arrange (the slot is drawn over the whole range and folded into [0, count), so edge slots are hit)
            int slot = rawSlot % count;

            // Act & Assert
            assertThat(TeamMoveRules.orbitDegrees(slot, count, ticks))
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThan(360.0);
        }

        /**
         * For any roster and time, two different slots should be at least an even share of the circle apart.
         *
         * <p>Method under test: {@link TeamMoveRules#orbitDegrees(int, int, long)}
         */
        @Property
        void slotsStayApart(
                @ForAll @IntRange(min = 2, max = 12) int count,
                @ForAll @IntRange(min = 0, max = 11) int rawA,
                @ForAll @IntRange(min = 0, max = 11) int rawB,
                @ForAll @LongRange(min = 0, max = 100_000) long ticks) {
            // Arrange (fold both slots into [0, count); only equal slots are discarded)
            int a = rawA % count;
            int b = rawB % count;
            Assume.that(a != b);

            // Act
            double gap =
                    Math.abs(TeamMoveRules.orbitDegrees(a, count, ticks) - TeamMoveRules.orbitDegrees(b, count, ticks));
            double around = Math.min(gap, 360.0 - gap);

            // Assert
            assertThat(around).isGreaterThanOrEqualTo(360.0 / count - 1e-6);
        }
    }
}
