package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.drainActionBar;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.heldCount;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.hold;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.requireEffect;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.useHeld;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.useHeldViaServer;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.SpellScrollItem;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.SpellDeclarations;
import io.github.willrieske.spellcraft.spell.rules.SpellRules;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.level.GameType;

/** Cast-rule and cure GameTests for Strength of Infinity. Registered in {@link SpellcraftGameTests}. */
public final class InfinityGameTests {
    private InfinityGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "infinity_effect_is_beneficial", InfinityGameTests::isBeneficial);
        SpellcraftGameTests.add(
                SHARED, "infinity_cast_in_survival", h -> castIsInfiniteAndConsumes(h, GameType.SURVIVAL));
        SpellcraftGameTests.add(
                SHARED, "infinity_cast_in_adventure", h -> castIsInfiniteAndConsumes(h, GameType.ADVENTURE));
        SpellcraftGameTests.add(SHARED, "infinity_refused_in_creative", InfinityGameTests::refusedInCreative);
        SpellcraftGameTests.add(SHARED, "infinity_refused_while_active", InfinityGameTests::refusedWhileActive);
        SpellcraftGameTests.add(
                SHARED, "infinity_refused_in_creative_while_active", InfinityGameTests::refusedInCreativeWhileActive);
        SpellcraftGameTests.add(
                SHARED, "creative_use_via_server_shrinks_once", InfinityGameTests::creativeUseViaServerShrinksOnce);
        SpellcraftGameTests.add(
                SHARED, "milk_still_clears_without_infinity", InfinityGameTests::milkStillClearsWithoutInfinity);
        SpellcraftGameTests.add(SHARED, "infinity_milk_does_not_cure", InfinityGameTests::milkDoesNotCure);
        SpellcraftGameTests.add(SHARED, "infinity_totem_does_not_cure", InfinityGameTests::totemDoesNotCure);
        SpellcraftGameTests.add(SHARED, "infinity_effect_clear_removes", InfinityGameTests::effectClearRemoves);
        SpellcraftGameTests.add(SHARED, "infinity_instabuild_still_consumes", InfinityGameTests::instabuildConsumes);
        SpellcraftGameTests.add(SHARED, "every_declared_spell_has_a_scroll", InfinityGameTests::everySpellHasAScroll);
    }

    static MobEffectInstance infinite() {
        return new MobEffectInstance(ModEffects.INFINITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    static void isBeneficial(GameTestHelper helper) {
        helper.assertValueEqual(
                ModEffects.INFINITY.value().getCategory(), MobEffectCategory.BENEFICIAL, "effect category");
        helper.succeed();
    }

    private static void castIsInfiniteAndConsumes(GameTestHelper helper, GameType mode) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        hold(player, new ItemStack(ModItems.INFINITY_SCROLL.get(), 2));
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.SUCCESS, "use result");
        helper.assertValueEqual(heldCount(player), 1, "one scroll used up");
        helper.assertTrue(requireEffect(helper, player, ModEffects.INFINITY).isInfiniteDuration(), "infinite duration");
        helper.succeed();
    }

    static void refusedInCreative(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.CREATIVE);
        hold(player, new ItemStack(ModItems.INFINITY_SCROLL.get(), 2));
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.FAIL, "refused in creative");
        helper.assertValueEqual(heldCount(player), 2, "a refused scroll is kept");
        helper.assertFalse(player.hasEffect(ModEffects.INFINITY), "no effect in creative");
        helper.succeed();
    }

    static void refusedWhileActive(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(infinite());
        hold(player, new ItemStack(ModItems.INFINITY_SCROLL.get(), 2));
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.FAIL, "recast refused");
        player.setShiftKeyDown(true);
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.FAIL, "sneak + use refused");
        helper.assertValueEqual(heldCount(player), 2, "refused scrolls are kept");
        helper.assertTrue(player.hasEffect(ModEffects.INFINITY), "Infinity can't be dispelled in game");
        helper.succeed();
    }

    /** Both refusal reasons at once: FAIL, the scroll kept, and the action-bar message sent. */
    static void refusedInCreativeWhileActive(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.CREATIVE);
        player.addEffect(infinite());
        hold(player, new ItemStack(ModItems.INFINITY_SCROLL.get(), 2));
        drainActionBar(player); // discard what joining the level sent
        helper.assertValueEqual(useHeld(helper, player), InteractionResult.FAIL, "refused");
        helper.assertValueEqual(heldCount(player), 2, "a refused scroll is kept");
        List<Component> messages = drainActionBar(player);
        helper.assertValueEqual(messages.size(), 1, "one action-bar message");
        helper.assertTrue(
                messages.get(0).getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(SpellScrollItem.REFUSED_KEY),
                "the 'scroll not needed' message");
        helper.succeed();
    }

    /** Through the real server path, a creative player's scroll is still single-use (the stack shrinks by 1). */
    static void creativeUseViaServerShrinksOnce(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.CREATIVE);
        hold(player, new ItemStack(ModItems.UPSIDE_DOWN_SCROLL.get(), 2));
        helper.assertTrue(useHeldViaServer(player) instanceof InteractionResult.Success, "use succeeded");
        helper.assertValueEqual(heldCount(player), 1, "shrunk by exactly one");
        helper.succeed();
    }

    /** The mixin's unchanged branch: with no Infinity, milk still clears every effect. */
    static void milkStillClearsWithoutInfinity(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 200));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player);
        helper.assertTrue(player.getActiveEffects().isEmpty(), "milk clears all effects");
        helper.succeed();
    }

    static void milkDoesNotCure(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(infinite());
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 200));
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(ModEffects.INFINITY), "milk leaves Infinity");
        helper.assertFalse(player.hasEffect(ModEffects.UPSIDE_DOWN), "milk still cures everything else");
        helper.succeed();
    }

    static void totemDoesNotCure(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(infinite());
        player.addEffect(new MobEffectInstance(ModEffects.UPSIDE_DOWN, 200));
        DeathProtection.TOTEM_OF_UNDYING.applyEffects(new ItemStack(Items.TOTEM_OF_UNDYING), player);
        helper.assertTrue(player.hasEffect(ModEffects.INFINITY), "a totem leaves Infinity");
        helper.assertFalse(player.hasEffect(ModEffects.UPSIDE_DOWN), "a totem still cures everything else");
        helper.succeed();
    }

    /** {@code /effect clear} runs exactly {@code removeAllEffects()} (docs/api-notes.md §10). */
    static void effectClearRemoves(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(infinite());
        player.removeAllEffects();
        helper.assertFalse(player.hasEffect(ModEffects.INFINITY), "/effect clear removes Infinity");
        helper.succeed();
    }

    /** With Infinity's instabuild on, vanilla's consume() would skip; scrolls must still be used up. */
    static void instabuildConsumes(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.getAbilities().instabuild = true;
        hold(player, new ItemStack(ModItems.UPSIDE_DOWN_SCROLL.get(), 2));
        useHeld(helper, player);
        helper.assertValueEqual(heldCount(player), 1, "scroll used up despite instabuild");
        helper.succeed();
    }

    static void everySpellHasAScroll(GameTestHelper helper) {
        helper.assertValueEqual(Spells.ALL.size(), SpellDeclarations.ALL.size(), "every declaration is bound");
        for (SpellRules rules : SpellDeclarations.ALL) {
            helper.assertTrue(
                    BuiltInRegistries.ITEM.containsKey(Spellcraft.id(rules.name() + "_scroll")),
                    "scroll registered for " + rules.name());
        }
        helper.succeed();
    }
}
