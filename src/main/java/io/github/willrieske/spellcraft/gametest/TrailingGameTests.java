package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Cast-rule GameTests for Strength of Trailing (spec §6), through the shared {@link CastRuleGameTests}. The trail
 * itself is tested in {@code TrailGameTests} and {@code TrailLayerGameTests}.
 */
public final class TrailingGameTests {
    private static final CastRuleGameTests RULES = new CastRuleGameTests(
            ModEffects.TRAILING, ModItems.TRAILING_SCROLL, "Trailing", ServerConfig.TRAILING_DURATION_SECONDS, 480);

    private TrailingGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "trailing_effect_is_beneficial_marker", TrailingGameTests::isABeneficialMarker);
        SpellcraftGameTests.add(SHARED, "trailing_cast_consumes", RULES::castConsumesAndGrants);
        SpellcraftGameTests.add(SHARED, "trailing_creative_casts", RULES::creativeCasts);
        SpellcraftGameTests.add(SHARED, "trailing_recast_refreshes", RULES::recastRefreshes);
        SpellcraftGameTests.add(SHARED, "trailing_sneak_dispels", RULES::sneakDispels);
        SpellcraftGameTests.add(SHARED, "trailing_sneak_inactive_casts", RULES::sneakInactiveCasts);
        SpellcraftGameTests.add(SHARED, "trailing_sneak_other_item_keeps", RULES::sneakOtherItemKeepsTheEffect);
        SpellcraftGameTests.add(SHARED, "trailing_milk_clears", RULES::milkClearsTheEffect);
        SpellcraftGameTests.add("config_mutation", "trailing_config_duration", h -> RULES.configDuration(h, 7));
    }

    static void isABeneficialMarker(GameTestHelper helper) {
        helper.assertValueEqual(
                ModEffects.TRAILING.value().getCategory(), MobEffectCategory.BENEFICIAL, "effect category");
        helper.succeed();
    }
}
