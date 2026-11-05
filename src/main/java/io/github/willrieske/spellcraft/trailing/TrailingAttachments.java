package io.github.willrieske.spellcraft.trailing;

import io.github.willrieske.spellcraft.Spellcraft;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Per-level data Trailing keeps (docs/api-notes.md §18). Saved with the level on every world save. */
public final class TrailingAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Spellcraft.MODID);

    /**
     * Every trail in the level: owners, flames and countdowns. Mutated in place. Every level reads it each tick, so
     * an empty ledger is not written to disk (the predicate skips it).
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TrailFires>> FIRES = ATTACHMENT_TYPES.register(
            "trailing_fires",
            () -> AttachmentType.builder(() -> new TrailFires())
                    .serialize(TrailFires.CODEC, fires -> !fires.isEmpty())
                    .build());

    private TrailingAttachments() {}
}
