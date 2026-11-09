package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FAR_NORTH;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.boundVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.followOnly;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.parked;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.unmarkedVex;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Bound Vex GameTests (Evoking II spec section 3, section 11 "Part 2"), follow and portal area: following and
 * teleporting to the summoner, holding a post while staying, portal follow, same-dimension teleport, and the
 * logout and server-stop note tests. Tests whose vex flies or teleports, that need headroom, or that park many
 * vexes register through {@code FangGameTests.addPadded} (padding 13); the rest run inside the shared 3x3x3 box.
 * Shared constants and helpers live in {@link BoundVexTestSupport}.
 */
public final class BoundVexFollowGameTests {
    /**
     * The batches of the two server-stop tests: the stop handler clears every repair claim and portal note on the
     * server, so each test runs alone (batches run one after another), as {@code FangGameTests}' server-stop test does.
     * Two batches, because each test's handler call would clear what the other one is watching.
     */
    private static final String SERVER_STOP_NOTES = "bound_vex_server_stop_notes";

    private BoundVexFollowGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(
                SHARED, "bound_vex_follow_flies_into_the_orbit", BoundVexFollowGameTests::followFliesIntoTheOrbit);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_follow_teleports_from_thirteen_blocks",
                BoundVexFollowGameTests::followTeleportsFromThirteen);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_follow_idles_without_a_summoner",
                BoundVexFollowGameTests::followIdlesWithoutASummoner);
        FangGameTests.addPadded(
                SHARED, "bound_vex_staying_holds_its_post", BoundVexFollowGameTests::stayingHoldsItsPost);
        FangGameTests.addPadded(
                SHARED, "bound_vex_follows_again_after_stay", BoundVexFollowGameTests::followsAgainAfterStay);
        FangGameTests.addPadded(SHARED, "bound_vex_portal_follow", BoundVexFollowGameTests::portalFollow);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_same_dimension_teleport_brings_nothing",
                BoundVexFollowGameTests::sameDimensionTeleportBringsNothing);
        FangGameTests.addPadded(
                SHARED, "bound_vex_logout_drops_the_portal_note", BoundVexFollowGameTests::logoutDropsThePortalNote);
        FangGameTests.addPadded(
                SERVER_STOP_NOTES,
                "bound_vex_server_stop_drops_the_portal_note",
                BoundVexFollowGameTests::serverStopDropsThePortalNote);
    }

    /**
     * When a following Bound Vex hovers seven blocks from its summoner, should fly into the orbit band: three ticks in
     * it is still more than six blocks out (a teleport would have put it within {@code TELEPORT_LANDING_MAX}), and it
     * then comes within five blocks (the band's edge plus one; a flight toward a spot inside the band always crosses
     * that line). The vex has no target goals ({@link BoundVexTestSupport#followOnly}), so no hostile can pull it off
     * its flight.
     *
     * <p>Method under test: {@link FollowSummonerGoal}, through the vex's goal selector
     */
    static void followFliesIntoTheOrbit(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, FEET.north(7), player);
        helper.assertTrue(vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 2.0, "setup: seven blocks out");

        // Act & Assert
        helper.runAfterDelay(
                3,
                () -> helper.assertTrue(
                        vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 2.0,
                        "still flying after three ticks, not teleported, got " + vex.distanceTo(player)));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 3, "not before the flight check");
            helper.assertTrue(
                    vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                    "in the orbit band, got " + vex.distanceTo(player));
        });
    }

    /**
     * When a following Bound Vex is thirteen blocks from its summoner, should be beside the summoner within five ticks:
     * a flight could not cover that distance so fast, so it teleported. The vex has no target goals
     * ({@link BoundVexTestSupport#followOnly}), so no hostile in a neighboring structure can hold it.
     *
     * <p>Method under test: {@link FollowSummonerGoal}, through the vex's goal selector
     */
    static void followTeleportsFromThirteen(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, FEET.north(FAR_NORTH), player);

        // Act & Assert
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    vex.distanceTo(player) <= FollowSummonerGoal.TELEPORT_LANDING_MAX,
                    "beside the summoner after a teleport, got " + vex.distanceTo(player));
            helper.succeed();
        });
    }

    /**
     * When a Bound Vex with no summoner mark hovers thirteen blocks from a player, should neither fly nor teleport for
     * forty ticks: the follow goal is idle without a summoner (spec §10).
     *
     * <p>Method under test: {@link FollowSummonerGoal}, through the vex's goal selector
     */
    static void followIdlesWithoutASummoner(GameTestHelper helper) {
        // Arrange
        FangGameTests.caster(helper, NORTH);
        BoundVex vex = unmarkedVex(helper, FEET.north(FAR_NORTH));
        Vec3 start = vex.position();

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        40,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(start) < 0.01,
                                "an unmarked vex stays put, moved "
                                        + vex.position().distanceTo(start)))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex's post is thirteen blocks from its summoner, should neither fly nor teleport to the
     * summoner over sixty ticks. The post is the structure's middle and the player stands thirteen blocks south, so
     * the wander (±7 blocks around the post) stays inside the padding. The vex has no target goals
     * ({@link BoundVexTestSupport#followOnly}), so the distance check cannot be spoiled by a chase.
     *
     * <p>Method under test: {@link FollowSummonerGoal} and {@code BoundVex.StayWanderGoal}, through the goal selector
     */
    static void stayingHoldsItsPost(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, FEET, player);
        vex.setStaying(true);
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(FEET.south(FAR_NORTH)));

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        60,
                        () -> helper.assertTrue(
                                vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 1.0,
                                "a staying vex stays more than five blocks from the summoner, got "
                                        + vex.distanceTo(player)))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex whose post is thirteen blocks from its summoner is switched back to following after
     * twenty ticks, should come back within five blocks of its summoner (once any wander leg under way is done). The
     * post is the structure's middle and the player stands thirteen blocks south, so every wander leg (±7 blocks around
     * the post) stays inside the padding: a vex that crossed into a chunk the padding did not force would stop ticking
     * and never fly back (as in {@link #stayingHoldsItsPost}). The vex has no target goals
     * ({@link BoundVexTestSupport#followOnly}): the subject is movement, and its legs still reach four blocks into the
     * padding, where a neighboring test's hostile may stand and hold it (charge outranks follow).
     *
     * <p>If this fails again, the message names the vex's target, goals and positions ({@code vexState}).
     *
     * <p>Method under test: {@link BoundVex#setStaying} and {@link FollowSummonerGoal}, through the goal selector
     */
    static void followsAgainAfterStay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, FEET, player);
        vex.setStaying(true);
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(FEET.south(FAR_NORTH)));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(
                            vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 1.0,
                            "setup: still away while staying");
                    vex.setStaying(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "back beside the summoner, got " + vex.distanceTo(player) + "; " + vexState(vex, player)))
                .thenSucceed();
    }

    /**
     * When the summoner moves to the Nether, should bring the one following Bound Vex two blocks away to a ring cell
     * beside the arrival point, still marked, and leave behind the staying one, a following one seventeen blocks away
     * (past {@code PORTAL_RANGE}, which then idles where it is) and another player's vex two blocks away.
     *
     * <p>Method under test: {@link BoundVexEvents#onTravel} and {@link BoundVexEvents#onChangedDimension}
     */
    static void portalFollow(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerPlayer stranger = summoner(helper);
        UUID id = player.getUUID();
        BoundVex following = boundVex(helper, FEET.south(2), player);
        BoundVex staying = boundVex(helper, FEET.north(2), player);
        staying.setStaying(true);
        BoundVex far = boundVex(helper, FEET.offset(-12, 0, -12), player); // about 16.97 blocks from the player
        BoundVex strangers = boundVex(helper, FEET.east(2), stranger);
        Vec3 farStart = far.position();
        ServerLevel overworld = helper.getLevel();
        helper.assertTrue(far.distanceTo(player) > BoundVexEvents.PORTAL_RANGE, "setup: the far vex is out of range");

        // Act
        BlockPos netherFeet = FangGameTests.toNether(helper, player);
        ServerLevel nether = player.level();

        // Assert
        helper.succeedWhen(() -> {
            List<? extends BoundVex> arrived =
                    nether.getEntities(ModEntities.BOUND_VEX.get(), vex -> vex.isSummonedBy(id) && vex.isAlive());
            helper.assertValueEqual(arrived.size(), 1, "one Bound Vex in the Nether");
            helper.assertTrue(
                    arrived.get(0).blockPosition().closerThan(netherFeet, 4.5),
                    "beside the arrival point (a ring cell)");
            helper.assertFalse(arrived.get(0).isStaying(), "the one that came is the following one");
            helper.assertFalse(
                    following.isAlive(), "the old instance is gone (a cross-dimension teleport makes a new one)");
            List<? extends BoundVex> left =
                    overworld.getEntities(ModEntities.BOUND_VEX.get(), vex -> vex.isSummonedBy(id) && vex.isAlive());
            helper.assertValueEqual(left.size(), 2, "the staying and the far vex are left in the Overworld");
            helper.assertTrue(staying.isAlive() && staying.isStaying(), "the staying one stayed");
            helper.assertTrue(far.isAlive(), "the far one stayed");
            helper.assertTrue(
                    far.position().distanceTo(farStart) < 0.01,
                    "the far one idles without its summoner in the level, moved "
                            + far.position().distanceTo(farStart));
            helper.assertTrue(strangers.isAlive(), "another player's vex stayed");
        });
    }

    /**
     * When the summoner teleports within the Overworld (as a pearl or {@code /tp} does) with a following Bound Vex
     * near, and a changed-dimension event is then posted by hand, should move nothing: the travel event took no note
     * for a same-dimension teleport.
     *
     * <p>Method under test: {@link BoundVexEvents#onTravel} and {@link BoundVexEvents#onChangedDimension}
     */
    static void sameDimensionTeleportBringsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(2), player);
        helper.assertFalse(vex.isStaying(), "setup: a follower, so a note would have been taken");
        Vec3 start = vex.position();

        // Act: the event's "from" is the vex's own level, so a stale note would find the vex and move it
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(FEET.south(10)));
        ResourceKey<Level> here = helper.getLevel().dimension();
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, here, here));

        // Assert
        helper.assertTrue(vex.isAlive(), "the same vex is still here");
        helper.assertTrue(
                vex.position().distanceTo(start) < 0.01,
                "not moved to the summoner, moved " + vex.position().distanceTo(start));
        helper.succeed();
    }

    /**
     * Takes the portal note for {@code player} as a move to the Nether would (the travel event, posted by hand while
     * the player stays in the Overworld), then stands in for the end of the move: the changed-dimension event from the
     * Overworld, which brings the noted vexes to the player.
     */
    private static void noteThenArrive(ServerPlayer player, Runnable betweenTheEvents) {
        BoundVexEvents.onTravel(new EntityTravelToDimensionEvent(player, Level.NETHER));
        betweenTheEvents.run();
        BoundVexEvents.onChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(
                player, player.level().dimension(), Level.NETHER));
    }

    /**
     * When a player's portal note is taken and the player then logs out, should leave a later changed-dimension event
     * of that player (a cancelled travel event leaves a note behind, and the same player id returns) with nothing to
     * bring: a following vex eight blocks away stays where it is. Without the logout the same two events bring it (the
     * control).
     *
     * <p>Method under test: {@link BoundVexEvents#onLogout}, through {@link BoundVexEvents#onChangedDimension}
     */
    static void logoutDropsThePortalNote(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(8), player);
        Vec3 start = vex.position();
        noteThenArrive(player, () -> {});
        helper.assertTrue(
                vex.position().distanceTo(start) > 2.0,
                "setup: with the note kept, the vex is brought to the summoner");
        vex.setPos(start);

        // Act
        noteThenArrive(player, () -> BoundVexEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player)));

        // Assert
        helper.assertTrue(
                vex.position().distanceTo(start) < 0.01,
                "no note left after the logout, moved " + vex.position().distanceTo(start));
        helper.succeed();
    }

    /**
     * When a player's portal note is taken and the server then stops, should leave a later changed-dimension event
     * with nothing to bring (single-player loads one server per world in the same JVM): a following vex eight blocks
     * away stays where it is. Without the stop the same two events bring it (the control).
     *
     * <p>Method under test: {@link BoundVexEvents#onServerStopped}, through {@link BoundVexEvents#onChangedDimension}
     */
    static void serverStopDropsThePortalNote(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(8), player);
        Vec3 start = vex.position();
        noteThenArrive(player, () -> {});
        helper.assertTrue(
                vex.position().distanceTo(start) > 2.0,
                "setup: with the note kept, the vex is brought to the summoner");
        vex.setPos(start);

        // Act
        noteThenArrive(
                player,
                () -> BoundVexEvents.onServerStopped(
                        new ServerStoppedEvent(helper.getLevel().getServer())));

        // Assert
        helper.assertTrue(
                vex.position().distanceTo(start) < 0.01,
                "no note left after the server stopped, moved " + vex.position().distanceTo(start));
        helper.succeed();
    }

    /**
     * What a failing follow test needs to say why: the vex's target (type and distance), staying flag, whether the move
     * control holds a wanted spot, the running movement and target goals, and the vex's and player's positions.
     */
    private static String vexState(BoundVex vex, ServerPlayer player) {
        LivingEntity target = vex.getTarget();
        String targetText = target == null
                ? "none"
                : target.getType().toShortString() + " at " + vex.distanceTo(target) + " blocks";
        return "target " + targetText + ", staying " + vex.isStaying() + ", wanted "
                + vex.getMoveControl().hasWanted() + ", running goals "
                + runningGoals(vex.goalSelector.getAvailableGoals())
                + ", running target goals " + runningGoals(vex.targetSelector.getAvailableGoals()) + ", vex at "
                + vex.position() + ", player at " + player.position();
    }

    private static List<String> runningGoals(Collection<WrappedGoal> goals) {
        return goals.stream()
                .filter(WrappedGoal::isRunning)
                .map(wrapped -> wrapped.getGoal().getClass().getSimpleName())
                .toList();
    }
}
