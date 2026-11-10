package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.endVoid;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.freeSlots;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.placeFor;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.platform;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.set;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.soak;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.FEET;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.NIGHT;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import com.mojang.logging.LogUtils;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.VexRescues;
import io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.Place;
import io.github.willrieske.spellcraft.spell.rules.GuardPosture;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * GameTests of the Part 5 world helpers. This class holds the tests of the support code ({@link
 * BoundVexWorldTestSupport} and the pinned time of day); the rule tests of the later tasks register here too.
 */
public final class BoundVexWorldGameTests {
    /** Where the posture tests log what they measured. */
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many ticks a close posture is watched once its vex is in place. */
    private static final int WATCH_TICKS = 60;

    /**
     * The least height above the summoner's feet a close-posture vex may hang at (spec §6.1, §9): a floor with margin,
     * below the in-place low edge of 2.0 (band 2.5 less the 0.5 slack) so one tick of coast does not fail it.
     */
    private static final double LEAST_HEIGHT = 1.7;

    /** A runaway guard only: a close-posture vex farther sideways than this has left its band by far. */
    private static final double RUNAWAY = 5.0;

    /** The server-stop test clears every rescue on the server, so it runs in an environment of its own. */
    private static final String SERVER_STOP_RESCUES = "bound_vex_world_server_stop_rescues";

    /** How far sideways from the player the lava arrangement fills with stone (the shore search reaches 6). */
    private static final int POOL_REACH = 6;

    /** How far below the player's feet the lava arrangement fills with stone. */
    private static final int POOL_DEPTH = 6;

    /** How close a rescuer must hang to its summoner's side point. */
    private static final double SIDE_TOLERANCE = 0.1;

