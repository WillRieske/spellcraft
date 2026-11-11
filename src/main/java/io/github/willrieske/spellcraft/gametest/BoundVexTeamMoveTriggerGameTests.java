package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.beside;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.bigHusk;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.foe;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.frozenVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.hangPoint;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.setBase;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.config.TeamMoveMode;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import io.github.willrieske.spellcraft.spell.rules.TeamMoveRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** GameTests of the automatic triggers (Parts 6-7 spec section 2.3). */
final class BoundVexTeamMoveTriggerGameTests {
    private BoundVexTeamMoveTriggerGameTests() {}

    /** Registers the tests. */
    static void registerAll() {
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_present_summoner_with_no_vexes_leaves_no_record",
                BoundVexTeamMoveTriggerGameTests::presentSummonerWithNoVexesLeavesNoRecord);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_cooling_shield_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::coolingShieldStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_cooling_multishot_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::coolingMultishotStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_cooling_evader_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::coolingEvaderStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_ravager_with_twelve_raids",
                BoundVexTeamMoveTriggerGameTests::ravagerWithTwelveRaids);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_ravager_with_four_shields",
                BoundVexTeamMoveTriggerGameTests::ravagerWithFourShields);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_ravager_with_eleven_shields",
                BoundVexTeamMoveTriggerGameTests::ravagerWithElevenShields);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_enderman_with_nine_shields",
                BoundVexTeamMoveTriggerGameTests::endermanWithNineShields);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_vindicator_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::vindicatorStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_strong_hunter_starts_a_multishot",
                BoundVexTeamMoveTriggerGameTests::strongHunterStartsAMultishot);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_strong_hunter_of_nobody_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::strongHunterOfNobodyStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_hurt_summoner_starts_an_evader",
                BoundVexTeamMoveTriggerGameTests::hurtSummonerStartsAnEvader);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_hurt_villager_starts_an_evader",
                BoundVexTeamMoveTriggerGameTests::hurtVillagerStartsAnEvader);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_hurt_vex_is_not_warded",
                BoundVexTeamMoveTriggerGameTests::hurtVexIsNotWarded);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_enemy_moves_come_before_the_evader",
                BoundVexTeamMoveTriggerGameTests::enemyMovesComeBeforeTheEvader);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_plain_husk_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::plainHuskStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_enemy_outside_the_guard_area_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::enemyOutsideTheGuardAreaStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_staying_vex_is_not_taken",
                BoundVexTeamMoveTriggerGameTests::stayingVexIsNotTaken);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_cooling_raid_falls_through_to_a_shield",
                BoundVexTeamMoveTriggerGameTests::coolingRaidFallsThroughToAShield);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_ghast_hunting_a_vex_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::ghastHuntingAVexStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_nearest_candidate_is_the_subject",
                BoundVexTeamMoveTriggerGameTests::nearestCandidateIsTheSubject);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_raid_candidate_beats_a_nearer_shield_candidate",
                BoundVexTeamMoveTriggerGameTests::raidCandidateBeatsANearerShieldCandidate);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_lowest_fraction_is_warded",
                BoundVexTeamMoveTriggerGameTests::lowestFractionIsWarded);
        FangGameTests.addPadded(
                SHARED, "team_move_trigger_summoner_wins_ties", BoundVexTeamMoveTriggerGameTests::summonerWinsTies);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_healthy_ally_is_not_warded",
                BoundVexTeamMoveTriggerGameTests::healthyAllyIsNotWarded);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_far_ally_is_not_warded",
                BoundVexTeamMoveTriggerGameTests::farAllyIsNotWarded);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_dead_summoner_scans_nothing",
                BoundVexTeamMoveTriggerGameTests::deadSummonerScansNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_staying_vexes_guard_area_does_not_count",
                BoundVexTeamMoveTriggerGameTests::stayingVexesGuardAreaDoesNotCount);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_hunter_of_a_staying_vex_starts_nothing",
                BoundVexTeamMoveTriggerGameTests::hunterOfAStayingVexStartsNothing);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_two_free_vexes_start_no_multishot",
                BoundVexTeamMoveTriggerGameTests::twoFreeVexesStartNoMultishot);
        FangGameTests.addPadded(
                SHARED,
                "team_move_trigger_scan_clock_is_ten_ticks",
                BoundVexTeamMoveTriggerGameTests::scanClockIsTenTicks);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_trigger_manual_mode_does_not_scan",
                BoundVexTeamMoveTriggerGameTests::manualModeDoesNotScan);
    }

    /**
     * When twelve vexes are free and a ravager (100 health) stands in their guard area, should a Raid start with all
     * twelve, each geared.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Raid trigger
     */
    static void ravagerWithTwelveRaids(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12);
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.RAID), "a Raid starts");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 12, "with all twelve");
        TeamMoves.roster(player).forEach(vex -> TeamMoveTestSupport.assertRaidGear(helper, vex));
        helper.succeed();
    }

    /**
     * When only four vexes are free beside a ravager, should a Shield start with three.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Raid fall-through to a Shield
     */
    static void ravagerWithFourShields(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 4);
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield starts");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "with three");
        helper.succeed();
    }

    /**
     * When eleven vexes are free beside a ravager (one short of a Raid), should a Shield start with nine.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Raid fall-through to a Shield
     */
    static void ravagerWithElevenShields(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 11);
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield starts");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 9, "with nine");
        helper.succeed();
    }

    /**
     * When nine vexes are free beside an enderman (40 health), should a Shield start with nine.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Shield trigger
     */
    static void endermanWithNineShields(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 9);
        foe(helper, EntityTypes.ENDERMAN, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield starts");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 9, "with nine");
        helper.succeed();
    }

    /**
     * When a vindicator (attack damage 5) hunts a free vex, should no move start.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Multishot damage threshold
     */
    static void vindicatorStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 3);
        foe(helper, EntityTypes.VINDICATOR, beside(helper)).setTarget(vexes.get(0));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "attack damage 5 earns no move");
        helper.succeed();
    }

    /**
     * When a husk with attack damage 6 hunts a free vex, should a Multishot start with that vex in its roster of three.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Multishot trigger
     */
    static void strongHunterStartsAMultishot(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk husk = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
        husk.setTarget(vexes.get(0));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.MULTISHOT), "a Multishot starts");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "with three");
        helper.assertTrue(TeamMoves.roster(player).contains(vexes.get(0)), "the hunted vex is in");
        helper.succeed();
    }

    /**
     * When a husk with attack damage 6 hunts nobody, should no move start.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: Multishot needs a hunted free vex
     */
    static void strongHunterOfNobodyStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 4);
        setBase(foe(helper, EntityTypes.HUSK, beside(helper)), Attributes.ATTACK_DAMAGE, 6.0);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "no free vex is hunted");
        helper.succeed();
    }

    /**
     * When the summoner is at 40 % health with five free vexes and no enemy, should an Evader start with three,
     * warding the summoner.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Evader trigger for the summoner
     */
    static void hurtSummonerStartsAnEvader(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 5);
        player.setHealth(player.getMaxHealth() * 0.4F);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.EVADER), "an Evader starts");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "with three");
        helper.assertValueEqual(TeamMoves.subject(player), Optional.of(player.getUUID()), "warding the summoner");
        helper.succeed();
    }

    /**
     * When a villager within 16 blocks is at 40 % health, should an Evader start warding it.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Evader trigger for an ally
     */
    static void hurtVillagerStartsAnEvader(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        // Plain padded air, not the barrier-shell layers below the hang point
        Villager villager = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(2.0, 0.0, 2.0));
        villager.setHealth(villager.getMaxHealth() * 0.4F);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.EVADER), "an Evader starts");
        helper.assertValueEqual(TeamMoves.subject(player), Optional.of(villager.getUUID()), "warding the villager");
        helper.succeed();
    }

    /**
     * When one of the summoner's own vexes is at 40 % health, should no Evader start (a vex is never warded).
     *
     * <p>Method under test: {@code TeamMoves.wardFor}: no summoner-marked mob
     */
    static void hurtVexIsNotWarded(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 3);
        vexes.get(0).setHealth(vexes.get(0).getMaxHealth() * 0.4F);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "no Evader for a hurt vex");
        helper.succeed();
    }

    /**
     * When a ravager stands beside twelve free vexes and the summoner is hurt, should the Raid win over the Evader (the
     * enemy side comes first).
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the order of the triggers
     */
    static void enemyMovesComeBeforeTheEvader(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12);
        foe(helper, EntityTypes.RAVAGER, beside(helper));
        player.setHealth(player.getMaxHealth() * 0.4F);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.RAID), "the Raid wins");
        helper.succeed();
    }

    /**
     * When a plain husk (20 health, attack damage 3) stands in the guard area, should no move start.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the thresholds
     */
    static void plainHuskStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 4);
        foe(helper, EntityTypes.HUSK, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing earns a move");
        helper.succeed();
    }

    /**
     * When a ravager stands ten blocks from the vexes (outside every guard area), should no move start.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: candidates are inside a following vex's guard area
     */
    static void enemyOutsideTheGuardAreaStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 4);
        foe(helper, EntityTypes.RAVAGER, hangPoint(helper).add(10.0, 0.0, 0.0));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "out of reach, out of mind");
        helper.succeed();
    }

    /**
     * When one of three vexes stays, should a ravager start no move (two free vexes) and a staying vex never be taken.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: staying vexes are not free
     */
    static void stayingVexIsNotTaken(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3).get(1).setStaying(true);
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "two free vexes are too few for a Shield");
        helper.succeed();
    }

    /**
     * When twelve vexes are free beside a ravager but the Raid ended a moment ago, should a Shield start with nine (the
     * Raid is cooling, so the ravager falls through).
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: a cooling kind is not ready
     */
    static void coolingRaidFallsThroughToAShield(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12);
        Ravager first = foe(helper, EntityTypes.RAVAGER, beside(helper));
        helper.assertTrue(TeamMoves.start(player, TeamMove.RAID, first), "setup: a Raid runs");
        first.kill(helper.getLevel());
        TeamMoves.tick(player); // ends the Raid; its scan finds nothing and closes the scan clock for 10 ticks
        foe(helper, EntityTypes.RAVAGER, beside(helper));
        helper.assertTrue(TeamMoves.cooling(player, TeamMove.RAID), "setup: the Raid cools");

        // Act
        helper.startSequence()
                .thenExecuteAfter(TeamMoves.SCAN_INTERVAL, () -> TeamMoves.tick(player))
                // Assert
                .thenExecute(() -> {
                    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield");
                    helper.assertValueEqual(TeamMoves.roster(player).size(), 9, "with nine");
                })
                .thenSucceed();
    }

    /**
     * When a ghast (no attack-damage attribute) hunts a free vex, should no move start and the scan not fail.
     *
     * <p>Method under test: {@code TeamMoves.attackDamage}: the missing-attribute path
     */
    static void ghastHuntingAVexStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 3);
        foe(helper, EntityTypes.GHAST, beside(helper)).setTarget(vexes.get(0));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "a ghast earns no move and the scan survives it");
        helper.succeed();
    }

    /**
     * When two 40-health husks stand in the guard area, should the Shield's subject be the one nearer the summoner.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the nearest candidate wins (spec section 2.3)
     */
    static void nearestCandidateIsTheSubject(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        Husk a = bigHusk(helper, beside(helper));
        Husk b = bigHusk(helper, hangPoint(helper).add(3.0, 0.0, 2.0));
        helper.assertTrue(Math.abs(a.distanceToSqr(player) - b.distanceToSqr(player)) > 0.01, "setup: no tie");
        Husk nearer = a.distanceToSqr(player) < b.distanceToSqr(player) ? a : b; // computed, never hard-coded

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(
                TeamMoves.subject(player), Optional.of(nearer.getUUID()), "the nearer husk is the subject");
        helper.succeed();
    }

    /**
     * When a 40-health husk is nearer the summoner than a ravager, both in the guard area, should the Raid still win
     * (the Raid comes before the Shield across candidates) with the ravager as its subject.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Raid-before-Shield order across candidates
     */
    static void raidCandidateBeatsANearerShieldCandidate(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12);
        Vec3 east = beside(helper);
        Vec3 farEast = hangPoint(helper).add(3.0, 0.0, 2.0);
        boolean eastIsCloser =
                player.position().distanceToSqr(east) < player.position().distanceToSqr(farEast);
        bigHusk(helper, eastIsCloser ? east : farEast);
        Ravager ravager = foe(helper, EntityTypes.RAVAGER, eastIsCloser ? farEast : east);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.RAID), "the Raid wins");
        helper.assertValueEqual(TeamMoves.subject(player), Optional.of(ravager.getUUID()), "against the ravager");
        helper.succeed();
    }

    /**
     * When the summoner is at 40 % and a villager at 30 %, should an Evader ward the villager (the lowest health
     * fraction wins).
     *
     * <p>Method under test: {@code TeamMoves.wardFor}: the ranking
     */
    static void lowestFractionIsWarded(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        player.setHealth(player.getMaxHealth() * 0.4F);
        Villager villager = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(2.0, 0.0, 2.0));
        villager.setHealth(villager.getMaxHealth() * 0.3F);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.subject(player), Optional.of(villager.getUUID()), "the villager is warded");
        helper.succeed();
    }

    /**
     * When the summoner and a villager are at the same health fraction, should an Evader ward the summoner.
     *
     * <p>Method under test: {@code TeamMoves.wardFor}: the summoner wins ties
     */
    static void summonerWinsTies(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        player.setHealth(player.getMaxHealth() * 0.4F);
        Villager villager = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(2.0, 0.0, 2.0));
        villager.setHealth(villager.getMaxHealth() * 0.4F);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.subject(player), Optional.of(player.getUUID()), "the summoner is warded");
        helper.succeed();
    }

    /**
     * When a villager is at exactly half health, should no Evader start (the boundary is strict).
     *
     * <p>Method under test: {@code TeamMoves.wardFor}: {@code health < maxHealth / 2}
     */
    static void healthyAllyIsNotWarded(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        Villager villager = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(2.0, 0.0, 2.0));
        villager.setHealth(villager.getMaxHealth() / 2);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "half health is not hurt enough");
        helper.succeed();
    }

    /**
     * When a villager at 40 % health stands 17 blocks above the summoner, should no Evader start (the ward must be
     * within 16 blocks).
     *
     * <p>Method under test: {@code TeamMoves.wardFor}: the range clause
     */
    static void farAllyIsNotWarded(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        Villager villager = foe(helper, EntityTypes.VILLAGER, player.position().add(0.0, 17.0, 0.0));
        villager.setHealth(villager.getMaxHealth() * 0.4F);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "17 blocks is too far to ward");
        helper.succeed();
    }

    /**
     * When the summoner is dead with twelve vexes and a ravager beside, should the scan start nothing.
     *
     * <p>Method under test: {@code TeamMoves.tick}: the scan needs a present summoner
     */
    static void deadSummonerScansNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12);
        foe(helper, EntityTypes.RAVAGER, beside(helper));
        player.kill(helper.getLevel());

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "a dead summoner leads nothing");
        helper.succeed();
    }

    /**
     * When all three vexes stay and a ravager stands in their stay area, should no move start (a staying vex's guard
     * area does not count).
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: candidates come from following vexes only
     */
    static void stayingVexesGuardAreaDoesNotCount(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3).forEach(vex -> vex.setStaying(true));
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nobody follows, so nothing triggers");
        helper.succeed();
    }

    /**
     * When a husk with attack damage 6 hunts a staying vex while three others are free, should no Multishot start (the
     * hunted vex is not free).
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: {@code targetsFreeVex}
     */
    static void hunterOfAStayingVexStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        vexes.get(0).setStaying(true);
        Husk husk = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
        husk.setTarget(vexes.get(0));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "the hunted vex is staying, not free");
        helper.succeed();
    }

    /**
     * When a husk with attack damage 6 hunts one of only two free vexes, should no Multishot start.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: a Multishot needs three free vexes
     */
    static void twoFreeVexesStartNoMultishot(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 2);
        Husk husk = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
        husk.setTarget(vexes.get(0));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "two vexes are too few for a Multishot");
        helper.succeed();
    }

    /**
     * When the scan found nothing this tick and a husk of 40 health appears, should a second step in the same tick not
     * scan again, one a tick short of the clock not scan either, and one on the clock start the Shield.
     *
     * <p>Method under test: {@code TeamMoves.tick}: the 10-tick scan clock
     */
    static void scanClockIsTenTicks(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        TeamMoves.tick(player);
        bigHusk(helper, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "the clock is closed within the same tick");
        helper.startSequence()
                .thenExecuteAfter(TeamMoves.SCAN_INTERVAL - 1, () -> {
                    TeamMoves.tick(player);
                    helper.assertTrue(TeamMoves.running(player).isEmpty(), "one tick short of the clock: no scan");
                })
                .thenExecuteAfter(1, () -> {
                    TeamMoves.tick(player);
                    helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "on the clock");
                })
                .thenSucceed();
    }

    /**
     * When the setting is manual, should the scan not run: a ravager beside twelve vexes starts nothing.
     *
     * <p>Method under test: {@code TeamMoves.tick}: the {@code teamMoves} setting
     */
    static void manualModeDoesNotScan(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12);
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        GameTestSupport.withConfig(ServerConfig.TEAM_MOVES, TeamMoveMode.MANUAL, () -> TeamMoves.tick(player));

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "manual mode starts nothing by itself");
        helper.succeed();
    }

    /**
     * When the setting is automatic and the summoner is present with no vexes at all, should a scan start no move and
     * leave the summoner with no record (the scan clock lives apart from the record).
     *
     * <p>Method under test: {@code TeamMoves.tick}: the scan clock without a record
     */
    static void presentSummonerWithNoVexesLeavesNoRecord(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "no move starts without vexes");
        helper.assertFalse(TeamMoves.hasRecord(player), "and the scan created no record");
        helper.succeed();
    }

    /**
     * When a Shield ended a moment ago and a new husk of 40 health stands in the guard area, should the scan start
     * nothing before the cooldown is over (a Shield cools like every kind), and the move start once it is over.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: a cooling Shield is not ready
     */
    static void coolingShieldStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 4);
        Husk first = bigHusk(helper, beside(helper));
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, first), "setup: a Shield runs");
        first.kill(helper.getLevel());
        TeamMoves.tick(player); // ends the Shield; the scan finds nothing and closes the scan clock
        bigHusk(helper, beside(helper));
        helper.assertTrue(TeamMoves.cooling(player, TeamMove.SHIELD), "setup: the Shield cools");

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(TeamMoves.SCAN_INTERVAL, () -> TeamMoves.tick(player))
                .thenExecute(() -> helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing starts yet"))
                .thenExecute(() -> {
                    TeamMoves.age(player, TeamMoveRules.COOLDOWN_TICKS);
                    TeamMoves.tick(player);
                    helper.assertValueEqual(
                            TeamMoves.running(player),
                            Optional.of(TeamMove.SHIELD),
                            "it starts once the cooldown is over");
                })
                .thenSucceed();
    }

    /**
     * When a Multishot ended a moment ago and a husk with attack damage 6 hunts a free vex, should the scan start
     * nothing before the cooldown is over, and the move start once it is over.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: a cooling Multishot is not ready
     */
    static void coolingMultishotStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk first = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(first, Attributes.ATTACK_DAMAGE, 6.0);
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, first), "setup: a Multishot runs");
        first.kill(helper.getLevel());
        TeamMoves.tick(player);
        Husk second = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(second, Attributes.ATTACK_DAMAGE, 6.0);
        second.setTarget(vexes.get(0));
        helper.assertTrue(TeamMoves.cooling(player, TeamMove.MULTISHOT), "setup: the Multishot cools");

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(TeamMoves.SCAN_INTERVAL, () -> TeamMoves.tick(player))
                .thenExecute(() -> helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing starts yet"))
                .thenExecute(() -> {
                    TeamMoves.age(player, TeamMoveRules.COOLDOWN_TICKS);
                    TeamMoves.tick(player);
                    helper.assertValueEqual(
                            TeamMoves.running(player),
                            Optional.of(TeamMove.MULTISHOT),
                            "it starts once the cooldown is over");
                })
                .thenSucceed();
    }

    /**
     * When an Evader ended a moment ago and the summoner is hurt again, should the scan start nothing before the
     * cooldown is over, and the move start once it is over.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: a cooling Evader is not ready
     */
    static void coolingEvaderStartsNothing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        player.setHealth(player.getMaxHealth() * 0.4F);
        helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, player), "setup: an Evader runs");
        player.setHealth(player.getMaxHealth());
        TeamMoves.tick(player); // the ward is healed: the Evader ends
        player.setHealth(player.getMaxHealth() * 0.4F);
        helper.assertTrue(TeamMoves.cooling(player, TeamMove.EVADER), "setup: the Evader cools");

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(TeamMoves.SCAN_INTERVAL, () -> TeamMoves.tick(player))
                .thenExecute(() -> helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing starts yet"))
                .thenExecute(() -> {
                    TeamMoves.age(player, TeamMoveRules.COOLDOWN_TICKS);
                    TeamMoves.tick(player);
                    helper.assertValueEqual(
                            TeamMoves.running(player),
                            Optional.of(TeamMove.EVADER),
                            "it starts once the cooldown is over");
                })
                .thenSucceed();
    }
}
