package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FAR_NORTH;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.VEX_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.boundVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.followOnly;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.parked;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.unmarkedVex;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.evoking.VexSummons;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
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
        FangGameTests.addPadded(
                SHARED, "bound_vex_end_exit_brings_followers", BoundVexFollowGameTests::endExitBringsFollowers);
        FangGameTests.addPadded(
                SHARED, "bound_vex_death_respawn_brings_nothing", BoundVexFollowGameTests::deathRespawnBringsNothing);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_follow_ignores_a_spectating_summoner",
                BoundVexFollowGameTests::followIgnoresASpectatingSummoner);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_staying_vex_reposts_in_another_dimension",
                BoundVexFollowGameTests::stayingVexRepostsInAnotherDimension);
        FangGameTests.addPadded(
                SHARED, "bound_vex_portal_note_skips_a_dying_vex", BoundVexFollowGameTests::portalNoteSkipsADyingVex);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_portal_arrival_skips_a_vex_killed_after_the_note",
                BoundVexFollowGameTests::portalArrivalSkipsAVexKilledAfterTheNote);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_orbit_spots_stay_inside_the_band",
                BoundVexFollowGameTests::orbitSpotsStayInsideTheBand);
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
     * Respawns {@code player} the way the End exit does ({@code endExit}: {@code keepAllPlayerData} true, as the
     * credits path calls it) or the way a death does, at the middle of this test's structure: a temporary listener
     * sends the respawn there (the default spawn point is far from the structure, in chunks the test does not load).
     * The old player instance is gone afterwards; the new one is returned.
     */
    private static ServerPlayer respawnAtTheStructure(GameTestHelper helper, ServerPlayer player, boolean endExit) {
        Vec3 home = helper.absoluteVec(Vec3.atBottomCenterOf(FEET));
        Consumer<PlayerRespawnPositionEvent> toTheStructure = event -> {
            if (event.getEntity().getUUID().equals(player.getUUID())) {
                event.setTeleportTransition(new TeleportTransition(
                        helper.getLevel(), home, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerRespawnPositionEvent.class, toTheStructure);
        try {
            return helper.getLevel()
                    .getServer()
                    .getPlayerList()
                    .respawn(
                            player,
                            endExit,
                            endExit ? Entity.RemovalReason.CHANGED_DIMENSION : Entity.RemovalReason.KILLED);
        } finally {
            NeoForge.EVENT_BUS.unregister(toTheStructure);
        }
    }

    /** The Bound Vexes of {@code summoner} alive in {@code level}. */
    private static List<? extends BoundVex> vexesOf(ServerLevel level, UUID summoner) {
        return level.getEntities(ModEntities.BOUND_VEX.get(), vex -> vex.isAlive() && vex.isSummonedBy(summoner));
    }

    /**
     * When a summoner with two Bound Vexes in the Nether (one following, one staying) leaves through the End exit's
     * respawn path ({@code PlayerList.respawn(player, true, CHANGED_DIMENSION)}, which posts neither the travel nor
     * the changed-dimension event), should find the following vex beside the respawned player in the Overworld, still
     * marked, and the staying one still in the Nether. The Nether stands in for the End, as the path is the same.
     *
     * <p>Method under test: {@link BoundVexEvents#onRespawnPosition} and {@link BoundVexEvents#onRespawn}
     */
    static void endExitBringsFollowers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        UUID id = player.getUUID();
        FangGameTests.toNether(helper, player);
        ServerLevel nether = player.level();
        List<BoundVex> vexes = VexSummons.summon(nether, player, 2);
        helper.assertValueEqual(vexes.size(), 2, "setup: two Bound Vexes beside the summoner");
        BoundVex following = vexes.get(0);
        BoundVex staying = vexes.get(1);
        staying.setStaying(true);
        whenDone(helper, () -> vexesOf(helper.getLevel(), id).forEach(Entity::discard));
        ServerPlayer[] respawned = new ServerPlayer[1];

        // Act
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        nether.getEntities().get(following.getUUID()) != null
                                && nether.getEntities().get(staying.getUUID()) != null,
                        "setup: both vexes are in the Nether's entity lookup"))
                .thenExecute(() -> {
                    respawned[0] = respawnAtTheStructure(helper, player, true);
                    helper.assertValueEqual(
                            respawned[0].level().dimension(), Level.OVERWORLD, "setup: respawned in the Overworld");
                })
                // Assert
                .thenWaitUntil(() -> {
                    List<? extends BoundVex> arrived = vexesOf(helper.getLevel(), id);
                    helper.assertValueEqual(arrived.size(), 1, "one Bound Vex came to the Overworld");
                    helper.assertTrue(
                            arrived.get(0).blockPosition().closerThan(helper.absolutePos(FEET), 4.5),
                            "beside the respawned player (a ring cell)");
                    helper.assertFalse(arrived.get(0).isStaying(), "the one that came is the following one");
                    helper.assertFalse(following.isAlive(), "the old instance is gone");
                    helper.assertTrue(
                            staying.isAlive()
                                    && staying.level().dimension().equals(Level.NETHER)
                                    && staying.isStaying(),
                            "the staying one stayed in the Nether");
                })
                .thenSucceed();
    }

    /**
     * When a summoner with a following Bound Vex in the Nether respawns the way a death does
     * ({@code keepAllPlayerData} false), should bring nothing: the vex stays in the Nether (spec §3.3, no cross-dimension
     * teleport on respawn after a death).
     *
     * <p>Method under test: {@link BoundVexEvents#onRespawnPosition} and {@link BoundVexEvents#onRespawn}
     */
    static void deathRespawnBringsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        UUID id = player.getUUID();
        FangGameTests.toNether(helper, player);
        ServerLevel nether = player.level();
        BoundVex following = VexSummons.summon(nether, player, 1).get(0);
        whenDone(helper, () -> vexesOf(helper.getLevel(), id).forEach(Entity::discard));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        nether.getEntities().get(following.getUUID()) != null, "setup: the vex is in the lookup"))
                .thenExecute(() -> respawnAtTheStructure(helper, player, false))
                .thenExecuteFor(5, () -> {
                    helper.assertTrue(vexesOf(helper.getLevel(), id).isEmpty(), "no vex in the Overworld");
                    helper.assertTrue(
                            following.isAlive() && following.level().dimension().equals(Level.NETHER),
                            "the vex is still in the Nether");
                })
                .thenSucceed();
    }

    /**
     * When a summoner with a following Bound Vex seven blocks away switches to spectator mode, should leave the vex
     * where it is for forty ticks (a spectator counts as absent, spec §3.1: vanilla pets ignore a spectating owner);
     * back in survival, should fly into the orbit again.
     *
     * <p>Method under test: {@link BoundVex#summoner}, through {@link FollowSummonerGoal}
     */
    static void followIgnoresASpectatingSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, FEET.north(7), player);
        player.setGameMode(GameType.SPECTATOR);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        40,
                        () -> helper.assertTrue(
                                vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 2.0,
                                "a vex does not follow a spectator, got " + vex.distanceTo(player)))
                .thenExecute(() -> player.setGameMode(GameType.SURVIVAL))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "it follows again in survival, got " + vex.distanceTo(player)))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex is moved to another dimension (as {@code /execute in ... run tp} or a vehicle does; the
     * move builds a new vex that copies the save), should still be staying there and have its post where it arrived,
     * not the old dimension's coordinates.
     *
     * <p>Method under test: {@link BoundVex#restoreFrom} and {@link BoundVex#onAddedToLevel}, through
     * {@code Entity.teleport}
     */
    static void stayingVexRepostsInAnotherDimension(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, FEET.north(5), player);
        vex.setStaying(true);
        BlockPos post = vex.getBoundOrigin();
        BlockPos netherFeet = FangGameTests.toNether(helper, player);
        ServerLevel nether = player.level();

        // Act
        Entity moved = vex.teleport(new TeleportTransition(
                nether, Vec3.atCenterOf(netherFeet.above()), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));

        // Assert
        if (!(moved instanceof BoundVex copy)) {
            throw helper.assertionException("the vex was not moved to a new Bound Vex: " + moved);
        }
        helper.assertTrue(copy.isStaying(), "still staying in the new dimension");
        helper.assertFalse(post.equals(copy.getBoundOrigin()), "the post is not the old dimension's " + post);
        helper.assertValueEqual(copy.getBoundOrigin(), copy.blockPosition(), "the post is where it arrived");
        helper.succeed();
    }

    /**
     * When a following Bound Vex is at zero health (dying, still in the level) when the summoner's travel event is
     * posted, and is revived before the arrival event, should not be brought: the note skipped it. A healthy vex is
     * brought by the same two events (the control).
     *
     * <p>Method under test: {@link BoundVexEvents#onTravel}: the living filter
     */
    static void portalNoteSkipsADyingVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(8), player);
        Vec3 start = vex.position();
        vex.setHealth(0.0F);
        helper.assertFalse(vex.isAlive(), "setup: the vex is dying");

        // Act: revived before the arrival, so only the note's filter can have left it out
        noteThenArrive(player, () -> vex.setHealth(vex.getMaxHealth()));

        // Assert
        helper.assertTrue(
                vex.position().distanceTo(start) < 0.01,
                "a vex that was dying at the note is not brought, moved "
                        + vex.position().distanceTo(start));
        noteThenArrive(player, () -> {});
        helper.assertTrue(vex.position().distanceTo(start) > 2.0, "control: a healthy vex is brought");
        helper.succeed();
    }

    /**
     * When a following Bound Vex is noted at the travel event and killed (zero health, still in the level) before the
     * arrival event, should not be brought: the arrival skips a dying vex. A healthy vex is brought by the same two
     * events (the control).
     *
     * <p>Method under test: {@link BoundVexEvents#onChangedDimension}: the living filter
     */
    static void portalArrivalSkipsAVexKilledAfterTheNote(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(8), player);
        Vec3 start = vex.position();

        // Act
        noteThenArrive(player, () -> vex.setHealth(0.0F));

        // Assert
        helper.assertFalse(vex.isAlive(), "setup: the vex is dying");
        helper.assertTrue(
                vex.position().distanceTo(start) < 0.01,
                "a vex killed after the note is not brought, moved "
                        + vex.position().distanceTo(start));
        vex.setHealth(vex.getMaxHealth());
        noteThenArrive(player, () -> {});
        helper.assertTrue(vex.position().distanceTo(start) > 2.0, "control: a healthy vex is brought");
        helper.succeed();
    }

    /**
     * When the follow goal starts two hundred times for a vex near its summoner, should always aim the move control at
     * a spot 2 to 4 blocks from the summoner's feet, at least 2 blocks sideways and 0.5 to 1.5 blocks up (spec §3.3:
     * the band is 2-4; a spot inside the player's body would block the view). Synchronous: the test calls the goal's
     * {@code start} and reads the wanted position.
     *
     * <p>Method under test: {@link FollowSummonerGoal#start}
     */
    static void orbitSpotsStayInsideTheBand(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = followOnly(helper, VEX_SPOT, player);
        FollowSummonerGoal follow = vex.goalSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(FollowSummonerGoal.class::isInstance)
                .map(FollowSummonerGoal.class::cast)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("the vex has no follow goal"));
        Vec3 feet = player.position();

        // Act & Assert
        for (int trial = 0; trial < 200; trial++) {
            follow.start();
            MoveControl<?> control = vex.getMoveControl();
            double dx = control.getWantedX() - feet.x;
            double dy = control.getWantedY() - feet.y;
            double dz = control.getWantedZ() - feet.z;
            double sideways = Math.sqrt(dx * dx + dz * dz);
            helper.assertTrue(
                    sideways >= FollowSummonerGoal.ORBIT_MIN - 1.0E-9,
                    "trial " + trial + ": at least " + FollowSummonerGoal.ORBIT_MIN + " blocks sideways, got "
                            + sideways);
            helper.assertTrue(dy >= 0.5 - 1.0E-9 && dy <= 1.5 + 1.0E-9, "trial " + trial + ": 0.5-1.5 up, got " + dy);
            helper.assertTrue(
                    Math.sqrt(dx * dx + dy * dy + dz * dz) <= FollowSummonerGoal.ORBIT_MAX,
                    "trial " + trial + ": inside the band's outer edge");
        }
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
