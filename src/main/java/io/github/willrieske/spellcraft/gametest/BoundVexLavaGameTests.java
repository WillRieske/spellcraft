package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.SIDE_TOLERANCE;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.assertLavaRunning;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.assertNoRescue;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.assertVelocity;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.column;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.followingVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.padded;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.set;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.soak;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.FEET;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.FleeCreeperGoal;
import io.github.willrieske.spellcraft.evoking.VexRescues;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * GameTests of the lava pull: the rescue that pulls a burning summoner to the shore, the Nether's water bucket
 * in its tail, and how a rescue ends (shore, timeout, logout, a dead rescuer or a server stop).
 */
public final class BoundVexLavaGameTests {
    /** The server-stop test clears every rescue on the server, so it runs in an environment of its own. */
    private static final String SERVER_STOP_RESCUES = "bound_vex_world_server_stop_rescues";

    /** How far sideways from the player the lava arrangement fills with stone (the shore search reaches 6). */
    private static final int POOL_REACH = 6;

    /** How far below the player's feet the lava arrangement fills with stone. */
    private static final int POOL_DEPTH = 6;

    /** The shore of {@link #lavaPool}, in the test coordinates: the center of the shore feet block. */
    private static final Vec3 SHORE = new Vec3(3.5, 1, 1.5);

    /** Passed to {@link #lavaPool} for an arrangement with no shore at all. */
    private static final int NO_SHORE = Integer.MIN_VALUE;

