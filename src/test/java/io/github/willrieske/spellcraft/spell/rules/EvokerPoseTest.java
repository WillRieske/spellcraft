package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.willrieske.spellcraft.spell.rules.EvokerPose.ArmRotation;
import io.github.willrieske.spellcraft.spell.rules.EvokerPose.HandOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EvokerPoseTest {
    @Nested
    class ArmRotationAt {
        /**
         * When the right arm is posed, armRotation should raise it to the illager's spellcasting pose: x swaying with
         * age, y zero, z three quarters of pi.
         *
         * <p>Method under test: {@link EvokerPose#armRotation(float, boolean)}
         */
        @Test
        void raisesTheRightArmLikeAnIllager() {
            // Act
            ArmRotation rotation = EvokerPose.armRotation(10.0F, true);
            // Assert
            assertThat(rotation.xRot()).isCloseTo((float) Math.cos(10.0F * 0.6662F) * 0.25F, within(1e-6F));
            assertThat(rotation.yRot()).isEqualTo(0.0F);
            assertThat(rotation.zRot()).isCloseTo((float) (Math.PI * 3.0 / 4.0), within(1e-6F));
        }

        /**
         * When the left arm is posed, armRotation should mirror the z rotation and keep the same sway.
         *
         * <p>Method under test: {@link EvokerPose#armRotation(float, boolean)}
         */
        @Test
        void mirrorsTheLeftArm() {
            // Act
            ArmRotation right = EvokerPose.armRotation(10.0F, true);
            ArmRotation left = EvokerPose.armRotation(10.0F, false);
            // Assert
            assertThat(left.xRot()).isEqualTo(right.xRot());
            assertThat(left.zRot()).isEqualTo(-right.zRot());
        }
    }

    @Nested
    class HandOffsetAt {
        /**
         * When the body faces south (yaw 0) at tick 0, handOffset should put the hand particle 0.6 blocks along the
         * swayed body angle, as the evoker's particles are.
         *
         * <p>Method under test: {@link EvokerPose#handOffset(float, int)}
         */
        @Test
        void followsTheEvokersFormula() {
            // Arrange
            double angle = 0.0 + Math.cos(0 * 0.6662F) * 0.25F;
            // Act
            HandOffset hand = EvokerPose.handOffset(0.0F, 0);
            // Assert
            assertThat(hand.dx()).isCloseTo(Math.cos(angle) * 0.6, within(1e-6));
            assertThat(hand.dz()).isCloseTo(Math.sin(angle) * 0.6, within(1e-6));
        }

        /**
         * When the body faces west (yaw 90) at tick 7, handOffset should rotate with the body and sway with the tick.
         *
         * <p>Method under test: {@link EvokerPose#handOffset(float, int)}
         */
        @Test
        void rotatesWithTheBodyAndSwaysWithTheTick() {
            // Arrange
            double angle = Math.toRadians(90.0F) + Math.cos(7 * 0.6662F) * 0.25F;
            // Act
            HandOffset hand = EvokerPose.handOffset(90.0F, 7);
            // Assert
            assertThat(hand.dx()).isCloseTo(Math.cos(angle) * 0.6, within(1e-6));
            assertThat(hand.dz()).isCloseTo(Math.sin(angle) * 0.6, within(1e-6));
            assertThat(Math.hypot(hand.dx(), hand.dz())).isCloseTo(EvokerPose.HAND_DISTANCE, within(1e-9));
        }
    }
}
