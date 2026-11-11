package io.github.willrieske.spellcraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Strength of Mirror (Parts 6-7 spec §7.2). Milk-curable. Task 4 fills in {@link #onEffectAdded}. */
public final class MirrorEffect extends MobEffect {
    /** The effect's color, as the Mirror icon's ink. */
    public static final int COLOR = 0xE066FF;

    /** Makes the effect: beneficial, in the Mirror color. */
    public MirrorEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    @Override
    public void onEffectAdded(LivingEntity mob, int amplifier) {
        super.onEffectAdded(mob, amplifier);
    }
}
