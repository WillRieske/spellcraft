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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** GameTests of the villager-ghost records (Parts 6-7 spec §9, §10) and, from Task 12, the ghosts. */
final class VillagerGhostGameTests {
    /** The environment of the 33-deaths test: it fills the whole record list. */
    private static final String RECORDS = "villager_ghost_records";

    /** The environment of the conversion test: it raises the difficulty to Hard (a global setting). */
    private static final String HARD = "villager_ghost_hard";

    /** The environment of the help tests: they fill the record list and run beside each other. */
    private static final String HELP = "villager_ghost_help";

    /** The environment of the fade test: the 200-tick quiet rule counts any hostile within 16 blocks. */
    private static final String FADE = "villager_ghost_fade";

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
                HELP, "villager_ghost_is_not_sent_for_a_record_beyond_48_blocks", 400, VillagerGhostGameTests::tooFar);
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
     * When the only record is 60 blocks away, should no ghost be sent for it.
     *
     * <p>Method under test: {@code VillagerGhosts#step}
     */
    static void tooFar(GameTestHelper helper) {
        // Arrange (the scene has no record of its own: with a near one, nearest-first and one-ghost-per-player would
        // pass whatever the range is)
        Scene scene = scene(helper, true, false);
        VillagerGhosts.Record far = ScrollTestSupport.record(helper, at(helper, 60.0, 0.0, 0.0));

        // Act
        VillagerGhosts.step(scene.player());

        // Assert
        helper.assertTrue(ghosts(helper, far).isEmpty(), "no ghost for a record beyond 48 blocks");
        helper.succeed();
    }

    /**
     * When something hurts a ghost, should it take no damage and be neither pickable nor attackable.
     *
     * <p>Method under test: {@code VillagerGhost#hurtServer}
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
}
