package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.FangSquare.Cell;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

class FangSquarePropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Of {
        /**
         * For any yaw, of should put the caster on an edge of the square: the larger of the center's offsets is
         * exactly 6.
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @Property
        void theCasterIsAlwaysOnAnEdge(@ForAll double yaw) {
            // Act
            FangSquare square = FangSquare.of(yaw);
            // Assert
            assertThat(Math.max(Math.abs(square.centerDx()), Math.abs(square.centerDz())))
                    .isEqualTo(FangSquare.HALF);
        }

        /**
         * For any yaw, turning a quarter turn clockwise should rotate the center a quarter turn: (dx, dz) becomes
         * (-dz, dx).
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @Property
        void aQuarterTurnRotatesTheCenter(@ForAll @DoubleRange(min = -360, max = 360) double yaw) {
            // Arrange
            FangSquare before = FangSquare.of(yaw);
            // Act
            FangSquare after = FangSquare.of(yaw + 90);
            // Assert
            assertThat(after).isEqualTo(new FangSquare(-before.centerDz(), before.centerDx()));
        }

        /**
         * For any yaw, of should answer the same for yaw + 360.
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @Property
        void aFullTurnChangesNothing(@ForAll @DoubleRange(min = -360, max = 360) double yaw) {
            assertThat(FangSquare.of(yaw + 360)).isEqualTo(FangSquare.of(yaw));
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Cells {
        /**
         * For any yaw, cells should hold 168 distinct cells, all inside the square (which includes the caster's
         * block), none at the caster's block, each delayed by its Chebyshev distance from the caster minus one (0 to
         * 11).
         *
         * <p>Method under test: {@link FangSquare#cells()}
         */
        @Property
        void cellsCoverTheSquareButTheCastersBlock(@ForAll @DoubleRange(min = -720, max = 720) double yaw) {
            // Arrange
            FangSquare square = FangSquare.of(yaw);
            // Act
            List<Cell> cells = square.cells();
            // Assert
            assertThat(square.contains(0, 0)).isTrue();
            assertThat(cells).hasSize(FangSquare.CELL_COUNT).doesNotHaveDuplicates();
            assertThat(cells).allMatch(cell -> square.contains(cell.dx(), cell.dz()));
            assertThat(cells).noneMatch(cell -> cell.dx() == 0 && cell.dz() == 0);
            assertThat(cells)
                    .allMatch(cell -> cell.delayTicks() == Math.max(Math.abs(cell.dx()), Math.abs(cell.dz())) - 1
                            && cell.delayTicks() <= 11);
        }
    }
}
