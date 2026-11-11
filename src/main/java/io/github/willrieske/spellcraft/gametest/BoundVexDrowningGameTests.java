package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.SIDE_TOLERANCE;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.assertLavaRunning;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.assertNoRescue;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.assertVelocity;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.followingVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.padded;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.set;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.soak;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.FEET;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.VexRescues;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of the drowning push: the rescue that pushes a drowning summoner up to the surface, who it
 * is not for, how it ends, and the precedence of the lava pull over it.
 */
public final class BoundVexDrowningGameTests {
    private BoundVexDrowningGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_drowning_push", BoundVexDrowningGameTests::drowningPush);
        padded("bound_vex_world_drowning_not_under_a_ceiling", BoundVexDrowningGameTests::drowningNotUnderACeiling);
        padded("bound_vex_world_drowning_not_with_air_left", BoundVexDrowningGameTests::drowningNotWithAirLeft);
        padded(
                "bound_vex_world_drowning_not_with_water_breathing",
                BoundVexDrowningGameTests::drowningNotWithWaterBreathing);
        padded(
                "bound_vex_world_drowning_not_with_conduit_power",
                BoundVexDrowningGameTests::drowningNotWithConduitPower);
        padded("bound_vex_world_drowning_not_for_creative", BoundVexDrowningGameTests::drowningNotForCreative);
        padded(
                "bound_vex_world_drowning_not_for_a_ghostrail_carrier",
                BoundVexDrowningGameTests::drowningNotForAGhostrailCarrier);
        padded("bound_vex_world_drowning_not_for_passengers", BoundVexDrowningGameTests::drowningNotForPassengers);
        padded("bound_vex_world_lava_before_drowning", BoundVexDrowningGameTests::lavaBeforeDrowning);
        padded("bound_vex_world_drowning_ends_at_the_surface", BoundVexDrowningGameTests::drowningEndsAtTheSurface);
        padded(
                "bound_vex_world_drowning_ends_when_the_way_up_closes",
                BoundVexDrowningGameTests::drowningEndsWhenTheWayUpCloses);
        padded(
                "bound_vex_world_drowning_ends_with_water_breathing",
                BoundVexDrowningGameTests::drowningEndsWithWaterBreathing);
        padded("bound_vex_world_drowning_ends_after_200_ticks", BoundVexDrowningGameTests::drowningEndsAfter200Ticks);
        padded("bound_vex_world_drowning_only_in_the_overworld", BoundVexDrowningGameTests::drowningOnlyInTheOverworld);
    }

    /**
     * The water arrangement of the drowning tests: a survival player (made unhurtable) at {@link FangGameTests#STAND}
     * with water in their two blocks and {@code depth} blocks above their head, stone under and on all four sides of
     * every water block (water spreads into air), then air, or stone when {@code roofed}.
     *
     * @param helper the test helper
     * @param depth how many blocks of water stand above the player head block
     * @param roofed whether a stone block caps the column
     * @return the player
     */
    private static ServerPlayer waterColumn(GameTestHelper helper, int depth, boolean roofed) {
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        helper.setBlock(FEET.below(), Blocks.STONE);
        for (int up = 0; up <= 1 + depth; up++) {
            BlockPos at = FEET.above(up);
            helper.setBlock(at.east(), Blocks.STONE);
            helper.setBlock(at.west(), Blocks.STONE);
            helper.setBlock(at.north(), Blocks.STONE);
            helper.setBlock(at.south(), Blocks.STONE);
        }
        for (int up = 0; up <= 1 + depth; up++) {
            helper.setBlock(FEET.above(up), Blocks.WATER);
        }
        helper.setBlock(FEET.above(2 + depth), roofed ? Blocks.STONE : Blocks.AIR);
        return player;
    }

    /** One server step of a mock player under water: the fluid state, then the air supply, then the rescue tick. */
    private static void drownStep(ServerPlayer player, int air) {
        soak(player);
        player.setAirSupply(air);
        VexRescues.tick(player);
    }

    /** Fails the test unless a drowning rescue is running for {@code player}. */
    private static void assertDrowningRunning(GameTestHelper helper, ServerPlayer player, String what) {
        helper.assertValueEqual(VexRescues.running(player), Optional.of(VexRescues.Kind.DROWNING), what);
    }

    /**
     * When a player with 40 air left is under water with open water above and a following vex near, should one step
     * start a drowning rescue with the client movement slowed by the water drag and the upward speed at 0.4, and put
     * the vex at their side.
     *
     * <p>Method under test: {@code VexRescues.tick}, {@code RescuePush.drowningPush} through it
     */
    static void drowningPush(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        BoundVex vex = followingVex(helper, player);
        player.setKnownMovement(new Vec3(0.1, -0.2, 0.05));

        // Act
        drownStep(player, 40);

        // Assert
        assertDrowningRunning(helper, player, "the drowning rescue started");
        assertVelocity(helper, player, 0.08, 0.4, 0.04);
        helper.assertTrue(player.syncVelocity, "the velocity is marked for sending");
        Vec3 side = player.position().add(-0.6, 1.2, 0.0);
        helper.assertTrue(
                vex.position().distanceTo(side) <= SIDE_TOLERANCE, "the vex hangs at the side, not " + vex.position());
        helper.succeed();
    }

    /**
     * When {@code unhurt} is applied to a drowning player (or a stone roof caps the water, or the air is 100), should a
     * step start no rescue and leave their velocity alone.
     *
     * @param helper the test helper
     * @param unhurt what makes the player a poor candidate
     * @param roofed whether the column has a stone roof
     * @param air the air supply for the step
     */
    private static void drowningNotFor(GameTestHelper helper, Consumer<ServerPlayer> unhurt, boolean roofed, int air) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, roofed);
        followingVex(helper, player);
        unhurt.accept(player);
        Vec3 before = player.getDeltaMovement();

        // Act
        drownStep(player, air);

        // Assert
        assertNoRescue(helper, player, "no rescue starts");
        helper.assertValueEqual(player.getDeltaMovement(), before, "the velocity is untouched");
        helper.succeed();
    }

    /**
     * When a stone block caps the water, should no rescue start (a push would pin the player to the roof).
     *
     * <p>Method under test: {@code VexRescues.hasWayUp} through {@code tick}
     */
    static void drowningNotUnderACeiling(GameTestHelper helper) {
        drowningNotFor(helper, player -> {}, true, 40);
    }

    /**
     * When the player still has 100 air, should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: the low-air threshold
     */
    static void drowningNotWithAirLeft(GameTestHelper helper) {
        drowningNotFor(helper, player -> {}, false, 100);
    }

    /**
     * When the player has Water Breathing, should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: who gets the drowning push
     */
    static void drowningNotWithWaterBreathing(GameTestHelper helper) {
        drowningNotFor(
                helper, player -> player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200)), false, 40);
    }

    /**
     * When the player has Conduit Power, should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: who gets the drowning push
     */
    static void drowningNotWithConduitPower(GameTestHelper helper) {
        drowningNotFor(
                helper, player -> player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 1200)), false, 40);
    }

    /**
     * When the player is in creative mode, should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: who gets the drowning push
     */
    static void drowningNotForCreative(GameTestHelper helper) {
        drowningNotFor(helper, player -> player.setGameMode(GameType.CREATIVE), false, 40);
    }

    /**
     * When the player has Ghostrail (it takes no damage and is flying), should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: a Ghostrail carrier is not rescued
     */
    static void drowningNotForAGhostrailCarrier(GameTestHelper helper) {
        drowningNotFor(
                helper,
                player -> Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS),
                false,
                VexRescues.LOW_AIR - 1);
    }

    /**
     * When a player under water rides a minecart (a pig would throw them off in the water), should a step start no
     * rescue and leave their velocity alone.
     *
     * <p>Method under test: {@code VexRescues.wantsDrowningPush}: passengers are not rescued
     */
    static void drowningNotForPassengers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);
        Entity cart = helper.spawn(EntityTypes.MINECART, FEET);
        cart.setNoGravity(true);
        cart.setPermanentlyInvulnerable(true);
        whenDone(helper, cart::discard);
        helper.assertTrue(player.startRiding(cart), "setup: the mock player can ride");
        soak(player);
        player.setAirSupply(40);
        helper.assertTrue(player.isUnderWater() && player.isPassenger(), "setup: a passenger under water");
        Vec3 before = player.getDeltaMovement();

        // Act
        VexRescues.tick(player);

        // Assert
        assertNoRescue(helper, player, "no rescue starts");
        helper.assertValueEqual(player.getDeltaMovement(), before, "the velocity is untouched");
        helper.succeed();
    }

    /**
     * When a player stands half a block up with lava in their lower block, air above it and water around their eyes
     * (lava next to water turns to obsidian, so the air keeps them apart), low on air, so that the lava pull and the
     * drowning push both apply, should a step start the lava rescue (the first need wins).
     *
     * <p>Method under test: {@code VexRescues.startFirstNeeded}: the order of the needs
     */
    static void lavaBeforeDrowning(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 0, false);
        helper.setBlock(FEET.above(), Blocks.AIR);
        helper.setBlock(FEET, Blocks.AIR);
        helper.setBlock(FEET, Blocks.LAVA);
        BlockPos eyes = FEET.above(2);
        helper.setBlock(eyes.east(), Blocks.STONE);
        helper.setBlock(eyes.west(), Blocks.STONE);
        helper.setBlock(eyes.north(), Blocks.STONE);
        helper.setBlock(eyes.south(), Blocks.STONE);
        helper.setBlock(eyes, Blocks.WATER);
        MockPlayers.moveTo(helper, player, FangGameTests.STAND.add(0.0, 0.5, 0.0));
        followingVex(helper, player);
        soak(player);
        player.setAirSupply(40);
        helper.assertTrue(player.isInLava() && player.isUnderWater(), "setup: in lava and under water at once");

        // Act
        VexRescues.tick(player);

        // Assert
        assertLavaRunning(helper, player, "the lava rescue started, not the drowning push");
        helper.succeed();
    }

    /**
     * When the player rises until their eyes are in air, should the rescue end on the next step.
     *
     * <p>Method under test: {@code VexRescues.drowning}: the end at the surface
     */
    static void drowningEndsAtTheSurface(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    MockPlayers.moveTo(helper, player, new Vec3(1.5, 5.0, 1.5));
                    drownStep(player, 40);
                    assertNoRescue(helper, player, "the rescue ends at the surface");
                })
                .thenSucceed();
    }

    /**
     * When stone closes the water above the player during the rescue, should the rescue end on the next step.
     *
     * <p>Method under test: {@code VexRescues.drowning}: the end when the way up closes
     */
    static void drowningEndsWhenTheWayUpCloses(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.setBlock(FEET.above(3), Blocks.STONE);
                    drownStep(player, 40);
                    assertNoRescue(helper, player, "the rescue ends once the way up is closed");
                })
                .thenSucceed();
    }

    /**
     * When the player gains Water Breathing during the rescue, should the rescue end on the next step.
     *
     * <p>Method under test: {@code VexRescues.drowning}: the player stops needing the rescue
     */
    static void drowningEndsWithWaterBreathing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "setup: the rescue started");
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200));
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertNoRescue(helper, player, "the rescue ends once the player breathes water");
                })
                .thenSucceed();
    }

    /**
     * When the player is held under water, should the rescue run through the 200th step, end on the 201st, and start
     * again on the step after (drowning has no pause).
     *
     * <p>Method under test: {@code VexRescues.drowning}: the 200-tick limit
     */
    static void drowningEndsAfter200Ticks(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);
        long[] start = {0};

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "the first step starts the rescue");
                    start[0] = helper.getLevel().getGameTime();
                })
                .thenIdle(1)
                .thenExecuteFor(199, () -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "the rescue runs through the 200th step");
                })
                .thenExecute(() -> {
                    drownStep(player, 40);
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - start[0], 200L, "setup: this is the 201st step");
                    assertNoRescue(helper, player, "the 201st step ends the rescue");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "a new rescue starts at once: no pause");
                })
                .thenSucceed();
    }

    /**
     * When a player with 40 air is under water in a Nether room, should no rescue start (the rule is Overworld only).
     *
     * <p>Method under test: {@code VexRescues.tick}: the dimension check of the drowning push
     */
    static void drowningOnlyInTheOverworld(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 4, 5);
        BlockPos slot = player.blockPosition();
        for (int up = 0; up <= 2; up++) {
            BlockPos at = slot.above(up);
            for (BlockPos side : new BlockPos[] {at.east(), at.west(), at.north(), at.south()}) {
                set(helper, nether, side, Blocks.STONE.defaultBlockState());
            }
        }
        for (int up = 0; up <= 2; up++) {
            set(helper, nether, slot.above(up), Blocks.WATER.defaultBlockState());
        }
        BoundVex vex = BoundVexTestSupport.noTargets(
                vexIn(helper, nether, player.position().add(3, 3, 0), player));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    soak(player);
                    helper.assertTrue(player.isUnderWater(), "setup: the player is under water in the Nether");
                    drownStep(player, 40);
                    assertNoRescue(helper, player, "no drowning rescue in the Nether");
                })
                .thenSucceed();
    }
}
