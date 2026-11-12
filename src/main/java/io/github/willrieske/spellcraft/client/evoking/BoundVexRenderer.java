package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's vex renderer plus the armor layer (Evoking II Part 3 spec §6): a Bound Vex wears the helmet, chestplate
 * and leggings it was given. A Caster also wears the robe layer and raises its arms while casting ({@link
 * BoundVexModel}; Parts 6-7 spec §6.3-§6.4). Its charging texture, held items and everything else are vanilla's.
 *
 * <p>Functional spec §8.11, §12.1, §12.5.
 */
public final class BoundVexRenderer extends VexRenderer {
    /**
     * Builds the renderer with the casting-pose model and adds the robe and armor layers.
     *
     * @param context the renderer provider's context
     */
    public BoundVexRenderer(EntityRendererProvider.Context context) {
        super(context);
        // VexRenderer's constructor built a vanilla model first; this one replaces it.
        this.model = new BoundVexModel(context.bakeLayer(ModelLayers.VEX));
        addLayer(new BoundVexRobeLayer(this));
        addLayer(new BoundVexArmorLayer(this, context));
    }

    @Override
    public VexRenderState createRenderState() {
        return new BoundVexRenderState();
    }

    /**
     * Vanilla's extraction, then the Caster flags and the three armor pieces (copies, as vanilla's humanoid extraction
     * makes them).
     */
    @Override
    public void extractRenderState(Vex entity, VexRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // Always true: createRenderState above makes every state this renderer extracts into. The pattern keeps the
        // cast safe without a branch that does anything else.
        if (state instanceof BoundVexRenderState gear) {
            gear.caster = entity instanceof BoundVex vex && vex.isCaster();
            gear.casting = CastingState.of(entity).isPresent();
            gear.headArmor = renderable(entity, EquipmentSlot.HEAD);
            gear.chestArmor = renderable(entity, EquipmentSlot.CHEST);
            gear.legsArmor = renderable(entity, EquipmentSlot.LEGS);
        }
    }

    /** The piece worn in {@code slot} when it has an armor model for that slot, else empty (as vanilla's humanoids). */
    private static ItemStack renderable(LivingEntity entity, EquipmentSlot slot) {
        ItemStack stack = entity.getItemBySlot(slot);
        return HumanoidArmorLayer.shouldRender(stack, slot) ? stack.copy() : ItemStack.EMPTY;
    }
}
