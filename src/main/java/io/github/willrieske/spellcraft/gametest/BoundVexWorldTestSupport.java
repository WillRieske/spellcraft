package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The End and Nether rooms of the Part 5 GameTests. Tests in a batch run at the same time and share every dimension
 * but their own place in it, so a test never builds at its player's coordinates in another dimension: it takes a
 * private slot from a pool (grid points 200 blocks apart) and everything it does stays inside that slot. The slot is
 * given back, the blocks restored, the chunks unforced and the non-player entities discarded when the test ends
 * (docs/api-notes.md §24).
 */
final class BoundVexWorldTestSupport {
    /** The distance between two neighboring slots; wider than the cleanup reach of two slots put together. */
    private static final int SPACING = 200;

    /** The height of an End slot: far above the main island's terrain, in empty void. */
    private static final int END_Y = 80;

    /** The height of a Nether slot: inside the ceiling's reach of the stone shell, which keeps lava out. */
    private static final int NETHER_Y = 100;

    /** An End slot is at least this far from the origin: the main island ends near 200. */
    private static final int END_MIN_DISTANCE = 450;

    /** An End slot is at most this far from the origin: the outer islands start near 1000. */
    private static final int END_MAX_DISTANCE = 850;

    /** The first Nether slot column; the pool runs east and south from here. */
    private static final int NETHER_FIRST_X = 1000;

    /** How many Nether slot columns and rows there are. */
    private static final int NETHER_GRID = 5;

    /** The most a test may ask of {@link #endVoid}: its reach plus the cleanup margin stays inside a slot's half. */
    private static final int MAX_REACH = 90;

    /** How far beyond a place's reach the cleanup looks for leftover entities. */
    private static final int CLEANUP_MARGIN = 8;

    /** The slots that are taken right now, by dimension. Touched only on the server thread; guarded anyway. */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> TAKEN = new LinkedHashMap<>();

    /** The places in use right now, so {@link #platform} can find the one it belongs to. */
    private static final List<Place> ACTIVE = new ArrayList<>();

    private BoundVexWorldTestSupport() {}

    /**
     * A private place in another dimension: a slot, the chunks forced around it and the blocks changed there.
     * Cleaning it up undoes all of it.
     */
    private static final class Place {
        /** The dimension's level. */
        private final ServerLevel level;

        /** The slot: the block the player's feet occupy. */
        private final BlockPos slot;

        /** How far from the slot the place reaches, in blocks. */
        private final int reach;

        /** The forced chunks. */
        private final List<ChunkPos> chunks = new ArrayList<>();

        /** The first state of every block this place replaced, in the order they were replaced. */
        private final Map<BlockPos, BlockState> was = new LinkedHashMap<>();

        /**
         * The ground {@link #clearGround} removed, which is a few thousand blocks: put back without neighbor updates.
         */
        private final Map<BlockPos, BlockState> ground = new LinkedHashMap<>();

        /** Takes the slot and forces the chunks within {@code reach} of it. */
        Place(ServerLevel level, BlockPos slot, int reach) {
            this.level = level;
            this.slot = slot;
            this.reach = reach;
            int minCx = SectionPos.blockToSectionCoord(slot.getX() - reach);
            int maxCx = SectionPos.blockToSectionCoord(slot.getX() + reach);
            int minCz = SectionPos.blockToSectionCoord(slot.getZ() - reach);
            int maxCz = SectionPos.blockToSectionCoord(slot.getZ() + reach);
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
            chunks.forEach(chunk -> {
                level.setChunkForced(chunk.x(), chunk.z(), true);
                level.getChunk(chunk.x(), chunk.z()); // loaded now, so the carving lands and height maps are real
            });
        }

        /** Whether {@code pos} lies inside this place's box. */
        boolean contains(BlockPos pos) {
            return Math.abs(pos.getX() - slot.getX()) <= reach && Math.abs(pos.getZ() - slot.getZ()) <= reach;
        }

        /** Sets {@code pos} to {@code state}, remembering what was there the first time. */
        void set(BlockPos pos, BlockState state) {
            was.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }

