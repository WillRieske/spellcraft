package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.evoking.HurtBySummonerSideGoal.MEMORY_TICKS;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FAR_NORTH;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.PREY_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.VEX_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.boundVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.calmCreeper;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.husk;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.noTargets;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.parked;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.placeBeside;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bound Vex GameTests (Evoking II spec section 3, section 11 "Part 2"), targeting and wolves area: the three
 * target goals, the grudge memory, pvp and huntable rules, and the summoner's wolves. Tests whose vex flies or
 * teleports, that need headroom, or that park many vexes register through {@code FangGameTests.addPadded}
 * (padding 13); the rest run inside the shared 3x3x3 box. Shared constants and helpers live in {@link
 * BoundVexTestSupport}.
 */
public final class BoundVexTargetGameTests {
    /**
     * Ticks for a test that waits for a 10-tick target scan or watches a 40-tick window (not
     * {@code FangGameTests}' 200-tick {@code LONG}).
     */
    private static final int TARGETING_TICKS = 100;

    /** The window over which "never a target" is watched: four target scans. */
    private static final int TARGET_WINDOW = 40;

    /** The box's bottom north-east corner, a second spot inside the box for an ally or a second mob. */
    private static final BlockPos SIDE_SPOT = new BlockPos(2, 1, 0);

    /** The box's bottom south-west corner, a third spot inside the box. */
    private static final BlockPos CORNER_SPOT = new BlockPos(0, 1, 2);

