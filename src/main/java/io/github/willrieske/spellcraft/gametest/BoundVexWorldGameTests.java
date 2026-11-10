package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.endVoid;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.platform;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.soak;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.FEET;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.NIGHT;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * GameTests of the Part 5 world helpers. This class holds the tests of the support code ({@link
 * BoundVexWorldTestSupport} and the pinned time of day); the rule tests of the later tasks register here too.
 */
public final class BoundVexWorldGameTests {
    private BoundVexWorldGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_setup_end_room", BoundVexWorldGameTests::setupEndRoom);
        padded("bound_vex_world_setup_end_void", BoundVexWorldGameTests::setupEndVoid);
        padded("bound_vex_world_setup_slots_are_private", BoundVexWorldGameTests::setupSlotsArePrivate);
        padded("bound_vex_world_setup_fluids", BoundVexWorldGameTests::setupFluids);
        SpellcraftGameTests.add(NIGHT, "bound_vex_world_setup_night", BoundVexWorldGameTests::setupNight);
        SpellcraftGameTests.add(SHARED, "bound_vex_world_setup_day", BoundVexWorldGameTests::setupDay);
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
    static void setupSlotsArePrivate(GameTestHelper helper) {
        // Arrange
        ServerPlayer first = FangGameTests.caster(helper, 0.0F);
        ServerPlayer second = BoundVexTestSupport.summoner(helper);

        // Act
        endVoid(helper, first, 16);
        endVoid(helper, second, 16);

        // Assert
        double sideways = Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
        helper.assertTrue(sideways >= 200.0, "two slots are at least 200 blocks apart, not " + sideways);
        helper.succeed();
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
