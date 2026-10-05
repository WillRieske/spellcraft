package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.heldCount;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.hold;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.requireEffect;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.useHeld;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.withConfig;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Server-rule GameTests for Strength of Upside-Down. Registered in {@link SpellcraftGameTests}. */
public final class UpsideDownGameTests {
    private static final int TICKS_PER_SECOND = 20;
    private static final int DEFAULT_DURATION_TICKS = 180 * TICKS_PER_SECOND;

    private UpsideDownGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "upside_down_effect_is_neutral_marker", UpsideDownGameTests::isANeutralMarker);
        SpellcraftGameTests.add(SHARED, "upside_down_milk_clears", UpsideDownGameTests::milkClearsTheEffect);
        SpellcraftGameTests.add(SHARED, "upside_down_cast_consumes", UpsideDownGameTests::castConsumesAndGrants);
        SpellcraftGameTests.add(SHARED, "upside_down_recast_refreshes", UpsideDownGameTests::recastRefreshes);
        SpellcraftGameTests.add(SHARED, "upside_down_sneak_dispels", UpsideDownGameTests::sneakDispels);
        SpellcraftGameTests.add(SHARED, "upside_down_sneak_inactive_casts", UpsideDownGameTests::sneakInactiveCasts);
        SpellcraftGameTests.add(
                SHARED, "upside_down_sneak_other_item_keeps", UpsideDownGameTests::sneakOtherItemKeepsTheEffect);
        SpellcraftGameTests.add(SHARED, "upside_down_creative_consumes", UpsideDownGameTests::creativeConsumes);
        SpellcraftGameTests.add("config_mutation", "upside_down_config_duration", UpsideDownGameTests::configDuration);
    }

    private static ServerPlayer survivalWithScrolls(GameTestHelper helper, int count) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        hold(player, new ItemStack(ModItems.UPSIDE_DOWN_SCROLL.get(), count));
        return player;
    }

    static void isANeutralMarker(GameTestHelper helper) {
        helper.assertValueEqual(
                ModEffects.UPSIDE_DOWN.value().getCategory(), MobEffectCategory.NEUTRAL, "effect category");
        helper.succeed();
    }

    static void milkClearsTheEffect(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 200));
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player);
        helper.assertFalse(player.hasEffect(ModEffects.UPSIDE_DOWN), "milk should clear Upside-Down");
        helper.succeed();
    }

    static void castConsumesAndGrants(GameTestHelper helper) {
        ServerPlayer player = survivalWithScrolls(helper, 2);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "use result");
        helper.assertValueEqual(heldCount(player), 1, "one scroll used up");
        MobEffectInstance effect = requireEffect(helper, player, ModEffects.UPSIDE_DOWN);
        helper.assertValueEqual(effect.getDuration(), DEFAULT_DURATION_TICKS, "duration ticks");
        helper.assertFalse(effect.isVisible(), "particles hidden so other players see nothing");
        helper.assertValueEqual(
                ModItems.UPSIDE_DOWN_SCROLL.get().getDefaultInstance().getMaxStackSize(), 16, "scrolls stack to 16");
        helper.succeed();
    }

    static void recastRefreshes(GameTestHelper helper) {
        ServerPlayer player = survivalWithScrolls(helper, 2);
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 40, 0, false, false, true));
        useHeld(helper, player);
        helper.assertValueEqual(
                requireEffect(helper, player, ModEffects.UPSIDE_DOWN).getDuration(),
                DEFAULT_DURATION_TICKS,
                "recast resets the timer");
        helper.assertValueEqual(heldCount(player), 1, "recast uses up a scroll");
        helper.succeed();
    }

    static void sneakDispels(GameTestHelper helper) {
        ServerPlayer player = survivalWithScrolls(helper, 2);
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 200, 0, false, false, true));
        player.setShiftKeyDown(true);
        useHeld(helper, player);
        helper.assertFalse(player.hasEffect(ModEffects.UPSIDE_DOWN), "sneak + use while active dispels");
        helper.assertValueEqual(heldCount(player), 1, "dispel uses up a scroll");
        helper.succeed();
    }

    static void sneakInactiveCasts(GameTestHelper helper) {
        ServerPlayer player = survivalWithScrolls(helper, 1);
        player.setShiftKeyDown(true);
        useHeld(helper, player);
        helper.assertValueEqual(
                requireEffect(helper, player, ModEffects.UPSIDE_DOWN).getDuration(),
                DEFAULT_DURATION_TICKS,
                "sneak + use while inactive casts");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the last scroll is gone");
        helper.succeed();
    }

    /** Dispel is a scroll action: sneak-using anything else leaves an active spell running. */
    static void sneakOtherItemKeepsTheEffect(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 200, 0, false, false, true));
        ItemStack paper = new ItemStack(Items.PAPER);
        hold(player, paper);
        player.setShiftKeyDown(true);
        paper.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.hasEffect(ModEffects.UPSIDE_DOWN), "sneak-use of another item must not dispel");
        helper.succeed();
    }

    static void creativeConsumes(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.CREATIVE);
        hold(player, new ItemStack(ModItems.UPSIDE_DOWN_SCROLL.get(), 2));
        useHeld(helper, player);
        helper.assertTrue(player.hasEffect(ModEffects.UPSIDE_DOWN), "creative casts Upside-Down");
        helper.assertValueEqual(heldCount(player), 1, "creative still uses up the scroll");
        helper.succeed();
    }

    /** Mutates global server config, so it runs alone in the {@code config_mutation} batch. */
    static void configDuration(GameTestHelper helper) {
        withConfig(ServerConfig.UPSIDE_DOWN_DURATION_SECONDS, 5, () -> {
            ServerPlayer player = survivalWithScrolls(helper, 1);
            useHeld(helper, player);
            helper.assertValueEqual(
                    requireEffect(helper, player, ModEffects.UPSIDE_DOWN).getDuration(),
                    5 * TICKS_PER_SECOND,
                    "configured duration ticks");
        });
        helper.succeed();
    }
}
