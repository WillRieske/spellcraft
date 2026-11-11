package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.beside;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.bigHusk;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.foe;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.frozenVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.setBase;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.config.TeamMoveMode;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.EvokingNetwork;
import io.github.willrieske.spellcraft.evoking.EvokingRequest;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;

/**
 * GameTests of the manual team-move request (Parts 6-7 spec section 3.4) and, from Task 8, of the packet's
 * dispatch.
 */
final class BoundVexTeamMoveRequestGameTests {
    private BoundVexTeamMoveRequestGameTests() {}

    /** Registers the tests. */
    static void registerAll() {
        FangGameTests.addPadded(
                SHARED, "team_move_request_automatic_answers", BoundVexTeamMoveRequestGameTests::automaticAnswers);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_no_enemy_is_unavailable",
                BoundVexTeamMoveRequestGameTests::noEnemyIsUnavailable);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_too_few_vexes_is_unavailable",
                BoundVexTeamMoveRequestGameTests::tooFewVexesIsUnavailable);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_far_enemy_is_unavailable",
                BoundVexTeamMoveRequestGameTests::farEnemyIsUnavailable);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_running_move_answers_running",
                BoundVexTeamMoveRequestGameTests::runningMoveAnswersRunning);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_cooling_kind_answers_cooling",
                BoundVexTeamMoveRequestGameTests::coolingKindAnswersCooling);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_shield_request_starts",
                BoundVexTeamMoveRequestGameTests::shieldRequestStarts);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_multishot_request_needs_no_hunted_vex",
                BoundVexTeamMoveRequestGameTests::multishotRequestNeedsNoHuntedVex);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_multishot_with_two_is_unavailable",
                BoundVexTeamMoveRequestGameTests::multishotWithTwoIsUnavailable);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_evader_request_needs_no_enemy",
                BoundVexTeamMoveRequestGameTests::evaderRequestNeedsNoEnemy);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_raid_request_starts",
                BoundVexTeamMoveRequestGameTests::raidRequestStarts);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_request_needs_no_evoking",
                BoundVexTeamMoveRequestGameTests::requestNeedsNoEvoking);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_dead_sender_is_ignored",
                BoundVexTeamMoveRequestGameTests::deadSenderIsIgnored);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_spectating_sender_is_ignored",
                BoundVexTeamMoveRequestGameTests::spectatingSenderIsIgnored);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_evader_without_a_ward_is_unavailable",
                BoundVexTeamMoveRequestGameTests::evaderWithoutAWardIsUnavailable);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_raid_with_eleven_is_unavailable",
                BoundVexTeamMoveRequestGameTests::raidWithElevenIsUnavailable);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_ally_giant_is_not_a_subject",
                BoundVexTeamMoveRequestGameTests::allyGiantIsNotASubject);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_automatic_beats_running",
                BoundVexTeamMoveRequestGameTests::automaticBeatsRunning);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_running_beats_cooling",
                BoundVexTeamMoveRequestGameTests::runningBeatsCooling);
        FangGameTests.addPadded(
                "config_mutation",
                "team_move_request_flood_is_ignored",
                BoundVexTeamMoveRequestGameTests::floodIsIgnored);
        FangGameTests.addPadded(
                SHARED, "team_move_packet_cast_still_casts", BoundVexTeamMoveRequestGameTests::castStillCasts);
        FangGameTests.addPadded(
                SHARED,
                "team_move_packet_team_move_reaches_the_request",
                BoundVexTeamMoveRequestGameTests::teamMoveReachesTheRequest);
        FangGameTests.addPadded(
                SHARED,
                "team_move_packet_out_of_range_team_move_is_ignored",
                BoundVexTeamMoveRequestGameTests::outOfRangeTeamMoveIsIgnored);
        FangGameTests.addPadded(
                SHARED,
                "team_move_packet_out_of_range_cast_is_ignored",
                BoundVexTeamMoveRequestGameTests::outOfRangeCastIsIgnored);
    }

    /** Runs {@code body} with the setting on MANUAL and gives back its answer; the setting is restored. */
    private static <T> T manual(Supplier<T> body) {
        AtomicReference<T> answer = new AtomicReference<>();
        GameTestSupport.withConfig(ServerConfig.TEAM_MOVES, TeamMoveMode.MANUAL, () -> answer.set(body.get()));
        return answer.get();
    }

    /**
     * When the setting is automatic, should every request answer "Team moves are automatic" and start nothing, even
     * with an enemy and vexes ready.
     *
     * <p>Method under test: {@link TeamMoves#request}: the automatic answer
     */
    static void automaticAnswers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        bigHusk(helper, beside(helper));

        // Act
        TeamMoves.Answer answer = TeamMoves.request(player, TeamMove.SHIELD);

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.AUTOMATIC, "the answer");
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.AUTOMATIC_KEY);
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
        helper.succeed();
    }

    /**
     * When the setting is manual and no enemy is near, should the request answer "unavailable".
     *
     * <p>Method under test: {@link TeamMoves#request}: no subject
     */
    static void noEnemyIsUnavailable(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.UNAVAILABLE_KEY);
        helper.succeed();
    }

    /**
     * When the setting is manual and only two vexes are free, should the request answer "unavailable" for a Shield.
     *
     * <p>Method under test: {@link TeamMoves#request}: no roster
     */
    static void tooFewVexesIsUnavailable(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 2);
        bigHusk(helper, beside(helper));

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
        helper.succeed();
    }

    /**
     * When the setting is manual and an enemy about 17 blocks from the summoner (inside the 16-block search box,
     * outside the 16-block sphere) would fit, should the request answer "unavailable".
     *
     * <p>Method under test: {@link TeamMoves#request}: the 16-block search, the sphere clause
     */
    static void farEnemyIsUnavailable(GameTestHelper helper) {
        // Arrange (+12/+12/0 is inside the inflated box and 16.97 blocks away, so only the sphere clause rejects it)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        bigHusk(helper, player.position().add(12.0, 12.0, 0.0));

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
        helper.succeed();
    }

    /**
     * When the setting is manual and a move runs, should a second request answer "already in a team move" whatever the
     * kind.
     *
     * <p>Method under test: {@link TeamMoves#request}: one move at a time
     */
    static void runningMoveAnswersRunning(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 6);
        helper.assertTrue(
                TeamMoves.start(player, TeamMove.SHIELD, bigHusk(helper, beside(helper))), "setup: a Shield runs");
        GameTestSupport.drainActionBar(player);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.EVADER));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.RUNNING, "the answer");
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.RUNNING_KEY);
        helper.succeed();
    }

    /**
     * When the setting is manual and the Shield ended a moment ago, should a request for a Shield answer "not ready
     * yet" (a husk and vexes are ready, so this is also the proof that cooling beats unavailable).
     *
     * <p>Method under test: {@link TeamMoves#request}: the cooldown
     */
    static void coolingKindAnswersCooling(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        Husk first = bigHusk(helper, beside(helper));
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, first), "setup: a Shield runs");
        first.kill(helper.getLevel());
        TeamMoves.tick(player);
        bigHusk(helper, beside(helper));
        GameTestSupport.drainActionBar(player);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.COOLING, "the answer");
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.COOLING_KEY);
        helper.succeed();
    }

    /**
     * When the setting is manual with three free vexes and a 40-health husk near, should a Shield request start the
     * Shield with its usual message and nothing else.
     *
     * <p>Method under test: {@link TeamMoves#request}: the Shield
     */
    static void shieldRequestStarts(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        Husk husk = bigHusk(helper, beside(helper));

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield runs");
        helper.assertValueEqual(TeamMoves.subject(player), Optional.of(husk.getUUID()), "against the husk");
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "shield");
        helper.succeed();
    }

    /**
     * When the setting is manual and a husk with attack damage 6 hunts nobody, should a Multishot request start with
     * the nearest free vex in the roster (a hunted vex is not needed).
     *
     * <p>Method under test: {@link TeamMoves#request}: the Multishot
     */
    static void multishotRequestNeedsNoHuntedVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk husk = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
        BoundVex nearest = vexes.stream()
                .min(Comparator.comparingDouble(vex -> vex.distanceToSqr(husk)))
                .orElseThrow();

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.MULTISHOT));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "three vexes");
        helper.assertTrue(TeamMoves.roster(player).contains(nearest), "the nearest vex is in");
        helper.succeed();
    }

    /**
     * When the setting is manual and a husk with attack damage 6 stands near but only two vexes are free, should a
     * Multishot request answer "unavailable".
     *
     * <p>Method under test: {@link TeamMoves#request}: the Multishot roster minimum
     */
    static void multishotWithTwoIsUnavailable(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 2);
        setBase(foe(helper, EntityTypes.HUSK, beside(helper)), Attributes.ATTACK_DAMAGE, 6.0);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.MULTISHOT));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
        helper.succeed();
    }

    /**
     * When the setting is manual and the summoner is hurt, should an Evader request start with no enemy near.
     *
     * <p>Method under test: {@link TeamMoves#request}: the Evader
     */
    static void evaderRequestNeedsNoEnemy(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 2);
        player.setHealth(8.0F);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.EVADER));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 2, "both vexes");
        helper.succeed();
    }

    /**
     * When the setting is manual with twelve free vexes and a ravager near, should a Raid request start the Raid.
     *
     * <p>Method under test: {@link TeamMoves#request}: the Raid
     */
    static void raidRequestStarts(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12);
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.RAID));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 12, "all twelve");
        helper.succeed();
    }

    /**
     * When the summoner no longer has the Evoking effect, should a manual request still start the move (the vexes
     * outlive the effect).
     *
     * <p>Method under test: {@link TeamMoves#request}: no Evoking needed
     */
    static void requestNeedsNoEvoking(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        player.removeEffect(ModEffects.EVOKING);
        frozenVexes(helper, player, 3);
        bigHusk(helper, beside(helper));

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.STARTED, "the answer");
        helper.succeed();
    }

    /**
     * When a summoner sends a second request in the tick of the first, should it be ignored without a word on the
     * action bar (the first was answered), and when {@code REQUEST_INTERVAL} ticks have passed, should the next one be
     * answered again.
     *
     * <p>Method under test: {@link TeamMoves#request}: the per-player throttle
     */
    static void floodIsIgnored(GameTestHelper helper) {
        // Arrange (no enemy near, so every handled request is answered "unavailable")
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        TeamMoves.Answer first = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));
        GameTestSupport.drainActionBar(player);

        // Act
        TeamMoves.Answer second = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));
        List<?> spoken = GameTestSupport.drainActionBar(player);
        TeamMoves.age(player, TeamMoves.REQUEST_INTERVAL - 1);
        TeamMoves.Answer almost = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));
        TeamMoves.age(player, 1);
        TeamMoves.Answer later = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(first, TeamMoves.Answer.UNAVAILABLE, "the first request is answered");
        helper.assertValueEqual(second, TeamMoves.Answer.IGNORED, "the second, in the same tick, is ignored");
        helper.assertTrue(spoken.isEmpty(), "and silently");
        helper.assertValueEqual(almost, TeamMoves.Answer.IGNORED, "one tick short of the interval is still ignored");
        helper.assertValueEqual(later, TeamMoves.Answer.UNAVAILABLE, "after the interval it is answered again");
        helper.succeed();
    }

    /**
     * When the sender is dead, should the request be ignored: no answer on the action bar and nothing started.
     *
     * <p>Method under test: {@link TeamMoves#request}: dead and spectating senders
     */
    static void deadSenderIsIgnored(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        bigHusk(helper, beside(helper));
        player.kill(helper.getLevel());
        GameTestSupport.drainActionBar(player);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.IGNORED, "the answer");
        helper.assertTrue(GameTestSupport.drainActionBar(player).isEmpty(), "nothing is said");
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
        helper.succeed();
    }

    /**
     * When the sender is a spectator, should the request be ignored: no answer on the action bar and nothing started.
     *
     * <p>Method under test: {@link TeamMoves#request}: spectating senders
     */
    static void spectatingSenderIsIgnored(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        bigHusk(helper, beside(helper));
        player.setGameMode(GameType.SPECTATOR);
        GameTestSupport.drainActionBar(player);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.IGNORED, "the answer");
        helper.assertTrue(GameTestSupport.drainActionBar(player).isEmpty(), "nothing is said");
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
        helper.succeed();
    }

    /**
     * When the setting is manual and the summoner is healthy with no hurt ally near, should an Evader request answer
     * "unavailable" (no ward).
     *
     * <p>Method under test: {@link TeamMoves#request}: the Evader needs a ward
     */
    static void evaderWithoutAWardIsUnavailable(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.EVADER));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
        helper.succeed();
    }

    /**
     * When the setting is manual with eleven free vexes and a ravager near, should a Raid request answer "unavailable".
     *
     * <p>Method under test: {@link TeamMoves#request}: the Raid roster minimum
     */
    static void raidWithElevenIsUnavailable(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 11);
        foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.RAID));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
        helper.succeed();
    }

    /**
     * When the setting is manual with twelve free vexes and an iron golem (100 health, an ally, not an enemy) near,
     * should a Raid request answer "unavailable" and start nothing.
     *
     * <p>Method under test: {@link TeamMoves#request}: the subject must be huntable and prey
     */
    static void allyGiantIsNotASubject(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12);
        foe(helper, EntityTypes.IRON_GOLEM, beside(helper));

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.RAID));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.UNAVAILABLE, "the answer");
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
        helper.succeed();
    }

    /**
     * When the setting is automatic and a Shield runs, should a request answer "automatic", not "already running" (the
     * automatic answer comes first).
     *
     * <p>Method under test: {@link TeamMoves#request}: the order of the answers
     */
    static void automaticBeatsRunning(GameTestHelper helper) {
        // Arrange (the setting stays on its default, AUTOMATIC: no manual(...))
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 6);
        helper.assertTrue(
                TeamMoves.start(player, TeamMove.SHIELD, bigHusk(helper, beside(helper))), "setup: a Shield runs");
        GameTestSupport.drainActionBar(player);

        // Act
        TeamMoves.Answer answer = TeamMoves.request(player, TeamMove.MULTISHOT);

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.AUTOMATIC, "the answer");
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.AUTOMATIC_KEY);
        helper.succeed();
    }

    /**
     * When the setting is manual, an Evader runs and the Shield is cooling, should a request for a Shield answer
     * "already running", not "not ready yet" (running comes before cooling).
     *
     * <p>Method under test: {@link TeamMoves#request}: the order of the answers
     */
    static void runningBeatsCooling(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 6);
        Husk husk = bigHusk(helper, beside(helper));
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, husk), "setup: a Shield runs");
        husk.kill(helper.getLevel());
        TeamMoves.tick(player); // the Shield ends and cools
        player.setHealth(8.0F);
        helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, player), "setup: an Evader runs");
        GameTestSupport.drainActionBar(player);

        // Act
        TeamMoves.Answer answer = manual(() -> TeamMoves.request(player, TeamMove.SHIELD));

        // Assert
        helper.assertValueEqual(answer, TeamMoves.Answer.RUNNING, "the answer");
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.RUNNING_KEY);
        helper.succeed();
    }

    /**
     * When a CAST request names the vexes spell, should the player start casting vexes exactly as a direct request
     * does.
     *
     * <p>Method under test: {@link EvokingNetwork#handle}: the CAST kind
     */
    static void castStillCasts(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);

        // Act
        EvokingNetwork.handle(player, EvokingRequest.cast(EvokerSpell.VEXES));

        // Assert
        helper.assertTrue(player.hasEffect(ModEffects.CASTING_VEXES), "the vexes spell is being cast");
        helper.succeed();
    }

    /**
     * When a TEAM_MOVE request arrives in automatic mode, should the answer be "Team moves are automatic": the request
     * got to {@code TeamMoves.request}.
     *
     * <p>Method under test: {@link EvokingNetwork#handle}: the TEAM_MOVE kind
     */
    static void teamMoveReachesTheRequest(GameTestHelper helper) {
        // Arrange (drain first, as the other caster tests do: assertActionBarKey demands exactly one message)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        GameTestSupport.drainActionBar(player);

        // Act
        EvokingNetwork.handle(player, EvokingRequest.teamMove(TeamMove.RAID));

        // Assert
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.AUTOMATIC_KEY);
        helper.succeed();
    }

    /**
     * When a TEAM_MOVE request carries NONE's ordinal, a negative byte or 99, should it be ignored: no message, no
     * move.
     *
     * <p>Method under test: {@link EvokingNetwork#handle}: the argument range of TEAM_MOVE
     */
    static void outOfRangeTeamMoveIsIgnored(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        GameTestSupport.drainActionBar(player);

        // Act
        for (byte arg : new byte[] {TeamMove.NONE.arg(), -1, 99, Byte.MIN_VALUE, Byte.MAX_VALUE}) {
            EvokingNetwork.handle(player, new EvokingRequest(EvokingRequest.Kind.TEAM_MOVE, arg));
        }

        // Assert
        helper.assertTrue(GameTestSupport.drainActionBar(player).isEmpty(), "nothing is said");
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing started");
        helper.succeed();
    }

    /**
     * When a CAST request carries an ordinal no spell has, should it be ignored: no casting effect.
     *
     * <p>Method under test: {@link EvokingNetwork#handle}: the argument range of CAST
     */
    static void outOfRangeCastIsIgnored(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);

        // Act
        for (byte arg : new byte[] {(byte) EvokerSpell.values().length, -1, 99, Byte.MIN_VALUE, Byte.MAX_VALUE}) {
            EvokingNetwork.handle(player, new EvokingRequest(EvokingRequest.Kind.CAST, arg));
        }

        // Assert
        helper.assertFalse(player.hasEffect(ModEffects.CASTING_VEXES), "no vexes");
        helper.assertFalse(player.hasEffect(ModEffects.CASTING_FANGS), "no fangs");
        helper.succeed();
    }
}
