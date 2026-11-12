package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

/** Functional spec §8.7. */
class SegmentDistancePropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class DistanceToSegment {
        /**
         * For any point and segment, distanceToSegment should be no more than the distance to either end: the nearest
         * point of the segment cannot be farther than an end.
         *
         * <p>Method under test: {@link SegmentDistance#distanceToSegment}
         */
        @Property
        void isNeverMoreThanTheDistanceToEitherEnd(
                @ForAll @DoubleRange(min = -50, max = 50) double px,
                @ForAll @DoubleRange(min = -50, max = 50) double py,
                @ForAll @DoubleRange(min = -50, max = 50) double pz,
                @ForAll @DoubleRange(min = -50, max = 50) double ax,
                @ForAll @DoubleRange(min = -50, max = 50) double ay,
                @ForAll @DoubleRange(min = -50, max = 50) double az,
                @ForAll @DoubleRange(min = -50, max = 50) double bx,
                @ForAll @DoubleRange(min = -50, max = 50) double by,
                @ForAll @DoubleRange(min = -50, max = 50) double bz) {
            // Act
            double distance = SegmentDistance.distanceToSegment(px, py, pz, ax, ay, az, bx, by, bz);
            // Assert
            double toStart = Math.sqrt(square(px - ax) + square(py - ay) + square(pz - az));
            double toEnd = Math.sqrt(square(px - bx) + square(py - by) + square(pz - bz));
            assertThat(distance).isGreaterThanOrEqualTo(0.0);
            assertThat(distance).isLessThanOrEqualTo(toStart + 1.0E-9);
            assertThat(distance).isLessThanOrEqualTo(toEnd + 1.0E-9);
        }

        private static double square(double value) {
            return value * value;
        }
    }
}
