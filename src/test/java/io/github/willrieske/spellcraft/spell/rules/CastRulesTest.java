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
    })
    void decidesEveryCase(String spell, boolean sneaking, boolean active, PlayerMode mode, CastOutcome expected) {
        SpellRules rules = switch (spell) {
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
