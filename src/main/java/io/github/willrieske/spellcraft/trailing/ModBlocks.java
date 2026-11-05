package io.github.willrieske.spellcraft.trailing;

import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The blocks Spellcraft registers for Trailing; registered on the mod event bus by {@code Spellcraft}. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Spellcraft.MODID);

    private static final int LIGHT = 10;

    /** Trail fire: soul fire's properties (docs/api-notes.md §18) plus random ticks for orphan cleanup. No item. */
    public static final DeferredBlock<TrailFireBlock> TRAIL_FIRE = BLOCKS.registerBlock(
            "trail_fire",
            TrailFireBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .replaceable()
                    .noCollision()
                    .instabreak()
                    .lightLevel(state -> LIGHT)
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.POPPED)
                    .randomTicks()
                    .noLootTable());

    private ModBlocks() {}
}
