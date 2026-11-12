package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Scale;

/** Functional spec §6.4, §7.1, §7.5. */
class GuardAreaPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Contains {
        /**
         * For any offset, contains should answer the same for the mirrored offset: the box is centered on the vex.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @Property
        void isSymmetric(
                @ForAll @DoubleRange(min = -10, max = 10) double dx,
                @ForAll @DoubleRange(min = -10, max = 10) double dy,
                @ForAll @DoubleRange(min = -10, max = 10) double dz) {
            assertThat(GuardArea.contains(dx, dy, dz)).isEqualTo(GuardArea.contains(-dx, -dy, -dz));
        }

        /**
         * For any point with two axes inside the box, putting the third axis exactly on a face (either sign) should be
         * inside, and 0.01 blocks past that face should be outside.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @Property
        void aFaceIsInsideAndJustPastItIsOut(
                @ForAll @IntRange(min = 0, max = 2) int axis,
                @ForAll boolean negative,
                @ForAll @DoubleRange(min = -GuardArea.HALF_WIDTH, max = GuardArea.HALF_WIDTH) double u,
                @ForAll @DoubleRange(min = -GuardArea.HALF_HEIGHT, max = GuardArea.HALF_HEIGHT) double v) {
            // Arrange
            double half = axis == 1 ? GuardArea.HALF_HEIGHT : GuardArea.HALF_WIDTH;
            double sign = negative ? -1 : 1;
            double[] onFace = edgePoint(axis, sign * half, u, v);
            double[] pastFace = edgePoint(axis, sign * (half + 0.01), u, v);
            // Act & Assert
            assertThat(GuardArea.contains(onFace[0], onFace[1], onFace[2])).isTrue();
            assertThat(GuardArea.contains(pastFace[0], pastFace[1], pastFace[2]))
                    .isFalse();
        }

        /**
         * Builds a point whose {@code axis} coordinate is {@code edge}; of the other two, a horizontal one takes
         * {@code u} and the vertical one {@code v} (when the edge axis is vertical, both horizontals take {@code u}).
         */
        private static double[] edgePoint(int axis, double edge, double u, double v) {
            return switch (axis) {
                case 0 -> new double[] {edge, v, u};
                case 1 -> new double[] {u, edge, u};
                default -> new double[] {u, v, edge};
            };
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class WithinReach {
        /**
         * For any point inside the box, withinReach should answer true: a vex can always fight what it guards.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @Property
        void everyPointOfTheBoxIsWithinReach(
                @ForAll @DoubleRange(min = -GuardArea.HALF_WIDTH, max = GuardArea.HALF_WIDTH) double dx,
                @ForAll @DoubleRange(min = -GuardArea.HALF_HEIGHT, max = GuardArea.HALF_HEIGHT) double dy,
                @ForAll @DoubleRange(min = -GuardArea.HALF_WIDTH, max = GuardArea.HALF_WIDTH) double dz) {
            // Act & Assert
            assertThat(GuardArea.withinReach(dx, dy, dz)).isTrue();
        }

        /**
         * For any direction, a point just past the reach along it should be out of reach, and a point just short of it
         * within.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @Property
        void reachIsASphere(
                @ForAll @DoubleRange(min = 0, max = 6.283) @Scale(3) double yaw,
                @ForAll @DoubleRange(min = -1.57, max = 1.57) @Scale(3) double pitch) {
            // Arrange
            double cx = Math.cos(pitch) * Math.cos(yaw);
            double cy = Math.sin(pitch);
            double cz = Math.cos(pitch) * Math.sin(yaw);
            double in = GuardArea.REACH - 0.01;
            double out = GuardArea.REACH + 0.01;
            // Act & Assert
            assertThat(GuardArea.withinReach(cx * in, cy * in, cz * in)).isTrue();
            assertThat(GuardArea.withinReach(cx * out, cy * out, cz * out)).isFalse();
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class ContainsStaying {
        /**
         * When a point is mirrored in any axis, containsStaying should give the same answer.
         *
         * <p>Method under test: {@link GuardArea#containsStaying}
         */
        @Property
        void stayBoxIsSymmetric(
                @ForAll @DoubleRange(min = -20, max = 20) double dx,
                @ForAll @DoubleRange(min = -20, max = 20) double dy,
                @ForAll @DoubleRange(min = -20, max = 20) double dz) {
            // Act
            boolean inside = GuardArea.containsStaying(dx, dy, dz);
            // Assert
            assertThat(GuardArea.containsStaying(-dx, dy, dz)).isEqualTo(inside);
            assertThat(GuardArea.containsStaying(dx, -dy, dz)).isEqualTo(inside);
            assertThat(GuardArea.containsStaying(dx, dy, -dz)).isEqualTo(inside);
        }

        /**
         * When the following box accepts a point, the stay box should accept it too (it is the wider box).
         *
         * <p>Method under test: {@link GuardArea#containsStaying}
         */
        @Property
        void stayBoxContainsTheFollowingBox(
                @ForAll @DoubleRange(min = -5, max = 5) double dx,
                @ForAll @DoubleRange(min = -5, max = 5) double dy,
                @ForAll @DoubleRange(min = -5, max = 5) double dz) {
            // Arrange
            Assume.that(GuardArea.contains(dx, dy, dz));
            // Act & Assert
            assertThat(GuardArea.containsStaying(dx, dy, dz)).isTrue();
        }
    }
}
