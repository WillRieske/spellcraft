package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GhostRecordsTest {
    @Nested
    class Add {
        /**
         * When there is room, add should append the record and keep the order.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void appendsWhenThereIsRoom() {
            assertThat(GhostRecords.add(List.of(1, 2), 3, 3)).containsExactly(1, 2, 3);
        }

        /**
         * When the list is full, add should drop the oldest (first) record and keep the newest.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void dropsTheOldestWhenFull() {
            assertThat(GhostRecords.add(List.of(1, 2, 3), 4, 3)).containsExactly(2, 3, 4);
        }

        /**
         * When called, add should not change the list it was given.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void leavesTheInputAlone() {
            // Arrange
            List<Integer> before = new ArrayList<>(List.of(1, 2, 3));
            // Act
            GhostRecords.add(before, 4, 3);
            // Assert
            assertThat(before).containsExactly(1, 2, 3);
        }

        /**
         * When the maximum is below one, add should refuse (a list that can hold nothing).
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void refusesAMaximumBelowOne() {
            assertThatThrownBy(() -> GhostRecords.add(List.of(), 1, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * When the list is already over the maximum (an old save), add should trim it to the maximum, oldest first.
         *
         * <p>Method under test: {@link GhostRecords#add(List, Object, int)}
         */
        @Test
        void trimsAnOverlongInput() {
            assertThat(GhostRecords.add(List.of(1, 2, 3, 4), 5, 2)).containsExactly(4, 5);
        }
    }

    /**
     * When the constant is read, it should be 32.
     *
     * <p>Method under test: {@link GhostRecords#MAX}
     */
    @Test
    void maxIs32() {
        assertThat(GhostRecords.MAX).isEqualTo(32);
    }
}
