package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.evoking.VillagerGhost;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Draws a villager ghost (Parts 6-7 spec §9): the villager model with the base villager texture, translucent and tinted
 * pale blue (alpha 0.6), plus a copy of vanilla's profession layer that draws the type, profession and level textures
 * with the same tint. Vanilla's {@code VillagerRenderer} needs a {@code Villager} and the profession layer's helpers
 * are private and draw with {@code entityCutout}, which ignores alpha; hence the renderer and the layer are this mod's
 * own. The villager model's arms are one crossed part, so a ghost shows particles and sounds only when it casts.
 */
public final class VillagerGhostRenderer
        extends LivingEntityRenderer<VillagerGhost, VillagerRenderState, VillagerModel> {
    /** The base villager texture. */
    private static final Identifier BASE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

    /** Pale blue at alpha 0.6, as ARGB (a first guess for Will's eye). */
    static final int TINT = 0x99DDE6FF;

    /**
     * Creates the renderer: the villager model and the profession layer's no-hat model.
     *
     * @param context the renderer context
     */
    public VillagerGhostRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        addLayer(new VillagerGhostProfessionLayer(
                this, context.getResourceManager(), new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT))));
    }

    /** The base villager texture, whatever the state. */
    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return BASE;
    }

    /** Makes an empty render state. */
    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    /** Copies the ghost's villager data into the state; a ghost is never unhappy. */
    @Override
    public void extractRenderState(VillagerGhost ghost, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(ghost, state, partialTicks);
        state.villagerData = ghost.villagerData();
        state.isUnhappy = false;
    }

    /** Translucent, so the tint's alpha shows (vanilla's cutout type would ignore it). */
    @Override
    protected @Nullable RenderType getRenderType(
            VillagerRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        return isBodyVisible ? RenderTypes.entityTranslucent(getTextureLocation(state)) : null;
    }

    /** The ghost's pale blue tint. */
    @Override
    protected int getModelTint(VillagerRenderState state) {
        return TINT;
    }
}
