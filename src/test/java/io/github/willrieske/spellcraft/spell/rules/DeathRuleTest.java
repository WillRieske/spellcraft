package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.DeathRule.Slot;
import io.github.willrieske.spellcraft.spell.rules.DeathRule.SlotKind;
import io.github.willrieske.spellcraft.spell.rules.DeathRule.Sorted;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DeathRuleTest {
    private static Slot filled(int index, SlotKind kind) {
        return new Slot(index, kind, false, false);
    }

    @Test
    void u1HotbarSlotsAreStashedInPlace() {
        for (int i = 0; i < 9; i++) {
            assertThat(DeathRule.sort(List.of(filled(i, SlotKind.HOTBAR)), false)
                            .stash())
                    .containsExactly(i);
        }
    }

    @Test
    void u2MainSlotsAreStashedInPlace() {
        for (int i = 9; i < 36; i++) {
            assertThat(DeathRule.sort(List.of(filled(i, SlotKind.MAIN)), false).stash())
                    .containsExactly(i);
        }
    }

    @Test
    void u3TheOffhandIsStashed() {
        assertThat(DeathRule.sort(List.of(filled(40, SlotKind.OFFHAND)), false).stash())
                .containsExactly(40);
    }

    @Test
    void u4ArmorIsDeleted() {
        for (int i = 36; i < 40; i++) {
            Sorted sorted = DeathRule.sort(List.of(filled(i, SlotKind.ARMOR)), false);
            assertThat(sorted.delete()).containsExactly(i);
            assertThat(sorted.stash()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(SlotKind.class)
    void u5VanishingItemsAreDeletedFromAnySlot(SlotKind kind) {
        Sorted sorted = DeathRule.sort(List.of(new Slot(3, kind, false, true)), false);
        assertThat(sorted.delete()).containsExactly(3);
        assertThat(sorted.stash()).isEmpty();
    }

    @Test
    void u6EmptySlotsAreSkipped() {
        Sorted sorted = DeathRule.sort(List.of(new Slot(0, SlotKind.HOTBAR, true, false)), false);
        assertThat(sorted.stash()).isEmpty();
        assertThat(sorted.delete()).isEmpty();
    }

    @Test
    void u7KeepInventorySortsNothing() {
        assertThat(DeathRule.sort(List.of(filled(0, SlotKind.HOTBAR), filled(36, SlotKind.ARMOR)), true))
                .isEqualTo(Sorted.NOTHING);
    }

    @Test
    void u8AFullInventoryStashes37AndDeletes4() {
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            slots.add(filled(i, SlotKind.HOTBAR));
        }
        for (int i = 9; i < 36; i++) {
            slots.add(filled(i, SlotKind.MAIN));
        }
        for (int i = 36; i < 40; i++) {
            slots.add(filled(i, SlotKind.ARMOR));
        }
        slots.add(filled(40, SlotKind.OFFHAND));
        Sorted sorted = DeathRule.sort(slots, false);
        assertThat(sorted.stash()).hasSize(37);
        assertThat(sorted.delete()).containsExactly(36, 37, 38, 39);
    }
}
