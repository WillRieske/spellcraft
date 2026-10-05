package io.github.willrieske.spellcraft.infinity;

import com.mojang.logging.LogUtils;
import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.rules.DeathRule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * Infinity's death rule (spec §4): on death, stash what {@link DeathRule} keeps and empty the inventory, so vanilla
 * drops nothing; on respawn, put the stash back in the same slots. XP is untouched (vanilla).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class InfinityDeath {
    private static final Logger LOGGER = LogUtils.getLogger();

    private InfinityDeath() {}

    /** Lowest priority and not for cancelled events: every mod that might cancel the death has already run. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasEffect(ModEffects.INFINITY)) {
            stash(player, player.level().getGameRules().get(GameRules.KEEP_INVENTORY));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            restore(event.getEntity(), event.getOriginal());
        }
    }

    static void stash(Player player, boolean keepInventory) {
        Inventory inventory = player.getInventory();
        List<DeathRule.Slot> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            boolean vanishing = !stack.isEmpty()
                    && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
            slots.add(new DeathRule.Slot(i, kindOf(i), stack.isEmpty(), vanishing));
        }
        if (keepInventory) {
            return; // vanilla keeps everything; DeathRule sorts nothing
        }
        DeathRule.Sorted sorted = DeathRule.sort(slots, false);
        List<DeathStash.Entry> entries = sorted.stash().stream()
                .map(slot -> new DeathStash.Entry(slot, inventory.getItem(slot).copy()))
                .toList();
        player.setData(InfinityAttachments.STASH, new DeathStash(entries));
        // Stashed items are now held by the stash; deleted items (armor, Vanishing) simply go.
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, ItemStack.EMPTY);
        }
    }

    /**
     * Puts {@code original}'s death stash back into {@code target}'s inventory, then clears it. Never throws for a
     * corrupt or out-of-range entry: logs a warning and discards it, so a bad stash can't crash the respawn. Works in
     * two passes: first every entry whose slot is free goes back in place; then each entry whose slot another mod's
     * Clone handler already filled is added to the first free space, or dropped at the player when the inventory
     * is full. A creative target ({@code hasInfiniteMaterials}) always drops such leftovers, because
     * {@link Inventory#add} would delete them.
     */
    public static void restore(Player target, Player original) {
        DeathStash stash = original.getData(InfinityAttachments.STASH);
        if (stash.entries().isEmpty()) {
            return;
        }
        Inventory inventory = target.getInventory();
        // Pass 1 fills every free target slot; pass 2 relocates the leftovers. Relocating inside pass 1 could take a
        // free slot that a later entry owns and push that entry out of place too.
        List<ItemStack> leftovers = new ArrayList<>();
        for (DeathStash.Entry entry : stash.entries()) {
            if (entry.slot() < 0 || entry.slot() >= inventory.getContainerSize()) {
                LOGGER.warn(
                        "Discarding stashed {} for {}: slot {} is out of range",
                        entry.stack(),
                        target.getPlainTextName(),
                        entry.slot());
                continue;
            }
            ItemStack stack = entry.stack().copy();
            if (inventory.getItem(entry.slot()).isEmpty()) {
                inventory.setItem(entry.slot(), stack);
            } else {
                leftovers.add(stack); // another mod's Clone handler filled the slot first
            }
        }
        for (ItemStack stack : leftovers) {
            // Inventory#add deletes what doesn't fit when the player has infinite materials (creative), so those
            // players get the drop directly: never lose either item.
            if (target.hasInfiniteMaterials() || !inventory.add(stack)) {
                target.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
        original.removeData(InfinityAttachments.STASH);
    }

    /** Vanilla's layout (docs/api-notes.md §12): 0-8 hotbar, 9-35 main, 40 offhand, the rest worn. */
    private static DeathRule.SlotKind kindOf(int slot) {
        if (slot < Inventory.getSelectionSize()) {
            return DeathRule.SlotKind.HOTBAR;
        }
        if (slot < Inventory.INVENTORY_SIZE) {
            return DeathRule.SlotKind.MAIN;
        }
        if (slot == Inventory.SLOT_OFFHAND) {
            return DeathRule.SlotKind.OFFHAND;
        }
        return DeathRule.SlotKind.ARMOR;
    }
}
