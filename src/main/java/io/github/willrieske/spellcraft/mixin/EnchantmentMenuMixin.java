package io.github.willrieske.spellcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.willrieske.spellcraft.scribing.Scribing;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the enchanting table scribe paper (docs/api-notes.md §15). Both hooks act only when the item slot holds a
 * scribable item; every other item takes the untouched vanilla path. {@code require = 1} on each.
 */
@Mixin(EnchantmentMenu.class)
abstract class EnchantmentMenuMixin {
    @Shadow
    @Final
    private Container enchantSlots;

    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Shadow
    @Final
    private DataSlot enchantmentSeed;

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true, require = 1)
    // UnusedMethod: invoked by Mixin at runtime, not by Java code.
    // ReferenceEquality: vanilla's slotsChanged compares the container by identity too (Container has no equals).
    @SuppressWarnings({"UnusedMethod", "ReferenceEquality"})
    private void spellcraft$offerScribing(Container container, CallbackInfo ci) {
        if (container == enchantSlots && Scribing.isScribable(enchantSlots.getItem(0))) {
            access.execute((level, pos) -> Scribing.offer((EnchantmentMenu) (Object) this));
            // Cancel on both sides: vanilla's client branch would zero the costs for a non-enchantable item, and
            // the server only resends a data slot when its own value changes.
            ci.cancel();
        }
    }

    /** Vanilla's checks (lapis, levels, instabuild waiver) have run; only the apply step is swapped. */
    @WrapOperation(
            method = "clickMenuButton",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"),
            require = 1)
    @SuppressWarnings("UnusedMethod") // invoked by Mixin at runtime, not by Java code
    private void spellcraft$scribe(
            ContainerLevelAccess instance,
            BiConsumer<Level, BlockPos> vanillaApply,
            Operation<Void> original,
            @Local(argsOnly = true) Player player,
            @Local(argsOnly = true) int buttonId) {
        if (!Scribing.isScribable(enchantSlots.getItem(0))) {
            original.call(instance, vanillaApply);
            return;
        }
        instance.execute((level, pos) -> {
            Scribing.scribe(player, buttonId, enchantSlots, level, pos);
            enchantmentSeed.set(player.getEnchantmentSeed());
            ((EnchantmentMenu) (Object) this).slotsChanged(enchantSlots);
        });
    }
}
