package io.github.willrieske.spellcraft.spell.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The block columns a target's bounding box covers (Evoking II spec §2.2): a fang goes under each one. Pure: the box
 * arrives as its horizontal extent, not as an {@code AABB}. Meant for entity-sized boxes (a few blocks wide); a box
 * thousands of blocks wide would list every column in it.
 */
public final class FangFootprints {
    /**
     * One block column, in absolute block coordinates.
     *
     * @param x the block's x
     * @param z the block's z
     */
    public record Column(int x, int z) {}

    private FangFootprints() {}

    /**
     * Every column the box covers with positive width: from {@code floor(min)} to {@code ceil(max) − 1} on each axis,
     * so a box whose edge sits exactly on a block boundary does not claim the block beyond it. A box with no width on
     * an axis still claims the column it sits in. Row by row (z, then x).
     *
     * @param minX the box's west edge
     * @param minZ the box's north edge
     * @param maxX the box's east edge
     * @param maxZ the box's south edge
     * @return the columns, west to east within each row, north to south
     * @throws IllegalArgumentException if a coordinate is not finite or a max is below its min
     */
    public static List<Column> columns(double minX, double minZ, double maxX, double maxZ) {
        requireBox(minX, maxX);
        requireBox(minZ, maxZ);
        int x0 = (int) Math.floor(minX);
        int x1 = Math.max(x0, (int) Math.ceil(maxX) - 1);
        int z0 = (int) Math.floor(minZ);
        int z1 = Math.max(z0, (int) Math.ceil(maxZ) - 1);
        List<Column> columns = new ArrayList<>();
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                columns.add(new Column(x, z));
            }
        }
        return Collections.unmodifiableList(columns);
    }

    /**
     * The columns of {@code columns} that lie inside {@code square} for a caster whose block is {@code (feetX,
     * feetZ)}, the caster's own column dropped; order kept.
     *
     * @param columns the candidate columns, absolute
     * @param square the square ahead of the caster
     * @param feetX the caster's block x
     * @param feetZ the caster's block z
     * @return the kept columns
     */
    public static List<Column> inSquare(List<Column> columns, FangSquare square, int feetX, int feetZ) {
        List<Column> kept = new ArrayList<>();
        for (Column column : columns) {
            int dx = column.x() - feetX;
            int dz = column.z() - feetZ;
            if ((dx != 0 || dz != 0) && square.contains(dx, dz)) {
                kept.add(column);
            }
        }
        return Collections.unmodifiableList(kept);
    }

    private static void requireBox(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || max < min) {
            throw new IllegalArgumentException("not a box: [" + min + ", " + max + "]");
        }
    }
}
