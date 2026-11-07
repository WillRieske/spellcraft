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

    /**
     * When the Evoking effect is asked for its category, should it be beneficial.
     *
     * <p>Method under test: {@link ModEffects#EVOKING}
     */
    static void isBeneficial(GameTestHelper helper) {
        // Act & Assert
        helper.assertValueEqual(
                ModEffects.EVOKING.value().getCategory(), MobEffectCategory.BENEFICIAL, "effect category");
        helper.succeed();
    }

    /**
     * When a survival player uses a Scroll of Evoking, should the effect be infinite with particles hidden and the
     * icon shown, and one scroll be used up.
     *
     * <p>Method under test: {@link SpellScrollItem#use}
     */
    static void castIsInfiniteAndConsumes(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL);

        // Act
        InteractionResult result = useHeld(helper, player);

        // Assert
        helper.assertValueEqual(result, InteractionResult.SUCCESS, "use result");
        helper.assertValueEqual(heldCount(player), 1, "one scroll used up");
        MobEffectInstance instance = requireEffect(helper, player, ModEffects.EVOKING);
        helper.assertTrue(instance.isInfiniteDuration(), "infinite duration");
        helper.assertFalse(instance.isVisible(), "particles hidden so other players see nothing");
        helper.assertTrue(instance.showIcon(), "icon shown");
        helper.succeed();
    }

    /**
     * When a creative player uses a Scroll of Evoking, should the cast not be refused: the effect starts and the
     * scroll is still used up.
     *
     * <p>Method under test: {@link SpellScrollItem#use}
     */
    static void creativeCasts(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = withScrolls(helper, GameType.CREATIVE);

        // Act
        InteractionResult result = useHeld(helper, player);

        // Assert
        helper.assertValueEqual(result, InteractionResult.SUCCESS, "creative is not refused");
        helper.assertTrue(player.hasEffect(ModEffects.EVOKING), "creative casts Evoking");
        helper.assertValueEqual(heldCount(player), 1, "creative still uses up the scroll");
        helper.succeed();
    }

    /**
     * When a player who already has Evoking uses another scroll, should the use be refused: the scroll is kept, the
     * effect stays, and the refusal notice shows.
     *
     * <p>Method under test: {@link SpellScrollItem#use}
     */
    static void recastRefused(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL);
        player.addEffect(evoking());
        drainActionBar(player); // discard what joining the level sent

        // Act
        InteractionResult result = useHeld(helper, player);

        // Assert
        helper.assertValueEqual(result, InteractionResult.FAIL, "recast refused");
        helper.assertValueEqual(heldCount(player), 2, "a refused scroll is kept");
        helper.assertTrue(player.hasEffect(ModEffects.EVOKING), "the effect stays");
        assertActionBarKey(helper, player, SpellScrollItem.REFUSED_KEY);
        helper.succeed();
    }

    /**
     * When a player with Evoking sneaks and uses a scroll, should the effect be dispelled and a scroll used up.
     *
     * <p>Method under test: {@link SpellScrollItem#use}
     */
    static void sneakDispels(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL);
        player.addEffect(evoking());
        player.setShiftKeyDown(true);

        // Act
        InteractionResult result = useHeld(helper, player);

        // Assert
        helper.assertValueEqual(result, InteractionResult.SUCCESS, "dispel is a use");
        helper.assertFalse(player.hasEffect(ModEffects.EVOKING), "sneak + use while active dispels");
        helper.assertValueEqual(heldCount(player), 1, "dispel uses up a scroll");
        helper.succeed();
    }

    /**
     * When a player without Evoking sneaks and uses a scroll, should the effect be cast and a scroll used up.
     *
     * <p>Method under test: {@link SpellScrollItem#use}
     */
    static void sneakInactiveCasts(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = withScrolls(helper, GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        // Act
        InteractionResult result = useHeld(helper, player);

        // Assert
        helper.assertValueEqual(result, InteractionResult.SUCCESS, "use result");
        helper.assertTrue(player.hasEffect(ModEffects.EVOKING), "sneak + use while inactive casts");
        helper.assertValueEqual(heldCount(player), 1, "one scroll used up");
        helper.succeed();
    }

    /**
     * When a player with Evoking sneaks and uses another item, should the effect stay: dispel is a scroll action.
     *
     * <p>Method under test: {@link net.minecraft.world.item.Item#use}
     */
    static void sneakOtherItemKeeps(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(evoking());
        hold(player, new ItemStack(Items.STICK));
        player.setShiftKeyDown(true);

        // Act
        useHeld(helper, player);

        // Assert
        helper.assertTrue(player.hasEffect(ModEffects.EVOKING), "another item does not dispel");
        helper.succeed();
    }

    /**
     * When a player with Evoking drinks milk, should the effect be cleared.
     *
     * <p>Method under test: {@link net.minecraft.world.item.Item#finishUsingItem}
     */
    static void milkClears(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(evoking());

        // Act
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player);

        // Assert
        helper.assertFalse(player.hasEffect(ModEffects.EVOKING), "milk cures Evoking");
        helper.succeed();
    }

    /**
     * When each casting effect is asked for its particle, category and color, should it carry its own particle, be
     * neutral, and be mint: the sync flag of spec section 2.
     *
     * <p>Method under test: {@link io.github.willrieske.spellcraft.effect.CastingEffect}
     */
    static void castingEffectsCarryTheirParticles(GameTestHelper helper) {
        // Act & Assert
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
