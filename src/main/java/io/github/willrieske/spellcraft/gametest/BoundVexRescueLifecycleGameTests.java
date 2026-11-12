package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexRescueJobGameTests.lavaOn;
import static io.github.willrieske.spellcraft.gametest.BoundVexRescueJobGameTests.lavaStep;
import static io.github.willrieske.spellcraft.gametest.BoundVexRescueJobGameTests.netherRoom;
import static io.github.willrieske.spellcraft.gametest.BoundVexRescueJobGameTests.rescuePlayer;
import static io.github.willrieske.spellcraft.gametest.BoundVexRescueJobGameTests.stepWhileLive;
import static io.github.willrieske.spellcraft.gametest.BoundVexRescueJobGameTests.walkOut;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.SIDE_TOLERANCE;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.VexRescues;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of a summoner who leaves a rescue unfinished: dying while it pulls them out of lava or while its tail
 * shows the bucket, and changing dimension at either moment. The rescue must be gone, the rescuer released and the
 * conjured bucket dismissed, and no vex may stay pinned to the summoner's side.
 *
 * <p>Functional spec §6.5.
 */
public final class BoundVexRescueLifecycleGameTests {
    /** How many ticks after the lava rescue starts it is asserted to run. */
    private static final int WITHIN = 3;

    /** How far from the side point a released vex is put, to see whether anything pulls it back. */
    private static final double AWAY = 8.0;

    /** How close to the side point a vex still counts as pinned there after being put {@link #AWAY}. */
    private static final double PINNED = 3.0;

    /** How far around the Overworld arrival point the test looks for vexes that followed. */
    private static final double SEARCH = 24.0;

    /** How far from the summoner the rescuer is put before the move: past the range in which vexes follow (16). */
    private static final double BEYOND_FOLLOWING = 24.0;

    /** How many ticks a body may take. */
    private static final int BODY_TICKS = 200;

    private BoundVexRescueLifecycleGameTests() {}

    static void registerAll() {
        add("bound_vex_world_rescue_summoner_dies_mid_pull", BoundVexRescueLifecycleGameTests::diesMidPull);
        add("bound_vex_world_rescue_summoner_dies_mid_tail", BoundVexRescueLifecycleGameTests::diesMidTail);
        add(
                "bound_vex_world_rescue_summoner_changes_dimension_mid_pull",
                BoundVexRescueLifecycleGameTests::changesDimensionMidPull);
        add(
                "bound_vex_world_rescue_summoner_changes_dimension_mid_tail",
                BoundVexRescueLifecycleGameTests::changesDimensionMidTail);
        add(
                "bound_vex_world_rescue_summoner_changes_dimension_and_the_rescuer_stays_behind",
                BoundVexRescueLifecycleGameTests::changesDimensionLeavingTheRescuerBehind);
    }

    /** Registers a padded test with room for a pull, a tail and the aftermath. */
    private static void add(String name, Consumer<GameTestHelper> body) {
        FangGameTests.addPadded(SHARED, name, BODY_TICKS, body);
    }

    /** One lava rescue in a Nether room, as far as the test has taken it. */
    private record Scene(ServerPlayer player, ServerLevel nether, BlockPos slot, BoundVex vex, AtomicBoolean live) {}

    /** Builds the room, the lava column and the vex; the rescue starts when {@code live} is set. */
    private static Scene scene(GameTestHelper helper) {
        ServerPlayer player = rescuePlayer(helper);
        ServerLevel nether = netherRoom(helper, player);
        BlockPos slot = player.blockPosition();
        // A mock player is never ticked, which would end the immunity of a dimension change (it would make the kill
        // below do nothing).
        player.hasChangedDimension();
        BoundVex vex = BoundVexTestSupport.noTargets(
                vexIn(helper, nether, new Vec3(slot.getX() + 2.0, slot.getY() + 3.0, slot.getZ() + 0.5), player));
        AtomicBoolean live = new AtomicBoolean();
        stepWhileLive(helper, player, live, BoundVexRescueJobGameTests::lavaStep);
        return new Scene(player, nether, slot, vex, live);
    }

