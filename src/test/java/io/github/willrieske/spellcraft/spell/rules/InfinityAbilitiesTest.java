package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.willrieske.spellcraft.spell.rules.InfinityAbilities.Action;
import io.github.willrieske.spellcraft.spell.rules.InfinityAbilities.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InfinityAbilitiesTest {
    @ParameterizedTest(name = "{0} infinity={1} marked={2} mayfly={3} instabuild={4} -> {5}")
    @CsvSource({
        "SURVIVAL,  true,  false, false, false, GRANT",
        "ADVENTURE, true,  false, false, false, GRANT",
        "SURVIVAL,  true,  true,  true,  true,  NONE",
        "ADVENTURE, true,  true,  true,  true,  NONE",
        // Vanilla reset a flag (e.g. a game mode round trip): grant again.
        "SURVIVAL,  true,  true,  false, true,  GRANT",
        "SURVIVAL,  true,  true,  true,  false, GRANT",
        // Flags from elsewhere while Infinity is active: take ownership so removal revokes them.
        "SURVIVAL,  true,  false, true,  true,  GRANT",
        // Creative and spectator own their flags: drop the mark, change nothing.
        "CREATIVE,  true,  true,  true,  true,  RELEASE",
        "SPECTATOR, true,  true,  true,  false, RELEASE",
        "CREATIVE,  true,  false, true,  true,  NONE",
        // Infinity gone: revoke only what we granted.
        "SURVIVAL,  false, true,  true,  true,  REVOKE",
        "CREATIVE,  false, true,  true,  true,  REVOKE",
        "SURVIVAL,  false, false, true,  false, NONE",
        "ADVENTURE, false, false, false, false, NONE",
    })
    void decidesEveryCase(
            PlayerMode mode, boolean hasInfinity, boolean marked, boolean mayfly, boolean instabuild, Action expected) {
        assertThat(InfinityAbilities.decide(new State(mode, hasInfinity, marked, mayfly, instabuild)))
                .isEqualTo(expected);
    }

    @Test
    void stateRequiresAMode() {
        assertThatThrownBy(() -> new State(null, true, false, false, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode");
    }
}
