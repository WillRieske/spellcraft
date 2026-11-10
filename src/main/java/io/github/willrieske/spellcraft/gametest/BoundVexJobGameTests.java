package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.boundVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.followOnly;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.husk;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.noTargets;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.patientCreeper;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.placeBeside;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.DodgeGolemGoal;
import io.github.willrieske.spellcraft.evoking.FleeCreeperGoal;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Bound Vex GameTests (Evoking II spec section 3, section 11 "Part 2"), jobs area: fleeing a creeper, dodging a
 * golem's swing, repairing golems, repair claims and the server-stop claims test. Tests whose vex flies or
 * teleports, that need headroom, or that park many vexes register through {@code FangGameTests.addPadded}
 * (padding 13); the rest run inside the shared 3x3x3 box. Shared constants and helpers live in {@link
 * BoundVexTestSupport}.
 */
public final class BoundVexJobGameTests {
    private static final String SERVER_STOP_CLAIMS = "bound_vex_server_stop_claims";

    /**
     * Where the golem tests stand the golem: four blocks east of the box's middle, in the padding. Not three: a golem
     * is 1.4 wide and 2.7 tall, and its eyes would then sit in the box's barrier shell (x = 3), which hurts it
     * ("inWall", 1 health per tick or so), so a "full" golem would soon need repair.
     */
    private static final BlockPos GOLEM_SPOT = FEET.east(4);

