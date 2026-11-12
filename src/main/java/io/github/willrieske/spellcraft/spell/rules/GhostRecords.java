package io.github.willrieske.spellcraft.spell.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * The one list rule of the villager-ghost records (Parts 6-7 spec §9): the oldest record goes first.
 *
 * <p>Functional spec §16.1.
 */
public final class GhostRecords {
    /** Most records a level keeps. Functional spec §16.1. */
    public static final int MAX = 32;

    private GhostRecords() {}

    /**
     * {@code records} with {@code record} added at the end, trimmed from the front to at most {@code max}.
     *
     * @param records the records so far, oldest first; not changed
     * @param record the new record
     * @param max the most to keep, at least 1
     * @param <T> the record type
     * @return a new list, oldest first, newest last
     * @throws IllegalArgumentException if {@code max} is less than 1
     */
    public static <T> List<T> add(List<T> records, T record, int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max must be at least 1, got " + max);
        }
        List<T> all = new ArrayList<>(records.size() + 1);
        all.addAll(records);
        all.add(record);
        return List.copyOf(all.subList(Math.max(0, all.size() - max), all.size()));
    }
}
