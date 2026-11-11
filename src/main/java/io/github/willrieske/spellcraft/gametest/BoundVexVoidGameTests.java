package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.SIDE_TOLERANCE;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.assertNoRescue;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.column;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.endVoid;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.followingVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.padded;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.platform;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.set;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.voidPlayer;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.VexRescues;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * GameTests of the void catch: the End rescue that carries a falling summoner to the last ground or a landing
 * spot, who is caught and by which vex, and how the last-ground and no-landing records are kept and cleared.
 */
public final class BoundVexVoidGameTests {
    /** How far the void tests' places reach: the landing search looks 32 blocks, the farthest platform is 40 away. */
    private static final int VOID_REACH = 48;

    /** How far east of the player's column the landing platform stands. */
    private static final int LANDING_EAST = 10;

    /** How far east of the player's column the last-ground platform stands: outside the 32-block landing search. */
    private static final int GROUND_EAST = 40;

    /** The height of the test platforms; the End slot is at 80. */
    private static final int PLATFORM_Y = 60;

    /** How far under the End's lowest Y a fallen player is. */
    private static final int BELOW_FLOOR = 5;

    /** How far east of the player's column a void test's vex is by default. */
    private static final int VEX_EAST = 3;

    /** How close the player must land to an expected point, in blocks. */
    private static final double LANDING_TOLERANCE = 0.01;