    /** Starts the lava rescue and returns when it runs (the vex has been held at the side). */
    private static void startPull(GameTestHelper helper, Scene scene) {
        lavaOn(helper, scene.nether(), scene.slot());
        scene.live().set(true);
    }

    /** Fails the test unless the lava rescue runs with the scene's vex held at the side. */
    private static void assertPulling(GameTestHelper helper, Scene scene) {
        helper.assertValueEqual(
                VexRescues.running(scene.player()), Optional.of(VexRescues.Kind.LAVA), "setup: the rescue runs");
        helper.assertTrue(
                scene.vex().position().distanceTo(scene.player().position().add(-0.6, 1.2, 0.0)) <= SIDE_TOLERANCE,
                "setup: the vex is held at the side");
    }

    /**
     * Ends the player's life and runs the rescue tick as the server would for a dead player: a dead player is still
     * ticked until it respawns, so {@code PlayerTickEvent.Post} fires for it (checked in the sources:
     * {@code ServerGamePacketListenerImpl.tickPlayer} calls {@code ServerPlayer.doTick}, whose {@code Player.tick}
     * posts the event).
     */
    private static void dieAndTick(GameTestHelper helper, Scene scene) {
        scene.live().set(false);
        scene.player().kill(scene.nether());
        helper.assertFalse(scene.player().isAlive(), "setup: the summoner is dead");
        VexRescues.tick(scene.player());
    }

    /** Fails the test unless nothing runs for the player and the vex is not held: put away, it stays away. */
    private static void assertReleased(GameTestHelper helper, Scene scene) {
        helper.assertTrue(VexRescues.running(scene.player()).isEmpty(), "no rescue entry is left");
        helper.assertTrue(VexRescues.rescuer(scene.player()).isEmpty(), "and no rescuer");
    }

