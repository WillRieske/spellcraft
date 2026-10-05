package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.DeathRule.Slot;
import io.github.willrieske.spellcraft.spell.rules.DeathRule.SlotKind;
import io.github.willrieske.spellcraft.spell.rules.DeathRule.Sorted;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DeathRulePropertiesTest {
    /** Up to 43 slots (vanilla's player inventory size), index = position, random kind and flags. */
    @Provide
    Arbitrary<List<Slot>> inventories() {
        Arbitrary<IntFunction<Slot>> slotAt = Combinators.combine(
                        Arbitraries.of(SlotKind.class), Arbitraries.of(true, false), Arbitraries.of(true, false))
                // The cast gives the inner lambda its target type; without it inference fails.
                .as((kind, empty, vanishing) -> (IntFunction<Slot>) index -> new Slot(index, kind, empty, vanishing));
        return slotAt.list()
                .ofMaxSize(43)
                .map(makers -> IntStream.range(0, makers.size())
                        .mapToObj(i -> makers.get(i).apply(i))
                        .toList());
    }

    @Property
    void everyNonEmptySlotIsStashedOrDeletedExactlyOnce(@ForAll("inventories") List<Slot> slots) {
        Sorted sorted = DeathRule.sort(slots, false);
        Set<Integer> stash = new HashSet<>(sorted.stash());
        Set<Integer> delete = new HashSet<>(sorted.delete());
        assertThat(java.util.Collections.disjoint(stash, delete)).isTrue();
        Set<Integer> nonEmpty = new HashSet<>();
        slots.stream().filter(s -> !s.empty()).forEach(s -> nonEmpty.add(s.index()));
        Set<Integer> both = new HashSet<>(stash);
        both.addAll(delete);
        assertThat(both).isEqualTo(nonEmpty);
    }

    @Property
    void neverStashesArmorOrVanishingItems(@ForAll("inventories") List<Slot> slots) {
        Set<Integer> stash = new HashSet<>(DeathRule.sort(slots, false).stash());
        slots.stream()
                .filter(s -> s.kind() == SlotKind.ARMOR || s.vanishing())
                .forEach(s -> assertThat(stash).doesNotContain(s.index()));
    }

    @Property
    void keepInventoryAlwaysSortsNothing(@ForAll("inventories") List<Slot> slots) {
        assertThat(DeathRule.sort(slots, true)).isEqualTo(Sorted.NOTHING);
    }
}
