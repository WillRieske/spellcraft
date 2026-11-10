package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.GuardPosture.Place;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.Scale;

class GuardPosturePropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class InPlace {
        /**
         * For every place, darkness, rolls and angle, a spot built from the posture's own sideways and height should
         * count as in place, so one hop ends the move.
         *
         * <p>Method under test: {@link GuardPosture#inPlace(double, double, double)}
         */
        @Property
        void spot_isAlwaysInPlace(
                @ForAll Place place,
                @ForAll boolean dark,
                @ForAll @DoubleRange(min = 0, max = 1) double sideRoll,
                @ForAll @DoubleRange(min = 0, max = 1) double heightRoll,
                @ForAll @DoubleRange(min = 0, max = 6.283) @Scale(3) double angle) {
            // Arrange
            GuardPosture posture = GuardPosture.of(place, dark);
            double side = posture.sideways(sideRoll);
            // Act
            boolean inPlace =
                    posture.inPlace(Math.cos(angle) * side, posture.height(heightRoll), Math.sin(angle) * side);
            // Assert
            assertThat(inPlace).isTrue();
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Spots {
        /**
         * For every place, darkness and rolls from 0 to 1, sideways should land within the row's sideways range and
         * height within its height range.
         *
         * <p>Methods under test: {@link GuardPosture#sideways(double)}, {@link GuardPosture#height(double)}
         */
        @Property
        void everySpotIsInsideItsRowsRanges(
                @ForAll Place place,
                @ForAll boolean dark,
                @ForAll @DoubleRange(min = 0, max = 1) double sideRoll,
                @ForAll @DoubleRange(min = 0, max = 1) double heightRoll) {
            // Arrange
            GuardPosture posture = GuardPosture.of(place, dark);
            // Act
            double side = posture.sideways(sideRoll);
            double height = posture.height(heightRoll);
            // Assert
            assertThat(side).isBetween(posture.sideMin(), posture.sideMax());
            assertThat(height).isBetween(posture.heightMin(), posture.heightMax());
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Of {
        /**
         * For every place and darkness, the band should have a positive width and height.
         *
         * <p>Method under test: {@link GuardPosture#of(Place, boolean)}
         */
        @Property
        void bandsAreNotEmpty(@ForAll Place place, @ForAll boolean dark) {
            // Arrange & Act
            GuardPosture posture = GuardPosture.of(place, dark);

            // Assert
            assertThat(posture.sideMin()).isLessThan(posture.sideMax());
            assertThat(posture.heightMin()).isLessThan(posture.heightMax());
        }

        /**
         * For every place and darkness, the scan interval should be even and at least 2: the goal clock runs every
         * second tick.
         *
         * <p>Method under test: {@link GuardPosture#of(Place, boolean)}
         */
        @Property
        void scanIntervalIsEvenAndAtLeastTwo(@ForAll Place place, @ForAll boolean dark) {
            // Arrange & Act
            int interval = GuardPosture.of(place, dark).scanInterval();

            // Assert
            assertThat(interval).isGreaterThanOrEqualTo(2);
            assertThat(interval % 2).isZero();
        }

        /**
         * For every place and darkness, the posture should be no wider and scan no slower than the wide row.
         *
         * <p>Method under test: {@link GuardPosture#of(Place, boolean)}
         */
        @Property
        void noPostureIsWiderOrSlowerThanWide(@ForAll Place place, @ForAll boolean dark) {
            // Arrange & Act
            GuardPosture posture = GuardPosture.of(place, dark);

            // Assert
            assertThat(posture.sideMax()).isLessThanOrEqualTo(GuardPosture.WIDE.sideMax());
            assertThat(posture.scanInterval()).isLessThanOrEqualTo(GuardPosture.WIDE.scanInterval());
        }

        /**
         * For every close posture and roll, the height should be at least 2.5: above the summoner's head.
         *
         * <p>Method under test: {@link GuardPosture#height(double)}
         */
        @Property
        void closePosturesStayOverhead(
                @ForAll Place place, @ForAll boolean dark, @ForAll @DoubleRange(min = 0, max = 1) double roll) {
            // Arrange & Act
            GuardPosture posture = GuardPosture.of(place, dark);

            // Assert
            if (posture.close()) {
                assertThat(posture.height(roll)).isGreaterThanOrEqualTo(2.5);
            }
        }
    }
}
