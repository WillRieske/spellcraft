package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws the Bound Vex with vanilla's vex renderer (Evoking II spec §3.1); Part 3 adds the armor layer. */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class BoundVexRenderers {
    private BoundVexRenderers() {}

    /**
     * Registers the renderer (a mod-bus event). {@code VexRenderer} renders any {@code Vex}, charging texture included
     * (docs/api-notes.md §21).
     *
     * @param event the renderer registration event
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BOUND_VEX.get(), VexRenderer::new);
    }
}
