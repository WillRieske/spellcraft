package io.github.willrieske.spellcraft.gametest;

import io.github.willrieske.spellcraft.effect.ModEffects;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
}
