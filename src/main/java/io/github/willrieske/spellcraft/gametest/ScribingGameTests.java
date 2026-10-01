package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.withConfig;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.SpellScrollItem;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Scribing GameTests: a real enchanting menu at a real table. Registered in {@link SpellcraftGameTests}. */
public final class ScribingGameTests {
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);
    private static final int LEVELS = 30;
    private static final Identifier ENCHANTER = Identifier.withDefaultNamespace("story/enchant_item");

    private ScribingGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "scribing_offers_costs_and_odds", ScribingGameTests::offersCostsAndOdds);
        for (int option = 0; option < 3; option++) {
            int chosen = option;
            SpellcraftGameTests.add(SHARED, "scribing_option_" + option, h -> optionScribes(h, chosen));
        }
        SpellcraftGameTests.add(SHARED, "scribing_too_few_lapis", ScribingGameTests::tooFewLapis);
        SpellcraftGameTests.add(SHARED, "scribing_too_few_levels", ScribingGameTests::tooFewLevels);
        SpellcraftGameTests.add(SHARED, "scribing_instabuild_is_free", ScribingGameTests::instabuildIsFree);
        SpellcraftGameTests.add(SHARED, "scribing_instabuild_keeps_lapis", ScribingGameTests::instabuildKeepsLapis);
        SpellcraftGameTests.add(SHARED, "scribing_book_still_enchants", ScribingGameTests::bookStillEnchants);
        SpellcraftGameTests.add(SHARED, "scribing_second_click_nothing", ScribingGameTests::secondClickDoesNothing);
        SpellcraftGameTests.add(SHARED, "scribing_no_enchanter_advancement", ScribingGameTests::noEnchanterAdvancement);
        SpellcraftGameTests.add("scribing_zero", "scribing_zero_percent", ScribingGameTests::zeroPercent);
        SpellcraftGameTests.add("scribing_full", "scribing_hundred_percent", ScribingGameTests::hundredPercent);
    }

    private static ServerPlayer scribe(GameTestHelper helper, int levels) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setExperienceLevels(levels);
        return player;
    }

    private static EnchantmentMenu table(GameTestHelper helper, ServerPlayer player, ItemStack item, int lapis) {
        helper.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
        EnchantmentMenu menu = new EnchantmentMenu(
                1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
        menu.getSlot(0).set(item);
        if (lapis > 0) {
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, lapis));
        }
        return menu;
    }

    private static boolean isScroll(ItemStack stack) {
        return stack.getItem() instanceof SpellScrollItem;
    }

    static void offersCostsAndOdds(GameTestHelper helper) {
        EnchantmentMenu menu = table(helper, scribe(helper, LEVELS), new ItemStack(Items.PAPER), 3);
        helper.assertValueEqual(List.of(menu.costs[0], menu.costs[1], menu.costs[2]), List.of(1, 2, 3), "costs");
        helper.assertValueEqual(
                List.of(menu.levelClue[0], menu.levelClue[1], menu.levelClue[2]), List.of(25, 50, 75), "odds");
        helper.assertValueEqual(
                List.of(menu.enchantClue[0], menu.enchantClue[1], menu.enchantClue[2]),
                List.of(0, 0, 0),
                "clue is not -1, so the options look enabled");
        helper.succeed();
    }

    private static void optionScribes(GameTestHelper helper, int option) {
        ServerPlayer player = scribe(helper, LEVELS);
        EnchantmentMenu menu = table(helper, player, new ItemStack(Items.PAPER), 3);
        helper.assertTrue(menu.clickMenuButton(player, option), "click accepted");
        helper.assertTrue(isScroll(menu.getSlot(0).getItem()), "paper became a scroll");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3 - (option + 1), "lapis taken");
        helper.assertValueEqual(player.experienceLevel, LEVELS - (option + 1), "levels taken");
        helper.succeed();
    }

    static void tooFewLapis(GameTestHelper helper) {
        ServerPlayer player = scribe(helper, LEVELS);
        EnchantmentMenu menu = table(helper, player, new ItemStack(Items.PAPER), 1);
        helper.assertFalse(menu.clickMenuButton(player, 1), "option 2 needs 2 lapis");
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.PAPER), "paper unchanged");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "lapis unchanged");
        helper.assertValueEqual(player.experienceLevel, LEVELS, "levels unchanged");
        helper.succeed();
    }

    static void tooFewLevels(GameTestHelper helper) {
        ServerPlayer player = scribe(helper, 1);
        EnchantmentMenu menu = table(helper, player, new ItemStack(Items.PAPER), 3);
        helper.assertFalse(menu.clickMenuButton(player, 2), "option 3 needs 3 levels");
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.PAPER), "paper unchanged");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "lapis unchanged");
        helper.succeed();
    }

    static void instabuildIsFree(GameTestHelper helper) {
        ServerPlayer player = scribe(helper, 0);
        player.getAbilities().instabuild = true;
        EnchantmentMenu menu = table(helper, player, new ItemStack(Items.PAPER), 0);
        helper.assertTrue(menu.clickMenuButton(player, 2), "vanilla waives the cost for instabuild");
        helper.assertTrue(isScroll(menu.getSlot(0).getItem()), "paper became a scroll");
        helper.succeed();
    }

    /** The reason Scribing calls consume (not shrink): an instabuild player keeps their lapis. */
    static void instabuildKeepsLapis(GameTestHelper helper) {
        ServerPlayer player = scribe(helper, 0);
        player.getAbilities().instabuild = true;
        EnchantmentMenu menu = table(helper, player, new ItemStack(Items.PAPER), 3);
        helper.assertTrue(menu.clickMenuButton(player, 2), "click accepted");
        helper.assertTrue(isScroll(menu.getSlot(0).getItem()), "paper became a scroll");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "lapis kept");
        helper.succeed();
    }

    static void bookStillEnchants(GameTestHelper helper) {
        ServerPlayer player = scribe(helper, LEVELS);
        EnchantmentMenu menu = table(helper, player, new ItemStack(Items.BOOK), 3);
        helper.assertTrue(menu.costs[0] > 0, "vanilla offers the book an enchantment");
        helper.assertTrue(menu.clickMenuButton(player, 0), "click accepted");
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.ENCHANTED_BOOK), "vanilla enchanting unchanged");
        helper.succeed();
    }

    static void secondClickDoesNothing(GameTestHelper helper) {
        ServerPlayer player = scribe(helper, LEVELS);
        EnchantmentMenu menu = table(helper, player, new ItemStack(Items.PAPER), 3);
        menu.clickMenuButton(player, 0);
        ItemStack scroll = menu.getSlot(0).getItem().copy();
        helper.assertFalse(menu.clickMenuButton(player, 0), "a scroll can't be scribed again");
        helper.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), scroll), "scroll unchanged");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 2, "no more lapis taken");
        helper.succeed();
    }

    /**
     * Spec §5: scribing grants no Enchanter advancement. The control proves the check can see vanilla's trigger
     * (`GameTestServer` loads the vanilla pack, so `story/enchant_item` exists).
     */
    static void noEnchanterAdvancement(GameTestHelper helper) {
        AdvancementHolder enchanter =
                helper.getLevel().getServer().getAdvancements().get(ENCHANTER);
        helper.assertTrue(enchanter != null, "vanilla's Enchanter advancement is loaded");
        ServerPlayer control = scribe(helper, LEVELS);
        CriteriaTriggers.ENCHANTED_ITEM.trigger(control, new ItemStack(Items.BOOK), 1);
        helper.assertTrue(
                control.getAdvancements().getOrStartProgress(enchanter).isDone(),
                "control: vanilla's trigger awards Enchanter");

        ServerPlayer player = scribe(helper, LEVELS);
        EnchantmentMenu menu = table(helper, player, new ItemStack(Items.PAPER), 3);
        helper.assertTrue(menu.clickMenuButton(player, 0), "click accepted");
        helper.assertTrue(isScroll(menu.getSlot(0).getItem()), "paper became a scroll");
        helper.assertFalse(
                player.getAdvancements().getOrStartProgress(enchanter).isDone(), "scribing grants no Enchanter");
        helper.succeed();
    }

    private static void drawsOnly(GameTestHelper helper, List<Integer> chances, Item expected) {
        withConfig(ServerConfig.TOP_TIER_CHANCE_PERCENT, chances, () -> {
            ServerPlayer player = scribe(helper, LEVELS);
            EnchantmentMenu menu = table(helper, player, new ItemStack(Items.PAPER), 3);
            helper.assertTrue(menu.clickMenuButton(player, 2), "click accepted");
            helper.assertTrue(menu.getSlot(0).getItem().is(expected), "drew " + expected);
        });
        helper.succeed();
    }

    /** Mutates global server config, so it runs alone in the {@code scribing_zero} batch. */
    static void zeroPercent(GameTestHelper helper) {
        drawsOnly(helper, List.of(0, 0, 0), ModItems.UPSIDE_DOWN_SCROLL.get());
    }

    /** Mutates global server config, so it runs alone in the {@code scribing_full} batch. */
    static void hundredPercent(GameTestHelper helper) {
        drawsOnly(helper, List.of(100, 100, 100), ModItems.INFINITY_SCROLL.get());
    }
}
