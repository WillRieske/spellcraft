package io.github.willrieske.spellcraft.infinity;

import com.mojang.serialization.Codec;
import io.github.willrieske.spellcraft.Spellcraft;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Per-player data Infinity keeps (docs/api-notes.md §13). Saved with the player; not copied on death. */
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

    private InfinityAttachments() {}
}
