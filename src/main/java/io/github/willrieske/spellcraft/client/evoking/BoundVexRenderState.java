package io.github.willrieske.spellcraft.client.evoking;

import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.world.item.ItemStack;

/**
 * What the renderer of a Bound Vex needs to draw its armor (Evoking II Part 3 spec §6): the three armor pieces it
 * wears, copied from the entity on the render thread's extraction step. The main-hand and off-hand items already ride
 * {@code ArmedEntityRenderState} in the vanilla vex state, so only armor is added here.
 */
public class BoundVexRenderState extends VexRenderState {
    /** The helmet, or {@link ItemStack#EMPTY} when none is worn or it has no armor model. */
    public ItemStack headArmor = ItemStack.EMPTY;

    /** The chestplate, or {@link ItemStack#EMPTY} when none is worn or it has no armor model. */
    public ItemStack chestArmor = ItemStack.EMPTY;

    /** The leggings, or {@link ItemStack#EMPTY} when none is worn or it has no armor model. */
    public ItemStack legsArmor = ItemStack.EMPTY;

    /** Whether the vex is a Caster: the robe layer draws the robe. */
    public boolean caster;

    /** Whether the vex is casting (the casting effect is on): {@code BoundVexModel} raises both arms. */
    public boolean casting;
}
