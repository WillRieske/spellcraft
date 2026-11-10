package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.endVoid;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.freeSlots;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.placeFor;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.platform;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.set;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.soak;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.FEET;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.NIGHT;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import com.mojang.logging.LogUtils;
import io.github.willrieske.spellcraft.evoking.AttackCrystalGoal;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.FleeCreeperGoal;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.VexRescues;
import io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.Place;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.GuardPosture;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * GameTests of the Part 5 world helpers. This class holds the tests of the support code ({@link
 * BoundVexWorldTestSupport} and the pinned time of day); the rule tests of the later tasks register here too.
 */
public final class BoundVexWorldGameTests {
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

    /** The server-stop test clears every rescue on the server, so it runs in an environment of its own. */
    private static final String SERVER_STOP_RESCUES = "bound_vex_world_server_stop_rescues";

    /** The environment (a batch of its own) of the server-stop crystal test: the stop handler clears every claim. */
    private static final String SERVER_STOP_CRYSTALS = "bound_vex_world_server_stop_crystals";

    /** How many ticks the give-up test's body may take: the 200-tick limit, the retry delay's start and a flight. */
    private static final int CRYSTAL_LONG_BODY = 500;

    /** How far sideways from the player the lava arrangement fills with stone (the shore search reaches 6). */
    private static final int POOL_REACH = 6;

    /** How far below the player's feet the lava arrangement fills with stone. */
    private static final int POOL_DEPTH = 6;

    /** How close a rescuer must hang to its summoner's side point. */
    private static final double SIDE_TOLERANCE = 0.1;

    /** The shore of {@link #lavaPool}, in the test coordinates: the center of the shore feet block. */
    private static final Vec3 SHORE = new Vec3(3.5, 1, 1.5);

    /** Passed to {@link #lavaPool} for an arrangement with no shore at all. */
    private static final int NO_SHORE = Integer.MIN_VALUE;

