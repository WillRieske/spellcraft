package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CastRulesTest {
    @ParameterizedTest(name = "{0}: sneaking={1} active={2} mode={3} -> {4}")
    @CsvSource({
        // Upside-Down: refresh on recast, dispellable, not redundant in creative.
        "UPSIDE_DOWN, false, false, SURVIVAL,  CAST",
        "UPSIDE_DOWN, false, false, ADVENTURE, CAST",
        "UPSIDE_DOWN, false, false, CREATIVE,  CAST",
        "UPSIDE_DOWN, false, false, SPECTATOR, CAST",
        "UPSIDE_DOWN, true,  false, SURVIVAL,  CAST",
        "UPSIDE_DOWN, true,  false, ADVENTURE, CAST",
        "UPSIDE_DOWN, true,  false, CREATIVE,  CAST",
        "UPSIDE_DOWN, true,  false, SPECTATOR, CAST",
        "UPSIDE_DOWN, false, true,  SURVIVAL,  REFRESH",
        "UPSIDE_DOWN, false, true,  ADVENTURE, REFRESH",
        "UPSIDE_DOWN, false, true,  CREATIVE,  REFRESH",
        "UPSIDE_DOWN, false, true,  SPECTATOR, REFRESH",
        "UPSIDE_DOWN, true,  true,  SURVIVAL,  DISPEL",
        "UPSIDE_DOWN, true,  true,  ADVENTURE, DISPEL",
        "UPSIDE_DOWN, true,  true,  CREATIVE,  DISPEL",
        "UPSIDE_DOWN, true,  true,  SPECTATOR, DISPEL",
        // Infinity: refuse on recast, not dispellable, redundant in creative.
        "INFINITY,    false, false, CREATIVE,  REFUSE",
        "INFINITY,    true,  false, CREATIVE,  REFUSE",
        "INFINITY,    false, true,  CREATIVE,  REFUSE",
        "INFINITY,    true,  true,  CREATIVE,  REFUSE",
        "INFINITY,    false, false, SURVIVAL,  CAST",
        "INFINITY,    true,  false, SURVIVAL,  CAST",
        "INFINITY,    false, false, ADVENTURE, CAST",
        "INFINITY,    true,  false, ADVENTURE, CAST",
        "INFINITY,    false, false, SPECTATOR, CAST",
        "INFINITY,    true,  false, SPECTATOR, CAST",
        "INFINITY,    false, true,  SURVIVAL,  REFUSE",
        "INFINITY,    true,  true,  SURVIVAL,  REFUSE",
        "INFINITY,    false, true,  ADVENTURE, REFUSE",
        "INFINITY,    true,  true,  ADVENTURE, REFUSE",
        "INFINITY,    false, true,  SPECTATOR, REFUSE",
        "INFINITY,    true,  true,  SPECTATOR, REFUSE",
        // Evoking: refuse on recast yet dispellable, not redundant in creative (the first spell with both).
        "EVOKING,     false, false, SURVIVAL,  CAST",
        "EVOKING,     false, false, ADVENTURE, CAST",
        "EVOKING,     false, false, CREATIVE,  CAST",
        "EVOKING,     false, false, SPECTATOR, CAST",
        "EVOKING,     true,  false, SURVIVAL,  CAST",
        "EVOKING,     true,  false, ADVENTURE, CAST",
        "EVOKING,     true,  false, CREATIVE,  CAST",
        "EVOKING,     true,  false, SPECTATOR, CAST",
        "EVOKING,     false, true,  SURVIVAL,  REFUSE",
        "EVOKING,     false, true,  ADVENTURE, REFUSE",
        "EVOKING,     false, true,  CREATIVE,  REFUSE",
        "EVOKING,     false, true,  SPECTATOR, REFUSE",
        "EVOKING,     true,  true,  SURVIVAL,  DISPEL",
        "EVOKING,     true,  true,  ADVENTURE, DISPEL",
        "EVOKING,     true,  true,  CREATIVE,  DISPEL",
        "EVOKING,     true,  true,  SPECTATOR, DISPEL",
        // Mirror: refuse on recast yet dispellable, not redundant in creative (never refuses a first cast).
        "MIRROR,      false, false, SURVIVAL,  CAST",
        "MIRROR,      false, false, ADVENTURE, CAST",
        "MIRROR,      false, false, CREATIVE,  CAST",
        "MIRROR,      false, false, SPECTATOR, CAST",
        "MIRROR,      true,  false, SURVIVAL,  CAST",
        "MIRROR,      true,  false, ADVENTURE, CAST",
        "MIRROR,      true,  false, CREATIVE,  CAST",
        "MIRROR,      true,  false, SPECTATOR, CAST",
        "MIRROR,      false, true,  SURVIVAL,  REFUSE",
        "MIRROR,      false, true,  ADVENTURE, REFUSE",
        "MIRROR,      false, true,  CREATIVE,  REFUSE",
        "MIRROR,      false, true,  SPECTATOR, REFUSE",
        "MIRROR,      true,  true,  SURVIVAL,  DISPEL",
        "MIRROR,      true,  true,  ADVENTURE, DISPEL",
        "MIRROR,      true,  true,  CREATIVE,  DISPEL",
        "MIRROR,      true,  true,  SPECTATOR, DISPEL",
        // Ghostrail: as Mirror, but redundant in creative, so every creative row is refused first.
        "GHOSTRAIL,   false, false, SURVIVAL,  CAST",
        "GHOSTRAIL,   false, false, ADVENTURE, CAST",
        "GHOSTRAIL,   false, false, CREATIVE,  REFUSE",
        "GHOSTRAIL,   false, false, SPECTATOR, CAST",
        "GHOSTRAIL,   true,  false, SURVIVAL,  CAST",
        "GHOSTRAIL,   true,  false, ADVENTURE, CAST",
        "GHOSTRAIL,   true,  false, CREATIVE,  REFUSE",
        "GHOSTRAIL,   true,  false, SPECTATOR, CAST",
        "GHOSTRAIL,   false, true,  SURVIVAL,  REFUSE",
        "GHOSTRAIL,   false, true,  ADVENTURE, REFUSE",
        "GHOSTRAIL,   false, true,  CREATIVE,  REFUSE",
        "GHOSTRAIL,   false, true,  SPECTATOR, REFUSE",
        "GHOSTRAIL,   true,  true,  SURVIVAL,  DISPEL",
        "GHOSTRAIL,   true,  true,  ADVENTURE, DISPEL",
        "GHOSTRAIL,   true,  true,  CREATIVE,  REFUSE",
        "GHOSTRAIL,   true,  true,  SPECTATOR, DISPEL",
    })
    void decidesEveryCase(String spell, boolean sneaking, boolean active, PlayerMode mode, CastOutcome expected) {
        SpellRules rules = switch (spell) {
            case "EVOKING" -> SpellDeclarations.EVOKING;
            case "MIRROR" -> SpellDeclarations.MIRROR;
            case "GHOSTRAIL" -> SpellDeclarations.GHOSTRAIL;
            case "INFINITY" -> SpellDeclarations.INFINITY;
            case "UPSIDE_DOWN" -> SpellDeclarations.UPSIDE_DOWN;
            default -> throw new IllegalArgumentException("unknown spell in the CSV: " + spell);
        };
        assertThat(CastRules.decide(rules, new Caster(sneaking, active, mode))).isEqualTo(expected);
    }

    @Test
    void onlyRefuseKeepsTheScroll() {
        for (CastOutcome outcome : CastOutcome.values()) {
            assertThat(outcome.consumesScroll()).as(outcome.name()).isEqualTo(outcome != CastOutcome.REFUSE);
        }
    }
}
