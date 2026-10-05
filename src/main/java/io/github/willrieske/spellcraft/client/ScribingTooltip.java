package io.github.willrieske.spellcraft.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.EnchantmentMenu;

/** The enchanting-table tooltip for a scribing option (spec §5). Vanilla's cost lines, our first two lines. */
public final class ScribingTooltip {
    /**
     * Vanilla's hover test is {@code isHovering(60, 14 + 19 * i, 108, 17, …)}, which pads each 17 px box by 1 px, so
     * row {@code i} covers {@code [13 + 19 * i, 32 + 19 * i)} below the screen top: three contiguous 19 px bands.
     */
    private static final int FIRST_BAND = 13;

    private static final int ROW_PITCH = 19;
    private static final int OPTIONS = 3;

    private ScribingTooltip() {}

    /** Which option the mouse is over, or -1. Agrees with vanilla's hover test on every pixel (no edge flicker). */
    public static int optionAt(int mouseY, int topPos) {
        int y = mouseY - topPos - FIRST_BAND;
        if (y < 0) {
            return -1;
        }
        int option = y / ROW_PITCH;
        return option < OPTIONS ? option : -1;
    }

    /** The tooltip lines for scribing option {@code option}: title, odds, then the cost (or the level requirement). */
    public static List<Component> lines(EnchantmentMenu menu, int option, LocalPlayer player) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("container.spellcraft.scribe").withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("container.spellcraft.scribe.chance", menu.levelClue[option])
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (player.hasInfiniteMaterials()) {
            return lines;
        }
        lines.add(CommonComponents.EMPTY);
        int cost = option + 1;
        if (player.experienceLevel < menu.costs[option]) {
            lines.add(Component.translatable("container.enchant.level.requirement", menu.costs[option])
                    .withStyle(ChatFormatting.RED));
            return lines;
        }
        Component lapis = cost == 1
                ? Component.translatable("container.enchant.lapis.one")
                : Component.translatable("container.enchant.lapis.many", cost);
        lines.add(lapis.copy().withStyle(menu.getGoldCount() >= cost ? ChatFormatting.GRAY : ChatFormatting.RED));
        Component levels = cost == 1
                ? Component.translatable("container.enchant.level.one")
                : Component.translatable("container.enchant.level.many", cost);
        lines.add(levels.copy().withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
