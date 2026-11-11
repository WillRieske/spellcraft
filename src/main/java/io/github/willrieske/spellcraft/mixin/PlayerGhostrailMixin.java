package io.github.willrieske.spellcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willrieske.spellcraft.effect.ModEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a Ghostrail player pass through blocks. {@code Player.tick} sets {@code noPhysics} from {@code isSpectator()}
 * on both sides ({@code LocalPlayer.tick} reaches it; the effect is synced), and no event reaches that assignment,
 * so this wraps the first {@code isSpectator()} call in {@code tick()} (ordinal 0: the statement
 * {@code this.noPhysics = this.isSpectator()}, api-notes §26). The server then accepts the moves
 * ({@code ServerGamePacketListenerImpl} skips its "moved wrongly" check for {@code noPhysics}). {@code require = 1}:
 * a moved call fails startup loudly. The handler is one expression (the original answer or the effect), so there is
 * no logic to share.
 */
@Mixin(Player.class)
abstract class PlayerGhostrailMixin {
    /**
     * Wraps the first {@code isSpectator()} call of {@code Player.tick}: true for a spectator (the original answer)
     * or for a player with Ghostrail.
     *
     * @param player the ticking player
     * @param original the original call
     * @return whether {@code noPhysics} should be set this tick
     */
    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z",
                            ordinal = 0),
            require = 1)
    @SuppressWarnings("UnusedMethod") // invoked by Mixin at runtime, not by Java code
    private boolean spellcraft$ghostrailNoPhysics(Player player, Operation<Boolean> original) {
        return original.call(player) || player.hasEffect(ModEffects.GHOSTRAIL);
    }
}
