package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.willrieske.spellcraft.spell.rules.RescuePush.Vector;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

class RescuePushPropertiesTest {
    private static final double EPS = 1.0e-9;

    private static boolean finite(Vector v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class LavaPull {
        /**
         * For any velocity and spot, the sideways speed after the pull should be at most 0.35 (a hard cap).
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Property
        void sidewaysSpeedIsCapped(
                @ForAll @DoubleRange(min = -10, max = 10) double vx,
                @ForAll @DoubleRange(min = -10, max = 10) double vy,
                @ForAll @DoubleRange(min = -10, max = 10) double vz,
                @ForAll @DoubleRange(min = -10, max = 10) double sx,
                @ForAll @DoubleRange(min = -10, max = 10) double sz) {
            // Arrange & Act
            Vector result = RescuePush.lavaPull(new Vector(vx, vy, vz), sx, sz);

            // Assert
            assertThat(Math.hypot(result.x(), result.z())).isLessThanOrEqualTo(RescuePush.PULL_CAP + EPS);
        }

        /**
         * For any velocity and spot, the upward speed should be at least 0.3 and no component should be NaN.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Property
        void keepsTheUpwardFloorAndIsFinite(
                @ForAll @DoubleRange(min = -10, max = 10) double vx,
                @ForAll @DoubleRange(min = -10, max = 10) double vy,
                @ForAll @DoubleRange(min = -10, max = 10) double vz,
                @ForAll @DoubleRange(min = -10, max = 10) double sx,
                @ForAll @DoubleRange(min = -10, max = 10) double sz) {
            // Arrange & Act
            Vector result = RescuePush.lavaPull(new Vector(vx, vy, vz), sx, sz);

            // Assert
            assertThat(result.y()).isGreaterThanOrEqualTo(RescuePush.PULL_UP);
            assertThat(finite(result)).isTrue();
        }

        /**
         * For any spot, a player who steers toward it by 0.02 each tick (the client adds its input, then the next push
         * applies the drag) should never pass 0.35 sideways in 200 ticks and should settle: no ratchet.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)} iterated
         */
        @Property
        void steeringTowardTheSpotSettlesUnderTheCap(
                @ForAll @DoubleRange(min = -10, max = 10) double sx,
                @ForAll @DoubleRange(min = -10, max = 10) double sz) {
            // Arrange
            Assume.that(Math.hypot(sx, sz) > 0.01);
            double ix = 0.02 * sx / Math.hypot(sx, sz);
            double iz = 0.02 * sz / Math.hypot(sx, sz);
            Vector v = new Vector(0, 0, 0);
            Vector previous = v;

            // Act & Assert
            for (int tick = 0; tick < 200; tick++) {
                previous = v;
                v = RescuePush.lavaPull(new Vector(v.x() + ix, v.y(), v.z() + iz), sx, sz);
                assertThat(Math.hypot(v.x(), v.z())).isLessThanOrEqualTo(RescuePush.PULL_CAP + EPS);
            }
            assertThat(Math.hypot(v.x() - previous.x(), v.z() - previous.z())).isLessThan(1.0e-6);
        }
    }

    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Lifts {
        /**
         * For any velocity, lavaLift and drowningPush should keep the upward speed at or above their floor (0.4), never
         * speed the player up sideways and give no NaN.
         *
         * <p>Methods under test: {@link RescuePush#lavaLift(Vector)}, {@link RescuePush#drowningPush(Vector)}
         */
        @Property
        void keepTheFloorAndNeverSpeedUpSideways(
                @ForAll @DoubleRange(min = -10, max = 10) double vx,
                @ForAll @DoubleRange(min = -10, max = 10) double vy,
                @ForAll @DoubleRange(min = -10, max = 10) double vz) {
            // Arrange
            Vector v = new Vector(vx, vy, vz);

            // Act & Assert
            for (Vector result : new Vector[] {RescuePush.lavaLift(v), RescuePush.drowningPush(v)}) {
                assertThat(result.y()).isGreaterThanOrEqualTo(RescuePush.LIFT);
                assertThat(Math.hypot(result.x(), result.z())).isLessThanOrEqualTo(Math.hypot(vx, vz) + EPS);
                assertThat(finite(result)).isTrue();
            }
        }

        /**
         * For any constant sideways steering of 0.02 per tick, the drowning push applied 200 times (the client adds its
         * input, the push applies the drag) should keep the sideways speed under 0.02 / (1 - 0.8): no ratchet.
         *
         * <p>Method under test: {@link RescuePush#drowningPush(Vector)} iterated
         */
        @Property
        void drowningSteeringSettlesUnderTheFixedPoint(@ForAll @DoubleRange(min = 0, max = 6.28) double angle) {
            // Arrange
            double ix = 0.02 * Math.cos(angle);
            double iz = 0.02 * Math.sin(angle);
            Vector v = new Vector(0, 0, 0);

            // Act & Assert
            for (int tick = 0; tick < 200; tick++) {
                v = RescuePush.drowningPush(new Vector(v.x() + ix, v.y(), v.z() + iz));
                assertThat(Math.hypot(v.x(), v.z())).isLessThanOrEqualTo(0.02 / (1 - RescuePush.WATER_DRAG) + EPS);
            }
        }
    }

    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Returned {
        /**
         * For any velocity and heading, returned should keep the speed and give no NaN.
         *
         * <p>Method under test: {@link RescuePush#returned(Vector, double, double, double)}
         */
        @Property
        void keepsTheSpeed(
                @ForAll @DoubleRange(min = -10, max = 10) double vx,
                @ForAll @DoubleRange(min = -10, max = 10) double vy,
                @ForAll @DoubleRange(min = -10, max = 10) double vz,
                @ForAll @DoubleRange(min = -10, max = 10) double hx,
                @ForAll @DoubleRange(min = -10, max = 10) double hy,
                @ForAll @DoubleRange(min = -10, max = 10) double hz) {
            // Arrange & Act
            Vector result = RescuePush.returned(new Vector(vx, vy, vz), hx, hy, hz);

            // Assert
            assertThat(finite(result)).isTrue();
            assertThat(Math.sqrt(result.x() * result.x() + result.y() * result.y() + result.z() * result.z()))
                    .isCloseTo(Math.sqrt(vx * vx + vy * vy + vz * vz), within(EPS));
        }
    }
}
