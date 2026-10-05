package io.github.willrieske.spellcraft.spell.rules;

import java.util.Objects;

/**
 * What the cast rules need to know about the player using a scroll.
 *
 * @param sneaking whether the player is holding sneak
 * @param effectActive whether the spell's effect is already on the player
 * @param mode the player's game mode
 */
public record Caster(boolean sneaking, boolean effectActive, PlayerMode mode) {
    public Caster {
        Objects.requireNonNull(mode, "mode");
    }
}
