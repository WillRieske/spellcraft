package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SegmentDistanceTest {
    @Nested
    class DistanceToSegment {
        /**
         * When a point is beside the middle of a segment, distanceToSegment should answer the perpendicular distance.
         *
         * <p>Method under test: {@link SegmentDistance#distanceToSegment}
         */
        @Test
        void besideTheMiddleIsThePerpendicularDistance() {
            // Act
            double distance = SegmentDistance.distanceToSegment(5, 3, 0, 0, 0, 0, 10, 0, 0);
            // Assert
            assertThat(distance).isCloseTo(3.0, within(1.0E-12));
        }

        /**
         * When a point is beyond the start of a segment, distanceToSegment should answer the distance to the start (the
         * projection is clamped).
         *
         * <p>Method under test: {@link SegmentDistance#distanceToSegment}
         */
        @Test
        void beyondTheStartIsTheDistanceToTheStart() {
            // Act
            double distance = SegmentDistance.distanceToSegment(-3, 4, 0, 0, 0, 0, 10, 0, 0);
            // Assert
            assertThat(distance).isCloseTo(5.0, within(1.0E-12));
        }

        /**
         * When a point is beyond the end of a segment, distanceToSegment should answer the distance to the end (the
         * projection is clamped).
         *
         * <p>Method under test: {@link SegmentDistance#distanceToSegment}
         */
        @Test
        void beyondTheEndIsTheDistanceToTheEnd() {
            // Act
            double distance = SegmentDistance.distanceToSegment(13, 0, 4, 0, 0, 0, 10, 0, 0);
            // Assert
            assertThat(distance).isCloseTo(5.0, within(1.0E-12));
        }

        /**
         * When the segment has zero length, distanceToSegment should answer the distance to that point.
         *
         * <p>Method under test: {@link SegmentDistance#distanceToSegment}
         */
        @Test
        void aZeroLengthSegmentIsAPoint() {
            // Act
            double distance = SegmentDistance.distanceToSegment(1, 2, 2, 0, 0, 0, 0, 0, 0);
            // Assert
            assertThat(distance).isCloseTo(3.0, within(1.0E-12));
        }

        /**
         * When a point lies on the segment, distanceToSegment should answer zero, at the ends and in the middle.
         *
         * <p>Method under test: {@link SegmentDistance#distanceToSegment}
         */
        @Test
        void aPointOnTheSegmentIsAtDistanceZero() {
            // Act & Assert
            assertThat(SegmentDistance.distanceToSegment(0, 0, 0, 0, 0, 0, 4, 4, 4))
                    .isZero();
            assertThat(SegmentDistance.distanceToSegment(4, 4, 4, 0, 0, 0, 4, 4, 4))
                    .isZero();
            assertThat(SegmentDistance.distanceToSegment(2, 2, 2, 0, 0, 0, 4, 4, 4))
                    .isCloseTo(0.0, within(1.0E-12));
        }
    }
}
