package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.spell.rules.SpellRules;
import java.util.function.IntSupplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A castable spell: its effect, the pure business rules for casting it, and how long it lasts (read at cast time,
 * so config edits apply to the next cast). No registry: spells are constants in {@link Spells}.
 */
public record Spell(Identifier id, Holder<MobEffect> effect, SpellRules rules, IntSupplier durationTicks) {
    /** Duration for spells that never run out; the HUD shows "∞". */
    public static final IntSupplier INFINITE = () -> MobEffectInstance.INFINITE_DURATION;
}
