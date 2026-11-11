package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.at;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.foeAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.hostileAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.sampleVillagerData;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.VillagerGhosts;
import io.github.willrieske.spellcraft.spell.rules.GhostRecords;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.phys.AABB;

/** GameTests of the villager-ghost records (Parts 6-7 spec §9, §10) and, from Task 12, the ghosts. */
final class VillagerGhostGameTests {
    /** The environment of the 33-deaths test: it fills the whole record list. */
    private static final String RECORDS = "villager_ghost_records";

    /** The environment of the conversion test: it raises the difficulty to Hard (a global setting). */
    private static final String HARD = "villager_ghost_hard";

    private VillagerGhostGameTests() {}

    /** Registers every villager-ghost record GameTest. */
    static void registerAll() {
        FangGameTests.addPadded(
                SHARED,
                "villager_ghost_record_a_pillager_kill_writes_a_record",
                VillagerGhostGameTests::pillagerKillWritesARecord);
        FangGameTests.addPadded(
                SHARED, "villager_ghost_record_keeps_the_villagers_data", VillagerGhostGameTests::recordKeepsTheData);
        FangGameTests.addPadded(
                SHARED, "villager_ghost_record_none_for_a_player_kill", VillagerGhostGameTests::playerKillWritesNone);
        FangGameTests.addPadded(
                SHARED,
                "villager_ghost_record_none_for_a_bound_vex_kill",
                VillagerGhostGameTests::boundVexKillWritesNone);
        FangGameTests.addPadded(
                SHARED, "villager_ghost_record_none_for_a_golem_kill", VillagerGhostGameTests::golemKillWritesNone);
        FangGameTests.addPadded(SHARED, "villager_ghost_record_none_for_lava", VillagerGhostGameTests::lavaWritesNone);
        FangGameTests.addPadded(
                SHARED, "villager_ghost_record_none_for_a_fall", VillagerGhostGameTests::fallWritesNone);
        FangGameTests.addPadded(
                SHARED, "villager_ghost_records_survive_a_save", VillagerGhostGameTests::recordsSurviveASave);
        FangGameTests.addPadded(
                RECORDS, "villager_ghost_records_33_deaths_keep_32", 400, VillagerGhostGameTests::thirtyThreeDeaths);
        FangGameTests.addPadded(
                HARD,
                "villager_ghost_record_survives_a_zombie_conversion",
                VillagerGhostGameTests::conversionStillLeavesARecord);
    }

    /**
     * Kills {@code villager} with {@code source} (a hit far above its health) and asserts that it died, so a kill that
     * failed cannot make a "writes none" test pass silently.
     *
     * @param helper the test helper
     * @param villager the villager to kill
     * @param source the damage source
     */
    private static void kill(GameTestHelper helper, Villager villager, DamageSource source) {
        villager.hurtServer(helper.getLevel(), source, 1000.0F);
        helper.assertTrue(villager.isDeadOrDying(), "the villager died");
    }

    /**
     * The record at {@code pos}, if any.
     *
     * @param helper the test helper
     * @param pos the block position
     * @return the record there
     */
    private static Optional<VillagerGhosts.Record> recordAt(GameTestHelper helper, BlockPos pos) {
        return VillagerGhosts.get(helper.getLevel()).records().stream()
                .filter(record -> record.pos().equals(pos))
                .findFirst();
    }

    /**
     * A no-AI villager of the non-default sample data in a test-owned spot, which removes the record at its spot when
     * the test ends.
     *
     * @param helper the test helper
     * @return the villager
     */
    private static Villager villager(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        villager.setNoAi(true);
        villager.setVillagerData(sampleVillagerData(helper));
        BlockPos pos = villager.blockPosition();
        ServerLevel level = helper.getLevel();
        whenDone(helper, () -> {
            villager.discard();
            recordAt(helper, pos).ifPresent(record -> VillagerGhosts.get(level).remove(record.id()));
        });
        return villager;
    }