    private BoundVexVoidGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_void_catch", BoundVexVoidGameTests::voidCatch);
        padded(
                "bound_vex_world_void_catch_falling_near_the_floor",
                BoundVexVoidGameTests::voidCatchFallingNearTheFloor);
        padded(
                "bound_vex_world_void_catch_not_with_ground_below",
                helper -> voidNotCaughtNearTheFloor(helper, true, new Vec3(0.0, -0.5, 0.0)));
        padded(
                "bound_vex_world_void_catch_not_when_not_falling",
                helper -> voidNotCaughtNearTheFloor(helper, false, Vec3.ZERO));
        padded("bound_vex_world_void_catch_uses_last_ground", BoundVexVoidGameTests::voidCatchUsesLastGround);
        padded("bound_vex_world_void_catch_last_ground_gone", BoundVexVoidGameTests::voidCatchLastGroundGone);
        padded("bound_vex_world_void_catch_rescuer_range_inside", helper -> voidRescuerRange(helper, 40, true));
        padded("bound_vex_world_void_catch_rescuer_range_outside", helper -> voidRescuerRange(helper, 70, false));
        padded("bound_vex_world_void_catch_rescuer_range_edge_inside", helper -> voidRescuerRange(helper, 63, true));
        padded("bound_vex_world_void_catch_rescuer_range_edge_outside", helper -> voidRescuerRange(helper, 64, false));
        padded("bound_vex_world_void_catch_by_a_following_vex", BoundVexVoidGameTests::voidCatchByAFollowingVex);
        padded("bound_vex_world_void_catch_not_by_a_staying_vex", BoundVexVoidGameTests::voidCatchNotByAStayingVex);
        padded("bound_vex_world_void_catch_refused_teleport", BoundVexVoidGameTests::voidCatchRefusedTeleport);
        padded(
                "bound_vex_world_void_logout_forgets_the_empty_search",
                BoundVexVoidGameTests::voidLogoutForgetsTheSearch);
        padded("bound_vex_world_void_catch_dismounts", BoundVexVoidGameTests::voidCatchDismounts);
        padded("bound_vex_world_void_catch_creative", BoundVexVoidGameTests::voidCatchCreative);
        padded(
                "bound_vex_world_void_catch_not_for_a_ghostrail_carrier",
                BoundVexVoidGameTests::voidCatchNotForAGhostrailCarrier);
        padded("bound_vex_world_void_catch_not_while_gliding", BoundVexVoidGameTests::voidCatchNotWhileGliding);
        padded("bound_vex_world_void_catch_skips_a_portal_column", BoundVexVoidGameTests::voidCatchSkipsAPortalColumn);
        padded(
                "bound_vex_world_void_no_landing_is_not_searched_every_tick",
                BoundVexVoidGameTests::voidNoLandingIsNotSearchedEveryTick);
        padded("bound_vex_world_void_not_in_the_overworld", BoundVexVoidGameTests::voidNotInTheOverworld);
    }

    /**
     * A vex of {@code player} in the End, {@code east} blocks east of the slot's column and 3 blocks up from the
     * End's lowest Y, with no targets and no AI: the follow goal would teleport it to the player's old place before the
     * test drops the player, so it stays where it was put.
     */
    private static BoundVex voidVex(
            GameTestHelper helper, ServerLevel end, ServerPlayer player, BlockPos slot, int east) {
        BoundVex vex = BoundVexTestSupport.noTargets(
                vexIn(helper, end, new Vec3(slot.getX() + 0.5 + east, end.getMinY() + 3, slot.getZ() + 0.5), player));
        vex.setNoAi(true);
        return vex;
    }

    /** Puts {@code player} at {@code at}, in the air. */
    private static void dropTo(ServerPlayer player, Vec3 at) {
        player.setPos(at);
        player.setOnGround(false);
    }

    /** Puts {@code player} at {@code at}, standing on the ground. */
    private static void standAt(ServerPlayer player, Vec3 at) {
        player.setPos(at);
        player.setOnGround(true);
    }

    /** Fails the test unless a void catch is running for {@code player}. */
    private static void assertVoidRunning(GameTestHelper helper, ServerPlayer player, String what) {
        helper.assertValueEqual(VexRescues.running(player), Optional.of(VexRescues.Kind.VOID), what);
    }

    /** Fails the test unless {@code player} is within {@link #LANDING_TOLERANCE} of {@code expected}. */
    private static void assertLandedAt(GameTestHelper helper, ServerPlayer player, Vec3 expected) {
        helper.assertTrue(
                player.position().distanceTo(expected) <= LANDING_TOLERANCE,
                "the player is at " + expected + ", not " + player.position());
    }

    /** Fails the test unless a rescue was refused: none runs and {@code player} is still at {@code expected}. */
    private static void assertStillThere(GameTestHelper helper, ServerPlayer player, Vec3 expected, String what) {
        assertNoRescue(helper, player, what);
        assertLandedAt(helper, player, expected);
    }

    /**
     * When a player is five blocks under the End's lowest Y and a platform stands ten blocks east of their column,
     * should one step teleport them onto the platform's nearest column, with no fall distance and no velocity, give
     * them the four effects of the enchanted golden apple, run a void catch whose rescuer is at their side holding the
     * apple, and, 20 steps later, end it with the sword back in the rescuer's hand.
     *
     * <p>Method under test: {@code VexRescues.tick}, {@code StandableSpots.landing} through it
     */
    static void voidCatch(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        BlockPos nearest = slot.offset(LANDING_EAST - 1, PLATFORM_Y + 1 - slot.getY(), 0);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, column(slot, end.getMinY() - BELOW_FLOOR));
                    player.fallDistance = 40.0;
                    player.setDeltaMovement(0.0, -1.5, 0.0);
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the void catch started");
                    helper.assertValueEqual(player.blockPosition(), nearest, "the platform's nearest column");
                    helper.assertTrue(end.getBlockState(nearest.below()).is(Blocks.STONE), "stone under the feet");
                    helper.assertTrue(end.getBlockState(nearest).isAir(), "air at the feet");
                    helper.assertValueEqual(player.fallDistance, 0.0, "the fall is forgotten");
                    helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "the player is still");
                    helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "Regeneration");
                    helper.assertTrue(player.hasEffect(MobEffects.RESISTANCE), "Resistance");
                    helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE), "Fire Resistance");
                    helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION), "Absorption");
                    helper.assertTrue(
                            vex.position().distanceTo(player.position().add(-0.6, 1.2, 0.0)) <= SIDE_TOLERANCE,
                            "the vex hangs at the side, not " + vex.position());
                    helper.assertTrue(
                            vex.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE), "the vex holds the apple");
                    helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "the apple is conjured");
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the tail runs");
                    helper.assertTrue(vex.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE), "the apple stays");
                })
                .thenExecute(() -> {
                    VexRescues.tick(player);
                    assertNoRescue(helper, player, "the tail ends after 20 steps");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
                })
                .thenSucceed();
    }

    /**
     * When a player who stood on a platform 40 blocks east (outside the landing search) is in the air ten blocks
     * above the End's lowest Y, moving down with nothing below them, should one step teleport them back onto it.
     *
     * <p>Method under test: {@code VexRescues.tick}, {@code VoidRule.needsCatch} through it: the near-floor case
     */
    static void voidCatchFallingNearTheFloor(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(GROUND_EAST, PLATFORM_Y - slot.getY(), 0), 0);
        BoundVex vex = voidVex(helper, end, player, slot, 20);
        Vec3 ground = column(slot.east(GROUND_EAST), PLATFORM_Y + 1);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    standAt(player, ground);
                    VexRescues.tick(player);
                    assertNoRescue(helper, player, "setup: standing needs no catch");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    dropTo(player, column(slot, end.getMinY() + 10));
                    player.setKnownMovement(new Vec3(0.0, -0.5, 0.0));
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "falling with nothing below needs a catch");
                    assertLandedAt(helper, player, ground);
                })
                .thenSucceed();
    }

    /**
     * When a player is ten blocks above the End's lowest Y with a landing platform near, should one step catch no one
     * when a stone block is five blocks under them (and they fall), or when they are not moving down.
     *
     * @param helper the test helper
     * @param stoneBelow whether a stone block stands under the player's column
     * @param movement the player's known movement
     */
    private static void voidNotCaughtNearTheFloor(GameTestHelper helper, boolean stoneBelow, Vec3 movement) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
        if (stoneBelow) {
            platform(helper, end, new BlockPos(slot.getX(), end.getMinY() + 5, slot.getZ()), 0);
        }
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        Vec3 at = column(slot, end.getMinY() + 10);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, at);
                    player.setKnownMovement(movement);
                    VexRescues.tick(player);
                    assertStillThere(helper, player, at, "no catch");
                })
                .thenSucceed();
    }

    /**
     * When a player who never stood anywhere is under the floor, should no catch start; and when they then stand on a
     * platform 40 blocks east (one step records it) and fall again, all within the 20 ticks of the empty search's
     * memory, should the catch put them back on it (the memory pauses only the landing search).
     *
     * <p>Method under test: {@code VexRescues.tick}: the last-ground fallback and its memory
     */
    static void voidCatchUsesLastGround(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(GROUND_EAST, PLATFORM_Y - slot.getY(), 0), 0);
        BoundVex vex = voidVex(helper, end, player, slot, 20);
        Vec3 ground = column(slot.east(GROUND_EAST), PLATFORM_Y + 1);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "with no ground to go back to, nothing happens");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    standAt(player, ground);
                    VexRescues.tick(player);
                    assertNoRescue(helper, player, "setup: standing needs no catch");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the catch started");
                    assertLandedAt(helper, player, ground);
                })
                .thenSucceed();
    }

    /**
     * When the platform a player last stood on is gone before they fall, should no catch start.
     *
     * <p>Method under test: {@code VoidCatch.catchFromVoid}: the last ground must still be standable
     */
    static void voidCatchLastGroundGone(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        BlockPos stone = slot.offset(GROUND_EAST, PLATFORM_Y - slot.getY(), 0);
        platform(helper, end, stone, 0);
        BoundVex vex = voidVex(helper, end, player, slot, 20);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    standAt(player, column(slot.east(GROUND_EAST), PLATFORM_Y + 1));
                    VexRescues.tick(player);
                    assertNoRescue(helper, player, "setup: standing needs no catch");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    set(helper, end, stone, Blocks.AIR.defaultBlockState());
                    dropTo(player, fallen);
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "a broken bridge is not a landing");
                })
                .thenSucceed();
    }

    /**
     * When a vex {@code east} blocks away from a player under the floor stands near a landing platform, should the
     * catch start only if the vex is within 64 blocks.
     *
     * @param helper the test helper
     * @param east how far east of the player's column the vex is
     * @param caught whether the player is expected to be caught
     */
    private static void voidRescuerRange(GameTestHelper helper, int east, boolean caught) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, 80);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
        BoundVex vex = voidVex(helper, end, player, slot, east);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    VexRescues.tick(player);
                    if (caught) {
                        assertVoidRunning(helper, player, "a vex " + east + " blocks away catches");
                    } else {
                        assertStillThere(helper, player, fallen, "a vex " + east + " blocks away is too far");
                    }
                })
                .thenSucceed();
    }

    /**
     * When a player rides a pig under the End's floor, should the catch dismount them and put them on the platform.
     *
     * <p>Method under test: {@code VoidCatch.catchFromVoid}: vanilla's teleport dismounts the player (the catch
     * relies on it)
     */
    static void voidCatchDismounts(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        Mob pig = EntityTypes.PIG.create(end, EntitySpawnReason.STRUCTURE);
        if (pig == null) {
            throw helper.assertionException("setup: could not create a pig");
        }
        pig.snapTo(slot.getX() + 0.5, slot.getY(), slot.getZ() + 0.5, 0.0F, 0.0F);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setPermanentlyInvulnerable(true);
        end.addFreshEntity(pig);
        whenDone(helper, pig::discard);
        helper.assertTrue(player.startRiding(pig), "setup: the mock player can ride");

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, column(slot, end.getMinY() - BELOW_FLOOR));
                    helper.assertTrue(player.isPassenger(), "setup: still riding");
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the catch started");
                    helper.assertFalse(player.isPassenger(), "the player is off the pig");
                    helper.assertTrue(player.getY() > PLATFORM_Y, "the player is on the platform");
                })
                .thenSucceed();
    }

    /**
     * When a creative player is under the floor, should the catch start unless they are flying: flying first (none),
     * then not flying (caught).
     *
     * <p>Method under test: {@code VexRescues.tick}: creative players are caught, flying ones are not
     */
    static void voidCatchCreative(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);
        player.setGameMode(GameType.CREATIVE);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    player.getAbilities().flying = true;
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "a flying creative player is left alone");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    player.getAbilities().flying = false;
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "a creative player who is not flying is caught");
                    helper.assertTrue(player.getY() > PLATFORM_Y, "the player is on the platform");
                })
                .thenSucceed();
    }

    /**
     * When a player with Ghostrail is under the floor, should no catch start (it takes no damage and is flying).
     *
     * <p>Method under test: {@code VexRescues.tick}: a Ghostrail carrier is not rescued
     */
    static void voidCatchNotForAGhostrailCarrier(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
                    dropTo(player, column(slot, end.getMinY() - BELOW_FLOOR));
                    VexRescues.tick(player);
                    assertStillThere(
                            helper,
                            player,
                            column(slot, end.getMinY() - BELOW_FLOOR),
                            "no void catch for a Ghostrail carrier");
                })
                .thenSucceed();
    }

    /**
     * When a player glides (elytra flight) under the floor, should no catch start.
     *
     * <p>Method under test: {@code VexRescues.tick}: gliding players are left alone
     */
    static void voidCatchNotWhileGliding(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    player.startFallFlying();
                    helper.assertTrue(player.isFallFlying(), "setup: the mock player glides");
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "a gliding player is left alone");
                })
                .thenSucceed();
    }

    /**
     * When the platform column nearest the fallen player is bedrock under an End portal block, should the player
     * land on another column, never in the portal.
     *
     * <p>Method under test: {@code StandableSpots.landing}: a column whose top is not standable is skipped
     */
    static void voidCatchSkipsAPortalColumn(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        BlockPos middle = slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0);
        platform(helper, end, middle, 1);
        BlockPos portalColumn = middle.west();
        set(helper, end, portalColumn, Blocks.BEDROCK.defaultBlockState());
        set(helper, end, portalColumn.above(), Blocks.END_PORTAL.defaultBlockState());
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, column(slot, end.getMinY() - BELOW_FLOOR));
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the catch started");
                    BlockPos feet = player.blockPosition();
                    helper.assertTrue(
                            feet.getX() != portalColumn.getX() || feet.getZ() != portalColumn.getZ(),
                            "the player is not in the portal column: " + feet);
                    helper.assertTrue(end.getBlockState(feet).isAir(), "the player's feet are in air");
                    helper.assertTrue(end.getBlockState(feet.below()).is(Blocks.STONE), "stone under the feet");
                })
                .thenSucceed();
    }

    /**
     * When a catch found no landing and no last ground, should the next 19 steps not search again (a platform built
     * meanwhile is not found), and the 20th step after the first catch the player.
     *
     * <p>Method under test: {@code VoidCatch.catchFromVoid}: the 20-tick memory of a failed search
     */
    static void voidNoLandingIsNotSearchedEveryTick(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);
        long[] first = {0};

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    first[0] = helper.getLevel().getGameTime();
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "setup: nothing to land on");
                    platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "the search is not repeated yet");
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - first[0], 20L, "setup: this is the 20th step after");
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the memory has run out: the platform is found");
                })
                .thenSucceed();
    }

    /**
     * When a player is far under the Overworld's lowest Y with a vex near, should no catch start (the catch is for
     * the End).
     *
     * <p>Method under test: {@code VoidCatch.needsCatch}: the dimension check
     */
    static void voidNotInTheOverworld(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        BoundVex vex = followingVex(helper, player);
        ServerLevel level = helper.getLevel();
        whenDone(helper, () -> MockPlayers.moveTo(helper, player, FangGameTests.STAND));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    Vec3 fallen = column(player.blockPosition(), level.getMinY() - BELOW_FLOOR);
                    dropTo(player, fallen);
                    vex.setPos(fallen.add(3.0, 3.0, 0.0));
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "no catch outside the End");
                })
                .thenSucceed();
    }

    /**
     * The arrangement of the void catch tests: an unhurtable player in the End void with a landing platform ten
     * blocks east, placed in a place of reach 48.
     *
     * @param helper the test helper
     * @param player the player
     * @return the End's level
     */
    private static ServerLevel voidWithPlatform(GameTestHelper helper, ServerPlayer player) {
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
        return end;
    }

    /**
     * When the only vex has its full AI and flies 25 blocks east of a player who has already fallen, should it be
     * the rescuer. The player is parked under the floor before the vex spawns, so the follow goal moves it toward
     * the player, never away; wherever it is after its first ticks it is within 64 blocks.
     *
     * <p>Method under test: {@code VexRescues.nearestRescuer} with a vex that runs its goals
     */
    static void voidCatchByAFollowingVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = voidWithPlatform(helper, player);
        BlockPos slot = player.blockPosition();
        dropTo(player, column(slot, end.getMinY() - BELOW_FLOOR));
        BoundVex vex = BoundVexTestSupport.noTargets(
                vexIn(helper, end, new Vec3(slot.getX() + 0.5 + 25, end.getMinY() + 3, slot.getZ() + 0.5), player));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the catch started");
                    helper.assertValueEqual(VexRescues.rescuer(player), Optional.of(vex), "the vex is the rescuer");
                })
                .thenSucceed();
    }

    /**
     * When the only vex beside the fall point is told to stay, should no catch start.
     *
     * <p>Method under test: {@code VexRescues.holds}: a staying vex does not rescue
     */
    static void voidCatchNotByAStayingVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = voidWithPlatform(helper, player);
        BlockPos slot = player.blockPosition();
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        vex.setStaying(true);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "a staying vex does not catch");
                })
                .thenSucceed();
    }

    /**
     * When something cancels the teleport (a listener on the travel event), should the catch start nothing: no rescue,
     * no apple effects, the player where they were, the rescuer's sword in its hand; and should it not try again
     * (no search, no second teleport) during the next 19 steps.
     *
     * <p>Method under test: {@code VoidCatch.catchFromVoid}: the teleport's refusal
     */
    static void voidCatchRefusedTeleport(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = voidWithPlatform(helper, player);
        BlockPos slot = player.blockPosition();
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);
        AtomicInteger refused = new AtomicInteger();
        Consumer<EntityTravelToDimensionEvent> refuse = event -> {
            if (event.getEntity().equals(player)) {
                refused.incrementAndGet();
                event.setCanceled(true);
            }
        };
        whenDone(helper, () -> NeoForge.EVENT_BUS.unregister(refuse));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    NeoForge.EVENT_BUS.addListener(refuse);
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "a refused teleport starts no rescue");
                    helper.assertValueEqual(refused.get(), 1, "the teleport was tried once");
                    helper.assertFalse(player.hasEffect(MobEffects.REGENERATION), "no apple was eaten");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the rescuer's hand is untouched");
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    VexRescues.tick(player);
                    helper.assertValueEqual(refused.get(), 1, "no second attempt while the pause runs");
                })
                .thenExecute(() -> NeoForge.EVENT_BUS.unregister(refuse))
                .thenSucceed();
    }

    /**
     * When a search found nothing (the memory pauses it for 20 ticks), the player logs out, and a platform is built,
     * should the very next step find it: the logout forgets the memory.
     *
     * <p>Method under test: {@code VexRescues.onLogout}: the empty search's memory
     */
    static void voidLogoutForgetsTheSearch(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        Vec3 fallen = column(slot, end.getMinY() - BELOW_FLOOR);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, fallen);
                    VexRescues.tick(player);
                    assertStillThere(helper, player, fallen, "setup: nothing to land on");
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    platform(helper, end, slot.offset(LANDING_EAST, PLATFORM_Y - slot.getY(), 0), 1);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the memory was forgotten: the platform is found");
                })
                .thenSucceed();
    }
}