    /**
     * When the summoner dies while a lava rescue pulls them, should the next rescue tick (the server ticks a dead
     * player) drop the entry, and a vex put eight blocks away stay there two ticks later: it is not held at the side.
     *
     * <p>Method under test: {@code VexRescues.tick}: the alive check
     */
    static void diesMidPull(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper);
        BoundVex vex = scene.vex();
        Vec3[] away = new Vec3[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> startPull(helper, scene))
                .thenExecuteAfter(WITHIN, () -> {
                    assertPulling(helper, scene);
                    dieAndTick(helper, scene);
                    assertReleased(helper, scene);
                    away[0] = scene.player().position().add(AWAY, 3.0, 0.0);
                    vex.setPos(away[0]);
                })
                .thenExecuteAfter(2, () -> {
                    VexRescues.tick(scene.player());
                    assertReleased(helper, scene);
                    helper.assertTrue(
                            vex.position().distanceTo(scene.player().position().add(-0.6, 1.2, 0.0)) > PINNED,
                            "the vex is not pinned to the dead summoner's side");
                })
                .thenSucceed();
    }

    /**
     * When the summoner dies during the tail of a Nether lava rescue (the bucket in the rescuer's hand), should the
     * next rescue tick drop the entry and take the conjured bucket away, the sword back, and the vex not be held.
     *
     * <p>Method under test: {@code VexRescues.tick} and {@code VexRescues.drop}: the alive check and the dismissal
     */
    static void diesMidTail(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper);
        BoundVex vex = scene.vex();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> startPull(helper, scene))
                .thenExecuteAfter(WITHIN, () -> walkOut(helper, scene.player(), scene.slot()))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.getMainHandItem().is(Items.WATER_BUCKET) && vex.isConjured(EquipmentSlot.MAINHAND),
                        "setup: the tail shows the bucket"))
                .thenExecute(() -> {
                    dieAndTick(helper, scene);
                    assertReleased(helper, scene);
                    helper.assertFalse(vex.isConjured(EquipmentSlot.MAINHAND), "the bucket is dismissed");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
                    vex.setPos(scene.player().position().add(AWAY, 3.0, 0.0));
                })
                .thenExecuteAfter(2, () -> {
                    VexRescues.tick(scene.player());
                    helper.assertTrue(
                            vex.position().distanceTo(scene.player().position().add(-0.6, 1.2, 0.0)) > PINNED,
                            "the vex is not pinned to the dead summoner's side");
                })
                .thenSucceed();
    }

    /**
     * The Bound Vexes of {@code player} that are alive in the Overworld test level around the stand, and in the
     * Nether level of {@code scene}.
     */
    private static List<BoundVex> followers(GameTestHelper helper, Scene scene) {
        List<BoundVex> found = new ArrayList<>();
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(FangGameTests.FEET));
        AABB overworldBox = new AABB(stand, stand).inflate(SEARCH);
        Vec3 here = Vec3.atBottomCenterOf(scene.slot());
        AABB netherBox = new AABB(here, here).inflate(SEARCH);
        for (BoundVex vex : helper.getLevel().getEntitiesOfClass(BoundVex.class, overworldBox)) {
            if (vex.isAlive() && vex.isSummonedBy(scene.player().getUUID())) {
                found.add(vex);
            }
        }
        for (BoundVex vex : scene.nether().getEntitiesOfClass(BoundVex.class, netherBox)) {
            if (vex.isAlive() && vex.isSummonedBy(scene.player().getUUID())) {
                found.add(vex);
            }
        }
        return found;
    }

    /**
     * Moves the summoner to the Overworld test level with the call the portal tests use (the travel event notes the
     * followers, the changed-dimension event brings them), runs the rescue tick in the new level as the server would,
     * and checks the aftermath: no rescue entry, and no vex of the summoner (the one that followed or any other)
     * keeps a conjured item or stays pinned to the side point.
     */
    private static void changeDimensionAndCheck(GameTestHelper helper, Scene scene) {
        scene.live().set(false);
        ServerPlayer player = scene.player();
        UUID id = player.getUUID();
        whenDone(helper, () -> {
            Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(FangGameTests.FEET));
            helper.getLevel()
                    .getEntitiesOfClass(BoundVex.class, new AABB(stand, stand).inflate(SEARCH), v -> v.isSummonedBy(id))
                    .forEach(BoundVex::discard);
        });
        MockPlayers.moveTo(helper, player, FangGameTests.STAND);
        helper.assertValueEqual(player.level().dimension(), Level.OVERWORLD, "setup: the summoner is in the Overworld");
        lavaStep(player); // the fluid state a tick of the real player would refresh in the new level, then the rescue
        // tick
        assertReleased(helper, scene);
    }

    /**
     * Fails the test unless every vex of the summoner is free: none holds a conjured item in its main hand, and none
     * returns to the side point after being put {@link #AWAY} from it.
     */
    private static void assertFollowersFree(GameTestHelper helper, Scene scene) {
        List<BoundVex> vexes = followers(helper, scene);
        for (BoundVex vex : vexes) {
            helper.assertFalse(vex.isConjured(EquipmentSlot.MAINHAND), "no vex keeps a conjured bucket or apple");
            vex.setPos(scene.player().position().add(AWAY, 3.0, 0.0));
        }
        helper.assertTrue(!vexes.isEmpty(), "setup: the vex followed the summoner");
    }

    /** Fails the test unless no vex of the summoner is within {@link #PINNED} of the side point. */
    private static void assertNoneHeld(GameTestHelper helper, Scene scene) {
        Vec3 side = scene.player().position().add(-0.6, 1.2, 0.0);
        for (BoundVex vex : followers(helper, scene)) {
            helper.assertTrue(vex.position().distanceTo(side) > PINNED, "no vex is pinned to the side point");
        }
    }

    /**
     * When the summoner changes dimension while a lava rescue pulls them (the vex follows through the portal events),
     * should the next rescue tick find no rescue to keep, no vex keep a conjured item, and a vex put eight blocks away
     * not be pulled to the side.
     *
     * <p>Method under test: {@code VexRescues.tick} and {@code VexRescues.holds}: a rescuer in another level
     *
     * <p>Functional spec §6.5.
     */
    static void changesDimensionMidPull(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper);
        BoundVex vex = scene.vex();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> startPull(helper, scene))
                .thenExecuteAfter(WITHIN, () -> {
                    assertPulling(helper, scene);
                    changeDimensionAndCheck(helper, scene);
                })
                .thenWaitUntil(() -> helper.assertTrue(!followers(helper, scene).isEmpty(), "the vex followed"))
                .thenExecute(() -> assertFollowersFree(helper, scene))
                .thenExecuteAfter(2, () -> {
                    VexRescues.tick(scene.player());
                    assertReleased(helper, scene);
                    assertNoneHeld(helper, scene);
                })
                .thenSucceed();
    }

    /**
     * When the summoner changes dimension during the tail of a Nether lava rescue, should the next rescue tick find no
     * rescue to keep, no vex keep the conjured bucket, and a vex put eight blocks away not be pulled to the side.
     *
     * <p>Method under test: {@code VexRescues.tick} and {@code VexRescues.holds}: a tail whose rescuer is gone
     *
     * <p>Functional spec §6.5.
     */
    static void changesDimensionMidTail(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper);
        BoundVex vex = scene.vex();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> startPull(helper, scene))
                .thenExecuteAfter(WITHIN, () -> walkOut(helper, scene.player(), scene.slot()))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.getMainHandItem().is(Items.WATER_BUCKET) && vex.isConjured(EquipmentSlot.MAINHAND),
                        "setup: the tail shows the bucket"))
                .thenExecute(() -> changeDimensionAndCheck(helper, scene))
                .thenWaitUntil(() -> helper.assertTrue(!followers(helper, scene).isEmpty(), "the vex followed"))
                .thenExecute(() -> assertFollowersFree(helper, scene))
                .thenExecuteAfter(2, () -> {
                    VexRescues.tick(scene.player());
                    assertReleased(helper, scene);
                    assertNoneHeld(helper, scene);
                })
                .thenSucceed();
    }

    /**
     * When the summoner changes dimension during the tail of a Nether lava rescue and the rescuer is too far to follow
     * (it stays alive in the Nether, bucket in hand), should the next rescue tick drop the entry because the rescuer
     * is in another level, and take the conjured bucket away.
     *
     * <p>Method under test: {@code VexRescues.holds}: the level clause
     *
     * <p>Functional spec §6.5.
     */
    static void changesDimensionLeavingTheRescuerBehind(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper);
        BoundVex vex = scene.vex();
        ServerPlayer player = scene.player();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> startPull(helper, scene))
                .thenExecuteAfter(WITHIN, () -> walkOut(helper, player, scene.slot()))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.getMainHandItem().is(Items.WATER_BUCKET) && vex.isConjured(EquipmentSlot.MAINHAND),
                        "setup: the tail shows the bucket"))
                .thenExecute(() -> {
                    scene.live().set(false);
                    vex.setPos(player.position().add(BEYOND_FOLLOWING, 0.0, 0.0));
                    MockPlayers.moveTo(helper, player, FangGameTests.STAND);
                    helper.assertValueEqual(
                            player.level().dimension(), Level.OVERWORLD, "setup: the summoner is in the Overworld");
                    helper.assertValueEqual(
                            vex.level().dimension(), Level.NETHER, "setup: the rescuer stayed in the Nether");
                    helper.assertTrue(vex.isAlive(), "setup: the rescuer is alive");
                    helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "setup: the bucket is still conjured");
                    VexRescues.tick(player);
                    assertReleased(helper, scene);
                    helper.assertFalse(vex.isConjured(EquipmentSlot.MAINHAND), "the bucket is dismissed");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
                })
                .thenSucceed();
    }
}
