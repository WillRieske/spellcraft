package io.github.willrieske.spellcraft;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.gametest.SpellcraftGameTests;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Common (both-sides) entry point. Registers everything the server needs. */
@Mod(Spellcraft.MODID)
public final class Spellcraft {
    public static final String MODID = "spellcraft";

    public Spellcraft(IEventBus modEventBus, ModContainer modContainer) {
        ModEffects.MOB_EFFECTS.register(modEventBus);
        SpellcraftGameTests.TEST_FUNCTIONS.register(modEventBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
