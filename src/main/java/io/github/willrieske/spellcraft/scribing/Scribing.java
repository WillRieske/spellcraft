package io.github.willrieske.spellcraft.scribing;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.Spell;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.ScrollDraw;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Scribing paper into a random scroll at an enchanting table (spec §5). Called from {@code EnchantmentMenuMixin};
 * vanilla's own checks (lapis, levels, the instabuild waiver) have already run.
 */
public final class Scribing {
    public static final TagKey<Item> SCRIBABLE = TagKey.create(Registries.ITEM, Spellcraft.id("scribable"));

    /** Any value but -1: NeoForge's screen greys out an option whose clue is -1 (docs/api-notes.md §15). */
    private static final int CLUE = 0;

    private Scribing() {}

    /** Whether the stack is something the enchanting table scribes instead of enchanting. */
    public static boolean isScribable(ItemStack stack) {
        return !stack.isEmpty() && stack.is(SCRIBABLE);
    }

    /** Every spell a scribe can draw, paired with its tier. */
    public static List<ScrollDraw.Candidate<Spell>> candidates() {
        return Spells.ALL.stream()
                .map(spell -> new ScrollDraw.Candidate<>(spell, spell.rules().tier()))
                .toList();
    }

    /** Server: show the three options. The odds ride in {@code levelClue}, which vanilla syncs to the client. */
    public static void offer(EnchantmentMenu menu) {
        List<Integer> chances = ServerConfig.scribingTopTierChances();
        for (int option = 0; option < ScrollDraw.OPTIONS; option++) {
            menu.costs[option] = option + 1;
            menu.enchantClue[option] = CLUE;
            menu.levelClue[option] = chances.get(option);
        }
        menu.broadcastChanges();
    }

    /** Server: take the cost, draw a spell, and swap the paper for its scroll. */
    public static void scribe(Player player, int option, Container slots, Level level, BlockPos pos) {
        int cost = option + 1;
        ItemStack lapis = slots.getItem(1);
        player.onEnchantmentPerformed(slots.getItem(0), cost);
        Spell spell = ScrollDraw.draw(
                option, candidates(), ServerConfig.scribingTopTierChances(), level.getRandom()::nextInt);
        slots.setItem(0, ModItems.scrollFor(spell));
        // consume, not shrink: vanilla's waiver leaves instabuild players' lapis alone.
        lapis.consume(cost, player);
        if (lapis.isEmpty()) {
            slots.setItem(1, ItemStack.EMPTY);
        }
        player.awardStat(Stats.ENCHANT_ITEM);
        slots.setChanged();
        level.playSound(
                null,
                pos,
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.BLOCKS,
                1.0F,
                level.getRandom().nextFloat() * 0.1F + 0.9F);
    }
}
