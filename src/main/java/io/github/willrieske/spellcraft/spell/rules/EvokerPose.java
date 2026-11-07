package io.github.willrieske.spellcraft.spell.rules;

/**
 * The evoker's look while casting (spec §7): the illager model's spellcasting arm pose, and where the evoker's hand
 * particles sit relative to its feet. Pure numbers; the client applies them.
 */
public final class EvokerPose {
    /** How far from the body center each hand particle sits, horizontally, at scale 1 (multiply by the scale). */
    public static final double HAND_DISTANCE = 0.6;

    /** How far above the feet the hand particles sit, at scale 1 (multiply by the scale). */
    public static final double HAND_HEIGHT = 1.8;

    private static final float SWAY_RATE = 0.6662F;
    private static final float ARM_SWAY = 0.25F;
    private static final float ARM_RAISE = (float) (Math.PI * 3.0 / 4.0);

    /**
     * A model arm's rotation, in radians.
     *
     * @param xRot forward sway
     * @param yRot twist (always 0)
     * @param zRot the raise, positive for the right arm and negative for the left
     */
    public record ArmRotation(float xRot, float yRot, float zRot) {}

    /**
     * One hand particle's offset from the body center; the other hand's is the negation.
     *
     * @param dx blocks east
     * @param dz blocks south
     */
    public record HandOffset(double dx, double dz) {}

    private EvokerPose() {}

    /**
     * The illager spellcasting pose at {@code ageInTicks} for the right ({@code true}) or left arm.
     *
     * @param ageInTicks the model's age in ticks, including the partial tick
     * @param rightArm true for the right arm, false for the left
     * @return the arm's rotation in radians
     */
    public static ArmRotation armRotation(float ageInTicks, boolean rightArm) {
        float sway = (float) Math.cos(ageInTicks * SWAY_RATE) * ARM_SWAY;
        return new ArmRotation(sway, 0.0F, rightArm ? ARM_RAISE : -ARM_RAISE);
    }

    /**
     * Where one hand particle sits when the body faces {@code bodyYawDegrees} at {@code tick}; the other hand is
     * {@code (-dx, -dz)}. Multiply by the entity's scale.
     *
     * @param bodyYawDegrees the body's yaw in degrees
     * @param tick the age in ticks that drives the sway
     * @return the offset of one hand particle from the body center
     */
    public static HandOffset handOffset(float bodyYawDegrees, int tick) {
        double angle = Math.toRadians(bodyYawDegrees) + Math.cos(tick * SWAY_RATE) * ARM_SWAY;
        return new HandOffset(Math.cos(angle) * HAND_DISTANCE, Math.sin(angle) * HAND_DISTANCE);
    }
}