    private BoundVexWorldGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_setup_end_room", BoundVexWorldGameTests::setupEndRoom);
        padded("bound_vex_world_setup_end_void", BoundVexWorldGameTests::setupEndVoid);
        padded("bound_vex_world_setup_two_places_are_apart", BoundVexWorldGameTests::setupTwoPlacesAreApart);
        padded("bound_vex_world_setup_cleanup_end_void", BoundVexWorldGameTests::setupCleanupEndVoid);
        padded("bound_vex_world_setup_cleanup_room", BoundVexWorldGameTests::setupCleanupRoom);
        padded("bound_vex_world_setup_rejects_bad_extents", BoundVexWorldGameTests::setupRejectsBadExtents);
        padded("bound_vex_world_setup_fluids", BoundVexWorldGameTests::setupFluids);
        SpellcraftGameTests.add(NIGHT, "bound_vex_world_setup_night", BoundVexWorldGameTests::setupNight);
        SpellcraftGameTests.add(SHARED, "bound_vex_world_setup_day", BoundVexWorldGameTests::setupDay);
        padded("bound_vex_world_posture_overworld_day", BoundVexWorldGameTests::postureOverworldDay);
        FangGameTests.addPadded(NIGHT, "bound_vex_world_posture_night", BoundVexWorldGameTests::postureNight);
        padded("bound_vex_world_posture_nether", BoundVexWorldGameTests::postureNether);
        padded("bound_vex_world_posture_end", BoundVexWorldGameTests::postureEnd);
        padded("bound_vex_world_posture_staying_scans_by_place", BoundVexWorldGameTests::postureStayingScansByPlace);
        FangGameTests.addPadded(
                NIGHT,
                "bound_vex_world_posture_close_flight_yields_to_a_target",
                BoundVexWorldGameTests::closeFlightYieldsToATarget);
        FangGameTests.addPadded(
                NIGHT,
                "bound_vex_world_posture_close_flight_follows_a_fleeing_summoner",
                BoundVexWorldGameTests::closeFlightFollowsAFleeingSummoner);
        padded("bound_vex_world_scan_cadence_end", BoundVexWorldGameTests::scanCadenceEnd);
        padded("bound_vex_world_scan_cadence_overworld_day", BoundVexWorldGameTests::scanCadenceOverworldDay);
        padded("bound_vex_world_lava_pull", BoundVexWorldGameTests::lavaPull);
        padded("bound_vex_world_lava_pull_keeps_your_steering", BoundVexWorldGameTests::lavaPullKeepsYourSteering);
        padded("bound_vex_world_lava_pull_ends_on_the_shore", BoundVexWorldGameTests::lavaPullEndsOnTheShore);
        padded("bound_vex_world_lava_pull_pauses_after_60_ticks", BoundVexWorldGameTests::lavaPullPausesAfter60);
        padded("bound_vex_world_lava_pull_ignores_a_low_shore", BoundVexWorldGameTests::lavaPullIgnoresALowShore);
        padded(
                "bound_vex_world_lava_pull_not_for_fire_resistance",
                helper -> lavaPullNotForTheUnhurt(helper, BoundVexWorldGameTests::fireResistant, false));
        padded(
                "bound_vex_world_lava_pull_not_for_spectators",
                helper -> lavaPullNotForTheUnhurt(helper, player -> player.setGameMode(GameType.SPECTATOR), false));
        padded(
                "bound_vex_world_lava_pull_not_for_creative",
                helper -> lavaPullNotForTheUnhurt(helper, player -> player.setGameMode(GameType.CREATIVE), false));
        padded("bound_vex_world_lava_pull_not_for_passengers", BoundVexWorldGameTests::lavaPullNotForPassengers);
        padded(
                "bound_vex_world_lava_pull_not_with_a_staying_vex",
                helper -> lavaPullNotForTheUnhurt(helper, player -> {}, true));
        padded("bound_vex_world_lava_rescuer_keeps_its_target", BoundVexWorldGameTests::lavaRescuerKeepsItsTarget);
        padded("bound_vex_world_rescue_dropped_on_logout", BoundVexWorldGameTests::rescueDroppedOnLogout);
        padded("bound_vex_world_lava_pull_takes_a_fleeing_vex", BoundVexWorldGameTests::lavaPullTakesAFleeingVex);
        padded(
                "bound_vex_world_lava_pull_replaces_a_dead_rescuer",
                BoundVexWorldGameTests::lavaPullReplacesADeadRescuer);
        padded("bound_vex_world_lava_overworld_keeps_burning", BoundVexWorldGameTests::lavaOverworldKeepsBurning);
        padded("bound_vex_world_lava_nether_bucket", BoundVexWorldGameTests::lavaNetherBucket);
        padded(
                "bound_vex_world_lava_nether_no_bucket_while_in_lava",
                BoundVexWorldGameTests::lavaNetherNoBucketWhileInLava);
        FangGameTests.addPadded(
                SERVER_STOP_RESCUES,
                "bound_vex_world_rescues_cleared_on_server_stop",
                BoundVexWorldGameTests::rescuesClearedOnServerStop);
    }

    /** Registers {@code body} in the shared environment with the padded box and its chunk wait. */
    private static void padded(String name, Consumer<GameTestHelper> body) {
        FangGameTests.addPadded(SHARED, name, body);
    }

    /**
     * When {@code room} builds a room in the End, should the player stand on stone in air and a Bound Vex spawned there
     * stay alive and know its summoner.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#room}
     */
    static void setupEndRoom(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);

        // Act
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = vexIn(helper, end, player.position().add(1, 1, 0), player);

        // Assert
        BlockPos feet = player.blockPosition();
        helper.assertTrue(player.level().equals(end), "the player is in the End room's level");
        helper.assertValueEqual(end.dimension(), Level.END, "the room's level is the End");
        helper.assertTrue(end.getBlockState(feet.below()).is(Blocks.STONE), "stone under the player's feet");
        helper.assertTrue(end.getBlockState(feet).isAir(), "air at the feet");
        helper.assertTrue(end.getBlockState(feet.above()).isAir(), "air at the head");
        helper.assertTrue(player.isAlive(), "the player is alive");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(vex.isAlive(), "the vex in the End is alive after 5 ticks");
            helper.assertTrue(vex.tickCount > 0, "the vex in the End ticks (its chunk is entity-ticking)");
            helper.assertTrue(vex.summoner().isPresent(), "the vex in the End knows its summoner");
            helper.succeed();
        });
    }

    /**
     * When {@code endVoid} places a player in the End, should every chunk within 32 blocks be loaded with nothing to
     * land on, and a {@code platform} become the first free Y of its column.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#endVoid}, {@link BoundVexWorldTestSupport#platform}
     */
    static void setupEndVoid(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);

        // Act
        ServerLevel end = endVoid(helper, player, 48);
        BlockPos at = player.blockPosition();

        // Assert
        for (int dx = -32; dx <= 32; dx += 8) {
            for (int dz = -32; dz <= 32; dz += 8) {
                helper.assertTrue(
                        end.hasChunk(
                                SectionPos.blockToSectionCoord(at.getX() + dx),
                                SectionPos.blockToSectionCoord(at.getZ() + dz)),
                        "the chunk at " + dx + "," + dz + " is loaded");
                helper.assertValueEqual(
                        end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX() + dx, at.getZ() + dz),
                        end.getMinY(),
                        "nothing to land on at " + dx + "," + dz);
            }
        }
        BlockPos center = at.offset(5, -3, 0);
        platform(helper, end, center, 1);
        helper.assertValueEqual(
                end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()),
                center.getY() + 1,
                "the platform's top + 1 is the column's height");
        BlockPos block = at.above(3);
        set(helper, end, block, Blocks.OBSIDIAN.defaultBlockState());
        helper.assertTrue(end.getBlockState(block).is(Blocks.OBSIDIAN), "set puts the block into the place");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(player.isAlive(), "the player in the void is alive after 5 ticks");
            helper.succeed();
        });
    }

    /**
     * When two players each ask for an End void in one test, should their places be at least 200 blocks apart.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#endVoid}
     */
    static void setupTwoPlacesAreApart(GameTestHelper helper) {
        // Arrange
        ServerPlayer first = FangGameTests.caster(helper, 0.0F);
        ServerPlayer second = BoundVexTestSupport.summoner(helper);

        // Act
        endVoid(helper, first, 16);
        endVoid(helper, second, 16);

        // Assert
        double sideways = Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
        helper.assertTrue(sideways >= 200.0, "two places are at least 200 blocks apart, not " + sideways);
        helper.succeed();
    }

    /**
     * When an End void with a platform, a set block, a vex and an armor stand ends (twice), should its slot be free
     * again, the floor,
     * platform and block restored, the chunks unforced, the entities removed and the player back in the test level.
     *
     * <p>Method under test: {@code BoundVexWorldTestSupport.Place.finish}
     */
    static void setupCleanupEndVoid(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        int free = freeSlots(helper, Level.END);
        ServerLevel end = endVoid(helper, player, 16);
        BlockPos slot = player.blockPosition();
        BlockPos platformAt = slot.offset(4, -3, 0);
        BlockPos block = slot.offset(0, 5, 3);
        platform(helper, end, platformAt, 1);
        set(helper, end, block, Blocks.LAVA.defaultBlockState());
        BoundVex vex = vexIn(helper, end, player.position().add(1, 1, 0), player);
        Entity stand = armorStandIn(end, player.position().add(-1, 1, 0));
        Place place = placeFor(helper, end, slot, "cleanup test");
        helper.assertValueEqual(freeSlots(helper, Level.END), free - 1, "the place took a slot");
        helper.assertTrue(forced(end, slot), "the place's chunk is forced");
        helper.assertTrue(end.getBlockState(slot.atY(1)).isAir(), "the floor is cleared under the slot");
        helper.assertTrue(end.getBlockState(platformAt).is(Blocks.STONE), "the platform is stone");
        helper.assertTrue(end.getBlockState(block).is(Blocks.LAVA), "the set block is lava");

        whenTicking(
                helper,
                stand,
                () -> { // a cleanup finds only entities of ticking chunks
                    int before = freeSlots(helper, Level.END); // other tests take and give slots between ticks

                    // Act
                    place.finish();

                    // Assert
                    assertEnded(helper, end, player, vex, stand, slot, before + 1);
                    helper.assertTrue(end.getBlockState(platformAt).isAir(), "the platform is gone");
                    helper.assertTrue(end.getBlockState(block).isAir(), "the set block is air again");

                    // Act again, as whenDone will when the test ends, after something else changed the block
                    end.setBlock(block, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
                    int afterFirst = freeSlots(helper, Level.END);
                    place.finish();

                    // Assert
                    helper.assertTrue(end.getBlockState(block).is(Blocks.GLASS), "the second cleanup undid nothing");
                    helper.assertValueEqual(
                            freeSlots(helper, Level.END), afterFirst, "the second cleanup released no slot");
                    end.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    helper.assertTrue(
                            player.position().distanceTo(helper.absoluteVec(FangGameTests.STAND)) < 0.01,
                            "the player is still back at STAND");
                    helper.succeed();
                });
    }

    /**
     * When a Nether room with a vex and an armor stand ends (twice), should its slot be free again, the shell
     * restored to what it was,
     * the chunks unforced, the entities removed and the player back in the test level.
     *
     * <p>Method under test: {@code BoundVexWorldTestSupport.Place.finish}
     */
    static void setupCleanupRoom(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        int free = freeSlots(helper, Level.NETHER);
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 2, 3);
        BlockPos slot = player.blockPosition();
        BoundVex vex = vexIn(helper, nether, player.position().add(1, 1, 0), player);
        Entity stand = armorStandIn(nether, player.position().add(-1, 1, 0));
        Place place = placeFor(helper, nether, slot, "cleanup test");
        BlockPos[] shell = {slot.below(), slot.offset(3, 1, 0), slot.offset(0, 3, -3), slot.offset(-3, 0, 3)};
        for (BlockPos pos : shell) {
            helper.assertTrue(nether.getBlockState(pos).is(Blocks.STONE), "the shell is stone at " + pos);
        }
        helper.assertValueEqual(freeSlots(helper, Level.NETHER), free - 1, "the room took a slot");

        whenTicking(
                helper,
                stand,
                () -> { // a cleanup finds only entities of ticking chunks
                    int before = freeSlots(helper, Level.NETHER); // other tests take and give slots between ticks

                    // Act
                    place.finish();

                    // Assert
                    assertEnded(helper, nether, player, vex, stand, slot, before + 1);
                    for (BlockPos pos : shell) {
                        helper.assertTrue(nether.getBlockState(pos).isAir(), "the shell is restored to air at " + pos);
                    }

                    // Act again, as whenDone will when the test ends
                    place.finish();

                    // Assert
                    assertEnded(helper, nether, player, vex, stand, slot, before + 1);
                    helper.succeed();
                });
    }

    /**
     * When a platform reaches out of its place, or sits in none, or a room is wider than allowed, should the helper
     * fail with a "setup:" message and change nothing.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#platform}, {@link BoundVexWorldTestSupport#room}
     */
    static void setupRejectsBadExtents(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        int free = freeSlots(helper, Level.NETHER);
        ServerLevel end = endVoid(helper, player, 4);
        BlockPos slot = player.blockPosition();
        BlockPos nearEdge = slot.offset(3, -3, 0);

        // Act & Assert
        assertSetupFailure(helper, () -> platform(helper, end, nearEdge, 3), "a platform past the reach");
        helper.assertTrue(end.getBlockState(nearEdge).isAir(), "the failed platform placed nothing");
        assertSetupFailure(
                helper, () -> platform(helper, end, slot.atY(end.getMaxY() + 5), 0), "a platform above the world");
        assertSetupFailure(helper, () -> platform(helper, end, slot.offset(50, 0, 0), 0), "a platform outside");
        assertSetupFailure(
                helper,
                () -> set(helper, end, slot.offset(0, 0, 50), Blocks.STONE.defaultBlockState()),
                "a block outside");
        ServerPlayer other = BoundVexTestSupport.summoner(helper);
        assertSetupFailure(
                helper,
                () -> BoundVexWorldTestSupport.room(helper, other, Level.NETHER, 90, 3),
                "a room 181 blocks wide");
        helper.assertValueEqual(freeSlots(helper, Level.NETHER), free, "the rejected room took no slot");
        helper.succeed();
    }

    /**
     * When a following Bound Vex is in the Overworld by day, should its posture be the wide one and its scan interval
     * 10.
     *
     * <p>Method under test: {@code BoundVex.posture} and {@code BoundVex.scanInterval}
     */
    static void postureOverworldDay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        helper.assertFalse(helper.getLevel().isDarkOutside(), "setup: it is day");

        // Act
        BoundVex vex = BoundVexTestSupport.followOnly(helper, FEET.above(1), player);

        // Assert
        helper.assertTrue(vex.posture().equals(GuardPosture.WIDE), "the Overworld by day has the wide posture");
        helper.assertValueEqual(vex.scanInterval(), 10, "the wide posture scans every 10 ticks");
        helper.succeed();
    }

    /**
     * When a following Bound Vex six blocks north of its summoner is in the Overworld at night, should it close in
     * overhead (at least 1.7 above the summoner's feet for 60 ticks, never far off sideways) and scan every 4 ticks.
     *
     * <p>Method under test: {@code BoundVex.posture} through {@code FollowSummonerGoal}
     */
    static void postureNight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        BoundVex vex = BoundVexTestSupport.followOnly(helper, FEET.offset(0, 0, -6), player);

        // Act & Assert
        watchClosePosture(
                helper,
                player,
                vex,
                "night",
                4,
                () -> helper.assertTrue(helper.getLevel().isDarkOutside(), "setup: it is night"));
    }

    /**
     * When a following Bound Vex three blocks east of its summoner is in a Nether room, should it close in overhead (at
     * least 1.7 above the summoner's feet for 60 ticks, never far off sideways) and scan every 4 ticks.
     *
     * <p>Method under test: {@code BoundVex.posture} through {@code FollowSummonerGoal}
     */
    static void postureNether(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 4, 5);
        BoundVex vex = BoundVexTestSupport.noTargets(
                vexIn(helper, nether, player.position().add(3, 0, 0), player));

        // Act & Assert
        watchClosePosture(helper, player, vex, "nether", 4, () -> {});
    }

    /**
     * When a following Bound Vex three blocks east of its summoner is in an End room, should it close in overhead (at
     * least 1.7 above the summoner's feet for 60 ticks, never far off sideways) and scan every 2 ticks.
     *
     * <p>Method under test: {@code BoundVex.posture} through {@code FollowSummonerGoal}
     */
    static void postureEnd(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 4, 5);
        BoundVex vex = BoundVexTestSupport.noTargets(
                vexIn(helper, end, player.position().add(3, 0, 0), player));

        // Act & Assert
        watchClosePosture(helper, player, vex, "end", 2, () -> {});
    }

    /**
     * Waits until {@code vex} is in place for its posture and has arrived (no wanted position), watches it for
     * {@link #WATCH_TICKS} ticks, logs what it measured (the largest sideways distance, the lowest height, how many times the vex set off for a new spot),
     * then asserts the height, the runaway guard and the scan interval. Ends the test.
     *
     * @param helper the test helper
     * @param player the summoner
     * @param vex the following vex, already spawned
     * @param label the posture's name, for the log line
     * @param scanInterval the scan interval the posture should give
     * @param precondition a check that must pass before the vex's position is read (it may throw a GameTest failure)
     */
    private static void watchClosePosture(
            GameTestHelper helper,
            ServerPlayer player,
            BoundVex vex,
            String label,
            int scanInterval,
            Runnable precondition) {
        double[] farthest = {0.0};
        double[] lowest = {Double.MAX_VALUE};
        int[] hops = {0};
        boolean[] flying = {false};
        int[] ticks = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    precondition.run();
                    Vec3 offset = vex.position().subtract(player.position());
                    helper.assertTrue(
                            vex.posture().inPlace(offset.x, offset.y, offset.z), "setup: the vex is in place");
                    helper.assertFalse(vex.getMoveControl().hasWanted(), "setup: the vex has arrived and stopped");
                })
                .thenExecuteFor(WATCH_TICKS, () -> {
                    Vec3 offset = vex.position().subtract(player.position());
                    farthest[0] = Math.max(farthest[0], Math.hypot(offset.x, offset.z));
                    lowest[0] = Math.min(lowest[0], offset.y);
                    boolean wanted = vex.getMoveControl().hasWanted();
                    if (ticks[0]++ > 0 && wanted && !flying[0]) {
                        hops[0]++;
                    }
                    flying[0] = wanted;
                })
                .thenExecute(() -> {
                    LOGGER.info(
                            "posture {}: largest sideways distance {}, lowest height {}, hops {} in {} ticks",
                            label,
                            farthest[0],
                            lowest[0],
                            hops[0],
                            WATCH_TICKS);
                    helper.assertTrue(
                            lowest[0] >= LEAST_HEIGHT,
                            label + ": the vex stays at least " + LEAST_HEIGHT + " above the feet, lowest "
                                    + lowest[0]);
                    helper.assertTrue(
                            farthest[0] < RUNAWAY,
                            label + ": the vex stays within " + RUNAWAY + " sideways, farthest " + farthest[0]);
                    helper.assertValueEqual(vex.scanInterval(), scanInterval, label + ": scan interval");
                })
                .thenSucceed();
    }

    /**
     * When a Bound Vex with its full AI is mid-flight to its close spot at night and a husk appears ahead of it
     * (between it and the summoner, inside its guard area), should the vex take the husk as its target, within 6 ticks
     * of that have a free move control or be charging (the follow goal gave up its flight), and then come nearer to
     * the husk.
     *
     * <p>The approach window is 100 ticks. The charge goal rolls a one-in-seven chance on each of its checks (every
     * second tick), so 50 checks all failing has a chance of (6/7)^50, about 0.05 percent; the husk is held still, so
     * "nearer" cannot come from the husk moving.
     *
     * <p>Method under test: {@code FollowSummonerGoal.stop} (package-private), through the charge goal
     */
    static void closeFlightYieldsToATarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        BoundVex vex = BoundVexTestSupport.boundVex(helper, FEET.offset(0, 0, -6), player);
        Husk[] husk = new Husk[1];
        int[] ticks = {0};
        int[] tookAt = {-1};
        double[] before = {0.0};
        double[] nearest = {Double.MAX_VALUE};

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getLevel().isDarkOutside(), "setup: it is night");
                    helper.assertTrue(vex.getMoveControl().hasWanted(), "setup: the vex is mid-flight");
                })
                .thenExecute(() -> {
                    husk[0] = BoundVexTestSupport.husk(helper, FEET);
                    husk[0].setNoGravity(true);
                    BoundVexTestSupport.placeBeside(vex, husk[0], 0, 3);
                })
                .thenExecuteFor(12 + 6, () -> {
                    if (tookAt[0] < 0 && husk[0].equals(vex.getTarget())) {
                        tookAt[0] = ticks[0];
                        before[0] = vex.distanceTo(husk[0]);
                    }
                    if (tookAt[0] >= 0 && ticks[0] == tookAt[0] + 6) {
                        helper.assertTrue(
                                !vex.getMoveControl().hasWanted() || vex.isCharging(),
                                "6 ticks after taking the target the vex is free or charging");
                    }
                    ticks[0]++;
                })
                .thenExecute(() -> helper.assertTrue(tookAt[0] >= 0, "the vex took the husk as its target"))
                .thenExecuteFor(100, () -> nearest[0] = Math.min(nearest[0], vex.distanceTo(husk[0])))
                .thenExecute(() -> helper.assertTrue(
                        nearest[0] < before[0],
                        "the vex came nearer to the husk: " + nearest[0] + " from " + before[0]))
                .thenSucceed();
    }

    /**
     * When a Bound Vex starts a close flight about nine blocks from its summoner at night and the summoner then
     * appears more than twelve blocks away, should the vex give up the flight and, within 6 ticks, teleport to within
     * {@link FollowSummonerGoal#TELEPORT_LANDING_MAX} of the summoner.
     *
     * <p>Method under test: {@code FollowSummonerGoal.canContinueToUse} and {@code stop} (package-private)
     */
    static void closeFlightFollowsAFleeingSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        BoundVex vex = BoundVexTestSupport.followOnly(helper, FEET.offset(0, 0, -8), player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getLevel().isDarkOutside(), "setup: it is night");
                    helper.assertTrue(vex.getMoveControl().hasWanted(), "setup: the vex is mid-flight");
                    helper.assertTrue(vex.distanceTo(player) <= FollowSummonerGoal.TELEPORT_DISTANCE, "setup: near");
                })
                .thenExecute(() -> MockPlayers.moveTo(helper, player, FangGameTests.STAND.add(0, 0, 12)))
                .thenExecute(() -> helper.assertTrue(
                        vex.distanceTo(player) > FollowSummonerGoal.TELEPORT_DISTANCE,
                        "setup: the summoner is more than 12 blocks from the vex, not " + vex.distanceTo(player)))
                .thenExecuteAfter(
                        6,
                        () -> helper.assertTrue(
                                vex.distanceTo(player) <= FollowSummonerGoal.TELEPORT_LANDING_MAX,
                                "the vex teleported to its summoner, still " + vex.distanceTo(player) + " away"))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex is in an End room, should it not move for 40 ticks and scan every 2 ticks: staying
     * changes where it hovers, not how often it looks.
     *
     * <p>Method under test: {@code BoundVex.scanInterval}
     */
    static void postureStayingScansByPlace(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 4, 5);
        BoundVex vex = vexIn(helper, end, player.position().add(1, 2.5, 0), player);
        vex.setStaying(true);
        Vec3[] start = new Vec3[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> start[0] = vex.position())
                .thenExecuteFor(
                        40,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(start[0]) <= 0.5, "the staying vex stays where it was"))
                .thenExecute(() -> helper.assertValueEqual(vex.scanInterval(), 2, "a staying vex scans by place"))
                .thenSucceed();
    }

    /**
     * When a husk appears beside four parked Bound Vexes in an End room, should every one of them have it as its target
     * within 2 ticks. The scan clock is {@code (tickCount + id) % interval}, so four vexes with different ids cover
     * both parities: a scan interval of 2 reaches all four in time, a longer one would leave some waiting.
     * ({@code scanDue} is package-private, so the test watches the behavior it gives.)
     *
     * <p>Method under test: {@code BoundVex.scanDue} with the End posture's interval
     */
    static void scanCadenceEnd(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 4, 5);
        BoundVex[] vexes = new BoundVex[4];
        for (int i = 0; i < vexes.length; i++) {
            vexes[i] = vexIn(helper, end, player.position().add(-0.75 + 0.5 * i, 2, 0), player);
            vexes[i].goalSelector.removeAllGoals(goal -> true);
        }
        Husk[] husk = new Husk[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(allTicked(vexes), "setup: the vexes tick"))
                .thenExecute(() -> {
                    for (BoundVex vex : vexes) {
                        helper.assertTrue(vex.getTarget() == null, "setup: no vex has a target yet");
                    }
                    husk[0] = huskIn(end, player.position().add(2.5, 2, 0));
                })
                .thenExecuteAfter(2, () -> {
                    for (BoundVex vex : vexes) {
                        helper.assertTrue(
                                husk[0].equals(vex.getTarget()), "vex " + vex.getId() + " targets the husk in 2 ticks");
                    }
                })
                .thenSucceed();
    }

    /**
     * When a husk appears beside ten parked Bound Vexes in the Overworld by day, should at least one still have no
     * target 4 ticks later (their ids give them ten different scan phases of a 10-tick clock, so about half have to
     * wait longer), and all ten have it within 12 ticks (the old cadence still works).
     * ({@code scanDue} is package-private, so the test watches the behavior it gives.)
     *
     * <p>Method under test: {@code BoundVex.scanDue} with the wide posture's interval
     */
    static void scanCadenceOverworldDay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        helper.assertFalse(helper.getLevel().isDarkOutside(), "setup: it is day");
        BoundVex[] vexes = new BoundVex[10];
        for (int i = 0; i < vexes.length; i++) {
            vexes[i] = BoundVexTestSupport.parked(helper, FEET.offset(6, 1, 0), player); // outside the barrier shell
            vexes[i].setPos(vexes[i].position().add(-0.9 + 0.2 * i, 0, 0));
        }
        Husk[] husk = new Husk[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(allTicked(vexes), "setup: the vexes tick"))
                .thenExecute(() -> {
                    husk[0] = BoundVexTestSupport.husk(helper, FEET);
                    husk[0].setNoGravity(true);
                    BoundVexTestSupport.placeBeside(vexes[4], husk[0], 2.5, 0);
                })
                .thenExecuteAfter(4, () -> {
                    int waiting = 0;
                    for (BoundVex vex : vexes) {
                        if (vex.getTarget() == null) {
                            waiting++;
                        }
                    }
                    helper.assertTrue(waiting >= 1, "after 4 ticks some vex still has not scanned");
                })
                .thenExecuteAfter(8, () -> {
                    for (BoundVex vex : vexes) {
                        helper.assertTrue(
                                husk[0].equals(vex.getTarget()),
                                "vex " + vex.getId() + " targets the husk in 12 ticks");
                    }
                })
                .thenSucceed();
    }

    /** Whether every vex has ticked at least once. */
    private static boolean allTicked(BoundVex[] vexes) {
        for (BoundVex vex : vexes) {
            if (vex.tickCount <= 0) {
                return false;
            }
        }
        return true;
    }

    /** A persistent husk with no AI and no gravity at {@code at} in {@code level}; the place's cleanup removes it. */
    private static Husk huskIn(ServerLevel level, Vec3 at) {
        Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setPersistenceRequired();
        husk.setPos(at);
        level.addFreshEntity(husk);
        return husk;
    }

    /** Fails the test unless {@code action} throws a GameTest failure whose message contains "setup:". */
    private static void assertSetupFailure(GameTestHelper helper, Runnable action, String what) {
        try {
            action.run();
        } catch (GameTestAssertException failure) {
            helper.assertTrue(failure.getMessage().contains("setup:"), what + " fails with a setup: message");
            return;
        }
        throw helper.assertionException(what + " should have failed");
    }

    /** Runs {@code next} once {@code entity} has ticked: its chunk is then entity-ticking, so a cleanup finds it. */
    private static void whenTicking(GameTestHelper helper, Entity entity, Runnable next) {
        if (entity.tickCount > 0) {
            next.run();
        } else {
            helper.runAfterDelay(1, () -> whenTicking(helper, entity, next));
        }
    }

    /** An armor stand at {@code at}: an entity only the cleanup removes (a Bound Vex may leave by itself). */
    private static Entity armorStandIn(ServerLevel level, Vec3 at) {
        Entity stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        stand.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(stand);
        return stand;
    }

    /** Whether the chunk containing {@code pos} is forced in {@code level}. */
    private static boolean forced(ServerLevel level, BlockPos pos) {
        return level.getForceLoadedChunks().contains(ChunkPos.pack(pos));
    }

    /** Asserts everything a finished place must have undone, apart from the blocks only the caller knows about. */
    private static void assertEnded(
            GameTestHelper helper,
            ServerLevel level,
            ServerPlayer player,
            BoundVex vex,
            Entity stand,
            BlockPos slot,
            int expectedFree) {
        helper.assertValueEqual(
                freeSlots(helper, level.dimension()), expectedFree, "the slot is free again in " + level.dimension());
        for (int dx = -16; dx <= 16; dx += 16) {
            helper.assertFalse(forced(level, slot.offset(dx, 0, 0)), "the chunk at dx " + dx + " is no longer forced");
        }
        helper.assertTrue(vex.isRemoved(), "the vex is removed");
        helper.assertTrue(stand.isRemoved(), "the armor stand is removed");
        helper.assertTrue(player.level().equals(helper.getLevel()), "the player is back in the test level");
        helper.assertTrue(
                player.position().distanceTo(helper.absoluteVec(FangGameTests.STAND)) < 0.01,
                "the player is back at STAND");
        if (level.dimension().equals(Level.END)) {
            for (int y = 0; y <= 3; y++) {
                for (BlockPos column : new BlockPos[] {slot, slot.offset(10, 0, -9), slot.offset(-12, 0, 12)}) {
                    helper.assertFalse(
                            level.getBlockState(column.atY(y)).isAir(), "the floor is back at " + column.atY(y));
                }
            }
        }
    }

    /**
     * When a mock player stands in lava, water or air and {@code soak} runs, should it report exactly that fluid.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#soak}
     */
    static void setupFluids(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        for (BlockPos spot : new BlockPos[] {FEET, FEET.above()}) {
            helper.setBlock(spot.east(), Blocks.STONE);
            helper.setBlock(spot.west(), Blocks.STONE);
            helper.setBlock(spot.north(), Blocks.STONE);
            helper.setBlock(spot.south(), Blocks.STONE);
        }
        helper.setBlock(FEET.below(), Blocks.STONE);

        // Act & Assert
        helper.setBlock(FEET, Blocks.LAVA);
        helper.setBlock(FEET.above(), Blocks.LAVA);
        soak(player);
        helper.assertTrue(player.isInLava(), "a soaked player in lava is in lava");
        helper.setBlock(FEET, Blocks.WATER);
        helper.setBlock(FEET.above(), Blocks.WATER);
        soak(player);
        helper.assertTrue(player.isUnderWater(), "a soaked player in water is under water");
        helper.setBlock(FEET, Blocks.AIR);
        helper.setBlock(FEET.above(), Blocks.AIR);
        soak(player);
        helper.assertFalse(player.isInLava() || player.isUnderWater(), "a soaked player in air is in neither");
        helper.succeed();
    }

    /**
     * When the test runs in the night environment, should the Overworld be dark after two ticks.
     *
     * <p>Method under test: {@code OverworldTime} (the {@code NIGHT} environment)
     */
    static void setupNight(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getLevel().isDarkOutside(), "it is dark in the night environment");
            helper.succeed();
        });
    }

    /**
     * When the test runs in the shared environment, should the Overworld not be dark after two ticks.
     *
     * <p>Method under test: {@code OverworldTime} (the {@code SHARED} environment)
     */
    static void setupDay(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(helper.getLevel().isDarkOutside(), "it is not dark in the shared environment");
            helper.succeed();
        });
    }

    // ---- Task 4: the lava pull and the Nether's water bucket ----

    /**
     * The lava arrangement of the Task 4 tests: a survival player (made unhurtable) at {@link FangGameTests#STAND},
     * facing south, in a two-block column of lava inside a solid mass of stone, so no air touches the lava (lava
     * spreads into air) and the only standable spot within the shore search is the shore this method carves, if any.
     * The shore is two blocks east of the player, {@code shoreDrop} blocks below their feet level: stone under it, two
     * blocks of air over it.
     *
     * @param helper the test helper
     * @param shoreDrop how far below the player's feet level the shore's feet block is; negative for no shore at all
     * @return the player
     */
    private static ServerPlayer lavaPool(GameTestHelper helper, int shoreDrop) {
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        for (int x = -POOL_REACH; x <= POOL_REACH; x++) {
            for (int z = -POOL_REACH; z <= POOL_REACH; z++) {
                for (int y = -POOL_DEPTH; y <= 2; y++) {
                    helper.setBlock(FEET.offset(x, y, z), Blocks.STONE);
                }
            }
        }
        if (shoreDrop >= 0) {
            BlockPos shore = FEET.east(2).below(shoreDrop);
            helper.setBlock(shore, Blocks.AIR);
            helper.setBlock(shore.above(), Blocks.AIR);
        }
        fillPool(helper, Blocks.LAVA);
        return player;
    }

    /** Fills the player's two-block column with {@code block} (lava, or air to drain it). */
    private static void fillPool(GameTestHelper helper, Block block) {
        helper.setBlock(FEET, block);
        helper.setBlock(FEET.above(), block);
    }

    /** One server step of a mock player: the fluid state it would have, then the rescue tick (one per server tick). */
    private static void step(ServerPlayer player) {
        soak(player);
        VexRescues.tick(player);
    }

    /** A following vex of {@code player} five blocks from them (three up, four south), above the stone. */
    private static BoundVex followingVex(GameTestHelper helper, ServerPlayer player) {
        return BoundVexTestSupport.followOnly(helper, FEET.offset(0, 3, 4), player);
    }

    /** Fails the test unless the player's velocity is {@code (x, y, z)} within 1e-6. */
    private static void assertVelocity(GameTestHelper helper, ServerPlayer player, double x, double y, double z) {
        Vec3 v = player.getDeltaMovement();
        helper.assertTrue(
                Math.abs(v.x - x) < 1.0e-6 && Math.abs(v.y - y) < 1.0e-6 && Math.abs(v.z - z) < 1.0e-6,
                "the velocity is (" + x + ", " + y + ", " + z + "), not " + v);
    }

    /** Fails the test unless a lava rescue is running for {@code player}. */
    private static void assertLavaRunning(GameTestHelper helper, ServerPlayer player, String what) {
        helper.assertValueEqual(VexRescues.running(player), Optional.of(VexRescues.Kind.LAVA), what);
    }

    /** Fails the test unless no rescue is running for {@code player}. */
    private static void assertNoRescue(GameTestHelper helper, ServerPlayer player, String what) {
        helper.assertTrue(VexRescues.running(player).isEmpty(), what);
    }

    /** The shore of {@link #lavaPool}, in the test's coordinates: the center of the shore's feet block. */
    private static final Vec3 SHORE = new Vec3(3.5, 1, 1.5);

    /** Drains the pool and puts the player on the shore, standing on the ground. */
    private static void walkOut(GameTestHelper helper, ServerPlayer player) {
        fillPool(helper, Blocks.AIR);
        MockPlayers.moveTo(helper, player, SHORE);
        player.setOnGround(true);
    }

    /** Fills the pool with lava again and puts the player back in it. */
    private static void walkBack(GameTestHelper helper, ServerPlayer player) {
        fillPool(helper, Blocks.LAVA);
        MockPlayers.moveTo(helper, player, FangGameTests.STAND);
        player.setOnGround(false);
    }

    /**
     * When a player stands in lava with a shore two blocks east and a following vex five blocks away, should one step
     * start a lava rescue, push them {@code (0.1, 0.3, 0)}, mark the velocity for sending and put the vex at their
     * side.
     *
     * <p>Method under test: {@code VexRescues.tick}, {@code RescuePush.lavaPull} through it
     */
    static void lavaPull(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);

        // Act
        step(player);

        // Assert
        assertLavaRunning(helper, player, "the lava rescue started");
        assertVelocity(helper, player, 0.1, 0.3, 0.0);
        helper.assertTrue(player.syncVelocity, "the velocity is marked for sending");
        Vec3 side = player.position().add(-0.6, 1.2, 0.0);
        helper.assertTrue(
                vex.position().distanceTo(side) <= SIDE_TOLERANCE,
                "the vex hangs at the player's side " + side + ", not " + vex.position());
        helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(vex)), "the vex is the rescuer");
        helper.succeed();
    }

    /**
     * When the client last reported the player moving south at 0.2, should the lava pull add to that and not replace
     * it: velocity {@code (0.1, 0.3, 0.2)}.
     *
     * <p>Method under test: {@code VexRescues.tick}: the push reads {@code getKnownMovement}
     */
    static void lavaPullKeepsYourSteering(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        player.setKnownMovement(new Vec3(0, 0, 0.2));

        // Act
        step(player);

        // Assert
        assertVelocity(helper, player, 0.1, 0.3, 0.2);
        helper.succeed();
    }

    /**
     * When the player gets out onto the shore, should the rescue end at once, no new one start for 19 more steps and
     * a new one start on the 20th step after the ending.
     *
     * <p>Method under test: {@code VexRescues.tick}: the end on the ground and the 20-tick pause
     */
    static void lavaPullEndsOnTheShore(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        long[] ended = {0};

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    walkOut(helper, player);
                    step(player);
                    assertNoRescue(helper, player, "the rescue ends when the player stands on the shore");
                    ended[0] = helper.getLevel().getGameTime();
                    walkBack(helper, player);
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    step(player);
                    assertNoRescue(helper, player, "no rescue starts during the pause");
                })
                .thenExecute(() -> {
                    step(player);
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - ended[0], 20L, "setup: this is the 20th tick after");
                    assertLavaRunning(helper, player, "a rescue starts again 20 ticks after the ending");
                })
                .thenSucceed();
    }

    /**
     * When the player is held in lava, should the rescue run through the 60th step, end on the 61st, and (the pause
     * being 20 ticks) start again on the 20th step after that.
     *
     * <p>Method under test: {@code VexRescues.tick}: the 60-tick limit
     */
    static void lavaPullPausesAfter60(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        long[] marks = {0, 0};

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "the first step starts the rescue");
                    marks[0] = helper.getLevel().getGameTime();
                })
                .thenIdle(1)
                .thenExecuteFor(59, () -> {
                    step(player);
                    assertLavaRunning(helper, player, "the rescue runs through the 60th step");
                })
                .thenExecute(() -> {
                    step(player);
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - marks[0], 60L, "setup: this is the 61st step");
                    assertNoRescue(helper, player, "the 61st step ends the rescue");
                    marks[1] = helper.getLevel().getGameTime();
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    step(player);
                    assertNoRescue(helper, player, "no rescue starts during the pause");
                })
                .thenExecute(() -> {
                    step(player);
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - marks[1], 20L, "setup: this is the 20th tick after");
                    assertLavaRunning(helper, player, "a rescue starts again 20 ticks after the ending");
                })
                .thenSucceed();
    }

    /**
     * When the only standable spot is two blocks east and four blocks lower (out of the shore search), should the
     * pull be the lift: velocity {@code (0, 0.4, 0)}.
     *
     * <p>Method under test: {@code StandableSpots.shore} through {@code VexRescues.tick}
     */
    static void lavaPullIgnoresALowShore(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 4);
        followingVex(helper, player);

        // Act
        step(player);

        // Assert
        assertLavaRunning(helper, player, "the rescue started");
        assertVelocity(helper, player, 0.0, 0.4, 0.0);
        helper.succeed();
    }

    /** Gives {@code player} fire resistance. */
    private static void fireResistant(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200));
    }

    /**
     * When a player in lava has {@code unhurt} applied (or the only vex in range is staying), should a step start no
     * rescue and leave their velocity alone.
     *
     * <p>Method under test: {@code VexRescues.tick}: who gets the lava pull
     *
     * @param helper the test helper
     * @param unhurt what makes the player a poor candidate
     * @param stayingVex whether the vex in range is told to stay
     */
    private static void lavaPullNotForTheUnhurt(
            GameTestHelper helper, Consumer<ServerPlayer> unhurt, boolean stayingVex) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);
        vex.setStaying(stayingVex);
        unhurt.accept(player);
        Vec3 before = player.getDeltaMovement();

        // Act
        step(player);

        // Assert
        assertNoRescue(helper, player, "no rescue starts");
        helper.assertValueEqual(player.getDeltaMovement(), before, "the velocity is untouched");
        helper.succeed();
    }

    /**
     * When a player in lava rides a pig, should a step start no rescue and leave their velocity alone.
     *
     * <p>Method under test: {@code VexRescues.tick}: passengers are not rescued
     */
    static void lavaPullNotForPassengers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        Mob pig = helper.spawn(EntityTypes.PIG, FEET);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setPermanentlyInvulnerable(true);
        whenDone(helper, pig::discard);
        helper.assertTrue(player.startRiding(pig), "setup: the mock player can ride");
        soak(player);
        helper.assertTrue(player.isInLava() && player.isPassenger(), "setup: a passenger in lava");
        Vec3 before = player.getDeltaMovement();

        // Act
        step(player);

        // Assert
        assertNoRescue(helper, player, "no rescue starts");
        helper.assertValueEqual(player.getDeltaMovement(), before, "the velocity is untouched");
        helper.succeed();
    }

    /**
     * When the rescuer had a target (a husk), should it still have that target after the rescue ends.
     *
     * <p>Method under test: {@code RescueGoal} and the end of a rescue
     */
    static void lavaRescuerKeepsItsTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);
        Mob husk = BoundVexTestSupport.husk(helper, FEET.offset(0, 5, 0));
        husk.setNoGravity(true);
        vex.setTarget(husk);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenExecuteAfter(2, () -> {
                    walkOut(helper, player);
                    step(player);
                    assertNoRescue(helper, player, "setup: the rescue ended");
                })
                .thenExecuteAfter(2, () -> helper.assertTrue(husk.equals(vex.getTarget()), "the vex kept its target"))
                .thenSucceed();
    }

    /**
     * When a player with a running rescue logs out, should the rescue be gone and the rescuer's goal let go: two
     * ticks later the vex is no longer held at the player's side.
     *
     * <p>Method under test: {@code VexRescues.onLogout}, {@code RescueGoal}
     */
    static void rescueDroppedOnLogout(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);
        step(player);
        assertLavaRunning(helper, player, "setup: the rescue started");

        // Act
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        Vec3 away = player.position().add(0, 6, 5);
        vex.setPos(away);

        // Assert
        assertNoRescue(helper, player, "the logout drops the rescue");
        helper.runAfterDelay(2, () -> {
            Vec3 side = player.position().add(-0.6, 1.2, 0.0);
            helper.assertTrue(vex.position().distanceTo(side) > 2.0, "the goal no longer holds the vex at the side");
            helper.succeed();
        });
    }

    /**
     * When the vex is mid-flight from a hissing creeper (its flee goal runs), should the lava rescue still pick it
     * and hold it at the player's side two ticks later.
     *
     * <p>Method under test: {@code RescueGoal} (priority -1) against {@code FleeCreeperGoal}
     */
    static void lavaPullTakesAFleeingVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        Creeper creeper = BoundVexTestSupport.patientCreeper(helper, FEET.offset(0, 3, 2));
        creeper.setNoGravity(true);
        BoundVex vex = BoundVexTestSupport.boundVex(helper, FEET.offset(0, 3, 4), player);
        double[] before = {0.0};

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    creeper.setSwellDir(1);
                    before[0] = vex.distanceTo(creeper);
                })
                .thenExecuteAfter(
                        12,
                        () -> helper.assertTrue(
                                vex.distanceTo(creeper) >= before[0] + 2.0,
                                "setup: the vex flees; it was " + before[0] + " from the creeper, now "
                                        + vex.distanceTo(creeper)))
                .thenExecute(() -> {
                    step(player);
                    helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(vex)), "the fleeing vex rescues");
                })
                .thenExecuteAfter(2, () -> {
                    Vec3 side = player.position().add(-0.6, 1.2, 0.0);
                    helper.assertTrue(
                            vex.position().distanceTo(side) <= SIDE_TOLERANCE,
                            "the vex is at the player's side, not " + vex.position());
                })
                .thenSucceed();
    }

    /**
     * When the nearer of two vexes is the rescuer and it dies, should the next step drop the rescue and the one after
     * it make the other vex the rescuer.
     *
     * <p>Method under test: {@code VexRescues.tick}: a rescuer that no longer holds
     */
    static void lavaPullReplacesADeadRescuer(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex near = BoundVexTestSupport.followOnly(helper, FEET.offset(0, 3, 2), player);
        BoundVex far = BoundVexTestSupport.followOnly(helper, FEET.offset(0, 3, 6), player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(near)), "the nearer vex rescues");
                    near.kill(helper.getLevel());
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    step(player);
                    assertNoRescue(helper, player, "the dead rescuer's rescue is dropped");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    step(player);
                    helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(far)), "the other vex rescues");
                })
                .thenSucceed();
    }

    /**
     * When a burning player is pulled out of lava in the Overworld, should they still burn afterwards and the
     * rescuer still hold its sword: only the Nether has the bucket.
     *
     * <p>Method under test: {@code VexRescues.tick}: the end of a lava rescue by dimension
     */
    static void lavaOverworldKeepsBurning(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    walkOut(helper, player);
                    player.setRemainingFireTicks(100);
                    step(player);
                    assertNoRescue(helper, player, "the rescue ended on the shore");
                    helper.assertTrue(player.getRemainingFireTicks() > 0, "the player still burns");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the rescuer holds its sword, no bucket");
                })
                .thenSucceed();
    }

    /**
     * The Nether arrangement of the bucket tests: a room at a private place, the player in a two-block column of
     * lava with stone on every side, a following vex three blocks east.
     *
     * @return the vex; the player is {@code player}
     */
    private static BoundVex netherPool(GameTestHelper helper, ServerPlayer player) {
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 4, 5);
        BlockPos slot = player.blockPosition();
        for (BlockPos column : new BlockPos[] {slot, slot.above()}) {
            for (BlockPos side : new BlockPos[] {column.east(), column.west(), column.north(), column.south()}) {
                set(helper, nether, side, Blocks.STONE.defaultBlockState());
            }
        }
        set(helper, nether, slot.above(2), Blocks.STONE.defaultBlockState());
        set(helper, nether, slot, Blocks.LAVA.defaultBlockState());
        set(helper, nether, slot.above(), Blocks.LAVA.defaultBlockState());
        return BoundVexTestSupport.noTargets(
                vexIn(helper, nether, player.position().add(3, 3, 0), player));
    }

    /** Drains the Nether pool and puts the player on the floor three blocks east of it, on the ground. */
    private static void netherWalkOut(GameTestHelper helper, ServerPlayer player, BlockPos slot) {
        ServerLevel nether = player.level();
        set(helper, nether, slot, Blocks.AIR.defaultBlockState());
        set(helper, nether, slot.above(), Blocks.AIR.defaultBlockState());
        player.setPos(slot.getX() + 3.5, slot.getY(), slot.getZ() + 0.5);
        player.setOnGround(true);
    }

    /**
     * When a burning player is pulled out of lava in the Nether, should the fire go out, the rescuer hold a water
     * bucket (conjured) for a tail of 20 ticks in which nothing pushes the player, and then hold its sword again.
     *
     * <p>Method under test: {@code VexRescues.tick}: the Nether's bucket and its tail
     */
    static void lavaNetherBucket(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        BoundVex vex = netherPool(helper, player);
        BlockPos slot = player.blockPosition();
        long[] ended = {0};
        Vec3[] velocity = new Vec3[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started in the Nether");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    netherWalkOut(helper, player, slot);
                    player.setRemainingFireTicks(100);
                    step(player);
                    ended[0] = helper.getLevel().getGameTime();
                    velocity[0] = player.getDeltaMovement();
                    assertLavaRunning(helper, player, "the rescue lives on as a tail");
                    helper.assertTrue(player.getRemainingFireTicks() <= 0, "the fire is out");
                    helper.assertTrue(vex.getMainHandItem().is(Items.WATER_BUCKET), "the rescuer holds the bucket");
                    helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "the bucket is conjured");
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    step(player);
                    assertLavaRunning(helper, player, "the tail runs");
                    helper.assertValueEqual(player.getDeltaMovement(), velocity[0], "the tail pushes nothing");
                    helper.assertTrue(player.getRemainingFireTicks() <= 0, "the fire stays out");
                    helper.assertTrue(vex.getMainHandItem().is(Items.WATER_BUCKET), "the bucket stays in the hand");
                })
                .thenExecute(() -> {
                    step(player);
                    assertNoRescue(helper, player, "the tail ends after 20 ticks");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
                })
                .thenSucceed();
    }

    /**
     * When a player is held in Nether lava for 61 steps, should the rescuer never hold a bucket and the player still
     * burn: the bucket is for a player who is out.
     *
     * <p>Method under test: {@code VexRescues.tick}: no bucket while in lava
     */
    static void lavaNetherNoBucketWhileInLava(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        BoundVex vex = netherPool(helper, player);
        Runnable check = () -> {
            step(player);
            helper.assertFalse(vex.getMainHandItem().is(Items.WATER_BUCKET), "the rescuer never holds a bucket");
            helper.assertTrue(player.getRemainingFireTicks() > 0, "the player still burns");
        };
        player.setRemainingFireTicks(1000); // lava ignites a moving entity, and a mock player never moves

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    check.run();
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecuteFor(59, check)
                .thenExecute(() -> {
                    check.run();
                    assertNoRescue(helper, player, "the 61st step ends the rescue");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is still in the hand");
                })
                .thenSucceed();
    }

    /**
     * When the server stops with a rescue running, should no rescue run afterwards.
     *
     * <p>Method under test: {@code BoundVexEvents.onServerStopped}, {@code VexRescues.clear}
     */
    static void rescuesClearedOnServerStop(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        step(player);
        assertLavaRunning(helper, player, "setup: the rescue started");

        // Act
        BoundVexEvents.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));

        // Assert
        assertNoRescue(helper, player, "the server stop drops the rescue");
        helper.succeed();
    }
}
