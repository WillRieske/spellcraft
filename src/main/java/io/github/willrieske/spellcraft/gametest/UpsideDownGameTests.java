package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Server-rule GameTests for Strength of Upside-Down, the cast rules through the shared {@link CastRuleGameTests}.
 * Registered in {@link SpellcraftGameTests}.
 */
public final class UpsideDownGameTests {
    private static final CastRuleGameTests RULES = new CastRuleGameTests(
            ModEffects.UPSIDE_DOWN,
            ModItems.UPSIDE_DOWN_SCROLL,
            "Upside-Down",
            ServerConfig.UPSIDE_DOWN_DURATION_SECONDS,
            180);

    private UpsideDownGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "upside_down_effect_is_neutral_marker", UpsideDownGameTests::isANeutralMarker);
        SpellcraftGameTests.add(SHARED, "upside_down_milk_clears", RULES::milkClearsTheEffect);
        SpellcraftGameTests.add(SHARED, "upside_down_cast_consumes", RULES::castConsumesAndGrants);
        SpellcraftGameTests.add(SHARED, "upside_down_recast_refreshes", RULES::recastRefreshes);
        SpellcraftGameTests.add(SHARED, "upside_down_sneak_dispels", RULES::sneakDispels);
        SpellcraftGameTests.add(SHARED, "upside_down_sneak_inactive_casts", RULES::sneakInactiveCasts);
        SpellcraftGameTests.add(SHARED, "upside_down_sneak_other_item_keeps", RULES::sneakOtherItemKeepsTheEffect);
        SpellcraftGameTests.add(SHARED, "upside_down_creative_consumes", RULES::creativeCasts);
        SpellcraftGameTests.add("config_mutation", "upside_down_config_duration", h -> RULES.configDuration(h, 5));
    }

    static void isANeutralMarker(GameTestHelper helper) {
        helper.assertValueEqual(
                ModEffects.UPSIDE_DOWN.value().getCategory(), MobEffectCategory.NEUTRAL, "effect category");
        helper.succeed();
    }
}
