package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;

public final class Spells {
    public static final Spell UPSIDE_DOWN = new Spell(
            Spellcraft.id("upside_down"),
            ModEffects.UPSIDE_DOWN,
            ServerConfig::upsideDownDurationTicks,
            ServerConfig::upsideDownCooldownTicks);

    private Spells() {}
}
