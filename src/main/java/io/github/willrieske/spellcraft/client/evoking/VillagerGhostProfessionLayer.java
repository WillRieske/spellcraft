package io.github.willrieske.spellcraft.client.evoking;

import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.VillagerMetadataSection;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;

/** Draws the type, profession and level textures of a ghost translucent and tinted (vanilla's layer draws cutout). */
public final class VillagerGhostProfessionLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    /** The level badge names, by villager level. */
    private static final Map<Integer, Identifier> LEVEL_LOCATIONS = Map.of(
            1, Identifier.withDefaultNamespace("stone"),
            2, Identifier.withDefaultNamespace("iron"),
            3, Identifier.withDefaultNamespace("gold"),
            4, Identifier.withDefaultNamespace("emerald"),
            5, Identifier.withDefaultNamespace("diamond"));

    /** The hat metadata of the type textures read so far. */
    private final Map<ResourceKey<VillagerType>, VillagerMetadataSection.Hat> typeHats = new HashMap<>();

    /** The hat metadata of the profession textures read so far. */
    private final Map<ResourceKey<VillagerProfession>, VillagerMetadataSection.Hat> professionHats = new HashMap<>();

    /** Where the texture metadata is read from. */
    private final ResourceManager resourceManager;

    /** The model drawn for the type texture when a profession's hat covers the type's. */
    private final VillagerModel noHatModel;

    /**
     * Creates the layer for one renderer.
     *
     * @param renderer the ghost renderer it dresses
     * @param resourceManager where the hat metadata comes from
     * @param noHatModel the baked no-hat villager model
     */
    public VillagerGhostProfessionLayer(
            RenderLayerParent<VillagerRenderState, VillagerModel> renderer,
            ResourceManager resourceManager,
            VillagerModel noHatModel) {
        super(renderer);
        this.resourceManager = resourceManager;
        this.noHatModel = noHatModel;
    }

    /** Draws the type, then the profession, then the level badge, each translucent and tinted. */
    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            VillagerRenderState state,
            float yRot,
            float xRot) {
        VillagerData data = state.getVillagerData();
        if (state.isInvisible || data == null) {
            return;
        }
        Holder<VillagerType> type = data.type();
        Holder<VillagerProfession> profession = data.profession();
        VillagerMetadataSection.Hat typeHat = hat(typeHats, "type", type);
        VillagerMetadataSection.Hat professionHat = hat(professionHats, "profession", profession);
        VillagerModel model = getParentModel();
        boolean typeHatVisible = professionHat == VillagerMetadataSection.Hat.NONE
                || (professionHat == VillagerMetadataSection.Hat.PARTIAL
                        && typeHat != VillagerMetadataSection.Hat.FULL);
        draw(
                typeHatVisible ? model : noHatModel,
                identifier("type", type),
                poseStack,
                collector,
                lightCoords,
                state,
                1);
        if (!profession.is(VillagerProfession.NONE)) {
            draw(model, identifier("profession", profession), poseStack, collector, lightCoords, state, 2);
            if (!profession.is(VillagerProfession.NITWIT)) {
                Identifier level = LEVEL_LOCATIONS.get(Mth.clamp(data.level(), 1, LEVEL_LOCATIONS.size()));
                draw(model, identifier("profession_level", level), poseStack, collector, lightCoords, state, 3);
            }
        }
    }

    /** One texture, translucent, in the ghost's tint, in the draw order {@code order}. */
    private static void draw(
            VillagerModel model,
            Identifier texture,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            VillagerRenderState state,
            int order) {
        collector
                .order(order)
                .submitModel(
                        model,
                        state,
                        poseStack,
                        RenderTypes.entityTranslucent(texture),
                        lightCoords,
                        LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                        VillagerGhostRenderer.TINT,
                        null,
                        state.outlineColor);
    }

    /** The texture path of a key: {@code textures/entity/villager/<kind>/<path>.png}. */
    private static Identifier identifier(String kind, Identifier key) {
        return key.withPath(path -> "textures/entity/villager/" + kind + "/" + path + ".png");
    }

    /** The texture path of a registry holder, or the missing-texture location for an unbound holder. */
    private static Identifier identifier(String kind, Holder<?> holder) {
        return holder.unwrapKey()
                .map(key -> identifier(kind, key.identifier()))
                .orElse(MissingTextureAtlasSprite.getLocation());
    }

    /** The hat metadata of a type or profession texture ({@code NONE} when it has none), read once and cached. */
    private <K> VillagerMetadataSection.Hat hat(
            Map<ResourceKey<K>, VillagerMetadataSection.Hat> cache, String kind, Holder<K> holder) {
        ResourceKey<K> key = holder.unwrapKey().orElse(null);
        if (key == null) {
            return VillagerMetadataSection.Hat.NONE;
        }
        return cache.computeIfAbsent(
                key,
                k -> resourceManager
                        .getResource(identifier(kind, key.identifier()))
                        .flatMap(resource -> {
                            try {
                                return resource.metadata()
                                        .getSection(VillagerMetadataSection.TYPE)
                                        .map(VillagerMetadataSection::hat);
                            } catch (IOException e) {
                                return Optional.empty();
                            }
                        })
                        .orElse(VillagerMetadataSection.Hat.NONE));
    }
}
