package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.willrieske.spellcraft.spell.rules.RescuePush.Vector;
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
         * For any velocity and spot, the sideways speed after the pull should be at most the larger of 0.35 and the
         * speed before.
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
            Vector result = RescuePush.lavaPull(new Vector(vx, vy, vz), sx, sz);

            assertThat(Math.hypot(result.x(), result.z()))
                    .isLessThanOrEqualTo(Math.max(RescuePush.PULL_CAP, Math.hypot(vx, vz)) + EPS);
        }

        /**
         * For any velocity and spot, the upward speed should never go down and no component should be NaN.
         *
         * <p>Method under test: {@link RescuePush#lavaPull(Vector, double, double)}
         */
        @Property
        void neverLowersYAndIsFinite(
                @ForAll @DoubleRange(min = -10, max = 10) double vx,
                @ForAll @DoubleRange(min = -10, max = 10) double vy,
                @ForAll @DoubleRange(min = -10, max = 10) double vz,
                @ForAll @DoubleRange(min = -10, max = 10) double sx,
                @ForAll @DoubleRange(min = -10, max = 10) double sz) {
            Vector result = RescuePush.lavaPull(new Vector(vx, vy, vz), sx, sz);

            assertThat(result.y()).isGreaterThanOrEqualTo(vy);
            assertThat(finite(result)).isTrue();
        }
    }

    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Lifts {
        /**
         * For any velocity, lavaLift and drowningPush should never lower the upward speed, keep the sideways velocity
         * and give no NaN.
         *
         * <p>Methods under test: {@link RescuePush#lavaLift(Vector)}, {@link RescuePush#drowningPush(Vector)}
         */
        @Property
        void neverLowerYAndKeepSideways(
                @ForAll @DoubleRange(min = -10, max = 10) double vx,
                @ForAll @DoubleRange(min = -10, max = 10) double vy,
                @ForAll @DoubleRange(min = -10, max = 10) double vz) {
            Vector v = new Vector(vx, vy, vz);

            for (Vector result : new Vector[] {RescuePush.lavaLift(v), RescuePush.drowningPush(v)}) {
                assertThat(result.y()).isGreaterThanOrEqualTo(vy);
                assertThat(result.x()).isEqualTo(vx);
                assertThat(result.z()).isEqualTo(vz);
                assertThat(finite(result)).isTrue();
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
            Vector result = RescuePush.returned(new Vector(vx, vy, vz), hx, hy, hz);

            assertThat(finite(result)).isTrue();
            assertThat(Math.sqrt(result.x() * result.x() + result.y() * result.y() + result.z() * result.z()))
                    .isCloseTo(Math.sqrt(vx * vx + vy * vy + vz * vz), within(EPS));
        }
    }
}
