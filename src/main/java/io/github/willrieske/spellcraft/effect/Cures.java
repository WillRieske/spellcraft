package io.github.willrieske.spellcraft.effect;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** "Clear all effects" from an item (milk, totem) that spares {@link CureImmune} effects. */
public final class Cures {
    private Cures() {}

    /** Whether {@code entity} currently carries any {@link CureImmune} effect. */
    public static boolean hasCureImmune(LivingEntity entity) {
        return entity.getActiveEffects().stream().anyMatch(e -> isImmune(e.getEffect()));
    }

    /** Like {@code removeAllEffects()} but keeps immune effects; each removal still fires NeoForge's event. */
    public static boolean removeAllCurable(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return false;
        }
        List<Holder<MobEffect>> curable = entity.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(effect -> !isImmune(effect))
                .toList();
        boolean removed = false;
        for (Holder<MobEffect> effect : curable) {
            removed |= entity.removeEffect(effect);
        }
        return removed;
    }

    private static boolean isImmune(Holder<MobEffect> effect) {
        return effect.value() instanceof CureImmune;
    }
}
