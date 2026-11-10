package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.willrieske.spellcraft.spell.rules.RescuePush.Vector;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
         * When the pull would pass 0.35 sideways, lavaPull should cap the sideways speed at 0.35.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void passingTheCap_isCapped() {
            assertVector(RescuePush.lavaPull(new Vector(0.3, 0, 0), 3, 0), 0.35, 0.3, 0);
        }

        /**
         * When the player is already faster than the cap, lavaPull should neither speed them up nor slow them down.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void alreadyFasterThanTheCap_isKept() {
            assertVector(RescuePush.lavaPull(new Vector(0.5, 0, 0), 3, 0), 0.5, 0.3, 0);
        }

        /**
         * When the player moves sideways across the pull, lavaPull should keep that velocity.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void sidewaysVelocity_isKept() {
            assertVector(RescuePush.lavaPull(new Vector(0, 0, 0.2), 3, 0), 0.1, 0.3, 0.2);
        }

        /**
         * When the player already rises faster than 0.3, lavaPull should not lower the upward speed.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Test
        void fasterUpwardSpeed_isNeverLowered() {
            assertThat(RescuePush.lavaPull(new Vector(0, 0.6, 0), 3, 0).y()).isEqualTo(0.6);
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
    class LavaLift {
        /**
         * When the player sinks, lavaLift should raise the upward speed to 0.4 and keep the sideways speed.
         *
         * <p>Method under test: {@link RescuePush#lavaLift(Vector)}
         */
        @Test
        void sinking_isLiftedToFourTenths() {
            assertVector(RescuePush.lavaLift(new Vector(0.2, -0.1, 0.1)), 0.2, 0.4, 0.1);
        }

        /**
         * When the player already rises faster than 0.4, lavaLift should not lower the upward speed.
         *
         * <p>Method under test: {@link RescuePush#lavaLift(Vector)}
         */
        @Test
        void fasterUpwardSpeed_isNeverLowered() {
            assertThat(RescuePush.lavaLift(new Vector(0, 0.9, 0)).y()).isEqualTo(0.9);
        }
    }

    @Nested
    class DrowningPush {
        /**
         * When the player sinks, drowningPush should raise the upward speed to 0.4 and keep the sideways speed.
         *
         * <p>Method under test: {@link RescuePush#drowningPush(Vector)}
         */
        @Test
        void sinking_isPushedToFourTenths() {
            assertVector(RescuePush.drowningPush(new Vector(0.2, -0.1, 0.1)), 0.2, 0.4, 0.1);
        }

        /**
         * When the player already rises faster than 0.4, drowningPush should not lower the upward speed.
         *
         * <p>Method under test: {@link RescuePush#drowningPush(Vector)}
         */
        @Test
        void fasterUpwardSpeed_isNeverLowered() {
            assertThat(RescuePush.drowningPush(new Vector(0, 0.9, 0)).y()).isEqualTo(0.9);
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
