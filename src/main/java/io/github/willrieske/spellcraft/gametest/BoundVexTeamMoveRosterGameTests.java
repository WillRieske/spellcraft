package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.beside;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.bigHusk;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.foe;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.frozenVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.setBase;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.Dice;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of the free-vex test and the busy refusals (Parts 6-7 spec section 2.2, section 2.6).
 *
 * <p>Functional spec §5.2, §5.6, §8.13, §11.1, §11.2, §11.3, §11.5, §11.7, §11.8, §11.9, §11.10, §11.11.
 */
final class BoundVexTeamMoveRosterGameTests {
    private BoundVexTeamMoveRosterGameTests() {}

    /** Registers the tests. */
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
        FangGameTests.addPadded(
                SHARED, "team_move_start_shield_three", BoundVexTeamMoveRosterGameTests::startShieldThree);
        FangGameTests.addPadded(
                SHARED, "team_move_start_shield_nine", BoundVexTeamMoveRosterGameTests::startShieldNine);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_refused_with_two_free",
                BoundVexTeamMoveRosterGameTests::startRefusedWithTwoFree);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_multishot_targeted_vex_is_in",
                BoundVexTeamMoveRosterGameTests::startMultishotTargetedVexIsIn);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_multishot_nearest_when_nothing_hunts",
                BoundVexTeamMoveRosterGameTests::startMultishotNearestWhenNothingHunts);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_multishot_hunted_vex_not_free_falls_back",
                BoundVexTeamMoveRosterGameTests::startMultishotHuntedVexNotFree);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_evader_nearest_three",
                BoundVexTeamMoveRosterGameTests::startEvaderNearestThree);
        FangGameTests.addPadded(
                SHARED, "team_move_start_raid_twelve_geared", BoundVexTeamMoveRosterGameTests::startRaidTwelveGeared);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_raid_takes_twelve_of_thirteen",
                BoundVexTeamMoveRosterGameTests::startRaidTakesTwelveOfThirteen);
        FangGameTests.addPadded(
                SHARED, "team_move_start_stashes_given_gear", BoundVexTeamMoveRosterGameTests::startStashesGivenGear);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_refused_while_running",
                BoundVexTeamMoveRosterGameTests::startRefusedWhileRunning);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_refused_without_a_subject",
                BoundVexTeamMoveRosterGameTests::startRefusedWithoutASubject);
        FangGameTests.addPadded(
                SHARED,
                "team_move_start_refused_with_an_unusable_subject",
                BoundVexTeamMoveRosterGameTests::startRefusedWithAnUnusableSubject);
        FangGameTests.addPadded(
                SHARED, "team_move_start_message_once", BoundVexTeamMoveRosterGameTests::startMessageOnce);
        SpellcraftGameTests.add(
                "team_move_dice", "team_move_dice_script_rules", BoundVexTeamMoveRosterGameTests::diceScriptRules);
    }

    /**
     * When a summoner has three following vexes with no mark, no job and no rescue near them, should every one be free
     * and listed.
     *
     * <p>Method under test: {@link TeamMoves#isFree}, {@link TeamMoves#freeVexes}
     *
     * <p>Functional spec §11.2.
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
     *
     * <p>Functional spec §11.2.
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
     *
     * <p>Functional spec §11.2.
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
     * When the summoner stands in the Nether and the vexes are in the test level, at the summoner's own coordinates,
     * should none be free (only the level differs).
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
     *
     * <p>Functional spec §5.2.
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
     *
     * <p>Functional spec §11.7.
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
     * When the summoner clicks a vex in a team move with an empty hand, should it answer "That vex is busy" and keep
     * the sword in its hand.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     *
     * <p>Functional spec §11.7.
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
     * When the summoner gives a diamond sword to a vex in a team move, should the gift be taken as always (the refusal
     * is for the empty hand only) and no busy message come.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     *
     * <p>Functional spec §11.7.
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
     * When the summoner is 31.9 blocks from a following vex, should the vex be free, and when 32.1 blocks, should it
     * not.
     *
     * <p>Method under test: {@link TeamMoves#isFree}: the range clause
     *
     * <p>Functional spec §11.2.
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
     *
     * <p>Functional spec §11.2.
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
     * When a player who did not summon the vex clicks it with an empty hand, should the vex in a team move answer no
     * busy message (the {@code isSummonedBy} check comes first).
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     *
     * <p>Functional spec §5.6.
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

    /** The items a Raid member's main hand may hold (spec section 4.4). */
    private static final Set<Item> RAID_MAIN = Set.of(
            Items.NETHERITE_SWORD,
            Items.DIAMOND_SWORD,
            Items.NETHERITE_AXE,
            Items.DIAMOND_AXE,
            Items.MACE,
            Items.TRIDENT,
            Items.CROSSBOW);

    /** The roster vexes' main-hand and off-hand are the conjured shields. */
    private static void assertShieldKit(GameTestHelper helper, BoundVex vex) {
        helper.assertTrue(
                vex.isConjured(EquipmentSlot.MAINHAND) && vex.getMainHandItem().is(Items.SHIELD), "main-hand shield");
        helper.assertTrue(
                vex.isConjured(EquipmentSlot.OFFHAND) && vex.getOffhandItem().is(Items.SHIELD), "off-hand shield");
        helper.assertTrue(vex.getMainHandItem().has(DataComponents.UNBREAKABLE), "unbreakable");
    }

    /**
     * When four vexes are free and a 40-health husk stands beside them, should a Shield start with the three vexes
     * nearest the husk, each marked, targeting it and holding a shield in each hand, and the fourth left alone.
     *
     * <p>Method under test: {@link TeamMoves#start}: the Shield roster and kit
     *
     * <p>Functional spec §11.3, §11.5, §11.8.
     */
    static void startShieldThree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk husk = bigHusk(helper, beside(helper));
        List<BoundVex> nearest = vexes.stream()
                .sorted(Comparator.comparingDouble(vex -> vex.distanceToSqr(husk)))
                .limit(3)
                .toList();

        // Act
        boolean started = TeamMoves.start(player, TeamMove.SHIELD, husk);

        // Assert
        helper.assertTrue(started, "the move started");
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.SHIELD), "a Shield runs");
        helper.assertValueEqual(Set.copyOf(TeamMoves.roster(player)), Set.copyOf(nearest), "the three nearest");
        for (BoundVex vex : nearest) {
            assertShieldKit(helper, vex);
            helper.assertValueEqual(vex.teamMove(), TeamMove.SHIELD, "marked");
            helper.assertTrue(husk.equals(vex.getTarget()), "targets the husk");
        }
        BoundVex left =
                vexes.stream().filter(vex -> !nearest.contains(vex)).findFirst().orElseThrow();
        helper.assertValueEqual(left.teamMove(), TeamMove.NONE, "the fourth is left alone");
        helper.succeed();
    }

    /**
     * When ten vexes are free and a 40-health husk stands beside them, should a Shield take nine, and the one left out
     * be no nearer the husk than any member.
     *
     * <p>Method under test: {@link TeamMoves#start}: the Shield roster size and choice
     *
     * <p>Functional spec §11.3.
     */
    static void startShieldNine(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 10);
        Husk husk = bigHusk(helper, beside(helper));

        // Act
        boolean started = TeamMoves.start(player, TeamMove.SHIELD, husk);

        // Assert (never name a single "farthest" vex: in the rows-of-four layout vexes 0 and 8 tie)
        helper.assertTrue(started, "the move started");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 9, "nine of ten");
        TeamMoves.roster(player).forEach(vex -> assertShieldKit(helper, vex));
        double reach = TeamMoves.roster(player).stream()
                .mapToDouble(vex -> vex.distanceToSqr(husk))
                .max()
                .orElseThrow();
        vexes.stream()
                .filter(vex -> !TeamMoves.roster(player).contains(vex))
                .forEach(vex -> helper.assertTrue(
                        vex.distanceToSqr(husk) >= reach, "the one left out is no nearer than a member"));
        helper.succeed();
    }

    /**
     * When only two vexes are free, should a Shield not start, nobody be marked and nothing be said.
     *
     * <p>Method under test: {@link TeamMoves#start}: the minimum roster
     *
     * <p>Functional spec §11.3.
     */
    static void startRefusedWithTwoFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 2);
        Husk husk = bigHusk(helper, beside(helper));

        // Act
        boolean started = TeamMoves.start(player, TeamMove.SHIELD, husk);

        // Assert
        helper.assertFalse(started, "two vexes are not enough");
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "nothing runs");
        helper.assertTrue(vexes.stream().allMatch(vex -> vex.teamMove() == TeamMove.NONE), "nobody is marked");
        helper.assertTrue(GameTestSupport.drainActionBar(player).isEmpty(), "and nothing is said");
        helper.succeed();
    }

    /**
     * When a husk with attack damage 6 hunts the free vex farthest from it, should a Multishot start with that vex
     * first and then the two nearest it (not the three nearest the husk), each with a conjured, unbreakable crossbow
     * enchanted with Multishot, and the summoner be told once.
     *
     * <p>Method under test: {@link TeamMoves#start}: the Multishot roster and kit
     *
     * <p>Functional spec §11.3, §11.5, §11.9.
     */
    static void startMultishotTargetedVexIsIn(GameTestHelper helper) {
        // Arrange (the vexes stand at x = -1.5, -0.5, 0.5, 1.5 and the husk at x = +3, so the three nearest the husk
        // would be vexes 3, 2, 1)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk husk = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
        husk.setTarget(vexes.get(0));
        Holder<Enchantment> multishot = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.MULTISHOT);

        // Act
        boolean started = TeamMoves.start(player, TeamMove.MULTISHOT, husk);

        // Assert
        helper.assertTrue(started, "the move started");
        List<BoundVex> roster = TeamMoves.roster(player);
        helper.assertValueEqual(
                roster,
                List.of(vexes.get(0), vexes.get(1), vexes.get(2)),
                "the hunted vex and the two nearest it, hunted first");
        for (BoundVex vex : roster) {
            ItemStack crossbow = vex.getMainHandItem();
            helper.assertTrue(
                    vex.isConjured(EquipmentSlot.MAINHAND) && crossbow.is(Items.CROSSBOW), "a conjured crossbow");
            helper.assertValueEqual(crossbow.getEnchantmentLevel(multishot), 1, "Multishot I");
            helper.assertTrue(crossbow.has(DataComponents.UNBREAKABLE), "unbreakable");
            helper.assertFalse(vex.isConjured(EquipmentSlot.OFFHAND), "the off hand is its own");
            helper.assertTrue(husk.equals(vex.getTarget()), "it targets the husk");
        }
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "multishot");
        helper.succeed();
    }

    /**
     * When a husk with attack damage 6 hunts nobody, should a Multishot take the free vex nearest the husk first and
     * then the two nearest that one.
     *
     * <p>Method under test: {@link TeamMoves#start}: the Multishot anchor
     *
     * <p>Functional spec §11.3.
     */
    static void startMultishotNearestWhenNothingHunts(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk husk = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);

        // Act
        boolean started = TeamMoves.start(player, TeamMove.MULTISHOT, husk);

        // Assert (anchor first: vex 3 is nearest the husk, then 2 and 1 nearest it)
        helper.assertTrue(started, "the move started");
        helper.assertValueEqual(
                TeamMoves.roster(player),
                List.of(vexes.get(3), vexes.get(2), vexes.get(1)),
                "the nearest vex is the anchor, then the two nearest it");
        helper.succeed();
    }

    /**
     * When a husk with attack damage 6 hunts a vex that is staying (not free), should a Multishot still start, with the
     * three free vexes and without the hunted one.
     *
     * <p>Method under test: {@link TeamMoves#start}: the hunted vex must be free to be the anchor
     *
     * <p>Functional spec §11.2, §11.3.
     */
    static void startMultishotHuntedVexNotFree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        vexes.get(0).setStaying(true);
        Husk husk = foe(helper, EntityTypes.HUSK, beside(helper));
        setBase(husk, Attributes.ATTACK_DAMAGE, 6.0);
        husk.setTarget(vexes.get(0));

        // Act
        boolean started = TeamMoves.start(player, TeamMove.MULTISHOT, husk);

        // Assert
        helper.assertTrue(started, "the move started");
        helper.assertFalse(TeamMoves.roster(player).contains(vexes.get(0)), "the staying vex is not taken");
        helper.assertValueEqual(
                Set.copyOf(TeamMoves.roster(player)),
                Set.of(vexes.get(1), vexes.get(2), vexes.get(3)),
                "the three free vexes");
        helper.succeed();
    }

    /**
     * When the summoner is below half health and five vexes are free, should an Evader start with the three nearest the
     * summoner, a shield in each hand, no target, the summoner as its ward, and one message.
     *
     * <p>Method under test: {@link TeamMoves#start}: the Evader roster, kit and ward
     *
     * <p>Functional spec §11.3, §11.5, §11.10.
     */
    static void startEvaderNearestThree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 5);
        player.setHealth(8.0F);
        List<BoundVex> nearest = vexes.stream()
                .sorted(Comparator.comparingDouble(vex -> vex.distanceToSqr(player)))
                .limit(3)
                .toList();

        // Act
        boolean started = TeamMoves.start(player, TeamMove.EVADER, player);

        // Assert
        helper.assertTrue(started, "the move started");
        helper.assertValueEqual(Set.copyOf(TeamMoves.roster(player)), Set.copyOf(nearest), "the three nearest");
        for (BoundVex vex : TeamMoves.roster(player)) {
            assertShieldKit(helper, vex);
            helper.assertTrue(vex.getTarget() == null, "an Evader member takes no target");
        }
        helper.assertValueEqual(TeamMoves.subject(player), Optional.of(player.getUUID()), "the ward is the summoner");
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "evader");
        helper.succeed();
    }

    /**
     * When thirteen vexes are free and a ravager stands beside them, should a Raid take exactly twelve (the cap).
     *
     * <p>Method under test: {@link TeamMoves#start}: the Raid roster cap
     *
     * <p>Functional spec §11.3.
     */
    static void startRaidTakesTwelveOfThirteen(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 13);
        Ravager ravager = foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        boolean started = TeamMoves.start(player, TeamMove.RAID, ravager);

        // Assert
        helper.assertTrue(started, "the move started");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 12, "twelve of the thirteen");
        helper.succeed();
    }

    /**
     * When twelve vexes in iron helmets and swords are free and a ravager stands beside them, should a Raid take all
     * twelve, each with a raid weapon, a shield and three armor pieces of one material in the slots, all unbreakable,
     * the iron gear in the stash, and one message.
     *
     * <p>Method under test: {@link TeamMoves#start}: the Raid roster and kit
     *
     * <p>Functional spec §8.13, §11.3, §11.5, §11.11.
     */
    static void startRaidTwelveGeared(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 12)
                .forEach(vex -> vex.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)));
        Ravager ravager = foe(helper, EntityTypes.RAVAGER, beside(helper));

        // Act
        boolean started = TeamMoves.start(player, TeamMove.RAID, ravager);

        // Assert
        helper.assertTrue(started, "the move started");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 12, "all twelve");
        // The roster runs nearest the enemy first (Raid cells and Task 12's dice follow this order). Compare distances,
        // never identities: rows z = -1 and z = +1 mirror each other about a ravager in the middle row.
        List<Double> reach = TeamMoves.roster(player).stream()
                .map(vex -> vex.distanceToSqr(ravager))
                .toList();
        helper.assertValueEqual(reach, reach.stream().sorted().toList(), "the roster runs nearest the enemy first");
        for (BoundVex vex : TeamMoves.roster(player)) {
            TeamMoveTestSupport.assertRaidGear(helper, vex);
            helper.assertTrue(
                    vex.isConjured(EquipmentSlot.MAINHAND)
                            && RAID_MAIN.contains(vex.getMainHandItem().getItem()),
                    "a raid weapon, not " + vex.getMainHandItem());
            helper.assertTrue(
                    vex.isConjured(EquipmentSlot.OFFHAND)
                            && vex.getOffhandItem().is(Items.SHIELD),
                    "a shield");
            boolean netherite = vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET);
            Item chest = netherite ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE;
            Item legs = netherite ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS;
            helper.assertTrue(
                    netherite || vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                    "a netherite or diamond helmet");
            helper.assertTrue(vex.getItemBySlot(EquipmentSlot.CHEST).is(chest), "the chestplate matches the helmet");
            helper.assertTrue(vex.getItemBySlot(EquipmentSlot.LEGS).is(legs), "the leggings match the helmet");
            for (EquipmentSlot slot : BoundVex.GEAR_SLOTS) {
                helper.assertTrue(vex.isConjured(slot), slot + " is conjured");
                helper.assertTrue(vex.getItemBySlot(slot).has(DataComponents.UNBREAKABLE), slot + " is unbreakable");
            }
            helper.assertTrue(vex.stashed(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the iron helmet is stashed");
            helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "the iron sword is stashed");
        }
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "raid");
        helper.succeed();
    }

    /**
     * When a Shield starts for four free vexes, should each of the three members hold its iron sword in the stash and
     * the fourth keep it in its hand.
     *
     * <p>Method under test: {@link TeamMoves#start}: given gear is stashed
     *
     * <p>Functional spec §8.13, §11.5.
     */
    static void startStashesGivenGear(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk husk = bigHusk(helper, beside(helper));

        // Act
        TeamMoves.start(player, TeamMove.SHIELD, husk);

        // Assert
        for (BoundVex vex : vexes) {
            if (vex.teamMove() == TeamMove.SHIELD) {
                helper.assertTrue(
                        vex.stashed(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "a member stashes its sword");
            } else {
                helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the fourth keeps its sword");
            }
        }
        helper.succeed();
    }

    /**
     * When a Shield is running, should a second start (even of another husk, with vexes to spare) be refused and no
     * more than the first three vexes be marked.
     *
     * <p>Method under test: {@link TeamMoves#start}: one move at a time per summoner
     *
     * <p>Functional spec §11.1.
     */
    static void startRefusedWhileRunning(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 6);
        Husk first = bigHusk(helper, beside(helper));
        Husk second = bigHusk(helper, beside(helper).add(0.0, 0.0, 1.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, first), "setup: the first move starts");

        // Act
        boolean again = TeamMoves.start(player, TeamMove.SHIELD, second);

        // Assert
        helper.assertFalse(again, "a second move is refused");
        helper.assertValueEqual(
                vexes.stream().filter(vex -> vex.teamMove() != TeamMove.NONE).count(), 3L, "still three marked");
        helper.succeed();
    }

    /**
     * When the subject is missing or the move is none, should nothing start.
     *
     * <p>Method under test: {@link TeamMoves#start}: the guards
     */
    static void startRefusedWithoutASubject(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk husk = bigHusk(helper, beside(helper));

        // Act & Assert
        helper.assertFalse(TeamMoves.start(player, TeamMove.SHIELD, null), "no subject, no move");
        helper.assertFalse(TeamMoves.start(player, TeamMove.NONE, husk), "none is not a move");
        helper.assertTrue(vexes.stream().allMatch(vex -> vex.teamMove() == TeamMove.NONE), "nobody is marked");
        helper.succeed();
    }

    /**
     * When the subject is dead or the summoner is dead or a spectator, should nothing start and nobody be marked (spec
     * section 2.3 "present").
     *
     * <p>Method under test: {@link TeamMoves#start}: the guards on the subject and on the summoner
     */
    static void startRefusedWithAnUnusableSubject(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        Husk dead = bigHusk(helper, beside(helper));
        dead.kill(helper.getLevel());
        Husk living = bigHusk(helper, beside(helper));

        // Act & Assert
        helper.assertFalse(TeamMoves.start(player, TeamMove.SHIELD, dead), "a dead subject starts nothing");
        player.setGameMode(GameType.SPECTATOR);
        helper.assertFalse(TeamMoves.start(player, TeamMove.SHIELD, living), "a spectating summoner starts nothing");
        player.setGameMode(GameType.SURVIVAL);
        player.kill(helper.getLevel());
        helper.assertFalse(TeamMoves.start(player, TeamMove.SHIELD, living), "a dead summoner starts nothing");
        helper.assertTrue(vexes.stream().allMatch(vex -> vex.teamMove() == TeamMove.NONE), "nobody is marked");
        helper.succeed();
    }

    /**
     * When a Shield starts, should the summoner's action bar hold exactly one message, "Your vexes form a Shield!"'s
     * key.
     *
     * <p>Method under test: {@link TeamMoves#start}: the overlay message
     *
     * <p>Functional spec §11.5.
     */
    static void startMessageOnce(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 3);
        Husk husk = bigHusk(helper, beside(helper));

        // Act
        TeamMoves.start(player, TeamMove.SHIELD, husk);

        // Assert
        GameTestSupport.assertActionBarKey(helper, player, TeamMoves.MESSAGE_PREFIX + "shield");
        helper.succeed();
    }

    /**
     * When rolls are scripted on the Raid dice, should they come back in order, then running out throw (no fallback to
     * random while the script is active), an unscript with rolls left throw naming the die and the count (and still
     * clear it), a clean unscript return the die to the random source, a reset clear silently, and a scripted roll
     * equal to the bound or below zero throw. (A GameTest, not a unit test: {@code RandomSource} is not on the
     * unit-test classpath. The script is global, so the test has an environment of its own.)
     *
     * <p>Method under test: {@link Dice#below}, {@link Dice#script}, {@link Dice#unscript}, {@link Dice#reset}
     */
    static void diceScriptRules(GameTestHelper helper) {
        // Arrange
        RandomSource random = RandomSource.create(1L);
        try {
            Dice.RAID_KIT.script(3, 0);

            // Act
            int first = Dice.RAID_KIT.below(random, 7);
            int second = Dice.RAID_KIT.below(random, 7);
            String ranOut = messageOf(() -> Dice.RAID_KIT.below(random, 7));

            // Assert: scripted rolls in order, then a loud failure naming the die
            helper.assertValueEqual(first, 3, "the first scripted roll");
            helper.assertValueEqual(second, 0, "the second scripted roll");
            helper.assertTrue(ranOut != null && ranOut.contains("RAID_KIT"), "running out throws, naming the die");
            Dice.RAID_KIT.unscript();
            int afterwards = Dice.RAID_KIT.below(random, 7);
            helper.assertTrue(afterwards >= 0 && afterwards < 7, "a clean unscript returns to the random source");

            // Unscript with rolls left throws naming the die and the count, and still clears the die
            Dice.RAID_KIT.script(6, 6, 6);
            String leftover = messageOf(Dice.RAID_KIT::unscript);
            helper.assertTrue(
                    leftover != null && leftover.contains("RAID_KIT") && leftover.contains("3"),
                    "leftover rolls fail the unscript, naming the die and the count");
            helper.assertValueEqual(Dice.RAID_KIT.below(random, 1), 0, "and the die was cleared");

            // unscriptAll fails the same way, after clearing every die; resetAll never fails
            Dice.CASTER_ROLL.script(1, 1);
            helper.assertTrue(messageOf(Dice::unscriptAll) != null, "unscriptAll fails on leftover rolls of any die");
            helper.assertValueEqual(Dice.CASTER_ROLL.below(random, 1), 0, "and cleared it");
            Dice.RAID_KIT.script(6);
            Dice.resetAll();
            helper.assertValueEqual(Dice.RAID_KIT.below(random, 1), 0, "a reset clears silently");

            // A scripted roll outside [0, bound) throws
            Dice.RAID_KIT.script(7);
            helper.assertTrue(messageOf(() -> Dice.RAID_KIT.below(random, 7)) != null, "a roll equal to the bound");
            Dice.RAID_KIT.script(-1);
            helper.assertTrue(messageOf(() -> Dice.RAID_KIT.below(random, 7)) != null, "a negative roll throws");
        } finally {
            Dice.resetAll();
        }
        helper.succeed();
    }

    /** The message of the {@link IllegalStateException} {@code action} throws, or null when it throws nothing. */
    private static String messageOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (IllegalStateException expected) {
            return expected.getMessage();
        }
    }
}
