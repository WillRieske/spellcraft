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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Cast-rule GameTests for Strength of Trailing (spec §6). The trail itself is tested in {@code TrailGameTests}. */
public final class TrailingGameTests {
    private static final int TICKS_PER_SECOND = 20;
    private static final int DEFAULT_DURATION_TICKS = 480 * TICKS_PER_SECOND;

    private TrailingGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "trailing_effect_is_beneficial_marker", TrailingGameTests::isABeneficialMarker);
        SpellcraftGameTests.add(SHARED, "trailing_cast_consumes", TrailingGameTests::castConsumesAndGrants);
        SpellcraftGameTests.add(SHARED, "trailing_creative_casts", TrailingGameTests::creativeCasts);
        SpellcraftGameTests.add(SHARED, "trailing_recast_refreshes", TrailingGameTests::recastRefreshes);
        SpellcraftGameTests.add(SHARED, "trailing_sneak_dispels", TrailingGameTests::sneakDispels);
        SpellcraftGameTests.add(SHARED, "trailing_sneak_inactive_casts", TrailingGameTests::sneakInactiveCasts);
        SpellcraftGameTests.add(SHARED, "trailing_milk_clears", TrailingGameTests::milkClearsTheEffect);
        SpellcraftGameTests.add("config_mutation", "trailing_config_duration", TrailingGameTests::configDuration);
    }

    private static ServerPlayer withScrolls(GameTestHelper helper, GameType mode, int count) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        hold(player, new ItemStack(ModItems.TRAILING_SCROLL.get(), count));
        return player;
    }

    static void isABeneficialMarker(GameTestHelper helper) {
        helper.assertValueEqual(
                ModEffects.TRAILING.value().getCategory(), MobEffectCategory.BENEFICIAL, "effect category");
        helper.succeed();
    }

    static void castConsumesAndGrants(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 2);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "use result");
        helper.assertValueEqual(heldCount(player), 1, "one scroll used up");
        MobEffectInstance effect = requireEffect(helper, player, ModEffects.TRAILING);
        helper.assertValueEqual(effect.getDuration(), DEFAULT_DURATION_TICKS, "duration ticks");
        helper.assertFalse(effect.isVisible(), "particles hidden");
        helper.succeed();
    }

    static void creativeCasts(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.CREATIVE, 2);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "creative is not refused");
        helper.assertTrue(player.hasEffect(ModEffects.TRAILING), "creative casts Trailing");
        helper.assertValueEqual(heldCount(player), 1, "creative still uses up the scroll");
        helper.succeed();
    }

    static void recastRefreshes(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 2);
        player.addEffect(new MobEffectInstance(ModEffects.TRAILING, 40, 0, false, false, true));
        useHeld(helper, player);
        helper.assertValueEqual(
                requireEffect(helper, player, ModEffects.TRAILING).getDuration(),
                DEFAULT_DURATION_TICKS,
                "recast resets the timer");
        helper.assertValueEqual(heldCount(player), 1, "recast uses up a scroll");
        helper.succeed();
    }

    static void sneakDispels(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 2);
        player.addEffect(new MobEffectInstance(ModEffects.TRAILING, 200, 0, false, false, true));
        player.setShiftKeyDown(true);
        useHeld(helper, player);
        helper.assertFalse(player.hasEffect(ModEffects.TRAILING), "sneak + use while active dispels");
        helper.assertValueEqual(heldCount(player), 1, "dispel uses up a scroll");
        helper.succeed();
    }

    static void sneakInactiveCasts(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 1);
        player.setShiftKeyDown(true);
        useHeld(helper, player);
        helper.assertValueEqual(
                requireEffect(helper, player, ModEffects.TRAILING).getDuration(),
                DEFAULT_DURATION_TICKS,
                "sneak + use while inactive casts");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the last scroll is gone");
        helper.succeed();
    }

    static void milkClearsTheEffect(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(ModEffects.TRAILING, 200));
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player);
        helper.assertFalse(player.hasEffect(ModEffects.TRAILING), "milk should clear Trailing");
        helper.succeed();
    }

    /** Mutates global server config, so it runs alone in the {@code config_mutation} batch. */
    static void configDuration(GameTestHelper helper) {
        withConfig(ServerConfig.TRAILING_DURATION_SECONDS, 7, () -> {
            ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 1);
            useHeld(helper, player);
            helper.assertValueEqual(
                    requireEffect(helper, player, ModEffects.TRAILING).getDuration(),
                    7 * TICKS_PER_SECOND,
                    "configured duration ticks");
        });
        helper.succeed();
    }
}
