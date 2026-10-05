package io.github.willrieske.spellcraft.effect;

import io.github.willrieske.spellcraft.Spellcraft;
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

    private ModEffects() {}
}
