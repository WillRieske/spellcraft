package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.client.model.HumanoidModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Proves the {@code HumanoidModel.ArmPose} enum extension is wired correctly, during {@code ./gradlew runData}.
 *
 * <p>What it proves: {@link EvokingArmPose#PROXY} resolves (so {@code enumextensions.json} points a
 * {@code parameters.field} at it), {@code ArmPose.valueOf("SPELLCRAFT_EVOKING")} finds the same constant, and the
 * constant is two-handed and leaves the offhand pose alone, as the JSON's constructor arguments promise.
 *
 * <p>Why it lives in datagen: {@code runData} is the only automated run that boots the client distribution, so it is
 * the only one that initializes {@code ArmPose}. The GameTest server never loads that class. Without this check a
 * wrong {@code field}, {@code class} or argument list would only fail at the first humanoid render, in
 * {@code ArmPose.<clinit>}, which only a playtest reaches.
 *
 * <p>Functional spec §2.5.
 */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class EvokingArmPoseCheck {
    private static final String CONSTANT = "SPELLCRAFT_EVOKING";

    private EvokingArmPoseCheck() {}

    /**
     * Checks the extended constant and fails the datagen run if anything is off.
     *
     * @param event the mod-bus client datagen event (unused: the event only schedules the check)
     * @throws IllegalStateException if the constant is missing, is not the proxy's value, or has the wrong flags
     */
    @SubscribeEvent
    @SuppressWarnings("UnusedVariable") // the handler needs the event parameter to be subscribed
    public static void onGatherData(GatherDataEvent.Client event) {
        check();
    }

    /** The check itself, apart from the event so the messages stay in one place. */
    static void check() {
        HumanoidModel.ArmPose proxied = EvokingArmPose.PROXY.getValue();
        HumanoidModel.ArmPose named;
        try {
            named = HumanoidModel.ArmPose.valueOf(CONSTANT);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ArmPose has no " + CONSTANT + " constant: check enumextensions.json", e);
        }
        if (named != proxied) {
            throw new IllegalStateException(
                    "ArmPose." + CONSTANT + " is not EvokingArmPose.PROXY's value: check enumextensions.json");
        }
        if (!proxied.isTwoHanded()) {
            throw new IllegalStateException("ArmPose." + CONSTANT + " must be two-handed: check the JSON constructor");
        }
        if (proxied.affectsOffhandPose()) {
            throw new IllegalStateException(
                    "ArmPose." + CONSTANT + " must not affect the offhand pose: check the JSON constructor");
        }
    }
}
