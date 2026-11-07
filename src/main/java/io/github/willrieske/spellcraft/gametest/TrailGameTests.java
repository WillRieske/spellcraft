package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.withConfig;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.Trail;
import io.github.willrieske.spellcraft.trailing.ModBlocks;
import io.github.willrieske.spellcraft.trailing.TrailFires;
import io.github.willrieske.spellcraft.trailing.TrailLayer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Trail GameTests for Strength of Trailing: laying, fading, sparing and the ledger, each test named after its row in
 * the spec's GameTests table (spec §6, table G1–G28). The tracker lifecycle is in {@link TrailLayerGameTests}, the
 * edge cases in {@link TrailEdgeCaseGameTests}; both use the helpers here. Registered in {@link SpellcraftGameTests}.
 *
 * <p>Every test that lights a flame in the level's ledger forgets its owner at the end ({@link #forget}): the tests
 * share one level, and a leftover entry at a reused structure position would otherwise be another test's business.
 */
public final class TrailGameTests {
    /** Relative positions in the 3×3×3 structure: four standing spots at y = 1, each over its own floor block. */
    static final BlockPos A = new BlockPos(0, 1, 0);

    static final BlockPos B = new BlockPos(2, 1, 0);
    static final BlockPos C = new BlockPos(0, 1, 2);
    static final BlockPos D = new BlockPos(2, 1, 2);
    private static final List<BlockPos> SPOTS = List.of(A, B, C, D);