    private BoundVexTargetGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "bound_vex_three_target_goals", BoundVexTargetGameTests::threeTargetGoals);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_attacks_a_hostile_in_the_area",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::attacksAHostile);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_pig_but_not_a_husk",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::ignoresAPigButNotAHusk);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_creeper_until_provoked",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::ignoresACreeperUntilProvoked);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_targets_what_hurt_the_summoner",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::targetsWhatHurtTheSummoner);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_targets_what_the_summoner_hit",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::targetsWhatTheSummonerHit);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_targets_what_hurt_an_ally",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::targetsWhatHurtAnAlly);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_joins_an_allys_fight",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::joinsAnAllysFight);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ally_goal_skips_a_creeper",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::allyGoalSkipsACreeper);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ally_goal_skips_a_pig",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::allyGoalSkipsAPig);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_patrol_ignores_another_players_vex",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::patrolIgnoresAnotherPlayersVex);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_answers_another_players_vex_that_hurt_the_summoner",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::answersAnotherPlayersVexThatHurtTheSummoner);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_calls_the_summoners_wolves",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::callsTheSummonersWolves);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_wolf_call_skips_the_wrong_wolves",
                BoundVexTargetGameTests::wolfCallSkipsTheWrongWolves);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_wolf_call_only_on_a_new_target", BoundVexTargetGameTests::wolfCallOnlyOnANewTarget);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_wolf_call_skips_a_creeper", BoundVexTargetGameTests::wolfCallSkipsACreeper);
        FangGameTests.addPadded(
                SHARED, "bound_vex_drops_a_target_out_of_reach", BoundVexTargetGameTests::dropsATargetOutOfReach);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_drops_a_grudge_target_out_of_reach",
                BoundVexTargetGameTests::dropsAGrudgeTargetOutOfReach);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_drops_an_ally_fight_target_out_of_reach",
                BoundVexTargetGameTests::dropsAnAllyFightTargetOutOfReach);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_drops_a_target_that_becomes_an_ally",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::dropsANewAlly);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_no_summoner_no_target",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::noSummonerNoTarget);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_held_by_the_patrol_goal",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::heldByThePatrolGoal);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_stale_memory",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::ignoresAStaleMemory);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_own_attacker_comes_first",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::ownAttackerComesFirst);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_a_creative_attacker_does_not_block",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::creativeAttackerDoesNotBlock);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_ignores_an_ally_outside_the_area",
                BoundVexTargetGameTests::ignoresAnAllyOutsideTheArea);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_ignores_an_ally_whose_box_only_touches_the_area",
                BoundVexTargetGameTests::ignoresAnAllyWhoseBoxOnlyTouchesTheArea);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_ignores_a_hostile_whose_box_only_touches_the_area",
                BoundVexTargetGameTests::ignoresAHostileWhoseBoxOnlyTouchesTheArea);
        FangGameTests.addPadded(
                SHARED, "bound_vex_patrol_needs_line_of_sight", BoundVexTargetGameTests::patrolNeedsLineOfSight);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_patrol_drops_a_husk_it_cannot_see",
                BoundVexTargetGameTests::patrolDropsAHuskItCannotSee);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_patrol_drops_a_husk_that_becomes_an_ally",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::patrolDropsAHuskThatBecomesAnAlly);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ally_goal_drops_a_husk_that_becomes_an_ally",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::allyGoalDropsAHuskThatBecomesAnAlly);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_summoners_victim_comes_before_an_allys_attacker",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::summonersVictimComesBeforeAnAllysAttacker);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_stale_attacker_of_the_summoner",
                TARGETING_TICKS,
                0,
                BoundVexTargetGameTests::ignoresAStaleAttackerOfTheSummoner);
    }

    /**
     * Takes the patrol goal away from {@code vex} (the target goal that searches for hostiles), so a hostile in its
     * area is taken only through a memory or an ally.
     *
     * @param vex the vex
     * @return {@code vex}
     */
    static BoundVex withoutPatrol(BoundVex vex) {
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);
        return vex;
    }

    /**
     * Fills the three-high column of {@code block} on top of {@code base}, a wall a vex cannot see through.
     *
     * @param helper the test helper
     * @param base the lowest block of the wall, relative
     * @param block the block to fill it with ({@code Blocks.AIR} takes the wall down)
     */
    private static void wall(GameTestHelper helper, BlockPos base, Block block) {
        for (int up = 0; up < 3; up++) {
            helper.setBlock(base.above(up), block);
        }
    }

    /** An untamed wolf with no AI, discarded when the test ends. */
    private static Wolf untamedWolf(GameTestHelper helper, BlockPos relative) {
        Wolf wolf = helper.spawn(EntityTypes.WOLF, relative);
        wolf.setNoAi(true);
        whenDone(helper, wolf::discard);
        return wolf;
    }

    /** A tamed wolf of {@code owner} with no AI at {@code relative}, discarded when the test ends. */
    private static Wolf pet(GameTestHelper helper, BlockPos relative, ServerPlayer owner) {
        Wolf wolf = helper.spawn(EntityTypes.WOLF, relative);
        wolf.setNoAi(true);
        wolf.tame(owner);
        whenDone(helper, wolf::discard);
        return wolf;
    }

    /** A pig with no AI at {@code relative}, discarded when the test ends. */
    private static Pig pigHere(GameTestHelper helper, BlockPos relative) {
        Pig pig = FangGameTests.pig(helper, relative);
        whenDone(helper, pig::discard);
        return pig;
    }

    /** Whether {@code mob}'s target is {@code expected} (by UUID). */
    private static boolean targets(Mob mob, LivingEntity expected) {
        LivingEntity target = mob.getTarget();
        return target != null && target.getUUID().equals(expected.getUUID());
    }

    /** The simple class name of the vex's running target goal, or {@code "none"}. */
    private static String runningTargetGoal(BoundVex vex) {
        return vex.targetSelector.getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning)
                .map(wrapped -> wrapped.getGoal().getClass().getSimpleName())
                .findFirst()
                .orElse("none");
    }

    /**
     * When a Bound Vex is made, should carry exactly the three target goals of spec §3.4, in its order: the grudge goal
     * at priority 1, the ally goal at 2, the patrol goal at 3.
     *
     * <p>Method under test: {@code BoundVex.registerGoals} (protected)
     */
    static void threeTargetGoals(GameTestHelper helper) {
        // Arrange
        BoundVex vex = boundVex(helper, VEX_SPOT, summoner(helper));

        // Act
        Map<Integer, String> byPriority = new TreeMap<>();
        vex.targetSelector
                .getAvailableGoals()
                .forEach(wrapped -> byPriority.put(
                        wrapped.getPriority(), wrapped.getGoal().getClass().getSimpleName()));

        // Assert
        helper.assertValueEqual(
                byPriority,
                Map.of(1, "HurtBySummonerSideGoal", 2, "AllyFightGoal", 3, "NearestHostileGoal"),
                "the three target goals, in order");
        helper.succeed();
    }

    /**
     * When a husk stands inside a parked Bound Vex's guard area with the summoner nearer than the husk, should take the
     * husk as its target and never the summoner.
     *
     * <p>Method under test: {@code NearestHostileGoal} (package-private), through the target selector
     */
    static void attacksAHostile(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);

        // Act
        Husk husk = husk(helper, PREY_SPOT);

        // Assert: the first target it takes is the husk, and the summoner is never one afterwards
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() != null, "a target is taken"))
                .thenExecute(
                        () -> helper.assertTrue(targets(vex, husk), "the husk, not the nearer summoner, is the target"))
                .thenExecuteFor(20, () -> helper.assertFalse(targets(vex, player), "the summoner is never the target"))
                .thenSucceed();
    }

    /**
     * When only a pig stands in the guard area, should take no target over forty ticks; a husk added afterwards should
     * become the target (the control: the vex and its line of sight work here).
     *
     * <p>Method under test: {@code NearestHostileGoal} (package-private): its {@code Enemy} filter
     */
    static void ignoresAPigButNotAHusk(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        pigHere(helper, PREY_SPOT);
        Husk[] husk = new Husk[1];

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "a pig is never a target"))
                .thenExecute(() -> husk[0] = husk(helper, SIDE_SPOT))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk[0]), "the husk is the target"))
                .thenSucceed();
    }

    /**
     * When a calm creeper stands in the guard area, should take no target over forty ticks; once the vex remembers the
     * creeper hurting it, should take it as its target. (The memory is set directly: a real hit would knock the parked
     * vex out of the box and past reach.)
     *
     * <p>Method under test: {@code NearestHostileGoal}'s creeper exclusion and {@code HurtBySummonerSideGoal}'s
     * own-hurt memory (both package-private)
     */
    static void ignoresACreeperUntilProvoked(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Creeper creeper = calmCreeper(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "a calm creeper is never a target"))
                .thenExecute(() -> vex.setLastHurtByMob(creeper))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, creeper), "the creeper that hurt it is the target"))
                .thenSucceed();
    }

    /**
     * When a husk hurts the summoner, should take the husk as its target.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the summoner's attacker
     */
    static void targetsWhatHurtTheSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        // The husk is a hostile in the area too, so block the patrol goal's view of it: the memory must do the work.
        withoutPatrol(vex);

        // Act
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(husk), 1.0F);

        // Assert
        helper.assertTrue(
                player.getLastHurtByMob() != null
                        && player.getLastHurtByMob().getUUID().equals(husk.getUUID()),
                "setup: the summoner remembers the husk");
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, husk), "the summoner's attacker is the target"));
    }

    /**
     * When the summoner hits a pig (what {@code Player.attack} records as the last hurt mob), should take the pig as
     * its target even though a pig is not hostile.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the summoner's victim
     */
    static void targetsWhatTheSummonerHit(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Pig pig = pigHere(helper, PREY_SPOT);

        // Act
        player.setLastHurtMob(pig);

        // Assert
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, pig), "the pig the summoner hit is the target"));
    }

    /**
     * When a husk hurts the summoner's tamed wolf inside the guard area, should take the husk as its target.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): an ally's attacker
     */
    static void targetsWhatHurtAnAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf wolf = pet(helper, SIDE_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        withoutPatrol(vex);

        // Act
        wolf.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(husk), 1.0F);

        // Assert
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, husk), "the ally's attacker is the target"));
    }

    /**
     * When the summoner's tamed wolf inside the guard area has a husk as its target, should take that husk too.
     *
     * <p>Method under test: {@code AllyFightGoal} (package-private)
     */
    static void joinsAnAllysFight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf wolf = pet(helper, SIDE_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        withoutPatrol(vex);

        // Act
        wolf.setTarget(husk);

        // Assert
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, husk), "the ally's target is the target"));
    }

    /**
     * When the summoner's tamed wolf inside the guard area has a creeper as its target, should not join it over forty
     * ticks (the ally goal, like the patrol goal, never pulls a vex onto a creeper); a husk the wolf targets next
     * should be joined (the control: the goal works here).
     *
     * <p>Method under test: {@code BoundVex.huntable}, through {@code AllyFightGoal} (package-private)
     */
    static void allyGoalSkipsACreeper(GameTestHelper helper) {
        allyGoalSkips(helper, calmCreeper(helper, PREY_SPOT));
    }

    /**
     * When the summoner's tamed wolf inside the guard area has a pig as its target, should not join it over forty
     * ticks (the ally goal joins hostile targets only: "if prey and hostile", spec §3.4 item 2); a husk the wolf
     * targets next should be joined (the control: the goal works here).
     *
     * <p>Method under test: {@code BoundVex.huntable}'s {@code Enemy} test, through {@code AllyFightGoal}
     * (package-private)
     */
    static void allyGoalSkipsAPig(GameTestHelper helper) {
        allyGoalSkips(helper, pigHere(helper, PREY_SPOT));
    }

    /**
     * The body of the two "ally goal skips" tests: a parked vex with the patrol goal off, an ally wolf that targets
     * {@code skipped} for forty ticks (the vex never takes it), then a husk the wolf targets (the vex joins).
     */
    private static void allyGoalSkips(GameTestHelper helper, LivingEntity skipped) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = withoutPatrol(parked(helper, VEX_SPOT, player));
        Wolf wolf = pet(helper, SIDE_SPOT, player);
        Husk[] husk = new Husk[1];

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> {
                    wolf.setTarget(skipped);
                    helper.assertTrue(
                            vex.getTarget() == null,
                            "an ally's fight with a " + skipped.getType().toShortString() + " is not joined");
                })
                .thenExecute(() -> {
                    husk[0] = husk(helper, CORNER_SPOT);
                    wolf.setTarget(husk[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk[0]), "the husk the wolf fights is joined"))
                .thenSucceed();
    }

    /**
     * When another player's parked Bound Vex stands in a patrolling vex's guard area with {@code pvp} on, should not
     * take it as a target over forty ticks (a player is no {@code Enemy}, and another player's summons are treated like
     * that player); once that vex has hurt the patrolling one, should take it (the grudge goal answers a hit).
     *
     * <p>Method under test: {@code BoundVex.huntable}, through {@code NearestHostileGoal} (package-private); the
     * {@code pvp} rule is the server's default, which the setup asserts
     */
    static void patrolIgnoresAnotherPlayersVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        BoundVex strangersVex = strangersVexInTheBox(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "another player's vex is not patrolled"))
                .thenExecute(() -> vex.setLastHurtByMob(strangersVex))
                .thenWaitUntil(
                        () -> helper.assertTrue(targets(vex, strangersVex), "the vex that hurt it is the target"))
                .thenSucceed();
    }

    /**
     * When another player's parked Bound Vex stands in a patrolling vex's guard area with {@code pvp} on and hurts the
     * patrolling vex's summoner (not the vex itself), should not take it as a target over forty ticks; once the
     * summoner remembers that vex as its attacker, should take it (the grudge goal answers a hit on the summoner).
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the summoner's attacker, where the
     * attacker carries another player's mark and {@link BoundVex#isPrey(ServerLevel, UUID, LivingEntity)} allows it
     */
    static void answersAnotherPlayersVexThatHurtTheSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        BoundVex strangersVex = strangersVexInTheBox(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "another player's vex is not patrolled"))
                .thenExecute(() -> player.setLastHurtByMob(strangersVex))
                .thenWaitUntil(() ->
                        helper.assertTrue(targets(vex, strangersVex), "the vex that hurt the summoner is the target"))
                .thenSucceed();
    }

    /**
     * Another player's Bound Vex, parked at {@link BoundVexTestSupport#PREY_SPOT} (inside the guard box of a vex parked
     * at {@link BoundVexTestSupport#VEX_SPOT}) with no target goals of its own, after asserting that {@code pvp} is on
     * and so the vex of {@code player} may take it as prey.
     */
    private static BoundVex strangersVexInTheBox(GameTestHelper helper, ServerPlayer player) {
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BoundVex strangersVex = noTargets(parked(helper, PREY_SPOT, stranger));
        helper.assertTrue(helper.getLevel().isPvpAllowed(), "setup: pvp is on");
        helper.assertTrue(
                BoundVex.isPrey(helper.getLevel(), player.getUUID(), strangersVex),
                "setup: with pvp on, a stranger's vex is prey");
        return strangersVex;
    }

    /**
     * When a Bound Vex takes a target through its goals, should give it to the summoner's tamed, idle wolf within
     * sixteen blocks, and not to a sitting one.
     *
     * <p>Method under test: {@link BoundVex#setTarget}, through the target selector
     */
    static void callsTheSummonersWolves(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf idle = pet(helper, SIDE_SPOT, player);
        Wolf sitting = pet(helper, CORNER_SPOT, player);
        sitting.setOrderedToSit(true);

        // Act
        Husk husk = husk(helper, PREY_SPOT);

        // Assert
        helper.succeedWhen(() -> {
            helper.assertTrue(targets(vex, husk), "setup: the vex took the husk");
            helper.assertTrue(targets(idle, husk), "the idle wolf joins");
            helper.assertTrue(sitting.getTarget() == null, "the sitting wolf stays put");
        });
    }

    /**
     * When a Bound Vex takes a husk, should call only the summoner's tamed, idle, un-sitting wolf within sixteen
     * blocks: not an untamed wolf, another player's tamed wolf, a wolf already fighting, a sitting wolf, or the
     * summoner's wolf about seventeen blocks away (inside the cube a box query covers, outside the sixteen-block
     * sphere).
     *
     * <p>Method under test: {@link BoundVex#setTarget}
     */
    static void wolfCallSkipsTheWrongWolves(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerPlayer stranger = summoner(helper);
        BoundVex vex = parked(helper, FEET, player);
        noTargets(vex);
        Husk husk = husk(helper, PREY_SPOT);
        Pig pig = pigHere(helper, SIDE_SPOT);
        Wolf idle = pet(helper, FEET.east(2), player);
        Wolf untamed = untamedWolf(helper, FEET.east(3));
        Wolf strangers = pet(helper, FEET.west(2), stranger);
        Wolf busy = pet(helper, FEET.west(3), player);
        busy.setTarget(pig);
        Wolf sitting = pet(helper, FEET.south(2), player);
        sitting.setOrderedToSit(true);
        Wolf far = pet(helper, FEET.offset(-12, 0, -12), player);
        helper.assertTrue(far.distanceTo(vex) > 16.0, "setup: the far wolf is past sixteen blocks");

        // Act
        vex.setTarget(husk);

        // Assert
        helper.assertTrue(targets(idle, husk), "the summoner's idle wolf joins (the control)");
        helper.assertTrue(untamed.getTarget() == null, "an untamed wolf is not called");
        helper.assertTrue(strangers.getTarget() == null, "another player's wolf is not called");
        helper.assertTrue(targets(busy, pig), "a wolf already fighting keeps its own target");
        helper.assertTrue(sitting.getTarget() == null, "a sitting wolf is not called");
        helper.assertTrue(far.getTarget() == null, "a wolf past sixteen blocks is not called");
        helper.succeed();
    }

    /**
     * When a Bound Vex re-sets the target it already holds (as {@code TargetGoal.canContinueToUse} does on every full
     * tick), should not call a wolf that has gone idle; clearing the target should call nobody either.
     *
     * <p>Method under test: {@link BoundVex#setTarget}
     */
    static void wolfCallOnlyOnANewTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        noTargets(vex);
        Husk husk = husk(helper, PREY_SPOT);
        Wolf wolf = pet(helper, SIDE_SPOT, player);
        vex.setTarget(husk);
        helper.assertTrue(targets(wolf, husk), "setup: the new target called the wolf");
        wolf.setTarget(null);

        // Act
        vex.setTarget(husk);

        // Assert
        helper.assertTrue(wolf.getTarget() == null, "the same target again calls nobody");
        vex.setTarget(null);
        helper.assertTrue(wolf.getTarget() == null, "clearing the target calls nobody");
        helper.succeed();
    }

    /**
     * When a Bound Vex takes a creeper (one that provoked it), should not call the summoner's wolf into the blast
     * ({@code Wolf.wantsToAttack}); a husk taken next should call it (the control).
     *
     * <p>Method under test: {@link BoundVex#setTarget}
     */
    static void wolfCallSkipsACreeper(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        noTargets(vex);
        Creeper creeper = calmCreeper(helper, PREY_SPOT);
        Wolf wolf = pet(helper, SIDE_SPOT, player);

        // Act
        vex.setTarget(creeper);

        // Assert
        helper.assertTrue(targets(vex, creeper), "setup: the vex holds the creeper");
        helper.assertTrue(wolf.getTarget() == null, "no wolf is sent at a creeper");
        Husk husk = husk(helper, CORNER_SPOT);
        vex.setTarget(husk);
        helper.assertTrue(targets(wolf, husk), "a husk calls the wolf");
        helper.succeed();
    }

    /**
     * When a parked Bound Vex's husk target, held by the patrol goal, is moved past the 7.25 reach and out of the guard
     * box, should drop it within a few ticks and not take it again. The patrol goal is also bound by vanilla's follow
     * range (the same 7.25), so this test cannot tell {@code keepsTarget}'s reach clause from vanilla's; the grudge and
     * ally goals, which never call {@code super}, are tested below.
     *
     * <p>Method under test: {@code NearestHostileGoal.canContinueToUse} (package-private), through the target selector
     */
    static void dropsATargetOutOfReach(GameTestHelper helper) {
        // Arrange: both outside the shared box, so the barrier shell is not in the way
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(5), player);
        Husk husk = husk(helper, FEET.north(7));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the husk is the target"))
                .thenExecute(() -> FangGameTests.moveTo(helper, husk, FEET, 0, -FAR_NORTH))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "dropped once out of reach"))
                .thenExecuteFor(20, () -> helper.assertTrue(vex.getTarget() == null, "and not taken again"))
                .thenSucceed();
    }

    /**
     * When a parked Bound Vex holds a husk through the grudge goal (the vex remembers it hurting it, six blocks away,
     * the patrol goal off) and the husk is then moved eight blocks away, past the 7.25 reach, should drop it within a
     * few ticks and not take it again, though the memory is still fresh. The grudge goal never calls {@code super}, so
     * {@code keepsTarget}'s reach clause is the only distance bound it has.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code HurtBySummonerSideGoal.canContinueToUse}
     */
    static void dropsAGrudgeTargetOutOfReach(GameTestHelper helper) {
        // Arrange: both outside the shared box, so the barrier shell is not in the way
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = withoutPatrol(parked(helper, FEET.north(5), player));
        Husk husk = husk(helper, FEET.north(11));
        husk.setNoGravity(true);
        vex.setLastHurtByMob(husk);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(targets(vex, husk), "setup: the husk is the target");
                    helper.assertValueEqual(
                            runningTargetGoal(vex), "HurtBySummonerSideGoal", "held by the grudge goal");
                })
                .thenExecute(() -> FangGameTests.moveTo(helper, husk, FEET, 0, -FAR_NORTH))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "dropped once out of reach"))
                .thenExecuteFor(20, () -> helper.assertTrue(vex.getTarget() == null, "and not taken again"))
                .thenSucceed();
    }

    /**
     * When a parked Bound Vex holds a husk through the ally goal (an ally wolf in its guard area targets the husk, six
     * blocks from the vex, the patrol goal off) and the husk is then moved eight blocks away, past the 7.25 reach,
     * should drop it within a few ticks and not take it again, though the wolf still targets it. The ally goal never
     * calls {@code super}, so {@code keepsTarget}'s reach clause is the only distance bound it has.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code AllyFightGoal.canContinueToUse}
     */
    static void dropsAnAllyFightTargetOutOfReach(GameTestHelper helper) {
        // Arrange: both outside the shared box, so the barrier shell is not in the way
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = withoutPatrol(parked(helper, FEET.north(5), player));
        Wolf wolf = pet(helper, FEET.north(5).east(1), player);
        wolf.setNoGravity(true);
        Husk husk = husk(helper, FEET.north(11));
        husk.setNoGravity(true);
        wolf.setTarget(husk);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(targets(vex, husk), "setup: the husk is the target");
                    helper.assertValueEqual(runningTargetGoal(vex), "AllyFightGoal", "held by the ally goal");
                })
                .thenExecute(() -> FangGameTests.moveTo(helper, husk, FEET, 0, -FAR_NORTH))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "dropped once out of reach"))
                .thenExecuteFor(20, () -> helper.assertTrue(vex.getTarget() == null, "and not taken again"))
                .thenSucceed();
    }

    /**
     * When a parked Bound Vex has an untamed wolf as its target (the summoner hit it) and the summoner then tames it,
     * should drop the target within a few ticks.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code HurtBySummonerSideGoal.canContinueToUse}
     */
    static void dropsANewAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf wolf = untamedWolf(helper, PREY_SPOT);
        player.setLastHurtMob(wolf);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, wolf), "setup: the untamed wolf is the target"))
                .thenExecute(() -> wolf.tame(player))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "the tamed wolf is dropped"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex's summoner mark is taken away, should take no target over forty ticks with a husk beside it;
     * once the mark is back, should take the husk (the control: the vex ticks and sees the husk).
     *
     * <p>Method under test: {@link BoundVex#isPrey(LivingEntity)}, through every target goal
     */
    static void noSummonerNoTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.removeData(AllyAttachments.SUMMONER);
        Husk husk = husk(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "nothing without a summoner"))
                .thenExecute(() -> vex.setData(AllyAttachments.SUMMONER, player.getUUID()))
                .thenWaitUntil(
                        () -> helper.assertTrue(targets(vex, husk), "with the mark back, the husk is the target"))
                .thenSucceed();
    }

    /**
     * When a parked Bound Vex takes a husk through the patrol goal with no ally near, should still hold it through the
     * patrol goal two scans later: the ally goal does not count the vex itself as an ally and take over.
     *
     * <p>Method under test: {@code AllyFightGoal} and {@code BoundVex.alliesInGuardArea} (package-private)
     */
    static void heldByThePatrolGoal(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the husk is the target"))
                .thenIdle(20) // two scans of the ally goal
                .thenExecute(() -> {
                    helper.assertTrue(targets(vex, husk), "still the husk");
                    helper.assertValueEqual(runningTargetGoal(vex), "NearestHostileGoal", "held by the patrol goal");
                })
                .thenSucceed();
    }

    /**
     * When the summoner hit a pig 101 of its own ticks ago, should take no target over forty ticks; with the clock one
     * tick earlier (the hit exactly {@code MEMORY_TICKS} old, the last fresh age) should make the pig the target: the
     * limit is "older than 100", not "100 or older".
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the freshness window
     */
    static void ignoresAStaleMemory(GameTestHelper helper) {
        // Arrange: the mock summoner's clock advances by one each tick, so the test sets it back at the end of every
        // tick; the summoner ticks before the vex, so the vex's scan sees the clock one tick on
        ServerPlayer player = summoner(helper);
        BoundVex vex = withoutPatrol(parked(helper, VEX_SPOT, player));
        Pig pig = pigHere(helper, PREY_SPOT);
        player.setLastHurtMob(pig);
        int hit = player.getLastHurtMobTimestamp();
        player.tickCount = hit + MEMORY_TICKS;

        // Act & Assert: the scan sees an age of 101, then of 100
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> {
                    player.tickCount = hit + MEMORY_TICKS;
                    helper.assertTrue(vex.getTarget() == null, "a hit of 101 ticks is forgotten");
                })
                .thenWaitUntil(() -> {
                    player.tickCount = hit + MEMORY_TICKS - 1;
                    helper.assertTrue(targets(vex, pig), "a hit of exactly 100 ticks is still fresh");
                })
                .thenSucceed();
    }

    /**
     * When the vex remembers one husk hurting it and the summoner remembers another, both fresh, should take the vex's
     * own attacker: the first memory in the spec's order wins.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the memory order
     */
    static void ownAttackerComesFirst(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        withoutPatrol(vex);
        Husk own = husk(helper, PREY_SPOT);
        Husk summoners = husk(helper, SIDE_SPOT);

        // Act
        vex.setLastHurtByMob(own);
        player.setLastHurtByMob(summoners);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() != null, "setup: a target is taken"))
                .thenExecute(() -> helper.assertTrue(targets(vex, own), "the vex's own attacker, not the summoner's"))
                .thenSucceed();
    }

    /**
     * When the summoner's remembered attacker is a creative-mode player (whom a mob cannot target), should keep a husk
     * taken through the patrol goal through every scan: the memory is passed over ({@code keepsTarget}'s
     * {@code canAttack}), not taken and dropped each scan.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code HurtBySummonerSideGoal.canUse}
     */
    static void creativeAttackerDoesNotBlock(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        ServerPlayer creative = MockPlayers.inLevel(helper, GameType.CREATIVE);
        MockPlayers.moveIntoTest(helper, creative);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        player.setLastHurtByMob(creative);
        Husk husk = husk(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the husk is the target"))
                .thenExecuteFor(30, () -> helper.assertTrue(targets(vex, husk), "the husk is kept through three scans"))
                .thenSucceed();
    }

    /**
     * When the summoner's tamed wolf stands outside the guard area, targeting a husk within reach and remembering it as
     * its attacker, should take no target over forty ticks; once the wolf is inside the area, should take the husk.
     *
     * <p>Method under test: {@code BoundVex.alliesInGuardArea}, through {@code HurtBySummonerSideGoal} and
     * {@code AllyFightGoal}
     */
    static void ignoresAnAllyOutsideTheArea(GameTestHelper helper) {
        // Arrange: the husk is seven blocks north of the vex (within reach, outside the box), the wolf six
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET, player);
        Husk husk = husk(helper, FEET.north(7));
        Wolf wolf = pet(helper, FEET.north(6), player);
        wolf.setTarget(husk);
        wolf.setLastHurtByMob(husk);
        helper.assertFalse(vex.inGuardArea(wolf), "setup: the wolf is outside the area");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "an ally outside the area is ignored"))
                .thenExecute(() -> wolf.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(SIDE_SPOT))))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "inside the area, its fight is joined"))
                .thenSucceed();
    }

    /**
     * Whether the broad-phase box of a guard-area query around {@code vex} (the box inflated by the half sizes)
     * intersects {@code other}'s bounding box: what a query catches before the feet test runs.
     */
    private static boolean broadPhaseCatches(BoundVex vex, Entity other) {
        return new AABB(vex.position(), vex.position())
                .inflate(GuardArea.HALF_WIDTH, GuardArea.HALF_HEIGHT, GuardArea.HALF_WIDTH)
                .intersects(other.getBoundingBox());
    }

    /**
     * When the summoner's tamed wolf stands 4.2 blocks east of the vex (its bounding box reaches into the guard box,
     * its feet are outside), targeting a husk it remembers as its attacker, should take no target over forty ticks;
     * with the wolf 3.9 blocks east, should take the husk.
     *
     * <p>Method under test: {@code BoundVex.alliesInGuardArea} (package-private): the feet test after the broad phase
     */
    static void ignoresAnAllyWhoseBoxOnlyTouchesTheArea(GameTestHelper helper) {
        // Arrange: the husk is five blocks north (within reach, outside the box)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET, player);
        Husk husk = husk(helper, FEET.north(5));
        husk.setNoGravity(true);
        Wolf wolf = pet(helper, FEET, player);
        placeBeside(vex, wolf, 4.2, 0);
        wolf.setTarget(husk);
        wolf.setLastHurtByMob(husk);
        helper.assertFalse(vex.inGuardArea(wolf), "setup: the wolf's feet are outside the area");
        helper.assertTrue(broadPhaseCatches(vex, wolf), "setup: the wolf's box touches the query box");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "an ally by the edge is ignored"))
                .thenExecute(() -> placeBeside(vex, wolf, 3.9, 0))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "inside the area, its fight is joined"))
                .thenSucceed();
    }

    /**
     * When a husk stands 4.2 blocks north of the vex (its bounding box reaches into the guard box, its feet are
     * outside), should take no target over forty ticks; with the husk 3.9 blocks north, should take it.
     *
     * <p>Method under test: {@code NearestHostileGoal} (package-private): the feet test in its selector
     */
    static void ignoresAHostileWhoseBoxOnlyTouchesTheArea(GameTestHelper helper) {
        // Arrange: the vex is outside the structure's barrier shell, which would block its sight
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(6), player);
        Husk husk = husk(helper, FEET);
        placeBeside(vex, husk, 0, -4.2);
        helper.assertFalse(vex.inGuardArea(husk), "setup: the husk's feet are outside the area");
        helper.assertTrue(broadPhaseCatches(vex, husk), "setup: the husk's box touches the query box");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "a hostile by the edge is ignored"))
                .thenExecute(() -> placeBeside(vex, husk, 0, -3.9))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "inside the area, it is taken"))
                .thenSucceed();
    }

    /**
     * When a husk stands inside the guard area behind a wall of stone, should take no target over forty ticks; with
     * the wall gone, should take the husk.
     *
     * <p>Method under test: {@code NearestHostileGoal} (package-private): its line-of-sight requirement
     */
    static void patrolNeedsLineOfSight(GameTestHelper helper) {
        // Arrange: outside the barrier shell, a three-high wall between the vex and a husk three blocks north of it
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(6), player);
        Husk husk = husk(helper, FEET.north(9));
        husk.setNoGravity(true);
        BlockPos wall = FEET.north(8);
        wall(helper, wall, Blocks.STONE);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "a husk behind stone is unseen"))
                .thenExecute(() -> wall(helper, wall, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "with the wall gone, the husk is taken"))
                .thenSucceed();
    }

    /**
     * When the husk the patrol goal holds is walled off with stone, should drop it once it has been unseen for the
     * goal's unseen-memory ticks ({@code mustSee}), and not take it again.
     *
     * <p>Method under test: {@code NearestHostileGoal} (package-private): the sight rule of {@code canContinueToUse}
     */
    static void patrolDropsAHuskItCannotSee(GameTestHelper helper) {
        // Arrange: outside the barrier shell, a husk three blocks north of the vex, in sight
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, FEET.north(6), player);
        Husk husk = husk(helper, FEET.north(9));
        husk.setNoGravity(true);
        BlockPos wall = FEET.north(8);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the husk is the target"))
                .thenExecute(() -> wall(helper, wall, Blocks.STONE))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "dropped once unseen for long enough"))
                .thenExecuteFor(20, () -> helper.assertTrue(vex.getTarget() == null, "and not taken again"))
                .thenSucceed();
    }

    /**
     * When the husk the patrol goal holds becomes an ally of the summoner (the summoner mark; {@code TargetGoal}'s
     * own checks do not catch it), should drop it.
     *
     * <p>Method under test: {@code NearestHostileGoal} (package-private): {@code canContinueToUse}
     */
    static void patrolDropsAHuskThatBecomesAnAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(targets(vex, husk), "setup: the husk is the target");
                    helper.assertValueEqual(runningTargetGoal(vex), "NearestHostileGoal", "held by the patrol goal");
                })
                .thenExecute(() -> husk.setData(AllyAttachments.SUMMONER, player.getUUID()))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "the new ally is dropped"))
                .thenExecuteFor(20, () -> helper.assertTrue(vex.getTarget() == null, "and not taken again"))
                .thenSucceed();
    }

    /**
     * When the husk the ally goal holds (a wolf's target) becomes an ally of the summoner, should drop it.
     *
     * <p>Method under test: {@code AllyFightGoal} (package-private): {@code canContinueToUse}
     */
    static void allyGoalDropsAHuskThatBecomesAnAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        withoutPatrol(vex);
        Wolf wolf = pet(helper, SIDE_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        wolf.setTarget(husk);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(targets(vex, husk), "setup: the husk is the target");
                    helper.assertValueEqual(runningTargetGoal(vex), "AllyFightGoal", "held by the ally goal");
                })
                .thenExecute(() -> husk.setData(AllyAttachments.SUMMONER, player.getUUID()))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "the new ally is dropped"))
                .thenExecuteFor(20, () -> helper.assertTrue(vex.getTarget() == null, "and not taken again"))
                .thenSucceed();
    }

    /**
     * When the summoner remembers hitting a pig and an ally wolf remembers a husk hurting it, both fresh, should take
     * the pig: the summoner's victim (third) comes before an ally's attacker (fourth).
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the memory order
     */
    static void summonersVictimComesBeforeAnAllysAttacker(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        withoutPatrol(vex);
        Pig pig = pigHere(helper, PREY_SPOT);
        Husk husk = husk(helper, SIDE_SPOT);
        Wolf wolf = pet(helper, CORNER_SPOT, player);

        // Act
        wolf.setLastHurtByMob(husk);
        player.setLastHurtMob(pig);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() != null, "setup: a target is taken"))
                .thenExecute(
                        () -> helper.assertTrue(targets(vex, pig), "the summoner's victim, not the ally's attacker"))
                .thenSucceed();
    }

    /**
     * When the summoner's attacker was remembered 101 of the summoner's ticks ago, should take no target over forty
     * ticks; with the clock one tick earlier (exactly {@code MEMORY_TICKS} old, the last fresh age) should make the
     * husk the target. The summoner's memory is the one a test can age: a vex's own and an ally's are cleared by
     * vanilla at that age.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the freshness window
     */
    static void ignoresAStaleAttackerOfTheSummoner(GameTestHelper helper) {
        // Arrange: the mock summoner's clock advances by one each tick, so the test sets it back at the end of every
        // tick; the summoner ticks before the vex, so the vex's scan sees the clock one tick on
        ServerPlayer player = summoner(helper);
        BoundVex vex = withoutPatrol(parked(helper, VEX_SPOT, player));
        Husk husk = husk(helper, PREY_SPOT);
        player.setLastHurtByMob(husk);
        int hit = player.getLastHurtByMobTimestamp();
        player.tickCount = hit + MEMORY_TICKS;

        // Act & Assert: the scan sees an age of 101 (a stale attacker is forgotten); then the attacker is remembered
        // afresh, and the scan sees an age of 100
        int[] second = new int[1];
        helper.startSequence()
                .thenExecuteFor(TARGET_WINDOW, () -> {
                    player.tickCount = hit + MEMORY_TICKS;
                    helper.assertTrue(vex.getTarget() == null, "an attacker of 101 ticks is forgotten");
                })
                .thenExecute(() -> {
                    player.setLastHurtByMob(husk);
                    second[0] = player.getLastHurtByMobTimestamp();
                })
                .thenWaitUntil(() -> {
                    player.tickCount = second[0] + MEMORY_TICKS - 1;
                    helper.assertTrue(targets(vex, husk), "an attacker of exactly 100 ticks is still fresh");
                })
                .thenSucceed();
    }
}
