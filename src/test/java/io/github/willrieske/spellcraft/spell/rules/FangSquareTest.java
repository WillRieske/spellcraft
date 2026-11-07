package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.willrieske.spellcraft.spell.rules.FangSquare.Cell;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FangSquareTest {
    @Nested
    class Of {
        /**
         * When the caster faces north (yaw 180), of should put the square's center 6 blocks north, so the caster
         * stands mid-way along its south edge (F1).
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @Test
        void facingNorthPutsTheCasterMidWayAlongTheSouthEdge() {
            // Act
            FangSquare square = FangSquare.of(180);
            // Assert
            assertThat(square).isEqualTo(new FangSquare(0, -6));
            assertThat(square.minDx()).isEqualTo(-6);
            assertThat(square.maxDx()).isEqualTo(6);
            assertThat(square.minDz()).isEqualTo(-12);
            assertThat(square.maxDz()).isEqualTo(0);
        }

        /**
         * When the caster faces south, east or west, of should put the center 6 blocks that way (F2).
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @ParameterizedTest(name = "yaw {0} -> center ({1}, {2})")
        @CsvSource({"0, 0, 6", "-90, 6, 0", "90, -6, 0", "180, 0, -6"})
        void cardinalFacingsCenterTheSquareAhead(double yaw, int centerDx, int centerDz) {
            assertThat(FangSquare.of(yaw)).isEqualTo(new FangSquare(centerDx, centerDz));
        }

        /**
         * When the caster faces north-east (yaw -135), of should put the caster on the south-west corner (F3).
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @Test
        void facingNorthEastPutsTheCasterOnTheSouthWestCorner() {
            // Act
            FangSquare square = FangSquare.of(-135);
            // Assert
            assertThat(square).isEqualTo(new FangSquare(6, -6));
            assertThat(square.minDx()).isEqualTo(0);
            assertThat(square.minDz()).isEqualTo(-12);
        }

        /**
         * When the caster faces a diagonal, of should put the center on that corner of the +/-6 box (F3).
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @ParameterizedTest(name = "yaw {0} -> center ({1}, {2})")
        @CsvSource({"45, -6, 6", "-45, 6, 6", "135, -6, -6", "-135, 6, -6"})
        void diagonalFacingsCenterTheSquareOnTheCorner(double yaw, int centerDx, int centerDz) {
            assertThat(FangSquare.of(yaw)).isEqualTo(new FangSquare(centerDx, centerDz));
        }

        /**
         * When the caster turns a little either side of north, of should keep the center 6 blocks north and shift it
         * sideways by whole blocks, never backwards (F4).
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @Test
        void turningShiftsTheCenterAlongTheEdgeBlockByBlock() {
            // Arrange
            int previousDx = Integer.MIN_VALUE;
            for (int yaw = 160; yaw <= 200; yaw++) {
                // Act
                FangSquare square = FangSquare.of(yaw);
                // Assert
                assertThat(square.centerDz()).as("yaw " + yaw).isEqualTo(-6);
                assertThat(square.centerDx())
                        .as("yaw " + yaw + " never shifts back")
                        .isGreaterThanOrEqualTo(previousDx);
                previousDx = square.centerDx();
            }
            // Act & Assert
            assertThat(FangSquare.of(160).centerDx()).isEqualTo(-2);
            assertThat(FangSquare.of(200).centerDx()).isEqualTo(2);
        }

        /**
         * When the yaw is outside -180…180, of should answer as for the equivalent yaw (F7).
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @Test
        void yawWrapsAround() {
            // Act & Assert
            assertThat(FangSquare.of(540)).isEqualTo(FangSquare.of(180));
            assertThat(FangSquare.of(-270)).isEqualTo(FangSquare.of(90));
        }

        /**
         * When the yaw is NaN or infinite, of should throw, not answer a square the caster is not on the edge of (F8).
         *
         * <p>Method under test: {@link FangSquare#of(double)}
         */
        @Test
        void rejectsAYawThatIsNotFinite() {
            // Act & Assert
            assertThatThrownBy(() -> FangSquare.of(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FangSquare.of(Double.POSITIVE_INFINITY))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FangSquare.of(Double.NEGATIVE_INFINITY))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Cells {
        /**
         * When the cells are listed, there should be 168 distinct cells inside the square and none at the caster's
         * block (F5).
         *
         * <p>Method under test: {@link FangSquare#cells()}
         */
        @Test
        void listsEveryCellButTheCasters() {
            // Arrange
            FangSquare square = FangSquare.of(-135);
            // Act
            List<Cell> cells = square.cells();
            // Assert
            assertThat(cells).hasSize(FangSquare.CELL_COUNT).doesNotHaveDuplicates();
            assertThat(cells).noneMatch(cell -> cell.dx() == 0 && cell.dz() == 0);
            assertThat(cells).allMatch(cell -> square.contains(cell.dx(), cell.dz()));
        }

        /**
         * When the cells are listed, each delay should be the Chebyshev distance from the caster minus one: 0 next to
         * the caster, 11 at the far corner (F6).
         *
         * <p>Method under test: {@link FangSquare#cells()}
         */
        @Test
        void delaysRippleOutward() {
            // Arrange
            FangSquare square = FangSquare.of(180);
            // Act
            List<Cell> cells = square.cells();
            // Assert
            assertThat(cells)
                    .contains(new Cell(0, -1, 0), new Cell(1, 0, 0), new Cell(-6, -12, 11), new Cell(6, -12, 11));
            assertThat(cells)
                    .allMatch(cell -> cell.delayTicks() == Math.max(Math.abs(cell.dx()), Math.abs(cell.dz())) - 1);
        }
    }

    @Nested
    class Contains {
        /**
         * When asked about cells on and beyond the square's edge, contains should answer true on the edge and false
         * one block beyond.
         *
         * <p>Method under test: {@link FangSquare#contains(int, int)}
         */
        @Test
        void edgesAreInsideAndTheNextBlockIsNot() {
            // Arrange
            FangSquare square = FangSquare.of(180);
            // Act & Assert
            assertThat(square.contains(0, 0)).isTrue();
            assertThat(square.contains(-6, -12)).isTrue();
            assertThat(square.contains(6, 0)).isTrue();
            assertThat(square.contains(7, 0)).isFalse();
            assertThat(square.contains(0, 1)).isFalse();
            assertThat(square.contains(0, -13)).isFalse();
        }
    }
}
