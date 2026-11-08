package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.evoking.HurtBySummonerSideGoal.MEMORY_TICKS;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.DodgeGolemGoal;
import io.github.willrieske.spellcraft.evoking.EvokingCaster;
import io.github.willrieske.spellcraft.evoking.EvokingCaster.Request;
import io.github.willrieske.spellcraft.evoking.FleeCreeperGoal;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.evoking.VexSummons;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import io.github.willrieske.spellcraft.spell.rules.VexCap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Bound Vex GameTests (Evoking II spec §3, §11 "Part 2"). Tests whose vex flies or teleports, that need headroom, or
 * that park many vexes register through {@code FangGameTests.addPadded} (padding 13); the rest run inside the shared
 * 3×3×3 box, where the barrier shell blocks line of sight, so a targeting test keeps the vex and its prey both inside
 * the box. Hostiles are husks (they do not burn in daylight).
 */
public final class BoundVexGameTests {
    private static final int WARMUP = EvokerSpell.WARMUP_TICKS;

    /**
     * The batches of the two server-stop tests: the stop handler clears every repair claim and portal note on the
     * server, so each test runs alone (batches run one after another), as {@code FangGameTests}' server-stop test does.
     * Two batches, because each test's handler call would clear what the other one is watching.
     */
    private static final String SERVER_STOP_NOTES = "bound_vex_server_stop_notes";

    private static final String SERVER_STOP_CLAIMS = "bound_vex_server_stop_claims";

    /** The box's top north-west corner: where a test parks the vex. */
    static final BlockPos VEX_SPOT = new BlockPos(0, 2, 0);

    /** The box's opposite bottom corner: farther from {@link #VEX_SPOT} than the summoner standing at the box's middle. */
    static final BlockPos PREY_SPOT = new BlockPos(2, 1, 2);

    /** The middle of the structure's box, where {@code FangGameTests.caster} stands the player. */
    private static final BlockPos FEET = FangGameTests.FEET;

    /** A vex this far north of the caster is outside the teleport distance (12) and inside the padding (13). */
    static final int FAR_NORTH = 13;

    /**
     * Ticks for a test that waits for a 10-tick target scan or watches a 40-tick window (not
     * {@code FangGameTests}' 200-tick {@code LONG}).
     */
    private static final int TARGETING_TICKS = 100;

    /** The window over which "never a target" is watched: four target scans. */
    private static final int TARGET_WINDOW = 40;

    /**
     * Where the golem tests stand the golem: four blocks east of the box's middle, in the padding. Not three: a golem
     * is 1.4 wide and 2.7 tall, and its eyes would then sit in the box's barrier shell (x = 3), which hurts it
     * ("inWall", 1 health per tick or so), so a "full" golem would soon need repair.
     */
    private static final BlockPos GOLEM_SPOT = FEET.east(4);

    /** The box's bottom north-east corner, a second spot inside the box for an ally or a second mob. */
    private static final BlockPos SIDE_SPOT = new BlockPos(2, 1, 0);

    /** The box's bottom south-west corner, a third spot inside the box. */
    private static final BlockPos CORNER_SPOT = new BlockPos(0, 1, 2);

