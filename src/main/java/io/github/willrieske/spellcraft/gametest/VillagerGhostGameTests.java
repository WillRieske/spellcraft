package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.at;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.floor;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.foeAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.hostileAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.sampleVillagerData;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.EvokingCaster;
import io.github.willrieske.spellcraft.evoking.EvokingCaster.Request;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.evoking.VillagerGhost;
import io.github.willrieske.spellcraft.evoking.VillagerGhosts;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.GhostRecords;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * GameTests of the villager-ghost records (Parts 6-7 spec §9, §10) and, from Task 12, the ghosts.
 *
 * <p>Functional spec §14.8, §16.1, §16.2, §16.3, §16.4, §16.5.
 */
final class VillagerGhostGameTests {
    /** The environment of the 33-deaths test: it fills the whole record list. */
    private static final String RECORDS = "villager_ghost_records";

    /** The environment of the conversion test: it raises the difficulty to Hard (a global setting). */
    private static final String HARD = "villager_ghost_hard";

    /** The environment of the help tests: they fill the record list and run beside each other. */
    private static final String HELP = "villager_ghost_help";

    /** The environment of the fade test: the 200-tick quiet rule counts any hostile within 16 blocks. */
    private static final String FADE = "villager_ghost_fade";

    /**
     * The environments of the range tests, one each: they force-load a chunk far from the others, and a record
     * selection is level-wide (48-block reach), so a record of one must not sit in range of another test's player.
     */
    private static final String RANGE_FAR = "villager_ghost_range_far";

    /** The environment of the 46-block test; see {@link #RANGE_FAR}. */
    private static final String RANGE_NEAR = "villager_ghost_range_near";

    /** The environment of the 50-block test; see {@link #RANGE_FAR}. */
    private static final String RANGE_EDGE = "villager_ghost_range_edge";

    /**
     * The environment of the fade-timing test: a husk stays near the player for about 400 ticks, so no other test may
     * share it (the quiet rule counts any hostile within 16 blocks).
     */
    private static final String FADE_TIMING = "villager_ghost_fade_timing";

    /** The environment of the ready-beats-nearest test: record selection is level-wide, so it runs alone. */
    private static final String READY_FIRST = "villager_ghost_ready_first";

