package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * What each team move conjures (Parts 6-7 spec section 4): the kit of a member, how to put it back after another item
 * took the hand, and how to take it away. Every kit item is unbreakable.
 */
final class TeamMoveKits {
    /** The six plain Raid main-hand weapons, in dice order. */
    private static final List<Item> RAID_WEAPONS = List.of(
            Items.NETHERITE_SWORD,
            Items.DIAMOND_SWORD,
            Items.NETHERITE_AXE,
            Items.DIAMOND_AXE,
            Items.MACE,
            Items.TRIDENT);

    /** The seventh main-hand roll: a crossbow that fires rockets. */
    private static final int ROCKET_CROSSBOW = RAID_WEAPONS.size();

    private TeamMoveKits() {}

    /**
     * The slots a move conjures into.
     *
     * @param move the move
     * @return the slots, empty for none
     */
    static List<EquipmentSlot> slotsOf(TeamMove move) {
        return switch (move) {
            case SHIELD, EVADER -> List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
            case MULTISHOT -> List.of(EquipmentSlot.MAINHAND);
            case RAID ->
                List.of(
                        EquipmentSlot.MAINHAND,
                        EquipmentSlot.OFFHAND,
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS);
            case NONE -> List.of();
        };
    }

    /**
     * Builds one member's kit. A Raid kit is rolled here (through {@link Dice#RAID_KIT}) and kept, so a member put back
     * after a rescue gets the same items.
     *
     * @param move the move
     * @param level the level, for the enchantment lookup
     * @param random the source for unscripted rolls
     * @return the kit by slot
     */
    static Map<EquipmentSlot, ItemStack> kit(TeamMove move, ServerLevel level, RandomSource random) {
        Map<EquipmentSlot, ItemStack> kit = new EnumMap<>(EquipmentSlot.class);
        switch (move) {
            case SHIELD, EVADER -> {
                kit.put(EquipmentSlot.MAINHAND, unbreakable(new ItemStack(Items.SHIELD)));
                kit.put(EquipmentSlot.OFFHAND, unbreakable(new ItemStack(Items.SHIELD)));
            }
            case MULTISHOT -> kit.put(EquipmentSlot.MAINHAND, multishotCrossbow(level));
            case RAID -> raid(kit, random);
            case NONE -> {
                // no kit
            }
        }
        return kit;
    }

    /** A crossbow enchanted with Multishot I, unbreakable. */
    private static ItemStack multishotCrossbow(ServerLevel level) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.enchant(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MULTISHOT), 1);
        return unbreakable(crossbow);
    }

    /**
     * One of seven main-hand items at even odds, a shield, and head, chest and legs all netherite or all diamond (one
     * roll per member).
     */
    private static void raid(Map<EquipmentSlot, ItemStack> kit, RandomSource random) {
        // Part 7: a Caster rolls only the armor, gets the shield, and holds the Mirror scroll
        int main = Dice.RAID_KIT.below(random, ROCKET_CROSSBOW + 1);
        Item weapon = main < ROCKET_CROSSBOW ? RAID_WEAPONS.get(main) : Items.CROSSBOW;
        kit.put(EquipmentSlot.MAINHAND, unbreakable(new ItemStack(weapon)));
        kit.put(EquipmentSlot.OFFHAND, unbreakable(new ItemStack(Items.SHIELD)));
        boolean netherite = Dice.RAID_KIT.below(random, 2) == 0;
        kit.put(
                EquipmentSlot.HEAD,
                unbreakable(new ItemStack(netherite ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET)));
        kit.put(
                EquipmentSlot.CHEST,
                unbreakable(new ItemStack(netherite ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE)));
        kit.put(
                EquipmentSlot.LEGS,
                unbreakable(new ItemStack(netherite ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS)));
    }

    /** Marks {@code stack} unbreakable and returns it. */
    private static ItemStack unbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }

    /**
     * Conjures every item of {@code kit} into {@code vex} (given gear goes to the stash).
     *
     * @param vex the member
     * @param kit the kit by slot
     */
    static void conjure(BoundVex vex, Map<EquipmentSlot, ItemStack> kit) {
        kit.forEach((slot, stack) -> vex.conjure(slot, stack.copy()));
    }

    /**
     * Puts back what a rescue's bucket or apple, or anything else, took out of a kit slot: a slot that is not
     * conjured, or whose conjured item is another item, is conjured again. A kit item that is still there is left
     * alone, so a crossbow fired this tick is not replaced.
     *
     * @param vex the member
     * @param kit the kit by slot
     */
    static void restore(BoundVex vex, Map<EquipmentSlot, ItemStack> kit) {
        kit.forEach((slot, stack) -> {
            if (!vex.isConjured(slot) || !vex.getItemBySlot(slot).is(stack.getItem())) {
                vex.conjure(slot, stack.copy());
            }
        });
    }

    /**
     * Dismisses the slots {@code move} conjures into: the conjured items go and the stashed gear returns.
     *
     * @param vex the member
     * @param move the move whose kit to take away
     */
    static void dismiss(BoundVex vex, TeamMove move) {
        slotsOf(move).forEach(vex::dismiss);
    }
}
