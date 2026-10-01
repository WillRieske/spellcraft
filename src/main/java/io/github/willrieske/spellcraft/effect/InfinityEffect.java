package io.github.willrieske.spellcraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Strength of Infinity: a marker (the abilities are synced by {@code InfinityAbilitySync}), immune to cures. */
public class InfinityEffect extends MobEffect implements CureImmune {
    public static final int COLOR = 0xD69614;

    public InfinityEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