    private BoundVexWorldGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_setup_end_room", BoundVexWorldGameTests::setupEndRoom);
        padded("bound_vex_world_setup_end_void", BoundVexWorldGameTests::setupEndVoid);
        padded("bound_vex_world_setup_two_places_are_apart", BoundVexWorldGameTests::setupTwoPlacesAreApart);
        padded("bound_vex_world_setup_cleanup_end_void", BoundVexWorldGameTests::setupCleanupEndVoid);
        padded("bound_vex_world_setup_cleanup_room", BoundVexWorldGameTests::setupCleanupRoom);
        padded("bound_vex_world_setup_rejects_bad_extents", BoundVexWorldGameTests::setupRejectsBadExtents);
        padded("bound_vex_world_setup_fluids", BoundVexWorldGameTests::setupFluids);
        SpellcraftGameTests.add(NIGHT, "bound_vex_world_setup_night", BoundVexWorldGameTests::setupNight);
        SpellcraftGameTests.add(SHARED, "bound_vex_world_setup_day", BoundVexWorldGameTests::setupDay);
        padded("bound_vex_world_posture_overworld_day", BoundVexWorldGameTests::postureOverworldDay);
        FangGameTests.addPadded(NIGHT, "bound_vex_world_posture_night", BoundVexWorldGameTests::postureNight);
        padded("bound_vex_world_posture_nether", BoundVexWorldGameTests::postureNether);
        padded("bound_vex_world_posture_end", BoundVexWorldGameTests::postureEnd);
        padded("bound_vex_world_posture_staying_scans_by_place", BoundVexWorldGameTests::postureStayingScansByPlace);
        FangGameTests.addPadded(
                NIGHT,
                "bound_vex_world_posture_close_flight_yields_to_a_target",
                BoundVexWorldGameTests::closeFlightYieldsToATarget);
        FangGameTests.addPadded(
                NIGHT,
                "bound_vex_world_posture_close_flight_follows_a_fleeing_summoner",
                BoundVexWorldGameTests::closeFlightFollowsAFleeingSummoner);
        padded("bound_vex_world_scan_cadence_end", BoundVexWorldGameTests::scanCadenceEnd);
        padded("bound_vex_world_scan_cadence_overworld_day", BoundVexWorldGameTests::scanCadenceOverworldDay);
        padded("bound_vex_world_lava_pull", BoundVexWorldGameTests::lavaPull);
        padded("bound_vex_world_lava_pull_keeps_your_steering", BoundVexWorldGameTests::lavaPullKeepsYourSteering);
        padded("bound_vex_world_lava_pull_ends_on_the_shore", BoundVexWorldGameTests::lavaPullEndsOnTheShore);
        padded("bound_vex_world_lava_pull_pauses_after_60_ticks", BoundVexWorldGameTests::lavaPullPausesAfter60);
        padded("bound_vex_world_lava_pull_ignores_a_low_shore", BoundVexWorldGameTests::lavaPullIgnoresALowShore);
        padded(
                "bound_vex_world_lava_pull_not_for_fire_resistance",
                helper -> lavaPullNotForTheUnhurt(helper, BoundVexWorldGameTests::fireResistant, false));
        padded(
                "bound_vex_world_lava_pull_not_for_spectators",
                helper -> lavaPullNotForTheUnhurt(helper, player -> player.setGameMode(GameType.SPECTATOR), false));
        padded(
                "bound_vex_world_lava_pull_not_for_creative",
                helper -> lavaPullNotForTheUnhurt(helper, player -> player.setGameMode(GameType.CREATIVE), false));
        padded("bound_vex_world_lava_pull_not_for_passengers", BoundVexWorldGameTests::lavaPullNotForPassengers);
        padded(
                "bound_vex_world_lava_pull_not_with_a_staying_vex",
                helper -> lavaPullNotForTheUnhurt(helper, player -> {}, true));
        padded("bound_vex_world_lava_rescuer_keeps_its_target", BoundVexWorldGameTests::lavaRescuerKeepsItsTarget);
        padded("bound_vex_world_rescue_dropped_on_logout", BoundVexWorldGameTests::rescueDroppedOnLogout);
        padded("bound_vex_world_lava_pull_takes_a_fleeing_vex", BoundVexWorldGameTests::lavaPullTakesAFleeingVex);
        padded(
                "bound_vex_world_lava_pull_replaces_a_dead_rescuer",
                BoundVexWorldGameTests::lavaPullReplacesADeadRescuer);
        padded("bound_vex_world_lava_overworld_keeps_burning", BoundVexWorldGameTests::lavaOverworldKeepsBurning);
        padded("bound_vex_world_lava_nether_bucket", BoundVexWorldGameTests::lavaNetherBucket);
        padded(
                "bound_vex_world_lava_nether_no_bucket_while_in_lava",
                BoundVexWorldGameTests::lavaNetherNoBucketWhileInLava);
        padded(
                "bound_vex_world_lava_rescuer_drops_a_target_out_of_reach",
                BoundVexWorldGameTests::lavaRescuerDropsATargetOutOfReach);
        padded(
                "bound_vex_world_lava_pull_ends_after_10_ticks_out",
                BoundVexWorldGameTests::lavaPullEndsAfterTenTicksOut);
        padded("bound_vex_world_lava_rescuer_range_inside", BoundVexWorldGameTests::lavaRescuerRangeInside);
        padded("bound_vex_world_lava_rescuer_range_outside", BoundVexWorldGameTests::lavaRescuerRangeOutside);
        padded("bound_vex_world_lava_shore_two_up", BoundVexWorldGameTests::lavaShoreTwoUp);
        padded("bound_vex_world_lava_shore_one_down", BoundVexWorldGameTests::lavaShoreOneDown);
        padded("bound_vex_world_lava_shore_three_up", BoundVexWorldGameTests::lavaShoreThreeUp);
        padded(
                "bound_vex_world_lava_pull_ends_with_fire_resistance",
                BoundVexWorldGameTests::lavaPullEndsWithFireResistance);
        padded("bound_vex_world_lava_pull_never_passes_the_cap", BoundVexWorldGameTests::lavaPullNeverPassesTheCap);
        padded(
                "bound_vex_world_lava_nether_tail_ends_when_the_rescuer_stays",
                BoundVexWorldGameTests::lavaTailEndsWhenTheRescuerStays);
        padded(
                "bound_vex_world_lava_nether_tail_ends_when_the_player_logs_out",
                BoundVexWorldGameTests::lavaTailEndsWhenThePlayerLogsOut);
        padded(
                "bound_vex_world_lava_nether_tail_ends_when_the_rescuer_dies",
                BoundVexWorldGameTests::lavaTailEndsWhenTheRescuerDies);
        padded("bound_vex_world_drowning_push", BoundVexWorldGameTests::drowningPush);
        padded("bound_vex_world_drowning_not_under_a_ceiling", BoundVexWorldGameTests::drowningNotUnderACeiling);
        padded("bound_vex_world_drowning_not_with_air_left", BoundVexWorldGameTests::drowningNotWithAirLeft);
        padded(
                "bound_vex_world_drowning_not_with_water_breathing",
                BoundVexWorldGameTests::drowningNotWithWaterBreathing);
        padded("bound_vex_world_drowning_not_with_conduit_power", BoundVexWorldGameTests::drowningNotWithConduitPower);
        padded("bound_vex_world_drowning_not_for_creative", BoundVexWorldGameTests::drowningNotForCreative);
        padded("bound_vex_world_drowning_not_for_passengers", BoundVexWorldGameTests::drowningNotForPassengers);
        padded("bound_vex_world_lava_before_drowning", BoundVexWorldGameTests::lavaBeforeDrowning);
        padded("bound_vex_world_drowning_ends_at_the_surface", BoundVexWorldGameTests::drowningEndsAtTheSurface);
        padded(
                "bound_vex_world_drowning_ends_when_the_way_up_closes",
                BoundVexWorldGameTests::drowningEndsWhenTheWayUpCloses);
        padded(
                "bound_vex_world_drowning_ends_with_water_breathing",
                BoundVexWorldGameTests::drowningEndsWithWaterBreathing);
        padded("bound_vex_world_drowning_ends_after_200_ticks", BoundVexWorldGameTests::drowningEndsAfter200Ticks);
        padded("bound_vex_world_drowning_only_in_the_overworld", BoundVexWorldGameTests::drowningOnlyInTheOverworld);
        padded("bound_vex_world_void_catch", BoundVexWorldGameTests::voidCatch);
        padded(
                "bound_vex_world_void_catch_falling_near_the_floor",
                BoundVexWorldGameTests::voidCatchFallingNearTheFloor);
        padded(
                "bound_vex_world_void_catch_not_with_ground_below",
                helper -> voidNotCaughtNearTheFloor(helper, true, new Vec3(0.0, -0.5, 0.0)));
        padded(
                "bound_vex_world_void_catch_not_when_not_falling",
                helper -> voidNotCaughtNearTheFloor(helper, false, Vec3.ZERO));
        padded("bound_vex_world_void_catch_uses_last_ground", BoundVexWorldGameTests::voidCatchUsesLastGround);
        padded("bound_vex_world_void_catch_last_ground_gone", BoundVexWorldGameTests::voidCatchLastGroundGone);
        padded("bound_vex_world_void_catch_rescuer_range_inside", helper -> voidRescuerRange(helper, 40, true));
        padded("bound_vex_world_void_catch_rescuer_range_outside", helper -> voidRescuerRange(helper, 70, false));
        padded("bound_vex_world_void_catch_rescuer_range_edge_inside", helper -> voidRescuerRange(helper, 63, true));
        padded("bound_vex_world_void_catch_rescuer_range_edge_outside", helper -> voidRescuerRange(helper, 64, false));
        padded("bound_vex_world_void_catch_by_a_following_vex", BoundVexWorldGameTests::voidCatchByAFollowingVex);
        padded("bound_vex_world_void_catch_not_by_a_staying_vex", BoundVexWorldGameTests::voidCatchNotByAStayingVex);
        padded("bound_vex_world_void_catch_keeps_a_conjured_hand", BoundVexWorldGameTests::voidCatchKeepsAConjuredHand);
        padded("bound_vex_world_void_catch_refused_teleport", BoundVexWorldGameTests::voidCatchRefusedTeleport);
        padded(
                "bound_vex_world_void_logout_forgets_the_empty_search",
                BoundVexWorldGameTests::voidLogoutForgetsTheSearch);
        padded("bound_vex_world_void_catch_dismounts", BoundVexWorldGameTests::voidCatchDismounts);
        padded("bound_vex_world_void_catch_creative", BoundVexWorldGameTests::voidCatchCreative);
        padded("bound_vex_world_void_catch_not_while_gliding", BoundVexWorldGameTests::voidCatchNotWhileGliding);
        padded("bound_vex_world_void_catch_skips_a_portal_column", BoundVexWorldGameTests::voidCatchSkipsAPortalColumn);
        padded(
                "bound_vex_world_void_no_landing_is_not_searched_every_tick",
                BoundVexWorldGameTests::voidNoLandingIsNotSearchedEveryTick);
        padded("bound_vex_world_void_not_in_the_overworld", BoundVexWorldGameTests::voidNotInTheOverworld);
        padded("bound_vex_world_crystal_broken", BoundVexWorldGameTests::crystalBroken);
        padded("bound_vex_world_crystal_spares_your_side", BoundVexWorldGameTests::crystalSparesYourSide);
        padded("bound_vex_world_crystal_invulnerable_left_alone", BoundVexWorldGameTests::crystalInvulnerableLeftAlone);
        padded("bound_vex_world_crystal_removed_on_the_way", BoundVexWorldGameTests::crystalRemovedOnTheWay);
        padded("bound_vex_world_crystal_needs_a_dragon", BoundVexWorldGameTests::crystalNeedsADragon);
        padded("bound_vex_world_crystal_one_each", BoundVexWorldGameTests::crystalOneEach);
        padded("bound_vex_world_crystal_only_in_the_end", BoundVexWorldGameTests::crystalOnlyInTheEnd);
        padded(
                "bound_vex_world_crystal_needs_a_summoner_at_the_start",
                BoundVexWorldGameTests::crystalNeedsASummonerAtTheStart);
        padded(
                "bound_vex_world_crystal_summoner_lost_mid_flight",
                BoundVexWorldGameTests::crystalSummonerLostMidFlight);
        padded("bound_vex_world_crystal_dragon_dies_mid_flight", BoundVexWorldGameTests::crystalDragonDiesMidFlight);
        padded("bound_vex_world_crystal_neighbors_both_go", BoundVexWorldGameTests::crystalNeighborsBothGo);
        padded(
                "bound_vex_world_crystal_blast_spares_your_side_without_you",
                BoundVexWorldGameTests::crystalBlastSparesYourSideWithoutYou);
        padded("bound_vex_world_crystal_not_while_fighting", BoundVexWorldGameTests::crystalNotWhileFighting);
        padded(
                "bound_vex_world_crystal_staying_vex_only_in_its_area",
                BoundVexWorldGameTests::crystalStayingVexOnlyInItsArea);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_world_crystal_gives_up_after_200_ticks",
                CRYSTAL_LONG_BODY,
                BoundVexWorldGameTests::crystalGivesUpAfter200Ticks);
        padded("bound_vex_world_crystal_blast_by_a_player", BoundVexWorldGameTests::crystalBlastByAPlayer);
        padded(
                "bound_vex_world_crystal_blast_spares_another_players_vex",
                BoundVexWorldGameTests::crystalBlastSparesAnotherPlayersVex);
        FangGameTests.addPadded(
                SERVER_STOP_CRYSTALS,
                "bound_vex_world_crystal_claims_cleared_on_server_stop",
                BoundVexWorldGameTests::crystalClaimsClearedOnServerStop);
        padded("bound_vex_world_fireball_returned_to_the_ghast", BoundVexWorldGameTests::fireballReturnedToTheGhast);
        padded(
                "bound_vex_world_fireball_reversed_without_a_ghast",
                BoundVexWorldGameTests::fireballReversedWithoutAGhast);
        padded(
                "bound_vex_world_fireball_reversed_when_the_ghast_is_hidden",
                BoundVexWorldGameTests::fireballReversedWhenTheGhastIsHidden);
        padded(
                "bound_vex_world_fireball_block_hit_near_you",
                helper -> fireballHitNearPlayer(helper, floorHit(3.0), Outcome.RETURNED));
        padded(
                "bound_vex_world_fireball_block_hit_far_from_you",
                helper -> fireballHitNearPlayer(helper, floorHit(6.0), Outcome.LEFT_ALONE));
        padded(
                "bound_vex_world_fireball_block_hit_at_eye_height_near_your_body",
                helper -> fireballHitNearPlayer(helper, eyeHeightHit(3.9), Outcome.RETURNED));
        padded(
                "bound_vex_world_fireball_entity_hit_near_you",
                helper -> fireballHitNearPlayer(helper, huskHit(3.0), Outcome.RETURNED));
        padded(
                "bound_vex_world_fireball_entity_hit_far_from_you",
                helper -> fireballHitNearPlayer(helper, huskHit(6.0), Outcome.LEFT_ALONE));
        padded(
                "bound_vex_world_fireball_left_alone_when_you_own_it",
                helper -> fireballLeftAlone(helper, Alone.OWNED_BY_THE_PLAYER));
        padded(
                "bound_vex_world_fireball_left_alone_with_no_vex_near",
                helper -> fireballLeftAlone(helper, Alone.VEX_OUT_OF_REACH));
        padded(
                "bound_vex_world_fireball_left_alone_for_a_spectator",
                helper -> fireballLeftAlone(helper, Alone.SPECTATOR));
        padded(
                "bound_vex_world_fireball_left_alone_in_the_overworld",
                helper -> fireballLeftAlone(helper, Alone.OVERWORLD));
        padded(
                "bound_vex_world_fireball_left_alone_with_a_strangers_vex",
                helper -> fireballLeftAloneWithAStrangersVex(helper, true));
        padded(
                "bound_vex_world_fireball_left_alone_with_an_unmarked_vex",
                helper -> fireballLeftAloneWithAStrangersVex(helper, false));
        padded(
                "bound_vex_world_fireball_passes_through_your_vexes",
                BoundVexWorldGameTests::fireballPassesThroughYourVexes);
        padded(
                "bound_vex_world_fireball_hits_another_players_vex",
                BoundVexWorldGameTests::fireballHitsAnotherPlayersVex);
        padded("bound_vex_world_fireball_of_a_ghast_hits_a_vex", BoundVexWorldGameTests::fireballOfAGhastHitsAVex);
        padded("bound_vex_world_fireball_nearest_vex_acts", BoundVexWorldGameTests::fireballNearestVexActs);
        padded("bound_vex_world_fireball_staying_vex_acts", BoundVexWorldGameTests::fireballStayingVexActs);
        padded("bound_vex_world_fireball_kills_the_ghast", BoundVexWorldGameTests::fireballKillsTheGhast);
        padded("bound_vex_world_crit_in_the_end", BoundVexWorldGameTests::critInTheEnd);
        padded("bound_vex_world_crit_not_for_arrows", BoundVexWorldGameTests::critNotForArrows);
        padded("bound_vex_world_crit_not_when_spared", BoundVexWorldGameTests::critNotWhenSpared);
        padded("bound_vex_world_crit_keeps_golem_mercy", BoundVexWorldGameTests::critKeepsGolemMercy);
        padded(
                "bound_vex_world_crit_on_a_player_under_the_pvp_rule",
                BoundVexWorldGameTests::critOnAPlayerUnderThePvpRule);
        padded("bound_vex_world_no_crit_in_the_nether", BoundVexWorldGameTests::noCritInTheNether);
        FangGameTests.addPadded(
                SERVER_STOP_RESCUES,
                "bound_vex_world_rescues_cleared_on_server_stop",
                BoundVexWorldGameTests::rescuesClearedOnServerStop);
    }

    /** Registers {@code body} in the shared environment with the padded box and its chunk wait. */
    private static void padded(String name, Consumer<GameTestHelper> body) {
        FangGameTests.addPadded(SHARED, name, body);
    }

    /**
     * When {@code room} builds a room in the End, should the player stand on stone in air and a Bound Vex spawned there
     * stay alive and know its summoner.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#room}
     */
    static void setupEndRoom(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);

        // Act
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = vexIn(helper, end, player.position().add(1, 1, 0), player);

        // Assert
        BlockPos feet = player.blockPosition();
        helper.assertTrue(player.level().equals(end), "the player is in the End room's level");
        helper.assertValueEqual(end.dimension(), Level.END, "the room's level is the End");
        helper.assertTrue(end.getBlockState(feet.below()).is(Blocks.STONE), "stone under the player's feet");
        helper.assertTrue(end.getBlockState(feet).isAir(), "air at the feet");
        helper.assertTrue(end.getBlockState(feet.above()).isAir(), "air at the head");
        helper.assertTrue(player.isAlive(), "the player is alive");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(vex.isAlive(), "the vex in the End is alive after 5 ticks");
            helper.assertTrue(vex.tickCount > 0, "the vex in the End ticks (its chunk is entity-ticking)");
            helper.assertTrue(vex.summoner().isPresent(), "the vex in the End knows its summoner");
            helper.succeed();
        });
    }

    /**
     * When {@code endVoid} places a player in the End, should every chunk within 32 blocks be loaded with nothing to
     * land on, and a {@code platform} become the first free Y of its column.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#endVoid}, {@link BoundVexWorldTestSupport#platform}
     */
    static void setupEndVoid(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);

        // Act
        ServerLevel end = endVoid(helper, player, 48);
        BlockPos at = player.blockPosition();

        // Assert
        for (int dx = -32; dx <= 32; dx += 8) {
            for (int dz = -32; dz <= 32; dz += 8) {
                helper.assertTrue(
                        end.hasChunk(
                                SectionPos.blockToSectionCoord(at.getX() + dx),
                                SectionPos.blockToSectionCoord(at.getZ() + dz)),
                        "the chunk at " + dx + "," + dz + " is loaded");
                helper.assertValueEqual(
                        end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX() + dx, at.getZ() + dz),
                        end.getMinY(),
                        "nothing to land on at " + dx + "," + dz);
            }
        }
        BlockPos center = at.offset(5, -3, 0);
        platform(helper, end, center, 1);
        helper.assertValueEqual(
                end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()),
                center.getY() + 1,
                "the platform's top + 1 is the column's height");
        BlockPos block = at.above(3);
        set(helper, end, block, Blocks.OBSIDIAN.defaultBlockState());
        helper.assertTrue(end.getBlockState(block).is(Blocks.OBSIDIAN), "set puts the block into the place");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(player.isAlive(), "the player in the void is alive after 5 ticks");
            helper.succeed();
        });
    }

    /**
     * When two players each ask for an End void in one test, should their places be at least 200 blocks apart.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#endVoid}
     */
    static void setupTwoPlacesAreApart(GameTestHelper helper) {
        // Arrange
        ServerPlayer first = FangGameTests.caster(helper, 0.0F);
        ServerPlayer second = BoundVexTestSupport.summoner(helper);

        // Act
        endVoid(helper, first, 16);
        endVoid(helper, second, 16);

        // Assert
        double sideways = Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
        helper.assertTrue(sideways >= 200.0, "two places are at least 200 blocks apart, not " + sideways);
        helper.succeed();
    }

    /**
     * When an End void with a platform, a set block, a vex and an armor stand ends (twice), should its slot be free
     * again, the floor,
     * platform and block restored, the chunks unforced, the entities removed and the player back in the test level.
     *
     * <p>Method under test: {@code BoundVexWorldTestSupport.Place.finish}
     */
    static void setupCleanupEndVoid(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        int free = freeSlots(helper, Level.END);
        ServerLevel end = endVoid(helper, player, 16);
        BlockPos slot = player.blockPosition();
        BlockPos platformAt = slot.offset(4, -3, 0);
        BlockPos block = slot.offset(0, 5, 3);
        platform(helper, end, platformAt, 1);
        set(helper, end, block, Blocks.LAVA.defaultBlockState());
        BoundVex vex = vexIn(helper, end, player.position().add(1, 1, 0), player);
        Entity stand = armorStandIn(end, player.position().add(-1, 1, 0));
        Place place = placeFor(helper, end, slot, "cleanup test");
        helper.assertValueEqual(freeSlots(helper, Level.END), free - 1, "the place took a slot");
        helper.assertTrue(forced(end, slot), "the place's chunk is forced");
        helper.assertTrue(end.getBlockState(slot.atY(1)).isAir(), "the floor is cleared under the slot");
        helper.assertTrue(end.getBlockState(platformAt).is(Blocks.STONE), "the platform is stone");
        helper.assertTrue(end.getBlockState(block).is(Blocks.LAVA), "the set block is lava");

        whenTicking(
                helper,
                stand,
                () -> { // a cleanup finds only entities of ticking chunks
                    int before = freeSlots(helper, Level.END); // other tests take and give slots between ticks

                    // Act
                    place.finish();

                    // Assert
                    assertEnded(helper, end, player, vex, stand, slot, before + 1);
                    helper.assertTrue(end.getBlockState(platformAt).isAir(), "the platform is gone");
                    helper.assertTrue(end.getBlockState(block).isAir(), "the set block is air again");

                    // Act again, as whenDone will when the test ends, after something else changed the block
                    end.setBlock(block, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
                    int afterFirst = freeSlots(helper, Level.END);
                    place.finish();

                    // Assert
                    helper.assertTrue(end.getBlockState(block).is(Blocks.GLASS), "the second cleanup undid nothing");
                    helper.assertValueEqual(
                            freeSlots(helper, Level.END), afterFirst, "the second cleanup released no slot");
                    end.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    helper.assertTrue(
                            player.position().distanceTo(helper.absoluteVec(FangGameTests.STAND)) < 0.01,
                            "the player is still back at STAND");
                    helper.succeed();
                });
    }

    /**
     * When a Nether room with a vex and an armor stand ends (twice), should its slot be free again, the shell
     * restored to what it was,
     * the chunks unforced, the entities removed and the player back in the test level.
     *
     * <p>Method under test: {@code BoundVexWorldTestSupport.Place.finish}
     */
    static void setupCleanupRoom(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        int free = freeSlots(helper, Level.NETHER);
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 2, 3);
        BlockPos slot = player.blockPosition();
        BoundVex vex = vexIn(helper, nether, player.position().add(1, 1, 0), player);
        Entity stand = armorStandIn(nether, player.position().add(-1, 1, 0));
        Place place = placeFor(helper, nether, slot, "cleanup test");
        BlockPos[] shell = {slot.below(), slot.offset(3, 1, 0), slot.offset(0, 3, -3), slot.offset(-3, 0, 3)};
        for (BlockPos pos : shell) {
            helper.assertTrue(nether.getBlockState(pos).is(Blocks.STONE), "the shell is stone at " + pos);
        }
        helper.assertValueEqual(freeSlots(helper, Level.NETHER), free - 1, "the room took a slot");

        whenTicking(
                helper,
                stand,
                () -> { // a cleanup finds only entities of ticking chunks
                    int before = freeSlots(helper, Level.NETHER); // other tests take and give slots between ticks

                    // Act
                    place.finish();

                    // Assert
                    assertEnded(helper, nether, player, vex, stand, slot, before + 1);
                    for (BlockPos pos : shell) {
                        helper.assertTrue(nether.getBlockState(pos).isAir(), "the shell is restored to air at " + pos);
                    }

                    // Act again, as whenDone will when the test ends
                    place.finish();

                    // Assert
                    assertEnded(helper, nether, player, vex, stand, slot, before + 1);
                    helper.succeed();
                });
    }

    /**
     * When a platform reaches out of its place, or sits in none, or a room is wider than allowed, should the helper
     * fail with a "setup:" message and change nothing.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#platform}, {@link BoundVexWorldTestSupport#room}
     */
    static void setupRejectsBadExtents(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        int free = freeSlots(helper, Level.NETHER);
        ServerLevel end = endVoid(helper, player, 4);
        BlockPos slot = player.blockPosition();
        BlockPos nearEdge = slot.offset(3, -3, 0);

        // Act & Assert
        assertSetupFailure(helper, () -> platform(helper, end, nearEdge, 3), "a platform past the reach");
        helper.assertTrue(end.getBlockState(nearEdge).isAir(), "the failed platform placed nothing");
        assertSetupFailure(
                helper, () -> platform(helper, end, slot.atY(end.getMaxY() + 5), 0), "a platform above the world");
        assertSetupFailure(helper, () -> platform(helper, end, slot.offset(50, 0, 0), 0), "a platform outside");
        assertSetupFailure(
                helper,
                () -> set(helper, end, slot.offset(0, 0, 50), Blocks.STONE.defaultBlockState()),
                "a block outside");
        ServerPlayer other = BoundVexTestSupport.summoner(helper);
        assertSetupFailure(
                helper,
                () -> BoundVexWorldTestSupport.room(helper, other, Level.NETHER, 90, 3),
                "a room 181 blocks wide");
        helper.assertValueEqual(freeSlots(helper, Level.NETHER), free, "the rejected room took no slot");
        helper.succeed();
    }

    /**
     * When a following Bound Vex is in the Overworld by day, should its posture be the wide one and its scan interval
     * 10.
     *
     * <p>Method under test: {@code BoundVex.posture} and {@code BoundVex.scanInterval}
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

    /** A persistent husk with no AI and no gravity at {@code at} in {@code level}; the place's cleanup removes it. */
    private static Husk huskIn(ServerLevel level, Vec3 at) {
        Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setPersistenceRequired();
        husk.setPos(at);
        level.addFreshEntity(husk);
        return husk;
    }

    /** Fails the test unless {@code action} throws a GameTest failure whose message contains "setup:". */
    private static void assertSetupFailure(GameTestHelper helper, Runnable action, String what) {
        try {
            action.run();
        } catch (GameTestAssertException failure) {
            helper.assertTrue(failure.getMessage().contains("setup:"), what + " fails with a setup: message");
            return;
        }
        throw helper.assertionException(what + " should have failed");
    }

    /** Runs {@code next} once {@code entity} has ticked: its chunk is then entity-ticking, so a cleanup finds it. */
    private static void whenTicking(GameTestHelper helper, Entity entity, Runnable next) {
        if (entity.tickCount > 0) {
            next.run();
        } else {
            helper.runAfterDelay(1, () -> whenTicking(helper, entity, next));
        }
    }

    /** An armor stand at {@code at}: an entity only the cleanup removes (a Bound Vex may leave by itself). */
    private static Entity armorStandIn(ServerLevel level, Vec3 at) {
        Entity stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        stand.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(stand);
        return stand;
    }

    /** Whether the chunk containing {@code pos} is forced in {@code level}. */
    private static boolean forced(ServerLevel level, BlockPos pos) {
        return level.getForceLoadedChunks().contains(ChunkPos.pack(pos));
    }

    /** Asserts everything a finished place must have undone, apart from the blocks only the caller knows about. */
    private static void assertEnded(
            GameTestHelper helper,
            ServerLevel level,
            ServerPlayer player,
            BoundVex vex,
            Entity stand,
            BlockPos slot,
            int expectedFree) {
        helper.assertValueEqual(
                freeSlots(helper, level.dimension()), expectedFree, "the slot is free again in " + level.dimension());
        for (int dx = -16; dx <= 16; dx += 16) {
            helper.assertFalse(forced(level, slot.offset(dx, 0, 0)), "the chunk at dx " + dx + " is no longer forced");
        }
        helper.assertTrue(vex.isRemoved(), "the vex is removed");
        helper.assertTrue(stand.isRemoved(), "the armor stand is removed");
        helper.assertTrue(player.level().equals(helper.getLevel()), "the player is back in the test level");
        helper.assertTrue(
                player.position().distanceTo(helper.absoluteVec(FangGameTests.STAND)) < 0.01,
                "the player is back at STAND");
        if (level.dimension().equals(Level.END)) {
            for (int y = 0; y <= 3; y++) {
                for (BlockPos column : new BlockPos[] {slot, slot.offset(10, 0, -9), slot.offset(-12, 0, 12)}) {
                    helper.assertFalse(
                            level.getBlockState(column.atY(y)).isAir(), "the floor is back at " + column.atY(y));
                }
            }
        }
    }

    /**
     * When a mock player stands in lava, water or air and {@code soak} runs, should it report exactly that fluid.
     *
     * <p>Method under test: {@link BoundVexWorldTestSupport#soak}
     */
    static void setupFluids(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        for (BlockPos spot : new BlockPos[] {FEET, FEET.above()}) {
            helper.setBlock(spot.east(), Blocks.STONE);
            helper.setBlock(spot.west(), Blocks.STONE);
            helper.setBlock(spot.north(), Blocks.STONE);
            helper.setBlock(spot.south(), Blocks.STONE);
        }
        helper.setBlock(FEET.below(), Blocks.STONE);

        // Act & Assert
        helper.setBlock(FEET, Blocks.LAVA);
        helper.setBlock(FEET.above(), Blocks.LAVA);
        soak(player);
        helper.assertTrue(player.isInLava(), "a soaked player in lava is in lava");
        helper.setBlock(FEET, Blocks.WATER);
        helper.setBlock(FEET.above(), Blocks.WATER);
        soak(player);
        helper.assertTrue(player.isUnderWater(), "a soaked player in water is under water");
        helper.setBlock(FEET, Blocks.AIR);
        helper.setBlock(FEET.above(), Blocks.AIR);
        soak(player);
        helper.assertFalse(player.isInLava() || player.isUnderWater(), "a soaked player in air is in neither");
        helper.succeed();
    }

    /**
     * When the test runs in the night environment, should the Overworld be dark after two ticks.
     *
     * <p>Method under test: {@code OverworldTime} (the {@code NIGHT} environment)
     */
    static void setupNight(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getLevel().isDarkOutside(), "it is dark in the night environment");
            helper.succeed();
        });
    }

    /**
     * When the test runs in the shared environment, should the Overworld not be dark after two ticks.
     *
     * <p>Method under test: {@code OverworldTime} (the {@code SHARED} environment)
     */
    static void setupDay(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(helper.getLevel().isDarkOutside(), "it is not dark in the shared environment");
            helper.succeed();
        });
    }

    // ---- Task 4: the lava pull and the Nether's water bucket ----

    /**
     * The lava arrangement of the Task 4 tests: a survival player (made unhurtable) at {@link FangGameTests#STAND},
     * facing south, in a two-block column of lava inside a solid mass of stone, so no air touches the lava (lava
     * spreads into air) and the only standable spot within the shore search is the shore this method carves, if any.
     * The shore is two blocks east of the player, {@code shoreDrop} blocks below their feet level (negative: above it):
     * stone under it, two blocks of air over it.
     *
     * @param helper the test helper
     * @param shoreDrop how far below the player feet level the shore feet block is, or {@link #NO_SHORE}
     * @return the player
     */
    private static ServerPlayer lavaPool(GameTestHelper helper, int shoreDrop) {
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        for (int x = -POOL_REACH; x <= POOL_REACH; x++) {
            for (int z = -POOL_REACH; z <= POOL_REACH; z++) {
                for (int y = -POOL_DEPTH; y <= 2; y++) {
                    helper.setBlock(FEET.offset(x, y, z), Blocks.STONE);
                }
            }
        }
        if (shoreDrop != NO_SHORE) {
            BlockPos shore = FEET.east(2).below(shoreDrop);
            helper.setBlock(shore, Blocks.AIR);
            helper.setBlock(shore.above(), Blocks.AIR);
        }
        fillPool(helper, Blocks.LAVA);
        return player;
    }

    /** Fills the player's two-block column with {@code block} (lava, or air to drain it). */
    private static void fillPool(GameTestHelper helper, Block block) {
        helper.setBlock(FEET, block);
        helper.setBlock(FEET.above(), block);
    }

    /** One server step of a mock player: the fluid state it would have, then the rescue tick (one per server tick). */
    private static void step(ServerPlayer player) {
        soak(player);
        VexRescues.tick(player);
    }

    /** A following vex of {@code player} five blocks from them (three up, four south), above the stone. */
    private static BoundVex followingVex(GameTestHelper helper, ServerPlayer player) {
        return BoundVexTestSupport.followOnly(helper, FEET.offset(0, 3, 4), player);
    }

    /** Fails the test unless the player's velocity is {@code (x, y, z)} within 1e-6. */
    private static void assertVelocity(GameTestHelper helper, ServerPlayer player, double x, double y, double z) {
        Vec3 v = player.getDeltaMovement();
        helper.assertTrue(
                Math.abs(v.x - x) < 1.0e-6 && Math.abs(v.y - y) < 1.0e-6 && Math.abs(v.z - z) < 1.0e-6,
                "the velocity is (" + x + ", " + y + ", " + z + "), not " + v);
    }

    /** Fails the test unless a lava rescue is running for {@code player}. */
    private static void assertLavaRunning(GameTestHelper helper, ServerPlayer player, String what) {
        helper.assertValueEqual(VexRescues.running(player), Optional.of(VexRescues.Kind.LAVA), what);
    }

    /** Fails the test unless no rescue is running for {@code player}. */
    private static void assertNoRescue(GameTestHelper helper, ServerPlayer player, String what) {
        helper.assertTrue(VexRescues.running(player).isEmpty(), what);
    }

    /** Drains the pool and puts the player on the shore, standing on the ground. */
    private static void walkOut(GameTestHelper helper, ServerPlayer player) {
        fillPool(helper, Blocks.AIR);
        MockPlayers.moveTo(helper, player, SHORE);
        player.setOnGround(true);
    }

    /** Fills the pool with lava again and puts the player back in it. */
    private static void walkBack(GameTestHelper helper, ServerPlayer player) {
        fillPool(helper, Blocks.LAVA);
        MockPlayers.moveTo(helper, player, FangGameTests.STAND);
        player.setOnGround(false);
    }

    /**
     * When a player stands in lava with a shore two blocks east and a following vex five blocks away, should one step
     * start a lava rescue, push them {@code (0.1, 0.3, 0)}, mark the velocity for sending and put the vex at their
     * side.
     *
     * <p>Method under test: {@code VexRescues.tick}, {@code RescuePush.lavaPull} through it
     */
    static void lavaPull(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);

        // Act
        step(player);

        // Assert
        assertLavaRunning(helper, player, "the lava rescue started");
        assertVelocity(helper, player, 0.1, 0.3, 0.0);
        helper.assertTrue(player.syncVelocity, "the velocity is marked for sending");
        Vec3 side = player.position().add(-0.6, 1.2, 0.0);
        helper.assertTrue(
                vex.position().distanceTo(side) <= SIDE_TOLERANCE,
                "the vex hangs at the player's side " + side + ", not " + vex.position());
        helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(vex)), "the vex is the rescuer");
        helper.succeed();
    }

    /**
     * When the client last reported the player moving south at 0.2, should the lava pull keep that steering, slowed
     * by the lava drag: velocity {@code (0.1, 0.3, 0.1)}.
     *
     * <p>Method under test: {@code VexRescues.tick}: the push reads {@code getKnownMovement}
     */
    static void lavaPullKeepsYourSteering(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        player.setKnownMovement(new Vec3(0, 0, 0.2));

        // Act
        step(player);

        // Assert
        assertVelocity(helper, player, 0.1, 0.3, 0.1);
        helper.succeed();
    }

    /**
     * When the player gets out onto the shore, should the rescue end at once, no new one start for 19 more steps and
     * a new one start on the 20th step after the ending.
     *
     * <p>Method under test: {@code VexRescues.tick}: the end on the ground and the 20-tick pause
     */
    static void lavaPullEndsOnTheShore(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        long[] ended = {0};

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    walkOut(helper, player);
                    step(player);
                    assertNoRescue(helper, player, "the rescue ends when the player stands on the shore");
                    ended[0] = helper.getLevel().getGameTime();
                    walkBack(helper, player);
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    step(player);
                    assertNoRescue(helper, player, "no rescue starts during the pause");
                })
                .thenExecute(() -> {
                    step(player);
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - ended[0], 20L, "setup: this is the 20th tick after");
                    assertLavaRunning(helper, player, "a rescue starts again 20 ticks after the ending");
                })
                .thenSucceed();
    }

    /**
     * When the player is held in lava, should the rescue run through the 60th step, end on the 61st, and (the pause
     * being 20 ticks) start again on the 20th step after that.
     *
     * <p>Method under test: {@code VexRescues.tick}: the 60-tick limit
     */
    static void lavaPullPausesAfter60(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        long[] marks = {0, 0};

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "the first step starts the rescue");
                    marks[0] = helper.getLevel().getGameTime();
                })
                .thenIdle(1)
                .thenExecuteFor(59, () -> {
                    step(player);
                    assertLavaRunning(helper, player, "the rescue runs through the 60th step");
                })
                .thenExecute(() -> {
                    step(player);
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - marks[0], 60L, "setup: this is the 61st step");
                    assertNoRescue(helper, player, "the 61st step ends the rescue");
                    marks[1] = helper.getLevel().getGameTime();
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    step(player);
                    assertNoRescue(helper, player, "no rescue starts during the pause");
                })
                .thenExecute(() -> {
                    step(player);
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - marks[1], 20L, "setup: this is the 20th tick after");
                    assertLavaRunning(helper, player, "a rescue starts again 20 ticks after the ending");
                })
                .thenSucceed();
    }

    /**
     * When the only standable spot is two blocks east and four blocks lower (out of the shore search), should the
     * pull be the lift: velocity {@code (0, 0.4, 0)}.
     *
     * <p>Method under test: {@code StandableSpots.shore} through {@code VexRescues.tick}
     */
    static void lavaPullIgnoresALowShore(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 4);
        followingVex(helper, player);

        // Act
        step(player);

        // Assert
        assertLavaRunning(helper, player, "the rescue started");
        assertVelocity(helper, player, 0.0, 0.4, 0.0);
        helper.succeed();
    }

    /** Gives {@code player} fire resistance. */
    private static void fireResistant(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200));
    }

    /**
     * When a player in lava has {@code unhurt} applied (or the only vex in range is staying), should a step start no
     * rescue and leave their velocity alone.
     *
     * <p>Method under test: {@code VexRescues.tick}: who gets the lava pull
     *
     * @param helper the test helper
     * @param unhurt what makes the player a poor candidate
     * @param stayingVex whether the vex in range is told to stay
     */
    private static void lavaPullNotForTheUnhurt(
            GameTestHelper helper, Consumer<ServerPlayer> unhurt, boolean stayingVex) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);
        vex.setStaying(stayingVex);
        unhurt.accept(player);
        Vec3 before = player.getDeltaMovement();

        // Act
        step(player);

        // Assert
        assertNoRescue(helper, player, "no rescue starts");
        helper.assertValueEqual(player.getDeltaMovement(), before, "the velocity is untouched");
        helper.succeed();
    }

    /**
     * When a player in lava rides a pig, should a step start no rescue and leave their velocity alone.
     *
     * <p>Method under test: {@code VexRescues.tick}: passengers are not rescued
     */
    static void lavaPullNotForPassengers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        Mob pig = helper.spawn(EntityTypes.PIG, FEET);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setPermanentlyInvulnerable(true);
        whenDone(helper, pig::discard);
        helper.assertTrue(player.startRiding(pig), "setup: the mock player can ride");
        soak(player);
        helper.assertTrue(player.isInLava() && player.isPassenger(), "setup: a passenger in lava");
        Vec3 before = player.getDeltaMovement();

        // Act
        step(player);

        // Assert
        assertNoRescue(helper, player, "no rescue starts");
        helper.assertValueEqual(player.getDeltaMovement(), before, "the velocity is untouched");
        helper.succeed();
    }

    /**
     * A persistent-looking husk with no AI and no gravity that nothing in these tests can kill (1000 health), at
     * {@code relative}; discarded when the test ends.
     */
    private static Husk sturdyHusk(GameTestHelper helper, BlockPos relative) {
        Husk husk = BoundVexTestSupport.husk(helper, relative);
        husk.setNoGravity(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        husk.setHealth(1000.0F);
        return husk;
    }

    /**
     * When a full-AI rescuer is fighting a husk that is within the vex reach (7.25) of the player side point, should
     * it still have that target four ticks into the rescue.
     *
     * <p>Method under test: {@code RescueGoal} and {@code VexRescues.start}: the move keeps a target in reach
     */
    static void lavaRescuerKeepsItsTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = BoundVexTestSupport.boundVex(helper, FEET.offset(0, 3, 4), player);
        Husk husk = sturdyHusk(helper, FEET.offset(0, 4, 3));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(husk.equals(vex.getTarget()), "setup: the vex fights the husk"))
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                    helper.assertTrue(
                            husk.position().distanceTo(player.position().add(-0.6, 1.2, 0.0)) < 7.25,
                            "setup: the husk is within reach of the side point");
                })
                .thenExecuteAfter(4, () -> helper.assertTrue(husk.equals(vex.getTarget()), "the vex kept its target"))
                .thenSucceed();
    }

    /**
     * When a full-AI rescuer is fighting a husk 14 blocks from the player, should it have dropped that target four
     * ticks into the rescue: from the player side the husk is out of its reach, and the vex own rules drop it.
     *
     * <p>Method under test: {@code VexRescues.start}: the move does not carry a far target along
     */
    static void lavaRescuerDropsATargetOutOfReach(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        Husk husk = sturdyHusk(helper, FEET.offset(10, 3, 10));
        BoundVex vex = BoundVexTestSupport.boundVex(helper, FEET.offset(8, 3, 8), player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(husk.equals(vex.getTarget()), "setup: the vex fights the husk"))
                .thenExecute(() -> {
                    helper.assertTrue(husk.distanceTo(player) > 13.0, "setup: the husk is 14 blocks off");
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenExecuteAfter(
                        4,
                        () -> helper.assertFalse(
                                husk.equals(vex.getTarget()), "the vex dropped the target out of its reach"))
                .thenSucceed();
    }

    /**
     * When a player with a running rescue logs out, should the rescue be gone and the rescuer goal let go: the vex is
     * held at the side point before, and free (not snapped back) after.
     *
     * <p>Method under test: {@code VexRescues.onLogout}, {@code RescueGoal}
     */
    static void rescueDroppedOnLogout(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);
        Vec3[] away = new Vec3[1];

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenExecuteAfter(2, () -> {
                    Vec3 side = player.position().add(-0.6, 1.2, 0.0);
                    helper.assertTrue(vex.position().distanceTo(side) <= SIDE_TOLERANCE, "held at the side point");
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    assertNoRescue(helper, player, "the logout drops the rescue");
                    away[0] = side.add(0, 3, 0);
                    vex.setPos(away[0]);
                })
                .thenExecuteAfter(
                        2,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(away[0]) < 1.0, "the goal let go: the vex is free"))
                .thenSucceed();
    }

    /**
     * When the vex is mid-flight from a hissing creeper (its flee goal runs), should the lava rescue still pick it
     * and hold it at the player's side two ticks later.
     *
     * <p>Method under test: {@code RescueGoal} (priority -1) against {@code FleeCreeperGoal}
     */
    static void lavaPullTakesAFleeingVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        Creeper creeper = BoundVexTestSupport.patientCreeper(helper, FEET.offset(0, 3, 2));
        creeper.setNoGravity(true);
        BoundVex vex = BoundVexTestSupport.boundVex(helper, FEET.offset(0, 3, 4), player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> creeper.setSwellDir(1))
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.goalSelector.getAvailableGoals().stream()
                                .anyMatch(g -> g.isRunning() && g.getGoal() instanceof FleeCreeperGoal),
                        "setup: the flee goal runs"))
                .thenExecute(() -> {
                    step(player);
                    helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(vex)), "the fleeing vex rescues");
                })
                .thenExecuteAfter(2, () -> {
                    Vec3 side = player.position().add(-0.6, 1.2, 0.0);
                    helper.assertTrue(
                            vex.position().distanceTo(side) <= SIDE_TOLERANCE,
                            "the vex is at the player's side, not " + vex.position());
                })
                .thenSucceed();
    }

    /**
     * When the nearer of two vexes is the rescuer and it dies, should the next step drop the rescue and the one after
     * it make the other vex the rescuer.
     *
     * <p>Method under test: {@code VexRescues.tick}: a rescuer that no longer holds
     */
    static void lavaPullReplacesADeadRescuer(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex near = BoundVexTestSupport.followOnly(helper, FEET.offset(0, 3, 2), player);
        BoundVex far = BoundVexTestSupport.followOnly(helper, FEET.offset(0, 3, 6), player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(near)), "the nearer vex rescues");
                    near.kill(helper.getLevel());
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    step(player);
                    assertNoRescue(helper, player, "the dead rescuer's rescue is dropped");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    step(player);
                    helper.assertTrue(VexRescues.rescuer(player).equals(Optional.of(far)), "the other vex rescues");
                })
                .thenSucceed();
    }

    /**
     * When a burning player is pulled out of lava in the Overworld, should they still burn afterwards and the
     * rescuer still hold its sword: only the Nether has the bucket.
     *
     * <p>Method under test: {@code VexRescues.tick}: the end of a lava rescue by dimension
     */
    static void lavaOverworldKeepsBurning(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    walkOut(helper, player);
                    player.setRemainingFireTicks(100);
                    step(player);
                    assertNoRescue(helper, player, "the rescue ended on the shore");
                    helper.assertTrue(player.getRemainingFireTicks() > 0, "the player still burns");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the rescuer holds its sword, no bucket");
                })
                .thenSucceed();
    }

    /**
     * The Nether arrangement of the bucket tests: a room at a private place, the player in a two-block column of
     * lava with stone on every side, a following vex three blocks east.
     *
     * @return the vex; the player is {@code player}
     */
    private static BoundVex netherPool(GameTestHelper helper, ServerPlayer player) {
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 4, 5);
        BlockPos slot = player.blockPosition();
        for (BlockPos column : new BlockPos[] {slot, slot.above()}) {
            for (BlockPos side : new BlockPos[] {column.east(), column.west(), column.north(), column.south()}) {
                set(helper, nether, side, Blocks.STONE.defaultBlockState());
            }
        }
        set(helper, nether, slot.above(2), Blocks.STONE.defaultBlockState());
        set(helper, nether, slot, Blocks.LAVA.defaultBlockState());
        set(helper, nether, slot.above(), Blocks.LAVA.defaultBlockState());
        return BoundVexTestSupport.noTargets(
                vexIn(helper, nether, player.position().add(3, 3, 0), player));
    }

    /** Drains the Nether pool and puts the player on the floor three blocks east of it, on the ground. */
    private static void netherWalkOut(GameTestHelper helper, ServerPlayer player, BlockPos slot) {
        ServerLevel nether = player.level();
        set(helper, nether, slot, Blocks.AIR.defaultBlockState());
        set(helper, nether, slot.above(), Blocks.AIR.defaultBlockState());
        player.setPos(slot.getX() + 3.5, slot.getY(), slot.getZ() + 0.5);
        player.setOnGround(true);
    }

    /**
     * When a burning player is pulled out of lava in the Nether, should the fire go out, the rescuer hold a water
     * bucket (conjured) for a tail of 20 ticks in which nothing pushes the player, and then hold its sword again.
     *
     * <p>Method under test: {@code VexRescues.tick}: the Nether's bucket and its tail
     */
    static void lavaNetherBucket(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        BoundVex vex = netherPool(helper, player);
        BlockPos slot = player.blockPosition();
        long[] ended = {0};
        Vec3[] velocity = new Vec3[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started in the Nether");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    netherWalkOut(helper, player, slot);
                    player.setRemainingFireTicks(100);
                    step(player);
                    ended[0] = helper.getLevel().getGameTime();
                    velocity[0] = player.getDeltaMovement();
                    assertLavaRunning(helper, player, "the rescue lives on as a tail");
                    helper.assertTrue(player.getRemainingFireTicks() <= 0, "the fire is out");
                    helper.assertTrue(vex.getMainHandItem().is(Items.WATER_BUCKET), "the rescuer holds the bucket");
                    helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "the bucket is conjured");
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    step(player);
                    assertLavaRunning(helper, player, "the tail runs");
                    helper.assertValueEqual(player.getDeltaMovement(), velocity[0], "the tail pushes nothing");
                    helper.assertTrue(player.getRemainingFireTicks() <= 0, "the fire stays out");
                    helper.assertTrue(vex.getMainHandItem().is(Items.WATER_BUCKET), "the bucket stays in the hand");
                })
                .thenExecute(() -> {
                    step(player);
                    assertNoRescue(helper, player, "the tail ends after 20 ticks");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
                })
                .thenSucceed();
    }

    /**
     * When a player is held in Nether lava for 61 steps, should the rescuer never hold a bucket and the player still
     * burn: the bucket is for a player who is out.
     *
     * <p>Method under test: {@code VexRescues.tick}: no bucket while in lava
     */
    static void lavaNetherNoBucketWhileInLava(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        BoundVex vex = netherPool(helper, player);
        Runnable check = () -> {
            step(player);
            helper.assertFalse(vex.getMainHandItem().is(Items.WATER_BUCKET), "the rescuer never holds a bucket");
            helper.assertTrue(player.getRemainingFireTicks() > 0, "the player still burns");
        };
        player.setRemainingFireTicks(1000); // lava ignites a moving entity, and a mock player never moves

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    check.run();
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecuteFor(59, check)
                .thenExecute(() -> {
                    check.run();
                    assertNoRescue(helper, player, "the 61st step ends the rescue");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is still in the hand");
                })
                .thenSucceed();
    }

    /**
     * When the player is out of lava but in the air for the 10th tick in a row, should the rescue end on that tick and
     * not the 9th, and no tick out of lava should push.
     *
     * <p>Method under test: {@code VexRescues.tick}: the 10-tick end and no push out of lava
     */
    static void lavaPullEndsAfterTenTicksOut(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        Vec3[] pushed = new Vec3[1];

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                    pushed[0] = player.getDeltaMovement();
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    fillPool(helper, Blocks.AIR);
                    player.setOnGround(false);
                    step(player);
                    assertLavaRunning(helper, player, "out tick 1: the rescue runs");
                    helper.assertValueEqual(player.getDeltaMovement(), pushed[0], "out tick 1: no push");
                })
                .thenIdle(1)
                .thenExecuteFor(8, () -> {
                    step(player);
                    assertLavaRunning(helper, player, "out ticks 2 to 9: the rescue runs");
                    helper.assertValueEqual(player.getDeltaMovement(), pushed[0], "out ticks 2 to 9: no push");
                })
                .thenExecute(() -> {
                    step(player);
                    assertNoRescue(helper, player, "the 10th tick out ends the rescue");
                })
                .thenSucceed();
    }

    /**
     * When the player is in lava and a vex of theirs is {@code distance} blocks away, should it be the rescuer if and
     * only if {@code chosen}.
     */
    private static void lavaRescuerAt(GameTestHelper helper, double distance, boolean chosen) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        BoundVex vex = followingVex(helper, player);
        double along = distance / Math.sqrt(2.0);
        vex.setPos(player.position().add(along, 0.0, along));

        // Act
        step(player);

        // Assert
        helper.assertValueEqual(
                VexRescues.rescuer(player).equals(Optional.of(vex)),
                chosen,
                "the vex " + distance + " blocks away is chosen: " + chosen);
        helper.succeed();
    }

    /**
     * When a vex is 15.5 blocks from the player in lava, should it be chosen (the range is 16).
     *
     * <p>Method under test: {@code VexRescues.start}: the rescuer range
     */
    static void lavaRescuerRangeInside(GameTestHelper helper) {
        lavaRescuerAt(helper, 15.5, true);
    }

    /**
     * When a vex is 17 blocks from the player in lava, should it not be chosen.
     *
     * <p>Method under test: {@code VexRescues.start}: the rescuer range
     */
    static void lavaRescuerRangeOutside(GameTestHelper helper) {
        lavaRescuerAt(helper, 17.0, false);
    }

    /**
     * When the only standable spot is {@code drop} blocks below the player feet level (negative: above), should the
     * pull go toward it {@code (0.1, 0.3, 0)} if {@code pulls}, and lift {@code (0, 0.4, 0)} otherwise.
     */
    private static void lavaShoreAt(GameTestHelper helper, int drop, boolean pulls) {
        // Arrange
        ServerPlayer player = lavaPool(helper, drop);
        followingVex(helper, player);

        // Act
        step(player);

        // Assert
        assertLavaRunning(helper, player, "the rescue started");
        if (pulls) {
            assertVelocity(helper, player, 0.1, 0.3, 0.0);
        } else {
            assertVelocity(helper, player, 0.0, 0.4, 0.0);
        }
        helper.succeed();
    }

    /**
     * When the shore is two blocks above the player feet, should the pull go toward it.
     *
     * <p>Method under test: {@code StandableSpots.shore}: the top of the height window
     */
    static void lavaShoreTwoUp(GameTestHelper helper) {
        lavaShoreAt(helper, -2, true);
    }

    /**
     * When the shore is one block below the player feet, should the pull go toward it.
     *
     * <p>Method under test: {@code StandableSpots.shore}: the bottom of the height window
     */
    static void lavaShoreOneDown(GameTestHelper helper) {
        lavaShoreAt(helper, 1, true);
    }

    /**
     * When the shore is three blocks above the player feet, should the pull lift and not go toward it.
     *
     * <p>Method under test: {@code StandableSpots.shore}: just over the top of the height window
     */
    static void lavaShoreThreeUp(GameTestHelper helper) {
        lavaShoreAt(helper, -3, false);
    }

    /**
     * When the player gains Fire Resistance during the rescue, should the rescue end on the next step.
     *
     * <p>Method under test: {@code VexRescues.tick}: the player stops needing the rescue
     */
    static void lavaPullEndsWithFireResistance(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                    fireResistant(player);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    step(player);
                    assertNoRescue(helper, player, "the rescue ends once the player is fire resistant");
                })
                .thenSucceed();
    }

    /**
     * When the pushed velocity plus 0.02 toward the shore (the player steering) is fed back as the known movement for
     * 40 ticks, should the sideways speed never pass 0.35.
     *
     * <p>Method under test: {@code RescuePush.lavaPull} through {@code VexRescues.tick}: the drag and the cap
     */
    static void lavaPullNeverPassesTheCap(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        double[] fastest = {0.0};

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(40, () -> {
                    step(player);
                    Vec3 v = player.getDeltaMovement();
                    fastest[0] = Math.max(fastest[0], Math.hypot(v.x, v.z));
                    helper.assertTrue(Math.hypot(v.x, v.z) <= 0.35 + 1.0e-9, "the sideways speed passed the cap: " + v);
                    player.setKnownMovement(v.add(0.02, 0.0, 0.0));
                })
                .thenExecute(() -> {
                    assertLavaRunning(helper, player, "setup: the rescue ran the whole time");
                    helper.assertTrue(fastest[0] > 0.1, "setup: the player did move toward the shore");
                })
                .thenSucceed();
    }

    /**
     * A Nether tail: the rescue ends on the shore and the rescuer holds the bucket; then {@code interrupt} happens, one
     * more step runs, and the rescue must be gone (and the sword back, when {@code handBack}).
     */
    private static void interruptedTail(
            GameTestHelper helper, BiConsumer<ServerPlayer, BoundVex> interrupt, boolean handBack) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        BoundVex vex = netherPool(helper, player);
        BlockPos slot = player.blockPosition();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    step(player);
                    assertLavaRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    netherWalkOut(helper, player, slot);
                    step(player);
                    helper.assertTrue(vex.getMainHandItem().is(Items.WATER_BUCKET), "setup: the tail holds the bucket");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    interrupt.accept(player, vex);
                    step(player);
                    assertNoRescue(helper, player, "the tail ends early");
                    if (handBack) {
                        helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the bucket is dismissed");
                    }
                })
                .thenSucceed();
    }

    /**
     * When the rescuer is told to stay during the tail, should the tail end and the bucket be dismissed.
     *
     * <p>Method under test: {@code VexRescues.tick}: a rescuer that no longer holds, in the tail
     */
    static void lavaTailEndsWhenTheRescuerStays(GameTestHelper helper) {
        interruptedTail(helper, (player, vex) -> vex.setStaying(true), true);
    }

    /**
     * When the player logs out during the tail, should the tail end and the bucket be dismissed.
     *
     * <p>Method under test: {@code VexRescues.onLogout}, in the tail
     */
    static void lavaTailEndsWhenThePlayerLogsOut(GameTestHelper helper) {
        interruptedTail(
                helper, (player, vex) -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)), true);
    }

    /**
     * When the rescuer dies during the tail, should the rescue be dropped.
     *
     * <p>Method under test: {@code VexRescues.tick}: a dead rescuer, in the tail
     */
    static void lavaTailEndsWhenTheRescuerDies(GameTestHelper helper) {
        interruptedTail(
                helper, (player, vex) -> vex.kill(helper.getLevel().getServer().getLevel(Level.NETHER)), false);
    }

    /**
     * When the server stops with a rescue running, should no rescue run afterwards.
     *
     * <p>Method under test: {@code BoundVexEvents.onServerStopped}, {@code VexRescues.clear}
     */
    static void rescuesClearedOnServerStop(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = lavaPool(helper, 0);
        followingVex(helper, player);
        step(player);
        assertLavaRunning(helper, player, "setup: the rescue started");

        // Act
        BoundVexEvents.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));

        // Assert
        assertNoRescue(helper, player, "the server stop drops the rescue");
        helper.succeed();
    }

    // ---- Task 5: the drowning push ----

    /**
     * The water arrangement of the Task 5 tests: a survival player (made unhurtable) at {@link FangGameTests#STAND}
     * with water in their two blocks and {@code depth} blocks above their head, stone under and on all four sides of
     * every water block (water spreads into air), then air, or stone when {@code roofed}.
     *
     * @param helper the test helper
     * @param depth how many blocks of water stand above the player head block
     * @param roofed whether a stone block caps the column
     * @return the player
     */
    private static ServerPlayer waterColumn(GameTestHelper helper, int depth, boolean roofed) {
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        helper.setBlock(FEET.below(), Blocks.STONE);
        for (int up = 0; up <= 1 + depth; up++) {
            BlockPos at = FEET.above(up);
            helper.setBlock(at.east(), Blocks.STONE);
            helper.setBlock(at.west(), Blocks.STONE);
            helper.setBlock(at.north(), Blocks.STONE);
            helper.setBlock(at.south(), Blocks.STONE);
        }
        for (int up = 0; up <= 1 + depth; up++) {
            helper.setBlock(FEET.above(up), Blocks.WATER);
        }
        helper.setBlock(FEET.above(2 + depth), roofed ? Blocks.STONE : Blocks.AIR);
        return player;
    }

    /** One server step of a mock player under water: the fluid state, then the air supply, then the rescue tick. */
    private static void drownStep(ServerPlayer player, int air) {
        soak(player);
        player.setAirSupply(air);
        VexRescues.tick(player);
    }

    /** Fails the test unless a drowning rescue is running for {@code player}. */
    private static void assertDrowningRunning(GameTestHelper helper, ServerPlayer player, String what) {
        helper.assertValueEqual(VexRescues.running(player), Optional.of(VexRescues.Kind.DROWNING), what);
    }

    /**
     * When a player with 40 air left is under water with open water above and a following vex near, should one step
     * start a drowning rescue with the client movement slowed by the water drag and the upward speed at 0.4, and put
     * the vex at their side.
     *
     * <p>Method under test: {@code VexRescues.tick}, {@code RescuePush.drowningPush} through it
     */
    static void drowningPush(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        BoundVex vex = followingVex(helper, player);
        player.setKnownMovement(new Vec3(0.1, -0.2, 0.05));

        // Act
        drownStep(player, 40);

        // Assert
        assertDrowningRunning(helper, player, "the drowning rescue started");
        assertVelocity(helper, player, 0.08, 0.4, 0.04);
        helper.assertTrue(player.syncVelocity, "the velocity is marked for sending");
        Vec3 side = player.position().add(-0.6, 1.2, 0.0);
        helper.assertTrue(
                vex.position().distanceTo(side) <= SIDE_TOLERANCE, "the vex hangs at the side, not " + vex.position());
        helper.succeed();
    }

    /**
     * When {@code unhurt} is applied to a drowning player (or a stone roof caps the water, or the air is 100), should a
     * step start no rescue and leave their velocity alone.
     *
     * @param helper the test helper
     * @param unhurt what makes the player a poor candidate
     * @param roofed whether the column has a stone roof
     * @param air the air supply for the step
     */
    private static void drowningNotFor(GameTestHelper helper, Consumer<ServerPlayer> unhurt, boolean roofed, int air) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, roofed);
        followingVex(helper, player);
        unhurt.accept(player);
        Vec3 before = player.getDeltaMovement();

        // Act
        drownStep(player, air);

        // Assert
        assertNoRescue(helper, player, "no rescue starts");
        helper.assertValueEqual(player.getDeltaMovement(), before, "the velocity is untouched");
        helper.succeed();
    }

    /**
     * When a stone block caps the water, should no rescue start (a push would pin the player to the roof).
     *
     * <p>Method under test: {@code VexRescues.hasWayUp} through {@code tick}
     */
    static void drowningNotUnderACeiling(GameTestHelper helper) {
        drowningNotFor(helper, player -> {}, true, 40);
    }

    /**
     * When the player still has 100 air, should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: the low-air threshold
     */
    static void drowningNotWithAirLeft(GameTestHelper helper) {
        drowningNotFor(helper, player -> {}, false, 100);
    }

    /**
     * When the player has Water Breathing, should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: who gets the drowning push
     */
    static void drowningNotWithWaterBreathing(GameTestHelper helper) {
        drowningNotFor(
                helper, player -> player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200)), false, 40);
    }

    /**
     * When the player has Conduit Power, should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: who gets the drowning push
     */
    static void drowningNotWithConduitPower(GameTestHelper helper) {
        drowningNotFor(
                helper, player -> player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 1200)), false, 40);
    }

    /**
     * When the player is in creative mode, should no rescue start.
     *
     * <p>Method under test: {@code VexRescues.tick}: who gets the drowning push
     */
    static void drowningNotForCreative(GameTestHelper helper) {
        drowningNotFor(helper, player -> player.setGameMode(GameType.CREATIVE), false, 40);
    }

    /**
     * When a player under water rides a minecart (a pig would throw them off in the water), should a step start no
     * rescue and leave their velocity alone.
     *
     * <p>Method under test: {@code VexRescues.wantsDrowningPush}: passengers are not rescued
     */
    static void drowningNotForPassengers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);
        Entity cart = helper.spawn(EntityTypes.MINECART, FEET);
        cart.setNoGravity(true);
        cart.setPermanentlyInvulnerable(true);
        whenDone(helper, cart::discard);
        helper.assertTrue(player.startRiding(cart), "setup: the mock player can ride");
        soak(player);
        player.setAirSupply(40);
        helper.assertTrue(player.isUnderWater() && player.isPassenger(), "setup: a passenger under water");
        Vec3 before = player.getDeltaMovement();

        // Act
        VexRescues.tick(player);

        // Assert
        assertNoRescue(helper, player, "no rescue starts");
        helper.assertValueEqual(player.getDeltaMovement(), before, "the velocity is untouched");
        helper.succeed();
    }

    /**
     * When a player stands half a block up with lava in their lower block, air above it and water around their eyes
     * (lava next to water turns to obsidian, so the air keeps them apart), low on air, so that the lava pull and the
     * drowning push both apply, should a step start the lava rescue (the first need wins).
     *
     * <p>Method under test: {@code VexRescues.startFirstNeeded}: the order of the needs
     */
    static void lavaBeforeDrowning(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 0, false);
        helper.setBlock(FEET.above(), Blocks.AIR);
        helper.setBlock(FEET, Blocks.AIR);
        helper.setBlock(FEET, Blocks.LAVA);
        BlockPos eyes = FEET.above(2);
        helper.setBlock(eyes.east(), Blocks.STONE);
        helper.setBlock(eyes.west(), Blocks.STONE);
        helper.setBlock(eyes.north(), Blocks.STONE);
        helper.setBlock(eyes.south(), Blocks.STONE);
        helper.setBlock(eyes, Blocks.WATER);
        MockPlayers.moveTo(helper, player, FangGameTests.STAND.add(0.0, 0.5, 0.0));
        followingVex(helper, player);
        soak(player);
        player.setAirSupply(40);
        helper.assertTrue(player.isInLava() && player.isUnderWater(), "setup: in lava and under water at once");

        // Act
        VexRescues.tick(player);

        // Assert
        assertLavaRunning(helper, player, "the lava rescue started, not the drowning push");
        helper.succeed();
    }

    /**
     * When the player rises until their eyes are in air, should the rescue end on the next step.
     *
     * <p>Method under test: {@code VexRescues.drowning}: the end at the surface
     */
    static void drowningEndsAtTheSurface(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    MockPlayers.moveTo(helper, player, new Vec3(1.5, 5.0, 1.5));
                    drownStep(player, 40);
                    assertNoRescue(helper, player, "the rescue ends at the surface");
                })
                .thenSucceed();
    }

    /**
     * When stone closes the water above the player during the rescue, should the rescue end on the next step.
     *
     * <p>Method under test: {@code VexRescues.drowning}: the end when the way up closes
     */
    static void drowningEndsWhenTheWayUpCloses(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "setup: the rescue started");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.setBlock(FEET.above(3), Blocks.STONE);
                    drownStep(player, 40);
                    assertNoRescue(helper, player, "the rescue ends once the way up is closed");
                })
                .thenSucceed();
    }

    /**
     * When the player gains Water Breathing during the rescue, should the rescue end on the next step.
     *
     * <p>Method under test: {@code VexRescues.drowning}: the player stops needing the rescue
     */
    static void drowningEndsWithWaterBreathing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "setup: the rescue started");
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200));
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertNoRescue(helper, player, "the rescue ends once the player breathes water");
                })
                .thenSucceed();
    }

    /**
     * When the player is held under water, should the rescue run through the 200th step, end on the 201st, and start
     * again on the step after (drowning has no pause).
     *
     * <p>Method under test: {@code VexRescues.drowning}: the 200-tick limit
     */
    static void drowningEndsAfter200Ticks(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = waterColumn(helper, 1, false);
        followingVex(helper, player);
        long[] start = {0};

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "the first step starts the rescue");
                    start[0] = helper.getLevel().getGameTime();
                })
                .thenIdle(1)
                .thenExecuteFor(199, () -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "the rescue runs through the 200th step");
                })
                .thenExecute(() -> {
                    drownStep(player, 40);
                    helper.assertValueEqual(
                            helper.getLevel().getGameTime() - start[0], 200L, "setup: this is the 201st step");
                    assertNoRescue(helper, player, "the 201st step ends the rescue");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    drownStep(player, 40);
                    assertDrowningRunning(helper, player, "a new rescue starts at once: no pause");
                })
                .thenSucceed();
    }

    /**
     * When a player with 40 air is under water in a Nether room, should no rescue start (the rule is Overworld only).
     *
     * <p>Method under test: {@code VexRescues.tick}: the dimension check of the drowning push
     */
    static void drowningOnlyInTheOverworld(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 4, 5);
        BlockPos slot = player.blockPosition();
        for (int up = 0; up <= 2; up++) {
            BlockPos at = slot.above(up);
            for (BlockPos side : new BlockPos[] {at.east(), at.west(), at.north(), at.south()}) {
                set(helper, nether, side, Blocks.STONE.defaultBlockState());
            }
        }
        for (int up = 0; up <= 2; up++) {
            set(helper, nether, slot.above(up), Blocks.WATER.defaultBlockState());
        }
        BoundVex vex = BoundVexTestSupport.noTargets(
                vexIn(helper, nether, player.position().add(3, 3, 0), player));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    soak(player);
                    helper.assertTrue(player.isUnderWater(), "setup: the player is under water in the Nether");
                    drownStep(player, 40);
                    assertNoRescue(helper, player, "no drowning rescue in the Nether");
                })
                .thenSucceed();
    }

    // ---- Task 6: the void catch ----

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

    /** A survival player made unhurtable, at {@link FangGameTests#STAND}. */
    private static ServerPlayer voidPlayer(GameTestHelper helper) {
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        player.setPermanentlyInvulnerable(true);
        whenDone(helper, () -> player.setPermanentlyInvulnerable(false));
        return player;
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

    /** The point at height {@code y} in the column of {@code slot}. */
    private static Vec3 column(BlockPos slot, double y) {
        return new Vec3(slot.getX() + 0.5, y, slot.getZ() + 0.5);
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
     * <p>Method under test: {@code VexRescues.catchFromVoid}: the last ground must still be standable
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
     * <p>Method under test: {@code VexRescues.catchFromVoid}: vanilla's teleport dismounts the player (the catch
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
     * <p>Method under test: {@code VexRescues.catchFromVoid}: the 20-tick memory of a failed search
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
     * <p>Method under test: {@code VexRescues.needsVoidCatch}: the dimension check
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

    // ---- Task 7: the fireball return ----

    /** How far a fireball test's room reaches sideways (a ghast is 4 wide and stands 9 blocks off). */
    private static final int FIREBALL_ROOM_HALF_WIDTH = 12;

    /** How tall a fireball test's room is. */
    private static final int FIREBALL_ROOM_HEIGHT = 8;

    /** How far north of the player the ghast stands, in blocks. */
    private static final double GHAST_DISTANCE = 9.0;

    /** How far north of the player the fireball starts, in blocks. */
    private static final double FIREBALL_DISTANCE = 1.5;

    /** The speed of a fireball in these tests, in blocks per tick. */
    private static final double FIREBALL_SPEED = 1.5;

    /** How far east of the fireball the default vex hangs: well inside the vex reach of 7.25. */
    private static final double VEX_NEAR = 3.0;

    /** How far east of the fireball a vex hangs that is out of the vex reach. */
    private static final double VEX_FAR = 9.0;

    /** A survival player, at {@link FangGameTests#STAND} until a test moves them into a room. */
    private static ServerPlayer fireballPlayer(GameTestHelper helper) {
        return FangGameTests.caster(helper, 0.0F);
    }

    /** The Nether room of a fireball test, with {@code player} standing in it; undone when the test ends. */
    private static ServerLevel fireballRoom(GameTestHelper helper, ServerPlayer player) {
        return BoundVexWorldTestSupport.room(
                helper, player, Level.NETHER, FIREBALL_ROOM_HALF_WIDTH, FIREBALL_ROOM_HEIGHT);
    }

    /**
     * A ghast with no AI and no gravity {@link #GHAST_DISTANCE} blocks north of {@code player}, its body spanning
     * their eye height; discarded when the test ends.
     *
     * @param invulnerable whether nothing can hurt it (only the kill test wants it vulnerable)
     */
    private static Ghast ghastNorthOf(
            GameTestHelper helper, ServerLevel level, ServerPlayer player, boolean invulnerable) {
        Ghast ghast = EntityTypes.GHAST.create(level, EntitySpawnReason.COMMAND);
        if (ghast == null) {
            throw helper.assertionException("could not create a ghast");
        }
        Vec3 at = player.position().add(0.0, 0.5, -GHAST_DISTANCE);
        ghast.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        ghast.setNoAi(true);
        ghast.setNoGravity(true);
        ghast.setPersistenceRequired();
        ghast.setPermanentlyInvulnerable(invulnerable);
        level.addFreshEntity(ghast);
        whenDone(helper, ghast::discard);
        return ghast;
    }

    /** Where a fireball starts: at {@code player}'s eye height, {@link #FIREBALL_DISTANCE} blocks north of them. */
    private static Vec3 fireballSpot(ServerPlayer player) {
        return player.getEyePosition().add(0.0, 0.0, -FIREBALL_DISTANCE);
    }

    /**
     * A fireball of {@code ghast} (with no owner when {@code ghast} is null) at {@code at}, flying {@code motion}. It
     * is not in the level: the tests post its impact by hand.
     */
    private static LargeFireball fireballFlying(
            GameTestHelper helper, ServerLevel level, Ghast ghast, Vec3 at, Vec3 motion) {
        LargeFireball fireball = ghast != null
                ? new LargeFireball(level, ghast, new Vec3(0.0, 0.0, 1.0), 1)
                : EntityTypes.FIREBALL.create(level, EntitySpawnReason.COMMAND);
        if (fireball == null) {
            throw helper.assertionException("could not create a fireball");
        }
        fireball.setPos(at);
        fireball.setDeltaMovement(motion);
        return fireball;
    }

    /** A vex of {@code player} in {@code level} at {@code at}, with no AI and no targets: it stays where it is put. */
    private static BoundVex fireballVex(GameTestHelper helper, ServerLevel level, Vec3 at, ServerPlayer player) {
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, level, at, player));
        vex.setNoAi(true);
        return vex;
    }

    /** Waits until every one of {@code vexes} has ticked (entity queries see it only then), then runs {@code body}. */
    private static void afterVexesTick(GameTestHelper helper, Runnable body, BoundVex... vexes) {
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (BoundVex vex : vexes) {
                        helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks");
                    }
                })
                .thenExecute(body)
                .thenSucceed();
    }

    /** Posts the impact of {@code fireball} on {@code hit} as vanilla would; true means a handler cancelled it. */
    private static boolean impact(LargeFireball fireball, HitResult hit) {
        return EventHooks.onProjectileImpact(fireball, hit);
    }

    /** Fails the test unless {@code fireball} flies {@code (x, y, z)}, within 1e-6. */
    private static void assertFireballVelocity(
            GameTestHelper helper, LargeFireball fireball, double x, double y, double z) {
        Vec3 v = fireball.getDeltaMovement();
        helper.assertTrue(
                Math.abs(v.x - x) < 1.0e-6 && Math.abs(v.y - y) < 1.0e-6 && Math.abs(v.z - z) < 1.0e-6,
                "the fireball flies (" + x + ", " + y + ", " + z + "), not " + v);
    }

    /**
     * When a ghast's fireball is about to hit a player who has a Bound Vex 3 blocks from it and the ghast is in
     * sight, should the impact be cancelled, the summoner own the fireball, its speed stay 1.5, its heading point at
     * the ghast's eyes, and the vex hang at the fireball.
     *
     * <p>Method under test: {@code VexFireballs.onImpact}
     */
    static void fireballReturnedToTheGhast(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Ghast ghast = ghastNorthOf(helper, nether, player, true);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, ghast, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        fireball.accelerationPower = 0.5;
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    boolean cancelled = impact(fireball, new EntityHitResult(player));
                    helper.assertTrue(cancelled, "the impact is cancelled");
                    helper.assertValueEqual(fireball.accelerationPower, 0.1, "returned by an attack: acceleration 0.1");
                    helper.assertTrue(player.equals(fireball.getOwner()), "the summoner owns the fireball");
                    Vec3 velocity = fireball.getDeltaMovement();
                    helper.assertTrue(
                            Math.abs(velocity.length() - FIREBALL_SPEED) < 1.0e-6, "the speed is kept: " + velocity);
                    Vec3 toGhast = ghast.getEyePosition().subtract(fireball.position());
                    helper.assertTrue(
                            velocity.normalize().dot(toGhast.normalize()) > 0.999,
                            "the fireball flies at the ghast's eyes: " + velocity + " vs " + toGhast);
                    helper.assertTrue(
                            vex.position().distanceTo(fireball.position()) < 0.1,
                            "the vex hangs at the fireball, not " + vex.position());
                },
                vex);
    }

    /**
     * When a fireball with no ghast, flying {@code (0.3, 0, 1.2)}, is about to hit a player with a Bound Vex near,
     * should it fly straight back {@code (-0.3, 0, -1.2)} and belong to the summoner.
     *
     * <p>Method under test: {@code VexFireballs.sendBack}: no heading, straight back
     */
    static void fireballReversedWithoutAGhast(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.3, 0.0, 1.2));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(player)), "the impact is cancelled");
                    assertFireballVelocity(helper, fireball, -0.3, 0.0, -1.2);
                    helper.assertTrue(player.equals(fireball.getOwner()), "the summoner owns the fireball");
                },
                vex);
    }

    /**
     * When a stone wall stands between the vex and the ghast, should the fireball fly straight back, not at the ghast.
     *
     * <p>Method under test: {@code VexFireballs.sendBack}: the ghast must be in the vex's sight
     */
    static void fireballReversedWhenTheGhastIsHidden(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Ghast ghast = ghastNorthOf(helper, nether, player, true);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, ghast, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);
        BlockPos wall = BlockPos.containing(player.position().add(0.0, 0.0, -5.0));
        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y < FIREBALL_ROOM_HEIGHT; y++) {
                set(helper, nether, wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
            }
        }

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(player)), "the impact is cancelled");
                    assertFireballVelocity(helper, fireball, 0.0, 0.0, -FIREBALL_SPEED);
                },
                vex);
    }

    /** What a fireball impact should come to. */
    private enum Outcome {
        /** The impact is cancelled and the summoner owns the fireball. */
        RETURNED,
        /** The impact stands: no owner, same velocity. */
        LEFT_ALONE
    }

    /** Makes the hit a fireball test posts; run once the vexes have ticked. */
    @FunctionalInterface
    private interface HitMaker {
        /** The hit, for a player standing in {@code level}. */
        HitResult hit(GameTestHelper helper, ServerLevel level, ServerPlayer player);
    }

    /** A hit on the floor {@code east} blocks east of {@code player}'s feet. */
    private static HitMaker floorHit(double east) {
        return (helper, level, player) -> {
            Vec3 at = player.position().add(east, 0.0, 0.0);
            return new BlockHitResult(at, Direction.UP, BlockPos.containing(at).below(), false);
        };
    }

    /** A hit on a wall {@code east} blocks east of {@code player}, at their eye height. */
    private static HitMaker eyeHeightHit(double east) {
        return (helper, level, player) -> {
            Vec3 at = player.getEyePosition().add(east, 0.0, 0.0);
            return new BlockHitResult(at, Direction.WEST, BlockPos.containing(at), false);
        };
    }

    /** A hit on a husk with no AI standing {@code east} blocks east of {@code player}; discarded at the end. */
    private static HitMaker huskHit(double east) {
        return (helper, level, player) -> {
            Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            if (husk == null) {
                throw helper.assertionException("could not create a husk");
            }
            Vec3 at = player.position().add(east, 0.0, 0.0);
            husk.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            husk.setNoAi(true);
            husk.setNoGravity(true);
            husk.setPersistenceRequired();
            level.addFreshEntity(husk);
            whenDone(helper, husk::discard);
            return new EntityHitResult(husk);
        };
    }

    /**
     * When an ownerless fireball is about to land with the hit {@code maker} makes, near a player with a Bound Vex in
     * reach, should the impact come to {@code outcome}.
     *
     * <p>Method under test: {@code VexFireballs.onImpact}: {@link VexFireballs#NEAR_PLAYER}, measured to the body
     */
    private static void fireballHitNearPlayer(GameTestHelper helper, HitMaker maker, Outcome outcome) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);
        HitResult hit = maker.hit(helper, nether, player);

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    boolean cancelled = impact(fireball, hit);
                    if (outcome == Outcome.RETURNED) {
                        helper.assertTrue(cancelled, "the impact is cancelled");
                        helper.assertTrue(player.equals(fireball.getOwner()), "the summoner owns the fireball");
                    } else {
                        helper.assertTrue(!cancelled, "the impact is not cancelled");
                        helper.assertTrue(fireball.getOwner() == null, "the owner is untouched");
                        assertFireballVelocity(helper, fireball, 0.0, 0.0, FIREBALL_SPEED);
                    }
                },
                vex);
    }

    /**
     * The cases in which a fireball about to hit a player is left alone, whatever else is arranged for a return:
     * the player already owns it, the only vex is out of reach, the player is a spectator, or the level is the
     * Overworld test level.
     */
    private enum Alone {
        /** The player owns the fireball. */
        OWNED_BY_THE_PLAYER,
        /** The only vex is 9 blocks from the fireball. */
        VEX_OUT_OF_REACH,
        /** The player is a spectator. */
        SPECTATOR,
        /** The whole arrangement is in the Overworld test level, with no room. */
        OVERWORLD
    }

    /**
     * When one of the {@link Alone} cases holds, should the impact on the player not be cancelled and the fireball,
     * its owner and the vex stay as they were.
     *
     * <p>Method under test: {@code VexFireballs.onImpact}: the cases it leaves alone
     */
    private static void fireballLeftAlone(GameTestHelper helper, Alone why) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel level = why == Alone.OVERWORLD ? helper.getLevel() : fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, level, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        if (why == Alone.OWNED_BY_THE_PLAYER) {
            fireball.setOwner(player);
        }
        if (why == Alone.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        double away = why == Alone.VEX_OUT_OF_REACH ? VEX_FAR : VEX_NEAR;
        BoundVex vex = fireballVex(helper, level, spot.add(away, 0.0, 0.0), player);
        Vec3 vexBefore = vex.position();

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(!impact(fireball, new EntityHitResult(player)), "the impact is not cancelled");
                    helper.assertTrue(
                            why == Alone.OWNED_BY_THE_PLAYER
                                    ? player.equals(fireball.getOwner())
                                    : fireball.getOwner() == null,
                            "the owner is untouched");
                    assertFireballVelocity(helper, fireball, 0.0, 0.0, FIREBALL_SPEED);
                    helper.assertTrue(vex.position().distanceTo(vexBefore) < 1.0e-6, "the vex did not move");
                },
                vex);
    }

    /**
     * When the only vex in reach belongs to a second player, or carries no summoner mark, should the impact on the
     * first player not be cancelled and the vex stay where it is.
     *
     * <p>Method under test: {@code VexFireballs.nearestVexOf}: the vex must be summoned by that player
     *
     * @param marked true for a vex of a second mock player, false for an unmarked vex
     */
    private static void fireballLeftAloneWithAStrangersVex(GameTestHelper helper, boolean marked) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), stranger);
        if (!marked) {
            vex.removeData(AllyAttachments.SUMMONER);
        }
        Vec3 vexBefore = vex.position();

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(!impact(fireball, new EntityHitResult(player)), "the impact is not cancelled");
                    helper.assertTrue(fireball.getOwner() == null, "the owner is untouched");
                    helper.assertTrue(vex.position().distanceTo(vexBefore) < 1.0e-6, "the vex did not move");
                },
                vex);
    }

    /**
     * When a fireball its summoner owns is about to hit one of that summoner's Bound Vexes, should the impact be
     * cancelled and the fireball keep its owner and velocity (vanilla then flies it on past the vex).
     *
     * <p>Method under test: {@code VexFireballs.strikesOwnersVex}
     */
    static void fireballPassesThroughYourVexes(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        fireball.setOwner(player);
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);
        float health = vex.getHealth();

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(vex)), "the impact is cancelled");
                    helper.assertTrue(player.equals(fireball.getOwner()), "the owner is untouched");
                    assertFireballVelocity(helper, fireball, 0.0, 0.0, FIREBALL_SPEED);
                    helper.assertValueEqual(vex.getHealth(), health, "the vex is unhurt");
                },
                vex);
    }

    /**
     * When a fireball a player owns is about to hit another player's Bound Vex, should the impact stand.
     *
     * <p>Method under test: {@code VexFireballs.strikesOwnersVex}: only the owner's own vexes
     */
    static void fireballHitsAnotherPlayersVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        fireball.setOwner(player);
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), stranger);

        // Act & Assert
        afterVexesTick(
                helper, () -> helper.assertTrue(!impact(fireball, new EntityHitResult(vex)), "the impact stands"), vex);
    }

    /**
     * When a ghast's fireball is about to hit a Bound Vex with no player near it, should the impact stand.
     *
     * <p>Method under test: {@code VexFireballs.strikesOwnersVex}: only a player's fireball passes through
     */
    static void fireballOfAGhastHitsAVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Ghast ghast = ghastNorthOf(helper, nether, player, true);
        Vec3 far = player.position().add(10.0, 1.0, 0.0);
        LargeFireball fireball = fireballFlying(helper, nether, ghast, far, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, far.add(0.0, 0.0, 1.0), player);

        // Act & Assert
        afterVexesTick(
                helper, () -> helper.assertTrue(!impact(fireball, new EntityHitResult(vex)), "the impact stands"), vex);
    }

    /**
     * When two Bound Vexes of a player are 2 and 5 blocks from a fireball about to hit them, should only the nearer
     * one go to the fireball.
     *
     * <p>Method under test: {@code VexFireballs.nearestVexOf}
     */
    static void fireballNearestVexActs(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex nearer = fireballVex(helper, nether, spot.add(-2.0, 0.0, 0.0), player);
        BoundVex farther = fireballVex(helper, nether, spot.add(5.0, 0.0, 0.0), player);
        Vec3 fartherBefore = farther.position();

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(player)), "the impact is cancelled");
                    helper.assertTrue(
                            nearer.position().distanceTo(spot) < 0.1,
                            "the nearer vex hangs at the fireball, not " + nearer.position());
                    helper.assertTrue(
                            farther.position().distanceTo(fartherBefore) < 1.0e-6, "the farther vex did not move");
                },
                nearer,
                farther);
    }

    /**
     * When the only vex in reach is staying at its post, should it still return the fireball and then fly back to
     * within a block of its post point.
     *
     * <p>Method under test: {@code VexFireballs.onImpact}: a staying vex acts too
     */
    static void fireballStayingVexActs(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player));
        vex.setStaying(true);
        Vec3 post = Vec3.atBottomCenterOf(vex.blockPosition());

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(player)), "the impact is cancelled");
                    helper.assertTrue(
                            vex.position().distanceTo(spot) < 0.1, "the vex hangs at the fireball for the moment");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.position().distanceTo(post) < 1.0, "the vex flew back to its post, not " + vex.position()))
                .thenSucceed();
    }

    /**
     * When a ghast's fireball is returned by a vex and then flies on in the level, should it kill the (vulnerable)
     * ghast with the one hit vanilla gives a fireball whose owner is a player.
     *
     * <p>Method under test: {@code VexFireballs.sendBack}: the summoner becomes the owner
     */
    static void fireballKillsTheGhast(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Ghast ghast = ghastNorthOf(helper, nether, player, false);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, ghast, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    helper.assertTrue(nether.addFreshEntity(fireball), "setup: the fireball joins the level");
                    whenDone(helper, fireball::discard);
                })
                .thenWaitUntil(() -> helper.assertTrue(!ghast.isAlive(), "the returned fireball killed the ghast"))
                .thenSucceed();
    }

    /**
     * The arrangement of the Task 6 review tests: an unhurtable player in the End void with a landing platform ten
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
     * When the rescuer's main hand already holds a conjured emerald (as a job goal leaves it), should the catch still
     * happen, the emerald stay in the hand through the tail, and the tail's end not take it away.
     *
     * <p>Method under test: {@code VexRescues.showInHand}: a conjured hand is left alone
     */
    static void voidCatchKeepsAConjuredHand(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = voidWithPlatform(helper, player);
        BlockPos slot = player.blockPosition();
        BoundVex vex = voidVex(helper, end, player, slot, VEX_EAST);
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    dropTo(player, column(slot, end.getMinY() - BELOW_FLOOR));
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the catch started");
                    helper.assertTrue(vex.getMainHandItem().is(Items.EMERALD), "the emerald stays");
                })
                .thenIdle(1)
                .thenExecuteFor(19, () -> {
                    VexRescues.tick(player);
                    assertVoidRunning(helper, player, "the tail runs");
                    helper.assertTrue(vex.getMainHandItem().is(Items.EMERALD), "the emerald stays through the tail");
                })
                .thenExecute(() -> {
                    VexRescues.tick(player);
                    assertNoRescue(helper, player, "the tail ends");
                    helper.assertTrue(vex.getMainHandItem().is(Items.EMERALD), "the tail's end keeps the emerald");
                    helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "the emerald is still conjured");
                })
                .thenSucceed();
    }

    /**
     * When something cancels the teleport (a listener on the travel event), should the catch start nothing: no rescue,
     * no apple effects, the player where they were, the rescuer's sword in its hand; and should it not try again
     * (no search, no second teleport) during the next 19 steps.
     *
     * <p>Method under test: {@code VexRescues.catchFromVoid}: the teleport's refusal
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

    // ---- Task 8: end crystals and their blast ----

    /** How far the crystal tests' places reach: every crystal and vex stands within 30 blocks of the slot. */
    private static final int CRYSTAL_REACH = 48;

    /** How close to full health a vex counts as unhurt. */
    private static final float UNHURT = 0.001F;

    /**
     * The point {@code east} blocks east and {@code south} blocks south of the slot's block, at the slot's height. A
     * crystal there floats in the void with nothing under it, so the fire it sets each tick in a dragon fight
     * ({@code EndCrystal.tick}) finds no support and is never placed.
     */
    private static Vec3 crystalSpot(BlockPos slot, double east, double south) {
        return new Vec3(slot.getX() + 0.5 + east, slot.getY(), slot.getZ() + 0.5 + south);
    }

    /** An end crystal at {@code at} in {@code end}, discarded when the test ends. */
    private static EndCrystal crystalIn(GameTestHelper helper, ServerLevel end, Vec3 at) {
        EndCrystal crystal = EntityTypes.END_CRYSTAL.create(end, EntitySpawnReason.COMMAND);
        if (crystal == null) {
            throw helper.assertionException("setup: could not create an end crystal");
        }
        crystal.setPos(at);
        end.addFreshEntity(crystal);
        whenDone(helper, crystal::discard);
        return crystal;
    }

    /**
     * A vex of {@code player} with its full AI and no target goals, 2 blocks above {@code at}; when {@code dragon} is
     * set, the dragon counts as alive for it (undone when the test ends).
     */
    private static BoundVex crystalVex(
            GameTestHelper helper, ServerLevel end, ServerPlayer player, Vec3 at, boolean dragon) {
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, end, at.add(0.0, 2.0, 0.0), player));
        if (dragon) {
            AttackCrystalGoal.scriptDragon(vex, true);
            whenDone(helper, () -> AttackCrystalGoal.unscriptDragon(vex));
        }
        return vex;
    }

    /** Fails the test unless {@code entity} has its full health. */
    private static void assertUnhurt(GameTestHelper helper, LivingEntity entity, String who) {
        helper.assertTrue(
                entity.getMaxHealth() - entity.getHealth() < UNHURT,
                who + " is unhurt, health " + entity.getHealth() + " of " + entity.getMaxHealth());
    }

    /**
     * When a following vex has a crystal 20 blocks away and the dragon lives, should it fly there and break it, and
     * come away unhurt.
     *
     * <p>Method under test: {@code AttackCrystalGoal}: the approach and the strike
     */
    static void crystalBroken(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(crystal.isRemoved(), "the crystal is broken"))
                .thenExecute(() -> {
                    helper.assertTrue(vex.isAlive(), "the vex lives");
                    assertUnhurt(helper, vex, "the vex");
                })
                .thenSucceed();
    }

    /**
     * When a vex breaks a crystal two blocks from its summoner, a tamed wolf and a second vex of the summoner, should
     * none of them be hurt or thrown, and neither the breaking vex.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage} and {@code onExplosionKnockback}
     */
    static void crystalSparesYourSide(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        Vec3 spot = crystalSpot(slot, 2, 0);
        BoundVex breaker = crystalVex(helper, end, player, crystalSpot(slot, -10, 0), true);
        BoundVex second = BoundVexTestSupport.noTargets(vexIn(helper, end, spot.add(0.0, 0.0, 2.0), player));
        second.setNoAi(true);
        Wolf wolf = EntityTypes.WOLF.create(end, EntitySpawnReason.COMMAND);
        if (wolf == null) {
            throw helper.assertionException("setup: could not create a wolf");
        }
        wolf.setNoAi(true);
        wolf.setNoGravity(true);
        wolf.setPos(spot.add(0.0, 0.0, -2.0));
        end.addFreshEntity(wolf);
        wolf.tame(player);
        whenDone(helper, wolf::discard);
        EndCrystal crystal = crystalIn(helper, end, spot);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(crystal.isRemoved(), "the crystal is broken"))
                .thenExecute(() -> {
                    assertUnhurt(helper, player, "the player");
                    helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "the player is not thrown");
                    assertUnhurt(helper, wolf, "the wolf");
                    helper.assertValueEqual(wolf.getDeltaMovement(), Vec3.ZERO, "the wolf is not thrown");
                    assertUnhurt(helper, second, "the second vex");
                    helper.assertValueEqual(second.getDeltaMovement(), Vec3.ZERO, "the second vex is not thrown");
                    assertUnhurt(helper, breaker, "the breaking vex");
                })
                .thenSucceed();
    }

    /**
     * When the only crystal is invulnerable, should the vex leave it alone for 60 ticks and stay by its summoner.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: breakable crystals only
     */
    static void crystalInvulnerableLeftAlone(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 15, 0));
        crystal.setPermanentlyInvulnerable(true);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                    helper.assertTrue(vex.distanceTo(player) <= 6.0, "the vex never left, " + vex.distanceTo(player));
                })
                .thenSucceed();
    }

    /**
     * When the crystal a vex flies to disappears on the way, should the vex's goal end and start again for the other
     * crystal, and break it. (The crystals stand 16 and 17 blocks off, not 30: after five ticks of flight the second
     * must still be within the 32-block search.)
     *
     * <p>Method under test: {@code AttackCrystalGoal.canContinueToUse}, then {@code canUse}
     */
    static void crystalRemovedOnTheWay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal near = crystalIn(helper, end, crystalSpot(slot, 16, 0));
        EndCrystal far = crystalIn(helper, end, crystalSpot(slot, -17, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(AttackCrystalGoal.isClaimed(near), "setup: heading for the near one"))
                .thenExecute(() -> {
                    helper.assertFalse(AttackCrystalGoal.isClaimed(far), "setup: not for the far one");
                    near.discard();
                })
                .thenExecuteAfter(
                        2,
                        () -> helper.assertFalse(
                                AttackCrystalGoal.isClaimed(near), "the removed crystal's claim is given up"))
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(far), "the far one is next"))
                .thenWaitUntil(() -> helper.assertTrue(far.isRemoved(), "the far crystal is broken"))
                .thenSucceed();
    }

    /**
     * When the dragon does not live (scripted), should the vex leave a crystal alone for 60 ticks; and in the
     * Overworld test level, where there is no fight, should {@code livingDragon} be false.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}, {@code AttackCrystalGoal.livingDragon}
     */
    static void crystalNeedsADragon(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, end, crystalSpot(slot, 0, 2), player));
        AttackCrystalGoal.scriptDragon(vex, false);
        whenDone(helper, () -> AttackCrystalGoal.unscriptDragon(vex));
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 10, 0));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                    helper.assertFalse(
                            AttackCrystalGoal.livingDragon(helper.getLevel()), "the Overworld has no living dragon");
                    helper.assertFalse(AttackCrystalGoal.livingDragon(end), "the test End has no living dragon");
                })
                .thenSucceed();
    }

    /**
     * When two vexes of one player have two crystals six blocks apart, should each go for a different one (both
     * crystals are claimed at the same time), both crystals break and both vexes live.
     *
     * <p>Method under test: {@code AttackCrystalGoal}: the claim map
     */
    static void crystalOneEach(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex first = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVex second = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal a = crystalIn(helper, end, crystalSpot(slot, 15, -3));
        EndCrystal b = crystalIn(helper, end, crystalSpot(slot, 15, 3));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        AttackCrystalGoal.isClaimed(a) && AttackCrystalGoal.isClaimed(b), "each crystal has a vex"))
                .thenWaitUntil(() -> helper.assertTrue(a.isRemoved() && b.isRemoved(), "both crystals are broken"))
                .thenExecute(() -> {
                    helper.assertTrue(first.isAlive() && second.isAlive(), "both vexes live");
                    assertUnhurt(helper, first, "the first vex");
                    assertUnhurt(helper, second, "the second vex");
                })
                .thenSucceed();
    }

    /**
     * When the vex has a target (a husk with 1000 health three blocks away), should it leave a crystal ten blocks away
     * alone for 60 ticks.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: no target
     */
    static void crystalNotWhileFighting(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        Husk husk = huskIn(end, crystalSpot(slot, -3, 0).add(0.0, 2.0, 0.0));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        husk.setHealth(1000.0F);
        whenDone(helper, husk::discard);
        vex.setTarget(husk);
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 10, 0));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(vex.getTarget() != null, "setup: the vex still fights");
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                })
                .thenSucceed();
    }

    /**
     * When a vex stays at a post, should it break a crystal five blocks from the post but leave one twelve blocks away
     * (on the other side) alone for 100 ticks.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: the stay area
     */
    static void crystalStayingVexOnlyInItsArea(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        vex.setStaying(true);
        EndCrystal inside = crystalIn(helper, end, crystalSpot(slot, 5, 0).add(0.0, 2.0, 0.0));
        EndCrystal outside = crystalIn(helper, end, crystalSpot(slot, -12, 0).add(0.0, 2.0, 0.0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(inside.isRemoved(), "the near crystal is broken"))
                .thenExecuteAfter(100, () -> helper.assertTrue(outside.isAlive(), "the far crystal is left alone"))
                .thenSucceed();
    }

    /**
     * When a vex is held away from its crystal, should it give up after 200 ticks, hold no claim and take no crystal
     * for the retry delay, so a second vex added then breaks the crystal.
     *
     * <p>Method under test: {@code AttackCrystalGoal.stop}: the limit and the retry delay
     */
    static void crystalGivesUpAfter200Ticks(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex held = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVexTestSupport.holdAt(helper, held, held.position());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the vex has it"))
                .thenWaitUntil(() -> helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the claim is given up"))
                .thenExecute(() -> helper.assertTrue(crystal.isAlive(), "the crystal is still there"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the held vex takes none for a while");
                    crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
                })
                .thenWaitUntil(() -> helper.assertTrue(crystal.isRemoved(), "the second vex breaks it"))
                .thenSucceed();
    }

    /**
     * When a player breaks a crystal (no scripted dragon) with a vex two blocks from it and the player ten blocks
     * away, should the vex be unhurt and unmoved, and the player thrown as vanilla does. (The player takes no damage
     * from a blast they caused themselves: the server's PvP rule blocks it, so health is not asserted.)
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage} and {@code onExplosionKnockback}: a crystal's blast
     * never hurts or moves a Bound Vex, whoever set it off
     */
    static void crystalBlastByAPlayer(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        Vec3 spot = crystalSpot(slot, 10, 0);
        EndCrystal crystal = crystalIn(helper, end, spot);
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, end, spot.add(0.0, 0.0, 2.0), player));
        vex.setNoAi(true);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    crystal.hurtServer(end, end.damageSources().playerAttack(player), 1.0F);
                    helper.assertTrue(crystal.isRemoved(), "the crystal blew up");
                    assertUnhurt(helper, vex, "the vex");
                    helper.assertValueEqual(vex.getDeltaMovement(), Vec3.ZERO, "the vex is not thrown");
                    helper.assertTrue(player.getDeltaMovement().length() > 0.01, "the player is thrown");
                })
                .thenSucceed();
    }

    /**
     * When one player's vex breaks a crystal two blocks from another player's vex, should the other vex be unhurt and
     * unmoved.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: a crystal's blast never hurts a Bound Vex
     */
    static void crystalBlastSparesAnotherPlayersVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerPlayer other = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        Vec3 spot = crystalSpot(slot, 10, 0);
        crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVex theirs = BoundVexTestSupport.noTargets(vexIn(helper, end, spot.add(0.0, 0.0, 2.0), other));
        theirs.setNoAi(true);
        EndCrystal crystal = crystalIn(helper, end, spot);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(crystal.isRemoved(), "the crystal is broken"))
                .thenExecute(() -> {
                    assertUnhurt(helper, theirs, "the other player's vex");
                    helper.assertValueEqual(theirs.getDeltaMovement(), Vec3.ZERO, "the other vex is not thrown");
                })
                .thenSucceed();
    }

    /**
     * When the server stops while a held vex has a crystal claimed, should the claim be free as soon as the stop
     * handler has run. The handler runs in an environment of its own ({@link #SERVER_STOP_CRYSTALS}): it clears every
     * test's state.
     *
     * <p>Method under test: {@code BoundVexEvents.onServerStopped}, through {@code AttackCrystalGoal}'s claim map
     */
    static void crystalClaimsClearedOnServerStop(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVexTestSupport.holdAt(helper, vex, vex.position());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the claim stands"))
                .thenExecute(() -> {
                    BoundVexEvents.onServerStopped(
                            new ServerStoppedEvent(helper.getLevel().getServer()));
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the stop handler freed the claim");
                })
                .thenSucceed();
    }

    /**
     * When a vex with a scripted living dragon is in the Overworld test level and a crystal stands eight blocks away,
     * should the crystal be alive and unclaimed after 60 ticks (the job is for the End only).
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: the End clause
     */
    static void crystalOnlyInTheEnd(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        BoundVex vex =
                BoundVexTestSupport.noTargets(BoundVexTestSupport.boundVex(helper, FEET.offset(0, 3, 0), player));
        AttackCrystalGoal.scriptDragon(vex, true);
        whenDone(helper, () -> AttackCrystalGoal.unscriptDragon(vex));
        EndCrystal crystal =
                crystalIn(helper, helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(FEET.offset(8, 3, 0))));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "no vex claimed it");
                })
                .thenSucceed();
    }

    /**
     * When a vex's summoner mark names nobody who is here from the start and a crystal stands 15 blocks away, should
     * the crystal be alive and unclaimed after 60 ticks.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: a summoner must be present
     */
    static void crystalNeedsASummonerAtTheStart(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        vex.setData(AllyAttachments.SUMMONER, UUID.randomUUID());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 15, 0));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "no vex claimed it");
                })
                .thenSucceed();
    }

    /**
     * When a held vex has claimed a crystal and its summoner mark then names nobody who is here, should the claim be
     * given up within 2 ticks.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canContinueToUse}: the summoner must stay present
     */
    static void crystalSummonerLostMidFlight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVexTestSupport.holdAt(helper, vex, vex.position());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the vex has it"))
                .thenExecute(() -> vex.setData(AllyAttachments.SUMMONER, UUID.randomUUID()))
                .thenExecuteAfter(2, () -> {
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the claim is given up");
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                })
                .thenSucceed();
    }

    /**
     * When a held vex has claimed a crystal and the dragon (scripted) then dies, should the claim be given up within
     * 2 ticks and the crystal survive.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canContinueToUse}: the dragon must stay alive
     */
    static void crystalDragonDiesMidFlight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVexTestSupport.holdAt(helper, vex, vex.position());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the vex has it"))
                .thenExecute(() -> AttackCrystalGoal.scriptDragon(vex, false))
                .thenExecuteAfter(2, () -> {
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the claim is given up");
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                })
                .thenSucceed();
    }

    /**
     * When one vex breaks a crystal that has another crystal three blocks beside it and the summoner (vulnerable)
     * stands two blocks from the first, should both crystals be gone, as vanilla does it (the neighbor is removed by
     * the blast, without a second explosion of its own), and the summoner be unhurt with no velocity.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage} and {@code onExplosionKnockback}: a crystal chain
     */
    static void crystalNeighborsBothGo(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        crystalVex(helper, end, player, crystalSpot(slot, -10, 0), true);
        EndCrystal first = crystalIn(helper, end, crystalSpot(slot, 2, 0));
        EndCrystal second = crystalIn(helper, end, crystalSpot(slot, 5, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(first.isRemoved() && second.isRemoved(), "both crystals are gone"))
                .thenExecute(() -> {
                    assertUnhurt(helper, player, "the summoner");
                    helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "the summoner is not thrown");
                })
                .thenSucceed();
    }

    /**
     * When a vex whose summoner mark names nobody here is the cause of a crystal's blast, with another player (not
     * its summoner, vulnerable) two blocks from the crystal and a husk beside them, should the player be unhurt with
     * no velocity and the husk hurt.
     *
     * <p>Method under test: {@code BoundVexEvents.onExplosionKnockback}: the absent-summoner case
     */
    static void crystalBlastSparesYourSideWithoutYou(GameTestHelper helper) {
        // Arrange
        ServerPlayer owner = voidPlayer(helper);
        ServerLevel end = endVoid(helper, owner, CRYSTAL_REACH);
        BlockPos slot = owner.blockPosition();
        Vec3 spot = crystalSpot(slot, 10, 0);
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, end, spot.add(0.0, 0.0, -6.0), owner));
        vex.setNoAi(true);
        vex.setData(AllyAttachments.SUMMONER, UUID.randomUUID());
        ServerPlayer bystander = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        whenDone(helper, () -> MockPlayers.moveTo(helper, bystander, FangGameTests.STAND));
        Vec3 at = spot.add(0.0, 0.0, 2.0);
        bystander.teleportTo(end, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, false);
        bystander.hasChangedDimension(); // a mock player is never ticked, which would end the move's immunity
        plainVictim(bystander);
        Husk husk = critHusk(helper, end, spot.add(0.0, 0.0, 3.0));
        EndCrystal crystal = crystalIn(helper, end, spot);

        // Act
        crystal.hurtServer(end, end.damageSources().mobAttack(vex), 1.0F);

        // Assert
        helper.assertTrue(crystal.isRemoved(), "the crystal blew up");
        helper.assertValueEqual(bystander.getHealth(), CRIT_HEALTH, "the bystander is unhurt");
        helper.assertValueEqual(bystander.getDeltaMovement(), Vec3.ZERO, "the bystander is not thrown");
        helper.assertTrue(husk.getHealth() < CRIT_HEALTH, "the husk is hurt, health " + husk.getHealth());
        helper.succeed();
    }

    // ---- Task 9: critical hits in the End ----

    /** The health of the crit tests' husks and players, high enough that no tripled hit kills them. */
    private static final float CRIT_HEALTH = 200.0F;

    /** How close a measured tripled loss must be to three times the plain one. */
    private static final float CRIT_TOLERANCE = 0.01F;

    /** Gives {@code victim} {@link #CRIT_HEALTH} health and no armor, so a loss measures the hit alone. */
    private static void plainVictim(LivingEntity victim) {
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CRIT_HEALTH);
        victim.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        victim.setHealth(CRIT_HEALTH);
    }

    /** A husk with no AI and no gravity, plain (see {@link #plainVictim}), in {@code level} at {@code at}. */
    private static Husk critHusk(GameTestHelper helper, ServerLevel level, Vec3 at) {
        Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        if (husk == null) {
            throw helper.assertionException("could not create a husk");
        }
        husk.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setPersistenceRequired();
        plainVictim(husk);
        level.addFreshEntity(husk);
        whenDone(helper, husk::discard);
        return husk;
    }

    /** The health {@code victim} loses to one melee hit by {@code vex}. */
    private static float meleeLoss(BoundVex vex, LivingEntity victim) {
        float before = victim.getHealth();
        vex.doHurtTarget((ServerLevel) vex.level(), victim);
        return before - victim.getHealth();
    }

    /**
     * What one melee hit by a parked vex of {@code player} costs a plain husk in the Overworld test level, checked
     * against the vex's attack damage so that a crit leaking into the baseline cannot hide. Call it
     * while {@code player} is still in that level (a vex whose summoner is in another level has no backstop to pass).
     */
    private static float overworldHuskLoss(GameTestHelper helper, ServerPlayer player) {
        BoundVex vex = BoundVexTestSupport.parked(helper, FEET.offset(0, 3, 4), player);
        Husk husk =
                critHusk(helper, helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(FEET.offset(2, 1, 0))));
        float loss = meleeLoss(vex, husk);
        helper.assertTrue(
                Math.abs(loss - vex.getAttributeValue(Attributes.ATTACK_DAMAGE)) < CRIT_TOLERANCE,
                "setup: the Overworld hit is the vex's plain attack damage, not " + loss);
        return loss;
    }

    /** A vex of {@code player} next to them in {@code level}, with no AI. */
    private static BoundVex critVex(GameTestHelper helper, ServerLevel level, ServerPlayer player) {
        BoundVex vex = vexIn(helper, level, player.position().add(1.0, 1.0, 0.0), player);
        vex.setNoAi(true);
        return vex;
    }

    /** A husk 2 blocks west of {@code player} in {@code level}. */
    private static Husk critHuskBeside(GameTestHelper helper, ServerLevel level, ServerPlayer player) {
        return critHusk(helper, level, player.position().add(-2.0, 0.0, 0.0));
    }

    /**
     * When a Bound Vex hits a husk in the End, should the health lost be three times what the same hit costs a husk
     * in the Overworld, within 0.01 (the Overworld cost is measured, not assumed).
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: the End crit
     */
    static void critInTheEnd(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        float plain = overworldHuskLoss(helper, player);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        Husk husk = critHuskBeside(helper, end, player);

        // Act
        float loss = meleeLoss(vex, husk);

        // Assert
        helper.assertTrue(
                Math.abs(loss - 3.0F * plain) < CRIT_TOLERANCE, "the End hit costs 3 x " + plain + ", not " + loss);
        helper.succeed();
    }

    /**
     * When a Bound Vex's arrow (its direct entity is the arrow) deals 4 to a husk in the End, should the husk lose 4.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: only the vex's own melee crits
     */
    static void critNotForArrows(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        Husk husk = critHuskBeside(helper, end, player);
        Arrow arrow = new Arrow(end, vex, new ItemStack(Items.ARROW), null);

        // Act
        husk.hurtServer(end, end.damageSources().arrow(arrow, vex), 4.0F);

        // Assert
        helper.assertValueEqual(CRIT_HEALTH - husk.getHealth(), 4.0F, "the arrow costs its plain 4");
        helper.succeed();
    }

    /**
     * When a Bound Vex in the End hits its summoner's tamed wolf, should the wolf's health not change (the backstop
     * spares it, and a spared hit is not tripled). Whether the crit was skipped is not observable here: a canceled
     * hit does nothing at any size.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: the backstop comes first
     */
    static void critNotWhenSpared(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        Wolf wolf = EntityTypes.WOLF.create(end, EntitySpawnReason.COMMAND);
        if (wolf == null) {
            throw helper.assertionException("could not create a wolf");
        }
        Vec3 at = player.position().add(-2.0, 0.0, 0.0);
        wolf.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        wolf.setNoAi(true);
        wolf.setNoGravity(true);
        wolf.setPersistenceRequired();
        wolf.tame(player);
        end.addFreshEntity(wolf);
        whenDone(helper, wolf::discard);
        float health = wolf.getHealth();

        // Act
        vex.doHurtTarget(end, wolf);

        // Assert
        helper.assertValueEqual(wolf.getHealth(), health, "the wolf is unhurt");
        helper.succeed();
    }

    /**
     * When a Bound Vex in the End hits an iron golem that is mad at its summoner and stands at 30 health, should the
     * golem stop at 25: mercy clamps the tripled hit as it does a plain one.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage} and {@code GolemMercy.onDamagePre}
     */
    static void critKeepsGolemMercy(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        IronGolem golem = EntityTypes.IRON_GOLEM.create(end, EntitySpawnReason.COMMAND);
        if (golem == null) {
            throw helper.assertionException("could not create a golem");
        }
        Vec3 at = player.position().add(-2.0, 0.0, 0.0);
        golem.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        golem.setNoAi(true);
        golem.setNoGravity(true);
        golem.setPersistenceRequired();
        golem.setHealth(30.0F);
        end.addFreshEntity(golem);
        whenDone(helper, golem::discard);
        GolemGameTests.anger(golem, player);
        helper.assertTrue(vex.isPrey(golem), "setup: a golem mad at the summoner is prey");

        // Act
        vex.doHurtTarget(end, golem);

        // Assert
        helper.assertValueEqual(golem.getHealth(), 25.0F, "the golem stops at 25");
        helper.succeed();
    }

    /**
     * When a Bound Vex hits a second player, who is prey under the server's PvP rule, in the End, should the health
     * lost be three times what the same hit costs another such player in the Overworld, within 0.01 (both measured,
     * neither wearing armor).
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: the crit on a player victim
     */
    static void critOnAPlayerUnderThePvpRule(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerPlayer overworldVictim = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        plainVictim(overworldVictim);
        BoundVex overworldVex = BoundVexTestSupport.parked(helper, FEET.offset(0, 3, 4), player);
        helper.assertTrue(helper.getLevel().isPvpAllowed(), "setup: pvp is on");
        helper.assertTrue(overworldVex.isPrey(overworldVictim), "setup: the Overworld victim is prey");
        float plain = meleeLoss(overworldVex, overworldVictim);
        helper.assertTrue(plain > 0.0F, "setup: the Overworld hit hurts (" + plain + ")");
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        ServerPlayer victim = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        whenDone(helper, () -> MockPlayers.moveTo(helper, victim, FangGameTests.STAND));
        Vec3 at = player.position().add(-2.0, 0.0, 0.0);
        victim.teleportTo(end, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, false);
        victim.hasChangedDimension(); // a mock player is never ticked, which would end the dimension change's immunity
        plainVictim(victim);
        helper.assertTrue(vex.isPrey(victim), "setup: the End victim is prey");

        // Act
        float loss = meleeLoss(vex, victim);

        // Assert
        helper.assertTrue(
                Math.abs(loss - 3.0F * plain) < CRIT_TOLERANCE, "the End hit costs 3 x " + plain + ", not " + loss);
        helper.succeed();
    }

    /**
     * When a Bound Vex hits a husk in a Nether room, should the health lost equal what the same hit costs a husk in
     * the Overworld.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: the crit is for the End only
     */
    static void noCritInTheNether(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        float plain = overworldHuskLoss(helper, player);
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 3, 4);
        BoundVex vex = critVex(helper, nether, player);
        Husk husk = critHuskBeside(helper, nether, player);

        // Act
        float loss = meleeLoss(vex, husk);

        // Assert
        helper.assertTrue(Math.abs(loss - plain) < CRIT_TOLERANCE, "the Nether hit costs " + plain + ", not " + loss);
        helper.succeed();
    }
}
