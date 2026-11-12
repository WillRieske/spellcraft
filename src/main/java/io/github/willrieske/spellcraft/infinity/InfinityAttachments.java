package io.github.willrieske.spellcraft.infinity;

import com.mojang.serialization.Codec;
import io.github.willrieske.spellcraft.Spellcraft;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Per-player data Infinity keeps (docs/api-notes.md §13). Saved with the player; not copied on death.
 *
 * <p>Functional spec §14.7.
 */
public final class InfinityAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Spellcraft.MODID);

    /**
     * True while Infinity (not vanilla or another mod) set the player's {@code mayfly}/{@code instabuild}. Not
     * copied on death: {@code restoreFrom} resets abilities from the game mode anyway.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GRANTED = ATTACHMENT_TYPES.register(
            "infinity_granted",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("granted"))
                    .build());

    /**
     * True while Ghostrail (not vanilla, Infinity or another mod) set the player's {@code mayfly} and {@code flying}.
     * Saved, so a relog mid-effect does not leave {@code mayfly} on for good. Not copied on death, as {@link #GRANTED}.
     * Not the same as {@code EvokingAttachments.GHOSTRAIL_MARK}: that one records the carrier's original
     * {@code noGravity}, for every kind of carrier; this one records only who set the abilities flags, for players.
     * Functional spec §14.7.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GHOSTRAIL_GRANTED =
            ATTACHMENT_TYPES.register(
                    "ghostrail_granted",
                    () -> AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL.fieldOf("granted"))
                            .build());

    /** The death stash (spec §4). Read from the dead player on clone, so it needs no copy-on-death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DeathStash>> STASH = ATTACHMENT_TYPES.register(
            "infinity_death_stash",
            () -> AttachmentType.builder(() -> DeathStash.EMPTY)
                    .serialize(DeathStash.CODEC)
                    .build());

    private InfinityAttachments() {}
}
