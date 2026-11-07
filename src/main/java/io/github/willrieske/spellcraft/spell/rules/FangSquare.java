package io.github.willrieske.spellcraft.spell.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The 13×13 block area a fang cast covers (spec §5), as offsets from the caster's block. The caster's block is always
 * on an edge: the facing vector {@code (−sin yaw, cos yaw)} is scaled so its larger component is ±1 and the center is
 * 6 blocks along it, so one center offset is exactly ±6 and the other sweeps −6…6 as the player turns.
 *
 * @param centerDx the square's center, east of the caster's block
 * @param centerDz the square's center, south of the caster's block
 */
public record FangSquare(int centerDx, int centerDz) {
    /** Half the square's side; the center is this far from every edge. */
    public static final int HALF = 6;

    /** The square's side in blocks. */
    public static final int SIZE = 2 * HALF + 1;

    /** How many cells get a fang: every cell but the caster's. */
    public static final int CELL_COUNT = SIZE * SIZE - 1;

    /**
     * One cell of the square.
     *
     * @param dx blocks east of the caster's block
     * @param dz blocks south of the caster's block
     * @param delayTicks the fang's warmup beyond the cast's: the Chebyshev distance from the caster minus one, so the
     *     fangs ripple outward one block per tick
     */
    public record Cell(int dx, int dz, int delayTicks) {}

    /**
     * The square for a caster facing {@code yawDegrees} (Minecraft yaw: 0 south, 90 west, 180 north, −90 east; any
     * finite value is accepted and wraps). Rounding is half-up, so the square is mirror-symmetric except at exact
     * ties, which no real yaw reaches.
     *
     * @param yawDegrees the caster's yaw
     * @return the square, with the caster's block on its edge
     * @throws IllegalArgumentException if the yaw is NaN or infinite
     */
    public static FangSquare of(double yawDegrees) {
        if (!Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("yaw must be finite, got " + yawDegrees);
        }
        double yaw = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double k = Math.max(Math.abs(fx), Math.abs(fz));
        return new FangSquare((int) Math.round(HALF * fx / k), (int) Math.round(HALF * fz / k));
    }

    /** The square's westmost dx. */
    public int minDx() {
        return centerDx - HALF;
    }

    /** The square's eastmost dx. */
    public int maxDx() {
        return centerDx + HALF;
    }

    /** The square's northmost dz. */
    public int minDz() {
        return centerDz - HALF;
    }

    /** The square's southmost dz. */
    public int maxDz() {
        return centerDz + HALF;
    }

    /**
     * Whether the cell at {@code (dx, dz)} from the caster's block lies inside the square (edges included).
     *
     * @param dx blocks east of the caster's block
     * @param dz blocks south of the caster's block
     * @return true if the cell is inside the square
     */
    public boolean contains(int dx, int dz) {
        return dx >= minDx() && dx <= maxDx() && dz >= minDz() && dz <= maxDz();
    }

    /**
     * Every cell but the caster's, row by row, each with its ripple delay.
     *
     * @return an unmodifiable list of {@link #CELL_COUNT} cells
     */
    public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>(CELL_COUNT);
        for (int dz = minDz(); dz <= maxDz(); dz++) {
            for (int dx = minDx(); dx <= maxDx(); dx++) {
                if (dx != 0 || dz != 0) {
                    cells.add(new Cell(dx, dz, Math.max(Math.abs(dx), Math.abs(dz)) - 1));
                }
            }
        }
        return Collections.unmodifiableList(cells);
    }
}
