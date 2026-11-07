package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.assertActionBarKey;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.drainActionBar;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.heldCount;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.hold;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.requireEffect;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.useHeld;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.particle.ModParticles;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.SpellScrollItem;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Cast-rule GameTests for Strength of Evoking (spec §2, §9), written like {@link InfinityGameTests}: the effect is
 * infinite and a recast is refused, which {@link CastRuleGameTests} (finite, refreshing spells) does not fit. The
 * casting itself is in {@code FangGameTests} and {@code VexGameTests}.
 */
public final class EvokingGameTests {
    private EvokingGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "evoking_effect_is_beneficial", EvokingGameTests::isBeneficial);
        SpellcraftGameTests.add(SHARED, "evoking_cast_is_infinite", EvokingGameTests::castIsInfiniteAndConsumes);
        SpellcraftGameTests.add(SHARED, "evoking_creative_casts", EvokingGameTests::creativeCasts);
        SpellcraftGameTests.add(SHARED, "evoking_recast_refused", EvokingGameTests::recastRefused);
        SpellcraftGameTests.add(SHARED, "evoking_sneak_dispels", EvokingGameTests::sneakDispels);
        SpellcraftGameTests.add(SHARED, "evoking_sneak_inactive_casts", EvokingGameTests::sneakInactiveCasts);
        SpellcraftGameTests.add(SHARED, "evoking_sneak_other_item_keeps", EvokingGameTests::sneakOtherItemKeeps);
        SpellcraftGameTests.add(SHARED, "evoking_milk_clears", EvokingGameTests::milkClears);
        SpellcraftGameTests.add(
                SHARED,
                "evoking_casting_effects_carry_their_particles",
                EvokingGameTests::castingEffectsCarryTheirParticles);
    }

    /** Strength of Evoking as a cast gives it: infinite, particles hidden, icon shown. */
    static MobEffectInstance evoking() {
        return new MobEffectInstance(ModEffects.EVOKING, MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    /** A survival player holding two Scrolls of Evoking. */
    static ServerPlayer withScrolls(GameTestHelper helper, GameType mode) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        hold(player, new ItemStack(ModItems.EVOKING_SCROLL.get(), 2));
        return player;
    }

    static void isBeneficial(GameTestHelper helper) {
        helper.assertValueEqual(
                ModEffects.EVOKING.value().getCategory(), MobEffectCategory.BENEFICIAL, "effect category");
        helper.succeed();
    }

    static void castIsInfiniteAndConsumes(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "use result");
        helper.assertValueEqual(heldCount(player), 1, "one scroll used up");
        MobEffectInstance instance = requireEffect(helper, player, ModEffects.EVOKING);
        helper.assertTrue(instance.isInfiniteDuration(), "infinite duration");
        helper.assertFalse(instance.isVisible(), "particles hidden so other players see nothing");
        helper.assertTrue(instance.showIcon(), "icon shown");
        helper.succeed();
    }

    static void creativeCasts(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.CREATIVE);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "creative is not refused");
        helper.assertTrue(player.hasEffect(ModEffects.EVOKING), "creative casts Evoking");
        helper.assertValueEqual(heldCount(player), 1, "creative still uses up the scroll");
        helper.succeed();
    }

    static void recastRefused(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL);
        player.addEffect(evoking());
        drainActionBar(player); // discard what joining the level sent
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.FAIL, "recast refused");
        helper.assertValueEqual(heldCount(player), 2, "a refused scroll is kept");
        helper.assertTrue(player.hasEffect(ModEffects.EVOKING), "the effect stays");
        assertActionBarKey(helper, player, SpellScrollItem.REFUSED_KEY);
        helper.succeed();
    }

    static void sneakDispels(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL);
        player.addEffect(evoking());
        player.setShiftKeyDown(true);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "dispel is a use");
        helper.assertFalse(player.hasEffect(ModEffects.EVOKING), "sneak + use while active dispels");
        helper.assertValueEqual(heldCount(player), 1, "dispel uses up a scroll");
        helper.succeed();
    }

    static void sneakInactiveCasts(GameTestHelper helper) {
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "use result");
        helper.assertTrue(player.hasEffect(ModEffects.EVOKING), "sneak + use while inactive casts");
        helper.assertValueEqual(heldCount(player), 1, "one scroll used up");
        helper.succeed();
    }

    /** Dispel is a scroll action: sneak + use with another item in hand leaves the effect. */
    static void sneakOtherItemKeeps(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(evoking());
        hold(player, new ItemStack(Items.STICK));
        player.setShiftKeyDown(true);
        useHeld(helper, player);
        helper.assertTrue(player.hasEffect(ModEffects.EVOKING), "another item does not dispel");
        helper.succeed();
    }

    static void milkClears(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(evoking());
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player);
        helper.assertFalse(player.hasEffect(ModEffects.EVOKING), "milk cures Evoking");
        helper.succeed();
    }

    /** Each casting effect is neutral, mint, and carries its own particle: the sync flag of spec section 2. */
    static void castingEffectsCarryTheirParticles(GameTestHelper helper) {
        helper.assertValueEqual(
                ModEffects.CASTING_FANGS
                        .value()
                        .createParticleOptions(new MobEffectInstance(ModEffects.CASTING_FANGS, 40)),
                ModParticles.CASTING_FANGS.get(),
                "casting_fangs particle");
        helper.assertValueEqual(
                ModEffects.CASTING_VEXES
                        .value()
                        .createParticleOptions(new MobEffectInstance(ModEffects.CASTING_VEXES, 100)),
                ModParticles.CASTING_VEXES.get(),
                "casting_vexes particle");
        helper.assertValueEqual(
                ModEffects.CASTING_FANGS.value().getCategory(), MobEffectCategory.NEUTRAL, "casting_fangs category");
        helper.assertValueEqual(
                ModEffects.CASTING_VEXES.value().getCategory(), MobEffectCategory.NEUTRAL, "casting_vexes category");
        helper.assertValueEqual(ModEffects.CASTING_FANGS.value().getColor(), 0xD2F8EA, "casting_fangs color");
        helper.assertValueEqual(ModEffects.CASTING_VEXES.value().getColor(), 0xD2F8EA, "casting_vexes color");
        helper.succeed();
    }
}
