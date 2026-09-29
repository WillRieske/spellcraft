package io.github.willrieske.spellcraft.gametest;

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
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-rule GameTests for Strength of Upside-Down. Registered in {@link SpellcraftGameTests}. */
public final class UpsideDownGameTests {
    private UpsideDownGameTests() {}

    static void effectIsANeutralMarker(GameTestHelper helper) {
        helper.assertValueEqual(
                ModEffects.UPSIDE_DOWN.value().getCategory(), MobEffectCategory.NEUTRAL, "effect category");
        helper.succeed();
    }

    // makeMockServerPlayer() has no connection, so addEffect (which sends a packet) NPEs; the in-level mock has one.
    @SuppressWarnings("removal")
    static void milkClearsTheEffect(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 200));
        helper.assertTrue(player.hasEffect(ModEffects.UPSIDE_DOWN), "precondition: effect applied");
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player);
        helper.assertFalse(player.hasEffect(ModEffects.UPSIDE_DOWN), "milk should clear Upside-Down");
        helper.succeed();
    }

    private static final int TICKS_PER_SECOND = 20;
    private static final int DEFAULT_DURATION_TICKS = 180 * TICKS_PER_SECOND;

    // See milkClearsTheEffect: the in-level mock player is deprecated-for-removal but is the only one with a
    // connection.
    @SuppressWarnings("removal")
    static void castGrantsTheFullDuration(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack scroll = useScroll(helper, player);
        MobEffectInstance effect = requireEffect(helper, player);
        helper.assertValueEqual(effect.getDuration(), DEFAULT_DURATION_TICKS, "duration ticks");
        helper.assertFalse(effect.isVisible(), "particles must be hidden so other players see nothing");
        helper.assertFalse(player.getCooldowns().isOnCooldown(scroll), "default cooldown is 0");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    static void recastResetsToTheFullDuration(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 40, 0, false, false, true));
        useScroll(helper, player);
        helper.assertValueEqual(
                requireEffect(helper, player).getDuration(), DEFAULT_DURATION_TICKS, "recast duration ticks");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    static void sneakCastWhileActiveRemovesTheEffect(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        useScroll(helper, player);
        player.setShiftKeyDown(true);
        useScroll(helper, player);
        helper.assertFalse(player.hasEffect(ModEffects.UPSIDE_DOWN), "sneak-cast while active should dispel");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    static void sneakCastWhileInactiveCasts(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setShiftKeyDown(true);
        useScroll(helper, player);
        helper.assertValueEqual(
                requireEffect(helper, player).getDuration(), DEFAULT_DURATION_TICKS, "sneak-cast duration ticks");
        helper.succeed();
    }

    /** Mutates global server config, so it runs alone in the {@code config_mutation} batch. */
    @SuppressWarnings("removal")
    static void configValuesAreHonored(GameTestHelper helper) {
        withConfig(
                ServerConfig.UPSIDE_DOWN_DURATION_SECONDS,
                5,
                () -> withConfig(ServerConfig.UPSIDE_DOWN_COOLDOWN_SECONDS, 2, () -> {
                    ServerPlayer player = helper.makeMockServerPlayerInLevel();
                    ItemStack scroll = useScroll(helper, player);
                    helper.assertValueEqual(
                            requireEffect(helper, player).getDuration(),
                            5 * TICKS_PER_SECOND,
                            "configured duration ticks");
                    helper.assertTrue(player.getCooldowns().isOnCooldown(scroll), "configured cooldown should apply");
                }));
        helper.succeed();
    }

    /**
     * The gate is vanilla's: {@code ServerPlayerGameMode.useItem} refuses a stack on cooldown before
     * {@code Item.use} runs (docs/api-notes.md §7). Goes through it so a second cast during the cooldown is
     * proven refused. Mutates global server config, so it runs alone in its own batch.
     */
    @SuppressWarnings("removal")
    static void castIsRefusedDuringCooldown(GameTestHelper helper) {
        withConfig(ServerConfig.UPSIDE_DOWN_COOLDOWN_SECONDS, 2, () -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ItemStack scroll = giveScroll(player);
            player.gameMode.useItem(player, helper.getLevel(), scroll, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getCooldowns().isOnCooldown(scroll), "precondition: first cast starts cooldown");
            // addEffect keeps the longer of two durations, so clear the first cast before planting a short one.
            player.removeEffect(ModEffects.UPSIDE_DOWN);
            player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 40, 0, false, false, true));
            InteractionResult second =
                    player.gameMode.useItem(player, helper.getLevel(), scroll, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(second, InteractionResult.PASS, "gate should refuse the use");
            helper.assertValueEqual(
                    requireEffect(helper, player).getDuration(), 40, "duration must not reset during cooldown");
        });
        helper.succeed();
    }

    /** Runs {@code body} with a global server config value set to {@code temporary}, restoring it afterwards. */
    private static void withConfig(ModConfigSpec.IntValue value, int temporary, Runnable body) {
        int saved = value.getAsInt();
        value.set(temporary);
        try {
            body.run();
        } finally {
            value.set(saved);
        }
    }

    private static ItemStack giveScroll(ServerPlayer player) {
        ItemStack scroll = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!scroll.is(ModItems.UPSIDE_DOWN_SCROLL.get())) {
            scroll = new ItemStack(ModItems.UPSIDE_DOWN_SCROLL.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
        }
        return scroll;
    }

    private static ItemStack useScroll(GameTestHelper helper, ServerPlayer player) {
        ItemStack scroll = giveScroll(player);
        scroll.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        return scroll;
    }

    private static MobEffectInstance requireEffect(GameTestHelper helper, ServerPlayer player) {
        MobEffectInstance effect = player.getEffect(ModEffects.UPSIDE_DOWN);
        if (effect == null) {
            throw helper.assertionException("expected Strength of Upside-Down to be active");
        }
        return effect;
    }
}
