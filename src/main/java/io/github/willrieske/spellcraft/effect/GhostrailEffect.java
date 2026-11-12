package io.github.willrieske.spellcraft.effect;

import io.github.willrieske.spellcraft.evoking.Ghostrail;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Strength of Ghostrail (Parts 6-7 spec §7.3). Milk-curable. {@link #onEffectAdded} calls
 * {@link Ghostrail#begin}.
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
        if (mob.level() instanceof ServerLevel) {
            Ghostrail.begin(mob);
        }
    }
}
