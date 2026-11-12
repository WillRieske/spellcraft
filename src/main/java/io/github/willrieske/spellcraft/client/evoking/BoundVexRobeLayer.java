package io.github.willrieske.spellcraft.client.evoking;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Draws the vex model again with the robe texture for a Caster (Parts 6-7 spec §6.3), scaled up about the body. The
 * robe uses {@code RenderTypes.entityCutoutZOffset} so that it draws over the body it hugs without z-fighting. The
 * scale alone moves the 2-pixel body out by only about 0.06 pixel, too little to rule out z-fighting at range, so the
 * z offset does that job and the growth is for the look. The texture is transparent on the head and the wings, so only
 * the body and the arms are dressed. Both are first guesses for Will's eye (playtest checklist, Part 7).
 *
 * <p>Functional spec §12.1.
 */
public final class BoundVexRobeLayer extends RenderLayer<VexRenderState, VexModel> {
    /** The robe texture, 32 x 32 like the vex's. */
    private static final Identifier ROBE = Spellcraft.id("textures/entity/bound_vex_robe.png");

    /** The robe is this much larger than the body, about the body's pivot. */
    private static final float GROWTH = 1.06F;

    /** The body's pivot height in blocks: the root's offset (-2.5 pixels) plus the body's (20), over 16. */
    private static final float BODY_PIVOT = 17.5F / 16.0F;

    /**
     * Creates the layer for one renderer.
     *
     * @param renderer the vex renderer it dresses
     */
    public BoundVexRobeLayer(RenderLayerParent<VexRenderState, VexModel> renderer) {
        super(renderer);
    }

    /** Draws the robe over a Caster's body and arms; nothing for a plain vex or an invisible one. */
    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            VexRenderState state,
            float yRot,
            float xRot) {
        if (!(state instanceof BoundVexRenderState vex) || !vex.caster || state.isInvisible) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, BODY_PIVOT, 0.0F);
        poseStack.scale(GROWTH, GROWTH, GROWTH);
        poseStack.translate(0.0F, -BODY_PIVOT, 0.0F);
        collector.submitModel(
                getParentModel(),
                state,
                poseStack,
                RenderTypes.entityCutoutZOffset(ROBE),
                lightCoords,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                -1,
                null,
                state.outlineColor);
        poseStack.popPose();
    }
}
