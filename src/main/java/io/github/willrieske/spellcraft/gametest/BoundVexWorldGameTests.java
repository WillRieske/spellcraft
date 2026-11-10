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
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.Place;
import io.github.willrieske.spellcraft.spell.rules.GuardPosture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
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
        padded("bound_vex_world_scan_cadence_end", BoundVexWorldGameTests::scanCadenceEnd);
        padded("bound_vex_world_scan_cadence_overworld_day", BoundVexWorldGameTests::scanCadenceOverworldDay);
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
     * Waits until {@code vex} is in place for its posture, watches it for {@link #WATCH_TICKS} ticks, logs what it
     * measured (the largest sideways distance, the lowest height, how many times the vex set off for a new spot),
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
     * When a Bound Vex with its full AI is mid-flight to its close spot at night and a husk appears in its guard area,
     * should the vex take the husk as its target within 20 ticks with the follow goal no longer running, and then come
     * nearer: the longer-running follow goal does not hold the vex.
     *
     * <p>Method under test: {@code FollowSummonerGoal} (package-private), interrupted by the charge goal
     */
    static void closeFlightYieldsToATarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        BoundVex vex = BoundVexTestSupport.boundVex(helper, FEET.offset(0, 0, -6), player);
        Husk[] husk = new Husk[1];
        double[] before = {0.0};
        double[] nearest = {Double.MAX_VALUE};
        boolean[] targeted = {false};

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getLevel().isDarkOutside(), "setup: it is night");
                    helper.assertTrue(vex.getMoveControl().hasWanted(), "setup: the vex is mid-flight");
                })
                .thenExecute(() -> {
                    husk[0] = BoundVexTestSupport.husk(helper, FEET);
                    husk[0].setNoGravity(true);
                    BoundVexTestSupport.placeBeside(vex, husk[0], 0, -3);
                    before[0] = vex.distanceTo(husk[0]);
                })
                .thenExecuteFor(20, () -> {
                    targeted[0] |= husk[0].equals(vex.getTarget());
                    nearest[0] = Math.min(nearest[0], vex.distanceTo(husk[0]));
                })
                .thenExecute(() -> {
                    helper.assertTrue(targeted[0], "the vex took the husk as its target");
                    helper.assertFalse(
                            vex.goalSelector.getAvailableGoals().stream()
                                    .anyMatch(g -> g.isRunning() && g.getGoal() instanceof FollowSummonerGoal),
                            "the follow goal no longer holds the vex");
                })
                // The charge goal rolls a one-in-seven chance per check, so the approach itself gets the test's time.
                .thenWaitUntil(() -> {
                    nearest[0] = Math.min(nearest[0], vex.distanceTo(husk[0]));
                    helper.assertTrue(
                            nearest[0] < before[0],
                            "the vex came nearer to the husk: " + nearest[0] + " from " + before[0]);
                })
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
     * When a husk stands three blocks from a parked Bound Vex in an End room, should the vex have it as its target
     * within 3 ticks.
     *
     * <p>Method under test: {@code BoundVex.scanDue} with the End posture's interval
     */
    static void scanCadenceEnd(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 4, 5);
        BoundVex vex = vexIn(helper, end, player.position().add(0, 2, 0), player);
        vex.goalSelector.removeAllGoals(goal -> true);

        // Act & Assert
        expectTargetWithin(helper, vex, 3, () -> huskIn(end, player.position().add(3, 2, 0)));
    }

    /**
     * When a husk stands three blocks from a parked Bound Vex in the Overworld by day, should the vex have it as its
     * target within 11 ticks (the old cadence still works).
     *
     * <p>Method under test: {@code BoundVex.scanDue} with the wide posture's interval
     */
    static void scanCadenceOverworldDay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        BoundVex vex = BoundVexTestSupport.parked(helper, FEET.offset(6, 1, 0), player); // outside the barrier shell

        // Act & Assert
        expectTargetWithin(helper, vex, 11, () -> {
            Husk husk = BoundVexTestSupport.husk(helper, FEET);
            husk.setNoGravity(true);
            BoundVexTestSupport.placeBeside(vex, husk, 3, 0);
            return husk;
        });
    }

    /**
     * Waits until {@code vex} has ticked, makes the husk appear, and asserts that {@code ticks} ticks later the vex
     * has that husk as its target.
     */
    private static void expectTargetWithin(GameTestHelper helper, BoundVex vex, int ticks, Supplier<Husk> appear) {
        Husk[] husk = new Husk[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    helper.assertTrue(vex.getTarget() == null, "setup: the vex has no target yet");
                    husk[0] = appear.get();
                })
                .thenExecuteAfter(
                        ticks,
                        () -> helper.assertTrue(
                                husk[0].equals(vex.getTarget()), "the vex targets the husk within " + ticks + " ticks"))
                .thenSucceed();
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
}
