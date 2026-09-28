package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Spellcraft.MODID);

    public static final DeferredItem<SpellScrollItem> UPSIDE_DOWN_SCROLL = registerScroll(Spells.UPSIDE_DOWN);

    private ModItems() {}

    private static DeferredItem<SpellScrollItem> registerScroll(Spell spell) {
        return ITEMS.registerItem(
                spell.id().getPath() + "_scroll",
                properties -> new SpellScrollItem(spell, properties),
                () -> new Item.Properties().stacksTo(1).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    }
}
