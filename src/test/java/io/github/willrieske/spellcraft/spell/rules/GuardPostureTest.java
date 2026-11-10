package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.GuardPosture.Place;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GuardPostureTest {
    private static void assertRow(
            GuardPosture posture,
            double sideMin,
            double sideMax,
            double heightMin,
            double heightMax,
            double speed,
            int scanInterval,
            boolean close) {
        assertThat(posture.sideMin()).isEqualTo(sideMin);
        assertThat(posture.sideMax()).isEqualTo(sideMax);
        assertThat(posture.heightMin()).isEqualTo(heightMin);
        assertThat(posture.heightMax()).isEqualTo(heightMax);
        assertThat(posture.speed()).isEqualTo(speed);
        assertThat(posture.scanInterval()).isEqualTo(scanInterval);
        assertThat(posture.close()).isEqualTo(close);
    }

    @Nested
    class Of {
        /**
         * When the place is the Overworld by day, of should give today's wide band at the 10-tick scan.
         *
         * <p>Method under test: {@link GuardPosture#of(Place, boolean)}
         */
        @Test
        void overworldDay_isTheWideRow() {
            GuardPosture posture = GuardPosture.of(Place.OVERWORLD, false);

            assertRow(posture, 2.0, 3.5, 0.5, 1.5, 0.7, 10, false);
            assertThat(posture).isSameAs(GuardPosture.WIDE);
        }

        /**
         * When the place is the Overworld in the dark, of should give the near, overhead band at the 4-tick scan.
         *
         * <p>Method under test: {@link GuardPosture#of(Place, boolean)}
         */
        @Test
        void overworldDark_isTheNearRow() {
            assertRow(GuardPosture.of(Place.OVERWORLD, true), 1.0, 2.0, 2.2, 3.0, 0.35, 4, true);
        }

        /**
         * When the place is the Nether, of should give the near row whether or not it is dark.
         *
         * <p>Method under test: {@link GuardPosture#of(Place, boolean)}
         */
        @ParameterizedTest(name = "dark = {0}")
        @CsvSource({"true", "false"})
        void nether_isTheNearRow(boolean dark) {
            assertRow(GuardPosture.of(Place.NETHER, dark), 1.0, 2.0, 2.2, 3.0, 0.35, 4, true);
        }

        /**
         * When the place is the End, of should give the nearest row at the 2-tick scan whether or not it is dark.
         *
         * <p>Method under test: {@link GuardPosture#of(Place, boolean)}
         */
        @ParameterizedTest(name = "dark = {0}")
        @CsvSource({"true", "false"})
        void end_isTheNearestRow(boolean dark) {
            assertRow(GuardPosture.of(Place.END, dark), 0.5, 1.5, 2.2, 3.0, 0.35, 2, true);
        }

        /**
         * When the place is a modded dimension, of should give the wide row whether or not it is dark.
         *
         * <p>Method under test: {@link GuardPosture#of(Place, boolean)}
         */
        @ParameterizedTest(name = "dark = {0}")
        @CsvSource({"true", "false"})
        void other_isTheWideRow(boolean dark) {
            assertThat(GuardPosture.of(Place.OTHER, dark)).isSameAs(GuardPosture.WIDE);
        }
    }

    @Nested
    class InPlace {
        /**
         * When the posture is wide, inPlace should be true within 4 blocks of the feet (the middle too) and false
         * beyond, measured in three dimensions.
         *
         * <p>Method under test: {@link GuardPosture#inPlace(double, double, double)}
         */
        @ParameterizedTest(name = "wide ({0}, {1}, {2}) in place = {3}")
        @CsvSource({
            "4, 0, 0, true",
            "4.01, 0, 0, false",
            "0, 4, 0, true",
            "0, 4.01, 0, false",
            "0, 0, 4, true",
            "0, 0, 4.01, false",
            "0, 0, 0, true",
            "3, 3, 0, false"
        })
        void wide_isWithinFourBlocks(double dx, double dy, double dz, boolean expected) {
            assertThat(GuardPosture.WIDE.inPlace(dx, dy, dz)).isEqualTo(expected);
        }

        /**
         * When the posture is the dark Overworld's, inPlace should allow the outer edge plus 0.5 sideways and the
         * height range widened by 0.5 each way, with no inner limit.
         *
         * <p>Method under test: {@link GuardPosture#inPlace(double, double, double)}
         */
        @ParameterizedTest(name = "near ({0}, {1}, {2}) in place = {3}")
        @CsvSource({
            "2.5, 2.2, 0, true",
            "2.51, 2.2, 0, false",
            "1, 1.7, 0, true",
            "1, 1.69, 0, false",
            "1, 3.5, 0, true",
            "1, 3.51, 0, false",
            "0, 2.5, 0, true",
            "0, 2.5, 2.5, true",
            "0, 2.5, 2.51, false",
            "0, 2.5, -2.51, false"
        })
        void near_isTheBandWidenedByHalfABlock(double dx, double dy, double dz, boolean expected) {
            assertThat(GuardPosture.of(Place.OVERWORLD, true).inPlace(dx, dy, dz))
                    .isEqualTo(expected);
        }

        /**
         * When any offset is NaN, inPlace should answer false in both kinds of posture.
         *
         * <p>Method under test: {@link GuardPosture#inPlace(double, double, double)}
         */
        @Test
        void nan_isNeverInPlace() {
            assertThat(GuardPosture.WIDE.inPlace(Double.NaN, 0, 0)).isFalse();
            assertThat(GuardPosture.of(Place.END, false).inPlace(0, Double.NaN, 0))
                    .isFalse();
            assertThat(GuardPosture.of(Place.END, false).inPlace(0, 2.5, Double.NaN))
                    .isFalse();
        }
    }

    @Nested
    class Sideways {
        /**
         * When the roll is 0, 0.5 or 1, sideways should give the inner edge, the middle and the outer edge.
         *
         * <p>Method under test: {@link GuardPosture#sideways(double)}
         */
        @Test
        void spansTheBand() {
            GuardPosture posture = GuardPosture.of(Place.NETHER, false);

            assertThat(posture.sideways(0)).isEqualTo(posture.sideMin());
            assertThat(posture.sideways(1)).isEqualTo(posture.sideMax());
            assertThat(posture.sideways(0.5)).isEqualTo(1.5);
        }

        /**
         * When the band is wider than a block, sideways should scale the roll by the band's width.
         *
         * <p>Method under test: {@link GuardPosture#sideways(double)}
         */
        @Test
        void inAWideBand_scalesByTheWidth() {
            assertThat(GuardPosture.WIDE.sideways(0.5)).isEqualTo(2.75);
        }
    }

    @Nested
    class Height {
        /**
         * When the roll is 0, 0.5 or 1, height should give the lowest height, the middle and the highest.
         *
         * <p>Method under test: {@link GuardPosture#height(double)}
         */
        @Test
        void spansTheBand() {
            GuardPosture posture = GuardPosture.of(Place.NETHER, false);

            assertThat(posture.height(0)).isEqualTo(posture.heightMin());
            assertThat(posture.height(1)).isEqualTo(posture.heightMax());
            assertThat(posture.height(0.5)).isEqualTo(2.6);
        }
    }
}
