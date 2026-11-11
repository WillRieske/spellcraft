package io.github.willrieske.spellcraft.gametest;

import io.github.willrieske.spellcraft.effect.ModEffects;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Helpers of the Part 7 GameTests: players, floors, images and hostiles. */
final class ScrollTestSupport {
    private ScrollTestSupport() {}

    /**
     * A survival player at {@link FangGameTests#STAND} (the padded box is loaded), without the Evoking effect.
     *
     * @param helper the test's helper
     * @return the player
     */
    static ServerPlayer plainPlayer(GameTestHelper helper) {
        ServerPlayer player = FangGameTests.caster(helper, FangGameTests.NORTH);
        player.removeEffect(ModEffects.EVOKING);
        return player;
    }

    /**
     * Lays stone under the square of {@code radius} blocks around the standing spot (the padded box is air).
     *
     * @param helper the test's helper
     * @param radius blocks from the standing spot to the edge of the square
     */
    static void floor(GameTestHelper helper, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                helper.setBlock(FangGameTests.FEET.offset(dx, -1, dz), Blocks.STONE);
            }
        }
    }
}
