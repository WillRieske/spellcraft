package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.AIR_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.enemyVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.noTargets;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.patientCreeper;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.rolling;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.stayingVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.swordBack;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.BribeGoal;
import io.github.willrieske.spellcraft.evoking.FleeCreeperGoal;
import io.github.willrieske.spellcraft.evoking.FleeFromViewGoal;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Bound Vex GameTests for the bribe (Evoking II Parts 4-5 spec §3). Tests whose vex flies or that need headroom
 * register through {@code FangGameTests.addPadded} (padding 13); the rest run inside the shared 3x3x3 box. Shared
 * constants and helpers live in {@link BoundVexTestSupport}; the cure's tests are in {@link BoundVexCureGameTests}.
 *
 * <p>Functional spec §5.5, §7.4, §7.9, §9.6, §9.7, §9.8, §11.2.
 */
public final class BoundVexBribeGameTests {
    /**
     * The environment of the one test that needs the level's players to itself: a bribed vex leaves when no player sees
     * it, and in {@code SHARED} the other tests' mock players, in range and in the open, would keep it in view.
     */
    private static final String ALONE = "bound_vex_bribe_alone";

    /** Ticks a Bound Vex may still hold a target that became courted: the target goals re-check every second tick. */
    private static final int COURTED_TARGET_LAG = 4;

    /** The environment of the test that stops the server: the stop handler clears every claim on it. */
    private static final String SERVER_STOP_CLAIMS = "bound_vex_bribe_server_stop_claims";

    private BoundVexBribeGameTests() {}

    static void registerAll() {
        plain("bound_vex_bribe_a_bribed_vex_is_not_prey", BoundVexBribeGameTests::aBribedVexIsNotPrey);
        padded("bound_vex_bribe_a_bribe_that_succeeds", BoundVexBribeGameTests::aBribeThatSucceeds);
        FangGameTests.addPadded(
                ALONE,
                "bound_vex_bribe_a_bribed_vex_leaves_out_of_sight",
                BoundVexBribeGameTests::aBribedVexLeavesOutOfSight);
        padded("bound_vex_bribe_a_bribe_that_fails", BoundVexBribeGameTests::aBribeThatFails);
        padded("bound_vex_bribe_a_courted_vex_is_not_prey", BoundVexBribeGameTests::aCourtedVexIsNotPrey);
        padded("bound_vex_bribe_courting_vex_is_not_free", BoundVexBribeGameTests::courtingVexIsNotFree);
        padded(
                "bound_vex_bribe_no_offer_to_a_vex_that_is_not_attacking_your_side",
                BoundVexBribeGameTests::noOfferToAVexThatIsNotAttackingYourSide);
        padded("bound_vex_bribe_the_offer_ends_if_the_vex_dies", BoundVexBribeGameTests::theOfferEndsIfTheVexDies);
        padded(
                "bound_vex_bribe_an_approach_timeout_leaves_no_mark",
                BoundVexBribeGameTests::anApproachTimeoutLeavesNoMark);
        padded(
                "bound_vex_bribe_the_offer_ends_if_the_vex_leaves_or_the_briber_flees",
                BoundVexBribeGameTests::theOfferEndsIfTheVexLeavesOrTheBriberFlees);
        plain(
                "bound_vex_bribe_a_bribed_vex_loaded_from_a_save_only_flees",
                BoundVexBribeGameTests::aBribedVexLoadedFromASaveOnlyFlees);
        padded(
                "bound_vex_bribe_a_staying_vex_bribes_only_in_its_area",
                BoundVexBribeGameTests::aStayingVexBribesOnlyInItsArea);
        padded(
                "bound_vex_bribe_a_refused_vex_is_rolled_for_exactly_once",
                BoundVexBribeGameTests::aRefusedVexIsRolledForExactlyOnce);
        padded(
                "bound_vex_bribe_each_enemy_vex_gets_exactly_one_offer",
                BoundVexBribeGameTests::eachEnemyVexGetsExactlyOneOffer);
        padded("bound_vex_bribe_a_stale_claim_is_free", BoundVexBribeGameTests::aStaleClaimIsFree);
        FangGameTests.addPadded(
                SERVER_STOP_CLAIMS,
                "bound_vex_bribe_server_stop_frees_the_bribe_claims",
                BoundVexBribeGameTests::serverStopFreesTheBribeClaims);
        padded(
                "bound_vex_bribe_the_summoner_going_absent_ends_the_offer",
                BoundVexBribeGameTests::theSummonerGoingAbsentEndsTheOffer);
        plain(
                "bound_vex_bribe_a_bribed_vex_arriving_in_another_dimension_only_flees",
                BoundVexBribeGameTests::aBribedVexArrivingInAnotherDimensionOnlyFlees);
    }

