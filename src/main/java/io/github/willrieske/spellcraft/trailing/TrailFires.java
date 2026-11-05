package io.github.willrieske.spellcraft.trailing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.spell.rules.Trail;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** Saves and loads the ledger as a {@code trails} list with one {@link Entry} per owner. */
    public static final MapCodec<TrailFires> CODEC =
            Entry.CODEC.listOf().fieldOf("trails").xmap(TrailFires::new, TrailFires::entries);

    private final Map<UUID, Trail> byOwner = new LinkedHashMap<>();
    private final Map<Long, UUID> ownerByPos = new HashMap<>();

    /** No trails. */
    public TrailFires() {}

    /**
     * Rebuilds the ledger from saved entries; empty trails are dropped. If a corrupt save lists an owner twice, the
     * last non-empty trail wins and only its flames are indexed.
     *
     * @param entries the saved trails
     */
    public TrailFires(List<Entry> entries) {
        for (Entry entry : entries) {
            Trail trail = new Trail(entry.flames(), entry.countdown());
            if (!trail.isEmpty()) {
                byOwner.put(entry.owner(), trail);
            }
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
     * Whether the ledger holds no trails (an owner with no flames is never kept).
     *
     * @return {@code true} if there is nothing to save
     */
    public boolean isEmpty() {
        return byOwner.isEmpty();
    }

    /** Who owns the flame at {@code pos}, if the ledger knows one. */
    public Optional<UUID> ownerOf(BlockPos pos) {
        return Optional.ofNullable(ownerByPos.get(pos.asLong()));
    }

    /**
     * Lights {@code pos} for {@code owner} if the spot allows it (spec §4): air, or a replaceable block with no fluid
     * that is not fire, over a block with a sturdy top face. Then trims the owner's trail to the configured maximum.
     *
     * @return whether a flame was lit
     */
    public boolean light(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        if (!canLight(level, pos)) {
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

    /** One fade tick for every trail in the level; trails that empty are forgotten. */
    public void fadeTick(ServerLevel level) {
        Iterator<Map.Entry<UUID, Trail>> trails = byOwner.entrySet().iterator();
        while (trails.hasNext()) {
            Map.Entry<UUID, Trail> ownerTrail = trails.next();
            ownerTrail.getValue().tick().ifPresent(flame -> extinguish(level, ownerTrail.getKey(), flame));
            if (ownerTrail.getValue().isEmpty()) {
                trails.remove();
            }
        }
    }

    /**
     * Puts out {@code owner}'s flame. If the spot has since been relit by someone else (the first flame was put out
     * by hand or water, and another owner walked there), that newer flame is theirs and stays. Accepted limit (spec
     * §4): if the same owner relit the spot, their trail lists it twice and the older entry puts the new flame out
     * early.
     */
    private void extinguish(ServerLevel level, UUID owner, Trail.Flame flame) {
        if (!owner.equals(ownerByPos.get(flame.pos()))) {
            return;
        }
        ownerByPos.remove(flame.pos());
        BlockPos pos = BlockPos.of(flame.pos());
        if (level.getBlockState(pos).is(ModBlocks.TRAIL_FIRE.get())) {
            level.removeBlock(pos, false);
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
