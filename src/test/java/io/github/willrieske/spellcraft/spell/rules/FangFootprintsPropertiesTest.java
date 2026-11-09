package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.FangFootprints.Column;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

class FangFootprintsPropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Columns {
        /**
         * For any box that fits inside one block on both axes, columns should answer exactly that block.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Property
        void aBoxInsideOneBlockGivesOneColumn(
                @ForAll @IntRange(min = -50, max = 50) int bx,
                @ForAll @IntRange(min = -50, max = 50) int bz,
                @ForAll @DoubleRange(min = 0.0, max = 0.5) double startX,
                @ForAll @DoubleRange(min = 0.0, max = 0.5) double startZ,
                @ForAll @DoubleRange(min = 0.0, max = 0.49) double width) {
            // Act
            List<Column> columns =
                    FangFootprints.columns(bx + startX, bz + startZ, bx + startX + width, bz + startZ + width);
            // Assert
            assertThat(columns).containsExactly(new Column(bx, bz));
        }

        /**
         * For any box of width w ≤ 3 on each axis, columns should answer exactly the distinct columns the box has
         * positive width in (a zero-width axis still gives the column it sits in), and so at most (⌈w⌉ + 1)² of them.
         * (The spec's "a 3×3 box gives ≤ 9" holds only for a block-aligned box; a straddling 3.0-wide box touches four
         * columns per axis.)
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Property
        void columnsAreExactlyTheBlocksWithPositiveWidthInTheBox(
                @ForAll @DoubleRange(min = -40, max = 40) double minX,
                @ForAll @DoubleRange(min = -40, max = 40) double minZ,
                @ForAll @DoubleRange(min = 0.0, max = 3.0) double width) {
            // Arrange
            double maxX = minX + width;
            double maxZ = minZ + width;
            int perAxis = (int) Math.ceil(width) + 1;
            List<Column> expected = new ArrayList<>();
            for (int z = (int) Math.floor(minZ) - 1; z <= (int) Math.ceil(maxZ); z++) {
                for (int x = (int) Math.floor(minX) - 1; x <= (int) Math.ceil(maxX); x++) {
                    boolean xIn = (x + 1 > minX && x < maxX) || (maxX == minX && x == (int) Math.floor(minX));
                    boolean zIn = (z + 1 > minZ && z < maxZ) || (maxZ == minZ && z == (int) Math.floor(minZ));
                    if (xIn && zIn) {
                        expected.add(new Column(x, z));
                    }
                }
            }
            // Act
            List<Column> columns = FangFootprints.columns(minX, minZ, maxX, maxZ);
            // Assert
            assertThat(columns).containsExactlyElementsOf(expected);
            assertThat(columns).hasSizeLessThanOrEqualTo(perAxis * perAxis);
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class InSquare {
        /**
         * For any yaw, caster block and box near the caster, inSquare should answer exactly the input columns that lie
         * inside the square and are not the caster's own block, in input order.
         *
         * <p>Method under test: {@link FangFootprints#inSquare(List, FangSquare, int, int)}
         */
        @Property
        void keepsExactlyTheSquaresColumnsButTheCasters(
                @ForAll @DoubleRange(min = -360, max = 360) double yaw,
                @ForAll @IntRange(min = -100, max = 100) int feetX,
                @ForAll @IntRange(min = -100, max = 100) int feetZ,
                @ForAll @DoubleRange(min = -15, max = 15) double dx,
                @ForAll @DoubleRange(min = -15, max = 15) double dz,
                @ForAll @DoubleRange(min = 0.0, max = 3.0) double width) {
            // Arrange
            FangSquare square = FangSquare.of(yaw);
            List<Column> all = FangFootprints.columns(feetX + dx, feetZ + dz, feetX + dx + width, feetZ + dz + width);
            List<Column> expected = all.stream()
                    .filter(c -> square.contains(c.x() - feetX, c.z() - feetZ))
                    .filter(c -> !(c.x() == feetX && c.z() == feetZ))
                    .toList();
            // Act
            List<Column> kept = FangFootprints.inSquare(all, square, feetX, feetZ);
            // Assert
            assertThat(kept).containsExactlyElementsOf(expected);
        }
    }
}
