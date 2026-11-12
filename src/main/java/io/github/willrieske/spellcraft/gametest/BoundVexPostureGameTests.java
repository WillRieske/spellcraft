package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.huskIn;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.padded;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.FEET;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.NIGHT;

import com.mojang.logging.LogUtils;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.spell.rules.GuardPosture;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * GameTests of the Bound Vex guard postures by place and time of day: the close posture's band, the staying
 * vex's scan cadence by place, and the close flight that yields to a target or follows a fleeing summoner.
 *
 * <p>Functional spec §6.1, §6.2, §6.3, §7.1.
 */
public final class BoundVexPostureGameTests {
    /** Where the posture tests log what they measured. */
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many ticks a close posture is watched once its vex is in place. */
    private static final int WATCH_TICKS = 60;

    /**
     * The least height above the summoner's feet a close-posture vex may hang at (spec §6.1, §9): a floor with
     * margin, below the in-place low edge of 2.0 (band 2.5 less the 0.5 slack) so one tick of coast does not fail it.
     */
    private static final double LEAST_HEIGHT = 1.7;

    /** A runaway guard only: a close-posture vex farther sideways than this has left its band by far. */
    private static final double RUNAWAY = 5.0;

    private BoundVexPostureGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_posture_overworld_day", BoundVexPostureGameTests::postureOverworldDay);
        FangGameTests.addPadded(NIGHT, "bound_vex_world_posture_night", BoundVexPostureGameTests::postureNight);
        padded("bound_vex_world_posture_nether", BoundVexPostureGameTests::postureNether);
        padded("bound_vex_world_posture_end", BoundVexPostureGameTests::postureEnd);
        padded("bound_vex_world_posture_staying_scans_by_place", BoundVexPostureGameTests::postureStayingScansByPlace);
        FangGameTests.addPadded(
                NIGHT,
                "bound_vex_world_posture_close_flight_yields_to_a_target",
                BoundVexPostureGameTests::closeFlightYieldsToATarget);
        FangGameTests.addPadded(
                NIGHT,
                "bound_vex_world_posture_close_flight_follows_a_fleeing_summoner",
                BoundVexPostureGameTests::closeFlightFollowsAFleeingSummoner);
        padded("bound_vex_world_scan_cadence_end", BoundVexPostureGameTests::scanCadenceEnd);
        padded("bound_vex_world_scan_cadence_overworld_day", BoundVexPostureGameTests::scanCadenceOverworldDay);
    }

    /**
     * When a following Bound Vex is in the Overworld by day, should its posture be the wide one and its scan interval
     * 10.
     *
     * <p>Method under test: {@code BoundVex.posture} and {@code BoundVex.scanInterval}
     *
     * <p>Functional spec §6.1, §6.2.
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
     *
     * <p>Functional spec §6.1, §6.2.
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
     *
     * <p>Functional spec §6.1, §6.2.
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
     *
     * <p>Functional spec §6.1, §6.2.
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
     * {@link #WATCH_TICKS} ticks, logs what it measured (the largest sideways distance, the lowest height, how many
     * times the vex set off for a new spot),
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
     *
     * <p>Functional spec §7.1.
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
     *
     * <p>Functional spec §6.3.
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
     *
     * <p>Functional spec §6.2.
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
     *
     * <p>Functional spec §6.2.
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
     *
     * <p>Functional spec §6.2.
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
}
