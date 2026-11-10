package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
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

    /** The box's opposite bottom corner: farther from {@link #VEX_SPOT} than the summoner at the box's middle. */
    static final BlockPos PREY_SPOT = new BlockPos(2, 1, 2);

    /** The middle of the structure's box, where {@code FangGameTests.caster} stands the player. */
    static final BlockPos FEET = FangGameTests.FEET;

    /**
     * Where the flying tests hang a vex: four blocks over the structure's middle, above the barrier shell (which
     * surrounds the box and blocks line of sight), so a hostile at the same height is in view.
     */
    static final BlockPos AIR_SPOT = FEET.above(4);

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
     * A husk with no AI and no gravity, {@code dx} blocks east and {@code dz} blocks south of {@code at} (world axes,
     * same height). It is spawned in the structure and then moved by absolute position: a rotated structure puts
     * relative coordinates a block off. Discarded when the test ends.
     *
     * @param helper the test helper
     * @param at the reference point, absolute
     * @param dx blocks east of {@code at}
     * @param dz blocks south of {@code at}
     * @return the husk
     */
    static Husk huskAt(GameTestHelper helper, Vec3 at, double dx, double dz) {
        Husk husk = husk(helper, FEET);
        husk.setNoGravity(true);
        husk.setPos(at.add(dx, 0.0, dz));
        return husk;
    }

    /**
     * A staying Bound Vex of {@code summoner} with the full AI at {@link #AIR_SPOT}, holding {@code mainHand} (the
     * iron sword it spawned with when {@code mainHand} is empty), discarded when the test ends. Its post is where it
     * hangs.
     *
     * @param helper the test helper
     * @param summoner the player it counts as summoned by
     * @param mainHand the item for its main hand, or {@link ItemStack#EMPTY} to keep the sword
     * @return the vex
     */
    static BoundVex stayingVex(GameTestHelper helper, ServerPlayer summoner, ItemStack mainHand) {
        BoundVex vex = boundVex(helper, AIR_SPOT, summoner);
        if (!mainHand.isEmpty()) {
            vex.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
        }
        vex.setStaying(true);
        return vex;
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

    /**
     * A persistent husk with no AI and no gravity in the Nether at {@code at} (an absolute position), inside a small
     * stone-shelled pocket of air, so it neither falls, despawns nor suffocates. The chunk is forced and loaded, and
     * when the test ends the husk is discarded, the blocks restored and the chunk unforced (as
     * {@code FangGameTests.toNether} does for a player).
     *
     * @param helper the test helper
     * @param at where the husk stands, absolute
     * @return the husk, a mob of another level than the test's
     */
    static Husk huskInNether(GameTestHelper helper, Vec3 at) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw helper.assertionException("no Nether on the GameTest server");
        }
        BlockPos base = BlockPos.containing(at);
        ChunkPos chunk = new ChunkPos(base.getX() >> 4, base.getZ() >> 4);
        nether.setChunkForced(chunk.x(), chunk.z(), true);
        nether.getChunk(chunk.x(), chunk.z());
        Map<BlockPos, BlockState> was = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 3, 2))) {
            was.put(pos.immutable(), nether.getBlockState(pos));
        }
        was.keySet().forEach(pos -> {
            boolean shell = Math.abs(pos.getX() - base.getX()) == 2
                    || Math.abs(pos.getZ() - base.getZ()) == 2
                    || pos.getY() == base.getY() - 1
                    || pos.getY() == base.getY() + 3;
            nether.setBlock(pos, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
        });
        Husk husk = EntityTypes.HUSK.create(nether, EntitySpawnReason.COMMAND);
        if (husk == null) {
            throw helper.assertionException("could not create a husk in the Nether");
        }
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setPersistenceRequired();
        husk.setPos(at);
        nether.addFreshEntity(husk);
        whenDone(helper, () -> {
            husk.discard();
            was.forEach((pos, state) -> nether.setBlock(pos, state, Block.UPDATE_ALL));
            nether.setChunkForced(chunk.x(), chunk.z(), false);
        });
        return husk;
    }

    /** Moves {@code entity} to {@code dx} east and {@code dz} south of {@code vex} (same height), held in place. */
    static void placeBeside(BoundVex vex, Entity entity, double dx, double dz) {
        entity.setNoGravity(true);
        entity.setPos(vex.position().add(dx, 0, dz));
    }

    /** How many ticks {@link #everyTick} keeps running: longer than any padded test lasts. */
    private static final int EVERY_TICK_SPAN = 1500;

    /**
     * Runs {@code action} once per tick until the test ends, in a sequence of its own beside the test's. Not
     * {@code GameTestHelper.onEachTick}: that registers one entry per remaining tick in the test's tick map (a
     * Spellcraft test that uses it crashed the server in the map's iterator when a padded test's wait added an
     * entry), where a sequence adds none.
     *
     * @param helper the test helper
     * @param action what to run each tick; it must not throw
     */
    static void everyTick(GameTestHelper helper, Runnable action) {
        helper.startSequence().thenExecuteFor(EVERY_TICK_SPAN, action);
    }

    /**
     * Holds {@code entity} at {@code at} until the test ends: every tick its position is set again and its velocity
     * zeroed (through {@link #everyTick}). {@link #placeBeside} sets a position only once.
     *
     * @param helper the test helper
     * @param entity the entity to hold
     * @param at where to hold it, absolute
     */
    static void holdAt(GameTestHelper helper, Entity entity, Vec3 at) {
        everyTick(helper, () -> {
            entity.setPos(at);
            entity.setDeltaMovement(Vec3.ZERO);
        });
    }

    /**
     * A {@link #calmCreeper} whose fuse is so long that it hisses without exploding once {@code setSwellDir(1)} is
     * called: the fuse is the {@code Fuse} short tag, written over the creeper's own save and loaded back.
     *
     * @param helper the test helper
     * @param relative where to spawn it, relative to the structure
     * @return the creeper
     */
    static Creeper patientCreeper(GameTestHelper helper, BlockPos relative) {
        Creeper creeper = calmCreeper(helper, relative);
        CompoundTag tag = GameTestSupport.saved(creeper);
        tag.putShort("Fuse", Short.MAX_VALUE);
        creeper.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        return creeper;
    }

    private BoundVexTestSupport() {}
}
