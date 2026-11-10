package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws the Bound Vex with vanilla's vex renderer and the armor layer (Evoking II spec §3.1, Part 3 spec §6). */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class BoundVexRenderers {
    private BoundVexRenderers() {}

    /**
     * Registers the renderer (a mod-bus event). {@link BoundVexRenderer} is vanilla's {@code VexRenderer}, which renders
     * any {@code Vex}, charging texture included (docs/api-notes.md §21), plus the armor layer.
     *
     * @param event the renderer registration event
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BOUND_VEX.get(), BoundVexRenderer::new);
    }
}
