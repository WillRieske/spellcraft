package io.github.willrieske.spellcraft.spell.rules;

import java.util.Objects;

/**
 * Which creative abilities Infinity should hold (spec §3). {@code invulnerable} is deliberately absent: Infinity
 * never grants immortality. The "mark" records that Infinity, not vanilla or another mod, set the flags.
 */
public final class InfinityAbilities {
    public enum Action {
        /** Set {@code mayfly} and {@code instabuild}; set the mark. */
        GRANT,
        /** Reset the flags to the game mode's defaults (stops flight where the mode can't fly); clear the mark. */
        REVOKE,
        /** Clear the mark only: creative and spectator own their flags. */
        RELEASE,
        NONE
    }

    public record State(PlayerMode mode, boolean hasInfinity, boolean marked, boolean mayfly, boolean instabuild) {
        public State {
            Objects.requireNonNull(mode, "mode");
        }
    }

    private InfinityAbilities() {}

    /** One action settles the state every tick (spec §3). */
    public static Action decide(State state) {
        boolean grantingMode = state.mode() == PlayerMode.SURVIVAL || state.mode() == PlayerMode.ADVENTURE;
        if (state.hasInfinity()) {
            if (grantingMode) {
                return state.marked() && state.mayfly() && state.instabuild() ? Action.NONE : Action.GRANT;
            }
            return state.marked() ? Action.RELEASE : Action.NONE;
        }
        return state.marked() ? Action.REVOKE : Action.NONE;
    }
}
