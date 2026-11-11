package io.github.willrieske.spellcraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Strength of Ghostrail (Parts 6-7 spec §7.3). Milk-curable. Task 7 makes {@link #onEffectAdded} call
 * {@code Ghostrail.begin}.
 */
public final class GhostrailEffect extends MobEffect {
    /** The effect's color, as the Ghostrail icon's ink. */
    public static final int COLOR = 0xDDE6FF;

    /** Makes the effect: beneficial, in the Ghostrail color. */
    public GhostrailEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    @Override
    public void onEffectAdded(LivingEntity mob, int amplifier) {
        super.onEffectAdded(mob, amplifier);
    }
}
