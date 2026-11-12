package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.willrieske.spellcraft.spell.rules.RescuePush.Vector;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Functional spec §10.3, §10.5, §10.6. */
class RescuePushTest {
    private static final double EPS = 1.0e-9;

    private static void assertVector(Vector actual, double x, double y, double z) {
        assertThat(actual.x()).isCloseTo(x, within(EPS));
        assertThat(actual.y()).isCloseTo(y, within(EPS));
        assertThat(actual.z()).isCloseTo(z, within(EPS));
    }

    @Nested
    class LavaPull {
        /**
         * When the player is still, lavaPull should add 0.1 toward the spot and raise the upward speed to 0.3.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void fromRest_addsOneTenthAndLifts() {
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0), 3, 0), 0.1, 0.3, 0);
        }

        /**
         * When the player moves toward the spot at 0.3, lavaPull should halve that (the lava drag) and add 0.1:
         * 0.25 sideways.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void movingTowardTheSpot_isSlowedByDragThenPulled() {
            assertVector(RescuePush.lavaPull(new Vector(0.3, 0, 0), 3, 0), 0.25, 0.3, 0);
        }

        /**
         * When the drag plus the pull would pass 0.35 sideways, lavaPull should cap the sideways speed at 0.35.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void passingTheCap_isCapped() {
            assertVector(RescuePush.lavaPull(new Vector(0.5, 0, 0), 3, 0), 0.35, 0.3, 0);
        }

        /**
         * When the player is far faster than the cap toward the spot, lavaPull should still cap the sideways speed at
         * 0.35 (a hard cap).
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void farFasterThanTheCap_isCappedToo() {
            assertVector(RescuePush.lavaPull(new Vector(2, 0, 0), 3, 0), 0.35, 0.3, 0);
        }

        /**
         * When the player moves sideways across the pull, lavaPull should keep half of that velocity (the drag).
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void sidewaysVelocity_isKept() {
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0.2), 3, 0), 0.1, 0.3, 0.1);
        }

        /**
         * When the player rises at 0.9, lavaPull should halve the upward speed (the drag) to 0.45, above the floor.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void fasterUpwardSpeed_isHalvedByDrag() {
            assertThat(RescuePush.lavaPull(new Vector(0, 0.9, 0), 3, 0).y()).isEqualTo(0.45);
        }

        /**
         * When the spot is straight above or below, lavaPull should add nothing sideways and give no NaN.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void spotDirectlyAbove_addsNothingSideways() {
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0), 0, 0), 0, 0.3, 0);
        }

        /**
         * When the spot lies diagonally, lavaPull should add 0.1 along the unit direction on both axes.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void diagonalSpot_pullsOnBothAxes() {
            // Act & Assert
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0), 3, 4), 0.06, 0.3, 0.08);
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0), -6, -8), -0.06, 0.3, -0.08);
        }

        /**
         * When the spot is exactly a millionth of a block away, lavaPull should treat it as no direction at all.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void spotAMillionthAway_addsNothingSideways() {
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0), 1.0e-6, 0), 0, 0.3, 0);
        }

        /**
         * When the spot is a small but real distance away, lavaPull should still pull toward it.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void aNearbySpot_stillPulls() {
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0), 1.0e-3, 0), 0.1, 0.3, 0);
        }
    }

    @Nested
    class LavaPullCrossing {
        /**
         * When the player moves across the pull at 0.5 (spot 3 blocks away), lavaPull should halve that to 0.25 and add
         * 0.1 toward the spot; the speed (about 0.27) is under the cap, so the cap changes nothing.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void acrossThePullUnderTheCap_isNotScaled() {
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0.5), 3, 0), 0.1, 0.3, 0.25);
        }

        /**
         * When the player moves across the pull at 2, lavaPull should scale the whole sideways velocity down to 0.35
         * and keep its direction: 0.1 toward the spot and 1.0 across before the scaling.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void acrossThePullOverTheCap_keepsTheDirection() {
            // Arrange
            double length = Math.hypot(0.1, 1.0);
            // Act
            Vector result = RescuePush.lavaPull(new Vector(0, 0, 2), 3, 0);
            // Assert
            assertVector(result, 0.1 * 0.35 / length, 0.3, 1.0 * 0.35 / length);
            assertThat(Math.hypot(result.x(), result.z())).isCloseTo(0.35, within(EPS));
        }
    }

    @Nested
    class LavaLift {
        /**
         * When the player sinks, lavaLift should raise the upward speed to 0.4 and halve the sideways speed.
         *
         * <p>Method under test: {@link RescuePush#lavaLift(Vector)}
         */
        @Test
        void sinking_isLiftedToFourTenths() {
            assertVector(RescuePush.lavaLift(new Vector(0.2, -0.1, 0.1)), 0.1, 0.4, 0.05);
        }

