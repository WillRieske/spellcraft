package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TrailTrackerTest {
    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;

    private final TrailTracker tracker = new TrailTracker();

    @Nested
    class Observe {
        /**
         * When the player has not been observed yet, observe should light nothing.
         *
         * <p>Method under test: {@link TrailTracker#observe(long, boolean)}
         */
        @Test
        void firstObservationLightsNothing() {
            assertThat(tracker.observe(A, true)).isEmpty();
        }

        /**
         * When the player stays on the same block, grounded or not, observe should light nothing.
         *
         * <p>Method under test: {@link TrailTracker#observe(long, boolean)}
         */
        @Test
        void stayingOnTheSameBlockLightsNothing() {
            // Arrange
            tracker.observe(A, true);
            // Act & Assert
            assertThat(tracker.observe(A, true)).isEmpty();
            assertThat(tracker.observe(A, false)).isEmpty();
        }

        /**
         * When the player was grounded while on a block and leaves it, observe should light that block.
         *
         * <p>Method under test: {@link TrailTracker#observe(long, boolean)}
         */
        @Test
        void leavingABlockTheyStoodOnLightsIt() {
            // Arrange
            tracker.observe(A, true);
            // Act & Assert
            assertThat(tracker.observe(B, true)).hasValue(A);
        }

        /**
         * When the player was never grounded while on a block and leaves it, observe should light nothing.
         *
         * <p>Method under test: {@link TrailTracker#observe(long, boolean)}
         */
        @Test
        void leavingABlockTheyNeverStoodOnLightsNothing() {
            // Arrange
            tracker.observe(A, false);
            // Act & Assert
            assertThat(tracker.observe(B, true)).isEmpty();
        }

        /**
         * When the player leaves the ground before leaving the block (a jump), observe should still light the
         * block once they leave it.
         *
         * <p>Method under test: {@link TrailTracker#observe(long, boolean)}
         */
        @Test
        void leavingTheGroundBeforeLeavingTheBlockStillLightsIt() {
            // Arrange
            tracker.observe(A, true);
            // Act & Assert
            assertThat(tracker.observe(A, false)).as("still on A, airborne").isEmpty();
            assertThat(tracker.observe(B, false))
                    .as("left A: the player was grounded on A earlier")
                    .hasValue(A);
        }

        /**
         * When the player jumps across blocks, observe should light the take-off block and later the landing block,
         * but never a block only passed in the air.
         *
         * <p>Method under test: {@link TrailTracker#observe(long, boolean)}
         */
        @Test
        void jumpLightsTheTakeOffBlockAndThenTheLandingBlockOnly() {
            // Arrange
            tracker.observe(A, true);
            // Act & Assert
            assertThat(tracker.observe(B, false))
                    .as("take-off: the player was grounded on A")
                    .hasValue(A);
            assertThat(tracker.observe(C, false))
                    .as("B was only passed in the air")
                    .isEmpty();
            assertThat(tracker.observe(C, true))
                    .as("landing on C: C was airborne so far, same block")
                    .isEmpty();
            assertThat(tracker.observe(A, true)).as("leaving C").hasValue(C);
        }
    }

    @Nested
    class Reset {
        /**
         * When the tracker is reset, the next observation should light nothing and tracking should resume after it.
         *
         * <p>Method under test: {@link TrailTracker#reset()}
         */
        @Test
        void resetForgetsTheObservation() {
            // Arrange
            tracker.observe(A, true);
            // Act
            tracker.reset();
            // Assert
            assertThat(tracker.observe(B, true)).isEmpty();
            assertThat(tracker.observe(C, true))
                    .as("tracking resumes after the reset")
                    .hasValue(B);
        }
    }
}