    /** The environment of the spent-record test: with another ready record in range the ghost would go there. */
    private static final String SPENT = "villager_ghost_spent";

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
        registerGhostTests();
    }

    /** Registers the GameTests of the ghost itself (Task 12); each runs up to 400 ticks, a ghost takes up to 250. */
    private static void registerGhostTests() {
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_appears_for_a_player_in_need_near_a_record",
                400,
                VillagerGhostGameTests::appears);
        FangGameTests.addPadded(
                HELP, "villager_ghost_takes_the_villagers_look", 400, VillagerGhostGameTests::takesTheLook);
        FangGameTests.addPadded(
                HELP, "villager_ghost_flies_to_the_player_and_casts_mirror", 400, VillagerGhostGameTests::castsMirror);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_casts_ghostrail_first_when_both_apply",
                400,
                VillagerGhostGameTests::ghostrailFirst);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_casts_no_ghostrail_for_a_creative_player",
                400,
                VillagerGhostGameTests::noGhostrailForCreative);
        FangGameTests.addPadded(
                FADE,
                "villager_ghost_fades_200_ticks_after_the_last_hostile_is_gone",
                400,
                VillagerGhostGameTests::fades);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_is_not_sent_to_a_player_not_in_need",
                400,
                VillagerGhostGameTests::notForTheUnhurt);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_is_not_sent_to_a_hurt_player_with_no_hostile_near",
                400,
                VillagerGhostGameTests::notForAHurtPlayerWithNoHostile);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_is_not_sent_to_a_player_without_evoking",
                400,
                VillagerGhostGameTests::notWithoutEvoking);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_a_second_record_sends_no_second_ghost",
                400,
                VillagerGhostGameTests::oneGhostPerPlayer);
        FangGameTests.addPadded(
                RANGE_FAR,
                "villager_ghost_is_not_sent_for_a_record_beyond_48_blocks",
                400,
                VillagerGhostGameTests::tooFar);
        FangGameTests.addPadded(
                RANGE_NEAR,
                "villager_ghost_is_sent_for_a_record_46_blocks_away",
                400,
                VillagerGhostGameTests::fortySixBlocksAway);
        FangGameTests.addPadded(
                RANGE_EDGE,
                "villager_ghost_is_not_sent_for_a_record_50_blocks_away",
                400,
                VillagerGhostGameTests::fiftyBlocksAway);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_casts_no_ghostrail_when_the_player_turns_creative_in_the_wind_up",
                400,
                VillagerGhostGameTests::creativeDuringWindUp);
        FangGameTests.addPadded(HELP, "villager_ghost_cannot_be_hurt", 400, VillagerGhostGameTests::cannotBeHurt);
        FangGameTests.addPadded(
                HELP, "villager_ghost_is_no_target_for_fangs", 400, VillagerGhostGameTests::fangsIgnoreTheGhost);
        FangGameTests.addPadded(
                HELP, "villager_ghost_without_a_record_discards_itself", 400, VillagerGhostGameTests::noRecordNoGhost);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_whose_record_was_dropped_discards_itself",
                400,
                VillagerGhostGameTests::droppedRecord);
        registerSelectionTests();
    }

    /** Registers the GameTests of the record selection, the need rules and the fade conditions (branch review). */
    private static void registerSelectionTests() {
        FangGameTests.addPadded(
                SHARED,
                "villager_ghost_records_load_leniently_past_one_bad_record",
                VillagerGhostGameTests::loadsLeniently);
        FangGameTests.addPadded(
                HELP, "villager_ghost_is_sent_for_the_nearest_record", 400, VillagerGhostGameTests::nearestRecordWins);
        FangGameTests.addPadded(
                READY_FIRST,
                "villager_ghost_is_sent_for_a_ready_record_before_a_nearer_spent_one",
                400,
                VillagerGhostGameTests::readyBeatsNearest);
        FangGameTests.addPadded(
                SPENT,
                "villager_ghost_for_a_spent_record_comes_and_casts_nothing",
                400,
                VillagerGhostGameTests::spentRecordCastsNothing);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_a_record_with_a_live_ghost_goes_to_the_next_player",
                400,
                VillagerGhostGameTests::liveGhostRecordSkipped);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_is_sent_to_a_healthy_player_among_three_hostiles_within_8_blocks",
                400,
                VillagerGhostGameTests::crowdComes);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_is_not_sent_to_a_healthy_player_with_only_two_hostiles_within_8_blocks",
                400,
                VillagerGhostGameTests::crowdNeedsThreeWithin8);
        FangGameTests.addPadded(
                FADE_TIMING,
                "villager_ghost_stays_through_the_quiet_period_and_a_returning_hostile_resets_it",
                800,
                VillagerGhostGameTests::fadeTiming);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_carries_an_infinite_ghostrail",
                400,
                VillagerGhostGameTests::carriesInfiniteGhostrail);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_fades_when_the_helped_player_turns_spectator",
                400,
                VillagerGhostGameTests::fadesForSpectator);
        FangGameTests.addPadded(
                HELP, "villager_ghost_fades_when_the_helped_player_dies", 400, VillagerGhostGameTests::fadesForDeath);
        FangGameTests.addPadded(
                HELP,
                "villager_ghost_player_tick_event_steps_on_tick_20_and_not_21",
                400,
                VillagerGhostGameTests::tickGate);
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
     *
     * <p>Functional spec §16.1.
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
     *
     * <p>Functional spec §16.3.
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
     *
     * <p>Functional spec §16.1.
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
     *
     * <p>Functional spec §16.1.
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
     *
     * <p>Functional spec §16.1.
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
     *
     * <p>Functional spec §16.1.
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
     *
     * <p>Functional spec §16.1.
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
     *
     * <p>Functional spec §16.1.
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
     *
     * <p>Functional spec §16.1.
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
     *
     * <p>Functional spec §16.1.
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

    /**
     * The ghost scene's player, its record (null when not asked for) and its husk (null when not asked for).
     *
     * @param player a survival player with Evoking at 40 % health
     * @param record the record 10 blocks east, or null
     * @param husk the frozen husk 3 blocks east, or null
     */
    private record Scene(
            ServerPlayer player,
            VillagerGhosts.@Nullable Record record,
            @Nullable Husk husk) {}

    /**
     * A player with Evoking at 40 % health, optionally a frozen husk 3 blocks away (a hostile within the ghost's 16
     * blocks: without one no ghost is sent, and with one it does not fade) and optionally a record 10 blocks east. A
     * test that needs the husk gone discards it.
     *
     * @param helper the test helper
     * @param husk whether to place the husk
     * @param record whether to add the record
     * @return the scene
     */
    private static Scene scene(GameTestHelper helper, boolean husk, boolean record) {
        ServerPlayer player = FangGameTests.caster(helper, FangGameTests.NORTH);
        floor(helper, 12);
        player.setHealth(player.getMaxHealth() * 0.4F);
        Husk hostile = husk ? hostileAt(helper, at(helper, 3.0, 0.0, 0.0)) : null;
        VillagerGhosts.Record near = record ? ScrollTestSupport.record(helper, at(helper, 10.0, 0.0, 0.0)) : null;
        return new Scene(player, near, hostile);
    }

    /**
     * The live ghosts for {@code record} in the test level.
     *
     * @param helper the test helper
     * @param record the record
     * @return its live ghosts
     */
    private static List<VillagerGhost> ghosts(GameTestHelper helper, VillagerGhosts.Record record) {
        return helper
                .getLevel()
                .getEntities(
                        EntityTypeTest.forClass(VillagerGhost.class),
                        ghost -> ghost.isAlive()
                                && ghost.recordId().filter(record.id()::equals).isPresent())
                .stream()
                .map(VillagerGhost.class::cast)
                .toList();
    }

    /**
     * The record of {@code scene} as it is saved now.
     *
     * @param helper the test helper
     * @param scene the scene (with a record)
     * @return the saved record
     */
    private static VillagerGhosts.Record saved(GameTestHelper helper, Scene scene) {
        return VillagerGhosts.get(helper.getLevel()).find(scene.record().id()).orElseThrow();
    }

    /**
     * When a player in need has a record 10 blocks away, should one step spawn a ghost at the record helping that
     * player.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void appears(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        List<VillagerGhost> ghosts = ghosts(helper, scene.record());
        helper.assertValueEqual(ghosts.size(), 1, "one ghost");
        helper.assertTrue(
                ghosts.get(0)
                        .helpedId()
                        .filter(scene.player().getUUID()::equals)
                        .isPresent(),
                "for this player");
        helper.assertTrue(
                ghosts.get(0).distanceToSqr(Vec3.atCenterOf(scene.record().pos())) < 4.0, "at the record");
        helper.succeed();
    }

    /**
     * When a ghost is sent for a record, should it take the villager's type, profession and level.
     *
     * <p>Method under test: {@code VillagerGhost#bind}
     *
     * <p>Functional spec §16.3.
     */
    static void takesTheLook(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        List<VillagerGhost> ghosts = ghosts(helper, scene.record());
        helper.assertValueEqual(ghosts.size(), 1, "one ghost");
        helper.assertTrue(ghosts.get(0).villagerData().equals(scene.record().data()), "the record's look");
        helper.succeed();
    }

    /**
     * When a ghost has come to a player at 40 % health, should it cast Mirror from within 3.5 blocks and save the
     * cooldown on the record.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.3, §16.4.
     */
    static void castsMirror(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        ServerPlayer player = scene.player();

        // Act
        VillagerGhosts.step(player);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(player.hasEffect(ModEffects.MIRROR), "Mirror was cast"))
                .thenExecute(() -> {
                    VillagerGhost ghost = ghosts(helper, scene.record()).get(0);
                    helper.assertTrue(ghost.distanceTo(player) <= 3.5, "the ghost came to the player");
                    helper.assertTrue(
                            saved(helper, scene).mirrorReadyAt()
                                    > helper.getLevel().getGameTime(),
                            "the cooldown is saved on the record");
                })
                .thenSucceed();
    }

    /**
     * When the player is below a quarter of their health and Mirror applies too, should the ghost cast Ghostrail
     * first.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.4.
     */
    static void ghostrailFirst(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        ServerPlayer player = scene.player();
        player.setHealth(3.0F);

        // Act
        VillagerGhosts.step(player);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(player.hasEffect(ModEffects.GHOSTRAIL), "Ghostrail was cast"))
                .thenExecute(() -> {
                    helper.assertTrue(!player.hasEffect(ModEffects.MIRROR), "Mirror has not been cast yet");
                    helper.assertTrue(saved(helper, scene).ghostrailReadyAt() > 0L, "the cooldown is saved");
                })
                .thenSucceed();
    }

    /**
     * When the player is in creative mode (where Ghostrail is redundant), should the ghost cast Mirror and no
     * Ghostrail, leaving the record's Ghostrail cooldown untouched.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.4.
     */
    static void noGhostrailForCreative(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        ServerPlayer player = scene.player();
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(3.0F);

        // Act
        VillagerGhosts.step(player);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(player.hasEffect(ModEffects.MIRROR), "Mirror was cast"))
                .thenExecute(() -> {
                    helper.assertTrue(!player.hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail for creative");
                    helper.assertValueEqual(
                            saved(helper, scene).ghostrailReadyAt(), 0L, "Ghostrail cooldown untouched");
                })
                .thenSucceed();
    }

    /**
     * When the last hostile near the player is gone, should the ghost fade 200 ticks later and the record stay.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.5.
     */
    static void fades(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        VillagerGhosts.step(scene.player());
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);

        // Act
        scene.husk().discard();

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ghost.isRemoved(), "the ghost faded"))
                .thenExecute(() -> helper.assertTrue(
                        VillagerGhosts.get(helper.getLevel())
                                .find(scene.record().id())
                                .isPresent(),
                        "the record is kept"))
                .thenSucceed();
    }

    /**
     * When the player is at full health with one hostile near (no crowd, nothing hurt), should no ghost be sent.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void notForTheUnhurt(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        scene.player().setHealth(scene.player().getMaxHealth());

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        helper.assertTrue(ghosts(helper, scene.record()).isEmpty(), "no ghost for a player not in need");
        helper.succeed();
    }

    /**
     * When the player is hurt but no hostile is near, should no ghost be sent now or later (spawn and fade agree: a
     * ghost sent here would puff away at once and be sent again).
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void notForAHurtPlayerWithNoHostile(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, false, true);

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        helper.assertTrue(ghosts(helper, scene.record()).isEmpty(), "no ghost at once");
        helper.startSequence()
                .thenExecuteFor(100, () -> VillagerGhosts.step(scene.player()))
                .thenExecute(
                        () -> helper.assertTrue(ghosts(helper, scene.record()).isEmpty(), "none after 100 ticks"))
                .thenSucceed();
    }

    /**
     * When the player has no Evoking effect, should no ghost be sent.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void notWithoutEvoking(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        scene.player().removeEffect(ModEffects.EVOKING);

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        helper.assertTrue(ghosts(helper, scene.record()).isEmpty(), "no ghost without Evoking");
        helper.succeed();
    }

    /**
     * When two records are in range and the player is stepped twice, should exactly one ghost help them.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void oneGhostPerPlayer(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        VillagerGhosts.Record second = ScrollTestSupport.record(helper, at(helper, -10.0, 0.0, 0.0));

        // Act
        VillagerGhosts.step(scene.player());
        VillagerGhosts.step(scene.player());

        // Assert
        helper.assertValueEqual(
                ghosts(helper, scene.record()).size() + ghosts(helper, second).size(), 1, "one ghost in all");
        helper.succeed();
    }

    /**
     * A record {@code east} blocks east of the standing spot whose chunk is force-loaded (and loaded now), so that
     * only the 48-block range can exclude it from {@code step}; any ghost sent for the record is discarded when the
     * test ends.
     *
     * <p>The chunk is never unforced here. The runner unforces every forced chunk when a batch ends, and it does so
     * before this test's own end listeners run, after it has already placed the next batch and forced that batch's
     * chunks. An unforce from here could hit a chunk the next test's structure now sits in, and a test whose chunks
     * never tick never starts and never times out ({@code GameTestInfo.tick}), so the whole run would hang. Each range
     * test runs alone in its own environment, so the runner's unforce covers it.
     *
     * @param helper the test helper
     * @param east how many blocks east of the standing spot
     * @return the record
     */
    private static VillagerGhosts.Record loadedRecord(GameTestHelper helper, double east) {
        ServerLevel level = helper.getLevel();
        VillagerGhosts.Record record = ScrollTestSupport.record(helper, at(helper, east, 0.0, 0.0));
        ChunkPos chunk = ChunkPos.containing(record.pos());
        level.setChunkForced(chunk.x(), chunk.z(), true);
        level.getChunk(chunk.x(), chunk.z()); // loads the chunk now (the return value is not needed)
        whenDone(helper, () -> ghosts(helper, record).forEach(Entity::discard));
        helper.assertTrue(level.isLoaded(record.pos()), "setup: the record's chunk is loaded");
        return record;
    }

    /**
     * The range tests' body: a scene with a husk and no record of its own (with a near one, nearest-first and
     * one-ghost-per-player would pass whatever the range is), a loaded record {@code east} blocks east, then, once that
     * record's chunk has its entities loaded and ticking (a ghost added to a chunk that does not yet would not show),
     * one {@code step} and a count of the ghosts sent for the record.
     *
     * @param helper the test helper
     * @param east how many blocks east of the standing spot the record is
     * @param expected how many ghosts must have been sent for the record
     * @param message what the count means
     */
    private static void stepForRecordAt(GameTestHelper helper, double east, int expected, String message) {
        // Arrange
        Scene scene = scene(helper, true, false);
        VillagerGhosts.Record record = loadedRecord(helper, east);
        ChunkPos chunk = ChunkPos.containing(record.pos());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().areEntitiesActuallyLoadedAndTicking(chunk),
                        "setup: the record's chunk is entity-ticking"))
                // Act
                .thenExecute(() -> VillagerGhosts.step(scene.player()))
                // Assert
                .thenExecute(
                        () -> helper.assertValueEqual(ghosts(helper, record).size(), expected, message))
                .thenSucceed();
    }

    /**
     * When the only record is 60 blocks away (its chunk loaded), should no ghost be sent for it.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void tooFar(GameTestHelper helper) {
        // Arrange, Act & Assert (the shared body marks the three)
        stepForRecordAt(helper, 60.0, 0, "no ghost for a record beyond 48 blocks");
    }

    /**
     * When the only record is 46 blocks away (just inside the 48-block reach, its chunk loaded), should a ghost be
     * sent.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void fortySixBlocksAway(GameTestHelper helper) {
        // Arrange, Act & Assert (the shared body marks the three)
        stepForRecordAt(helper, 46.0, 1, "one ghost for a record within 48 blocks");
    }

    /**
     * When the only record is 50 blocks away (just outside the 48-block reach, its chunk loaded), should no ghost be
     * sent.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void fiftyBlocksAway(GameTestHelper helper) {
        // Arrange, Act & Assert (the shared body marks the three)
        stepForRecordAt(helper, 50.0, 0, "no ghost for a record 50 blocks away");
    }

    /**
     * When the player turns creative during the ghost's Ghostrail wind-up, should the Ghostrail not land (the shared
     * landing refuses a spell that is redundant in creative), and the ghost go on to cast Mirror.
     *
     * <p>Method under test: {@code ScrollCasting#land} through {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.4.
     */
    static void creativeDuringWindUp(GameTestHelper helper) {
        // Arrange (below a quarter of the health: the ghost starts a Ghostrail wind-up as soon as it is near)
        Scene scene = scene(helper, true, true);
        ServerPlayer player = scene.player();
        player.setHealth(3.0F);
        VillagerGhosts.step(player);
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);

        // Act (the wind-up starts on the tick the ghost comes within 3 blocks)
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ghost.distanceTo(player) <= 3.0, "the ghost is near"))
                .thenExecute(() -> {
                    helper.assertTrue(!player.hasEffect(ModEffects.GHOSTRAIL), "setup: no Ghostrail yet");
                    player.setGameMode(GameType.CREATIVE);
                })

                // Assert
                .thenWaitUntil(() -> helper.assertTrue(player.hasEffect(ModEffects.MIRROR), "Mirror was cast"))
                .thenExecute(() -> {
                    helper.assertTrue(!player.hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail landed");
                    helper.assertValueEqual(
                            saved(helper, scene).ghostrailReadyAt(), 0L, "Ghostrail cooldown untouched");
                })
                .thenSucceed();
    }

    /**
     * When something hurts a ghost, should it take no damage and be neither pickable nor attackable.
     *
     * <p>Method under test: {@code VillagerGhost#hurtServer}
     *
     * <p>Functional spec §16.3.
     */
    static void cannotBeHurt(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        VillagerGhosts.step(scene.player());
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);
        ServerLevel level = helper.getLevel();

        // Act
        boolean hurt = ghost.hurtServer(level, level.damageSources().generic(), 5.0F);

        // Assert
        helper.assertTrue(!hurt, "the hit is refused");
        helper.assertTrue(ghost.getHealth() == ghost.getMaxHealth(), "full health");
        helper.assertTrue(!ghost.isPickable(), "not pickable");
        helper.assertTrue(!ghost.isAttackable(), "not attackable by a player");
        helper.assertTrue(!ghost.attackable(), "not attackable by a mob");
        helper.succeed();
    }

    /**
     * When the ghost hovers by an Evoking player and is the only living thing near, should a fangs request find no
     * targets.
     *
     * <p>Method under test: {@code EvokingCaster#request} through {@code Fangs.isTarget}
     *
     * <p>Functional spec §16.3.
     */
    static void fangsIgnoreTheGhost(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        VillagerGhosts.step(scene.player());
        scene.husk().discard();
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);
        ghost.snapTo(
                scene.player().getX() + 2.0,
                scene.player().getEyeY(),
                scene.player().getZ());

        // Act
        Request request = EvokingCaster.request(scene.player(), EvokerSpell.FANGS);

        // Assert
        helper.assertValueEqual(request, Request.NO_TARGETS, "the ghost is no target");
        helper.succeed();
    }

    /**
     * When a ghost nobody sent (no record) is spawned, should it discard itself.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     */
    static void noRecordNoGhost(GameTestHelper helper) {
        // Arrange & Act
        VillagerGhost ghost = helper.spawn(ModEntities.VILLAGER_GHOST.get(), new BlockPos(1, 2, 1));

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ghost.isRemoved(), "the ghost discarded itself"))
                .thenSucceed();
    }

    /**
     * When the record of a working ghost is dropped, should the ghost discard itself within a few ticks.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     */
    static void droppedRecord(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        VillagerGhosts.step(scene.player());
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);

        // Act
        VillagerGhosts.get(helper.getLevel()).remove(scene.record().id());

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ghost.isRemoved(), "the ghost discarded itself"))
                .thenSucceed();
    }

    /**
     * The live ghosts helping {@code player}.
     *
     * @param helper the test helper
     * @param player the helped player
     * @return their ghosts
     */
    private static List<VillagerGhost> helping(GameTestHelper helper, ServerPlayer player) {
        return helper
                .getLevel()
                .getEntities(
                        EntityTypeTest.forClass(VillagerGhost.class),
                        ghost -> ghost.isAlive()
                                && ghost.helpedId()
                                        .filter(player.getUUID()::equals)
                                        .isPresent())
                .stream()
                .map(VillagerGhost.class::cast)
                .toList();
    }

    /**
     * When the saved list holds two records and one of them is corrupted (its villager data is no longer a
     * compound), should the decode return the good record and skip the bad one.
     *
     * <p>Method under test: {@code VillagerGhosts#CODEC}
     */
    static void loadsLeniently(GameTestHelper helper) {
        // Arrange
        VillagerData data = sampleVillagerData(helper);
        VillagerGhosts ghosts = VillagerGhosts.get(helper.getLevel());
        VillagerGhosts.Record good = new VillagerGhosts.Record(UUID.randomUUID(), new BlockPos(7, 70, 9), data, 1L, 2L);
        VillagerGhosts.Record bad = new VillagerGhosts.Record(UUID.randomUUID(), new BlockPos(8, 70, 9), data, 3L, 4L);
        ghosts.add(good);
        ghosts.add(bad);
        whenDone(helper, () -> {
            ghosts.remove(good.id());
            ghosts.remove(bad.id());
        });
        CompoundTag encoded = (CompoundTag)
                VillagerGhosts.CODEC.encodeStart(NbtOps.INSTANCE, ghosts).getOrThrow();
        int[] badPos = {8, 70, 9};
        for (Tag entry : encoded.getListOrEmpty("records")) {
            CompoundTag record = (CompoundTag) entry;
            if (record.getIntArray("pos")
                    .filter(pos -> Arrays.equals(pos, badPos))
                    .isPresent()) {
                record.put("data", StringTag.valueOf("a profession that no longer exists"));
            }
        }

        // Act
        VillagerGhosts decoded =
                VillagerGhosts.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        // Assert
        helper.assertTrue(decoded.find(good.id()).filter(good::equals).isPresent(), "the good record comes back");
        helper.assertTrue(decoded.find(bad.id()).isEmpty(), "the bad record is skipped");
        helper.succeed();
    }

    /**
     * When two ready records are in range (10 and 25 blocks), should the ghost be sent for the 10-block one.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void nearestRecordWins(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, false);
        VillagerGhosts.Record near = loadedRecord(helper, 10.0);
        VillagerGhosts.Record far = loadedRecord(helper, 25.0);

        helper.startSequence()
                .thenWaitUntil(() -> awaitTicking(helper, near, far))
                // Act
                .thenExecute(() -> VillagerGhosts.step(scene.player()))
                // Assert
                .thenExecute(() -> {
                    helper.assertValueEqual(ghosts(helper, near).size(), 1, "a ghost for the nearer record");
                    helper.assertValueEqual(ghosts(helper, far).size(), 0, "none for the farther one");
                })
                .thenSucceed();
    }

    /**
     * When the record at 10 blocks has both spells on cooldown and the one at 25 blocks is ready, should the ghost be
     * sent for the ready one.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void readyBeatsNearest(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, false);
        VillagerGhosts.Record spent = loadedRecord(helper, 10.0);
        VillagerGhosts.Record ready = loadedRecord(helper, 25.0);
        spend(helper, spent);

        helper.startSequence()
                .thenWaitUntil(() -> awaitTicking(helper, spent, ready))
                // Act
                .thenExecute(() -> VillagerGhosts.step(scene.player()))
                // Assert
                .thenExecute(() -> {
                    helper.assertValueEqual(ghosts(helper, ready).size(), 1, "a ghost for the ready record");
                    helper.assertValueEqual(ghosts(helper, spent).size(), 0, "none for the spent one");
                })
                .thenSucceed();
    }

    /**
     * When the only record has both spells on cooldown, should a ghost still come (nothing better exists) and, once
     * near, cast nothing and leave the record as it was.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.2, §16.4.
     */
    static void spentRecordCastsNothing(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        ServerPlayer player = scene.player();
        VillagerGhosts.Record spent = spend(helper, scene.record());

        // Act
        VillagerGhosts.step(player);

        // Assert
        helper.assertValueEqual(ghosts(helper, spent).size(), 1, "the only record is sent");
        VillagerGhost ghost = ghosts(helper, spent).get(0);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ghost.distanceTo(player) <= 3.0, "the ghost is near"))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(!player.hasEffect(ModEffects.MIRROR), "no Mirror");
                    helper.assertTrue(!player.hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail");
                    helper.assertTrue(saved(helper, scene).equals(spent), "the record is unchanged");
                })
                .thenSucceed();
    }

    /**
     * Puts both spells of {@code record} on a cooldown that outlasts the test.
     *
     * @param helper the test helper
     * @param record the record
     * @return the record as it is saved now
     */
    private static VillagerGhosts.Record spend(GameTestHelper helper, VillagerGhosts.Record record) {
        VillagerGhosts ghosts = VillagerGhosts.get(helper.getLevel());
        long until = helper.getLevel().getGameTime() + 1_000_000L;
        ghosts.setMirrorReadyAt(record.id(), until);
        ghosts.setGhostrailReadyAt(record.id(), until);
        return ghosts.find(record.id()).orElseThrow();
    }

    /**
     * Asserts that the chunk of every record has its entities loaded and ticking.
     *
     * @param helper the test helper
     * @param records the records
     */
    private static void awaitTicking(GameTestHelper helper, VillagerGhosts.Record... records) {
        for (VillagerGhosts.Record record : records) {
            helper.assertTrue(
                    helper.getLevel().areEntitiesActuallyLoadedAndTicking(ChunkPos.containing(record.pos())),
                    "setup: the record's chunk is entity-ticking");
        }
    }

    /**
     * When two players in need stand in range of two records, should the first player's ghost take the nearer record
     * and the second player's take the other one (a record with a live ghost is skipped).
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void liveGhostRecordSkipped(GameTestHelper helper) {
        // Arrange
        Scene first = scene(helper, true, false);
        ServerPlayer second = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        second.addEffect(EvokingGameTests.evoking());
        MockPlayers.moveTo(helper, second, FangGameTests.STAND, FangGameTests.NORTH);
        second.setHealth(second.getMaxHealth() * 0.4F);
        VillagerGhosts.Record near = ScrollTestSupport.record(helper, at(helper, 8.0, 0.0, 0.0));
        VillagerGhosts.Record far = ScrollTestSupport.record(helper, at(helper, 10.0, 0.0, 0.0));
        whenDone(helper, () -> {
            ghosts(helper, near).forEach(Entity::discard);
            ghosts(helper, far).forEach(Entity::discard);
        });

        // Act
        VillagerGhosts.step(first.player());
        VillagerGhosts.step(second);

        // Assert
        helper.assertValueEqual(ghosts(helper, near).size(), 1, "one ghost for the nearer record");
        helper.assertValueEqual(ghosts(helper, far).size(), 1, "one ghost for the other record");
        helper.assertTrue(
                helping(helper, first.player()).size() == 1
                        && helping(helper, second).size() == 1,
                "one ghost for each player");
        helper.assertTrue(
                ghosts(helper, near)
                        .get(0)
                        .helpedId()
                        .filter(first.player().getUUID()::equals)
                        .isPresent(),
                "the first player's ghost came from the nearer record");
        helper.succeed();
    }

    /**
     * A full-health player with a record 10 blocks east and husks at the given offsets.
     *
     * @param helper the test helper
     * @param husks pairs of east and south offsets, one pair per husk
     * @return the scene (its husk is null; the husks are discarded when the test ends)
     */
    private static Scene healthyAmong(GameTestHelper helper, double[]... husks) {
        Scene scene = scene(helper, false, true);
        scene.player().setHealth(scene.player().getMaxHealth());
        for (double[] husk : husks) {
            hostileAt(helper, at(helper, husk[0], 0.0, husk[1]));
        }
        return scene;
    }

    /**
     * When a full-health player has three hostiles within 8 blocks (a crowd), should a ghost be sent.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void crowdComes(GameTestHelper helper) {
        // Arrange
        Scene scene = healthyAmong(helper, new double[] {3.0, 0.0}, new double[] {0.0, 3.0}, new double[] {-3.0, 0.0});

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        helper.assertValueEqual(ghosts(helper, scene.record()).size(), 1, "a ghost for a crowd");
        helper.succeed();
    }

    /**
     * When a full-health player has three hostiles within 16 blocks but only two within 8 (the third is at 9), should
     * no ghost be sent.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     *
     * <p>Functional spec §16.2.
     */
    static void crowdNeedsThreeWithin8(GameTestHelper helper) {
        // Arrange
        Scene scene = healthyAmong(helper, new double[] {3.0, 0.0}, new double[] {0.0, 3.0}, new double[] {-9.0, 0.0});

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        helper.assertTrue(helping(helper, scene.player()).isEmpty(), "no ghost for only two hostiles within 8 blocks");
        helper.succeed();
    }

    /**
     * When the last hostile is gone, should the ghost still be there 150 ticks later; when a hostile then returns for
     * a while, should the quiet count start over (the ghost is still there 150 ticks into the second quiet stretch,
     * though the first began over 360 ticks before), and fade after 200 quiet ticks.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.5.
     */
    static void fadeTiming(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        VillagerGhosts.step(scene.player());
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);
        Husk[] returned = new Husk[1];

        // Act & Assert
        scene.husk().discard();
        helper.startSequence()
                .thenIdle(150)
                .thenExecute(() -> {
                    helper.assertTrue(!ghost.isRemoved(), "still there 150 ticks into the quiet period");
                    returned[0] = hostileAt(helper, at(helper, 3.0, 0.0, 0.0));
                })
                .thenIdle(60)
                .thenExecute(() -> returned[0].discard())
                .thenIdle(150)
                .thenExecute(() -> helper.assertTrue(
                        !ghost.isRemoved(), "still there 150 ticks into the second quiet period: the count restarted"))
                .thenWaitUntil(() -> helper.assertTrue(ghost.isRemoved(), "the ghost faded after 200 quiet ticks"))
                .thenSucceed();
    }

    /**
     * When a ghost is sent, should it carry an infinite Ghostrail.
     *
     * <p>Method under test: {@code VillagerGhost#bind}
     *
     * <p>Functional spec §14.8.
     */
    static void carriesInfiniteGhostrail(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);
        helper.assertTrue(ghost.hasEffect(ModEffects.GHOSTRAIL), "the ghost has Ghostrail");
        MobEffectInstance ghostrail = ghost.getEffect(ModEffects.GHOSTRAIL);
        helper.assertTrue(ghostrail != null && ghostrail.isInfiniteDuration(), "and it is infinite");
        helper.succeed();
    }

    /**
     * When the helped player turns spectator, should the ghost fade.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.5.
     */
    static void fadesForSpectator(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        VillagerGhosts.step(scene.player());
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);

        // Act
        scene.player().setGameMode(GameType.SPECTATOR);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ghost.isRemoved(), "the ghost faded"))
                .thenSucceed();
    }

    /**
     * When the helped player dies, should the ghost fade.
     *
     * <p>Method under test: {@code VillagerGhost#tick}
     *
     * <p>Functional spec §16.5.
     */
    static void fadesForDeath(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        VillagerGhosts.step(scene.player());
        VillagerGhost ghost = ghosts(helper, scene.record()).get(0);

        // Act
        scene.player().kill(helper.getLevel());

        // Assert
        helper.assertTrue(!scene.player().isAlive(), "setup: the player is dead");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ghost.isRemoved(), "the ghost faded"))
                .thenSucceed();
    }

    /**
     * When the player's post-tick event fires at {@code tickCount} 21 and then 20, should only the 20 step the player
     * (a ghost is sent on 20 and none on 21).
     *
     * <p>Method under test: {@code VillagerGhosts#onPlayerTick}
     *
     * <p>Functional spec §16.2.
     */
    static void tickGate(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, true, true);
        ServerPlayer player = scene.player();

        // Act & Assert
        player.tickCount = 21;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        helper.assertTrue(ghosts(helper, scene.record()).isEmpty(), "no ghost on tick 21");
        player.tickCount = 20;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        helper.assertValueEqual(ghosts(helper, scene.record()).size(), 1, "a ghost on tick 20");
        helper.succeed();
    }
}
