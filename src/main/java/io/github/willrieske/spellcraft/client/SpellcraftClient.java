package io.github.willrieske.spellcraft.client;

import io.github.willrieske.spellcraft.Spellcraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point. Never loaded on a dedicated server. */
@Mod(value = Spellcraft.MODID, dist = Dist.CLIENT)
public final class SpellcraftClient {
    public SpellcraftClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
