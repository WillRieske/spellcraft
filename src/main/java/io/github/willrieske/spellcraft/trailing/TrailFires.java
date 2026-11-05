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
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * The Ledger (spec glossary): every trail in one level, each owner's {@link Trail}, the stale flames, and which flame
 * belongs to whom. The value of the {@code trailing_fires} level attachment; level attachments are re-saved on every
 * world save (docs/api-notes.md §18), so this object is mutated in place.
 *
 * <p>Who owns a spot (spec §4): whoever lit it last. The index is the truth. A trail or a stale entry may still list
 * a flame the index gives to someone else (the flame was put out and another owner relit the spot); that entry is
 * dead, its turn (or its sweep) is a no-op, and the save drops it: {@link #entries()} and the saved {@code stale} list
 * keep only flames the index gives to that owner. So a save lists each spot under one owner at most, and a reload can
 * never hand a spot back to the older owner. The live trails and stale set are left as they are.
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
     * {@link StaleFlame}s, each holding only flames the index gives to that owner (see the class comment). {@code
     * stale} is optional when read (a save from before it existed has none) and is not written while empty.
     */
    public static final MapCodec<TrailFires> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Entry.CODEC.listOf().fieldOf("trails").forGetter(TrailFires::entries),
                    StaleFlame.CODEC
                            .listOf()
                            .optionalFieldOf("stale", List.of())
                            .forGetter(TrailFires::savedStaleFlames))
            .apply(instance, TrailFires::new));

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, Trail> byOwner = new LinkedHashMap<>();
    private final Map<Long, UUID> ownerByPos = new HashMap<>();
    /** A set, so a spot its owner's trail lists twice goes stale once, and removal during the sweep is O(1). */
    private final Set<StaleFlame> stale = new LinkedHashSet<>();

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
     * are indexed too. A save this class wrote lists each spot under one owner at most (see the class comment), so no
     * other rule is needed to decide who owns a spot.
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

    /**
     * The level's ledger (created empty on first use).
     *
     * @param level the level whose ledger to fetch
     * @return the ledger stored on {@code level}
     */
    public static TrailFires of(ServerLevel level) {
        return level.getData(TrailingAttachments.FIRES);
    }

    /**
     * Every owner's trail as the save writes it, in the order the owners first lit a flame: only the flames the index
     * gives to that owner (dead entries are dropped, see the class comment), and no owner whose every flame is dead.
     * The countdown is kept as it is; if the oldest flame was dead, the rebuilt trail clamps it to the new oldest, so
     * after a reload that flame may fade up to one turn early (accepted).
     *
     * @return a snapshot of the trails, one {@link Entry} per owner with an owned flame
     */
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        byOwner.forEach((owner, trail) -> {
            List<Trail.Flame> owned = trail.flames().stream()
                    .filter(flame -> owns(owner, flame.pos()))
                    .toList();
            if (!owned.isEmpty()) {
                entries.add(new Entry(owner, owned, trail.countdown()));
            }
        });
        return entries;
    }

    /**
     * Every flame waiting for its chunk to be ready before it goes out, in the order they went stale, dead entries
     * included (the sweep forgets those without touching the spot). The save writes only the owned ones.
     *
     * @return a copy of the stale flames
     */
    public List<StaleFlame> staleFlames() {
        return List.copyOf(stale);
    }

    /** The stale flames as the save writes them: only those the index still gives to their owner, in order. */
    private List<StaleFlame> savedStaleFlames() {
        return stale.stream().filter(flame -> owns(flame.owner(), flame.pos())).toList();
    }

    /**
     * Whether the ledger holds nothing the save would keep: no trail or stale flame the index gives to its owner. Dead
     * entries do not count, so this agrees with what the codec writes ({@link #entries()} and the owned stale flames):
     * the attachment skips saving an empty ledger, and a ledger of dead entries alone is not written.
     *
     * @return {@code true} if there is nothing to save
     */
    public boolean isEmpty() {
        for (Map.Entry<UUID, Trail> ownerTrail : byOwner.entrySet()) {
            for (Trail.Flame flame : ownerTrail.getValue().flames()) {
                if (owns(ownerTrail.getKey(), flame.pos())) {
                    return false;
                }
            }
        }
        for (StaleFlame flame : stale) {
            if (owns(flame.owner(), flame.pos())) {
                return false;
            }
        }
        return true;
    }

    /** Whether the index gives the flame at {@code pos} to {@code owner}. */
    private boolean owns(UUID owner, long pos) {
        return owner.equals(ownerByPos.get(pos));
    }

    /**
     * Who owns the flame at {@code pos}, if the ledger knows one.
     *
     * @param pos the flame's position
     * @return the owner's UUID, or empty for an orphan or a spot with no flame
     */
    public Optional<UUID> ownerOf(BlockPos pos) {
        return Optional.ofNullable(ownerByPos.get(pos.asLong()));
    }

    /**
     * Forgets {@code owner}'s trail and stale flames and every index entry pointing at them, leaving the blocks where
     * they are (they become orphans). For GameTests that share one level and must leave no ledger entry behind.
     *
     * @param owner the owner to forget
     */
    public void forget(UUID owner) {
        byOwner.remove(owner);
        stale.removeIf(flame -> flame.owner().equals(owner));
        ownerByPos.values().removeIf(owner::equals);
    }

    /**
     * Lights the stand-in cell for {@code owner} (spec §4): {@code feet} when it can take a flame, else the cell above
     * it (a player on soul sand or mud stands 2/16 deep inside the block, so {@code feet} is that block; both report a
     * full support shape, so the cell above qualifies), else nothing. The flame lingers when the block below the lit
     * cell is a soul-fire base. If {@code setBlock} reports no change, nothing is recorded. Then trims the owner's
     * trail to the configured maximum.
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
        if (!level.setBlock(pos, ModBlocks.TRAIL_FIRE.get().defaultBlockState(), Block.UPDATE_ALL)) {
            return false;
        }
        UUID id = owner.getUUID();
        Trail trail = byOwner.computeIfAbsent(id, key -> new Trail());
        trail.add(new Trail.Flame(pos.asLong(), lingers));
        ownerByPos.put(pos.asLong(), id);
        for (Trail.Flame evicted : trail.trim(ServerConfig.trailingMaxFlames())) {
            extinguish(level, id, evicted);
        }
        return true;
    }

    /**
     * A cell can take a flame when it is inside the build limits and is air, or replaceable with no fluid and not
     * fire, over a sturdy top face. The bounds check comes first: above the top layer {@code getBlockState} reports
     * void air ({@code Level.java:370-372}), which would pass the air test.
     */
    private static boolean canLight(ServerLevel level, BlockPos pos) {
        if (!level.isInValidBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        boolean free = state.isAir()
                || (state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(BlockTags.FIRE));
        BlockPos below = pos.below();
        return free && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /**
     * One fade tick for every trail in the level; trails that empty are forgotten. Then the stale sweep: every stale
     * flame whose chunk is ready now ({@link #isReady}) is put out, if the index still gives it to its owner, and
     * forgotten.
     *
     * @param level the level this ledger belongs to
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
            if (isReady(level, flame.pos())) {
                waiting.remove();
                if (owns(flame.owner(), flame.pos())) {
                    putOut(level, flame.pos());
                }
            }
        }
    }

    /**
     * Puts out {@code owner}'s flame, only while the index still says the flame is theirs: if the spot was put out
     * (by hand or water) and relit by another owner, that newer flame is theirs and stays. Accepted limit (spec §4):
     * if the same owner relit the spot, their trail lists it twice and the older entry puts the new flame out early.
     * If the flame's chunk is not ready ({@link #isReady}), nothing is read or changed there (reading a block loads
     * its chunk, or waits for a running load, docs/api-notes.md §18): the flame becomes stale instead, keeps its
     * index entry (so the owner and their allies stay spared) and waits for the sweep in {@link #fadeTick}. No chunk
     * is ever loaded, or waited on, to put a flame out.
     */
    private void extinguish(ServerLevel level, UUID owner, Trail.Flame flame) {
        if (!owns(owner, flame.pos())) {
            return;
        }
        if (!isReady(level, flame.pos())) {
            stale.add(new StaleFlame(owner, flame.pos()));
            return;
        }
        putOut(level, flame.pos());
    }

    /**
     * Whether the chunk holding {@code pos} is ready: at FULL status in the chunk cache, so reading or writing a block
     * there neither loads anything nor waits for a load. {@code ServerChunkCache.getChunkNow} ({@code
     * ServerChunkCache.java:180-210}) is a cache and chunk-holder lookup that returns the chunk only when it is ready.
     * Not {@code Level.isLoaded}: that is true once the chunk's ticket level reaches FULL ({@code
     * ServerChunkCache.java:262-267}), while its load may still be running, and a block read would then block the
     * server thread until it finishes (docs/api-notes.md §18). Server thread only: {@code getChunkNow} answers
     * {@code null} on any other thread.
     *
     * @param level the level the position is in
     * @param pos the position ({@link BlockPos#asLong()})
     * @return {@code true} if blocks at {@code pos} can be read and written without touching the chunk system
     */
    static boolean isReady(ServerLevel level, long pos) {
        return level.getChunkSource()
                        .getChunkNow(
                                SectionPos.blockToSectionCoord(BlockPos.getX(pos)),
                                SectionPos.blockToSectionCoord(BlockPos.getZ(pos)))
                != null;
    }

    /** Drops the index entry at {@code pos} and removes the block there if it is still trail fire (chunk ready). */
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
     *
     * <p>Fast path, since this runs every tick for every entity in a flame: an entity with no team that is neither an
     * {@code OwnableEntity} nor an allay cannot be allied to a player, so the owner lookup (a scan of the level's
     * players, {@code EntityGetter.java:113-122}) is skipped. Why: {@code Entity.java:2879-2889} {@code isAlliedTo} is
     * {@code this == other || considersEntityAsAlly(other) || other.considersEntityAsAlly(this)}, and the default
     * {@code considersEntityAsAlly} is a team check that is false when either side has no team ({@code Team.java:17},
     * {@code Entity.java:2887}). The vanilla overrides that ally a team-less entity to a player are
     * {@code TamableAnimal.java:209-222} (an {@code OwnableEntity}, {@code TamableAnimal.java:34}; its
     * {@code getTeam()} {@code :192-206} also borrows the owner's) and {@code Allay.java:160-163} (the player it
     * likes); {@code AbstractIllager} and {@code Evoker} ally only illagers and vexes. An entity a mod adds with its
     * own {@code considersEntityAsAlly} for players would need adding here.
     *
     * @param level the level the flame is in
     * @param pos the flame's position
     * @param entity the entity standing in the flame
     * @return {@code true} if the flame must not burn {@code entity}
     */
    public boolean isSpared(ServerLevel level, BlockPos pos, Entity entity) {
        UUID owner = ownerByPos.get(pos.asLong());
        if (owner == null) {
            return false;
        }
        if (owner.equals(entity.getUUID())) {
            return true;
        }
        if (entity.getTeam() == null && !(entity instanceof OwnableEntity) && !(entity instanceof Allay)) {
            return false;
        }
        Player ownerPlayer = level.getPlayerByUUID(owner);
        return ownerPlayer != null && ownerPlayer.isAlliedTo(entity);
    }
}
