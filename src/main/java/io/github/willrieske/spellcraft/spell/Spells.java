package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.rules.SpellDeclarations;
import java.util.List;

/** Binds each pure {@link SpellDeclarations} entry to its effect and duration. */
public final class Spells {
    public static final Spell UPSIDE_DOWN = new Spell(
            Spellcraft.id(SpellDeclarations.UPSIDE_DOWN.name()),
            ModEffects.UPSIDE_DOWN,
            SpellDeclarations.UPSIDE_DOWN,
            ServerConfig::upsideDownDurationTicks);

    /** Every spell, in declaration order (creative tab, scribing). */
    public static final List<Spell> ALL = List.of(UPSIDE_DOWN);

    private Spells() {}
}
