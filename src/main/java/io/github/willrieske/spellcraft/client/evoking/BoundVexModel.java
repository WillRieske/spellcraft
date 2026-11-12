package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.spell.rules.EvokerPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.renderer.entity.state.VexRenderState;

/** The vex model with the arms-up casting pose of a Caster (Parts 6-7 spec §6.4). */
public final class BoundVexModel extends VexModel {
    /**
     * Creates the model from its baked layer.
     *
     * @param root the baked {@code ModelLayers.VEX} root
     */
    public BoundVexModel(ModelPart root) {
        super(root);
    }

    /** Vanilla's pose, then, while casting, both arms raised as {@link EvokerPose#armRotation} says. */
    @Override
    public void setupAnim(VexRenderState state) {
        super.setupAnim(state);
        if (state instanceof BoundVexRenderState vex && vex.casting) {
            ModelPart body = root().getChild("body");
            raise(body.getChild("right_arm"), EvokerPose.armRotation(state.ageInTicks, true));
            raise(body.getChild("left_arm"), EvokerPose.armRotation(state.ageInTicks, false));
        }
    }

    /** Sets all three rotations of {@code arm} from {@code rotation}. */
    private static void raise(ModelPart arm, EvokerPose.ArmRotation rotation) {
        arm.xRot = rotation.xRot();
        arm.yRot = rotation.yRot();
        arm.zRot = rotation.zRot();
    }
}
