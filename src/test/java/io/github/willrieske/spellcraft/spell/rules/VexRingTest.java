package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.willrieske.spellcraft.spell.rules.VexRing.Offset;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VexRingTest {
    @Nested
    class OffsetAt {
        /**
         * When every index is asked for, offset should give 40 distinct cells, each 2 or 3 blocks from the caster on
         * its farther axis, and every such cell exactly once.
         *
         * <p>Method under test: {@link VexRing#offset(int)}
         */
        @Test
        void coversTheRingExactlyOnce() {
            // Act
            Set<Offset> offsets = new HashSet<>();
            IntStream.range(0, VexRing.COUNT).forEach(index -> offsets.add(VexRing.offset(index)));
            // Assert
            assertThat(offsets).hasSize(40);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dz));
                    boolean inRing = distance >= 2;
                    assertThat(offsets.contains(new Offset(dx, dz)))
                            .as("(%d, %d)", dx, dz)
                            .isEqualTo(inRing);
                }
            }
        }

        /**
         * When the first and last index are asked for, offset should give the ring's corners in the spec's fixed order
         * (north row first, west to east).
         *
         * <p>Method under test: {@link VexRing#offset(int)}
         */
        @Test
        void listsTheCellsInFixedOrder() {
            // Act & Assert
            assertThat(VexRing.offset(0)).isEqualTo(new Offset(-3, -3));
            assertThat(VexRing.offset(39)).isEqualTo(new Offset(3, 3));
        }

        /**
         * When the index is outside 0…39, offset should throw.
         *
         * <p>Method under test: {@link VexRing#offset(int)}
         */
        @Test
        void rejectsIndexesOutsideTheRing() {
            // Act & Assert
            assertThatThrownBy(() -> VexRing.offset(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> VexRing.offset(VexRing.COUNT)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class LifeTicks {
        /**
         * When the roll is 0 or 89, lifeTicks should give the evoker's 30 s and 119 s (20 · (30 + roll)).
         *
         * <p>Method under test: {@link VexRing#lifeTicks(int)}
         */
        @Test
        void followsTheEvokersFormula() {
            // Act & Assert
            assertThat(VexRing.lifeTicks(0)).isEqualTo(600);
            assertThat(VexRing.lifeTicks(89)).isEqualTo(2380);
        }

        /**
         * When the roll is outside 0…89, lifeTicks should throw.
         *
         * <p>Method under test: {@link VexRing#lifeTicks(int)}
         */
        @Test
        void rejectsRollsOutsideTheBound() {
            // Act & Assert
            assertThatThrownBy(() -> VexRing.lifeTicks(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> VexRing.lifeTicks(90)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
