package io.github.willrieske.spellcraft;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Common (both-sides) entry point. Registers everything the server needs. */
@Mod(Spellcraft.MODID)
public final class Spellcraft {
    public static final String MODID = "spellcraft";

    public Spellcraft(IEventBus modEventBus, ModContainer modContainer) {
        // Registrations are added by later tasks.
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
