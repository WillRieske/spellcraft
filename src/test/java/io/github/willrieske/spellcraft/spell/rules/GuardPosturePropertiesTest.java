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
            GuardPosture posture = GuardPosture.of(place, dark);
            double side = posture.sideways(sideRoll);

            boolean inPlace =
                    posture.inPlace(Math.cos(angle) * side, posture.height(heightRoll), Math.sin(angle) * side);

            assertThat(inPlace).isTrue();
        }
    }

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
            GuardPosture posture = GuardPosture.of(place, dark);

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
            int interval = GuardPosture.of(place, dark).scanInterval();

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
            GuardPosture posture = GuardPosture.of(place, dark);

            assertThat(posture.sideMax()).isLessThanOrEqualTo(GuardPosture.WIDE.sideMax());
            assertThat(posture.scanInterval()).isLessThanOrEqualTo(GuardPosture.WIDE.scanInterval());
        }

        /**
         * For every close posture and roll, the height should be at least 2.2: above the summoner's head.
         *
         * <p>Method under test: {@link GuardPosture#height(double)}
         */
        @Property
        void closePosturesStayOverhead(
                @ForAll Place place, @ForAll boolean dark, @ForAll @DoubleRange(min = 0, max = 1) double roll) {
            GuardPosture posture = GuardPosture.of(place, dark);

            if (posture.close()) {
                assertThat(posture.height(roll)).isGreaterThanOrEqualTo(2.2);
            }
        }
    }
}
