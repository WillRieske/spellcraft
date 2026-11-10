package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VoidRuleTest {
    private static boolean noGround() {
        return false;
    }

    @Nested
    class NeedsCatch {
        /**
         * When the player is more than 4 blocks under the floor, needsCatch should be true however the other flags
         * stand, even with ground below.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @ParameterizedTest(name = "onGround = {0}, movingDown = {1}")
        @CsvSource({"false, false", "false, true", "true, false", "true, true"})
        void wellBelowTheFloor_isAlwaysCaught(boolean onGround, boolean movingDown) {
            // Arrange
            BooleanSupplier groundBelow = () -> true;

            // Act
            boolean caught = VoidRule.needsCatch(-4.01, 0, onGround, movingDown, false, groundBelow);

            // Assert
            assertThat(caught).isTrue();
        }

        /**
         * When the player stands exactly 4 blocks under the floor, needsCatch should be false: the margin is strict.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void exactlyFourBelowStanding_isNotCaught() {
            assertThat(VoidRule.needsCatch(-4, 0, true, false, false, VoidRuleTest::noGround))
                    .isFalse();
        }

        /**
         * When the level's floor is not 0, needsCatch should measure from that floor.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void aLowerFloor_movesTheMargin() {
            // Act & Assert
            assertThat(VoidRule.needsCatch(-64.01, -60, true, false, false, VoidRuleTest::noGround))
                    .isTrue();
            assertThat(VoidRule.needsCatch(-63.99, -60, true, false, false, VoidRuleTest::noGround))
                    .isFalse();
            assertThat(VoidRule.needsCatch(-43.9, -60, false, true, false, VoidRuleTest::noGround))
                    .isFalse();
            assertThat(VoidRule.needsCatch(-44.1, -60, false, true, false, VoidRuleTest::noGround))
                    .isTrue();
        }

        /**
         * When the player falls in the air under the floor plus 16 with no ground below, needsCatch should be true.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void fallingNearTheFloorWithNoGround_isCaught() {
            assertThat(VoidRule.needsCatch(15.9, 0, false, true, false, VoidRuleTest::noGround))
                    .isTrue();
        }

        /**
         * When the player falls at exactly the floor plus 16, needsCatch should be false: the limit is strict.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void fallingAtSixteen_isNotCaught() {
            assertThat(VoidRule.needsCatch(16, 0, false, true, false, VoidRuleTest::noGround))
                    .isFalse();
        }

        /**
         * When there is ground within 64 blocks below, needsCatch should be false.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void groundBelow_isNotCaught() {
            assertThat(VoidRule.needsCatch(15.9, 0, false, true, false, () -> true))
                    .isFalse();
        }

        /**
         * When the player stands on something, needsCatch should be false near the floor.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void onGround_isNotCaught() {
            assertThat(VoidRule.needsCatch(15.9, 0, true, true, false, VoidRuleTest::noGround))
                    .isFalse();
        }

        /**
         * When the player is not moving down, needsCatch should be false near the floor.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void notMovingDown_isNotCaught() {
            assertThat(VoidRule.needsCatch(15.9, 0, false, false, false, VoidRuleTest::noGround))
                    .isFalse();
        }

        /**
         * When the player flies, needsCatch should be false in every case, even at y = -100.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @ParameterizedTest(name = "y = {0}, onGround = {1}, movingDown = {2}")
        @CsvSource({
            "-100, false, true",
            "-100, true, false",
            "-4.01, false, false",
            "15.9, false, true",
            "15.9, true, true"
        })
        void flying_isNeverCaught(double y, boolean onGround, boolean movingDown) {
            assertThat(VoidRule.needsCatch(y, 0, onGround, movingDown, true, VoidRuleTest::noGround))
                    .isFalse();
        }

        /**
         * When the player is at or above the floor plus 16, needsCatch should not ask about ground: it is a block ray.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void highUp_doesNotCastTheRay() {
            // Arrange
            AtomicInteger asked = new AtomicInteger();
            BooleanSupplier counting = () -> {
                asked.incrementAndGet();
                return false;
            };

            // Act
            boolean caught = VoidRule.needsCatch(16, 0, false, true, false, counting);

            // Assert
            assertThat(caught).isFalse();
            assertThat(asked).hasValue(0);
        }

        /**
         * When the player is on the ground or not moving down, needsCatch should not ask about ground either.
         *
         * <p>Method under test: {@link VoidRule#needsCatch(double, int, boolean, boolean, boolean, BooleanSupplier)}
         */
        @Test
        void onGroundOrRising_doesNotCastTheRay() {
            // Arrange
            AtomicInteger asked = new AtomicInteger();
            BooleanSupplier counting = () -> {
                asked.incrementAndGet();
                return false;
            };

            // Act
            VoidRule.needsCatch(10, 0, true, true, false, counting);
            VoidRule.needsCatch(10, 0, false, false, false, counting);

            // Assert
            assertThat(asked).hasValue(0);
        }
    }
}
