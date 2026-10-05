package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.InfinityAbilities.Action;
import io.github.willrieske.spellcraft.spell.rules.InfinityAbilities.State;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class InfinityAbilitiesPropertiesTest {
    /** Test model of the plumbing: what applying {@code action} leaves behind (vanilla mode defaults on revoke). */
    private static State after(State s, Action action) {
        boolean modeFlies = s.mode() == PlayerMode.CREATIVE || s.mode() == PlayerMode.SPECTATOR;
        return switch (action) {
            case GRANT -> new State(s.mode(), s.hasInfinity(), true, true, true);
            case REVOKE -> new State(s.mode(), s.hasInfinity(), false, modeFlies, s.mode() == PlayerMode.CREATIVE);
            case RELEASE -> new State(s.mode(), s.hasInfinity(), false, s.mayfly(), s.instabuild());
            case NONE -> s;
        };
    }

    @Property
    void oneActionSettlesTheState(
            @ForAll PlayerMode mode,
            @ForAll boolean hasInfinity,
            @ForAll boolean marked,
            @ForAll boolean mayfly,
            @ForAll boolean instabuild) {
        State state = new State(mode, hasInfinity, marked, mayfly, instabuild);
        assertThat(InfinityAbilities.decide(after(state, InfinityAbilities.decide(state))))
                .isEqualTo(Action.NONE);
    }

    @Property
    void grantsOnlyWithInfinityInSurvivalOrAdventure(
            @ForAll PlayerMode mode,
            @ForAll boolean hasInfinity,
            @ForAll boolean marked,
            @ForAll boolean mayfly,
            @ForAll boolean instabuild) {
        Action action = InfinityAbilities.decide(new State(mode, hasInfinity, marked, mayfly, instabuild));
        if (action == Action.GRANT) {
            assertThat(hasInfinity).isTrue();
            assertThat(mode).isIn(PlayerMode.SURVIVAL, PlayerMode.ADVENTURE);
        }
    }

    @Property
    void revokesOnlyWhatItMarked(
            @ForAll PlayerMode mode,
            @ForAll boolean hasInfinity,
            @ForAll boolean marked,
            @ForAll boolean mayfly,
            @ForAll boolean instabuild) {
        Action action = InfinityAbilities.decide(new State(mode, hasInfinity, marked, mayfly, instabuild));
        if (action == Action.REVOKE) {
            assertThat(marked).isTrue();
            assertThat(hasInfinity).isFalse();
        }
    }
}