        /**
         * Removes the GameTest world's flat floor (a few layers at the bottom of every dimension) from every forced
         * chunk, so the place has no block at all below its slot. Recorded for {@link #clean}; no neighbor updates,
         * because there are tens of thousands of blocks.
         */
        void clearGround() {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (ChunkPos chunk : chunks) {
                for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                    for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        for (int y = level.getMinY(); y < Math.min(top, slot.getY()); y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            ground.putIfAbsent(pos, level.getBlockState(pos));
                            level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }

        /**
         * Undoes the place: discards every non-player entity within {@code reach + 8}, restores the blocks, unforces
         * the chunks and gives the slot back.
         */
        void clean() {
            double r = reach + CLEANUP_MARGIN;
            AABB around = new AABB(
                    slot.getX() - r,
                    level.getMinY(),
                    slot.getZ() - r,
                    slot.getX() + r,
                    level.getMaxY() + 1,
                    slot.getZ() + r);
            for (Entity entity : level.getEntities((Entity) null, around, e -> !(e instanceof Player))) {
                entity.discard();
            }
            was.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            ground.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
            chunks.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
            synchronized (TAKEN) {
                TAKEN.get(level.dimension()).remove(slot);
            }
            ACTIVE.remove(this);
        }
    }

    /**
     * An air room inside a one-block stone shell at a private slot of {@code dimension}, with {@code player} standing
     * on the middle of its floor. The room is {@code 2 * halfWidth + 1} blocks wide and deep and {@code height} tall;
     * the floor block under the player's feet is stone. When the test ends the room is undone (see the class
     * comment) and the player goes back to {@link FangGameTests#STAND}.
     *
     * @param helper the test helper
     * @param player the player to move into the room
     * @param dimension {@link Level#END} or {@link Level#NETHER}
     * @param halfWidth how many blocks the room reaches from its middle, sideways
     * @param height how many blocks of air stand above the floor
     * @return the dimension's level
     */
    static ServerLevel room(
            GameTestHelper helper, ServerPlayer player, ResourceKey<Level> dimension, int halfWidth, int height) {
        ServerLevel level = levelOf(helper, dimension);
        BlockPos slot = takeSlot(helper, dimension);
        int reach = halfWidth + 1;
        Place place = begin(helper, player, level, slot, reach);
        BlockPos low = slot.offset(-reach, -1, -reach);
        BlockPos high = slot.offset(reach, height, reach);
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            boolean shell = pos.getX() == low.getX()
                    || pos.getX() == high.getX()
                    || pos.getY() == low.getY()
                    || pos.getY() == high.getY()
                    || pos.getZ() == low.getZ()
                    || pos.getZ() == high.getZ();
            place.set(pos, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        arrive(player, level, slot);
        return level;
    }

    /**
     * The End at a private slot with no blocks at all (the GameTest world's flat floor is cleared from the forced chunks): {@code player} stands in the void at the slot. Every chunk
     * within {@code reach} blocks is forced and loaded, so height maps and entity ticking cover that much. When the
     * test ends the place is undone (see the class comment) and the player goes back to
     * {@link FangGameTests#STAND}.
     *
     * @param helper the test helper
     * @param player the player to move into the void
     * @param reach how far from the player the test needs loaded chunks; at most 90
     * @return the End's level
     */
    static ServerLevel endVoid(GameTestHelper helper, ServerPlayer player, int reach) {
        if (reach < 0 || reach > MAX_REACH) {
            throw helper.assertionException("setup: reach " + reach + " is outside 0.." + MAX_REACH);
        }
        ServerLevel level = levelOf(helper, Level.END);
        BlockPos slot = takeSlot(helper, Level.END);
        begin(helper, player, level, slot, reach).clearGround();
        arrive(player, level, slot);
        return level;
    }

    /**
     * An open stone platform, one layer thick with nothing above it, centered on {@code center} and
     * {@code 2 * halfWidth + 1} blocks wide; removed when the test ends. It must lie inside a place that
     * {@link #room} or {@link #endVoid} made, which restores its blocks and then unforces the chunks.
     *
     * @param helper the test helper
     * @param level the level of the place
     * @param center the middle block of the platform (its top layer is this block's layer)
     * @param halfWidth how many blocks the platform reaches from its middle
     */
    static void platform(GameTestHelper helper, ServerLevel level, BlockPos center, int halfWidth) {
        Place place = ACTIVE.stream()
                .filter(p -> p.level.dimension().equals(level.dimension()) && p.contains(center))
                .findFirst()
                .orElseThrow(() -> helper.assertionException("setup: a platform needs an endVoid or room place"));
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-halfWidth, 0, -halfWidth), center.offset(halfWidth, 0, halfWidth))) {
            place.set(pos, Blocks.STONE.defaultBlockState());
        }
    }

