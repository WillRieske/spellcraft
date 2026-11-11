package io.github.willrieske.spellcraft.evoking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.rules.GhostRecords;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The villager-ghost records of one level (Parts 6-7 spec §9): where a villager died to a monster, what it looked like
 * and when its two spells may be cast again. At most {@link GhostRecords#MAX} are kept; a new one drops the oldest.
 * Saved with the level; a ghost is the visit, the record is the memory. Task 12 adds the spawner.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VillagerGhosts extends SavedData {
    // The spec and the plan name this type Record; it shadows java.lang.Record only inside this class's API.
    /** One note: a UUID, the death block, the villager's type, profession and level, and the two cooldown ends. */
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
         * This record with a new Mirror cooldown end.
         *
         * @param time the game time the cooldown ends
         * @return the changed record
         */
        public Record withMirrorReadyAt(long time) {
            return new Record(id, pos, data, time, ghostrailReadyAt);
        }

        /**
         * This record with a new Ghostrail cooldown end.
         *
         * @param time the game time the cooldown ends
         * @return the changed record
         */
        public Record withGhostrailReadyAt(long time) {
            return new Record(id, pos, data, mirrorReadyAt, time);
        }
    }

    /** The save format: the list, oldest first. */
    public static final Codec<VillagerGhosts> CODEC = RecordCodecBuilder.create(
            i -> i.group(Record.CODEC.listOf().fieldOf("records").forGetter(ghosts -> ghosts.records))
                    .apply(i, VillagerGhosts::new));

    /** The saved-data type; one file per level. */
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
     * Adds a record, dropping the oldest past {@link GhostRecords#MAX}.
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
     * one.
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
}
