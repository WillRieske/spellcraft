package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.frozenVexes;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** GameTests of the free-vex test and the busy refusals (Parts 6-7 spec section 2.2, section 2.6). */
public final class BoundVexTeamMoveRosterGameTests {
    private BoundVexTeamMoveRosterGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(
                SHARED, "team_move_free_a_following_vex", BoundVexTeamMoveRosterGameTests::followingVexIsFree);
        FangGameTests.addPadded(
                SHARED, "team_move_free_not_a_staying_vex", BoundVexTeamMoveRosterGameTests::stayingVexIsNotFree);
        FangGameTests.addPadded(
                SHARED, "team_move_free_not_a_marked_vex", BoundVexTeamMoveRosterGameTests::markedVexIsNotFree);
        FangGameTests.addPadded(
                SHARED,
                "team_move_free_not_another_summoners_vex",
                BoundVexTeamMoveRosterGameTests::strangersVexIsNotFree);
        FangGameTests.addPadded(
                SHARED,
                "team_move_free_not_a_vex_in_another_level",
                BoundVexTeamMoveRosterGameTests::otherLevelVexIsNotFree);
        FangGameTests.addPadded(
                SHARED,
                "team_move_free_not_a_summoned_by_command_vex",
                BoundVexTeamMoveRosterGameTests::unmarkedVexIsNotFree);
        FangGameTests.addPadded(
                SHARED, "team_move_busy_refuses_the_stay_click", BoundVexTeamMoveRosterGameTests::busyRefusesStay);
        FangGameTests.addPadded(
                SHARED,
                "team_move_busy_refuses_the_take_back_click",
                BoundVexTeamMoveRosterGameTests::busyRefusesTakeBack);
        FangGameTests.addPadded(
                SHARED, "team_move_busy_still_takes_a_gift", BoundVexTeamMoveRosterGameTests::busyTakesAGift);
        FangGameTests.addPadded(
                SHARED, "team_move_free_not_a_vex_beyond_32_blocks", BoundVexTeamMoveRosterGameTests::rangeIsThirtyTwo);
        FangGameTests.addPadded(
                SHARED, "team_move_free_not_a_dead_vex", BoundVexTeamMoveRosterGameTests::deadVexIsNotFree);
        FangGameTests.addPadded(
                SHARED,
                "team_move_busy_a_strangers_click_is_not_refused",
                BoundVexTeamMoveRosterGameTests::strangerIsNotToldBusy);
    }

    /**
     * When a summoner has three following vexes with no mark, no job and no rescue near them, should every one be free and
     * listed.
     *
     * <p>Method under test: {@link TeamMoves#isFree}, {@link TeamMoves#freeVexes}
     */
    static void followingVexIsFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 3);

        // Act
        List<BoundVex> free = TeamMoves.freeVexes(player);

        // Assert
        helper.assertValueEqual(free.size(), 3, "three free vexes");
        helper.assertTrue(TeamMoves.isFree(vexes.get(0), player), "the first one is free");
        helper.succeed();
    }

    /**
     * When one of three vexes is told to stay, should it not be free and the other two should be.
     *
     * <p>Method under test: {@link TeamMoves#isFree}
     */
    static void stayingVexIsNotFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 3);
        vexes.get(1).setStaying(true);

        // Act & Assert
        helper.assertFalse(TeamMoves.isFree(vexes.get(1), player), "a staying vex is not free");
        helper.assertValueEqual(TeamMoves.freeVexes(player).size(), 2, "the other two are");
        helper.succeed();
    }

    /**
     * When a vex already carries a team move, should it not be free.
     *
     * <p>Method under test: {@link TeamMoves#isFree}
     */
    static void markedVexIsNotFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 2);
        vexes.get(0).setTeamMove(TeamMove.SHIELD);

        // Act & Assert
        helper.assertFalse(TeamMoves.isFree(vexes.get(0), player), "a vex in a move is not free");
        helper.assertValueEqual(TeamMoves.freeVexes(player), List.of(vexes.get(1)), "only the other is listed");
        helper.succeed();
    }

    /**
     * When another player's vex hangs beside the summoner's, should the summoner's list leave it out.
     *
     * <p>Method under test: {@link TeamMoves#freeVexes}
     */
    static void strangersVexIsNotFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveIntoTest(helper, stranger);
        List<BoundVex> mine = frozenVexes(helper, player, 1);
        BoundVex theirs = BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, stranger);
        theirs.setNoAi(true);

        // Act & Assert
        helper.assertValueEqual(TeamMoves.freeVexes(player), mine, "only my vex is free for me");
        helper.assertFalse(TeamMoves.isFree(theirs, player), "a stranger's vex is not");
        helper.succeed();
    }

    /**
     * When the summoner stands in the Nether and the vexes are in the test level, at the summoner's own coordinates, should
     * none be free (only the level differs).
     *
     * <p>Method under test: {@link TeamMoves#isFree}: the same-level clause
     */
    static void otherLevelVexIsNotFree(GameTestHelper helper) {
        // Arrange: the summoner moves first; vexes that exist during the move would follow it (BoundVexEvents.onTravel)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 3, 4);
        List<BoundVex> vexes = frozenVexes(helper, player, 3);
        // Same coordinates as the vexes, so the range clause cannot be what rejects them
        player.setPos(vexes.get(0).position());

        // Act & Assert
        helper.assertValueEqual(
                vexes.get(0).level().dimension(), Level.OVERWORLD, "setup: the vex stayed in the test level");
        helper.assertValueEqual(player.level().dimension(), Level.NETHER, "setup: the summoner is in the Nether");
        helper.assertTrue(vexes.get(0).distanceTo(player) <= TeamMoves.FREE_RANGE, "setup: only the level differs");
        helper.assertFalse(TeamMoves.isFree(vexes.get(0), player), "a vex in another level is not free");
        helper.assertTrue(TeamMoves.freeVexes(player).isEmpty(), "nobody is free across levels");
        helper.succeed();
    }

    /**
     * When a vex has no summoner mark (as {@code /summon} makes one), should it never be free for anyone.
     *
     * <p>Method under test: {@link TeamMoves#isFree}: the summoner clause
     */
    static void unmarkedVexIsNotFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = BoundVexTestSupport.unmarkedVex(helper, BoundVexTestSupport.AIR_SPOT);

        // Act & Assert
        helper.assertFalse(TeamMoves.isFree(vex, player), "a vex nobody summoned is not free");
        helper.assertTrue(TeamMoves.freeVexes(player).isEmpty(), "and is not listed");
        helper.succeed();
    }

    /**
     * When the summoner sneak-clicks a vex that is in a team move with an empty hand, should it answer "That vex is
     * busy" and keep following.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void busyRefusesStay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = frozenVexes(helper, player, 1).get(0);
        vex.setTeamMove(TeamMove.SHIELD);
        GameTestSupport.hold(player, ItemStack.EMPTY);
        player.setShiftKeyDown(true);

        // Act
        InteractionResult result = vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);

        // Assert
        helper.assertValueEqual(result, InteractionResult.SUCCESS_SERVER, "the click is answered");
        helper.assertFalse(vex.isStaying(), "the vex still follows");
        GameTestSupport.assertActionBarKey(helper, player, BoundVex.BUSY_KEY);
        helper.succeed();
    }

    /**
     * When the summoner clicks a vex in a team move with an empty hand, should it answer "That vex is busy" and keep the
     * sword in its hand.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void busyRefusesTakeBack(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = frozenVexes(helper, player, 1).get(0);
        vex.setTeamMove(TeamMove.SHIELD);
        GameTestSupport.hold(player, ItemStack.EMPTY);

        // Act
        vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);

        // Assert
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "the sword stays");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "and the player's hand stays empty");
        GameTestSupport.assertActionBarKey(helper, player, BoundVex.BUSY_KEY);
        helper.succeed();
    }

    /**
     * When the summoner gives a diamond sword to a vex in a team move, should the gift be taken as always (the refusal is
     * for the empty hand only) and no busy message come.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void busyTakesAGift(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = frozenVexes(helper, player, 1).get(0);
        vex.setTeamMove(TeamMove.SHIELD);
        GameTestSupport.hold(player, new ItemStack(Items.DIAMOND_SWORD));

        // Act
        vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);

        // Assert
        helper.assertTrue(vex.getMainHandItem().is(Items.DIAMOND_SWORD), "the gift is taken");
        helper.assertTrue(GameTestSupport.drainActionBar(player).isEmpty(), "and nothing is said");
        helper.succeed();
    }

    /**
     * When the summoner is 31.9 blocks from a following vex, should the vex be free, and when 32.1 blocks, should it not.
     *
     * <p>Method under test: {@link TeamMoves#isFree}: the range clause
     */
    static void rangeIsThirtyTwo(GameTestHelper helper) {
        // Arrange (a mock player is never ticked, so the player, not the vex, is moved with setPos)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = frozenVexes(helper, player, 1).get(0);
        Vec3 at = vex.position();

        // Act & Assert (31.9 and 32.1 avoid a float-rounding edge in distanceTo)
        player.setPos(at.x + 31.9, at.y, at.z);
        helper.assertTrue(TeamMoves.isFree(vex, player), "inside 32 blocks");
        player.setPos(at.x + 32.1, at.y, at.z);
        helper.assertFalse(TeamMoves.isFree(vex, player), "outside 32 blocks");
        helper.succeed();
    }

    /**
     * When a following vex is killed, should it not be free and not be listed.
     *
     * <p>Method under test: {@link TeamMoves#isFree}: the alive clause
     */
    static void deadVexIsNotFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 2);
        vexes.get(0).kill(helper.getLevel());

        // Act & Assert
        helper.assertFalse(TeamMoves.isFree(vexes.get(0), player), "a dead vex is not free");
        helper.assertValueEqual(TeamMoves.freeVexes(player), List.of(vexes.get(1)), "only the live vex is listed");
        helper.succeed();
    }

    /**
     * When a player who did not summon the vex clicks it with an empty hand, should the vex in a team move answer no busy
     * message (the {@code isSummonedBy} check comes first).
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void strangerIsNotToldBusy(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveIntoTest(helper, stranger);
        BoundVex vex = frozenVexes(helper, player, 1).get(0);
        vex.setTeamMove(TeamMove.SHIELD);
        GameTestSupport.hold(stranger, ItemStack.EMPTY);

        // Act
        InteractionResult result = vex.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO);

        // Assert
        helper.assertValueEqual(result, InteractionResult.PASS, "a stranger's click is passed on");
        helper.assertTrue(GameTestSupport.drainActionBar(stranger).isEmpty(), "and no busy message is sent");
        helper.succeed();
    }
}
