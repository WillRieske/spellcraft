package io.github.willrieske.spellcraft.spell.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What an Infinity death does to each inventory slot (spec §4): armor and Curse of Vanishing items are deleted;
 * everything else is stashed for respawn, in place. With {@code keepInventory} on, vanilla keeps everything and
 * this rule sorts nothing.
 */
public final class DeathRule {
    public enum SlotKind {
        HOTBAR,
        MAIN,
        OFFHAND,
        /** Anything worn: the four armor slots, and vanilla's body-armor and saddle slots (docs/api-notes.md §12). */
        ARMOR
    }

    public record Slot(int index, SlotKind kind, boolean empty, boolean vanishing) {
        public Slot {
            Objects.requireNonNull(kind, "kind");
        }
    }

    public record Sorted(List<Integer> stash, List<Integer> delete) {
        public static final Sorted NOTHING = new Sorted(List.of(), List.of());

        public Sorted {
            stash = List.copyOf(stash);
            delete = List.copyOf(delete);
        }
    }

    private DeathRule() {}

    /**
     * Partitions non-empty slots into indices to stash (in place) and indices to delete (armor, Curse of Vanishing);
     * {@code keepInventory} sorts nothing.
     */
    public static Sorted sort(List<Slot> slots, boolean keepInventory) {
        if (keepInventory) {
            return Sorted.NOTHING;
        }
        List<Integer> stash = new ArrayList<>();
        List<Integer> delete = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.empty()) {
                continue;
            }
            if (slot.vanishing() || slot.kind() == SlotKind.ARMOR) {
                delete.add(slot.index());
            } else {
                stash.add(slot.index());
            }
        }
        return new Sorted(stash, delete);
    }
}
