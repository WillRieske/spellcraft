package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Entity data the ally rule reads (spec §3). Saved with the entity; never copied on death. */
public final class AllyAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Spellcraft.MODID);

    /**
     * The player who summoned this mob (Evoking puts it on every vex it calls). Read with {@code getExistingData}
     * or {@code hasData}; the default exists only because the builder needs one.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<UUID>> SUMMONER = ATTACHMENT_TYPES.register(
            "summoner",
            () -> AttachmentType.builder(() -> Util.NIL_UUID)
                    .serialize(UUIDUtil.CODEC.fieldOf("summoner"))
                    .build());

    private AllyAttachments() {}
}
