package io.github.willrieske.spellcraft.client.evoking;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * The render state of a Mirror image: the stand-in's own render state, drawn by the dispatcher (Parts 6-7 spec §7.2).
 */
public final class MirrorImageRenderState extends EntityRenderState {
    /** The state of the stand-in for this frame, or null when no stand-in could be made. */
    public @Nullable EntityRenderState standIn;
}
