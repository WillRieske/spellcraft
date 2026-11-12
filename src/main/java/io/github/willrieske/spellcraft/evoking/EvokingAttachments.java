package io.github.willrieske.spellcraft.evoking;

import com.mojang.serialization.Codec;
import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Entity data of Part 7's spells (Parts 6-7 spec §7.3, §8).
 *
 * <p>Functional spec §14.6, §15.2.
 */
public final class EvokingAttachments {
    /** The register of these attachment types; the mod constructor adds it to the mod event bus. */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Spellcraft.MODID);

    /**
     * Present on a carrier while Ghostrail runs; the value is the carrier's own {@code NoGravity} from before. Saved,
     * so a reload mid-effect still ends it properly, and its presence with no effect is how the end is noticed after
     * expiry, milk or {@code /effect clear}. Read with {@code getExistingData}/{@code hasData}. Not the player's
     * {@code InfinityAttachments.GHOSTRAIL_GRANTED}, which records who set the abilities flags. Functional spec §14.6.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GHOSTRAIL_MARK =
            ATTACHMENT_TYPES.register(
                    "ghostrail_mark",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL.fieldOf("no_gravity"))
                            .build());

    /**
     * The standable spot Ghostrail began at; not saved. The last place to send a carrier that ends deep in stone.
     * Functional spec §14.6.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BlockPos>> GHOSTRAIL_START =
            ATTACHMENT_TYPES.register(
                    "ghostrail_start",
                    () -> AttachmentType.builder(() -> BlockPos.ZERO).build());

    /** On a scroll mob: the game time its Mirror cooldown ends. Saved. Functional spec §15.2. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> MOB_MIRROR_READY =
            ATTACHMENT_TYPES.register(
                    "mob_mirror_ready",
                    () -> AttachmentType.builder(() -> 0L)
                            .serialize(Codec.LONG.fieldOf("ready"))
                            .build());

    /** On a scroll mob: the game time its Ghostrail cooldown ends. Saved. Functional spec §15.2. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> MOB_GHOSTRAIL_READY =
            ATTACHMENT_TYPES.register(
                    "mob_ghostrail_ready",
                    () -> AttachmentType.builder(() -> 0L)
                            .serialize(Codec.LONG.fieldOf("ready"))
                            .build());

    private EvokingAttachments() {}
}
