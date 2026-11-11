package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class GhostRecordsPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Add {
        /**
         * For any list and maximum, add should never return more than the maximum.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Property
        void neverMoreThanMax(
                @ForAll List<@IntRange(min = 0, max = 1000) Integer> records,
                @ForAll @IntRange(min = 0, max = 1000) int record,
                @ForAll @IntRange(min = 1, max = 40) int max) {
            assertThat(GhostRecords.add(records, record, max)).hasSizeLessThanOrEqualTo(max);
        }

        /**
         * For any list and maximum, add should keep the newest record, last.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Property
        void keepsTheNewestLast(
                @ForAll List<@IntRange(min = 0, max = 1000) Integer> records,
                @ForAll @IntRange(min = 0, max = 1000) int record,
                @ForAll @IntRange(min = 1, max = 40) int max) {
            assertThat(GhostRecords.add(records, record, max)).last().isEqualTo(record);
        }

        /**
         * For any list and maximum, the records add drops should all be older than the ones it keeps: the result is the
         * tail of the old list plus the new record.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Property
        void dropsOnlyTheOldest(
                @ForAll List<@IntRange(min = 0, max = 1000) Integer> records,
                @ForAll @IntRange(min = 0, max = 1000) int record,
                @ForAll @IntRange(min = 1, max = 40) int max) {
            // Arrange
            List<Integer> all = new ArrayList<>(records);
            all.add(record);
            // Act & Assert
            assertThat(GhostRecords.add(records, record, max))
                    .isEqualTo(all.subList(Math.max(0, all.size() - max), all.size()));
        }
    }
}
