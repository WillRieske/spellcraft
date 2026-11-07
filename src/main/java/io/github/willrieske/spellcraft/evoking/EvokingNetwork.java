package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers the mod's one payload (spec §4). The handler runs on the server thread (NeoForge's default) and hands
 * the request to {@link EvokingCaster}, which decides whether anything happens.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class EvokingNetwork {
    /** Bumped when the payload's shape changes; a client and server with different versions cannot connect. */
    private static final String VERSION = "1";

    private EvokingNetwork() {}

    /**
     * Registers the request payload.
     *
     * @param event the payload registration event
     */
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToServer(EvokingRequest.TYPE, EvokingRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                EvokingCaster.request(player, payload.spell());
            }
        });
    }
}
