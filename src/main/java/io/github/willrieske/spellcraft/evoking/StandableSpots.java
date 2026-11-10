package io.github.willrieske.spellcraft.evoking;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a rescued player can be put or pulled to (Parts 4–5 spec §6.2, §6.4): a standable spot is a feet block with a
 * sturdy top under it and two blocks of air, so lava, water, portals and gateways never count.
 */
final class StandableSpots {
    /** How far sideways the lava pull looks for a shore. */
    static final double SHORE_RANGE = 6.0;

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
     * and from one block below their feet block to two above.
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
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
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

    /** The distance from {@code from} to the center of {@code pos}, ignoring height. */
    private static double sideways(Vec3 from, BlockPos pos) {
        return Math.hypot(pos.getX() + 0.5 - from.x, pos.getZ() + 0.5 - from.z);
    }
}