    /**
     * When a pillager (an Enemy that is not a Zombie) kills a villager, should a record of the death be written there.
     *
     * <p>Method under test: {@code VillagerGhosts#onDeath}
     */
    static void pillagerKillWritesARecord(GameTestHelper helper) {
        // Arrange (the padded wrapper has already loaded the box; no player is needed)
        ServerLevel level = helper.getLevel();
        Pillager pillager = foeAt(helper, EntityTypes.PILLAGER, at(helper, 3.0, 0.0, 0.0));
        Villager villager = villager(helper);
        BlockPos pos = villager.blockPosition();

        // Act
        kill(helper, villager, level.damageSources().mobAttack(pillager));

        // Assert
        helper.assertTrue(recordAt(helper, pos).isPresent(), "a record at the death spot");
        helper.succeed();
    }

    /**
     * When a pillager kills a villager of non-default type, profession and level, should the record keep that data.
     *
     * <p>Method under test: {@code VillagerGhosts#onDeath}
     */
    static void recordKeepsTheData(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        Pillager pillager = foeAt(helper, EntityTypes.PILLAGER, at(helper, 3.0, 0.0, 0.0));
        Villager villager = villager(helper);
        BlockPos pos = villager.blockPosition();

        // Act
        kill(helper, villager, level.damageSources().mobAttack(pillager));

        // Assert
        helper.assertTrue(
                recordAt(helper, pos).orElseThrow().data().equals(sampleVillagerData(helper)),
                "the record keeps the type, profession and level");
        helper.succeed();
    }

    /**
     * When a player kills a villager, should no record be written.
     *
     * <p>Method under test: {@code VillagerGhosts#onDeath}
     */
    static void playerKillWritesNone(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer player = plainPlayer(helper);
        Villager villager = villager(helper);
        BlockPos pos = villager.blockPosition();

        // Act
        kill(helper, villager, level.damageSources().playerAttack(player));

        // Assert
        helper.assertTrue(recordAt(helper, pos).isEmpty(), "no record for a player's kill");
        helper.succeed();
    }

    /**
     * When a Bound Vex (an Enemy that carries a summoner mark) kills a villager, should no record be written.
     *
     * <p>Method under test: {@code Allies#isPlayersSide} through {@code VillagerGhosts#onDeath}
     */
    static void boundVexKillWritesNone(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer player = plainPlayer(helper);
        BoundVex vex = BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player);
        Villager villager = villager(helper);
        BlockPos pos = villager.blockPosition();

        // Act (the Bound Vex's friendly-fire backstop cancels a hit on a villager, so the villager never dies of one;
        // the death is delivered directly to prove the death handler's own side test)
        villager.die(level.damageSources().mobAttack(vex));

