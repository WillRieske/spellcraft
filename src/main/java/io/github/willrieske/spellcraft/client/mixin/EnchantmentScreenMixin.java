package io.github.willrieske.spellcraft.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.willrieske.spellcraft.client.ScribingTooltip;
import io.github.willrieske.spellcraft.scribing.Scribing;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replaces the enchanting tooltip while paper is being scribed (docs/api-notes.md §15). The tooltip must be swapped
 * at vanilla's call, because the first tooltip set in a frame wins. {@code require = 1}.
 */
@Mixin(EnchantmentScreen.class)
abstract class EnchantmentScreenMixin {
    @WrapOperation(
            method = "extractRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"),
            require = 1)
    @SuppressWarnings("UnusedMethod") // invoked by Mixin at runtime, not by Java code
    private void spellcraft$scribingTooltip(
            GuiGraphicsExtractor graphics,
            Font font,
            List<Component> lines,
            int mouseX,
            int mouseY,
            Operation<Void> original) {
        EnchantmentScreen screen = (EnchantmentScreen) (Object) this;
        EnchantmentMenu menu = screen.getMenu();
        LocalPlayer player = Minecraft.getInstance().player;
        int option = ScribingTooltip.optionAt(mouseY, screen.getTopPos());
        if (player == null
                || option < 0
                || !Scribing.isScribable(menu.getSlot(0).getItem())) {
            original.call(graphics, font, lines, mouseX, mouseY);
            return;
        }
        original.call(graphics, font, ScribingTooltip.lines(menu, option, player), mouseX, mouseY);
    }
}
