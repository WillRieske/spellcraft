package io.github.willrieske.spellcraft.gametest;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.MirrorImage;
import io.github.willrieske.spellcraft.evoking.MirrorImages;
import io.github.willrieske.spellcraft.evoking.VillagerGhosts;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Helpers of the Part 7 GameTests: players, floors, images and hostiles. */
final class ScrollTestSupport {
    private ScrollTestSupport() {}

    /** A summoner, a Raid-sized husk and twelve flying vexes, the first a Caster; nothing is started yet. */
    record RaidScene(ServerPlayer player, Husk husk, BoundVex caster) {}

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

    /**
     * A parked Caster of {@code player} at {@code absolute}: no target goals, no follow goal (so nothing pulls it
     * toward the player), {@code setCaster(true)}. Tests place it clearly inside {@code GuardPosture.WIDE_LIMIT} (4.0)
     * of the player or well outside it, never exactly on the limit.
     *
     * @param helper the test's helper
     * @param player the summoner
     * @param absolute where it hangs
     * @return the Caster
     */
    static BoundVex casterVex(GameTestHelper helper, ServerPlayer player, Vec3 absolute) {
        BoundVex vex = BoundVexTestSupport.noTargets(
                BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player));
        BoundVexTestSupport.pauseFollowing(vex);
        vex.setCaster(true);
        vex.setPos(absolute);
        return vex;
    }

    /**
     * Builds a Raid scene: a plain player, {@code TeamMoveTestSupport.bigHusk} beside the hang point with its health
     * raised to 1000 (the netherite swordsmen would otherwise kill the 40-health husk in the first volley and end the
     * move before a test can look at the Caster), twelve vexes from {@code flyingVexes}, the first made a Caster.
     *
     * @param helper the test's helper
     * @return the scene
     */
    static RaidScene raidScene(GameTestHelper helper) {
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        Husk husk = TeamMoveTestSupport.bigHusk(helper, TeamMoveTestSupport.beside(helper));
        TeamMoveTestSupport.setBase(husk, Attributes.MAX_HEALTH, 1000.0);
        husk.setHealth(1000.0F);
        List<BoundVex> vexes = TeamMoveTestSupport.flyingVexes(helper, player, 12, hang);
        vexes.get(0).setCaster(true);
        return new RaidScene(player, husk, vexes.get(0));
    }

    /**
     * A fixed non-default villager look for a record: desert type, librarian profession, level 3. A fresh villager's
     * data equals the codec's defaults, so a default sample could not catch a codec that drops a field.
     *
     * @param helper the test's helper
     * @return the sample data
     */
    static VillagerData sampleVillagerData(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        return new VillagerData(
                registries.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.DESERT),
                registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN),
                3);
    }

    /**
     * Adds a record at {@code at} (absolute) with the sample data and removes it again when the test ends.
     *
     * @param helper the test's helper
     * @param at the absolute position
     * @return the record
     */
    static VillagerGhosts.Record record(GameTestHelper helper, Vec3 at) {
        VillagerGhosts ghosts = VillagerGhosts.get(helper.getLevel());
        VillagerGhosts.Record record = new VillagerGhosts.Record(
                UUID.randomUUID(), BlockPos.containing(at), sampleVillagerData(helper), 0L, 0L);
        ghosts.add(record);
        GameTestSupport.whenDone(helper, () -> ghosts.remove(record.id()));
        return record;
    }
}
