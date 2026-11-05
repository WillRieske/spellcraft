package io.github.willrieske.spellcraft.trailing;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.spell.rules.Trail;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Every trail in one level: each owner's {@link Trail} and which flame belongs to whom. The value of the
 * {@code trailing_fires} level attachment; level attachments are re-saved on every world save (docs/api-notes.md
 * §18), so this object is mutated in place.
 */
public final class TrailFires {
    /**
     * One owner's saved trail.
     *
     * @param owner the player who laid the trail
     * @param flames the trail's flames, oldest first
     * @param countdown the ticks left until the oldest flame fades
     */
    public record Entry(UUID owner, List<Trail.Flame> flames, int countdown) {
        static final Codec<Trail.Flame> FLAME_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.LONG.fieldOf("pos").forGetter(Trail.Flame::pos),
                        Codec.BOOL.fieldOf("lingers").forGetter(Trail.Flame::lingers))
                .apply(instance, Trail.Flame::new));

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                        FLAME_CODEC.listOf().fieldOf("flames").forGetter(Entry::flames),
                        Codec.INT.fieldOf("countdown").forGetter(Entry::countdown))
                .apply(instance, Entry::new));

        public Entry {
            flames = List.copyOf(flames);
        }
    }

    /**
     * A stale flame (spec glossary): its turn to fade came while its chunk was unloaded. It has left its trail but
     * keeps its owner in the index until the sweep in {@link #fadeTick} puts it out.
     *
     * @param owner the player who laid the flame
     * @param pos the flame's position ({@link BlockPos#asLong()})
     */
    public record StaleFlame(UUID owner, long pos) {
        static final Codec<StaleFlame> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        UUIDUtil.CODEC.fieldOf("owner").forGetter(StaleFlame::owner),
                        Codec.LONG.fieldOf("pos").forGetter(StaleFlame::pos))
                .apply(instance, StaleFlame::new));
    }

    /**
     * Saves and loads the ledger as a {@code trails} list with one {@link Entry} per owner and a {@code stale} list of
     * {@link StaleFlame}s. {@code stale} is optional when read (a save from before it existed has none) and is not
     * written while empty.
     */
    public static final MapCodec<TrailFires> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Entry.CODEC.listOf().fieldOf("trails").forGetter(TrailFires::entries),
                    StaleFlame.CODEC
                            .listOf()
                            .optionalFieldOf("stale", List.of())
                            .forGetter(TrailFires::staleFlames))
            .apply(instance, TrailFires::new));

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, Trail> byOwner = new LinkedHashMap<>();
    private final Map<Long, UUID> ownerByPos = new HashMap<>();
    /** A set, so a spot its owner's trail lists twice goes stale once, and removal during the sweep is O(1). */
    private final Set<StaleFlame> stale = new LinkedHashSet<>();
    /** Reused by the stale sweep, so checking a chunk allocates nothing. */
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /** No trails. */
    public TrailFires() {}

    /**
     * Rebuilds the ledger from saved trails, with no stale flames.
     *
     * @param entries the saved trails
     * @see #TrailFires(List, List)
     */
    public TrailFires(List<Entry> entries) {
        this(entries, List.of());
    }

    /**
     * Rebuilds the ledger from saved trails and stale flames; empty trails are dropped. If a corrupt save lists an
     * owner twice, the last non-empty trail wins, only its flames are indexed, and a warning is logged. Stale flames
     * are indexed too; a trail flame at the same spot takes the index.
     *
     * @param entries the saved trails
     * @param staleFlames the saved stale flames
     */
    public TrailFires(List<Entry> entries, List<StaleFlame> staleFlames) {
        for (Entry entry : entries) {
            Trail trail = new Trail(entry.flames(), entry.countdown());
            if (!trail.isEmpty() && byOwner.put(entry.owner(), trail) != null) {
                LOGGER.warn("Trailing ledger lists owner {} twice; keeping the last trail", entry.owner());
            }
        }
        for (StaleFlame flame : staleFlames) {
            stale.add(flame);
            ownerByPos.put(flame.pos(), flame.owner());
        }
        byOwner.forEach((owner, trail) -> {
            for (Trail.Flame flame : trail.flames()) {
                ownerByPos.put(flame.pos(), owner);
            }
        });
    }

    /** The level's ledger (created empty on first use). */
    public static TrailFires of(ServerLevel level) {
        return level.getData(TrailingAttachments.FIRES);
    }

    /** Every owner's trail, in the order the owners first lit a flame. */
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        byOwner.forEach((owner, trail) -> entries.add(new Entry(owner, trail.flames(), trail.countdown())));
        return entries;
    }

    /**
     * The flames waiting for their chunk to load before they go out, in the order they went stale.
     *
     * @return a copy of the stale flames
     */
    public List<StaleFlame> staleFlames() {
        return List.copyOf(stale);
    }

    /**
     * Whether the ledger holds no trails and no stale flames (an owner with no flames is never kept).
     *
     * @return {@code true} if there is nothing to save
     */
    public boolean isEmpty() {
        return byOwner.isEmpty() && stale.isEmpty();
    }

    /** Who owns the flame at {@code pos}, if the ledger knows one. */
    public Optional<UUID> ownerOf(BlockPos pos) {
        return Optional.ofNullable(ownerByPos.get(pos.asLong()));
    }

    /**
     * Lights the stand-in cell for {@code owner} (spec §4): {@code feet} when it can take a flame, else the cell above
     * it (a player on soul sand or mud stands 2/16 deep inside the block, so {@code feet} is that block; both report a
     * full support shape, so the cell above qualifies), else nothing. The flame lingers when the block below the lit
     * cell is a soul-fire base. Then trims the owner's trail to the configured maximum.
     *
     * @param level the level to light in
     * @param owner the player laying the flame
     * @param feet the block position the player just left ({@code blockPosition()})
     * @return whether a flame was lit
     */
    public boolean light(ServerLevel level, ServerPlayer owner, BlockPos feet) {
        BlockPos pos;
        if (canLight(level, feet)) {
            pos = feet;
        } else if (canLight(level, feet.above())) {
            pos = feet.above();
        } else {
            return false;
        }
        BlockPos below = pos.below();
        boolean lingers = level.getBlockState(below).is(BlockTags.SOUL_FIRE_BASE_BLOCKS);
        level.setBlock(pos, ModBlocks.TRAIL_FIRE.get().defaultBlockState(), Block.UPDATE_ALL);
        UUID id = owner.getUUID();
        Trail trail = byOwner.computeIfAbsent(id, key -> new Trail());
        trail.add(new Trail.Flame(pos.asLong(), lingers));
        ownerByPos.put(pos.asLong(), id);
        for (Trail.Flame evicted : trail.trim(ServerConfig.trailingMaxBlocks())) {
            extinguish(level, id, evicted);
        }
        return true;
    }

    static boolean canLight(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean free = state.isAir()
                || (state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(BlockTags.FIRE));
        BlockPos below = pos.below();
        return free && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /**
     * One fade tick for every trail in the level; trails that empty are forgotten. Then the stale sweep: every stale
     * flame whose chunk is loaded now ({@code Level.isLoaded}, a chunk lookup that loads nothing) is put out and
     * forgotten.
     */
    public void fadeTick(ServerLevel level) {
        Iterator<Map.Entry<UUID, Trail>> trails = byOwner.entrySet().iterator();
        while (trails.hasNext()) {
            Map.Entry<UUID, Trail> ownerTrail = trails.next();
            ownerTrail.getValue().tick().ifPresent(flame -> extinguish(level, ownerTrail.getKey(), flame));
            if (ownerTrail.getValue().isEmpty()) {
                trails.remove();
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        Iterator<StaleFlame> waiting = stale.iterator();
        while (waiting.hasNext()) {
            StaleFlame flame = waiting.next();
            if (level.isLoaded(cursor.set(flame.pos()))) {
                waiting.remove();
                if (flame.owner().equals(ownerByPos.get(flame.pos()))) {
                    putOut(level, flame.pos());
                }
            }
        }
    }

    /**
     * Puts out {@code owner}'s flame, only while the index still says the flame is theirs: if the spot was put out
     * (by hand or water) and relit by another owner, that newer flame is theirs and stays. Accepted limit (spec §4):
     * if the same owner relit the spot, their trail lists it twice and the older entry puts the new flame out early.
     * If the flame's chunk is not loaded, nothing is read or changed there (reading a block loads its chunk,
     * docs/api-notes.md §18): the flame becomes stale instead, keeps its index entry (so the owner and their allies
     * stay spared) and waits for the sweep in {@link #fadeTick}. No chunk is ever loaded to put a flame out.
     */
    private void extinguish(ServerLevel level, UUID owner, Trail.Flame flame) {
        if (!owner.equals(ownerByPos.get(flame.pos()))) {
            return;
        }
        if (!level.isLoaded(cursor.set(flame.pos()))) {
            stale.add(new StaleFlame(owner, flame.pos()));
            return;
        }
        putOut(level, flame.pos());
    }

    /** Drops the index entry at {@code pos} and removes the block there if it is still trail fire (chunk loaded). */
    private void putOut(ServerLevel level, long pos) {
        ownerByPos.remove(pos);
        BlockPos at = BlockPos.of(pos);
        if (level.getBlockState(at).is(ModBlocks.TRAIL_FIRE.get())) {
            level.removeBlock(at, false);
        }
    }

    /**
     * Whether the flame at {@code pos} spares {@code entity}: the owner, or (while the owner is online in this
     * level) anyone the owner {@code isAlliedTo} (same team, tamed pets). An orphan spares nobody.
     */
    public boolean isSpared(ServerLevel level, BlockPos pos, Entity entity) {
        UUID owner = ownerByPos.get(pos.asLong());
        if (owner == null) {
            return false;
        }
        if (owner.equals(entity.getUUID())) {
            return true;
        }
        Player ownerPlayer = level.getPlayerByUUID(owner);
        return ownerPlayer != null && ownerPlayer.isAlliedTo(entity);
    }
}
