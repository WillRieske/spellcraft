package io.github.willrieske.spellcraft.spell;

import com.mojang.serialization.Codec;
import io.github.willrieske.spellcraft.Spellcraft;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
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

    /**
     * The cure mark (Evoking II Parts 4-5 spec §2.3): on a zombie villager a Bound Vex is curing, the game time the
     * cure began. Saved. While it is present the zombie villager is frozen, is an ally of every player and is never
     * prey. Read with {@link #isBeingCured} or {@code hasData}; the default exists only because the builder needs one.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> CURE = ATTACHMENT_TYPES.register(
            "cure",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG.fieldOf("cure"))
                    .build());

    /**
     * The bribed mark (spec §3.2): on a vex that took an emerald and is leaving. Saved. A bribed mob is never prey.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> BRIBED = ATTACHMENT_TYPES.register(
            "bribed",
            () -> AttachmentType.builder(() -> Boolean.TRUE)
                    .serialize(Codec.BOOL.fieldOf("bribed"))
                    .build());

    /**
     * The bribe-tried mark (spec §3.1): on a vex a Bound Vex has already offered an emerald to, whatever the answer.
     * Saved, so a vex is offered once and never again.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> BRIBE_TRIED =
            ATTACHMENT_TYPES.register(
                    "bribe_tried",
                    () -> AttachmentType.builder(() -> Boolean.TRUE)
                            .serialize(Codec.BOOL.fieldOf("bribe_tried"))
                            .build());

    private AllyAttachments() {}

    /**
     * Whether {@code entity} carries the cure mark. Uses {@code hasData}, never {@code getData}, which would create
     * the attachment.
     *
     * @param entity any entity
     * @return true while a Bound Vex's cure holds it
     */
    public static boolean isBeingCured(Entity entity) {
        return entity.hasData(CURE);
    }

    /**
     * Whether {@code entity} carries the bribed mark and is true in it.
     *
     * @param entity any entity
     * @return true for a vex that took the bribe
     */
    public static boolean isBribed(Entity entity) {
        return entity.getExistingData(BRIBED).orElse(false);
    }

    /**
     * Whether a Bound Vex has already offered {@code entity} a bribe.
     *
     * @param entity any entity
     * @return true once the offer was made
     */
    public static boolean wasOfferedBribe(Entity entity) {
        return entity.getExistingData(BRIBE_TRIED).orElse(false);
    }
}
