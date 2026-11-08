package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.EvokingCaster;
import io.github.willrieske.spellcraft.evoking.EvokingCaster.Request;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.evoking.VexSummons;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import io.github.willrieske.spellcraft.spell.rules.VexCap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Bound Vex GameTests (Evoking II spec §3, §11 "Part 2"). Tests whose vex flies or teleports, that need headroom, or
 * that park many vexes register through {@code FangGameTests.addPadded} (padding 13); the rest run inside the shared
 * 3×3×3 box, where the barrier shell blocks line of sight, so a targeting test keeps the vex and its prey both inside
 * the box. Hostiles are husks (they do not burn in daylight).
 */
public final class BoundVexGameTests {
    /** The yaw a caster faces; {@code FangGameTests.caster} takes it. */
    static final float NORTH = 180.0F;

    private static final int WARMUP = EvokerSpell.WARMUP_TICKS;

    /** The box's top north-west corner: where a test parks the vex. */
    static final BlockPos VEX_SPOT = new BlockPos(0, 2, 0);

    /** The box's opposite bottom corner: farther from {@link #VEX_SPOT} than the summoner standing at the box's middle. */
    static final BlockPos PREY_SPOT = new BlockPos(2, 1, 2);

    /** The middle of the structure's box, where {@code FangGameTests.caster} stands the player. */
    private static final BlockPos FEET = FangGameTests.FEET;

    /** A vex this far north of the caster is outside the teleport distance (12) and inside the padding (13). */
    static final int FAR_NORTH = 13;

    /** Ticks for a test that waits for a 10-tick target scan or watches a 40-tick window. */
    private static final int LONG = 100;