    private BoundVexGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(SHARED, "bound_vex_cap_refuses_at_twelve", BoundVexGameTests::capRefusesAtTwelve);
        FangGameTests.addPadded(
                SHARED, "bound_vex_cap_tops_up_from_ten", helper -> capTopsUpToTwelve(helper, VexCap.CAP - 2));
        FangGameTests.addPadded(
                SHARED, "bound_vex_cap_tops_up_from_eleven", helper -> capTopsUpToTwelve(helper, VexCap.CAP - 1));
        FangGameTests.addPadded(SHARED, "bound_vex_cap_is_per_summoner", BoundVexGameTests::capIsPerSummoner);
        FangGameTests.addPadded(SHARED, "bound_vex_cap_counts_every_level", BoundVexGameTests::capCountsEveryLevel);
        SpellcraftGameTests.add(SHARED, "bound_vex_gives_no_experience", BoundVexGameTests::givesNoExperience);
        SpellcraftGameTests.add(SHARED, "bound_vex_stay_toggle", BoundVexGameTests::stayToggle);
        SpellcraftGameTests.add(SHARED, "bound_vex_idles_without_a_summoner", BoundVexGameTests::idlesWithoutASummoner);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_idles_with_a_dead_summoner", BoundVexGameTests::idlesWithADeadSummoner);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_idles_with_the_summoner_elsewhere",
                BoundVexGameTests::idlesWithTheSummonerElsewhere);
        SpellcraftGameTests.add(SHARED, "bound_vex_save_keeps_stay_and_mark", BoundVexGameTests::saveKeepsStayAndMark);
        FangGameTests.addPadded(SHARED, "bound_vex_guard_area_edges", BoundVexGameTests::guardAreaEdges);
        FangGameTests.addPadded(
                SHARED, "bound_vex_follow_flies_into_the_orbit", BoundVexGameTests::followFliesIntoTheOrbit);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_follow_teleports_from_thirteen_blocks",
                BoundVexGameTests::followTeleportsFromThirteen);
        FangGameTests.addPadded(
                SHARED, "bound_vex_follow_idles_without_a_summoner", BoundVexGameTests::followIdlesWithoutASummoner);
        FangGameTests.addPadded(SHARED, "bound_vex_staying_holds_its_post", BoundVexGameTests::stayingHoldsItsPost);
        FangGameTests.addPadded(SHARED, "bound_vex_follows_again_after_stay", BoundVexGameTests::followsAgainAfterStay);
        FangGameTests.addPadded(SHARED, "bound_vex_portal_follow", BoundVexGameTests::portalFollow);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_same_dimension_teleport_brings_nothing",
                BoundVexGameTests::sameDimensionTeleportBringsNothing);
        FangGameTests.addPadded(
                SHARED, "bound_vex_logout_drops_the_portal_note", BoundVexGameTests::logoutDropsThePortalNote);
        FangGameTests.addPadded(
                SERVER_STOP_NOTES,
                "bound_vex_server_stop_drops_the_portal_note",
                BoundVexGameTests::serverStopDropsThePortalNote);
        FangGameTests.addPadded(
                SERVER_STOP_CLAIMS,
                "bound_vex_server_stop_frees_the_repair_claims",
                BoundVexGameTests::serverStopFreesTheRepairClaims);
        SpellcraftGameTests.add(SHARED, "bound_vex_three_target_goals", BoundVexGameTests::threeTargetGoals);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_attacks_a_hostile_in_the_area",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::attacksAHostile);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_pig_but_not_a_husk",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::ignoresAPigButNotAHusk);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_creeper_until_provoked",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::ignoresACreeperUntilProvoked);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_targets_what_hurt_the_summoner",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::targetsWhatHurtTheSummoner);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_targets_what_the_summoner_hit",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::targetsWhatTheSummonerHit);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_targets_what_hurt_an_ally",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::targetsWhatHurtAnAlly);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_joins_an_allys_fight", TARGETING_TICKS, 0, BoundVexGameTests::joinsAnAllysFight);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ally_goal_skips_a_creeper",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::allyGoalSkipsACreeper);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_ally_goal_skips_a_pig", TARGETING_TICKS, 0, BoundVexGameTests::allyGoalSkipsAPig);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_patrol_ignores_another_players_vex",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::patrolIgnoresAnotherPlayersVex);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_answers_another_players_vex_that_hurt_the_summoner",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::answersAnotherPlayersVexThatHurtTheSummoner);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_calls_the_summoners_wolves",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::callsTheSummonersWolves);
        FangGameTests.addPadded(
                SHARED, "bound_vex_wolf_call_skips_the_wrong_wolves", BoundVexGameTests::wolfCallSkipsTheWrongWolves);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_wolf_call_only_on_a_new_target", BoundVexGameTests::wolfCallOnlyOnANewTarget);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_wolf_call_skips_a_creeper", BoundVexGameTests::wolfCallSkipsACreeper);
        FangGameTests.addPadded(
                SHARED, "bound_vex_drops_a_target_out_of_reach", BoundVexGameTests::dropsATargetOutOfReach);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_drops_a_grudge_target_out_of_reach",
                BoundVexGameTests::dropsAGrudgeTargetOutOfReach);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_drops_an_ally_fight_target_out_of_reach",
                BoundVexGameTests::dropsAnAllyFightTargetOutOfReach);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_drops_a_target_that_becomes_an_ally",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::dropsANewAlly);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_no_summoner_no_target", TARGETING_TICKS, 0, BoundVexGameTests::noSummonerNoTarget);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_held_by_the_patrol_goal",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::heldByThePatrolGoal);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_ignores_a_stale_memory", TARGETING_TICKS, 0, BoundVexGameTests::ignoresAStaleMemory);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_own_attacker_comes_first",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::ownAttackerComesFirst);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_a_creative_attacker_does_not_block",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::creativeAttackerDoesNotBlock);
        FangGameTests.addPadded(
                SHARED, "bound_vex_ignores_an_ally_outside_the_area", BoundVexGameTests::ignoresAnAllyOutsideTheArea);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_ignores_an_ally_whose_box_only_touches_the_area",
                BoundVexGameTests::ignoresAnAllyWhoseBoxOnlyTouchesTheArea);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_ignores_a_hostile_whose_box_only_touches_the_area",
                BoundVexGameTests::ignoresAHostileWhoseBoxOnlyTouchesTheArea);
        FangGameTests.addPadded(
                SHARED, "bound_vex_patrol_needs_line_of_sight", BoundVexGameTests::patrolNeedsLineOfSight);
        FangGameTests.addPadded(
                SHARED, "bound_vex_patrol_drops_a_husk_it_cannot_see", BoundVexGameTests::patrolDropsAHuskItCannotSee);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_patrol_drops_a_husk_that_becomes_an_ally",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::patrolDropsAHuskThatBecomesAnAlly);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ally_goal_drops_a_husk_that_becomes_an_ally",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::allyGoalDropsAHuskThatBecomesAnAlly);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_summoners_victim_comes_before_an_allys_attacker",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::summonersVictimComesBeforeAnAllysAttacker);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_stale_attacker_of_the_summoner",
                TARGETING_TICKS,
                0,
                BoundVexGameTests::ignoresAStaleAttackerOfTheSummoner);
        FangGameTests.addPadded(SHARED, "bound_vex_flees_a_hissing_creeper", BoundVexGameTests::fleesAHissingCreeper);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_flee_ignores_a_calm_or_far_creeper",
                BoundVexGameTests::fleeIgnoresACalmOrFarCreeper);
        FangGameTests.addPadded(SHARED, "bound_vex_dodges_a_golems_swing", BoundVexGameTests::dodgesAGolemsSwing);
        FangGameTests.addPadded(
                SHARED, "bound_vex_dodge_ignores_a_swing_away_or_far", BoundVexGameTests::dodgeIgnoresASwingAwayOrFar);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_dodge_ignores_a_diagonal_swing_beyond_three_blocks",
                BoundVexGameTests::dodgeIgnoresADiagonalSwingBeyondThreeBlocks);
        FangGameTests.addPadded(
                SHARED, "bound_vex_repairs_a_hurt_ally_golem", BoundVexGameTests::repairsAHurtAllyGolem);
        FangGameTests.addPadded(SHARED, "bound_vex_repairs_two_golems_apart", BoundVexGameTests::repairsTwoGolemsApart);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_repair_claim_frees_when_the_vex_is_gone",
                BoundVexGameTests::claimFreesWhenTheVexIsGone);
        FangGameTests.addPadded(
                SHARED, "bound_vex_a_fight_interrupts_a_repair", BoundVexGameTests::fightInterruptsARepair);
        FangGameTests.addPadded(SHARED, "bound_vex_flee_interrupts_a_repair", BoundVexGameTests::fleeInterruptsARepair);
        FangGameTests.addPadded(
                SHARED, "bound_vex_no_repair_for_a_mad_or_full_golem", BoundVexGameTests::noRepairForAMadOrFullGolem);
    }

    /**
     * A Bound Vex of {@code summoner} at {@code relative} with its full AI and the iron sword ({@code finalizeSpawn}
     * runs because a spawn reason is given), discarded when the test ends. Not through {@code VexSummons.summon}: the
     * test chooses where it hovers.
     *
     * @param helper the test helper
     * @param relative where to spawn it, relative to the structure
     * @param summoner the player it counts as summoned by
     * @return the vex
     */
    static BoundVex boundVex(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), relative, EntitySpawnReason.MOB_SUMMONED);
        vex.setData(AllyAttachments.SUMMONER, summoner.getUUID());
        vex.setBoundOrigin(helper.absolutePos(relative));
        // A fresh vex has gravity until its first tick sets noGravity, and the drift would sink it into the floor.
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        whenDone(helper, vex::discard);
        return vex;
    }

    /**
     * {@link #boundVex} with its movement goals removed, so it hovers where it was put while its target goals still
     * run. Parking fixes every distance in a test.
     *
     * @param helper the test helper
     * @param relative where to park it
     * @param summoner the player it counts as summoned by
     * @return the parked vex
     */
    static BoundVex parked(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = boundVex(helper, relative, summoner);
        vex.goalSelector.removeAllGoals(goal -> true);
        return vex;
    }

    /**
     * A husk (a hostile that does not burn in daylight) with no AI, discarded when the test ends.
     *
     * @param helper the test helper
     * @param relative where to spawn it
     * @return the husk
     */
    static Husk husk(GameTestHelper helper, BlockPos relative) {
        Husk husk = helper.spawn(EntityTypes.HUSK, relative);
        husk.setNoAi(true);
        whenDone(helper, husk::discard);
        return husk;
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
     * Takes every target goal away from {@code vex}, so only the test decides who it targets.
     *
     * @param vex the vex
     * @return {@code vex}
     */
    static BoundVex noTargets(BoundVex vex) {
        vex.targetSelector.removeAllGoals(goal -> true);
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

    /** A creeper with no AI, so it neither swells nor walks, discarded when the test ends. */
    private static Creeper calmCreeper(GameTestHelper helper, BlockPos relative) {
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, relative);
        creeper.setNoAi(true);
        whenDone(helper, creeper::discard);
        return creeper;
    }

    /** A Bound Vex with no summoner mark (as {@code /summon} makes one), hovering, discarded when the test ends. */
    private static BoundVex unmarkedVex(GameTestHelper helper, BlockPos relative) {
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), relative);
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        whenDone(helper, vex::discard);
        return vex;
    }

    /** An untamed wolf with no AI, discarded when the test ends. */
    private static Wolf untamedWolf(GameTestHelper helper, BlockPos relative) {
        Wolf wolf = helper.spawn(EntityTypes.WOLF, relative);
        wolf.setNoAi(true);
        whenDone(helper, wolf::discard);
        return wolf;
    }

    /** A survival player with Evoking inside the shared box (no padding wait). */
    private static ServerPlayer summoner(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(EvokingGameTests.evoking());
        MockPlayers.moveIntoTest(helper, player);
        return player;
    }

    /**
     * When the summoner already has twelve Bound Vexes alive, should answer CAPPED with the too-many notice, start no
     * cast and spend no cooldown: with the vexes gone, the same press starts. Padded: twelve vexes in the shared box
     * would sit near the edge of a neighbor's vex search.
     *
     * <p>Method under test: {@link EvokingCaster#request}, through {@link BoundVex#countOwnedBy} and
     * {@link VexCap#toSummon}
     */
    static void capRefusesAtTwelve(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        MinecraftServer server = helper.getLevel().getServer();
        List<BoundVex> vexes = new ArrayList<>();
        for (int i = 0; i < VexCap.CAP; i++) {
            vexes.add(parked(helper, VEX_SPOT, player));
        }
        GameTestSupport.drainActionBar(player);

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(request, Request.CAPPED, "request at the cap");
        GameTestSupport.assertActionBarKey(helper, player, EvokingCaster.TOO_MANY_KEY);
        helper.assertFalse(player.hasEffect(ModEffects.CASTING_VEXES), "no casting effect");
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), VexCap.CAP, "twelve counted");

        // Act: the vexes go, and the same press starts, so no cooldown was spent
        vexes.forEach(Entity::discard);
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 0, "none counted once gone");
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "no cooldown spent");
        cancelCast(player);
        helper.succeed();
    }

    /**
     * When the summoner has ten (or eleven) Bound Vexes alive, should start the cast and summon two (or one), for
     * twelve in all: the remainder, not a whole batch.
     *
     * <p>Method under test: {@link EvokingCaster#request} then {@link EvokingCaster#tick}, through
     * {@code VexSummons.summon(level, player, count)}
     */
    static void capTopsUpToTwelve(GameTestHelper helper, int alive) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        for (int i = 0; i < alive; i++) {
            parked(helper, VEX_SPOT, player);
        }

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "request with room");

        // Assert
        helper.runAfterDelay(WARMUP, () -> {
            FangGameTests.step(player);
            helper.assertValueEqual(
                    BoundVex.countOwnedBy(helper.getLevel().getServer(), player.getUUID()),
                    VexCap.CAP,
                    "topped up from " + alive + " to twelve");
            helper.succeed();
        });
    }

    /**
     * When another player has twelve Bound Vexes alive and an unmarked Bound Vex ({@code /summon}) is near, should count
     * none of them for this summoner, whose press starts.
     *
     * <p>Method under test: {@link BoundVex#countOwnedBy}, through {@link EvokingCaster#request}
     */
    static void capIsPerSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer stranger = summoner(helper);
        for (int i = 0; i < VexCap.CAP; i++) {
            parked(helper, VEX_SPOT, stranger);
        }
        unmarkedVex(helper, VEX_SPOT).goalSelector.removeAllGoals(goal -> true);
        helper.assertValueEqual(
                BoundVex.countOwnedBy(server, stranger.getUUID()), VexCap.CAP, "setup: stranger capped");

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 0, "nobody else's vexes counted");
        helper.assertValueEqual(request, Request.STARTED, "this summoner's press starts");
        cancelCast(player);
        helper.succeed();
    }

    /**
     * Drops {@code player}'s pending cast the way a logout does, so a test that ended on a started request leaves no
     * cast on its mock player (a pending cast would summon vexes into a later test).
     */
    private static void cancelCast(ServerPlayer player) {
        EvokingCaster.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
    }

    /**
     * When the summoner has one Bound Vex in the Nether and eleven here, should count twelve and answer CAPPED: the
     * count spans every loaded level (spec §3.2). A count of this level alone would see eleven and start.
     *
     * <p>Method under test: {@link BoundVex#countOwnedBy}, through {@link EvokingCaster#request}
     */
    static void capCountsEveryLevel(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        FangGameTests.toNether(helper, player);
        List<BoundVex> inNether = VexSummons.summon(player.level(), player, 1);
        // Staying, so the summoner's trip back brings nothing along: a follower within 16 blocks would be carried to
        // the Overworld and the count would be twelve in one level, which no per-level count could tell apart.
        inNether.forEach(vex -> {
            vex.setStaying(true);
            whenDone(helper, vex::discard);
        });
        helper.assertValueEqual(inNether.size(), 1, "setup: one Bound Vex in the Nether");
        MockPlayers.moveIntoTest(helper, player);
        helper.assertValueEqual(player.level().dimension(), helper.getLevel().dimension(), "setup: summoner back");
        for (int i = 0; i < VexCap.CAP - 1; i++) {
            parked(helper, VEX_SPOT, player);
        }

        // Act & Assert: polled, because a vex in a just-forced Nether chunk is not in the level's entity lookup until
        // the chunk's ticket has taken effect a tick or two later (the count is of loaded levels, spec §13). The
        // count assertion fails first, so the request runs only once the Nether vex is counted.
        helper.succeedWhen(() -> {
            helper.assertValueEqual(
                    BoundVex.countOwnedBy(helper.getLevel().getServer(), player.getUUID()), VexCap.CAP, "both levels");
            helper.assertValueEqual(
                    EvokingCaster.request(player, EvokerSpell.VEXES), Request.CAPPED, "capped by the Nether vex");
        });
    }

    /**
     * When the summoner clicks a Bound Vex with an empty main hand, should toggle stay on (the post is the block it
     * stands at, whatever origin it had before) and off again, each handled by the server, the post kept when stay goes
     * off; a stranger's click, a sneaking click and a click with an item in hand should pass and change nothing.
     *
     * <p>Method under test: {@code BoundVex.mobInteract} (protected), through {@code Mob.interact}
     */
    static void stayToggle(GameTestHelper helper) {
        // Arrange
        ServerPlayer summoner = summoner(helper);
        ServerPlayer stranger = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, summoner);
        BlockPos post = helper.absolutePos(VEX_SPOT);
        vex.setBoundOrigin(post.above(3)); // another origin, so only the click can make it the post

        // Act & Assert
        helper.assertFalse(
                vex.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a stranger's click passes");
        helper.assertFalse(vex.isStaying(), "still following after a stranger's click");
        summoner.setShiftKeyDown(true);
        helper.assertFalse(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a sneaking click passes");
        summoner.setShiftKeyDown(false);
        GameTestSupport.hold(summoner, new ItemStack(Items.STICK));
        helper.assertFalse(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a click with an item passes");
        GameTestSupport.hold(summoner, ItemStack.EMPTY);
        helper.assertFalse(vex.isStaying(), "setup: following before the real click");
        helper.assertValueEqual(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO),
                InteractionResult.SUCCESS_SERVER,
                "the summoner's empty click is handled by the server");
        helper.assertTrue(vex.isStaying(), "staying");
        helper.assertValueEqual(vex.getBoundOrigin(), post, "the post is where it stood");
        helper.assertValueEqual(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO),
                InteractionResult.SUCCESS_SERVER,
                "a second click is handled too");
        helper.assertFalse(vex.isStaying(), "following again");
        helper.assertValueEqual(vex.getBoundOrigin(), post, "switching stay off leaves the post");
        helper.succeed();
    }

    /**
     * When a Bound Vex has no summoner mark at all (as {@code /summon} makes one), should idle: no summoner, no prey, a
     * click passes, and it is never removed for being far away.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(LivingEntity)},
     * {@link BoundVex#removeWhenFarAway}, {@link BoundVex#requiresCustomPersistence}
     */
    static void idlesWithoutASummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = unmarkedVex(helper, VEX_SPOT);
        vex.goalSelector.removeAllGoals(goal -> true);

        // Act & Assert
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner");
        helper.assertFalse(vex.isPrey(player), "nothing is prey without a summoner");
        helper.assertFalse(
                vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "a click passes");
        helper.assertFalse(vex.removeWhenFarAway(1.0E6), "never removed for distance");
        helper.assertTrue(vex.requiresCustomPersistence(), "persists on its own");
        helper.succeed();
    }

    /**
     * When a staying Bound Vex is saved and loaded the way a chunk save does, should still be staying and still carry
     * its summoner mark, and should save no life limit.
     *
     * <p>Method under test: {@code BoundVex.addAdditionalSaveData}/{@code readAdditionalSaveData} (protected), through
     * {@link GameTestSupport#reloaded}
     */
    static void saveKeepsStayAndMark(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setStaying(true);

        // Act
        if (!(GameTestSupport.reloaded(helper.getLevel(), vex) instanceof BoundVex copy)) {
            throw helper.assertionException("the reloaded copy is not a Bound Vex");
        }

        // Assert
        helper.assertTrue(copy.isStaying(), "staying survives the save");
        helper.assertTrue(copy.isSummonedBy(player.getUUID()), "the mark survives the save");
        helper.assertValueEqual(FangGameTests.savedInt(vex, "life_ticks"), Integer.MIN_VALUE, "no life limit saved");
        helper.succeed();
    }

    /**
     * When a Bound Vex is asked what killing it is worth, should answer no experience (vanilla's vex gives 3, which
     * three vexes a press would farm), whoever killed it.
     *
     * <p>Method under test: {@code BoundVex}'s constructor ({@code xpReward}), through
     * {@code LivingEntity.getExperienceReward}
     */
    static void givesNoExperience(GameTestHelper helper) {
        // Arrange
        BoundVex vex = boundVex(helper, VEX_SPOT, summoner(helper));

        // Act
        int reward = vex.getExperienceReward(helper.getLevel(), null);

        // Assert
        helper.assertValueEqual(reward, 0, "a Bound Vex gives no experience");
        helper.succeed();
    }

    /**
     * When a marked Bound Vex's summoner dies, should idle as if the summoner were offline (spec §10, "as offline until
     * respawn"): no summoner, and a husk that was prey a moment ago no longer is.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(LivingEntity)}
     */
    static void idlesWithADeadSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        helper.assertTrue(vex.isPrey(husk), "setup: a husk is prey while the summoner lives");

        // Act
        player.kill(helper.getLevel());

        // Assert
        helper.assertFalse(player.isAlive(), "setup: the summoner is dead");
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner while dead");
        helper.assertFalse(vex.isPrey(husk), "nothing is prey while the summoner is dead");
        helper.succeed();
    }

    /**
     * When a marked Bound Vex's summoner goes to another dimension, should idle (spec §3.1: the summoner must be in the
     * vex's level): no summoner, and a husk that was prey a moment ago no longer is.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(LivingEntity)}
     */
    static void idlesWithTheSummonerElsewhere(GameTestHelper helper) {
        // Arrange: staying, so the trip to the Nether does not carry the vex along (then no instance is left here)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setStaying(true);
        Husk husk = husk(helper, PREY_SPOT);
        helper.assertTrue(vex.isPrey(husk), "setup: a husk is prey while the summoner is here");

        // Act
        FangGameTests.toNether(helper, player);

        // Assert
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner while it is in the Nether");
        helper.assertFalse(vex.isPrey(husk), "nothing is prey while the summoner is elsewhere");
        helper.succeed();
    }

    /**
     * When another entity's feet are exactly on a face of the 8×8×7 guard box, should count it inside; half a block
     * past the face (or past the top), outside. Every goal asks this one method.
     *
     * <p>Method under test: {@link BoundVex#inGuardArea}
     */
    static void guardAreaEdges(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        Vec3 at = vex.position();

        // Act & Assert
        husk.setPos(at.add(4.0, 0.0, 0.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the east face: inside");
        husk.setPos(at.add(4.5, 0.0, 0.0));
        helper.assertFalse(vex.inGuardArea(husk), "half a block east of it: outside");
        husk.setPos(at.add(0.0, 0.0, -4.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the north face: inside");
        husk.setPos(at.add(0.0, 0.0, -4.5));
        helper.assertFalse(vex.inGuardArea(husk), "half a block north of it: outside");
        husk.setPos(at.add(0.0, 3.5, 0.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the top face: inside");
        husk.setPos(at.add(0.0, 4.0, 0.0));
        helper.assertFalse(vex.inGuardArea(husk), "half a block above it: outside");
        husk.setPos(at.add(4.0, -3.5, 4.0));
        helper.assertTrue(vex.inGuardArea(husk), "a bottom corner: inside");
        helper.succeed();
    }

    /**
     * When a following Bound Vex hovers seven blocks from its summoner, should fly into the orbit band: three ticks in
     * it is still more than six blocks out (a teleport would have put it within {@code TELEPORT_LANDING_MAX}), and it
     * then comes within five blocks (the band's edge plus one; a flight toward a spot inside the band always crosses
     * that line). The vex has no target goals ({@link #followOnly}), so no hostile can pull it off its flight.
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
     * ({@link #followOnly}), so no hostile in a neighboring structure can hold it.
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
     * {@link #boundVex} without its target goals, for a test whose subject is movement. A vex thirteen or more blocks
     * out hovers at the edge of its padding, where a hostile in a neighboring test's structure can be within its guard
     * area and reach; with a target the charge goal (3) outranks follow (4) and the vex chases it away, so a movement
     * test with the full target AI can fail for a reason that has nothing to do with movement.
     */
    private static BoundVex followOnly(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        return noTargets(boundVex(helper, relative, summoner));
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
     * ({@link #followOnly}), so the distance check cannot be spoiled by a chase.
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
     * and never fly back (as in {@link #stayingHoldsItsPost}). The vex has no target goals ({@link #followOnly}): the
     * subject is movement, and its legs still reach four blocks into the padding, where a neighboring test's hostile
     * may stand and hold it (charge outranks follow).
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
     * <p>Method under test: {@code NearestHostileGoal}'s creeper exclusion and {@code HurtBySummonerSideGoal}'s own-hurt
     * memory (both package-private)
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
     * ticks (the ally goal joins hostile targets only: "if prey and hostile", spec §3.4 item 2); a husk the wolf targets
     * next should be joined (the control: the goal works here).
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
     * Another player's Bound Vex, parked at {@link #PREY_SPOT} (inside the guard box of a vex parked at
     * {@link #VEX_SPOT}) with no target goals of its own, after asserting that {@code pvp} is on and so the vex of
     * {@code player} may take it as prey.
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
     * When a Bound Vex takes a husk, should call only the summoner's tamed, idle, un-sitting wolf within sixteen blocks:
     * not an untamed wolf, another player's tamed wolf, a wolf already fighting, a sitting wolf, or the summoner's wolf
     * about seventeen blocks away (inside the cube a box query covers, outside the sixteen-block sphere).
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
     * the patrol goal off) and the husk is then moved eight blocks away, past the 7.25 reach, should drop it within a few
     * ticks and not take it again, though the memory is still fresh. The grudge goal never calls {@code super}, so
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

    /** Moves {@code entity} to {@code dx} east and {@code dz} south of {@code vex} (same height), held in place. */
    private static void placeBeside(BoundVex vex, Entity entity, double dx, double dz) {
        entity.setNoGravity(true);
        entity.setPos(vex.position().add(dx, 0, dz));
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

    /**
     * A {@link #calmCreeper} whose fuse is so long that it hisses without exploding once {@code setSwellDir(1)} is
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

    /** {@link #boundVex} without its follow goal, so it stays where a repair test puts it until a job moves it. */
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
     * ({@link #followOnly}): it ends up eleven blocks out, near the edge of its padding.
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
     * control). The vex has no target goals ({@link #followOnly}): the subject is movement.
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