    private BoundVexJobGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(
                SERVER_STOP_CLAIMS,
                "bound_vex_server_stop_frees_the_repair_claims",
                BoundVexJobGameTests::serverStopFreesTheRepairClaims);
        FangGameTests.addPadded(
                SHARED, "bound_vex_flees_a_hissing_creeper", BoundVexJobGameTests::fleesAHissingCreeper);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_flee_ignores_a_calm_or_far_creeper",
                BoundVexJobGameTests::fleeIgnoresACalmOrFarCreeper);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_flee_ends_when_the_creeper_is_gone",
                BoundVexJobGameTests::fleeEndsWhenTheCreeperIsGone);
        FangGameTests.addPadded(SHARED, "bound_vex_dodges_a_golems_swing", BoundVexJobGameTests::dodgesAGolemsSwing);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_dodge_ignores_a_golem_that_is_not_swinging",
                BoundVexJobGameTests::dodgeIgnoresAGolemThatIsNotSwinging);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_a_fighting_vex_does_not_start_a_repair",
                BoundVexJobGameTests::fightingVexDoesNotStartARepair);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_a_repair_stopped_in_flight_keeps_the_held_item",
                BoundVexJobGameTests::repairStoppedInFlightKeepsTheHeldItem);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_a_golem_angered_mid_repair_is_dropped",
                BoundVexJobGameTests::golemAngeredMidRepairIsDropped);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_no_repair_for_a_golem_outside_the_guard_box",
                BoundVexJobGameTests::noRepairOutsideTheGuardBox);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_a_golem_leaving_the_guard_box_ends_the_repair",
                BoundVexJobGameTests::golemLeavingTheGuardBoxEndsTheRepair);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_dodge_ignores_a_swing_away_or_far",
                BoundVexJobGameTests::dodgeIgnoresASwingAwayOrFar);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_dodge_ignores_a_diagonal_swing_beyond_three_blocks",
                BoundVexJobGameTests::dodgeIgnoresADiagonalSwingBeyondThreeBlocks);
        FangGameTests.addPadded(
                SHARED, "bound_vex_job_repairing_vex_is_not_free", BoundVexJobGameTests::repairingVexIsNotFree);
        FangGameTests.addPadded(
                SHARED, "bound_vex_repairs_a_hurt_ally_golem", BoundVexJobGameTests::repairsAHurtAllyGolem);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_repair_taps_come_every_thirty_ticks",
                BoundVexJobGameTests::tapsComeEveryThirtyTicks);
        FangGameTests.addPadded(
                SHARED, "bound_vex_repairs_two_golems_apart", BoundVexJobGameTests::repairsTwoGolemsApart);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_repair_claim_frees_when_the_vex_is_gone",
                BoundVexJobGameTests::claimFreesWhenTheVexIsGone);
        FangGameTests.addPadded(
                SHARED, "bound_vex_a_fight_interrupts_a_repair", BoundVexJobGameTests::fightInterruptsARepair);
        FangGameTests.addPadded(
                SHARED, "bound_vex_flee_interrupts_a_repair", BoundVexJobGameTests::fleeInterruptsARepair);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_no_repair_for_a_mad_or_full_golem",
                BoundVexJobGameTests::noRepairForAMadOrFullGolem);
    }

    /**
     * A {@link BoundVexTestSupport#patientCreeper} that floats, for a test whose creeper must live on: the barrier
     * shell at the box's edge ({@code FEET.north(2)} and nearer) hurts a creeper standing in it, so a creeper there
     * dies in the end (the same "inWall" damage as {@link #GOLEM_SPOT}'s note), and without gravity it needs no floor
     * in the padded box.
     */
    private static Creeper floatingCreeper(GameTestHelper helper, BlockPos relative) {
        Creeper creeper = patientCreeper(helper, relative);
        creeper.setNoGravity(true);
        return creeper;
    }

    /**
     * A golem with no AI at {@code relative} and {@code health}, standing on a stone block (the padded box is air), and
     * discarded when the test ends (it stands in the padding, outside the structure's own cleanup).
     */
    private static IronGolem golemOnStone(GameTestHelper helper, BlockPos relative, float health) {
        helper.setBlock(relative.below(), Blocks.STONE);
        IronGolem golem = GolemGameTests.golem(helper, relative, health);
        whenDone(helper, golem::discard);
        return golem;
    }

    /**
     * {@link BoundVexTestSupport#boundVex} without its follow goal, so it stays where a repair test puts it until a job
     * moves it.
     */
    private static BoundVex unleashed(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = boundVex(helper, relative, summoner);
        vex.goalSelector.removeAllGoals(goal -> goal instanceof FollowSummonerGoal);
        return vex;
    }

    /** {@link #unleashed} without its target goals, so a repair test's vex has a target only when the test gives it. */
    private static BoundVex unleashedWithoutTargets(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        return noTargets(unleashed(helper, relative, summoner));
    }

    /** Turns {@code golem}'s body and head to {@code yaw} (−90 east, 90 west, 0 south, 180 north). */
    private static void face(IronGolem golem, float yaw) {
        golem.setYRot(yaw);
        golem.setYHeadRot(yaw);
        golem.yBodyRot = yaw;
    }

    /**
     * When a creeper seven blocks from a following Bound Vex (inside the eight-block notice range, far outside a short
     * one), three blocks from the summoner, starts swelling, should fly to eight blocks from it and hold, which puts it
     * more than five blocks from the summoner; when the creeper calms down (and stays alive), should come back to the
     * summoner (the follow goal resumes). The creeper floats off the barrier shell ({@link #floatingCreeper}), so it
     * lives on and only the goal's own swelling check can end the flee: without it the vex hovers there until the
     * test times out. The vex has no target goals ({@link BoundVexTestSupport#followOnly}): it ends up about eleven
     * blocks out, near the edge of its padding.
     *
     * <p>Method under test: {@link FleeCreeperGoal}, through the goal selector: the notice range and
     * {@code canContinueToUse}'s swelling check
     */
    static void fleesAHissingCreeper(GameTestHelper helper) {
        // Arrange: the creeper is between the vex and the summoner, so fleeing it means flying away from the summoner
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Creeper creeper = floatingCreeper(helper, FEET.north(3));
        BoundVex vex = followOnly(helper, FEET.north(10), player);
        helper.assertTrue(
                vex.distanceTo(creeper) > 6.0 && vex.distanceTo(creeper) < 8.0,
                "setup: the creeper is six to eight blocks off, got " + vex.distanceTo(creeper));

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> creeper.setSwellDir(1))
                .thenExecuteFor(
                        8,
                        () -> helper.assertTrue(
                                vex.distanceTo(creeper) >= 6.5,
                                "flees at once from seven blocks, not toward the summoner past the creeper, got "
                                        + vex.distanceTo(creeper)))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(creeper) >= FleeCreeperGoal.SAFE_DISTANCE,
                        "eight blocks from the creeper, got " + vex.distanceTo(creeper)))
                .thenExecuteFor(
                        20,
                        () -> helper.assertTrue(
                                vex.distanceTo(creeper) >= FleeCreeperGoal.SAFE_DISTANCE - 1.0, "holds there"))
                .thenExecute(() -> helper.assertTrue(
                        vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "setup: it fled away from its summoner, so the way back needs the follow goal, got "
                                + vex.distanceTo(player)))
                .thenExecute(() -> creeper.setSwellDir(-1))
                .thenWaitUntil(() -> {
                    helper.assertTrue(creeper.isAlive(), "setup: the calm creeper is alive, so only the swell ends it");
                    helper.assertTrue(
                            vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                            "back with the summoner once the creeper calms, got " + vex.distanceTo(player));
                })
                .thenSucceed();
    }

    /**
     * When a creeper two blocks from a Bound Vex, three blocks from its summoner, swells and the vex flees and holds,
     * and the creeper is then removed while still swelling (as an explosion does, with the swell direction left at 1),
     * should fly back to the summoner: the goal must end on a creeper that is gone, not only on one that calms. The
     * creeper floats off the barrier shell ({@link #floatingCreeper}).
     *
     * <p>Method under test: {@link FleeCreeperGoal}: {@code canContinueToUse}'s alive check, through the goal selector
     */
    static void fleeEndsWhenTheCreeperIsGone(GameTestHelper helper) {
        // Arrange: the creeper is between the vex and the summoner
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Creeper creeper = floatingCreeper(helper, FEET.north(3));
        BoundVex vex = followOnly(helper, FEET.north(5), player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> creeper.setSwellDir(1))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(creeper) >= FleeCreeperGoal.SAFE_DISTANCE,
                        "eight blocks from the creeper, got " + vex.distanceTo(creeper)))
                .thenExecuteFor(
                        20,
                        () -> helper.assertTrue(
                                vex.distanceTo(creeper) >= FleeCreeperGoal.SAFE_DISTANCE - 1.0,
                                "holds there while it hisses, got " + vex.distanceTo(creeper)))
                .thenExecute(() -> {
                    helper.assertValueEqual(creeper.getSwellDir(), 1, "setup: still swelling when it goes");
                    creeper.discard();
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "back with the summoner once the creeper is gone, got " + vex.distanceTo(player)))
                .thenSucceed();
    }

    /**
     * When a Bound Vex six blocks from its summoner has a calm creeper between them (two blocks from the vex) and a
     * swelling one about 8.5 blocks from the vex (outside the eight-block range but inside the cube a box query
     * covers), should fly in to its summoner: a flee goal wrongly started by either creeper would hold the move flag
     * and keep the follow goal from flying it. Once the near creeper, put beside the vex, swells, should flee it (the
     * control). The vex has no target goals ({@link BoundVexTestSupport#followOnly}): the subject is movement.
     *
     * <p>Method under test: {@link FleeCreeperGoal}: the swelling and range filters
     */
    static void fleeIgnoresACalmOrFarCreeper(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, FEET.north(6), player);
        Creeper near = patientCreeper(helper, FEET.north(4));
        near.setNoGravity(true);
        Creeper far = patientCreeper(helper, FEET.offset(6, 0, -12)); // (6, 0, -6) from the vex: about 8.49 blocks
        far.setSwellDir(1);
        helper.assertTrue(
                vex.distanceTo(far) > FleeCreeperGoal.NOTICE_RANGE, "setup: the swelling creeper is out of range");
        helper.assertTrue(
                vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 1.0,
                "setup: the vex must fly to be with its summoner");

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "flies in past a calm creeper, with a far one hissing, got " + vex.distanceTo(player)))
                .thenExecute(() -> {
                    near.setPos(vex.position().add(0.0, 0.0, -2.0));
                    near.setSwellDir(1);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(near) >= FleeCreeperGoal.SAFE_DISTANCE,
                        "flees the near creeper once it swells, got " + vex.distanceTo(near)))
                .thenSucceed();
    }

    /**
     * When an iron golem two blocks from a Bound Vex swings with its head facing it, should dart two to four and a half
     * blocks sideways (perpendicular to the golem's look; the dart is three) within twelve ticks and not away from it.
     * The summoner stands two blocks east of the vex, so the follow goal is idle on either side of the dart (about 3.6
     * blocks away).
     *
     * <p>Method under test: {@code DodgeGolemGoal} (package-private), through the goal selector
     */
    static void dodgesAGolemsSwing(GameTestHelper helper) {
        // Arrange: the golem looks east; the vex hovers two blocks east of it
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(9.5, 1, 1.5), NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 100.0F);
        face(golem, -90.0F);
        BoundVex vex = boundVex(helper, GOLEM_SPOT.east(2), player);
        Vec3 before = vex.position();

        // Act
        golem.handleEntityEvent((byte) 4);

        // Assert
        helper.assertTrue(golem.getAttackAnimationTick() > 0, "setup: the golem is mid-swing");
        helper.runAfterDelay(12, () -> {
            Vec3 moved = vex.position().subtract(before);
            helper.assertTrue(
                    Math.abs(moved.z) >= 2.0 && Math.abs(moved.z) <= 4.5,
                    "darted about three blocks sideways (north or south), got dz " + moved.z);
            helper.assertTrue(Math.abs(moved.x) < 1.5, "not away from the golem, got dx " + moved.x);
            helper.succeed();
        });
    }

    /**
     * When one golem two blocks from a Bound Vex swings with its head turned away, and another five blocks away swings
     * facing it, should not dart for ten ticks; once the near golem turns to face it and swings, should dart (the
     * control).
     *
     * <p>Method under test: {@code DodgeGolemGoal} (package-private): the facing and range filters
     */
    static void dodgeIgnoresASwingAwayOrFar(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(9.5, 1, 1.5), NORTH);
        IronGolem away = golemOnStone(helper, GOLEM_SPOT, 100.0F);
        face(away, 90.0F); // looks west, the vex is east
        IronGolem far = golemOnStone(helper, GOLEM_SPOT.east(2).north(5), 100.0F);
        face(far, 0.0F); // looks south, at the vex five blocks off
        BoundVex vex = boundVex(helper, GOLEM_SPOT.east(2), player);
        Vec3 before = vex.position();

        // Act
        away.handleEntityEvent((byte) 4);
        far.handleEntityEvent((byte) 4);

        // Assert
        helper.startSequence()
                .thenExecuteFor(
                        10,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(before) < 0.5,
                                "no dart from a swing away or far, moved "
                                        + vex.position().distanceTo(before)))
                .thenExecute(() -> {
                    face(away, -90.0F);
                    away.handleEntityEvent((byte) 4);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        Math.abs(vex.position().z - before.z) >= 1.0, "darts once the golem faces it"))
                .thenSucceed();
    }

    /**
     * When a golem facing a Bound Vex swings from 3.6 blocks away diagonally (three east and two south: inside the
     * three-block box the search covers, outside the three-block range), should not dart for ten ticks; once the golem
     * stands 2.8 blocks away and swings facing it, should dart (the control).
     *
     * <p>Method under test: {@code DodgeGolemGoal} (package-private): the range is a sphere, not the search box
     */
    static void dodgeIgnoresADiagonalSwingBeyondThreeBlocks(GameTestHelper helper) {
        // Arrange: the golem looks straight at the vex
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(9.5, 1, 3.5), NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 100.0F);
        BoundVex vex = boundVex(helper, GOLEM_SPOT.offset(3, 0, 2), player);
        face(golem, (float) Math.toDegrees(Math.atan2(-3.0, 2.0)));
        Vec3 before = vex.position();
        helper.assertTrue(vex.distanceTo(golem) > DodgeGolemGoal.NOTICE_RANGE, "setup: the golem is out of range");

        // Act
        golem.handleEntityEvent((byte) 4);

        // Assert
        helper.startSequence()
                .thenExecuteFor(
                        10,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(before) < 0.5,
                                "no dart from a swing beyond three blocks, moved "
                                        + vex.position().distanceTo(before)))
                .thenExecute(() -> {
                    placeBeside(vex, golem, -2, -2);
                    face(golem, (float) Math.toDegrees(Math.atan2(-2.0, 2.0)));
                    golem.handleEntityEvent((byte) 4);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.position().distanceTo(before) >= 1.0, "darts once the golem is within three blocks"))
                .thenSucceed();
    }

    /**
     * When a golem two blocks from a Bound Vex faces it but is not swinging (its attack animation is at 0), should not
     * dart for twenty ticks; once the golem swings, should dart (the control).
     *
     * <p>Method under test: {@code DodgeGolemGoal} (package-private): the swing condition
     */
    static void dodgeIgnoresAGolemThatIsNotSwinging(GameTestHelper helper) {
        // Arrange: the golem looks east at the vex, two blocks east of it, and is full, so nothing needs repair
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(9.5, 1, 1.5), NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 100.0F);
        face(golem, -90.0F);
        BoundVex vex = boundVex(helper, GOLEM_SPOT.east(2), player);
        Vec3 before = vex.position();
        helper.assertValueEqual(golem.getAttackAnimationTick(), 0, "setup: the golem is not swinging");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        20,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(before) < 0.5,
                                "no dart from a golem that faces it without swinging, moved "
                                        + vex.position().distanceTo(before)))
                .thenExecute(() -> golem.handleEntityEvent((byte) 4))
                .thenWaitUntil(() ->
                        helper.assertTrue(Math.abs(vex.position().z - before.z) >= 1.0, "darts once the golem swings"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex is repairing a hurt ally golem (it holds the claim), should the free-vex list leave it out and keep
     * an idle vex of the same summoner in.
     *
     * <p>Method under test: {@code Jobs.busy}, through {@link TeamMoves#isFree}
     */
    static void repairingVexIsNotFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(6.5, 1, 1.5), NORTH);
        golemOnStone(helper, GOLEM_SPOT, 50.0F);
        BoundVex worker = unleashed(helper, GOLEM_SPOT.north(2).above(2), player);
        BoundVex idle = TeamMoveTestSupport.frozenVexes(helper, player, 1).get(0);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(worker.getMainHandItem().is(Items.IRON_INGOT), "setup: it repairs"))
                .thenExecute(() -> {
                    helper.assertFalse(TeamMoves.isFree(worker, player), "a vex mid-job is not free");
                    helper.assertValueEqual(TeamMoves.freeVexes(player), List.of(idle), "only the idle vex is listed");
                })
                .thenSucceed();
    }

    /**
     * When an ally golem at half health stands in the guard area of two idle Bound Vexes, both out of reach of its
     * chest, should have exactly one vex fly in and take it (holding an iron ingot while the other keeps its sword),
     * heal it by exactly 25 (to 75) on the first tap with the repair sound and by 25 more on the second, and give the
     * vex its sword back once the golem is full. The spacing of the taps is {@link #tapsComeEveryThirtyTicks}: a vex
     * that flies in can drift out of reach between taps, which holds the tap clock, so it cannot pin the spacing.
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private), through the goal selector: {@code HEAL}
     */
    static void repairsAHurtAllyGolem(GameTestHelper helper) {
        // Arrange: each vex starts about 2.1 blocks from the chest (reach 1.5), so the one that claims must fly
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(6.5, 1, 1.5), NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        BoundVex first = boundVex(helper, GOLEM_SPOT.north(2).above(2), player);
        BoundVex second = boundVex(helper, GOLEM_SPOT.south(2).above(2), player);
        List<BoundVex> vexes = List.of(first, second);
        GameTestSupport.drainSounds(player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getHealth() > 50.0F, "the first tap lands"))
                .thenExecute(() -> {
                    helper.assertValueEqual(golem.getHealth(), 75.0F, "the first tap heals exactly 25");
                    long ingots = vexes.stream()
                            .filter(vex -> vex.getMainHandItem().is(Items.IRON_INGOT))
                            .count();
                    long swords = vexes.stream()
                            .filter(vex -> vex.getMainHandItem().is(Items.IRON_SWORD))
                            .count();
                    helper.assertValueEqual(ingots, 1L, "one vex holds the ingot");
                    helper.assertValueEqual(swords, 1L, "the other keeps its sword");
                    helper.assertTrue(golem.getHealth() < 100.0F, "setup: not full after one tap");
                    helper.assertTrue(
                            GameTestSupport.drainSounds(player).contains(SoundEvents.IRON_GOLEM_REPAIR),
                            "the tap played the repair sound");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(golem.getHealth(), 100.0F, "full after the second tap"))
                .thenWaitUntil(() -> helper.assertTrue(
                        vexes.stream().allMatch(vex -> vex.getMainHandItem().is(Items.IRON_SWORD)),
                        "both hold swords again once the golem is full"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex hovers inside reach of a golem at 10 health (a stationary vex: it never drifts out of reach, so
     * its tap clock never holds), should tap four times, 25 health each (to 35, 60, 85 and 100), exactly thirty ticks
     * apart.
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private), through the goal selector: {@code TAP_INTERVAL}
     */
    static void tapsComeEveryThirtyTicks(GameTestHelper helper) {
        // Arrange: the vex is 0.4 blocks from the chest, so it taps on the tick it takes the golem
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 10.0F);
        unleashedWithoutTargets(helper, GOLEM_SPOT.above(1), player);
        List<Long> tapTicks = new ArrayList<>();
        float[] last = {golem.getHealth()};

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(150, () -> {
                    if (golem.getHealth() != last[0]) {
                        tapTicks.add(helper.getTick());
                        helper.assertValueEqual(
                                golem.getHealth(), Math.min(last[0] + 25.0F, 100.0F), "each tap heals 25");
                        last[0] = golem.getHealth();
                    }
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(tapTicks.size(), 4, "four taps take it from 10 to 100, got " + tapTicks);
                    for (int i = 1; i < tapTicks.size(); i++) {
                        helper.assertValueEqual(
                                tapTicks.get(i) - tapTicks.get(i - 1),
                                30L,
                                "tap " + (i + 1) + " comes thirty ticks after the one before, got " + tapTicks);
                    }
                })
                .thenSucceed();
    }

    /**
     * When two hurt ally golems stand eight blocks apart with one Bound Vex beside each (each golem in only its own
     * vex's guard area), should have both golems repaired. Two vexes spawned one after the other have one odd and one
     * even entity id, so a scan gate that opens only for one parity fails here (docs/api-notes.md §21, the goal clock).
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private), through {@code BoundVex.scanDue}
     */
    static void repairsTwoGolemsApart(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem east = golemOnStone(helper, FEET.east(4), 50.0F);
        IronGolem west = golemOnStone(helper, FEET.west(4), 50.0F);
        unleashed(helper, FEET.east(4).above(2), player);
        unleashed(helper, FEET.west(4).above(2), player);

        // Act & Assert
        helper.succeedWhen(() -> {
            helper.assertTrue(east.getHealth() >= 75.0F, "the east golem is repaired, got " + east.getHealth());
            helper.assertTrue(west.getHealth() >= 75.0F, "the west golem is repaired, got " + west.getHealth());
        });
    }

    /**
     * When the Bound Vex repairing a golem is removed mid-repair (as death, Peaceful or an unload removes it, with no
     * goal {@code stop()}), should leave the golem free: a second vex then repairs it to full.
     *
     * <p>Method under test: {@code RepairGolemGoal}'s claim map (package-private)
     */
    static void claimFreesWhenTheVexIsGone(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        BoundVex first = unleashed(helper, GOLEM_SPOT.above(2), player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getHealth() >= 75.0F, "setup: the first vex tapped once"))
                .thenExecute(() -> {
                    helper.assertTrue(golem.getHealth() < 100.0F, "setup: not full yet");
                    first.discard();
                    unleashed(helper, GOLEM_SPOT.above(2), player);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(golem.getHealth(), 100.0F, "the second vex finished it"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex repairing a golem takes a target after the first tap, should stop: its sword comes back and the
     * golem is free, so a second vex (with no target goals) repairs it to full while the first still has its target.
     * The husk soaks up the first vex's blows (a huge absorption amount), so the first never wins its fight and
     * finishes the golem under its own claim: only a claim released in {@code stop} lets the second vex in.
     *
     * <p>Method under test: {@code RepairGolemGoal.canContinueToUse} and {@code stop} (package-private): the claim
     * release
     */
    static void fightInterruptsARepair(GameTestHelper helper) {
        // Arrange: target goals off, so only the test decides who has a target
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 25.0F);
        BoundVex first = unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player);
        Husk husk = husk(helper, GOLEM_SPOT.east(2));
        husk.setNoGravity(true);
        husk.setAbsorptionAmount(100000.0F);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(first.getMainHandItem().is(Items.IRON_INGOT), "setup: repairing"))
                .thenExecute(() -> first.setTarget(husk))
                .thenWaitUntil(
                        () -> helper.assertTrue(first.getMainHandItem().is(Items.IRON_SWORD), "the sword is back"))
                .thenExecute(() -> {
                    float now = golem.getHealth();
                    helper.assertTrue(now < 100.0F, "setup: stopped before full, at " + now);
                    unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(golem.getHealth(), 100.0F, "the second vex finished it"))
                .thenExecute(() -> {
                    helper.assertTrue(husk.equals(first.getTarget()), "the first vex is still fighting the husk");
                    helper.assertTrue(husk.isAlive(), "setup: the husk outlasted the repair");
                })
                .thenSucceed();
    }

    /**
     * When a Bound Vex repairing a golem has a hissing creeper come within eight blocks, should stop: the flee goal
     * (priority 0) outranks the repair (2) on the move flag, so the vex's sword comes back and it does not take the
     * ingot again while the creeper hisses, though the golem is still hurt.
     *
     * <p>Method under test: {@code RepairGolemGoal.stop} (package-private), through {@link FleeCreeperGoal} taking the
     * move flag
     */
    static void fleeInterruptsARepair(GameTestHelper helper) {
        // Arrange: target goals off, so only the creeper can end the repair; the creeper stands three blocks off
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 10.0F);
        BoundVex vex = unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player);
        Creeper creeper = patientCreeper(helper, GOLEM_SPOT.above(2).north(3));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.getMainHandItem().is(Items.IRON_INGOT), "setup: repairing"))
                .thenExecute(() -> creeper.setSwellDir(1))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.getMainHandItem().is(Items.IRON_SWORD), "the sword is back once the vex flees"))
                .thenExecute(() -> {
                    float now = golem.getHealth();
                    helper.assertTrue(now < 100.0F, "setup: the golem is still hurt, at " + now);
                })
                .thenExecuteFor(
                        20,
                        () -> helper.assertTrue(
                                vex.getMainHandItem().is(Items.IRON_SWORD), "no new ingot while the creeper hisses"))
                .thenSucceed();
    }

    /**
     * When the server stops while a Bound Vex repairs a golem and a second vex comes to it, should free the claim: the
     * second vex stays off the golem while the claim stands (twelve ticks, over a scan) and takes it once the stop
     * handler has run, while the first still taps. The handler runs on its own batch ({@link #SERVER_STOP_CLAIMS}): it
     * clears every claim on the server.
     *
     * <p>Method under test: {@link BoundVexEvents#onServerStopped}, through {@code RepairGolemGoal}'s claim map
     * (package-private)
     */
    static void serverStopFreesTheRepairClaims(GameTestHelper helper) {
        // Arrange: a golem that needs four taps, so the first vex is still repairing when the second takes over
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 10.0F);
        BoundVex first = unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player);
        BoundVex[] second = new BoundVex[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        first.getMainHandItem().is(Items.IRON_INGOT), "setup: the first vex holds the claim"))
                .thenExecute(() -> second[0] = unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player))
                .thenExecuteFor(
                        12,
                        () -> helper.assertFalse(
                                second[0].getMainHandItem().is(Items.IRON_INGOT),
                                "the claim keeps the second vex off the golem"))
                .thenExecute(() -> {
                    helper.assertTrue(golem.getHealth() < 100.0F, "setup: the golem still needs repair");
                    BoundVexEvents.onServerStopped(
                            new ServerStoppedEvent(helper.getLevel().getServer()));
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        second[0].getMainHandItem().is(Items.IRON_INGOT), "the second vex takes the freed golem"))
                .thenSucceed();
    }

    /**
     * When a golem mad at the summoner stands at half health and a full-health ally golem stands beside it, both in a
     * Bound Vex's guard area, should repair neither for sixty ticks (the vex keeps its sword); once the mad golem calms
     * down, should repair it (the control).
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private): the ally and health filters
     */
    static void noRepairForAMadOrFullGolem(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem mad = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        GolemGameTests.anger(mad, player);
        IronGolem full = golemOnStone(helper, GOLEM_SPOT.south(2), 100.0F);
        BoundVex vex = unleashed(helper, GOLEM_SPOT.south(1).above(2), player);
        noTargets(vex);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(60, () -> {
                    helper.assertValueEqual(mad.getHealth(), 50.0F, "a mad golem is not repaired");
                    helper.assertValueEqual(full.getHealth(), 100.0F, "setup: the full golem stays full");
                    helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "no ingot for either golem");
                })
                .thenExecute(() -> {
                    mad.setTarget(null);
                    mad.stopBeingAngry();
                })
                .thenWaitUntil(() -> helper.assertTrue(mad.getHealth() >= 75.0F, "repaired once it calms down"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex with a target hovers within reach of a hurt ally golem's chest (the target, a husk, is within
     * two blocks of the vex, so the vex has no charge to make), should keep its sword and leave the golem's health alone
     * for forty ticks, over several scans; once the target is cleared, should repair the golem (the control).
     *
     * <p>Method under test: {@code RepairGolemGoal.canUse}: the no-target gate (package-private), through the goal
     * selector
     */
    static void fightingVexDoesNotStartARepair(GameTestHelper helper) {
        // Arrange: target goals off, so the target is the test's; the vex is 0.6 blocks above the chest
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        BoundVex vex = unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player);
        Husk husk = husk(helper, GOLEM_SPOT.west(1).above(1));
        husk.setNoGravity(true);
        Vec3 start = vex.position();
        vex.setTarget(husk);
        helper.assertTrue(husk.equals(vex.getTarget()), "setup: the vex holds its target");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(40, () -> {
                    helper.assertTrue(
                            vex.getMainHandItem().is(Items.IRON_SWORD),
                            "a fighting vex keeps its sword, got " + vex.getMainHandItem());
                    helper.assertValueEqual(golem.getHealth(), 50.0F, "a fighting vex heals nothing");
                    helper.assertTrue(
                            vex.position().distanceTo(start) < 0.5, "a fighting vex does not fly to the golem");
                })
                .thenExecute(() -> vex.setTarget(null))
                .thenWaitUntil(() -> helper.assertTrue(golem.getHealth() >= 75.0F, "repairs once it has no target"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex flies to a hurt ally golem, three blocks off and about to arrive, and the golem is healed to full
     * before the vex gets there (so the repair stops before it ever conjured the ingot), should keep the item it holds
     * (the iron sword) for forty ticks; once the golem is hurt again, should repair it with the ingot and get the sword
     * back at the end.
     *
     * <p>Method under test: {@code RepairGolemGoal.stop}: the restore guard (package-private), through the goal
     * selector
     */
    static void repairStoppedInFlightKeepsTheHeldItem(GameTestHelper helper) {
        // Arrange: the vex starts three blocks west of the golem and two up, so it has to fly
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        MockPlayers.moveTo(helper, player, new Vec3(6.5, 1, 1.5), NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        face(golem, 90.0F);
        BoundVex vex = unleashedWithoutTargets(helper, GOLEM_SPOT.west(3).above(2), player);
        Vec3 start = vex.position();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.position().distanceTo(start) > 0.3, "setup: the vex is flying to the golem"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            vex.getMainHandItem().is(Items.IRON_SWORD),
                            "setup: still the sword before the vex arrives, got " + vex.getMainHandItem());
                    golem.setHealth(golem.getMaxHealth());
                })
                .thenExecuteFor(
                        40,
                        () -> helper.assertTrue(
                                vex.getMainHandItem().is(Items.IRON_SWORD),
                                "the sword is kept after a stop before the ingot, got " + vex.getMainHandItem()))
                .thenExecute(() -> golem.setHealth(50.0F))
                .thenWaitUntil(() -> helper.assertTrue(vex.getMainHandItem().is(Items.IRON_INGOT), "repairs again"))
                .thenWaitUntil(() -> helper.assertValueEqual(golem.getHealth(), 100.0F, "the golem is full again"))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.getMainHandItem().is(Items.IRON_SWORD),
                        "the sword is back after a full repair, got " + vex.getMainHandItem()))
                .thenSucceed();
    }

    /**
     * When a golem turns mad at the summoner while a Bound Vex repairs it (hurt, under 100), should drop it: the sword
     * comes back and the golem's health stays where it was for seventy ticks (over the next scans too).
     *
     * <p>Method under test: {@code RepairGolemGoal.canContinueToUse}: the ally check (package-private), through the
     * goal selector
     */
    static void golemAngeredMidRepairIsDropped(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 10.0F);
        face(golem, 90.0F);
        BoundVex vex = unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player);
        float[] at = new float[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_INGOT), "setup: the vex is repairing"))
                .thenExecute(() -> GolemGameTests.anger(golem, player))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.getMainHandItem().is(Items.IRON_SWORD), "the sword is back once the golem is mad"))
                .thenExecute(() -> {
                    at[0] = golem.getHealth();
                    helper.assertTrue(
                            at[0] < 100.0F,
                            "dropped while the golem was still hurt, not finished at full health, got " + at[0]);
                })
                .thenExecuteFor(
                        70,
                        () -> helper.assertValueEqual(
                                golem.getHealth(), at[0], "no more taps on a golem mad at the summoner"))
                .thenSucceed();
    }

    /**
     * When a hurt ally golem stands just below a Bound Vex's guard box (four blocks under it: the box reaches 3.5, but
     * the golem's body, 2.7 tall, still touches the box a query asks for), should not be repaired for forty ticks: the
     * vex keeps its sword, stays where it is and the golem's health holds; once the vex comes down beside it, should
     * repair it (the control).
     *
     * <p>Method under test: {@code RepairGolemGoal.canUse}: the guard-box filter (package-private), through the goal
     * selector
     */
    static void noRepairOutsideTheGuardBox(GameTestHelper helper) {
        // Arrange: target goals off; the golem's feet are 4 blocks below the vex's, so outside the 3.5 box
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 50.0F);
        BoundVex vex = unleashedWithoutTargets(helper, GOLEM_SPOT.above(4), player);
        Vec3 start = vex.position();
        helper.assertFalse(vex.inGuardArea(golem), "setup: the golem's feet are outside the guard box");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(40, () -> {
                    helper.assertTrue(
                            vex.getMainHandItem().is(Items.IRON_SWORD),
                            "no ingot for a golem outside the box, got " + vex.getMainHandItem());
                    helper.assertValueEqual(golem.getHealth(), 50.0F, "a golem outside the box is not repaired");
                    helper.assertTrue(
                            vex.position().distanceTo(start) < 0.5, "the vex does not fly out to a golem outside");
                })
                .thenExecute(() -> vex.setPos(vex.getX(), golem.getY() + 2.0, vex.getZ()))
                .thenWaitUntil(() -> helper.assertTrue(golem.getHealth() >= 75.0F, "repaired once it is inside"))
                .thenSucceed();
    }

    /**
     * When a golem a Bound Vex is repairing is moved six blocks away mid-repair (out of the guard box), should end the
     * repair: the sword comes back within a few ticks and the golem is not healed again. (The vex may still fly after
     * the golem on the move it was last given and start a new repair when the golem is back in its box: only the
     * stop is pinned.)
     *
     * <p>Method under test: {@code RepairGolemGoal.canContinueToUse}: the guard-box check (package-private), through
     * the goal selector
     */
    static void golemLeavingTheGuardBoxEndsTheRepair(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 10.0F);
        BoundVex vex = unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player);
        float[] at = new float[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_INGOT), "setup: the vex is repairing"))
                .thenExecute(() -> {
                    at[0] = golem.getHealth();
                    golem.setNoGravity(true);
                    golem.setPos(golem.position().add(6.0, 0.0, 0.0));
                    helper.assertFalse(vex.inGuardArea(golem), "setup: the golem is out of the guard box");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.getMainHandItem().is(Items.IRON_SWORD), "the sword is back once the golem is out"))
                .thenExecute(() ->
                        helper.assertValueEqual(golem.getHealth(), at[0], "the golem is not healed on its way out"))
                .thenSucceed();
    }
}
