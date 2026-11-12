package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

/** Functional spec §3.1. */
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
}