    /** Registers {@code body} in the shared environment, padded (see {@link FangGameTests#addPadded}). */
    private static void padded(String name, Consumer<GameTestHelper> body) {
        FangGameTests.addPadded(SHARED, name, body);
    }

    /** Registers {@code body} in the shared environment, unpadded (the shared 3x3x3 box). */
    private static void plain(String name, Consumer<GameTestHelper> body) {
        SpellcraftGameTests.add(SHARED, name, body);
    }

    /**
     * When a vanilla vex carries the bribed mark, should {@link BoundVex#isPrey} answer false for it; without the
     * mark, or with only the bribe-tried mark, it is prey.
     *
     * <p>Method under test: {@link BoundVex#isPrey(ServerLevel, java.util.UUID,
     * net.minecraft.world.entity.LivingEntity)}
     *
     * <p>Functional spec §7.4, §9.6, §9.7.
     */
    static void aBribedVexIsNotPrey(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        ServerLevel level = helper.getLevel();
        Vex vex = helper.spawn(EntityTypes.VEX, BoundVexTestSupport.VEX_SPOT);
        vex.setNoAi(true);
        whenDone(helper, vex::discard);

        // Act & Assert
        helper.assertTrue(BoundVex.isPrey(level, player.getUUID(), vex), "setup: a plain vex is prey");
        vex.setData(AllyAttachments.BRIBE_TRIED, true);
        helper.assertTrue(BoundVex.isPrey(level, player.getUUID(), vex), "an offered but not bribed vex is still prey");
        vex.setData(AllyAttachments.BRIBED, true);
        helper.assertFalse(BoundVex.isPrey(level, player.getUUID(), vex), "a bribed vex is not prey");
        helper.succeed();
    }

    /**
     * Scripts every roll of {@code vex} to {@code side} like {@link BoundVexTestSupport#rolling}, and counts the rolls.
     *
     * @return the number of times the dice were rolled, still counting
     */
    static AtomicInteger countingRolls(GameTestHelper helper, BoundVex vex, int side) {
        AtomicInteger rolls = new AtomicInteger();
        BribeGoal.scriptDice(vex, bound -> {
            rolls.incrementAndGet();
            return side;
        });
        whenDone(helper, () -> BribeGoal.unscriptDice(vex));
        return rolls;
    }

    /**
     * A vanilla vex with its AI on and no gravity at {@code relative} (a structure-relative point), discarded at the
     * end.
     */
    private static Vex livelyVex(GameTestHelper helper, Vec3 relative) {
        Vex vex = helper.spawn(EntityTypes.VEX, FEET);
        vex.setNoGravity(true);
        vex.setPos(helper.absoluteVec(relative));
        whenDone(helper, vex::discard);
        return vex;
    }

    /**
     * Makes every player of the level but {@code player} a spectator (which {@link FleeFromViewGoal} does not count)
     * until the test ends, when each gets its game mode back. For a test in {@link #ALONE}: the players left by
     * earlier batches would otherwise see the vex.
     */
    private static void spectateEveryoneElse(GameTestHelper helper, ServerPlayer player) {
        Map<ServerPlayer, GameType> modes = new HashMap<>();
        for (ServerPlayer other : helper.getLevel().players()) {
            if (!other.getUUID().equals(player.getUUID())) {
                modes.put(other, other.gameMode());
                other.setGameMode(GameType.SPECTATOR);
            }
        }
        whenDone(helper, () -> modes.forEach(ServerPlayer::setGameMode));
    }

    /** Whether {@code vex} holds a conjured emerald. */
    private static boolean holdsEmerald(BoundVex vex) {
        return vex.getMainHandItem().is(Items.EMERALD) && vex.isConjured(EquipmentSlot.MAINHAND);
    }

    /** Whether {@code vex} carries neither bribe mark. */
    private static boolean unmarked(Vex vex) {
        return !AllyAttachments.isBribed(vex) && !AllyAttachments.wasOfferedBribe(vex);
    }

    /** The classes of the goals in {@code selector}, in the selector's order. */
    private static List<Class<?>> goalClasses(GoalSelector selector) {
        return selector.getAvailableGoals().stream()
                .<Class<?>>map(wrapped -> wrapped.getGoal().getClass())
                .toList();
    }

