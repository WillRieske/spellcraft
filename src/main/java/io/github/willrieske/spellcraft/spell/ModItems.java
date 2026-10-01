package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.Spellcraft;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Spellcraft.MODID);

    private static final int SCROLL_STACK_SIZE = 16;

    /** Scroll per spell id, in declaration order. Filled by {@link #registerScroll}; must precede the fields below. */
    private static final Map<Identifier, DeferredItem<SpellScrollItem>> SCROLLS = new LinkedHashMap<>();

    public static final DeferredItem<SpellScrollItem> UPSIDE_DOWN_SCROLL = registerScroll(Spells.UPSIDE_DOWN);

    private ModItems() {}

    /** Every scroll, in declaration order. */
    public static Collection<DeferredItem<SpellScrollItem>> scrolls() {
        return Collections.unmodifiableCollection(SCROLLS.values());
    }

    public static ItemStack scrollFor(Spell spell) {
        DeferredItem<SpellScrollItem> scroll = SCROLLS.get(spell.id());
        if (scroll == null) {
            throw new IllegalArgumentException("no scroll registered for " + spell.id());
        }
        return new ItemStack(scroll.get());
    }

    private static DeferredItem<SpellScrollItem> registerScroll(Spell spell) {
        DeferredItem<SpellScrollItem> scroll = ITEMS.registerItem(
                spell.id().getPath() + "_scroll",
                properties -> new SpellScrollItem(spell, properties),
                () -> new Item.Properties()
                        .stacksTo(SCROLL_STACK_SIZE)
                        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
        SCROLLS.put(spell.id(), scroll);
        return scroll;
    }
}
