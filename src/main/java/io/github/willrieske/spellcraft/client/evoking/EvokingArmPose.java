package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.EvokerPose;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * The evoker's raised arms on a casting player (spec §7). {@code META-INF/enumextensions.json} adds the
 * {@code SPELLCRAFT_EVOKING} constant to {@code HumanoidModel.ArmPose} with {@link #PROXY}'s parameters (two-handed,
 * does not affect the offhand pose, this transformer); a render-state modifier sets both arms to it on every player
 * {@link CastingState} says is casting, which covers third person, other players and the inventory doll.
 */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class EvokingArmPose {
    /**
     * Named by {@code enumextensions.json}. {@code getValue()} forces the enum class to load (which applies the
     * extension) and throws a NullPointerException ("Enum not initialized ...") only if the JSON did not point a
     * {@code parameters.field} at this proxy (FML {@code EnumProxy.java:38-47}).
     */
    public static final EnumProxy<HumanoidModel.ArmPose> PROXY =
            new EnumProxy<>(HumanoidModel.ArmPose.class, true, false, (IArmPoseTransformer) EvokingArmPose::raise);

    private EvokingArmPose() {}

    /** The illager model's spellcasting pose for one arm. */
    static void raise(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart part = right ? model.rightArm : model.leftArm;
        EvokerPose.ArmRotation rotation = EvokerPose.armRotation(state.ageInTicks, right);
        part.xRot = rotation.xRot();
        part.yRot = rotation.yRot();
        part.zRot = rotation.zRot();
    }

    @SubscribeEvent
    public static void onRegisterModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                if (CastingState.of(avatar).isPresent()) {
                    HumanoidModel.ArmPose pose = PROXY.getValue();
                    state.rightArmPose = pose;
                    state.leftArmPose = pose;
                }
            }
        });
    }
}
