package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.withConfig;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.config.ServerConfig;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.rules.Trail;
import io.github.willrieske.spellcraft.trailing.ModBlocks;
import io.github.willrieske.spellcraft.trailing.TrailFires;
import io.github.willrieske.spellcraft.trailing.TrailLayer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Trail GameTests for Strength of Trailing: the rows of the spec's GameTests table (spec §6, table G1–G28), each
 * test named after its row. Registered in {@link SpellcraftGameTests}.
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
        SpellcraftGameTests.add(SHARED, "trail_g11_stops_with_the_effect", TrailGameTests::g11StopsWithTheEffect);
        SpellcraftGameTests.add(SHARED, "trail_g13_own_flame_is_not_relit", TrailGameTests::g13OwnFlameIsNotRelit);
        SpellcraftGameTests.add(SHARED, "trail_g4_lingers_on_soul_sand", TrailGameTests::g4LingersOnSoulSand);
        SpellcraftGameTests.add(SHARED, "trail_g5_fades_oldest_first", TrailGameTests::g5FadesOldestFirst);
        SpellcraftGameTests.add("config_mutation", "trail_g6_max_blocks", TrailGameTests::g6MaxBlocks);
        SpellcraftGameTests.add(
                "team_mutation", "trail_g7_spares_owner_and_allies", TrailGameTests::g7SparesOwnerAndAllies);
        SpellcraftGameTests.add(SHARED, "trail_g7b_spares_a_tamed_pet", TrailGameTests::g7bSparesATamedPet);
        SpellcraftGameTests.add(SHARED, "trail_g8_burns_an_unallied_player", TrailGameTests::g8BurnsAnUnalliedPlayer);
        SpellcraftGameTests.add(SHARED, "trail_g9_random_tick", TrailGameTests::g9RandomTick);
        SpellcraftGameTests.add(SHARED, "trail_g10_falls_with_the_ground", TrailGameTests::g10FallsWithTheGround);
        SpellcraftGameTests.add(SHARED, "trail_g12_codec_round_trip", TrailGameTests::g12CodecRoundTrip);
        SpellcraftGameTests.add(
                SHARED, "trail_g16_duplicate_owner_keeps_last", TrailGameTests::g16DuplicateOwnerKeepsTheLastTrail);
        SpellcraftGameTests.add(
                SHARED, "trail_g14_relit_spot_keeps_new_owner", TrailGameTests::g14RelitSpotKeepsNewOwner);
        SpellcraftGameTests.add(SHARED, "trail_g15_where_a_flame_may_go", TrailGameTests::g15WhereAFlameMayGo);
        SpellcraftGameTests.add(
                SHARED, "trail_g17_stand_in_cell_above_soul_sand", TrailGameTests::g17StandInCellAboveSoulSand);
        SpellcraftGameTests.add(SHARED, "trail_g26_carpet_and_top_slab", TrailGameTests::g26CarpetAndTopSlab);
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    /** Stone under every standing spot. */
    static void floors(GameTestHelper helper) {
        for (BlockPos spot : SPOTS) {
            helper.setBlock(spot.below(), Blocks.STONE);
        }
    }

    static ServerPlayer trailingPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(ModEffects.TRAILING, 600, 0, false, false, true));
        return player;
    }

    /** Teleports the player to a standing spot without tracking (see {@link #standAt} for the tracking variant). */
    static void teleport(GameTestHelper helper, ServerPlayer player, BlockPos spot) {
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(spot));
    }

    /** Teleports the player to a spot, sets its ground state, and runs one trail-laying step. */
    static void standAt(GameTestHelper helper, ServerPlayer player, BlockPos spot, boolean grounded) {
        teleport(helper, player, spot);
        player.setOnGround(grounded);
        TrailLayer.track(player);
    }

    static TrailFires fires(GameTestHelper helper) {
        return TrailFires.of(helper.getLevel());
    }

    static boolean light(GameTestHelper helper, ServerPlayer owner, BlockPos spot) {
        return fires(helper).light(helper.getLevel(), owner, helper.absolutePos(spot));
    }

    static void fade(GameTestHelper helper, int ticks) {
        for (int i = 0; i < ticks; i++) {
            fires(helper).fadeTick(helper.getLevel());
        }
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
        return fires(helper).ownerOf(helper.absolutePos(spot));
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
        helper.succeed();
    }

    static void g2NotFromTheAir(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, false);
        standAt(helper, player, B, true);
        helper.assertBlockNotPresent(trailFire(), A);
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
        TrailLayer.track(player);
        player.stopRiding();
        standAt(helper, player, B, true);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.succeed();
    }

    static void g11StopsWithTheEffect(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, true);
        player.removeEffect(ModEffects.TRAILING);
        standAt(helper, player, B, true);
        helper.assertBlockNotPresent(trailFire(), A);
        player.addEffect(new MobEffectInstance(ModEffects.TRAILING, 600, 0, false, false, true));
        standAt(helper, player, C, true);
        helper.assertBlockNotPresent(trailFire(), B);
        standAt(helper, player, D, true);
        helper.assertBlockPresent(trailFire(), C);
        // The tracker was dropped with the effect, so re-casting never lights the old spot A.
        helper.assertBlockNotPresent(trailFire(), A);
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
        TrailFires.Entry trail = fires(helper).entries().stream()
                .filter(e -> e.owner().equals(player.getUUID()))
                .findFirst()
                .orElseThrow(() -> helper.assertionException("the player has a trail"));
        helper.assertValueEqual(trail.flames().size(), 2, "A and B once each");
        helper.succeed();
    }

    static void g4LingersOnSoulSand(GameTestHelper helper) {
        floors(helper);
        helper.setBlock(A.below(), Blocks.SOUL_SAND);
        ServerPlayer player = trailingPlayer(helper);
        helper.assertTrue(light(helper, player, A), "lit over soul sand");
        fade(helper, Trail.LINGER_TICKS - 1);
        helper.assertBlockPresent(trailFire(), A);
        fade(helper, 1);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.succeed();
    }

    static void g5FadesOldestFirst(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        helper.assertTrue(light(helper, player, A), "lit A");
        helper.assertTrue(light(helper, player, B), "lit B");
        fade(helper, Trail.FADE_TICKS);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.assertBlockPresent(trailFire(), B);
        fade(helper, Trail.FADE_TICKS);
        helper.assertBlockNotPresent(trailFire(), B);
        helper.assertTrue(
                fires(helper).entries().stream().noneMatch(e -> e.owner().equals(player.getUUID())),
                "an emptied trail is forgotten");
        helper.succeed();
    }

    /** Mutates global server config, so it runs alone in the {@code config_mutation} batch. */
    static void g6MaxBlocks(GameTestHelper helper) {
        withConfig(ServerConfig.TRAILING_MAX_BLOCKS, 2, () -> {
            floors(helper);
            ServerPlayer player = trailingPlayer(helper);
            helper.assertTrue(light(helper, player, A), "lit A");
            helper.assertTrue(light(helper, player, B), "lit B");
            helper.assertTrue(light(helper, player, C), "lit C");
            helper.assertBlockNotPresent(trailFire(), A);
            helper.assertBlockPresent(trailFire(), B);
            helper.assertBlockPresent(trailFire(), C);
            helper.assertValueEqual(ownerOf(helper, A), Optional.empty(), "owner of the evicted flame");
        });
        helper.succeed();
    }

    /** Puts the owner on a team, so it runs alone in the {@code team_mutation} batch and removes the team after. */
    static void g7SparesOwnerAndAllies(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("spellcraft_trail_test");
        try {
            Zombie teammate = helper.spawn(EntityTypes.ZOMBIE, B);
            // Every mock player shares the profile name "spellcraft-mock", so this also puts leftover mock players
            // on the team while the test runs; removePlayerTeam in the finally clears all of them.
            scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
            Zombie stranger = helper.spawn(EntityTypes.ZOMBIE, C);

            stepInto(helper, A, owner);
            stepInto(helper, A, teammate);
            stepInto(helper, A, stranger);

            assertSpared(helper, owner, "the owner");
            assertSpared(helper, teammate, "a teammate");
            assertBurned(helper, stranger, "a stranger");
        } finally {
            scoreboard.removePlayerTeam(team);
        }
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
        helper.succeed();
    }

    static void g9RandomTick(GameTestHelper helper) {
        floors(helper);
        ServerLevel level = helper.getLevel();
        // An orphan (placed by hand, no owner) puts itself out.
        helper.setBlock(A, trailFire());
        helper.assertValueEqual(ownerOf(helper, A), Optional.empty(), "a hand-placed flame has no owner");
        level.getBlockState(helper.absolutePos(A)).randomTick(level, helper.absolutePos(A), level.getRandom());
        helper.assertBlockNotPresent(trailFire(), A);
        // An owned flame's random tick changes nothing: it stays, and flammable neighbours stay unlit.
        ServerPlayer owner = trailingPlayer(helper);
        helper.assertTrue(light(helper, owner, A), "lit A");
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.OAK_PLANKS);
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.TNT);
        level.getBlockState(helper.absolutePos(A)).randomTick(level, helper.absolutePos(A), level.getRandom());
        helper.assertBlockPresent(trailFire(), A);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, 1, 0));
        helper.assertBlockPresent(Blocks.TNT, new BlockPos(0, 1, 1));
        helper.assertTrue(
                helper.getLevel()
                        .getEntities(EntityTypes.TNT, helper.getBounds(), e -> true)
                        .isEmpty(),
                "no primed TNT");
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
        helper.succeed();
    }

    static void g12CodecRoundTrip(GameTestHelper helper) {
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        TrailFires.Entry xTrail =
                new TrailFires.Entry(x, List.of(new Trail.Flame(10L, false), new Trail.Flame(11L, true)), 7);
        TrailFires.Entry yTrail = new TrailFires.Entry(y, List.of(new Trail.Flame(20L, true)), 33);
        TrailFires original = new TrailFires(List.of(xTrail, yTrail));
        Tag saved =
                TrailFires.CODEC.codec().encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        TrailFires loaded =
                TrailFires.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertValueEqual(loaded.entries(), original.entries(), "entries after the round trip");
        helper.assertValueEqual(loaded.entries(), List.of(xTrail, yTrail), "entries as written");
        helper.assertValueEqual(loaded.ownerOf(BlockPos.of(11L)), Optional.of(x), "owner of x's flame");
        helper.assertValueEqual(loaded.ownerOf(BlockPos.of(20L)), Optional.of(y), "owner of y's flame");
        helper.assertValueEqual(loaded.ownerOf(BlockPos.of(99L)), Optional.empty(), "owner of nothing");
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

    /** A player on soul sand stands 2/16 inside it, so the cell above the sand (the stand-in cell) takes the flame. */
    static void g17StandInCellAboveSoulSand(GameTestHelper helper) {
        floors(helper);
        helper.setBlock(A.below(), Blocks.SOUL_SAND);
        ServerPlayer player = trailingPlayer(helper);
        MockPlayers.moveTo(helper, player, new Vec3(A.getX() + 0.5, A.below().getY() + 0.875, A.getZ() + 0.5));
        helper.assertValueEqual(player.blockPosition(), helper.absolutePos(A.below()), "feet block is the sand");
        player.setOnGround(true);
        TrailLayer.track(player);
        standAt(helper, player, B, true);
        helper.assertBlockPresent(trailFire(), A);
        helper.assertBlockPresent(Blocks.SOUL_SAND, A.below());
        fade(helper, 39);
        helper.assertBlockPresent(trailFire(), A);
        fade(helper, 1);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.succeed();
    }

    /** Carpet is the feet block and has no sturdy top: no flame. A top slab's surface is a full face: it lights. */
    static void g26CarpetAndTopSlab(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        helper.setBlock(A, Blocks.MOSS_CARPET);
        MockPlayers.moveTo(helper, player, new Vec3(A.getX() + 0.5, A.getY() + 1.0 / 16, A.getZ() + 0.5));
        helper.assertValueEqual(player.blockPosition(), helper.absolutePos(A), "feet block is the carpet");
        player.setOnGround(true);
        TrailLayer.track(player);
        standAt(helper, player, B, false);
        helper.assertBlockPresent(Blocks.MOSS_CARPET, A);
        helper.assertBlockNotPresent(trailFire(), A.above());
        helper.setBlock(C, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        standAt(helper, player, C.above(), true);
        standAt(helper, player, D, true);
        helper.assertBlockPresent(trailFire(), C.above());
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
        helper.assertValueEqual(ownerOf(helper, A), Optional.empty(), "gone after y's fade");
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
        // Other tests in the batch leave trails in the same level, so look at this owner's trail only.
        TrailFires.Entry mine = fires(helper).entries().stream()
                .filter(e -> e.owner().equals(owner.getUUID()))
                .findFirst()
                .orElseThrow(() -> helper.assertionException("the owner has a trail"));
        helper.assertValueEqual(mine.flames().size(), 1, "one flame");
        helper.succeed();
    }
}