    private BoundVexLavaGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_lava_pull", BoundVexLavaGameTests::lavaPull);
        padded("bound_vex_world_lava_pull_keeps_your_steering", BoundVexLavaGameTests::lavaPullKeepsYourSteering);
        padded("bound_vex_world_lava_pull_ends_on_the_shore", BoundVexLavaGameTests::lavaPullEndsOnTheShore);
        padded("bound_vex_world_lava_pull_pauses_after_60_ticks", BoundVexLavaGameTests::lavaPullPausesAfter60);
        padded("bound_vex_world_lava_pull_ignores_a_low_shore", BoundVexLavaGameTests::lavaPullIgnoresALowShore);
        padded(
                "bound_vex_world_lava_pull_not_for_fire_resistance",
                helper -> lavaPullNotForTheUnhurt(helper, BoundVexLavaGameTests::fireResistant, false));
        padded(
                "bound_vex_world_lava_pull_not_for_spectators",
                helper -> lavaPullNotForTheUnhurt(helper, player -> player.setGameMode(GameType.SPECTATOR), false));
        padded(
                "bound_vex_world_lava_pull_not_for_creative",
                helper -> lavaPullNotForTheUnhurt(helper, player -> player.setGameMode(GameType.CREATIVE), false));
        padded("bound_vex_world_lava_pull_not_for_passengers", BoundVexLavaGameTests::lavaPullNotForPassengers);
        padded(
                "bound_vex_world_lava_pull_not_with_a_staying_vex",
                helper -> lavaPullNotForTheUnhurt(helper, player -> {}, true));
        padded("bound_vex_world_lava_rescuer_keeps_its_target", BoundVexLavaGameTests::lavaRescuerKeepsItsTarget);
        padded("bound_vex_world_rescue_dropped_on_logout", BoundVexLavaGameTests::rescueDroppedOnLogout);
        padded("bound_vex_world_lava_pull_takes_a_fleeing_vex", BoundVexLavaGameTests::lavaPullTakesAFleeingVex);
        padded(
                "bound_vex_world_lava_pull_replaces_a_dead_rescuer",
                BoundVexLavaGameTests::lavaPullReplacesADeadRescuer);
        padded("bound_vex_world_lava_overworld_keeps_burning", BoundVexLavaGameTests::lavaOverworldKeepsBurning);
        padded("bound_vex_world_lava_nether_bucket", BoundVexLavaGameTests::lavaNetherBucket);
        padded(
                "bound_vex_world_lava_nether_no_bucket_while_in_lava",
                BoundVexLavaGameTests::lavaNetherNoBucketWhileInLava);
        padded(
                "bound_vex_world_lava_rescuer_drops_a_target_out_of_reach",
                BoundVexLavaGameTests::lavaRescuerDropsATargetOutOfReach);
        padded(
                "bound_vex_world_lava_pull_ends_after_10_ticks_out",
                BoundVexLavaGameTests::lavaPullEndsAfterTenTicksOut);
        padded("bound_vex_world_lava_rescuer_range_inside", BoundVexLavaGameTests::lavaRescuerRangeInside);
        padded("bound_vex_world_lava_rescuer_range_outside", BoundVexLavaGameTests::lavaRescuerRangeOutside);
        padded("bound_vex_world_lava_shore_two_up", BoundVexLavaGameTests::lavaShoreTwoUp);
        padded("bound_vex_world_lava_shore_one_down", BoundVexLavaGameTests::lavaShoreOneDown);
        padded("bound_vex_world_lava_shore_three_up", BoundVexLavaGameTests::lavaShoreThreeUp);
        padded(
                "bound_vex_world_lava_pull_ends_with_fire_resistance",
                BoundVexLavaGameTests::lavaPullEndsWithFireResistance);
        padded("bound_vex_world_lava_pull_never_passes_the_cap", BoundVexLavaGameTests::lavaPullNeverPassesTheCap);
        padded(
                "bound_vex_world_lava_nether_tail_ends_when_the_rescuer_stays",
                BoundVexLavaGameTests::lavaTailEndsWhenTheRescuerStays);
        padded(
                "bound_vex_world_lava_nether_tail_ends_when_the_player_logs_out",
                BoundVexLavaGameTests::lavaTailEndsWhenThePlayerLogsOut);
        padded(
                "bound_vex_world_lava_nether_tail_ends_when_the_rescuer_dies",
                BoundVexLavaGameTests::lavaTailEndsWhenTheRescuerDies);
        FangGameTests.addPadded(
                SERVER_STOP_RESCUES,
                "bound_vex_world_rescues_cleared_on_server_stop",
                BoundVexLavaGameTests::rescuesClearedOnServerStop);
    }

    /**
     * The lava arrangement of the lava tests: a survival player (made unhurtable) at {@link FangGameTests#STAND},
     * facing south, in a two-block column of lava inside a solid mass of stone, so no air touches the lava (lava
     * spreads into air) and the only standable spot within the shore search is the shore this method carves, if any.
     * The shore is two blocks east of the player, {@code shoreDrop} blocks below their feet level (negative: above it):
     * stone under it, two blocks of air over it.
     *
     * @param helper the test helper
     * @param shoreDrop how far below the player feet level the shore feet block is, or {@link #NO_SHORE}
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
        if (shoreDrop != NO_SHORE) {
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
     * When the client last reported the player moving south at 0.2, should the lava pull keep that steering, slowed
     * by the lava drag: velocity {@code (0.1, 0.3, 0.1)}.
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
        assertVelocity(helper, player, 0.1, 0.3, 0.1);
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
     * A persistent-looking husk with no AI and no gravity that nothing in these tests can kill (1000 health), at
     * {@code relative}; discarded when the test ends.
     */
    private static Husk sturdyHusk(GameTestHelper helper, BlockPos relative) {
        Husk husk = BoundVexTestSupport.husk(helper, relative);
        husk.setNoGravity(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        husk.setHealth(1000.0F);
        return husk;
    }

    /**
     * When a full-AI rescuer is fighting a husk that is within the vex reach (7.25) of the player side point, should
     * it still have that target four ticks into the rescue. The vex hangs where it spawned (its follow goal is paused)
     * until it holds the husk: its scan can come up to 9 ticks after it spawns, and by then the follow flight would
     * have sunk it into the stone, where it cannot see the husk.
     *
     * <p>Method under test: {@code RescueGoal} and {@code VexRescues.start}: the move keeps a target in reach
     */
    static void lavaRescuerKeepsItsTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = BoundVexTestSupport.boundVex(helper, FEET.offset(0, 3, 4), player);
        Husk husk = sturdyHusk(helper, FEET.offset(0, 4, 3));
        Goal follow = BoundVexTestSupport.pauseFollowing(vex);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(husk.equals(vex.getTarget()), "setup: the vex fights the husk"))
                .thenExecute(() -> {
                    BoundVexTestSupport.resumeFollowing(vex, follow);
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                    helper.assertTrue(
                            husk.position().distanceTo(player.position().add(-0.6, 1.2, 0.0)) < 7.25,
                            "setup: the husk is within reach of the side point");
                })
                .thenExecuteAfter(4, () -> helper.assertTrue(husk.equals(vex.getTarget()), "the vex kept its target"))
                .thenSucceed();
    }

    /**
     * When a full-AI rescuer is fighting a husk 14 blocks from the player, should it have dropped that target four
     * ticks into the rescue: from the player side the husk is out of its reach, and the vex own rules drop it. The
     * follow goal is paused until the vex holds the husk, as in {@link #lavaRescuerKeepsItsTarget}.
     *
     * <p>Method under test: {@code VexRescues.start}: the move does not carry a far target along
     */
    static void lavaRescuerDropsATargetOutOfReach(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        Husk husk = sturdyHusk(helper, FEET.offset(10, 3, 10));
        BoundVex vex = BoundVexTestSupport.boundVex(helper, FEET.offset(8, 3, 8), player);
        Goal follow = BoundVexTestSupport.pauseFollowing(vex);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(husk.equals(vex.getTarget()), "setup: the vex fights the husk"))
                .thenExecute(() -> {
                    BoundVexTestSupport.resumeFollowing(vex, follow);
                    helper.assertTrue(husk.distanceTo(player) > 13.0, "setup: the husk is 14 blocks off");
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenExecuteAfter(
                        4,
                        () -> helper.assertFalse(
                                husk.equals(vex.getTarget()), "the vex dropped the target out of its reach"))
                .thenSucceed();
    }

    /**
     * When a player with a running rescue logs out, should the rescue be gone and the rescuer goal let go: the vex is
     * held at the side point before, and free (not snapped back) after.
     *
     * <p>Method under test: {@code VexRescues.onLogout}, {@code RescueGoal}
     */
    static void rescueDroppedOnLogout(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);
        Vec3[] away = new Vec3[1];

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenExecuteAfter(2, () -> {
                    Vec3 side = player.position().add(-0.6, 1.2, 0.0);
                    helper.assertTrue(vex.position().distanceTo(side) <= SIDE_TOLERANCE, "held at the side point");
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    assertNoRescue(helper, player, "the logout drops the rescue");
                    away[0] = side.add(0, 3, 0);
                    vex.setPos(away[0]);
                })
                .thenExecuteAfter(
                        2,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(away[0]) < 1.0, "the goal let go: the vex is free"))
                .thenSucceed();
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

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> creeper.setSwellDir(1))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.goalSelector.getAvailableGoals().stream()
                                .anyMatch(g -> g.isRunning() && g.getGoal() instanceof FleeCreeperGoal),
                        "setup: the flee goal runs"))
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
     * When the player is out of lava but in the air for the 10th tick in a row, should the rescue end on that tick and
     * not the 9th, and no tick out of lava should push.
     *
     * <p>Method under test: {@code VexRescues.tick}: the 10-tick end and no push out of lava
     */
    static void lavaPullEndsAfterTenTicksOut(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        Vec3[] pushed = new Vec3[1];

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                    pushed[0] = player.getDeltaMovement();
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    fillPool(helper, Blocks.AIR);
                    player.setOnGround(false);
                    step(player);
                    assertLavaRunning(helper, player, "out tick 1: the rescue runs");
                    helper.assertValueEqual(player.getDeltaMovement(), pushed[0], "out tick 1: no push");
                })
                .thenIdle(1)
                .thenExecuteFor(8, () -> {
                    step(player);
                    assertLavaRunning(helper, player, "out ticks 2 to 9: the rescue runs");
                    helper.assertValueEqual(player.getDeltaMovement(), pushed[0], "out ticks 2 to 9: no push");
                })
                .thenExecute(() -> {
                    step(player);
                    assertNoRescue(helper, player, "the 10th tick out ends the rescue");
                })
                .thenSucceed();
    }

    /**
     * When the player is in lava and a vex of theirs is {@code distance} blocks away, should it be the rescuer if and
     * only if {@code chosen}.
     */
    private static void lavaRescuerAt(GameTestHelper helper, double distance, boolean chosen) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);
        double along = distance / Math.sqrt(2.0);
        vex.setPos(player.position().add(along, 0.0, along));

        // Act
        step(player);

        // Assert
        helper.assertValueEqual(
                VexRescues.rescuer(player).equals(Optional.of(vex)),
                chosen,
                "the vex " + distance + " blocks away is chosen: " + chosen);
        helper.succeed();
    }

    /**
     * When a vex is 15.5 blocks from the player in lava, should it be chosen (the range is 16).
     *
     * <p>Method under test: {@code VexRescues.start}: the rescuer range
     */
    static void lavaRescuerRangeInside(GameTestHelper helper) {
        lavaRescuerAt(helper, 15.5, true);
    }

    /**
     * When a vex is 17 blocks from the player in lava, should it not be chosen.
     *
     * <p>Method under test: {@code VexRescues.start}: the rescuer range
     */
    static void lavaRescuerRangeOutside(GameTestHelper helper) {
        lavaRescuerAt(helper, 17.0, false);
    }

    /**
     * When the only standable spot is {@code drop} blocks below the player feet level (negative: above), should the
     * pull go toward it {@code (0.1, 0.3, 0)} if {@code pulls}, and lift {@code (0, 0.4, 0)} otherwise.
     */
    private static void lavaShoreAt(GameTestHelper helper, int drop, boolean pulls) {
        // Arrange
        ServerPlayer player = lavaPool(helper, drop);
        followingVex(helper, player);

        // Act
        step(player);

        // Assert
        assertLavaRunning(helper, player, "the rescue started");
        if (pulls) {
            assertVelocity(helper, player, 0.1, 0.3, 0.0);
        } else {
            assertVelocity(helper, player, 0.0, 0.4, 0.0);
        }
        helper.succeed();
    }

    /**
     * When the shore is two blocks above the player feet, should the pull go toward it.
     *
     * <p>Method under test: {@code StandableSpots.shore}: the top of the height window
     */
    static void lavaShoreTwoUp(GameTestHelper helper) {
        lavaShoreAt(helper, -2, true);
    }

    /**
     * When the shore is one block below the player feet, should the pull go toward it.
     *
     * <p>Method under test: {@code StandableSpots.shore}: the bottom of the height window
     */
    static void lavaShoreOneDown(GameTestHelper helper) {
        lavaShoreAt(helper, 1, true);
    }

    /**
     * When the shore is three blocks above the player feet, should the pull lift and not go toward it.
     *
     * <p>Method under test: {@code StandableSpots.shore}: just over the top of the height window
     */
    static void lavaShoreThreeUp(GameTestHelper helper) {
        lavaShoreAt(helper, -3, false);
    }

    /**
     * When the player gains Fire Resistance during the rescue, should the rescue end on the next step.
     *
     * <p>Method under test: {@code VexRescues.tick}: the player stops needing the rescue
     */
    static void lavaPullEndsWithFireResistance(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                    fireResistant(player);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    step(player);
                    assertNoRescue(helper, player, "the rescue ends once the player is fire resistant");
                })
                .thenSucceed();
    }

    /**
     * When the pushed velocity plus 0.02 toward the shore (the player steering) is fed back as the known movement for
     * 40 ticks, should the sideways speed never pass 0.35.
     *
     * <p>Method under test: {@code RescuePush.lavaPull} through {@code VexRescues.tick}: the drag and the cap
     */
    static void lavaPullNeverPassesTheCap(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        double[] fastest = {0.0};

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(40, () -> {
                    step(player);
                    Vec3 v = player.getDeltaMovement();
                    fastest[0] = Math.max(fastest[0], Math.hypot(v.x, v.z));
                    helper.assertTrue(Math.hypot(v.x, v.z) <= 0.35 + 1.0e-9, "the sideways speed passed the cap: " + v);
                    player.setKnownMovement(v.add(0.02, 0.0, 0.0));
                })
                .thenExecute(() -> {
                    assertLavaRunning(helper, player, "setup: the rescue ran the whole time");
                    helper.assertTrue(fastest[0] > 0.1, "setup: the player did move toward the shore");
                })
                .thenSucceed();
    }

    /**
     * A Nether tail: the rescue ends on the shore and the rescuer holds the bucket; then {@code interrupt} happens, one
     * more step runs, and the rescue must be gone (and the sword back, when {@code handBack}).
     */
    private static void interruptedTail(
            GameTestHelper helper, BiConsumer<ServerPlayer, BoundVex> interrupt, boolean handBack) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        BoundVex vex = netherPool(helper, player);
        BlockPos slot = player.blockPosition();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    netherWalkOut(helper, player, slot);
                    step(player);
                    helper.assertTrue(vex.getMainHandItem().is(Items.WATER_BUCKET), "setup: the tail holds the bucket");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    interrupt.accept(player, vex);
                    step(player);
                    assertNoRescue(helper, player, "the tail ends early");
                    if (handBack) {
                        helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the bucket is dismissed");
                    }
                })
                .thenSucceed();
    }

    /**
     * When the rescuer is told to stay during the tail, should the tail end and the bucket be dismissed.
     *
     * <p>Method under test: {@code VexRescues.tick}: a rescuer that no longer holds, in the tail
     */
    static void lavaTailEndsWhenTheRescuerStays(GameTestHelper helper) {
        interruptedTail(helper, (player, vex) -> vex.setStaying(true), true);
    }

    /**
     * When the player logs out during the tail, should the tail end and the bucket be dismissed.
     *
     * <p>Method under test: {@code VexRescues.onLogout}, in the tail
     */
    static void lavaTailEndsWhenThePlayerLogsOut(GameTestHelper helper) {
        interruptedTail(
                helper, (player, vex) -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)), true);
    }

    /**
     * When the rescuer dies during the tail, should the rescue be dropped.
     *
     * <p>Method under test: {@code VexRescues.tick}: a dead rescuer, in the tail
     */
    static void lavaTailEndsWhenTheRescuerDies(GameTestHelper helper) {
        interruptedTail(
                helper, (player, vex) -> vex.kill(helper.getLevel().getServer().getLevel(Level.NETHER)), false);
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
