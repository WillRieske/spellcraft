package io.github.willrieske.spellcraft.effect;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.particle.ModParticles;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Spellcraft.MODID);

    /** Strength of Upside-Down. The client flips its view while its player has this. */
    public static final DeferredHolder<MobEffect, MobEffect> UPSIDE_DOWN =
            MOB_EFFECTS.register("upside_down", () -> new MarkerEffect(MobEffectCategory.NEUTRAL, 0x7F5AF0));

    /** Strength of Infinity. Its abilities are synced each server tick by {@code InfinityAbilitySync}. */
    public static final DeferredHolder<MobEffect, MobEffect> INFINITY =
            MOB_EFFECTS.register("infinity", InfinityEffect::new);

    /** Strength of Trailing. A marker: {@code TrailLayer} lays the trail while a player has it. */
    public static final DeferredHolder<MobEffect, MobEffect> TRAILING =
            MOB_EFFECTS.register("trailing", () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0x4FD4E6));

    /** Strength of Evoking. A marker: the keys work while a player has it. No duration. */
    public static final DeferredHolder<MobEffect, MobEffect> EVOKING =
            MOB_EFFECTS.register("evoking", () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, CastingEffect.MINT));

    /** On a player for the 40 ticks a fang cast keeps the arms up; carries {@code ModParticles.CASTING_FANGS}. */
    public static final DeferredHolder<MobEffect, MobEffect> CASTING_FANGS =
            MOB_EFFECTS.register("casting_fangs", () -> new CastingEffect(ModParticles.CASTING_FANGS));

    /** On a player for the 100 ticks a vex summon keeps the arms up; carries {@code ModParticles.CASTING_VEXES}. */
    public static final DeferredHolder<MobEffect, MobEffect> CASTING_VEXES =
            MOB_EFFECTS.register("casting_vexes", () -> new CastingEffect(ModParticles.CASTING_VEXES));

    private ModEffects() {}
}
