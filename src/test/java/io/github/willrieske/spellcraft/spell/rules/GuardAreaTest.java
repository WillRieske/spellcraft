package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GuardAreaTest {
    @Nested
    class Contains {
        /**
         * When a point sits inside the 8×8×7 box or on its faces, contains should answer true; just past a face (0.01
         * blocks) it should answer false, on every axis and both signs.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @ParameterizedTest(name = "({0}, {1}, {2}) inside = {3}")
        @CsvSource({
            "0, 0, 0, true",
            "4, 3.5, 4, true",
            "-4, -3.5, -4, true",
            "4.01, 0, 0, false",
            "-4.01, 0, 0, false",
            "0, 3.51, 0, false",
            "0, -3.51, 0, false",
            "0, 0, 4.01, false",
            "0, 0, -4.01, false"
        })
        void edgesAreInsideAndJustPastThemIsOut(double dx, double dy, double dz, boolean inside) {
            assertThat(GuardArea.contains(dx, dy, dz)).isEqualTo(inside);
        }

        /**
         * When any offset is NaN, contains should answer false: a bad position is never inside.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @Test
        void nanIsOutside() {
            // Act & Assert
            assertThat(GuardArea.contains(Double.NaN, 0, 0)).isFalse();
            assertThat(GuardArea.contains(0, Double.NaN, 0)).isFalse();
            assertThat(GuardArea.contains(0, 0, Double.NaN)).isFalse();
        }

        /**
         * When any offset is infinite, either sign, contains should answer false.
         *
         * <p>Method under test: {@link GuardArea#contains(double, double, double)}
         */
        @ParameterizedTest(name = "infinity {0} on axis {1} is outside")
        @CsvSource({
            "POSITIVE_INFINITY, 0",
            "NEGATIVE_INFINITY, 0",
            "POSITIVE_INFINITY, 1",
            "NEGATIVE_INFINITY, 1",
            "POSITIVE_INFINITY, 2",
            "NEGATIVE_INFINITY, 2"
        })
        void infinityIsOutside(String which, int axis) {
            // Arrange
            double infinity = which.equals("POSITIVE_INFINITY") ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            // Act
            boolean inside =
                    GuardArea.contains(axis == 0 ? infinity : 0, axis == 1 ? infinity : 0, axis == 2 ? infinity : 0);
            // Assert
            assertThat(inside).isFalse();
        }
    }

    @Nested
    class WithinReach {
        /**
         * When a point is exactly 7.25 blocks away along one axis, withinReach should answer true; a hair farther it
         * should answer false; the box's far corner (4, 3.5, 4, about 6.65 blocks) should be within reach, and a point
         * just past it (5, 3.5, 5, about 7.9 blocks) should not.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @ParameterizedTest(name = "({0}, {1}, {2}) within reach = {3}")
        @CsvSource({
            "7.25, 0, 0, true",
            "0, 7.25, 0, true",
            "0, 0, -7.25, true",
            "7.26, 0, 0, false",
            "0, -7.26, 0, false",
            "0, 0, 7.26, false",
            "4, 3.5, 4, true",
            "5, 3.5, 5, false"
        })
        void reachIsSevenAndAQuarter(double dx, double dy, double dz, boolean within) {
            assertThat(GuardArea.withinReach(dx, dy, dz)).isEqualTo(within);
        }

        /**
         * When any offset is NaN, withinReach should answer false.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @Test
        void nanIsOutOfReach() {
            // Act & Assert
            assertThat(GuardArea.withinReach(Double.NaN, 0, 0)).isFalse();
            assertThat(GuardArea.withinReach(0, Double.NaN, 0)).isFalse();
            assertThat(GuardArea.withinReach(0, 0, Double.NaN)).isFalse();
        }

        /**
         * When any offset is infinite, either sign, withinReach should answer false.
         *
         * <p>Method under test: {@link GuardArea#withinReach(double, double, double)}
         */
        @ParameterizedTest(name = "infinity {0} on axis {1} is out of reach")
        @CsvSource({
            "POSITIVE_INFINITY, 0",
            "NEGATIVE_INFINITY, 0",
            "POSITIVE_INFINITY, 1",
            "NEGATIVE_INFINITY, 1",
            "POSITIVE_INFINITY, 2",
            "NEGATIVE_INFINITY, 2"
        })
        void infinityIsOutOfReach(String which, int axis) {
            // Arrange
            double infinity = which.equals("POSITIVE_INFINITY") ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            // Act
            boolean within =
                    GuardArea.withinReach(axis == 0 ? infinity : 0, axis == 1 ? infinity : 0, axis == 2 ? infinity : 0);
            // Assert
            assertThat(within).isFalse();
        }
    }
}
