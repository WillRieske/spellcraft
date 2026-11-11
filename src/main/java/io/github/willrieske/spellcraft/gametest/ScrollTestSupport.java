package io.github.willrieske.spellcraft.gametest;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.MirrorImage;
import io.github.willrieske.spellcraft.evoking.MirrorImages;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

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

    /**
     * The live images of {@code carrier} in its level.
     *
     * @param carrier whoever has Mirror
     * @return the images
     */
    static List<MirrorImage> images(LivingEntity carrier) {
        return MirrorImages.of((ServerLevel) carrier.level(), carrier.getUUID());
    }

    /**
     * The absolute position {@code (dx, dy, dz)} from the standing spot.
     *
     * @param helper the test's helper
     * @param dx blocks east
     * @param dy blocks up
     * @param dz blocks south
     * @return the absolute position
     */
    static Vec3 at(GameTestHelper helper, double dx, double dy, double dz) {
        return helper.absoluteVec(FangGameTests.STAND.add(dx, dy, dz));
    }

    /**
     * A mob with no AI and no gravity at {@code at}, discarded when the test ends.
     *
     * @param <T> the mob type
     * @param helper the test's helper
     * @param type the mob's type
     * @param at the absolute position
     * @return the mob
     */
    static <T extends Mob> T foeAt(GameTestHelper helper, EntityType<T> type, Vec3 at) {
        return TeamMoveTestSupport.foe(helper, type, at);
    }

    /**
     * A frozen husk (hostile, and unlike a zombie it does not burn in daylight) at {@code at}.
     *
     * @param helper the test's helper
     * @param at the absolute position
     * @return the husk
     */
    static Husk hostileAt(GameTestHelper helper, Vec3 at) {
        return foeAt(helper, EntityTypes.HUSK, at);
    }
}
