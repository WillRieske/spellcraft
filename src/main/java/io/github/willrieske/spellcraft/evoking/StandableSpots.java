package io.github.willrieske.spellcraft.evoking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Where a rescued player can be put or pulled to (Parts 4–5 spec §6.2, §6.4): a standable spot is a feet block with
 * a sturdy top under it and two blocks of air, so lava, water, portals and gateways never count.
 */
final class StandableSpots {
    /** How far sideways the lava pull looks for a shore. */
    static final double SHORE_RANGE = 6.0;

    /** How far sideways the void catch looks for a landing. */
    static final int LANDING_RANGE = 32;

    /** Every column offset {@code (dx, dz)} within {@link #LANDING_RANGE} blocks, nearest first. */
    private static final List<int[]> COLUMNS = columns();

    private StandableSpots() {}

    /**
     * Whether a player can stand with their feet in {@code feet}.
     *
     * @param level the level
     * @param feet the feet block
     * @return true for a sturdy top below and air in the feet block and the one above
     */
    static boolean standable(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir();
    }

    /**
     * The nearest standable spot for a player in lava at {@code from}: within {@link #SHORE_RANGE} blocks sideways,
     * and from one block below their feet block to two above. Equally near spots resolve in the order {@code
     * BlockPos.betweenClosed} walks a box (z outermost, then y, x innermost): the first found wins.
     *
     * @param level the level
     * @param from the player's position
     * @return the shore's feet block, or empty
     */
    static Optional<BlockPos> shore(ServerLevel level, Vec3 from) {
        BlockPos feet = BlockPos.containing(from);
        int reach = (int) Math.ceil(SHORE_RANGE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    pos.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    if (sideways(from, pos) <= SHORE_RANGE && standable(level, pos)) {
                        double distance = Vec3.atBottomCenterOf(pos).distanceToSqr(from);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = pos.immutable();
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The column offsets of {@link #COLUMNS}: sorted once by squared distance, so the search is nearest first. */
    private static List<int[]> columns() {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -LANDING_RANGE; dx <= LANDING_RANGE; dx++) {
            for (int dz = -LANDING_RANGE; dz <= LANDING_RANGE; dz++) {
                if (dx * dx + dz * dz <= LANDING_RANGE * LANDING_RANGE) {
                    offsets.add(new int[] {dx, dz});
                }
            }
        }
        offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
        return List.copyOf(offsets);
    }

    /**
     * The nearest column to {@code from} within {@link #LANDING_RANGE} blocks whose top is a standable spot. A column
     * with no blocks, or in an unloaded chunk, reports the level's lowest Y and is skipped.
     *
     * @param level the level
     * @param from the falling player's position
     * @return the landing's feet block, or empty
     */
    static Optional<BlockPos> landing(ServerLevel level, Vec3 from) {
        BlockPos column = BlockPos.containing(from);
        for (int[] offset : COLUMNS) {
            int x = column.getX() + offset[0];
            int z = column.getZ() + offset[1];
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > level.getMinY()) {
                BlockPos feet = new BlockPos(x, y, z);
                if (standable(level, feet)) {
                    return Optional.of(feet);
                }
            }
        }
        return Optional.empty();
    }

    /** The distance from {@code from} to the center of {@code pos}, ignoring height. */
    private static double sideways(Vec3 from, BlockPos pos) {
        return Math.hypot(pos.getX() + 0.5 - from.x, pos.getZ() + 0.5 - from.z);
    }
}