        /**
         * When the player rises at 0.9, lavaLift should halve the upward speed (the drag) to 0.45, above the floor.
         *
         * <p>Method under test: {@link RescuePush#lavaLift(Vector)}
         */
        @Test
        void fasterUpwardSpeed_isHalvedByDrag() {
            assertThat(RescuePush.lavaLift(new Vector(0, 0.9, 0)).y()).isEqualTo(0.45);
        }
    }

    @Nested
    class DrowningPush {
        /**
         * When the player sinks, drowningPush should raise the upward speed to 0.4 and scale the sideways speed by the
         * water drag, 0.8.
         *
         * <p>Method under test: {@link RescuePush#drowningPush(Vector)}
         */
        @Test
        void sinking_isPushedToFourTenths() {
            assertVector(RescuePush.drowningPush(new Vector(0.2, -0.1, 0.1)), 0.16, 0.4, 0.08);
        }

        /**
         * When the player rises at 0.9, drowningPush should scale the upward speed by 0.8 to 0.72, above the floor.
         *
         * <p>Method under test: {@link RescuePush#drowningPush(Vector)}
         */
        @Test
        void fasterUpwardSpeed_isScaledByDrag() {
            assertThat(RescuePush.drowningPush(new Vector(0, 0.9, 0)).y()).isCloseTo(0.72, within(EPS));
        }
    }

    @Nested
    class Returned {
        /**
         * When there is a heading, returned should keep the old speed and point it along the heading.
         *
         * <p>Method under test: {@link RescuePush#returned(Vector, double, double, double)}
         */
        @Test
        void withHeading_keepsSpeedAlongIt() {
            assertVector(RescuePush.returned(new Vector(0, 0, -1.5), 0, 3, 4), 0, 0.9, 1.2);
        }

        /**
         * When the heading has zero length, returned should send the fireball straight back.
         *
         * <p>Method under test: {@link RescuePush#returned(Vector, double, double, double)}
         */
        @Test
        void withoutHeading_goesStraightBack() {
            assertVector(RescuePush.returned(new Vector(1, 0, 0), 0, 0, 0), -1, 0, 0);
        }

        /**
         * When the old velocity has all three components, returned with no heading should flip every one of them.
         *
         * <p>Method under test: {@link RescuePush#returned(Vector, double, double, double)}
         */
        @Test
        void withoutHeading_flipsEveryComponent() {
            assertVector(RescuePush.returned(new Vector(1, 2, 3), 0, 0, 0), -1, -2, -3);
        }

        /**
         * When the heading is exactly a millionth of a block long, returned should treat it as no heading.
         *
         * <p>Method under test: {@link RescuePush#returned(Vector, double, double, double)}
         */
        @Test
        void headingAMillionthLong_goesStraightBack() {
            assertVector(RescuePush.returned(new Vector(1, 0, 0), 0, 0, 1.0e-6), -1, 0, 0);
        }

        /**
         * When the heading is tiny but not zero, returned should still point along it, not straight back.
         *
         * <p>Method under test: {@link RescuePush#returned(Vector, double, double, double)}
         */
        @Test
        void tinyHeading_isStillUsed() {
            assertVector(RescuePush.returned(new Vector(1, 0, 0), 0, 0, 1.0e-3), 0, 0, 1);
        }
    }
}