        // Assert
        helper.assertTrue(villager.isDeadOrDying(), "the villager died");
        helper.assertTrue(recordAt(helper, pos).isEmpty(), "no record for a Bound Vex's kill");
        helper.succeed();
    }

    /**
     * When an iron golem kills a villager, should no record be written.
     *
     * <p>Method under test: {@code VillagerGhosts#onDeath}
     */
    static void golemKillWritesNone(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        IronGolem golem = foeAt(helper, EntityTypes.IRON_GOLEM, at(helper, 3.0, 0.0, 0.0));
        Villager villager = villager(helper);
        BlockPos pos = villager.blockPosition();

        // Act
        kill(helper, villager, level.damageSources().mobAttack(golem));

        // Assert
        helper.assertTrue(recordAt(helper, pos).isEmpty(), "no record for a golem's kill");
        helper.succeed();
    }

    /**
     * When lava kills a villager, should no record be written.
     *
     * <p>Method under test: {@code VillagerGhosts#onDeath}
     */
    static void lavaWritesNone(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        Villager villager = villager(helper);
        BlockPos pos = villager.blockPosition();

        // Act
        kill(helper, villager, level.damageSources().lava());

        // Assert
        helper.assertTrue(recordAt(helper, pos).isEmpty(), "no record for lava");
        helper.succeed();
    }

    /**
     * When a fall kills a villager, should no record be written.
     *
     * <p>Method under test: {@code VillagerGhosts#onDeath}
     */
    static void fallWritesNone(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        Villager villager = villager(helper);
        BlockPos pos = villager.blockPosition();

        // Act
        kill(helper, villager, level.damageSources().fall());

        // Assert
        helper.assertTrue(recordAt(helper, pos).isEmpty(), "no record for a fall");
        helper.succeed();
    }

    /**
     * When the records are encoded and decoded, should the same records come back, oldest first.
     *
     * <p>Method under test: {@code VillagerGhosts#CODEC}
     */
    static void recordsSurviveASave(GameTestHelper helper) {
        // Arrange
        VillagerData data = sampleVillagerData(helper);
        VillagerGhosts ghosts = VillagerGhosts.get(helper.getLevel());
        VillagerGhosts.Record record =
                new VillagerGhosts.Record(UUID.randomUUID(), new BlockPos(7, 70, 9), data, 123L, 456L);
        ghosts.add(record);
        whenDone(helper, () -> ghosts.remove(record.id()));

        // Act
        Tag encoded = VillagerGhosts.CODEC.encodeStart(NbtOps.INSTANCE, ghosts).getOrThrow();
        VillagerGhosts decoded =
                VillagerGhosts.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        // Assert
        helper.assertTrue(decoded.find(record.id()).filter(record::equals).isPresent(), "the record comes back whole");
        helper.succeed();
    }

    /**
     * When 33 villagers die to a pillager, should the oldest record be dropped and the other 32 kept, the newest
     * included.
     *
     * <p>Method under test: {@code VillagerGhosts#add}
     */
    static void thirtyThreeDeaths(GameTestHelper helper) {
        // Arrange (every villager spawns at one block, so a lookup by position would find the oldest record: the
        // new record is read from the tail of the list instead)
        ServerLevel level = helper.getLevel();
        VillagerGhosts ghosts = VillagerGhosts.get(level);
        Pillager pillager = foeAt(helper, EntityTypes.PILLAGER, at(helper, 3.0, 0.0, 0.0));
        List<UUID> ids = new ArrayList<>();
        whenDone(helper, () -> ids.forEach(ghosts::remove));

        // Act
        for (int i = 0; i < GhostRecords.MAX + 1; i++) {
            Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
            villager.setNoAi(true);
            villager.setVillagerData(sampleVillagerData(helper));
            kill(helper, villager, level.damageSources().mobAttack(pillager));
            villager.discard();
            List<VillagerGhosts.Record> records = ghosts.records();
            ids.add(records.get(records.size() - 1).id());
        }

        // Assert
        helper.assertTrue(ghosts.records().size() == GhostRecords.MAX, "the list is capped");
        helper.assertTrue(ghosts.find(ids.get(0)).isEmpty(), "the oldest record is dropped");
        helper.assertTrue(ghosts.find(ids.get(1)).isPresent(), "the second record is now the oldest kept");
        helper.assertTrue(ghosts.find(ids.get(ids.size() - 1)).isPresent(), "the newest record is kept");
        helper.succeed();
    }

    /**
     * When a husk kills a villager on Hard (which always converts it to a zombie villager), should a record still be
     * left, because the death event fires before the conversion.
     *
     * <p>Method under test: {@code VillagerGhosts#onDeath}
     *
     * <p>Sets the server difficulty, which is global, so it runs alone in the {@code villager_ghost_hard} batch; the
     * difficulty is restored through {@code whenDone}.
     */
    static void conversionStillLeavesARecord(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Difficulty saved = level.getDifficulty();
        whenDone(helper, () -> server.setDifficulty(saved, true));
        server.setDifficulty(Difficulty.HARD, true);
        Husk husk = hostileAt(helper, at(helper, 3.0, 0.0, 0.0));
        Villager villager = villager(helper);
        BlockPos pos = villager.blockPosition();
        AABB around = new AABB(pos).inflate(4.0);
        whenDone(
                helper,
                () -> level.getEntitiesOfClass(ZombieVillager.class, around).forEach(Entity::discard));

        // Act
        kill(helper, villager, level.damageSources().mobAttack(husk));

        // Assert
        helper.assertTrue(
                !level.getEntitiesOfClass(ZombieVillager.class, around).isEmpty(), "the husk converted the villager");
        helper.assertTrue(recordAt(helper, pos).isPresent(), "a record at the death spot");
        helper.succeed();
    }
}