    /**
     * A Bound Vex of {@code summoner} with its full AI and the iron sword in {@code level}, which is not the test's
     * level (as {@link BoundVexTestSupport#boundVex} does in the test level); discarded when the test ends. It has no
     * gravity until its first tick sets it, so the spawn gives it none.
     *
     * @param helper the test helper
     * @param level the level to spawn in
     * @param at where to spawn it, absolute
     * @param summoner the player it counts as summoned by
     * @return the vex
     */
    static BoundVex vexIn(GameTestHelper helper, ServerLevel level, Vec3 at, ServerPlayer summoner) {
        BoundVex vex = ModEntities.BOUND_VEX.get().create(level, EntitySpawnReason.STRUCTURE);
        if (vex == null) {
            throw helper.assertionException("could not create a Bound Vex in " + level.dimension());
        }
        vex.setPersistenceRequired();
        vex.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        vex.finalizeSpawn(
                level, level.getCurrentDifficultyAt(vex.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        vex.setData(AllyAttachments.SUMMONER, summoner.getUUID());
        vex.setBoundOrigin(BlockPos.containing(at));
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntityWithPassengers(vex);
        whenDone(helper, vex::discard);
        return vex;
    }

    /**
     * Makes a mock player report the fluid at its feet and eyes: the server never ticks a mock player, and the fluid
     * state is filled by {@code baseTick} (docs/api-notes.md §24). Twice, because {@code isUnderWater} reads the eye
     * state the previous tick left. The player must be invulnerable: {@code baseTick} also burns and drowns.
     *
     * @param player the mock player
     */
    static void soak(ServerPlayer player) {
        player.baseTick();
        player.baseTick();
    }

    /** The level of {@code dimension}, or a test failure naming the missing dimension. */
    private static ServerLevel levelOf(GameTestHelper helper, ResourceKey<Level> dimension) {
        ServerLevel level = helper.getLevel().getServer().getLevel(dimension);
        if (level == null) {
            throw helper.assertionException("no " + dimension.identifier() + " on the GameTest server");
        }
        return level;
    }

    /** Forces the place's chunks, registers its cleanup and the player's return, and records it as active. */
    private static Place begin(
            GameTestHelper helper, ServerPlayer player, ServerLevel level, BlockPos slot, int reach) {
        Place place = new Place(level, slot, reach);
        ACTIVE.add(place);
        whenDone(helper, () -> {
            place.clean();
            MockPlayers.moveTo(helper, player, FangGameTests.STAND);
        });
        return place;
    }

    /** Moves {@code player} to the middle of the slot's block. */
    private static void arrive(ServerPlayer player, ServerLevel level, BlockPos slot) {
        player.teleportTo(level, slot.getX() + 0.5, slot.getY(), slot.getZ() + 0.5, Set.of(), 0.0F, 0.0F, false);
    }

    /** Takes a free slot of {@code dimension}, or fails the test with "setup: no free slot". */
    private static BlockPos takeSlot(GameTestHelper helper, ResourceKey<Level> dimension) {
        synchronized (TAKEN) {
            Set<BlockPos> taken = TAKEN.computeIfAbsent(dimension, d -> new HashSet<>());
            for (BlockPos slot : slots(helper, dimension)) {
                if (taken.add(slot)) {
                    return slot;
                }
            }
        }
        throw helper.assertionException("setup: no free slot");
    }

    /** Every slot of {@code dimension}, in the order they are handed out. */
    private static List<BlockPos> slots(GameTestHelper helper, ResourceKey<Level> dimension) {
        List<BlockPos> slots = new ArrayList<>();
        if (dimension.equals(Level.END)) {
            for (int gx = -4; gx <= 4; gx++) {
                for (int gz = -4; gz <= 4; gz++) {
                    double distance = Math.hypot(gx * SPACING, gz * SPACING);
                    if (distance >= END_MIN_DISTANCE && distance <= END_MAX_DISTANCE) {
                        slots.add(new BlockPos(gx * SPACING, END_Y, gz * SPACING));
                    }
                }
            }
        } else if (dimension.equals(Level.NETHER)) {
            for (int gx = 0; gx < NETHER_GRID; gx++) {
                for (int gz = 0; gz < NETHER_GRID; gz++) {
                    slots.add(new BlockPos(NETHER_FIRST_X + gx * SPACING, NETHER_Y, gz * SPACING));
                }
            }
        } else {
            throw helper.assertionException("setup: no slots in " + dimension.identifier());
        }
        return slots;
    }
}
