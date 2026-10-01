package io.github.willrieske.spellcraft.spell.rules;

import java.util.Objects;

/**
 * The business rules for one spell, free of Minecraft types.
 *
 * @param name the resource path (scroll item {@code <name>_scroll}, effect {@code <name>})
 * @param tier the spell's power band
 * @param recast what a use does while the effect is already active (unless dispelled)
 * @param dispellable whether sneak + use while active removes the effect
 * @param redundantInCreative whether creative mode already grants what the spell gives, so casting there is refused
 */
public record SpellRules(String name, SpellTier tier, Recast recast, boolean dispellable, boolean redundantInCreative) {
    /** What a use does while the effect is already active. */
    public enum Recast {
        /** Restart the effect's duration. */
        REFRESH,
        /** Keep the scroll and tell the player the effect is already on. */
        REFUSE
    }

    public SpellRules {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(recast, "recast");
    }
}
