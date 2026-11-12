package io.github.willrieske.spellcraft.spell.rules;

import java.util.function.BooleanSupplier;

/**
 * Whether a player over the End's void needs the catch (Parts 4–5 spec §6.4).
 *
 * <p>Functional spec §10.11.
 */
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

    /**
     * Whether the spot a player last stood on can be used as a landing. A spot in an unloaded chunk is trusted as
     * recorded: looking at its blocks would load the chunk on the server thread (and again every time a failing check
     * repeats), while the teleport loads it anyway. A spot in a loaded chunk is used only if it is still standable, so
     * a broken bridge is not a landing.
     *
     * @param chunkLoaded whether the spot's chunk is loaded
     * @param standable whether the spot is still standable; asked only when the chunk is loaded
     * @return whether to land there
     */
    public static boolean lastGroundUsable(boolean chunkLoaded, BooleanSupplier standable) {
        return !chunkLoaded || standable.getAsBoolean();
    }
}