    /** The window over which "never a target" is watched: four target scans. */
    private static final int TARGET_WINDOW = 40;

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
        SpellcraftGameTests.add(SHARED, "bound_vex_three_target_goals", BoundVexGameTests::threeTargetGoals);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_attacks_a_hostile_in_the_area", LONG, 0, BoundVexGameTests::attacksAHostile);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_ignores_a_pig_but_not_a_husk", LONG, 0, BoundVexGameTests::ignoresAPigButNotAHusk);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_creeper_until_provoked",
                LONG,
                0,
                BoundVexGameTests::ignoresACreeperUntilProvoked);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_targets_what_hurt_the_summoner",
                LONG,
                0,
                BoundVexGameTests::targetsWhatHurtTheSummoner);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_targets_what_the_summoner_hit",
                LONG,
                0,
                BoundVexGameTests::targetsWhatTheSummonerHit);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_targets_what_hurt_an_ally", LONG, 0, BoundVexGameTests::targetsWhatHurtAnAlly);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_joins_an_allys_fight", LONG, 0, BoundVexGameTests::joinsAnAllysFight);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_calls_the_summoners_wolves", LONG, 0, BoundVexGameTests::callsTheSummonersWolves);
        FangGameTests.addPadded(
                SHARED, "bound_vex_wolf_call_skips_the_wrong_wolves", BoundVexGameTests::wolfCallSkipsTheWrongWolves);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_wolf_call_only_on_a_new_target", BoundVexGameTests::wolfCallOnlyOnANewTarget);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_wolf_call_skips_a_creeper", BoundVexGameTests::wolfCallSkipsACreeper);
        FangGameTests.addPadded(
                SHARED, "bound_vex_drops_a_target_out_of_reach", BoundVexGameTests::dropsATargetOutOfReach);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_drops_a_target_that_becomes_an_ally", LONG, 0, BoundVexGameTests::dropsANewAlly);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_no_summoner_no_target", LONG, 0, BoundVexGameTests::noSummonerNoTarget);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_held_by_the_patrol_goal", LONG, 0, BoundVexGameTests::heldByThePatrolGoal);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_ignores_a_stale_memory", LONG, 0, BoundVexGameTests::ignoresAStaleMemory);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_own_attacker_comes_first", LONG, 0, BoundVexGameTests::ownAttackerComesFirst);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_a_creative_attacker_does_not_block",
                LONG,
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
                LONG,
                0,
                BoundVexGameTests::patrolDropsAHuskThatBecomesAnAlly);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ally_goal_drops_a_husk_that_becomes_an_ally",
                LONG,
                0,
                BoundVexGameTests::allyGoalDropsAHuskThatBecomesAnAlly);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_summoners_victim_comes_before_an_allys_attacker",
                LONG,
                0,
                BoundVexGameTests::summonersVictimComesBeforeAnAllysAttacker);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_ignores_a_stale_attacker_of_the_summoner",
                LONG,
                0,
                BoundVexGameTests::ignoresAStaleAttackerOfTheSummoner);
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
        BoundVex unmarked = helper.spawn(ModEntities.BOUND_VEX.get(), VEX_SPOT);
        unmarked.goalSelector.removeAllGoals(goal -> true);
        whenDone(helper, unmarked::discard);
        helper.assertValueEqual(
                BoundVex.countOwnedBy(server, stranger.getUUID()), VexCap.CAP, "setup: stranger capped");

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 0, "nobody else's vexes counted");
        helper.assertValueEqual(request, Request.STARTED, "this summoner's press starts");
        helper.succeed();
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
        inNether.forEach(vex -> whenDone(helper, vex::discard));
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
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)},
     * {@link BoundVex#removeWhenFarAway}, {@link BoundVex#requiresCustomPersistence}
     */
    static void idlesWithoutASummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), VEX_SPOT);
        vex.goalSelector.removeAllGoals(goal -> true);
        whenDone(helper, vex::discard);

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
     * When a marked Bound Vex's summoner dies, should idle as if the summoner were offline (spec §10, "as offline until
     * respawn"): no summoner, and a husk that was prey a moment ago no longer is.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}
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
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}
     */
    static void idlesWithTheSummonerElsewhere(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
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
     * past the face (or past the top), outside. The goals of Tasks 5 and 6 all ask this one method.
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
     * that line).
     *
     * <p>Method under test: {@link FollowSummonerGoal}, through the vex's goal selector
     */
    static void followFliesIntoTheOrbit(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(7), player);
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
     * a flight could not cover that distance so fast, so it teleported.
     *
     * <p>Method under test: {@link FollowSummonerGoal}, through the vex's goal selector
     */
    static void followTeleportsFromThirteen(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(FAR_NORTH), player);

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
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), FEET.north(FAR_NORTH));
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        whenDone(helper, vex::discard);
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
     * the wander (±7 blocks around the post) stays inside the padding.
     *
     * <p>Method under test: {@link FollowSummonerGoal} and {@code BoundVex.StayWanderGoal}, through the goal selector
     */
    static void stayingHoldsItsPost(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET, player);
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
     * When a staying Bound Vex thirteen blocks out is switched back to following after twenty ticks, should come back
     * within five blocks of its summoner (once any wander leg under way is done).
     *
     * <p>Method under test: {@link BoundVex#setStaying} and {@link FollowSummonerGoal}, through the goal selector
     */
    static void followsAgainAfterStay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, FEET.north(FAR_NORTH), player);
        vex.setStaying(true);

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
                        "back beside the summoner, got " + vex.distanceTo(player)))
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

        // Assert
        helper.succeedWhen(
                () -> helper.assertTrue(targets(vex, husk), "the husk, not the nearer summoner, is the target"));
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
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, PREY_SPOT);
        creeper.setNoAi(true);
        whenDone(helper, creeper::discard);

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
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);

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
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);

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
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);

        // Act
        wolf.setTarget(husk);

        // Assert
        helper.succeedWhen(() -> helper.assertTrue(targets(vex, husk), "the ally's target is the target"));
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
        vex.targetSelector.removeAllGoals(goal -> true);
        Husk husk = husk(helper, PREY_SPOT);
        Pig pig = pigHere(helper, SIDE_SPOT);
        Wolf idle = pet(helper, FEET.east(2), player);
        Wolf untamed = helper.spawn(EntityTypes.WOLF, FEET.east(3));
        untamed.setNoAi(true);
        whenDone(helper, untamed::discard);
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
        vex.targetSelector.removeAllGoals(goal -> true);
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
        vex.targetSelector.removeAllGoals(goal -> true);
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, PREY_SPOT);
        creeper.setNoAi(true);
        whenDone(helper, creeper::discard);
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
     * When a parked Bound Vex's husk target is moved past the 7.25 reach and out of the guard box, should drop it
     * within a few ticks and not take it again.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through every target goal's {@code canContinueToUse}
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
     * When a parked Bound Vex has an untamed wolf as its target (the summoner hit it) and the summoner then tames it,
     * should drop the target within a few ticks.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code HurtBySummonerSideGoal.canContinueToUse}
     */
    static void dropsANewAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Wolf wolf = helper.spawn(EntityTypes.WOLF, PREY_SPOT);
        wolf.setNoAi(true);
        whenDone(helper, wolf::discard);
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
     * <p>Method under test: {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}, through every target goal
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
     * When the summoner hit a pig more than a hundred of its own ticks ago, should take no target over forty ticks; the
     * same hit made fresh should make the pig the target (the control).
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the freshness window
     */
    static void ignoresAStaleMemory(GameTestHelper helper) {
        // Arrange: the mock summoner is never ticked, so its clock is what the test sets
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Pig pig = pigHere(helper, PREY_SPOT);
        player.setLastHurtMob(pig);
        player.tickCount += 500;

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW, () -> helper.assertTrue(vex.getTarget() == null, "a stale hit is forgotten"))
                .thenExecute(() -> player.setLastHurtMob(pig))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, pig), "a fresh hit makes the pig the target"))
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
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);
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
        for (int up = 0; up < 3; up++) {
            helper.setBlock(wall.above(up), Blocks.STONE);
        }

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "a husk behind stone is unseen"))
                .thenExecute(() -> {
                    for (int up = 0; up < 3; up++) {
                        helper.setBlock(wall.above(up), Blocks.AIR);
                    }
                })
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
                .thenExecute(() -> {
                    for (int up = 0; up < 3; up++) {
                        helper.setBlock(wall.above(up), Blocks.STONE);
                    }
                })
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
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);
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
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);
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
     * When the summoner's attacker was remembered more than a hundred of the summoner's ticks ago, should take no
     * target over forty ticks; the same memory made fresh should make the husk the target (the control). The
     * summoner's memory is the one a test can age: a vex's own and an ally's are cleared by vanilla at that age.
     *
     * <p>Method under test: {@code HurtBySummonerSideGoal} (package-private): the freshness window
     */
    static void ignoresAStaleAttackerOfTheSummoner(GameTestHelper helper) {
        // Arrange: the mock summoner is never ticked, so its clock is what the test sets
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal);
        Husk husk = husk(helper, PREY_SPOT);
        player.setLastHurtByMob(husk);
        player.tickCount += 500;

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        TARGET_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "a stale attacker is forgotten"))
                .thenExecute(() -> player.setLastHurtByMob(husk))
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "a fresh memory makes the husk the target"))
                .thenSucceed();
    }
}
