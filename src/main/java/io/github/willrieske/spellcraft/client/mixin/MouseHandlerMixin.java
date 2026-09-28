package io.github.willrieske.spellcraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willrieske.spellcraft.client.ClientEvents;
import io.github.willrieske.spellcraft.client.upsidedown.UpsideDownView;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps mouse look screen-relative while the view is rolled. NeoForge has no event for the turn
 * deltas (docs/api-notes.md, fact 4). {@code require = 1}: if a Minecraft update moves the call,
 * startup fails loudly instead of the compensation silently disappearing.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),
            require = 1)
    @SuppressWarnings("UnusedMethod") // invoked by Mixin at runtime, not by Java code
    private void spellcraft$compensateRoll(LocalPlayer player, double dx, double dy, Operation<Void> original) {
        double roll = ClientEvents.renderedRoll();
        if (roll == 0.0) {
            original.call(player, dx, dy);
            return;
        }
        UpsideDownView.MouseDelta turned = UpsideDownView.turn(dx, dy, roll);
        original.call(player, turned.dx(), turned.dy());
    }
}
