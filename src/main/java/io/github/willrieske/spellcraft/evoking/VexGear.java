package io.github.willrieske.spellcraft.evoking;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

/**
 * What a held item is to a Bound Vex (Evoking II Part 3 spec §4.1): armor for the head, chest or legs, a shield for the
 * off hand, a launcher, a throwable or an arrow, or a melee weapon for the main hand, or nothing a vex can use. It
 * reads only the stack's item and components, so the client and the server classify alike (spec §3): the client
 * answers a click on a vex from this class alone. The vanilla facts (the equippable and attribute components, the
 * potion contents) are in docs/api-notes.md §22.
 */
public final class VexGear {
    /** The kinds of item a vex can be given (Part 3 spec §4.1). */
    public enum Kind {
        /** Worn on the head, chest or legs. */
        ARMOR,
        /** Held in the off hand; blocks hits (spec §6). */
        SHIELD,
        /** A bow or a crossbow. */
        LAUNCHER,
        /** A splash or lingering potion. */
        THROWABLE,
        /** An arrow, plain or tipped. */
        ARROW,
        /** Anything in the main hand with attack damage: a sword, an axe, a trident, a mace. */
        MELEE,
        /** Nothing a vex can use. */
        NONE
    }

    private VexGear() {}

    /**
     * Classifies a stack (Part 3 spec §4.1). The tests run in the spec's order: armor slots first, then the shield (a
     * vanilla shield also carries an off-hand {@code EQUIPPABLE}), the launchers, throwables and arrows, then "other
     * equippable" (boots, body armor, saddles: wearable, but not by a vex), and last the melee weapons. A splash or
     * lingering potion with no effects (water, awkward) is {@link Kind#NONE}, and so is an arrow tipped with at least
     * one effect when every effect is beneficial (spec §4.1: support gear is potions only).
     *
     * @param stack the item
     * @return its kind, {@link Kind#NONE} for an empty stack
     */
    public static Kind kindOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Kind.NONE;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            EquipmentSlot slot = equippable.slot();
            if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS) {
                return Kind.ARMOR;
            }
        }
        if (stack.has(DataComponents.BLOCKS_ATTACKS)) {
            return Kind.SHIELD;
        }
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
            return Kind.LAUNCHER;
        }
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            return effectCount(stack) > 0 ? Kind.THROWABLE : Kind.NONE;
        }
        if (stack.getItem() instanceof ArrowItem) {
            // An arrow wounds whoever it hits, so one tipped with only good effects cannot heal a friend: not gear.
            return effectCount(stack) > 0 && allBeneficial(stack) ? Kind.NONE : Kind.ARROW;
        }
        if (equippable != null) {
            return Kind.NONE;
        }
        return hasMainHandAttackDamage(stack) ? Kind.MELEE : Kind.NONE;
    }

    /**
     * The slot a vex holds {@code stack} in (Part 3 spec §4.1).
     *
     * @param stack the item
     * @return the slot, or empty for an item a vex cannot use
     */
    public static Optional<EquipmentSlot> slotFor(ItemStack stack) {
        return switch (kindOf(stack)) {
            case ARMOR ->
                Optional.ofNullable(stack.get(DataComponents.EQUIPPABLE)).map(Equippable::slot);
            case SHIELD -> Optional.of(EquipmentSlot.OFFHAND);
            case LAUNCHER, THROWABLE, ARROW, MELEE -> Optional.of(EquipmentSlot.MAINHAND);
            case NONE -> Optional.empty();
        };
    }

    /**
     * Whether {@code stack} is support gear: a throwable (so one with at least one effect) whose effects are all
     * beneficial (Part 3 spec §4.1, §4.3). Only potions are support gear: a beneficial tipped arrow is not gear at all.
     *
     * @param stack the item
     * @return true for support gear
     */
    public static boolean isSupport(ItemStack stack) {
        return kindOf(stack) == Kind.THROWABLE && allBeneficial(stack);
    }

    /**
     * Whether {@code stack} is ranged gear: a launcher, or a throwable or arrow that is not support gear (Part 3 spec
     * §4.3). A harming or poison tipped arrow and a plain or spectral arrow are ranged.
     *
     * @param stack the item
     * @return true for ranged gear
     */
    public static boolean isRanged(ItemStack stack) {
        Kind kind = kindOf(stack);
        return kind == Kind.LAUNCHER || ((kind == Kind.THROWABLE || kind == Kind.ARROW) && !isSupport(stack));
    }

    /** How many effects the stack's potion contents carry (its potion's and its custom ones). */
    private static int effectCount(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        int count = 0;
        if (contents != null) {
            for (MobEffectInstance ignored : contents.getAllEffects()) {
                count++;
            }
        }
        return count;
    }

    /** Whether every effect of the stack's potion contents is beneficial (true when it has none). */
    private static boolean allBeneficial(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return true;
        }
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the stack's attribute modifiers hold an attack-damage entry that applies in the main hand
     * (docs/api-notes.md §22: {@code ItemAttributeModifiers.Entry}, its {@code slot().test(EquipmentSlot)}).
     */
    private static boolean hasMainHandAttackDamage(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return false;
        }
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                    && entry.slot().test(EquipmentSlot.MAINHAND)) {
                return true;
            }
        }
        return false;
    }
}
