package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.willrieske.spellcraft.spell.rules.FangFootprints.Column;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FangFootprintsTest {
    @Nested
    class Columns {
        /**
         * When the box sits inside one block (a pig, 0.9 wide, centered on the block), columns should answer that one
         * column.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void aBoxInsideOneBlockGivesThatColumn() {
            assertThat(FangFootprints.columns(2.05, -3.95, 2.95, -3.05)).containsExactly(new Column(2, -4));
        }

        /**
         * When the box's edge sits exactly on a block boundary, columns should not claim the block beyond the edge:
         * [2.0, 3.0] is column 2 alone.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void anEdgeOnABoundaryClaimsNoBlockBeyondIt() {
            assertThat(FangFootprints.columns(2.0, 5.0, 3.0, 6.0)).containsExactly(new Column(2, 5));
        }

        /**
         * When the box straddles a boundary, columns should answer both blocks: [2.5, 3.5] is columns 2 and 3.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void aBoxAcrossABoundaryClaimsBothBlocks() {
            assertThat(FangFootprints.columns(2.5, 0.2, 3.5, 0.8)).containsExactly(new Column(2, 0), new Column(3, 0));
        }

        /**
         * When a ravager (1.95 wide) stands on the center of block (5, -6), columns should answer the 3×3 around it,
         * row by row. (Block -6 spans [-6, -5), so its center is z = -5.5.)
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void aRavagerOnABlockCenterCoversThreeByThree() {
            // Arrange
            double half = 1.95 / 2;
            // Act
            List<Column> columns = FangFootprints.columns(5.5 - half, -5.5 - half, 5.5 + half, -5.5 + half);
            // Assert
            assertThat(columns)
                    .containsExactly(
                            new Column(4, -7),
                            new Column(5, -7),
                            new Column(6, -7),
                            new Column(4, -6),
                            new Column(5, -6),
                            new Column(6, -6),
                            new Column(4, -5),
                            new Column(5, -5),
                            new Column(6, -5));
        }

        /**
         * When the box has no width on an axis (min equals max), columns should still answer the column it sits in; a
         * point on a boundary belongs to the block it starts.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void aZeroWidthBoxClaimsItsOwnColumn() {
            assertThat(FangFootprints.columns(2.5, 3.0, 2.5, 3.0)).containsExactly(new Column(2, 3));
        }

        /**
         * When a coordinate is NaN or infinite, or max is below min, columns should throw.
         *
         * <p>Method under test: {@link FangFootprints#columns(double, double, double, double)}
         */
        @Test
        void rejectsNonFiniteOrInvertedBoxes() {
            // Act & Assert
            assertThatThrownBy(() -> FangFootprints.columns(Double.NaN, 0, 1, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FangFootprints.columns(0, 0, Double.POSITIVE_INFINITY, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FangFootprints.columns(2, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FangFootprints.columns(0, 2, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class InSquare {
        /**
         * When columns lie inside the square (one of them beside the caster, in the caster's own row), on its edge, past
         * its edge and on the caster's own block, inSquare should keep the inside and edge ones, in order, and drop the
         * rest.
         *
         * <p>Method under test: {@link FangFootprints#inSquare(List, FangSquare, int, int)}
         */
        @Test
        void keepsTheSquaresColumnsButTheCasters() {
            // Arrange: facing north from (10, 20): dx -6…6, dz -12…0
            FangSquare square = FangSquare.of(180);
            List<Column> columns = List.of(
                    new Column(10, 20), // the caster's block
                    new Column(11, 20), // the east neighbor, in the caster's row: kept
                    new Column(10, 19), // just ahead
                    new Column(17, 19), // dx 7: past the east edge
                    new Column(16, 8), // the far corner, on the edge
                    new Column(10, 7), // dz -13: past the far edge
                    new Column(10, 21)); // behind
            // Act
            List<Column> kept = FangFootprints.inSquare(columns, square, 10, 20);
            // Assert
            assertThat(kept).containsExactly(new Column(11, 20), new Column(10, 19), new Column(16, 8));
        }

        /**
         * When no column is inside, inSquare should answer an empty list.
         *
         * <p>Method under test: {@link FangFootprints#inSquare(List, FangSquare, int, int)}
         */
        @Test
        void answersEmptyWhenNothingIsInside() {
            assertThat(FangFootprints.inSquare(List.of(new Column(0, 5)), FangSquare.of(180), 0, 0))
                    .isEmpty();
        }
    }
}
