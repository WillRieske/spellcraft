package io.github.willrieske.spellcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willrieske.spellcraft.effect.Cures;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Milk and the totem clear effects through this consume effect; {@code /effect clear} does not (docs/api-notes.md
 * §10). No event tells them apart, so this spares cure-immune effects here only. {@code require = 1}: a moved
 * call fails startup loudly.
 */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
abstract class ClearAllStatusEffectsConsumeEffectMixin {
    @WrapOperation(
            method = "apply",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z"),
            require = 1)
    @SuppressWarnings("UnusedMethod") // invoked by Mixin at runtime, not by Java code
    private boolean spellcraft$spareCureImmune(LivingEntity user, Operation<Boolean> original) {
        if (!Cures.hasCureImmune(user)) {
            return original.call(user);
        }
        return Cures.removeAllCurable(user);
    }
}
