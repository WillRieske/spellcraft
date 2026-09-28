package io.github.willrieske.spellcraft;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.gametest.SpellcraftGameTests;
import io.github.willrieske.spellcraft.spell.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Common (both-sides) entry point. Registers everything the server needs. */
@Mod(Spellcraft.MODID)
public final class Spellcraft {
    public static final String MODID = "spellcraft";

    public Spellcraft(IEventBus modEventBus, ModContainer modContainer) {
        ModEffects.MOB_EFFECTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        SpellcraftGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(Spellcraft::addToCreativeTabs);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            event.accept(ModItems.UPSIDE_DOWN_SCROLL);
        }
    }
}
