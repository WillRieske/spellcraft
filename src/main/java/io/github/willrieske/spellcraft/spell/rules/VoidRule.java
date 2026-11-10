package io.github.willrieske.spellcraft.spell.rules;

import java.util.function.BooleanSupplier;

/** Whether a player over the End's void needs the catch (Parts 4–5 spec §6.4). */
public final class VoidRule {
    /** Below the level's lowest Y by more than this, the catch is certain. */
    public static final int BELOW_FLOOR = 4;

    /** A falling player under the lowest Y plus this is checked for ground below. */
    public static final int NEAR_FLOOR = 16;

    private VoidRule() {}

    /**
     * Whether the player needs catching: never while flying; always once they are more than {@link #BELOW_FLOOR}
     * under the level's floor; and when they are in the air, moving down, under the floor plus {@link #NEAR_FLOOR}
     * and there is no ground within 64 blocks below them.
     *
     * @param y the player's feet
     * @param minY the level's lowest Y
     * @param onGround whether the player stands on something
     * @param movingDown whether the player's vertical movement is downward
     * @param flying whether the player flies (elytra or creative flight)
     * @param groundWithin64 a block ray straight down; asked only when everything else holds
     * @return whether to catch them
     */
    public static boolean needsCatch(
            double y, int minY, boolean onGround, boolean movingDown, boolean flying, BooleanSupplier groundWithin64) {
        if (flying) {
            return false;
        }
        if (y < minY - BELOW_FLOOR) {
            return true;
        }
        return !onGround && movingDown && y < minY + NEAR_FLOOR && !groundWithin64.getAsBoolean();
    }
}
