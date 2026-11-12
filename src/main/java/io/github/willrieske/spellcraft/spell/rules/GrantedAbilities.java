package io.github.willrieske.spellcraft.spell.rules;

import java.util.Objects;

/**
 * Which creative-style abilities Infinity and Ghostrail should hold (Parts 6-7 spec §7.3; formerly
 * {@code InfinityAbilities}). Infinity grants {@code mayfly} and {@code instabuild}; Ghostrail grants
 * {@code mayfly} and {@code flying}. {@code invulnerable} is deliberately absent. A source's "mark" records that
 * it, not vanilla or another mod, set the flags, so a revoke is never applied twice.
 */
public final class GrantedAbilities {
    /**
     * Everything the decision reads.
     *
     * @param mode the player's game mode
     * @param infinity whether the Infinity effect is active
     * @param ghostrail whether the Ghostrail effect is active
     * @param infinityMarked whether Infinity set the flags it holds
     * @param ghostrailMarked whether Ghostrail set the flags it holds
     * @param mayfly the player's current {@code mayfly} flag
     * @param instabuild the player's current {@code instabuild} flag
     * @param flying the player's current {@code flying} flag
     */
    public record State(
            PlayerMode mode,
            boolean infinity,
            boolean ghostrail,
            boolean infinityMarked,
            boolean ghostrailMarked,
            boolean mayfly,
            boolean instabuild,
            boolean flying) {
        /** Refuses a missing game mode. */
        public State {
            Objects.requireNonNull(mode, "mode");
        }
    }

    /**
     * What to do, in this order: reset to the game mode's abilities if {@code reset}; set {@code mayfly},
     * {@code instabuild} and {@code flying} true where they say so; store the two marks.
     *
     * @param reset whether to reset to the game mode's own abilities first
     * @param mayfly whether to set {@code mayfly}
     * @param instabuild whether to set {@code instabuild}
     * @param flying whether to set {@code flying}
     * @param infinityMarked the Infinity mark to store
     * @param ghostrailMarked the Ghostrail mark to store
     */
    public record Plan(
            boolean reset,
            boolean mayfly,
            boolean instabuild,
            boolean flying,
            boolean infinityMarked,
            boolean ghostrailMarked) {
        /**
         * Whether applying the plan touches the player's abilities (and so needs an abilities packet).
         *
         * @return true if the plan resets or sets any flag
         */
        public boolean changesAbilities() {
            return reset || mayfly || instabuild || flying;
        }
    }

    private GrantedAbilities() {}

    /**
     * One plan settles the state every tick. A source that ended (its mark set, its effect gone) resets the
     * abilities; whatever is still active then grants again in the same plan. While Ghostrail is active and the
     * player is not flying, it re-asserts {@code flying} every tick (spec §7.3).
     *
     * @param state the player's mode, effects, marks and flags
     * @return the plan
     */
    public static Plan decide(State state) {
        boolean granting = state.mode() == PlayerMode.SURVIVAL || state.mode() == PlayerMode.ADVENTURE;
        boolean infinityNeed = state.infinity() && granting;
        boolean ghostrailNeed = state.ghostrail() && granting;
        boolean ended =
                (state.infinityMarked() && !state.infinity()) || (state.ghostrailMarked() && !state.ghostrail());
        if (!infinityNeed && !ghostrailNeed) {
            return new Plan(ended, false, false, false, false, false);
        }
        boolean grant = ended
                || !state.mayfly()
                || (infinityNeed && (!state.instabuild() || !state.infinityMarked()))
                || (ghostrailNeed && (!state.flying() || !state.ghostrailMarked()));
        return new Plan(ended, grant, grant && infinityNeed, grant && ghostrailNeed, infinityNeed, ghostrailNeed);
    }
}
