package io.github.willrieske.spellcraft.spell.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Where summoned vexes appear (spec §6): the 40 cells whose Chebyshev distance from the caster's block is 2 or 3.
 */
public final class VexRing {
    /** The nearest a vex spawns, per axis. */
    public static final int MIN_DISTANCE = 2;

    /** The farthest a vex spawns, per axis. */
    public static final int MAX_DISTANCE = 3;

    /** How many spawn cells there are: a 7×7 square minus its 3×3 middle. */
    public static final int COUNT = 40;

    /** How many vexes one cast summons. */
    public static final int SUMMON_COUNT = 3;

    /**
     * A spawn cell, relative to the caster's block.
     *
     * @param dx blocks east
     * @param dz blocks south
     */
    public record Offset(int dx, int dz) {}

    private static final List<Offset> OFFSETS = ring();

    private VexRing() {}

    private static List<Offset> ring() {
        List<Offset> offsets = new ArrayList<>(COUNT);
        for (int dz = -MAX_DISTANCE; dz <= MAX_DISTANCE; dz++) {
            for (int dx = -MAX_DISTANCE; dx <= MAX_DISTANCE; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) >= MIN_DISTANCE) {
                    offsets.add(new Offset(dx, dz));
                }
            }
        }
        if (offsets.size() != COUNT) {
            throw new AssertionError("ring size " + offsets.size());
        }
        return List.copyOf(offsets);
    }

    /**
     * The spawn cell for a roll in {@code 0 … COUNT − 1}.
     *
     * @param index the roll, 0 up to but not including {@link #COUNT}
     * @return the spawn cell, relative to the caster's block
     * @throws IllegalArgumentException if the index is outside 0 … {@code COUNT − 1}
     */
    public static Offset offset(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("index must be 0-" + (COUNT - 1) + ", got " + index);
        }
        return OFFSETS.get(index);
    }
}
