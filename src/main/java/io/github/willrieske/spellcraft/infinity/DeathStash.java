package io.github.willrieske.spellcraft.infinity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** The items an Infinity player keeps through death, by inventory slot. Saved with the player. */
public record DeathStash(List<Entry> entries) {
    public static final DeathStash EMPTY = new DeathStash(List.of());

    public record Entry(int slot, ItemStack stack) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("slot").forGetter(Entry::slot),
                        ItemStack.CODEC.fieldOf("stack").forGetter(Entry::stack))
                .apply(instance, Entry::new));
    }

    public static final MapCodec<DeathStash> CODEC =
            Entry.CODEC.listOf().fieldOf("entries").xmap(DeathStash::new, DeathStash::entries);

    public DeathStash {
        entries = List.copyOf(entries);
    }
}