    private TrailGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "trail_g1_lights_the_block_left", TrailGameTests::g1LightsTheBlockLeft);
        SpellcraftGameTests.add(SHARED, "trail_g2_not_from_the_air", TrailGameTests::g2NotFromTheAir);
        SpellcraftGameTests.add(SHARED, "trail_g3_not_while_riding", TrailGameTests::g3NotWhileRiding);
        SpellcraftGameTests.add(SHARED, "trail_g4_lingers_on_soul_sand", TrailGameTests::g4LingersOnSoulSand);
        SpellcraftGameTests.add(SHARED, "trail_g5_fades_oldest_first", TrailGameTests::g5FadesOldestFirst);
        SpellcraftGameTests.add("config_mutation", "trail_g6_max_blocks", TrailGameTests::g6MaxBlocks);
        SpellcraftGameTests.add(
                "team_mutation", "trail_g7_spares_owner_and_allies", TrailGameTests::g7SparesOwnerAndAllies);
        SpellcraftGameTests.add(SHARED, "trail_g7b_spares_a_tamed_pet", TrailGameTests::g7bSparesATamedPet);
        SpellcraftGameTests.add(SHARED, "trail_g7c_spares_a_liked_allay", TrailGameTests::g7cSparesALikedAllay);
        SpellcraftGameTests.add(SHARED, "trail_g7d_spares_a_summoned_mob", TrailGameTests::g7dSparesASummonedMob);
        SpellcraftGameTests.add(SHARED, "trail_g8_burns_an_unallied_player", TrailGameTests::g8BurnsAnUnalliedPlayer);
        SpellcraftGameTests.add(SHARED, "trail_g9_random_tick", TrailGameTests::g9RandomTick);
        SpellcraftGameTests.add(SHARED, "trail_g10_falls_with_the_ground", TrailGameTests::g10FallsWithTheGround);
        SpellcraftGameTests.add(SHARED, "trail_g11_stops_with_the_effect", TrailGameTests::g11StopsWithTheEffect);
        SpellcraftGameTests.add(SHARED, "trail_g12_codec_round_trip", TrailGameTests::g12CodecRoundTrip);
        SpellcraftGameTests.add(SHARED, "trail_g13_own_flame_is_not_relit", TrailGameTests::g13OwnFlameIsNotRelit);
        SpellcraftGameTests.add(
                SHARED, "trail_g14_relit_spot_keeps_new_owner", TrailGameTests::g14RelitSpotKeepsNewOwner);
        SpellcraftGameTests.add(SHARED, "trail_g15_where_a_flame_may_go", TrailGameTests::g15WhereAFlameMayGo);
        SpellcraftGameTests.add(
                SHARED, "trail_g16_duplicate_owner_keeps_last", TrailGameTests::g16DuplicateOwnerKeepsTheLastTrail);
        SpellcraftGameTests.add(
                SHARED, "trail_g18_unloaded_flame_goes_stale", TrailGameTests::g18UnloadedFlameGoesStale);
        SpellcraftGameTests.add(
                SHARED, "trail_g19_stale_flame_goes_out_when_loaded", TrailGameTests::g19StaleFlameGoesOutWhenLoaded);
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    /** Stone under every standing spot. */
    static void floors(GameTestHelper helper) {
        for (BlockPos spot : SPOTS) {
            helper.setBlock(spot.below(), Blocks.STONE);
        }
    }

    /** A 30-second Strength of Trailing, particles hidden, as a cast gives. */
    static MobEffectInstance trailing() {
        return new MobEffectInstance(ModEffects.TRAILING, 600, 0, false, false, true);
    }

    static ServerPlayer trailingPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(trailing());
        return player;
    }

    /** Teleports the player to a standing spot without tracking (see {@link #standAt} for the tracking variant). */
    static void teleport(GameTestHelper helper, ServerPlayer player, BlockPos spot) {
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(spot));
    }

    /** Teleports the player to a spot, sets its ground state, and runs one trail-laying step. */
    static void standAt(GameTestHelper helper, ServerPlayer player, BlockPos spot, boolean grounded) {
        standAtExactly(helper, player, Vec3.atBottomCenterOf(spot), grounded);
    }

    /** {@link #standAt} with exact feet coordinates (relative), for a player sunk into or raised above a block. */
    static void standAtExactly(GameTestHelper helper, ServerPlayer player, Vec3 feet, boolean grounded) {
        MockPlayers.moveTo(helper, player, feet);
        player.setOnGround(grounded);
        TrailLayer.track(player);
    }

    /** The test level's ledger. */
    static TrailFires ledger(GameTestHelper helper) {
        return TrailFires.of(helper.getLevel());
    }

    static boolean light(GameTestHelper helper, ServerPlayer owner, BlockPos spot) {
        return ledger(helper).light(helper.getLevel(), owner, helper.absolutePos(spot));
    }

    static void fade(GameTestHelper helper, int ticks) {
        for (int i = 0; i < ticks; i++) {
            ledger(helper).fadeTick(helper.getLevel());
        }
    }

    /** Leaves no ledger entry of {@code owner}'s behind (see the class comment). */
    static void forget(GameTestHelper helper, Entity owner) {
        ledger(helper).forget(owner.getUUID());
    }

    static Block trailFire() {
        return ModBlocks.TRAIL_FIRE.get();
    }

    /** Runs the flame at {@code spot} over {@code entity} the way a real collision does. */
    static void stepInto(GameTestHelper helper, BlockPos spot, Entity entity) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(spot);
        InsideBlockEffectApplier.StepBasedCollector collector = new InsideBlockEffectApplier.StepBasedCollector();
        level.getBlockState(pos).entityInside(level, pos, entity, collector, false);
        collector.applyAndClear(entity);
    }

    static void assertBurned(GameTestHelper helper, LivingEntity entity, String who) {
        helper.assertTrue(entity.getRemainingFireTicks() > 0, who + " should be on fire");
        helper.assertTrue(entity.getHealth() < entity.getMaxHealth(), who + " should be hurt");
    }

    static void assertSpared(GameTestHelper helper, LivingEntity entity, String who) {
        helper.assertTrue(entity.getRemainingFireTicks() <= 0, who + " should not be on fire");
        helper.assertValueEqual(entity.getHealth(), entity.getMaxHealth(), who + "'s health");
    }

    static Optional<UUID> ownerOf(GameTestHelper helper, BlockPos spot) {
        return ledger(helper).ownerOf(helper.absolutePos(spot));
    }

    /** Whether the ledger says {@code owner} owns the flame at {@code spot} (blind to other tests' entries). */
    static boolean ownedBy(GameTestHelper helper, BlockPos spot, Entity owner) {
        return ownerOf(helper, spot).equals(Optional.of(owner.getUUID()));
    }

    /** {@code owner}'s trail in the level's ledger, if they have one (other tests leave trails of their own). */
    static Optional<TrailFires.Entry> trailOf(GameTestHelper helper, Entity owner) {
        return ledger(helper).entries().stream()
                .filter(entry -> entry.owner().equals(owner.getUUID()))
                .findFirst();
    }

    /** Writes {@code ledger} with its codec and reads it back. */
    private static TrailFires roundTrip(TrailFires ledger) {
        Tag saved =
                TrailFires.CODEC.codec().encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
        return TrailFires.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
    }

    // --- tests ---------------------------------------------------------------------------------------------------

    static void g1LightsTheBlockLeft(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, true);
        helper.assertBlockNotPresent(trailFire(), A);
        standAt(helper, player, B, true);
        helper.assertBlockPresent(trailFire(), A);
        helper.assertBlockNotPresent(trailFire(), B);
        helper.assertValueEqual(ownerOf(helper, A), Optional.of(player.getUUID()), "owner of A");
        forget(helper, player);
        helper.succeed();
    }

    static void g2NotFromTheAir(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, false);
        standAt(helper, player, B, true);
        helper.assertBlockNotPresent(trailFire(), A);
        forget(helper, player);
        helper.succeed();
    }

    static void g3NotWhileRiding(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        Pig pig = helper.spawn(EntityTypes.PIG, A);
        teleport(helper, player, A);
        helper.assertTrue(player.startRiding(pig, true, false), "mounted the pig");
        player.setOnGround(true);
        helper.assertTrue(player.isPassenger(), "riding when tracked");
        // Otherwise A could stay dark only because the rider's feet block is some other block.
        helper.assertValueEqual(player.blockPosition(), helper.absolutePos(A), "the rider's feet block");
        TrailLayer.track(player);
        player.stopRiding();
        standAt(helper, player, B, true);
        helper.assertBlockNotPresent(trailFire(), A);
        forget(helper, player);
        helper.succeed();
    }

    static void g4LingersOnSoulSand(GameTestHelper helper) {
        floors(helper);
        helper.setBlock(A.below(), Blocks.SOUL_SAND);
        ServerPlayer player = trailingPlayer(helper);
        helper.assertTrue(light(helper, player, A), "lit over soul sand");
        fade(helper, 39);
        helper.assertBlockPresent(trailFire(), A);
        fade(helper, 1);
        helper.assertBlockNotPresent(trailFire(), A);
        forget(helper, player);
        helper.succeed();
    }

    static void g5FadesOldestFirst(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        helper.assertTrue(light(helper, player, A), "lit A");
        helper.assertTrue(light(helper, player, B), "lit B");
        fade(helper, 20);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.assertBlockPresent(trailFire(), B);
        fade(helper, 20);
        helper.assertBlockNotPresent(trailFire(), B);
        helper.assertTrue(trailOf(helper, player).isEmpty(), "an emptied trail is forgotten");
        helper.succeed();
    }

    /** Mutates global server config, so it runs alone in the {@code config_mutation} batch. */
    static void g6MaxBlocks(GameTestHelper helper) {
        withConfig(ServerConfig.TRAILING_MAX_FLAMES, 2, () -> {
            floors(helper);
            ServerPlayer player = trailingPlayer(helper);
            helper.assertTrue(light(helper, player, A), "lit A");
            helper.assertTrue(light(helper, player, B), "lit B");
            helper.assertTrue(light(helper, player, C), "lit C");
            helper.assertBlockNotPresent(trailFire(), A);
            helper.assertBlockPresent(trailFire(), B);
            helper.assertBlockPresent(trailFire(), C);
            helper.assertFalse(ownedBy(helper, A, player), "the evicted flame is no longer the player's");
            helper.assertValueEqual(
                    trailOf(helper, player).map(entry -> entry.flames().size()), Optional.of(2), "flames kept");
            forget(helper, player);
        });
        helper.succeed();
    }

    /**
     * Puts the owner on a team, so it runs in the {@code team_mutation} batch (shared with G23; both bodies are
     * synchronous, so neither sees the other's team) and removes the team after.
     */
    static void g7SparesOwnerAndAllies(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("spellcraft_trail_test");
        try {
            Zombie ally = helper.spawn(EntityTypes.ZOMBIE, B);
            // Every mock player shares the profile name "spellcraft-mock", so this also puts leftover mock players
            // on the team while the test runs; removePlayerTeam in the finally clears all of them.
            scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
            Zombie stranger = helper.spawn(EntityTypes.ZOMBIE, C);

            stepInto(helper, A, owner);
            stepInto(helper, A, ally);
            stepInto(helper, A, stranger);

            assertSpared(helper, owner, "the owner");
            assertSpared(helper, ally, "an ally");
            assertBurned(helper, stranger, "a stranger");
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        forget(helper, owner);
        helper.succeed();
    }

    /** The owner has no team here, so only the tame-ownership path can spare the pet. */
    static void g7bSparesATamedPet(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        Wolf pet = helper.spawn(EntityTypes.WOLF, C);
        pet.tame(owner);
        stepInto(helper, A, pet);
        assertSpared(helper, pet, "the owner's pet");
        forget(helper, owner);
        helper.succeed();
    }

    /**
     * When a flame burns a zombie carrying the owner's summoner attachment and a zombie carrying a stranger's, should
     * spare the first and burn the second. A zombie is not fire immune (a vex is, so it could not show a burn) and
     * has no team and no owner, so only the attachment can spare it.
     *
     * <p>Method under test: {@link TrailFires#isSpared}, through {@link AllyAttachments#SUMMONER}
     */
    static void g7dSparesASummonedMob(GameTestHelper helper) {
        // Arrange
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        Zombie mine = helper.spawn(EntityTypes.ZOMBIE, C);
        mine.setData(AllyAttachments.SUMMONER, owner.getUUID());
        Zombie strangers = helper.spawn(EntityTypes.ZOMBIE, B);
        strangers.setData(AllyAttachments.SUMMONER, UUID.randomUUID());

        // Act
        stepInto(helper, A, mine);
        stepInto(helper, A, strangers);

        // Assert
        assertSpared(helper, mine, "the owner's summoned mob");
        assertBurned(helper, strangers, "a stranger's summoned mob");
        forget(helper, owner);
        helper.succeed();
    }

    /**
     * An allay has no team and is not an {@code OwnableEntity}, yet vanilla allies it to the player it likes
     * ({@code Allay.considersEntityAsAlly}); guards the {@code isSpared} fast path.
     */
    static void g7cSparesALikedAllay(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        Allay allay = helper.spawn(EntityTypes.ALLAY, C);
        allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, owner.getUUID());
        helper.assertTrue(owner.isAlliedTo(allay), "setup: vanilla allies the allay to the player it likes");
        stepInto(helper, A, allay);
        assertSpared(helper, allay, "the allay that likes the owner");
        forget(helper, owner);
        helper.succeed();
    }

    static void g8BurnsAnUnalliedPlayer(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        helper.assertTrue(light(helper, owner, A), "lit A");
        ServerPlayer other = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        teleport(helper, other, B);
        stepInto(helper, A, other);
        assertBurned(helper, other, "an unallied player");
        stepInto(helper, A, owner);
        assertSpared(helper, owner, "the owner");
        forget(helper, owner);
        helper.succeed();
    }

    /** An orphan's random tick removes it; an owned flame's leaves it. Neighbors are G25's. */
    static void g9RandomTick(GameTestHelper helper) {
        floors(helper);
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        // An orphan (placed by hand, no owner) puts itself out. Every lighting test forgets its owner, so no
        // leftover entry can own this spot.
        helper.setBlock(A, trailFire());
        helper.assertValueEqual(ownerOf(helper, A), Optional.empty(), "a hand-placed flame has no owner");
        level.getBlockState(a).randomTick(level, a, level.getRandom());
        helper.assertBlockNotPresent(trailFire(), A);
        ServerPlayer owner = trailingPlayer(helper);
        helper.assertTrue(light(helper, owner, A), "lit A");
        level.getBlockState(a).randomTick(level, a, level.getRandom());
        helper.assertBlockPresent(trailFire(), A);
        forget(helper, owner);
        helper.succeed();
    }

    static void g10FallsWithTheGround(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        helper.assertTrue(light(helper, owner, A), "lit A");
        helper.setBlock(A.below(), Blocks.AIR);
        helper.assertBlockNotPresent(trailFire(), A);
        // onPlace: a flame set where nothing supports it removes itself at once.
        helper.setBlock(C.below(), Blocks.AIR);
        helper.setBlock(C, trailFire());
        helper.assertBlockNotPresent(trailFire(), C);
        forget(helper, owner);
        helper.succeed();
    }

    static void g11StopsWithTheEffect(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, true);
        player.removeEffect(ModEffects.TRAILING);
        standAt(helper, player, B, true);
        helper.assertBlockNotPresent(trailFire(), A);
        player.addEffect(trailing());
        standAt(helper, player, C, true);
        helper.assertBlockNotPresent(trailFire(), B);
        standAt(helper, player, D, true);
        helper.assertBlockPresent(trailFire(), C);
        // The tracker was dropped with the effect, so re-casting never lights the old spot A.
        helper.assertBlockNotPresent(trailFire(), A);
        forget(helper, player);
        helper.succeed();
    }

    /**
     * A ledger of its own with two owners and two stale flames survives the codec. Then Y lights A, which X's trail
     * still lists, and B, where X has a stale flame: the index says Y, so the save keeps both spots under Y only and
     * the reloaded index still says Y.
     */
    static void g12CodecRoundTrip(GameTestHelper helper) {
        floors(helper);
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        BlockPos b = helper.absolutePos(B);
        UUID x = UUID.randomUUID();
        // No effect, so the player's own ticks lay nothing in the level's ledger.
        ServerPlayer yPlayer = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        UUID y = yPlayer.getUUID();
        Trail.Flame x10 = new Trail.Flame(10L, false);
        Trail.Flame x11 = new Trail.Flame(11L, true);
        TrailFires.Entry xTrail = new TrailFires.Entry(x, List.of(x10, new Trail.Flame(a.asLong(), false), x11), 7);
        TrailFires.Entry yTrail = new TrailFires.Entry(y, List.of(new Trail.Flame(20L, true)), 33);
        TrailFires.StaleFlame xStale = new TrailFires.StaleFlame(x, 30L);
        TrailFires.StaleFlame xStaleAtB = new TrailFires.StaleFlame(x, b.asLong());
        TrailFires.StaleFlame xStaleLast = new TrailFires.StaleFlame(x, 31L);
        TrailFires original = new TrailFires(List.of(xTrail, yTrail), List.of(xStale, xStaleAtB, xStaleLast));
        TrailFires loaded = roundTrip(original);
        helper.assertValueEqual(loaded.entries(), original.entries(), "entries after the round trip");
        helper.assertValueEqual(loaded.entries(), List.of(xTrail, yTrail), "entries as written");
        helper.assertValueEqual(loaded.staleFlames(), List.of(xStale, xStaleAtB, xStaleLast), "stale flames, in order");
        helper.assertValueEqual(loaded.ownerOf(BlockPos.of(11L)), Optional.of(x), "owner of x's flame");
        helper.assertValueEqual(loaded.ownerOf(a), Optional.of(x), "owner of x's flame at A");
        helper.assertValueEqual(loaded.ownerOf(BlockPos.of(20L)), Optional.of(y), "owner of y's flame");
        helper.assertValueEqual(loaded.ownerOf(BlockPos.of(30L)), Optional.of(x), "owner of x's stale flame");
        helper.assertValueEqual(loaded.ownerOf(b), Optional.of(x), "owner of x's stale flame at B");
        helper.assertValueEqual(loaded.ownerOf(BlockPos.of(99L)), Optional.empty(), "owner of nothing");

        // The contested spots: y lights last at A and B, so the index says y.
        helper.assertTrue(original.light(level, yPlayer, a), "y lights A");
        helper.assertTrue(original.light(level, yPlayer, b), "y lights B");
        helper.assertValueEqual(original.ownerOf(a), Optional.of(y), "setup: the index gives A to y");
        helper.assertValueEqual(original.ownerOf(b), Optional.of(y), "setup: the index gives B to y");
        TrailFires reloaded = roundTrip(original);
        helper.assertValueEqual(
                reloaded.entries(),
                List.of(
                        new TrailFires.Entry(x, List.of(x10, x11), 7),
                        new TrailFires.Entry(
                                y,
                                List.of(
                                        new Trail.Flame(20L, true),
                                        new Trail.Flame(a.asLong(), false),
                                        new Trail.Flame(b.asLong(), false)),
                                33)),
                "A is saved under y only");
        helper.assertValueEqual(
                reloaded.staleFlames(), List.of(xStale, xStaleLast), "x's stale flame at B is not saved");
        helper.assertValueEqual(reloaded.ownerOf(a), Optional.of(y), "owner of A after the reload");
        helper.assertValueEqual(reloaded.ownerOf(b), Optional.of(y), "owner of B after the reload");
        helper.setBlock(A, Blocks.AIR); // the level's ledger does not know these flames
        helper.setBlock(B, Blocks.AIR);

        // A save from before stale flames existed has no "stale" field (an empty list is not written either).
        Tag legacy = TrailFires.CODEC
                .codec()
                .encodeStart(NbtOps.INSTANCE, new TrailFires(List.of(yTrail)))
                .getOrThrow();
        helper.assertFalse(((CompoundTag) legacy).contains("stale"), "an empty stale list is not written");
        TrailFires legacyLoaded =
                TrailFires.CODEC.codec().parse(NbtOps.INSTANCE, legacy).getOrThrow();
        helper.assertValueEqual(legacyLoaded.entries(), List.of(yTrail), "a save without stale flames loads");
        helper.assertValueEqual(legacyLoaded.staleFlames(), List.of(), "and has none");
        helper.succeed();
    }

    static void g13OwnFlameIsNotRelit(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, true);
        standAt(helper, player, B, true);
        standAt(helper, player, A, true);
        standAt(helper, player, B, true);
        helper.assertBlockPresent(trailFire(), A);
        helper.assertBlockPresent(trailFire(), B);
        TrailFires.Entry trail =
                trailOf(helper, player).orElseThrow(() -> helper.assertionException("the player has a trail"));
        helper.assertValueEqual(trail.flames().size(), 2, "A and B once each");
        forget(helper, player);
        helper.succeed();
    }

    /** X's flame at A is put out by hand; Y relights A. X's turn to fade must not touch Y's flame. */
    static void g14RelitSpotKeepsNewOwner(GameTestHelper helper) {
        floors(helper);
        ServerPlayer x = trailingPlayer(helper);
        ServerPlayer y = trailingPlayer(helper);
        helper.assertTrue(light(helper, x, A), "x lights A");
        fade(helper, 10);
        helper.setBlock(A, Blocks.AIR);
        helper.assertTrue(light(helper, y, A), "y relights the empty spot");
        helper.assertValueEqual(ownerOf(helper, A), Optional.of(y.getUUID()), "owner after relighting");
        fade(helper, 10); // x's flame fades now (20 ticks in); y's is only 10 ticks in
        helper.assertBlockPresent(trailFire(), A);
        helper.assertValueEqual(ownerOf(helper, A), Optional.of(y.getUUID()), "still y's");
        fade(helper, 10);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.assertFalse(ownedBy(helper, A, y), "not y's after y's fade");
        helper.assertFalse(ownedBy(helper, A, x), "never x's again");
        forget(helper, x);
        forget(helper, y);
        helper.succeed();
    }

    static void g15WhereAFlameMayGo(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        helper.setBlock(A, Blocks.SHORT_GRASS);
        helper.assertTrue(light(helper, owner, A), "grass is replaced");
        helper.assertBlockPresent(trailFire(), A);
        helper.assertFalse(light(helper, owner, A), "an existing flame is not relit");
        helper.setBlock(C.below(), Blocks.OAK_FENCE);
        helper.assertFalse(light(helper, owner, C), "a fence top is not sturdy");
        // Soul sand first: vanilla soul fire set over stone removes itself at once (BaseFireBlock.onPlace).
        helper.setBlock(D.below(), Blocks.SOUL_SAND);
        helper.setBlock(D, Blocks.SOUL_FIRE);
        helper.assertBlockPresent(Blocks.SOUL_FIRE, D); // fail loudly if the setup did not hold
        helper.assertFalse(light(helper, owner, D), "vanilla fire is not replaced");
        helper.setBlock(B, Blocks.WATER);
        helper.assertFalse(light(helper, owner, B), "water is not lit");
        helper.setBlock(B, Blocks.AIR);
        TrailFires.Entry mine =
                trailOf(helper, owner).orElseThrow(() -> helper.assertionException("the owner has a trail"));
        helper.assertValueEqual(mine.flames().size(), 1, "one flame");
        forget(helper, owner);
        helper.succeed();
    }

    /** A corrupt save lists one owner twice: the last trail wins, and the dropped trail's spots are not indexed. */
    static void g16DuplicateOwnerKeepsTheLastTrail(GameTestHelper helper) {
        UUID x = UUID.randomUUID();
        TrailFires.Entry dropped = new TrailFires.Entry(x, List.of(new Trail.Flame(10L, false)), 5);
        TrailFires.Entry kept = new TrailFires.Entry(x, List.of(new Trail.Flame(20L, true)), 9);
        TrailFires rebuilt = new TrailFires(List.of(dropped, kept));
        helper.assertValueEqual(rebuilt.entries(), List.of(kept), "entries after the rebuild");
        helper.assertValueEqual(rebuilt.ownerOf(BlockPos.of(10L)), Optional.empty(), "owner of the dropped flame");
        helper.assertValueEqual(rebuilt.ownerOf(BlockPos.of(20L)), Optional.of(x), "owner of the kept flame");
        helper.succeed();
    }

    /** The flame's turn comes while its chunk is unloaded: it goes stale, keeps its owner, and nothing is loaded. */
    static void g18UnloadedFlameGoesStale(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(100_000, 64, 100_000);
        helper.assertFalse(level.isLoaded(far), "setup: the far chunk is not loaded");
        UUID owner = UUID.randomUUID();
        // A ledger of its own, so the level's ledger is untouched.
        TrailFires ledger =
                new TrailFires(List.of(new TrailFires.Entry(owner, List.of(new Trail.Flame(far.asLong(), false)), 1)));
        ledger.fadeTick(level);
        helper.assertValueEqual(
                ledger.staleFlames(), List.of(new TrailFires.StaleFlame(owner, far.asLong())), "stale flames");
        helper.assertValueEqual(ledger.entries(), List.of(), "the flame left its trail");
        helper.assertValueEqual(ledger.ownerOf(far), Optional.of(owner), "the stale flame keeps its owner");
        helper.assertFalse(level.isLoaded(far), "no chunk was loaded");
        helper.succeed();
    }

    /**
     * A stale flame whose chunk is loaded goes out on the next fade tick, and the ledger forgets it. A stale flame of
     * X's at a spot Y relit since is dead: the sweep forgets it and leaves Y's flame burning and Y's.
     */
    static void g19StaleFlameGoesOutWhenLoaded(GameTestHelper helper) {
        floors(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = trailingPlayer(helper);
        helper.assertTrue(light(helper, owner, A), "lit A");
        BlockPos a = helper.absolutePos(A);
        TrailFires ledger = new TrailFires(List.of(), List.of(new TrailFires.StaleFlame(owner.getUUID(), a.asLong())));
        helper.assertValueEqual(ledger.ownerOf(a), Optional.of(owner.getUUID()), "a stale flame is indexed");
        ledger.fadeTick(level);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.assertValueEqual(ledger.ownerOf(a), Optional.empty(), "owner after the sweep");
        helper.assertTrue(ledger.isEmpty(), "the ledger is empty");
        forget(helper, owner); // the level's own entry for A

        BlockPos b = helper.absolutePos(B);
        UUID x = UUID.randomUUID();
        // No effect, so the player's own ticks lay nothing in the level's ledger.
        ServerPlayer y = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        TrailFires contested = new TrailFires(List.of(), List.of(new TrailFires.StaleFlame(x, b.asLong())));
        helper.assertTrue(contested.light(level, y, b), "y relights B");
        helper.assertValueEqual(contested.ownerOf(b), Optional.of(y.getUUID()), "setup: the index gives B to y");
        contested.fadeTick(level);
        helper.assertBlockPresent(trailFire(), B);
        helper.assertValueEqual(contested.ownerOf(b), Optional.of(y.getUUID()), "y's flame is still y's");
        helper.assertValueEqual(contested.staleFlames(), List.of(), "x's stale entry is gone");
        helper.setBlock(B, Blocks.AIR); // the level's ledger does not know this flame
        helper.succeed();
    }
}
