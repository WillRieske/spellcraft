package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.heldCount;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.hold;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.requireEffect;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.useHeld;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.withConfig;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The cast rules every timed spell shares (cast, creative cast, recast, sneak dispel, sneak cast, sneak with another
 * item, milk, configured duration), written once. Each spell's test class builds one of these and registers the
 * methods under its own test names and environments.
 *
 * @param effect the spell's effect
 * @param scroll the spell's scroll item
 * @param spellName the spell's name in assertion messages
 * @param durationSeconds the spell's duration setting
 * @param defaultSeconds the setting's default value
 */
record CastRuleGameTests(
        Holder<MobEffect> effect,
        Supplier<? extends Item> scroll,
        String spellName,
        ModConfigSpec.IntValue durationSeconds,
        int defaultSeconds) {
    private static final int TICKS_PER_SECOND = 20;
    private static final int SCROLL_STACK = 16;

    private int defaultDurationTicks() {
        return defaultSeconds * TICKS_PER_SECOND;
    }

    private ServerPlayer withScrolls(GameTestHelper helper, GameType mode, int count) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        hold(player, new ItemStack(scroll.get(), count));
        return player;
    }

    private MobEffectInstance active(int ticks) {
        return new MobEffectInstance(effect, ticks, 0, false, false, true);
    }

    /** A survival cast from a stack of two takes one scroll and grants the default duration, particles hidden. */
    void castConsumesAndGrants(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 2);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "use result");
        helper.assertValueEqual(heldCount(player), 1, "one scroll used up");
        MobEffectInstance instance = requireEffect(helper, player, effect);
        helper.assertValueEqual(instance.getDuration(), defaultDurationTicks(), "duration ticks");
        helper.assertFalse(instance.isVisible(), "particles hidden so other players see nothing");
        helper.assertValueEqual(scroll.get().getDefaultInstance().getMaxStackSize(), SCROLL_STACK, "scrolls stack");
        helper.succeed();
    }

    /** Creative players cast too, and the scroll is still used up. */
    void creativeCasts(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.CREATIVE, 2);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "creative is not refused");
        helper.assertTrue(player.hasEffect(effect), "creative casts " + spellName);
        helper.assertValueEqual(heldCount(player), 1, "creative still uses up the scroll");
        helper.succeed();
    }

    /** Casting while active resets the timer and takes a scroll. */
    void recastRefreshes(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 2);
        player.addEffect(active(40));
        useHeld(helper, player);
        helper.assertValueEqual(
                requireEffect(helper, player, effect).getDuration(), defaultDurationTicks(), "recast resets the timer");
        helper.assertValueEqual(heldCount(player), 1, "recast uses up a scroll");
        helper.succeed();
    }

    /** Sneak + use while active dispels, and takes a scroll. */
    void sneakDispels(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 2);
        player.addEffect(active(200));
        player.setShiftKeyDown(true);
        useHeld(helper, player);
        helper.assertFalse(player.hasEffect(effect), "sneak + use while active dispels");
        helper.assertValueEqual(heldCount(player), 1, "dispel uses up a scroll");
        helper.succeed();
    }

    /** Sneak + use while inactive is an ordinary cast. */
    void sneakInactiveCasts(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 1);
        player.setShiftKeyDown(true);
        useHeld(helper, player);
        helper.assertValueEqual(
                requireEffect(helper, player, effect).getDuration(),
                defaultDurationTicks(),
                "sneak + use while inactive casts");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the last scroll is gone");
        helper.succeed();
    }

    /** Dispel is a scroll action: sneak-using anything else leaves an active spell running. */
    void sneakOtherItemKeepsTheEffect(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(active(200));
        ItemStack paper = new ItemStack(Items.PAPER);
        hold(player, paper);
        player.setShiftKeyDown(true);
        paper.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.hasEffect(effect), "sneak-use of another item must not dispel");
        helper.succeed();
    }

    /** Milk cures the effect (vanilla default). */
    void milkClearsTheEffect(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(effect, 200));
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player);
        helper.assertFalse(player.hasEffect(effect), "milk should clear " + spellName);
        helper.succeed();
    }

    /**
     * The duration setting is read at cast time. Mutates global server config, so it must run alone in the
     * {@code config_mutation} batch.
     *
     * @param helper the test's helper
     * @param seconds the duration to configure for the cast
     */
    void configDuration(GameTestHelper helper, int seconds) {
        withConfig(durationSeconds, seconds, () -> {
            ServerPlayer player = withScrolls(helper, GameType.SURVIVAL, 1);
            useHeld(helper, player);
            helper.assertValueEqual(
                    requireEffect(helper, player, effect).getDuration(),
                    seconds * TICKS_PER_SECOND,
                    "configured duration ticks");
        });
        helper.succeed();
    }
}