    /**
     * When a staying Bound Vex finds an enemy vex that targets its summoner and the roll accepts, should hold a
     * conjured emerald while the other vex is courted, then leave the other vex bribed (no target, only the flee goal),
     * hold its sword again, and take the bribed vex as a target never.
     *
     * <p>Method under test: {@link BribeGoal}, through the vex's goal selector: spec §3.1, an accepted roll
     *
     * <p>Functional spec §9.6, §9.7.
     */
    static void aBribeThatSucceeds(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 0);
        Vex other = enemyVex(helper, vex.position(), 3.0, 0.0, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(holdsEmerald(vex), "the Bound Vex holds an emerald out"))
                .thenExecute(() -> helper.assertTrue(BribeGoal.isCourted(other), "and the other vex is courted"))
                .thenWaitUntil(() -> helper.assertTrue(AllyAttachments.isBribed(other), "the other vex is bribed"))
                .thenExecute(() -> {
                    helper.assertTrue(other.getTarget() == null, "its target is cleared");
                    helper.assertValueEqual(
                            goalClasses(other.goalSelector), List.of(FleeFromViewGoal.class), "its only goal flees");
                    helper.assertTrue(other.targetSelector.getAvailableGoals().isEmpty(), "and it has no target goals");
                    helper.assertFalse(BribeGoal.isCourted(other), "it is no longer courted");
                    helper.assertTrue(swordBack(vex), "the Bound Vex holds its sword, nothing conjured");
                })
                .thenExecuteFor(
                        40,
                        () -> helper.assertTrue(
                                !other.equals(vex.getTarget()), "no Bound Vex takes the bribed vex as a target"))
                .thenSucceed();
    }

    /**
     * When a bribed vex hangs 13 blocks from the only player behind a stone wall, should be discarded within a few
     * ticks; in plain sight five blocks from the player, with its movement held, should be discarded only after
     * {@code LIMIT} ticks.
     *
     * <p>Method under test: {@link FleeFromViewGoal#tick}: the distance and sight rule, and the tick limit
     *
     * <p>Functional spec §9.7.
     */
    static void aBribedVexLeavesOutOfSight(GameTestHelper helper) {
        // Arrange: the player stands inside the box; a wall of stone and the barrier shell are between it and the vex
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        spectateEveryoneElse(helper, player);
        for (int x = 0; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, -5), Blocks.STONE);
            }
        }
        Vex hidden = livelyVex(helper, new Vec3(1.5, 1.0, -11.5));
        helper.assertFalse(player.hasLineOfSight(hidden), "setup: the wall hides the vex");
        helper.assertTrue(player.distanceTo(hidden) >= FleeFromViewGoal.CLEAR_DISTANCE, "setup: it is 12 blocks out");
        long[] installedAt = {0};
        Vex[] seen = {null};

        // Act & Assert
        helper.startSequence()
                .thenExecute(() -> {
                    FleeFromViewGoal.install(hidden);
                    installedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(hidden.isRemoved(), "the hidden vex is discarded"))
                .thenExecute(() -> {
                    long took = helper.getTick() - installedAt[0];
                    helper.assertTrue(took < 20, "it went at once, not at the limit, after " + took);
                    MockPlayers.moveTo(helper, player, new Vec3(1.5, 1.0, -6.5));
                    Vex visible = livelyVex(helper, new Vec3(1.5, 1.0, -11.5));
                    BoundVexTestSupport.holdAt(helper, visible, visible.position());
                    helper.assertTrue(player.hasLineOfSight(visible), "setup: the second vex is in plain sight");
                    FleeFromViewGoal.install(visible);
                    installedAt[0] = helper.getTick();
                    seen[0] = visible;
                })
                .thenExecuteFor(
                        FleeFromViewGoal.LIMIT - 20,
                        () -> helper.assertFalse(seen[0].isRemoved(), "a vex in sight and near is not discarded"))
                .thenWaitUntil(() -> helper.assertTrue(seen[0].isRemoved(), "discarded at the limit"))
                .thenExecute(() -> {
                    long took = helper.getTick() - installedAt[0];
                    helper.assertTrue(
                            took >= FleeFromViewGoal.LIMIT - 5, "only after the limit, not before it: " + took);
                })
                .thenSucceed();
    }

    /**
     * When the roll refuses, should the other vex carry only the bribe-tried mark, the Bound Vex then take it as a
     * target, and no emerald be conjured again in sixty ticks.
     *
     * <p>Method under test: {@link BribeGoal}: a refused roll, and the bribe-tried mark
     *
     * <p>Functional spec §9.6.
     */
    static void aBribeThatFails(GameTestHelper helper) {
        // Arrange: the other vex soaks up the Bound Vex's blows, so the test can watch the target
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 2);
        Vex other = enemyVex(helper, vex.position(), 3.0, 0.0, player);
        other.setAbsorptionAmount(100000.0F);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AllyAttachments.wasOfferedBribe(other), "the offer was made"))
                .thenExecute(() -> {
                    helper.assertFalse(AllyAttachments.isBribed(other), "and refused");
                    helper.assertFalse(BribeGoal.isCourted(other), "the other vex is prey again");
                    helper.assertTrue(swordBack(vex), "the Bound Vex holds its sword again");
                })
                .thenWaitUntil(() ->
                        helper.assertTrue(other.equals(vex.getTarget()), "the Bound Vex then targets the other vex"))
                .thenExecuteFor(60, () -> {
                    helper.assertFalse(holdsEmerald(vex), "no emerald is conjured again");
                    helper.assertFalse(BribeGoal.isCourted(other), "and the other vex is not courted again");
                })
                .thenSucceed();
    }

    /**
     * When a Bound Vex courts an enemy vex (it holds a live claim), should it not be free and not be listed.
     *
     * <p>Method under test: {@code Jobs.busy} (the bribe wiring), through {@link TeamMoves#isFree}
     *
     * <p>Functional spec §11.2.
     */
    static void courtingVexIsNotFree(GameTestHelper helper) {
        // Arrange (as aCourtedVexIsNotPrey)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex first = stayingVex(helper, player, ItemStack.EMPTY);
        BoundVex second = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, first, 2);
        rolling(helper, second, 2);
        Vex other = enemyVex(helper, first.position(), 3.0, 0.0, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        BribeGoal.isCourted(other) && (holdsEmerald(first) || holdsEmerald(second)),
                        "setup: a Bound Vex courts it"))
                .thenExecute(() -> {
                    BoundVex worker = holdsEmerald(first) ? first : second;
                    worker.setStaying(false);
                    TeamMoveTestSupport.assertFreeButForJobs(helper, worker, player);
                    helper.assertFalse(TeamMoves.isFree(worker, player), "a vex mid-courtship is not free");
                    helper.assertFalse(TeamMoves.freeVexes(player).contains(worker), "and is not listed");
                })
                .thenSucceed();
    }

    /**
     * When one of two Bound Vexes courts an enemy vex, should neither take it as a target and a hit on it by either
     * leave its health unchanged; after a refused roll, should the same hit hurt it.
     *
     * <p>Method under test: {@link BoundVex#isPrey} (the courted clause), through the target goals and the
     * friendly-fire backstop {@code BoundVexEvents.onIncomingDamage}
     *
     * <p>Functional spec §7.4, §7.9, §9.6.
     */
    static void aCourtedVexIsNotPrey(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex first = stayingVex(helper, player, ItemStack.EMPTY);
        BoundVex second = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, first, 2);
        rolling(helper, second, 2);
        Vex other = enemyVex(helper, first.position(), 3.0, 0.0, player);
        ServerLevel level = helper.getLevel();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BribeGoal.isCourted(other), "setup: a Bound Vex courts it"))
                .thenIdle(3)
                .thenExecuteFor(10, () -> {
                    helper.assertTrue(BribeGoal.isCourted(other), "still courted");
                    helper.assertTrue(
                            !other.equals(first.getTarget()) && !other.equals(second.getTarget()),
                            "neither Bound Vex takes the courted vex as a target");
                })
                .thenExecute(() -> {
                    float before = other.getHealth();
                    other.hurtServer(level, level.damageSources().mobAttack(first), 4.0F);
                    other.hurtServer(level, level.damageSources().mobAttack(second), 4.0F);
                    helper.assertValueEqual(
                            other.getHealth(), before, "a hit by a Bound Vex on a courted vex is canceled");
                })
                .thenWaitUntil(() -> helper.assertTrue(AllyAttachments.wasOfferedBribe(other), "the roll refuses"))
                .thenExecute(() -> {
                    float before = other.getHealth();
                    other.hurtServer(level, level.damageSources().mobAttack(second), 4.0F);
                    helper.assertTrue(other.getHealth() < before, "after the refusal the same hit hurts it");
                })
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex has a vanilla vex with no target, a vanilla vex targeting a pig, and another player's
     * Bound Vex targeting its summoner within its area for sixty ticks, should offer none of them an emerald.
     *
     * <p>Method under test: {@link BribeGoal#canUse}: the target clause and the exact vex type
     *
     * <p>Functional spec §9.6.
     */
    static void noOfferToAVexThatIsNotAttackingYourSide(GameTestHelper helper) {
        // Arrange: the enemies soak up the Bound Vex's blows, so they live for the whole window
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 0);
        Vec3 post = vex.position();
        Pig pig = helper.spawn(EntityTypes.PIG, FEET);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setPos(post.add(0.0, 0.0, 3.0));
        whenDone(helper, pig::discard);
        Vex idle = enemyVex(helper, post, 3.0, 0.0, null);
        Vex hunter = enemyVex(helper, post, -3.0, 0.0, pig);
        idle.setAbsorptionAmount(100000.0F);
        hunter.setAbsorptionAmount(100000.0F);
        BoundVex rival = noTargets(BoundVexTestSupport.parked(helper, AIR_SPOT, stranger));
        rival.setPos(post.add(0.0, 0.0, -3.0));
        rival.setTarget(player);
        rival.setAbsorptionAmount(100000.0F);
        helper.assertTrue(player.equals(rival.getTarget()), "setup: the rival's Bound Vex targets the summoner");

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(60, () -> {
                    helper.assertFalse(holdsEmerald(vex), "no emerald is held out");
                    helper.assertTrue(
                            unmarked(idle) && unmarked(hunter) && unmarked(rival), "nobody was offered a bribe");
                    helper.assertFalse(BribeGoal.isCourted(idle) || BribeGoal.isCourted(hunter), "nobody is courted");
                })
                .thenSucceed();
    }

    /**
     * When the vanilla vex dies in the offer step, should the Bound Vex hold its sword again, the claim be free and
     * the corpse carry no mark.
     *
     * <p>Method under test: {@link BribeGoal#canContinueToUse}: the other-vex-alive clause, and {@code stop}
     */
    static void theOfferEndsIfTheVexDies(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 0);
        Vex other = enemyVex(helper, vex.position(), 3.0, 0.0, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(holdsEmerald(vex), "setup: in the offer step"))
                .thenExecute(() -> other.kill(helper.getLevel()))
                .thenWaitUntil(() -> helper.assertTrue(swordBack(vex), "the Bound Vex holds its sword again"))
                .thenExecute(() -> {
                    helper.assertFalse(BribeGoal.isCourted(other), "the claim is free");
                    helper.assertTrue(unmarked(other), "and the dead vex carries no mark");
                })
                .thenSucceed();
    }

    /**
     * When the Bound Vex cannot reach the other vex (held in place), should give up after {@code APPROACH_LIMIT} ticks
     * with no mark on the other vex and its sword in hand, start no new bribe for {@code RETRY_DELAY} ticks, and then
     * try again.
     *
     * <p>Method under test: {@link BribeGoal#canContinueToUse}: the approach timeout, and {@code BribeGoal.RETRY_DELAY}
     */
    static void anApproachTimeoutLeavesNoMark(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 2);
        Vec3 post = vex.position();
        BoundVexTestSupport.holdAt(helper, vex, post);
        Vex other = enemyVex(helper, post, 5.0, 0.0, player);
        long[] at = {0, 0};

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BribeGoal.isCourted(other), "setup: claimed"))
                .thenExecute(() -> at[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertFalse(BribeGoal.isCourted(other), "released after the timeout"))
                .thenExecute(() -> {
                    at[1] = helper.getTick();
                    long waited = at[1] - at[0];
                    helper.assertTrue(
                            waited >= BribeGoal.APPROACH_LIMIT - 10, "it did not give up early, after " + waited);
                    helper.assertTrue(unmarked(other), "no mark on the other vex");
                    helper.assertTrue(swordBack(vex), "the hand is untouched");
                })
                .thenExecuteFor(
                        30,
                        () -> helper.assertFalse(
                                BribeGoal.isCourted(other), "and not claimed again at once by the same vex"))
                .thenWaitUntil(() -> helper.assertTrue(BribeGoal.isCourted(other), "it tries again"))
                .thenExecute(() -> {
                    long pause = helper.getTick() - at[1];
                    helper.assertTrue(
                            pause >= BribeGoal.RETRY_DELAY - 10, "after the retry delay, not before it: " + pause);
                })
                .thenSucceed();
    }

    /**
     * When the vanilla vex is moved out of the stay area mid-offer, then (offered again) a swelling creeper comes
     * beside the Bound Vex mid-offer, should each time end with no mark, the sword in hand and the claim free; and the
     * vanilla vex is prey again.
     *
     * <p>Method under test: {@link BribeGoal}: the leaves-the-area clause, and {@code stop} as the abort through
     * {@link FleeCreeperGoal} taking the move flag
     *
     * <p>Functional spec §9.8.
     */
    static void theOfferEndsIfTheVexLeavesOrTheBriberFlees(GameTestHelper helper) {
        // Arrange: no target goals, so the Bound Vex does not wander off to fight; a staying vex keeps its area fixed
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = noTargets(stayingVex(helper, player, ItemStack.EMPTY));
        rolling(helper, vex, 2);
        Vec3 post = vex.position();
        Vex other = enemyVex(helper, post, 3.0, 0.0, player);
        Creeper creeper = patientCreeper(helper, FEET);
        creeper.setNoGravity(true);
        ServerLevel level = helper.getLevel();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(holdsEmerald(vex), "setup: in the offer step"))
                .thenExecute(() -> other.setPos(post.add(9.0, 0.0, 0.0)))
                .thenWaitUntil(() -> {
                    helper.assertFalse(BribeGoal.isCourted(other), "the claim is free");
                    helper.assertTrue(swordBack(vex), "the Bound Vex holds its sword again");
                })
                .thenExecuteFor(30, () -> {
                    helper.assertTrue(unmarked(other), "no mark on a vex that left");
                    helper.assertFalse(BribeGoal.isCourted(other), "and it is not claimed out there");
                })
                .thenExecute(() -> other.setPos(post.add(3.0, 0.0, 0.0)))
                .thenWaitUntil(() -> helper.assertTrue(holdsEmerald(vex), "back in the area it is offered again"))
                .thenExecute(() -> {
                    creeper.setPos(post.add(0.0, 0.0, 3.0));
                    creeper.setSwellDir(1);
                })
                .thenWaitUntil(() -> {
                    helper.assertFalse(BribeGoal.isCourted(other), "the claim is free after the flight began");
                    helper.assertTrue(swordBack(vex), "the Bound Vex holds its sword again");
                })
                .thenExecute(() -> {
                    helper.assertTrue(unmarked(other), "no mark on the other vex");
                    helper.assertTrue(BoundVex.isPrey(level, player.getUUID(), other), "the other vex is prey again");
                })
                .thenSucceed();
    }

    /**
     * When a vex that carries the bribed mark is saved and loaded into a fresh entity and added to the level, should
     * the copy keep the mark and have the flee goal as its only goal and no target goals.
     *
     * <p>Method under test: {@code BoundVexEvents.onJoin}: the reinstall on join, keyed on the mark
     *
     * <p>Functional spec §9.7.
     */
    static void aBribedVexLoadedFromASaveOnlyFlees(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        Vex vex = helper.spawn(EntityTypes.VEX, FEET);
        vex.setData(AllyAttachments.BRIBED, true);
        Vex copy = (Vex) GameTestSupport.reloaded(level, vex);
        vex.discard();
        whenDone(helper, copy::discard);
        helper.assertTrue(AllyAttachments.isBribed(copy), "setup: the mark is kept");
        helper.assertTrue(copy.goalSelector.getAvailableGoals().size() > 1, "setup: a fresh vex has its vanilla goals");

        // Act
        level.addFreshEntity(copy);

        // Assert
        helper.assertValueEqual(goalClasses(copy.goalSelector), List.of(FleeFromViewGoal.class), "only the flee goal");
        helper.assertTrue(copy.targetSelector.getAvailableGoals().isEmpty(), "and no target goals");
        helper.succeed();
    }

    /**
     * When a staying vex has an enemy vex five blocks from its post and another nine blocks away, should offer the near
     * one and leave the far one (outside the stay area) alone.
     *
     * <p>Method under test: {@link BribeGoal#canUse}: the stay area, through {@code BoundVex.inGuardArea}
     *
     * <p>Functional spec §9.8.
     */
    static void aStayingVexBribesOnlyInItsArea(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 2);
        Vec3 post = vex.position();
        Vex near = enemyVex(helper, post, 5.0, 0.0, player);
        Vex far = enemyVex(helper, post, -9.0, 0.0, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(AllyAttachments.wasOfferedBribe(near), "the near one is offered"))
                .thenExecuteFor(40, () -> {
                    helper.assertTrue(unmarked(far), "the far one is not offered");
                    helper.assertFalse(BribeGoal.isCourted(far), "and not courted");
                })
                .thenSucceed();
    }

    /**
     * When four staying Bound Vexes (both entity-id parities), each with its own enemy vex and its own dice that count
     * their rolls and refuse, make their offers, should every dice be rolled exactly once, every enemy vex carry the
     * bribe-tried mark only, and none be bribed.
     *
     * <p>Method under test: {@link BribeGoal#tick} and its roll: the roll happens once, though vanilla ticks a goal
     * that requires an update every tick on ticks {@code canContinueToUse} is not asked
     *
     * <p>Functional spec §9.6.
     */
    static void aRefusedVexIsRolledForExactlyOnce(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> bribers = new ArrayList<>();
        List<AtomicInteger> rolls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BoundVex briber = stayingVex(helper, player, ItemStack.EMPTY);
            bribers.add(briber);
            rolls.add(countingRolls(helper, briber, 2));
        }
        Vec3 post = bribers.get(0).position();
        List<Vex> enemies = List.of(
                enemyVex(helper, post, 3.0, 0.0, player),
                enemyVex(helper, post, -3.0, 0.0, player),
                enemyVex(helper, post, 0.0, 3.0, player),
                enemyVex(helper, post, 0.0, -3.0, player));
        enemies.forEach(enemy -> enemy.setAbsorptionAmount(100000.0F));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        enemies.stream().allMatch(AllyAttachments::wasOfferedBribe), "every enemy vex was offered"))
                .thenIdle(40)
                .thenExecute(() -> {
                    for (int i = 0; i < 4; i++) {
                        helper.assertValueEqual(rolls.get(i).get(), 1, "Bound Vex " + i + " rolled exactly once");
                    }
                    helper.assertTrue(
                            enemies.stream().noneMatch(AllyAttachments::isBribed), "no refused vex is bribed");
                    helper.assertTrue(enemies.stream().allMatch(BoundVexTestSupport::exactlyOneMark), "one mark each");
                })
                .thenSucceed();
    }

    /**
     * When four staying Bound Vexes (two roll to accept, two to refuse) and three enemy vexes that all target the
     * summoner share one stay area, should each enemy vex be offered exactly once (exactly one mark each), the dice be
     * rolled exactly three times in all, and no Bound Vex hold a courted vex as its target at any tick.
     *
     * <p>Method under test: {@link BribeGoal}: the claims and the single roll, with more briber than prey
     *
     * <p>Functional spec §9.6.
     */
    static void eachEnemyVexGetsExactlyOneOffer(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> bribers = new ArrayList<>();
        List<AtomicInteger> rolls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BoundVex briber = stayingVex(helper, player, ItemStack.EMPTY);
            bribers.add(briber);
            rolls.add(countingRolls(helper, briber, i % 2 == 0 ? 0 : 2));
        }
        Vec3 post = bribers.get(0).position();
        List<Vex> enemies = List.of(
                enemyVex(helper, post, 3.0, 0.0, player),
                enemyVex(helper, post, -3.0, 0.0, player),
                enemyVex(helper, post, 0.0, 3.0, player));
        enemies.forEach(enemy -> enemy.setAbsorptionAmount(100000.0F));
        // A target goal asks canContinueToUse only on every second tick, so a target taken before the claim goes within
        // a tick or two; holding a courted vex for longer than LAG consecutive ticks is the failure.
        int[] streak = new int[bribers.size()];
        AtomicBoolean heldACourtedTarget = new AtomicBoolean();
        BoundVexTestSupport.everyTick(helper, () -> {
            for (int i = 0; i < streak.length; i++) {
                LivingEntity target = bribers.get(i).getTarget();
                streak[i] = target != null && BribeGoal.isCourted(target) ? streak[i] + 1 : 0;
                if (streak[i] > COURTED_TARGET_LAG) {
                    heldACourtedTarget.set(true);
                }
            }
        });

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        enemies.stream().allMatch(BoundVexTestSupport::exactlyOneMark),
                        "every enemy vex was offered and carries a mark"))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            rolls.stream().mapToInt(AtomicInteger::get).sum(), 3, "the dice were rolled three times");
                    helper.assertTrue(
                            enemies.stream().allMatch(BoundVexTestSupport::exactlyOneMark), "still one mark each");
                    helper.assertFalse(heldACourtedTarget.get(), "no Bound Vex held a courted vex as its target");
                })
                .thenSucceed();
    }

    /**
     * When the claim on an enemy vex is made older than the longest courtship, should the vex no longer count as
     * courted and be prey again, though the claimant still lives.
     *
     * <p>Method under test: {@link BribeGoal#isCourted}: the stale-claim clause
     */
    static void aStaleClaimIsFree(GameTestHelper helper) {
        // Arrange: the claimant is held in place, so its approach drags on
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 2);
        Vec3 post = vex.position();
        BoundVexTestSupport.holdAt(helper, vex, post);
        Vex other = enemyVex(helper, post, 5.0, 0.0, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BribeGoal.isCourted(other), "setup: claimed"))
                .thenExecute(() -> {
                    BribeGoal.ageClaim(other, BribeGoal.APPROACH_LIMIT + BribeGoal.OFFER_TICKS + 21);
                    helper.assertTrue(vex.isAlive(), "the claimant lives");
                    helper.assertFalse(BribeGoal.isCourted(other), "an old claim is free");
                    helper.assertTrue(
                            BoundVex.isPrey(helper.getLevel(), player.getUUID(), other), "and the vex is prey again");
                })
                .thenSucceed();
    }

    /**
     * When the server stops while a Bound Vex courts an enemy vex (its approach held up, so the claim stands), should
     * the claim be free as soon as the stop handler has run. The handler runs on its own batch
     * ({@link #SERVER_STOP_CLAIMS}): it clears every claim on the server.
     *
     * <p>Method under test: {@link BoundVexEvents#onServerStopped}, through {@code BribeGoal}'s claim map
     */
    static void serverStopFreesTheBribeClaims(GameTestHelper helper) {
        // Arrange: the claimant is held in place, so its approach drags on and the claim stands
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 2);
        Vec3 post = vex.position();
        BoundVexTestSupport.holdAt(helper, vex, post);
        Vex other = enemyVex(helper, post, 5.0, 0.0, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BribeGoal.isCourted(other), "setup: the claim stands"))
                .thenExecute(() -> {
                    BoundVexEvents.onServerStopped(
                            new ServerStoppedEvent(helper.getLevel().getServer()));
                    helper.assertFalse(BribeGoal.isCourted(other), "the stop handler freed the claim");
                })
                .thenSucceed();
    }

    /**
     * When the summoner becomes a spectator (which counts as absent) while a Bound Vex holds the emerald out, though
     * the roll is scripted to accept, should the offer end with no mark on the enemy vex, the claim free and the sword
     * back in hand, and stay ended.
     *
     * <p>Method under test: {@link BribeGoal#canContinueToUse}: the summoner-present clause, and {@code stop}
     *
     * <p>Functional spec §5.5.
     */
    static void theSummonerGoingAbsentEndsTheOffer(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        rolling(helper, vex, 0);
        Vex other = enemyVex(helper, vex.position(), 3.0, 0.0, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(holdsEmerald(vex), "setup: in the offer step"))
                .thenExecute(() -> player.setGameMode(GameType.SPECTATOR))
                .thenWaitUntil(() -> {
                    helper.assertTrue(swordBack(vex), "the Bound Vex holds its sword again");
                    helper.assertFalse(BribeGoal.isCourted(other), "the claim is free");
                })
                .thenExecuteFor(30, () -> {
                    helper.assertTrue(unmarked(other), "no mark on the other vex, though the roll would accept");
                    helper.assertFalse(holdsEmerald(vex), "and no new offer while the summoner is absent");
                })
                .thenSucceed();
    }

    /**
     * When a bribed vex is moved to the Nether with the call {@code BoundVexEvents.bring} uses, should the arrived
     * entity (a new instance, found by its UUID in the Nether) have the flee goal as its only goal and no target
     * goals: the join handler puts them back.
     *
     * <p>Method under test: {@code BoundVexEvents.onJoin}, for an entity that arrives from another dimension
     *
     * <p>Functional spec §9.7.
     */
    static void aBribedVexArrivingInAnotherDimensionOnlyFlees(GameTestHelper helper) {
        // Arrange: a stone pocket in the Nether at the vex's own coordinates; the vex has no AI, so it neither flees
        // nor is discarded for want of a player before the assertions run
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(AIR_SPOT));
        BoundVexTestSupport.huskInNether(helper, at).discard();
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw helper.assertionException("no Nether on the GameTest server");
        }
        Vex vex = helper.spawn(EntityTypes.VEX, FEET);
        vex.setNoAi(true);
        vex.setNoGravity(true);
        vex.setPos(at);
        vex.setData(AllyAttachments.BRIBED, true);
        FleeFromViewGoal.install(vex);
        UUID id = vex.getUUID();
        Entity[] returned = {null};
        whenDone(helper, () -> {
            vex.discard();
            if (returned[0] != null) {
                returned[0].discard();
            }
        });
        helper.assertValueEqual(goalClasses(vex.goalSelector), List.of(FleeFromViewGoal.class), "setup: it flees");

        // Act & Assert: the arrived vex joins the lookup a tick later (the Nether's chunk is promoted by then), and
        // it has no AI, so the flee goal cannot have run and discarded it
        helper.startSequence()
                .thenExecute(() -> {
                    returned[0] = vex.teleport(new TeleportTransition(
                            nether, at, Vec3.ZERO, vex.getYRot(), vex.getXRot(), TeleportTransition.DO_NOTHING));
                    helper.assertTrue(returned[0] instanceof Vex, "setup: the move made a new vex");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        nether.getEntities().get(id) instanceof Vex, "setup: the vex is in the Nether, found by UUID"))
                .thenExecute(() -> {
                    Vex arrived = (Vex) nether.getEntities().get(id);
                    helper.assertFalse(arrived.equals(vex), "a new instance");
                    helper.assertTrue(AllyAttachments.isBribed(arrived), "the mark came along");
                    helper.assertValueEqual(
                            goalClasses(arrived.goalSelector),
                            List.of(FleeFromViewGoal.class),
                            "its only goal is the flee goal");
                    helper.assertTrue(
                            arrived.targetSelector.getAvailableGoals().isEmpty(), "and it has no target goals");
                })
                .thenSucceed();
    }
}
