package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class VexCapPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class ToSummon {
        /**
         * For any count up to the cap, the count plus what toSummon answers should never exceed the cap, and the
         * answer should never exceed a batch or go negative.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Property
        void neverOverTheCapAndNeverMoreThanABatch(@ForAll @IntRange(min = 0, max = 12) int count) {
            // Act
            int summoned = VexCap.toSummon(count);
            // Assert
            assertThat(summoned).isBetween(0, VexCap.BATCH);
            assertThat(count + summoned).isLessThanOrEqualTo(VexCap.CAP);
        }

        /**
         * For any count at or over the cap, toSummon should answer zero.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Property
        void nothingAtOrOverTheCap(@ForAll @IntRange(min = 12, max = 1000) int count) {
            assertThat(VexCap.toSummon(count)).isZero();
        }

        /**
         * For any count under the cap, toSummon should answer at least one: a summoner with room always gets a vex.
         *
         * <p>Method under test: {@link VexCap#toSummon(int)}
         */
        @Property
        void roomMeansAtLeastOne(@ForAll @IntRange(min = 0, max = 11) int count) {
            assertThat(VexCap.toSummon(count)).isPositive();
        }
    }
}
