package io.github.willrieske.spellcraft.spell;

import java.util.function.IntSupplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * A castable spell: the effect it grants and, read at cast time, how long it lasts and the
 * scroll cooldown. No registry: spells are constants in {@link Spells}.
 */
public record Spell(Identifier id, Holder<MobEffect> effect, IntSupplier durationTicks, IntSupplier cooldownTicks) {}
