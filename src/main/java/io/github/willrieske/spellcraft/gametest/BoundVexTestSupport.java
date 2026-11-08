package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared constants and helpers of the Bound Vex GameTests (the cap, follow, target and job classes). Tests whose
 * vex flies or teleports, that need headroom, or that park many vexes register through {@code
 * FangGameTests.addPadded} (padding 13); the rest run inside the shared 3x3x3 box, where the barrier shell
 * blocks line of sight, so a targeting test keeps the vex and its prey both inside the box. Hostiles are husks
 * (they do not burn in daylight).
 */
public final class BoundVexTestSupport {
    /** The box's top north-west corner: where a test parks the vex. */
    static final BlockPos VEX_SPOT = new BlockPos(0, 2, 0);

    /** The box's opposite bottom corner: farther from {@link #VEX_SPOT} than the summoner standing at the box's middle. */
    static final BlockPos PREY_SPOT = new BlockPos(2, 1, 2);

    /** The middle of the structure's box, where {@code FangGameTests.caster} stands the player. */
    static final BlockPos FEET = FangGameTests.FEET;

    /** A vex this far north of the caster is outside the teleport distance (12) and inside the padding (13). */
    static final int FAR_NORTH = 13;

    /**
     * A Bound Vex of {@code summoner} at {@code relative} with its full AI and the iron sword ({@code finalizeSpawn}
     * runs because a spawn reason is given), discarded when the test ends. Not through {@code VexSummons.summon}: the
     * test chooses where it hovers.
     *
     * @param helper the test helper
     * @param relative where to spawn it, relative to the structure
     * @param summoner the player it counts as summoned by
     * @return the vex
     */
    static BoundVex boundVex(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), relative, EntitySpawnReason.MOB_SUMMONED);
        vex.setData(AllyAttachments.SUMMONER, summoner.getUUID());
        vex.setBoundOrigin(helper.absolutePos(relative));
        // A fresh vex has gravity until its first tick sets noGravity, and the drift would sink it into the floor.
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        whenDone(helper, vex::discard);
        return vex;
    }

    /**
     * {@link #boundVex} with its movement goals removed, so it hovers where it was put while its target goals still
     * run. Parking fixes every distance in a test.
     *
     * @param helper the test helper
     * @param relative where to park it
     * @param summoner the player it counts as summoned by
     * @return the parked vex
     */
    static BoundVex parked(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = boundVex(helper, relative, summoner);
        vex.goalSelector.removeAllGoals(goal -> true);
        return vex;
    }

    /**
     * A husk (a hostile that does not burn in daylight) with no AI, discarded when the test ends.
     *
     * @param helper the test helper
     * @param relative where to spawn it
     * @return the husk
     */
    static Husk husk(GameTestHelper helper, BlockPos relative) {
        Husk husk = helper.spawn(EntityTypes.HUSK, relative);
        husk.setNoAi(true);
        whenDone(helper, husk::discard);
        return husk;
    }

    /**
     * Takes every target goal away from {@code vex}, so only the test decides who it targets.
     *
     * @param vex the vex
     * @return {@code vex}
     */
    static BoundVex noTargets(BoundVex vex) {
        vex.targetSelector.removeAllGoals(goal -> true);
        return vex;
    }

    /** A creeper with no AI, so it neither swells nor walks, discarded when the test ends. */
    static Creeper calmCreeper(GameTestHelper helper, BlockPos relative) {
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, relative);
        creeper.setNoAi(true);
        whenDone(helper, creeper::discard);
        return creeper;
    }

    /** A Bound Vex with no summoner mark (as {@code /summon} makes one), hovering, discarded when the test ends. */
    static BoundVex unmarkedVex(GameTestHelper helper, BlockPos relative) {
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), relative);
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        whenDone(helper, vex::discard);
        return vex;
    }

    /** A survival player with Evoking inside the shared box (no padding wait). */
    static ServerPlayer summoner(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(EvokingGameTests.evoking());
        MockPlayers.moveIntoTest(helper, player);
        return player;
    }

    /**
     * {@link #boundVex} without its target goals, for a test whose subject is movement. A vex thirteen or more blocks
     * out hovers at the edge of its padding, where a hostile in a neighboring test's structure can be within its guard
     * area and reach; with a target the charge goal (3) outranks follow (4) and the vex chases it away, so a movement
     * test with the full target AI can fail for a reason that has nothing to do with movement.
     */
    static BoundVex followOnly(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        return noTargets(boundVex(helper, relative, summoner));
    }

    /** Moves {@code entity} to {@code dx} east and {@code dz} south of {@code vex} (same height), held in place. */
    static void placeBeside(BoundVex vex, Entity entity, double dx, double dz) {
        entity.setNoGravity(true);
        entity.setPos(vex.position().add(dx, 0, dz));
    }

    private BoundVexTestSupport() {}
}
