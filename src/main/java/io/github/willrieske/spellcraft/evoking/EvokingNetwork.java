package io.github.willrieske.spellcraft.evoking;

import com.mojang.logging.LogUtils;
import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Registers the mod's one payload (spec §4). The handler runs on the server thread (NeoForge's default) and hands
 * the request to {@link #handle}, which validates it and dispatches a cast to {@link EvokingCaster} and a team move
 * (the J and K keys) to {@link TeamMoves}; those decide whether anything happens.
 *
 * <p>Functional spec §2.6.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class EvokingNetwork {
    /**
     * Bumped when the payload's shape changes; a client and server with different versions cannot connect. Bumped to 2
     * when the payload gained a kind.
     */
    private static final String VERSION = "2";

    private static final Logger LOGGER = LogUtils.getLogger();

    private EvokingNetwork() {}

    /**
     * Registers the request payload; its handler passes both kinds (cast, team move) to {@link #handle}.
     *
     * @param event the payload registration event
     */
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToServer(EvokingRequest.TYPE, EvokingRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                handle(player, payload);
            }
        });
    }

    /**
     * Validates a request in full and hands it on (Part 6 spec §3.2): an argument out of range for its kind is ignored
     * (logged at debug); a {@code CAST} goes to {@link EvokingCaster#request} exactly as before, a {@code TEAM_MOVE} to
     * {@link TeamMoves#request}. Public so GameTests can drive it without a connection.
     *
     * @param player the sender
     * @param request the decoded request
     */
    public static void handle(ServerPlayer player, EvokingRequest request) {
        int arg = request.arg();
        switch (request.kind()) {
            case CAST -> {
                EvokerSpell[] spells = EvokerSpell.values();
                if (arg < 0 || arg >= spells.length) {
                    LOGGER.debug(
                            "Ignoring a cast request with argument {} from {}",
                            arg,
                            player.getName().getString());
                    return;
                }
                EvokingCaster.request(player, spells[arg]);
            }
            case TEAM_MOVE ->
                TeamMove.fromArg(arg)
                        .ifPresentOrElse(
                                move -> TeamMoves.request(player, move),
                                () -> LOGGER.debug(
                                        "Ignoring a team-move request with argument {} from {}",
                                        arg,
                                        player.getName().getString()));
        }
    }
}
