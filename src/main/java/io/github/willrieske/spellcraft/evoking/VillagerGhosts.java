package io.github.willrieske.spellcraft.evoking;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.rules.CasterRules;
import io.github.willrieske.spellcraft.spell.rules.GhostNeed;
import io.github.willrieske.spellcraft.spell.rules.GhostRecords;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/**
 * The villager-ghost records of one level (Parts 6-7 spec §9): where a villager died to a monster, what it looked like
 * and when its two spells may be cast again. At most {@link GhostRecords#MAX} are kept; a new one drops the oldest.
 * Saved with the level; a ghost is the visit, the record is the memory. The spawner sends a {@link VillagerGhost}
 * from a record to a player in need.
 *
 * <p>Functional spec §16.1, §16.2, §16.5.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VillagerGhosts extends SavedData {
    /** One note: a UUID, the death block, the villager's type, profession and level, and the two cooldown ends. */
    // The spec and the plan name this type Record; it shadows java.lang.Record only inside this class's API.
    @SuppressWarnings("AvoidCommonTypeNames")
    public record Record(UUID id, BlockPos pos, VillagerData data, long mirrorReadyAt, long ghostrailReadyAt) {
        /** The save format of one record. */
        private static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
                        UUIDUtil.CODEC.fieldOf("id").forGetter(Record::id),
                        BlockPos.CODEC.fieldOf("pos").forGetter(Record::pos),
                        VillagerData.CODEC.fieldOf("data").forGetter(Record::data),
                        Codec.LONG.fieldOf("mirror_ready").forGetter(Record::mirrorReadyAt),
                        Codec.LONG.fieldOf("ghostrail_ready").forGetter(Record::ghostrailReadyAt))
                .apply(i, Record::new));

        /**
         * Whether the ghost of this record could cast a spell at game time {@code now}: its Mirror or its Ghostrail
         * cooldown is over (the same {@link CasterRules#ready} test the ghost's own cast uses).
         *
         * @param now the level's game time
         * @return true when at least one of the two spells is ready
         */
        boolean hasReadySpell(long now) {
            return CasterRules.ready(now, mirrorReadyAt) || CasterRules.ready(now, ghostrailReadyAt);
        }

        /**
         * This record with a new Mirror cooldown end.
         *
         * @param time the game time the cooldown ends
         * @return the changed record
         */
        Record withMirrorReadyAt(long time) {
            return new Record(id, pos, data, time, ghostrailReadyAt);
        }

        /**
         * This record with a new Ghostrail cooldown end.
         *
         * @param time the game time the cooldown ends
         * @return the changed record
         */
        Record withGhostrailReadyAt(long time) {
            return new Record(id, pos, data, mirrorReadyAt, time);
        }
    }

    /** A record is only considered within this many blocks of the player. Functional spec §16.2. */
    static final double RANGE = 48.0;

    /** Ticks between two checks of each player. Functional spec §16.2. */
    private static final int STEP_INTERVAL = 20;

    /** Logs the records a load skips. */
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The records as a list: saved as a plain list, loaded leniently. A record that no longer decodes (a profession
     * or villager type that a removed mod added) is logged and skipped, so it does not take the others with it:
     * vanilla reads a saved-data file that fails to parse as empty, and the next save would overwrite the file.
     */
    private static final Codec<List<Record>> LENIENT_RECORDS = Codec.of(
            Record.CODEC.listOf(),
            Codec.PASSTHROUGH
                    .listOf()
                    .map(entries -> entries.stream()
                            .flatMap(entry -> Record.CODEC
                                    .parse(entry)
                                    .resultOrPartial(
                                            error -> LOGGER.warn("Skipping a villager ghost record: {}", error))
                                    .stream())
                            .toList()),
            "LenientRecords");

    /** The save format: the list, oldest first. */
    public static final Codec<VillagerGhosts> CODEC = RecordCodecBuilder.create(
            i -> i.group(LENIENT_RECORDS.fieldOf("records").forGetter(ghosts -> ghosts.records))
                    .apply(i, VillagerGhosts::new));

    /** The saved-data type; one file per level. Functional spec §16.1, §16.5. */
    public static final SavedDataType<VillagerGhosts> TYPE =
            new SavedDataType<>(Spellcraft.id("villager_ghosts"), VillagerGhosts::new, CODEC);

    /** The kept records, oldest first; replaced (never edited) on every change. */
    private List<Record> records;

    /** An empty list: the type's factory for a level with no saved file. */
    private VillagerGhosts() {
        this(List.of());
    }

    /**
     * A list with the given records (the codec's constructor).
     *
     * @param records the records, oldest first
     */
    private VillagerGhosts(List<Record> records) {
        this.records = List.copyOf(records);
    }

    /**
     * The records of {@code level}.
     *
     * @param level the level
     * @return its records
     */
    public static VillagerGhosts get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * The records, oldest first.
     *
     * @return the kept records
     */
    public List<Record> records() {
        return records;
    }

    /**
     * Adds a record, dropping the oldest past {@link GhostRecords#MAX}. Functional spec §16.1.
     *
     * @param record the new record
     */
    public void add(Record record) {
        records = GhostRecords.add(records, record, GhostRecords.MAX);
        setDirty();
    }

    /**
     * The record with {@code id}, if it is still kept.
     *
     * @param id the record's id
     * @return the record
     */
    public Optional<Record> find(UUID id) {
        return records.stream().filter(record -> record.id().equals(id)).findFirst();
    }

    /**
     * Forgets the record with {@code id} (tests; nothing in play removes one).
     *
     * @param id the record's id
     */
    public void remove(UUID id) {
        records = records.stream().filter(record -> !record.id().equals(id)).toList();
        setDirty();
    }

    /**
     * Sets the game time the record's Mirror cooldown ends.
     *
     * @param id the record's id
     * @param time the game time
     */
    public void setMirrorReadyAt(UUID id, long time) {
        replace(id, record -> record.withMirrorReadyAt(time));
    }

    /**
     * Sets the game time the record's Ghostrail cooldown ends.
     *
     * @param id the record's id
     * @param time the game time
     */
    public void setGhostrailReadyAt(UUID id, long time) {
        replace(id, record -> record.withGhostrailReadyAt(time));
    }

    /** Replaces the record with {@code id} by {@code change} of it and marks the data dirty. */
    private void replace(UUID id, UnaryOperator<Record> change) {
        records = records.stream()
                .map(record -> record.id().equals(id) ? change.apply(record) : record)
                .toList();
        setDirty();
    }

    /**
     * A villager that dies to a monster (an {@code Enemy} that is not on a player's side) leaves a record. The event
     * fires before a zombie's conversion of the villager, which is why a villager a zombie then converts still leaves
     * one. Functional spec §16.1.
     *
     * @param event the death event for any living entity
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (event.getEntity() instanceof Villager villager
                && villager.level() instanceof ServerLevel level
                && source.getEntity() instanceof Enemy
                && !Allies.isPlayersSide(source)) {
            get(level).add(new Record(UUID.randomUUID(), villager.blockPosition(), villager.getVillagerData(), 0L, 0L));
        }
    }

    /**
     * Runs {@link #step} every 20 ticks of each player's {@code tickCount}.
     *
     * @param event the player's post-tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % STEP_INTERVAL == 0) {
            step(player);
        }
    }

    /**
     * Sends a ghost to {@code player} when they are in need and an enemy is near (spec §9). The conditions run
     * cheapest first: the player's own state, then one query for the hostiles within {@link CasterRules#RANGE} (none:
     * no ghost, because the ghost's own "still needed" test is the same query, so a ghost sent for a hurt player with
     * no enemy would puff away at once and be sent again), then the need, and only then the level-wide scans: one
     * for a ghost already helping and, when a loaded record is in range, one for the records already out. Public so
     * GameTests step a mock player, which gets no player tick. A record is a candidate when it is within
     * {@link #RANGE} blocks and its chunk ticks entities (a ghost added to a chunk that does not tick would never
     * move or fade); among the candidates with no ghost out, the nearest one with a ready spell is chosen, and only
     * when none is ready the nearest.
     *
     * <p>Functional spec §16.2.
     *
     * @param player a player
     */
    public static void step(ServerPlayer player) {
        ServerLevel level = player.level();
        if (!player.isAlive() || player.isSpectator() || !player.hasEffect(ModEffects.EVOKING)) {
            return;
        }
        List<LivingEntity> hostiles = hostilesWithin(level, player, CasterRules.RANGE);
        if (hostiles.isEmpty()) {
            return;
        }
        int crowd = (int) hostiles.stream()
                .filter(hostile -> hostile.distanceTo(player) <= GhostNeed.CROWD_RANGE)
                .count();
        if (!GhostNeed.inNeed(player.getHealth(), player.getMaxHealth(), crowd) || helping(level, player)) {
            return;
        }
        List<Record> near = get(level).records().stream()
                .filter(record -> record.pos().distToCenterSqr(player.position()) <= RANGE * RANGE
                        && level.isPositionEntityTicking(record.pos()))
                .toList();
        if (near.isEmpty()) {
            return;
        }
        Set<UUID> out = recordsWithLiveGhost(level);
        List<Record> free =
                near.stream().filter(record -> !out.contains(record.id())).toList();
        long now = level.getGameTime();
        Comparator<Record> nearest =
                Comparator.comparingDouble(record -> record.pos().distToCenterSqr(player.position()));
        free.stream()
                .filter(record -> record.hasReadySpell(now))
                .min(nearest)
                .or(() -> free.stream().min(nearest))
                .ifPresent(record -> spawn(level, record, player));
    }

    /**
     * The hostiles near a player: living, within {@code range} blocks, {@code huntable} and {@code isPrey} for that
     * player. The one definition of "a hostile near the player", used by {@link #step} (with
     * {@link CasterRules#RANGE}, then filtered to {@link GhostNeed#CROWD_RANGE} for the crowd) and by the ghost's fade
     * check.
     *
     * @param level the level
     * @param player the player
     * @param range the radius in blocks
     * @return the hostiles, in query order
     */
    static List<LivingEntity> hostilesWithin(ServerLevel level, ServerPlayer player, double range) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(range),
                other -> other.isAlive()
                        && other.distanceTo(player) <= range
                        && BoundVex.huntable(player.getUUID(), other)
                        && BoundVex.isPrey(level, player.getUUID(), other));
    }

    /** Whether a live ghost is already helping {@code player}. */
    private static boolean helping(ServerLevel level, ServerPlayer player) {
        return !level.getEntities(
                        EntityTypeTest.forClass(VillagerGhost.class),
                        ghost -> ghost.isAlive()
                                && ghost.helpedId()
                                        .filter(player.getUUID()::equals)
                                        .isPresent())
                .isEmpty();
    }

    /** The ids of the records a live ghost is already out for, from one scan of the level's ghosts. */
    private static Set<UUID> recordsWithLiveGhost(ServerLevel level) {
        Set<UUID> ids = new HashSet<>();
        level.getEntities(EntityTypeTest.forClass(VillagerGhost.class), Entity::isAlive)
                .forEach(ghost -> ghost.recordId().ifPresent(ids::add));
        return ids;
    }

    /**
     * Sends a ghost out from {@code record} to {@code player}. The order matters: position the ghost first, then bind
     * it (its Ghostrail probes the block at its position for a start spot; an unpositioned entity sits at (0, 0, 0) and
     * the probe would load that chunk), then add it to the level.
     */
    private static void spawn(ServerLevel level, Record record, ServerPlayer player) {
        VillagerGhost ghost = ModEntities.VILLAGER_GHOST.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (ghost == null) {
            return;
        }
        ghost.snapTo(
                record.pos().getX() + 0.5, record.pos().getY(), record.pos().getZ() + 0.5, 0.0F, 0.0F);
        ghost.bind(record, player);
        level.addFreshEntity(ghost);
    }
}
