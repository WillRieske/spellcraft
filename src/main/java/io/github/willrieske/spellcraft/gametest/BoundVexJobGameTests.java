package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.boundVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.calmCreeper;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.followOnly;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.husk;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.noTargets;
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
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
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
        FangGameTests.addPadded(SHARED, "bound_vex_dodges_a_golems_swing", BoundVexJobGameTests::dodgesAGolemsSwing);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_dodge_ignores_a_swing_away_or_far",
                BoundVexJobGameTests::dodgeIgnoresASwingAwayOrFar);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_dodge_ignores_a_diagonal_swing_beyond_three_blocks",
                BoundVexJobGameTests::dodgeIgnoresADiagonalSwingBeyondThreeBlocks);
        FangGameTests.addPadded(
                SHARED, "bound_vex_repairs_a_hurt_ally_golem", BoundVexJobGameTests::repairsAHurtAllyGolem);
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
     * A {@link BoundVexTestSupport#calmCreeper} whose fuse is so long that it hisses without exploding once {@code setSwellDir(1)} is
     * called: the fuse is the {@code Fuse} short tag, written over the creeper's own save and loaded back.
     */
    private static Creeper patientCreeper(GameTestHelper helper, BlockPos relative) {
        Creeper creeper = calmCreeper(helper, relative);
        CompoundTag tag = GameTestSupport.saved(creeper);
        tag.putShort("Fuse", Short.MAX_VALUE);
        creeper.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
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

    /** {@link BoundVexTestSupport#boundVex} without its follow goal, so it stays where a repair test puts it until a job moves it. */
    private static BoundVex unleashed(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = boundVex(helper, relative, summoner);
        vex.goalSelector.removeAllGoals(goal -> goal instanceof FollowSummonerGoal);
        return vex;
    }

    /** {@link #unleashed} without its target goals, so a repair test's vex has a target only when the test gives one. */
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
     * When a creeper two blocks from a following Bound Vex, four blocks from its summoner, starts swelling, should fly
     * to eight blocks from it and hold, which puts it more than five blocks from the summoner; when the creeper calms
     * down, should come back to the summoner (the follow goal resumes). The vex has no target goals
     * ({@link BoundVexTestSupport#followOnly}): it ends up eleven blocks out, near the edge of its padding.
     *
     * <p>Method under test: {@link FleeCreeperGoal}, through the goal selector
     */
    static void fleesAHissingCreeper(GameTestHelper helper) {
        // Arrange: the creeper is between the vex and the summoner, so fleeing it means flying away from the summoner
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Creeper creeper = patientCreeper(helper, FEET.north(2));
        BoundVex vex = followOnly(helper, FEET.north(4), player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> creeper.setSwellDir(1))
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
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "back with the summoner once the creeper calms, got " + vex.distanceTo(player)))
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
     * When an ally golem at half health stands in the guard area of two idle Bound Vexes, both out of reach of its
     * chest, should have exactly one vex fly in and take it (holding an iron ingot while the other keeps its sword),
     * heal it by 25 on the first tap with the repair sound and by 25 more thirty ticks later, and give the vex its
     * sword back once the golem is full.
     *
     * <p>Method under test: {@code RepairGolemGoal} (package-private), through the goal selector
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
                .thenWaitUntil(() -> helper.assertTrue(golem.getHealth() >= 75.0F, "one tap: 25 healed"))
                .thenExecute(() -> {
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
     * golem is free, so a second vex (with no target goals) repairs it to full.
     *
     * <p>Method under test: {@code RepairGolemGoal.canContinueToUse} and {@code stop} (package-private)
     */
    static void fightInterruptsARepair(GameTestHelper helper) {
        // Arrange: target goals off, so only the test decides who has a target
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        IronGolem golem = golemOnStone(helper, GOLEM_SPOT, 25.0F);
        BoundVex first = unleashedWithoutTargets(helper, GOLEM_SPOT.above(2), player);
        Husk husk = husk(helper, GOLEM_SPOT.west(2));

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
     * handler has run, while the first still taps. The handler runs on its own batch ({@link #SERVER_STOP_CLAIMS}): it clears
     * every claim on the server.
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
}
