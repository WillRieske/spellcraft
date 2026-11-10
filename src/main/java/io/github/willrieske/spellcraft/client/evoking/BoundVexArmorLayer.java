package io.github.willrieske.spellcraft.client.evoking;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Draws the helmet, chestplate and leggings of a Bound Vex (Evoking II Part 3 spec §6) with vanilla's
 * {@link EquipmentLayerRenderer} and the humanoid armor models that zombies use. A vex is not a humanoid, so each
 * armor model is drawn at the vex model's own head or body part (the helmet at the head, the chestplate and leggings at
 * the body) and shrunk to fit it: the part's pose (the head's turn, the body's lean) carries the armor with it.
 *
 * <p>The sizes and offsets ({@link #HEAD_FIT}, {@link #CHEST_FIT}, {@link #LEGS_FIT}) are a first guess from the
 * models' geometry, never seen on screen: Will's eye decides them in the playtest (checklist, Part 3).
 */
public class BoundVexArmorLayer extends RenderLayer<VexRenderState, VexModel> {
    /** One model pixel in blocks: {@code ModelPart} poses are in sixteenths of a block. */
    private static final float PIXEL = 1.0F / 16.0F;

    /**
     * How a humanoid armor model is fitted onto a vex part: a scale per axis, then a shift down in model pixels.
     *
     * @param scaleX the scale across the part (left to right)
     * @param scaleY the scale along the part (up to down)
     * @param scaleZ the scale through the part (front to back)
     * @param shiftDownPixels how far the armor hangs lower than the part's pivot, in model pixels
     */
    private record Fit(float scaleX, float scaleY, float scaleZ, float shiftDownPixels) {}

    /**
     * The helmet's fit: first guess for Will's playtest. The vex head cube is 5 x 5 x 5 pixels with its pivot at the
     * bottom middle ({@code VexModel}: {@code addBox(-2.5, -5, -2.5, 5, 5, 5)}); the humanoid head cube is 8 x 8 x 8
     * with its pivot at the bottom middle too ({@code HumanoidModel.createMesh}: {@code addBox(-4, -8, -4, 8, 8, 8)}),
     * grown to 10 x 10 x 10 by the armor's outer deformation of 1. The two pivots line up, so no shift is needed, and
     * a uniform 5/8 = 0.625 scales the 8-pixel cube to the 5-pixel one; the deformation then stands out
     * 0.625 pixel from the vex head on every side.
     */
    private static final Fit HEAD_FIT = new Fit(0.625F, 0.625F, 0.625F, 0.0F);

    /**
     * The chestplate's fit: first guess for Will's playtest. The vex body is 3 wide, about 6 tall (a 4 pixel cube and
     * a 5 pixel one, 0.2 slimmer, from the pivot down) and 2 deep, pivoting at its top middle; the humanoid body cube
     * is 8 x 12 x 4 with its pivot at the top middle too, grown to 10 x 14 x 6 by the outer deformation of 1. Scaling
     * each axis so the grown cube stands about half a pixel clear of the vex body gives 4/10 = 0.4 across, 7/14 = 0.5
     * along and 3/6 = 0.5 through. The pivots line up, so no shift is needed. The sleeves do not follow the vex's
     * arms, so they are hidden; the vex's own arms show.
     */
    private static final Fit CHEST_FIT = new Fit(0.4F, 0.5F, 0.5F, 0.0F);

    /**
     * The leggings' fit: first guess for Will's playtest. The leggings use the humanoid body cube with the inner
     * deformation of 0.5, which grows 8 x 12 x 4 to 9 x 13 x 5, and so sits inside the chestplate when both are worn.
     * The same scales as {@link #CHEST_FIT} keep it clear of the vex body; one pixel of shift down lets its hem show
     * under a chestplate, at the vex's tail. A vex has no legs, so the leg parts are hidden.
     */
    private static final Fit LEGS_FIT = new Fit(0.4F, 0.5F, 0.5F, 1.0F);

    private final ArmorModelSet<HumanoidModel<HumanoidRenderState>> armorModels;
    private final EquipmentLayerRenderer equipmentRenderer;

    /** The state every armor model is set up with: the armor is posed by the vex part, not by a humanoid state. */
    private final HumanoidRenderState neutralState = new HumanoidRenderState();

    /**
     * Builds the layer and bakes the humanoid armor models (the zombie's armor set, as {@code ZombieRenderer} does).
     *
     * @param renderer the vex renderer this layer belongs to
     * @param context the renderer provider's context
     */
    public BoundVexArmorLayer(
            RenderLayerParent<VexRenderState, VexModel> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.armorModels = ArmorModelSet.bake(
                ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), part -> new HumanoidModel<HumanoidRenderState>(part));
        this.equipmentRenderer = context.getEquipmentRenderer();
        // The sleeves and the trouser legs have no vex part to follow.
        armorModels.chest().leftArm.visible = false;
        armorModels.chest().rightArm.visible = false;
        armorModels.legs().leftLeg.visible = false;
        armorModels.legs().rightLeg.visible = false;
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            VexRenderState state,
            float yRot,
            float xRot) {
        if (!(state instanceof BoundVexRenderState gear)) {
            return;
        }
        draw(poseStack, collector, lightCoords, state, EquipmentSlot.CHEST, gear.chestArmor);
        draw(poseStack, collector, lightCoords, state, EquipmentSlot.LEGS, gear.legsArmor);
        draw(poseStack, collector, lightCoords, state, EquipmentSlot.HEAD, gear.headArmor);
    }

    /** Draws one piece at its vex part; nothing for an empty stack or one without an armor model for the slot. */
    private void draw(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            VexRenderState state,
            EquipmentSlot slot,
            ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) {
            return;
        }
        Fit fit = slot == EquipmentSlot.HEAD ? HEAD_FIT : slot == EquipmentSlot.CHEST ? CHEST_FIT : LEGS_FIT;
        poseStack.pushPose();
        getParentModel().root().translateAndRotate(poseStack);
        ModelPart anchor = getParentModel().root().getChild(slot == EquipmentSlot.HEAD ? "head" : "body");
        anchor.translateAndRotate(poseStack);
        poseStack.translate(0.0F, fit.shiftDownPixels() * PIXEL, 0.0F);
        poseStack.scale(fit.scaleX(), fit.scaleY(), fit.scaleZ());
        equipmentRenderer.renderLayers(
                slot == EquipmentSlot.LEGS
                        ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                        : EquipmentClientInfo.LayerType.HUMANOID,
                equippable.assetId().orElseThrow(),
                armorModels.get(slot),
                neutralState,
                stack,
                poseStack,
                collector,
                lightCoords,
                state.outlineColor);
        poseStack.popPose();
    }
}
