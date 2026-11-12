package io.github.willrieske.spellcraft.effect;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.particle.ModParticles;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's status effects.
 *
 * <p>Functional spec §1.1, §1.3, §2.5, §12.5, §13.1, §14.1.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Spellcraft.MODID);

    /** Strength of Upside-Down. The client flips its view while its player has this. */
    public static final DeferredHolder<MobEffect, MobEffect> UPSIDE_DOWN =
            MOB_EFFECTS.register("upside_down", () -> new MarkerEffect(MobEffectCategory.NEUTRAL, 0x7F5AF0));

    /** Strength of Infinity. Its abilities are synced each server tick by {@code AbilitySync}. */
    public static final DeferredHolder<MobEffect, MobEffect> INFINITY =
            MOB_EFFECTS.register("infinity", InfinityEffect::new);

    /** Strength of Trailing. A marker: {@code TrailLayer} lays the trail while a player has it. */
    public static final DeferredHolder<MobEffect, MobEffect> TRAILING =
            MOB_EFFECTS.register("trailing", () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0x4FD4E6));

    /**
     * Strength of Evoking. A marker: the keys work while a player has it. No duration. Beneficial and milk-curable.
     * Functional spec §1.1, §1.3.
     */
    public static final DeferredHolder<MobEffect, MobEffect> EVOKING =
            MOB_EFFECTS.register("evoking", () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, CastingEffect.MINT));

    /**
     * On a player for the 40 ticks a fang cast keeps the arms up; carries {@code ModParticles.CASTING_FANGS}.
     * Functional spec §2.5.
     */
    public static final DeferredHolder<MobEffect, MobEffect> CASTING_FANGS =
            MOB_EFFECTS.register("casting_fangs", () -> new CastingEffect(ModParticles.CASTING_FANGS));

    /**
     * On a player for the 100 ticks a vex summon keeps the arms up; carries {@code ModParticles.CASTING_VEXES}.
     * Functional spec §2.5.
     */
    public static final DeferredHolder<MobEffect, MobEffect> CASTING_VEXES =
            MOB_EFFECTS.register("casting_vexes", () -> new CastingEffect(ModParticles.CASTING_VEXES));

    /**
     * Strength of Mirror. {@code MirrorEffect.onEffectAdded} spawns the images; they end with the effect.
     * Functional spec §13.1.
     */
    public static final DeferredHolder<MobEffect, MobEffect> MIRROR = MOB_EFFECTS.register("mirror", MirrorEffect::new);

    /**
     * Strength of Ghostrail. {@code Ghostrail} runs it every tick; the Mixin lets a player through blocks.
     * Functional spec §14.1.
     */
    public static final DeferredHolder<MobEffect, MobEffect> GHOSTRAIL =
            MOB_EFFECTS.register("ghostrail", GhostrailEffect::new);

    /**
     * On a non-player caster for the 20 ticks a scroll cast keeps the hands lit; carries
     * {@code ModParticles.CASTING_SCROLL}. Functional spec §12.5.
     */
    public static final DeferredHolder<MobEffect, MobEffect> CASTING_SCROLL =
            MOB_EFFECTS.register("casting_scroll", () -> new CastingEffect(ModParticles.CASTING_SCROLL));

    private ModEffects() {}
}
