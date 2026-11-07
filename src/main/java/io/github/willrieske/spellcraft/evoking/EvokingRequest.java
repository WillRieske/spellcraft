package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * The client's "I pressed the key for this spell" (spec §4): the mod's one custom packet. A request, never a command:
 * {@link EvokingCaster#request} validates everything.
 *
 * @param spell the spell whose key was pressed
 */
public record EvokingRequest(EvokerSpell spell) implements CustomPacketPayload {
    /** The payload id, {@code spellcraft:evoking_request}. */
    public static final Type<EvokingRequest> TYPE = new Type<>(Spellcraft.id("evoking_request"));

    /** Writes the spell as its enum ordinal. */
    public static final StreamCodec<RegistryFriendlyByteBuf, EvokingRequest> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.<RegistryFriendlyByteBuf, EvokerSpell>enumCodec(EvokerSpell.class),
            EvokingRequest::spell,
            EvokingRequest::new);

    public EvokingRequest {
        Objects.requireNonNull(spell, "spell");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
