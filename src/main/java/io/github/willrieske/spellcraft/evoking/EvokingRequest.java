package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * The client's "I pressed this key" (spec §4; Part 6 spec §3.2): the mod's one custom packet. A request, never a
 * command: {@link EvokingNetwork#handle} validates everything. Two kinds: {@link Kind#CAST} (the argument is the
 * {@link EvokerSpell} ordinal) and {@link Kind#TEAM_MOVE} (the {@link TeamMove} ordinal, Shield 0 to Raid 3).
 *
 * <p>Functional spec §2.6.
 *
 * @param kind what the key asks for
 * @param arg the ordinal of the spell or the move
 */
public record EvokingRequest(Kind kind, byte arg) implements CustomPacketPayload {
    /** What a request asks for. */
    public enum Kind {
        /** Cast an Evoking spell. */
        CAST,
        /** Start a team move by hand. */
        TEAM_MOVE
    }

    /** The payload id, {@code spellcraft:evoking_request}. */
    public static final Type<EvokingRequest> TYPE = new Type<>(Spellcraft.id("evoking_request"));

    /** Writes the kind as an enum and the argument as a byte. */
    public static final StreamCodec<RegistryFriendlyByteBuf, EvokingRequest> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.<RegistryFriendlyByteBuf, Kind>enumCodec(Kind.class),
            EvokingRequest::kind,
            ByteBufCodecs.BYTE,
            EvokingRequest::arg,
            EvokingRequest::new);

    /** Rejects a null kind, so a decoded or constructed request always names one. */
    public EvokingRequest {
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * A request to cast {@code spell}.
     *
     * @param spell the spell whose key was pressed
     * @return the request
     */
    @SuppressWarnings(
            "EnumOrdinal") // the wire format is the spell's declaration index; the server reads it back by index
    public static EvokingRequest cast(EvokerSpell spell) {
        return new EvokingRequest(Kind.CAST, (byte) spell.ordinal());
    }

    /**
     * A request to start {@code move}.
     *
     * @param move the move whose key was pressed
     * @return the request
     */
    public static EvokingRequest teamMove(TeamMove move) {
        return new EvokingRequest(Kind.TEAM_MOVE, move.arg());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
