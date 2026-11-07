package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.assertActionBarKey;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.drainActionBar;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.requireEffect;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.saved;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.summonedVex;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.withTeam;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.EvokingCaster;
import io.github.willrieske.spellcraft.evoking.EvokingCaster.Request;
import io.github.willrieske.spellcraft.evoking.EvokingRequest;
import io.github.willrieske.spellcraft.particle.ModParticles;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.FangSquare;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * Fang GameTests for Strength of Evoking (spec §9, table E1–E7, E13–E18, E19 for the real tick subscriber, E21 for
 * the request codec and E22 for the server stop), driven through the public {@link EvokingCaster#request}. A mock
 * player is never ticked by the server (docs/api-notes.md §19), so the tests drive the caster with {@link #step}
 * inside each delayed callback, and E19 posts the player-tick event itself. Every test that places fangs registers
 * with padding 13 and {@link #PADDED_MAX_TICKS} (the body's 200 ticks plus the wait for the padded chunks; its padded
 * box is cleared to air, so {@link #floor} lays stone under the square). The caster stands at {@link #FEET}, the
 * structure's middle; targets are pigs or an iron golem (zombies burn in daylight).
 */
public final class FangGameTests {
    /** The caster's block (relative): the middle of the 3×3×3 structure, one above its floor layer. */
    static final BlockPos FEET = new BlockPos(1, 1, 1);

    private static final Vec3 STAND = new Vec3(1.5, 1, 1.5);
    private static final float NORTH = 180.0F;
    private static final float NORTH_EAST = -135.0F;
    private static final int WARMUP = EvokerSpell.WARMUP_TICKS;
    private static final int LONG = 200;
    private static final int PADDING = 13;
    /** The longest {@link #padded} waits for the padded chunks to tick, in wall-clock time. */
    private static final long PADDING_DEADLINE_MILLIS = 15_000L;

    /** How long one wait between polls lasts; a polling tick takes at least this long. */
    private static final long POLL_MILLIS = 10L;

    /**
     * The test's own {@link #LONG} ticks plus the most polling ticks the wait can take: the deadline divided by the
     * poll time (the GameTest server ticks as fast as it can, so a polling tick lasts about {@link #POLL_MILLIS}).
     */
    private static final int PADDED_MAX_TICKS = LONG + (int) (PADDING_DEADLINE_MILLIS / POLL_MILLIS);

    /**
     * The server-stop test clears every player's cast, so it runs in an environment of its own: batches run one after
     * another, and in {@code SHARED} it would drop the pending casts of the fang tests running beside it.
     */
    private static final String SERVER_STOP = "evoking_server_stop";

    private FangGameTests() {}

    static void registerAll() {
        addPadded(SHARED, "fangs_e1_carpet_north", FangGameTests::e1CarpetNorth);
        addPadded(SHARED, "fangs_e2_corner_north_east", FangGameTests::e2CornerNorthEast);
        addPadded(SHARED, "fangs_e3_no_targets_notice", FangGameTests::e3NoTargetsNotice);
        addPadded("team_mutation", "fangs_e4_allies_are_not_targets", FangGameTests::e4AlliesAreNotTargets);
        addPadded(SHARED, "fangs_e5_player_modes", FangGameTests::e5PlayerModes);
        addPadded(SHARED, "fangs_e6_spare_summons", FangGameTests::e6SpareSummons);
        addPadded(SHARED, "fangs_e7_busy_and_cooling", FangGameTests::e7BusyAndCooling);
        addPadded(SHARED, "fangs_e13_lost_evoking_cancels", FangGameTests::e13LostEvokingCancels);
        addPadded(SHARED, "fangs_e14_ignored_requests", FangGameTests::e14IgnoredRequests);
        addPadded(SHARED, "fangs_e15_logout_drops_the_cast", FangGameTests::e15LogoutDropsTheCast);
        addPadded(SHARED, "fangs_e16_dimension_change", FangGameTests::e16DimensionChange);
        addPadded(SHARED, "fangs_e17_uneven_floor", FangGameTests::e17UnevenFloor);
        addPadded(SHARED, "fangs_e18_armor_stand", FangGameTests::e18ArmorStand);
        addPadded(SHARED, "fangs_e19_player_tick_event", FangGameTests::e19PlayerTickEvent);
        addPadded(SERVER_STOP, "fangs_server_stop_drops_the_cast", FangGameTests::serverStopDropsTheCast);
        SpellcraftGameTests.add(SHARED, "fangs_request_codec_round_trip", FangGameTests::requestCodecRoundTrip);
    }

    /**
     * Registers a test that places fangs or vexes far from its structure: padding 13, a tick budget that covers the
     * body plus the wait of {@link #padded}, and the body wrapped in {@link #padded}. The vex tests register their padded
     * tests through this too.
     */
    static void addPadded(String environment, String name, Consumer<GameTestHelper> body) {
        SpellcraftGameTests.add(environment, name, PADDED_MAX_TICKS, PADDING, padded(body));
    }

    /**
     * Steps the caster as the server's player tick would: a mock player is never ticked (docs/api-notes.md §19).
     *
     * @param player the mock player to step
     */
    static void step(ServerPlayer player) {
        EvokingCaster.tick(player);
    }

    /** A survival player with Evoking standing at {@link #FEET}, facing {@code yaw}; loads the padded box first. */
    static ServerPlayer caster(GameTestHelper helper, float yaw) {
        loadPadding(helper);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(EvokingGameTests.evoking());
        MockPlayers.moveTo(helper, player, STAND, yaw);
        return player;
    }

    /**
     * Wraps a padded test: forces every chunk of the padded box, waits until they all tick (a forced chunk is promoted
     * to entity-ticking over the following ticks, and chunk generation runs in real time on worker threads), then runs
     * {@code body}. Every delay in the body counts from that point. The wait is bounded by
     * {@link #PADDING_DEADLINE_MILLIS} of wall-clock time; when it runs out, the test fails naming the first chunk that
     * is not ticking.
     */
    static Consumer<GameTestHelper> padded(Consumer<GameTestHelper> body) {
        return helper -> {
            forcePadding(helper);
            awaitPadding(helper, System.nanoTime() + PADDING_DEADLINE_MILLIS * 1_000_000L, body);
        };
    }

    private static void awaitPadding(GameTestHelper helper, long deadlineNanos, Consumer<GameTestHelper> body) {
        ChunkPos missing = firstNotTicking(helper);
        if (missing == null) {
            body.accept(helper);
        } else if (System.nanoTime() >= deadlineNanos) {
            throw helper.assertionException("setup: padded chunk " + missing + " is not entity-ticking after "
                    + PADDING_DEADLINE_MILLIS / 1000 + " s");
        } else {
            // Keeps running the main-thread chunk tasks while it waits (BlockableEventLoop.managedBlock).
            long until = System.nanoTime() + POLL_MILLIS * 1_000_000L;
            helper.getLevel().getServer().managedBlock(() -> System.nanoTime() >= until);
            helper.runAfterDelay(1, () -> awaitPadding(helper, deadlineNanos, body));
        }
    }

    private static void forcePadding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forEachPaddedChunk(helper, pos -> {
            // Left forced on purpose: the GameTest server is discarded, and neighboring tests share padded chunks.
            level.setChunkForced(pos.x(), pos.z(), true);
            level.getChunk(pos.x(), pos.z()); // loads (and generates) it now, waiting for the worker threads
        });
    }

    private static ChunkPos firstNotTicking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos[] found = new ChunkPos[1];
        forEachPaddedChunk(helper, pos -> {
            if (found[0] == null && !level.areEntitiesActuallyLoadedAndTicking(pos)) {
                found[0] = pos;
            }
        });
        return found[0];
    }

    private static void forEachPaddedChunk(GameTestHelper helper, Consumer<ChunkPos> action) {
        AABB box = helper.getBoundsWithPadding();
        int minX = SectionPos.blockToSectionCoord(Mth.floor(box.minX));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(box.maxX));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(box.minZ));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(box.maxZ));
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                action.accept(new ChunkPos(cx, cz));
            }
        }
    }

    /**
     * Forces every chunk of the padded box (the squares reach 12 blocks past the 3×3 structure, and the framework
     * forces only the structure's chunks) and checks they tick, so a missing fang fails here with its cause. A padded
     * test is wrapped in {@link #padded}, which has already forced and waited for them, so this is idempotent: it
     * re-forces (a no-op) and asserts.
     */
    static void loadPadding(GameTestHelper helper) {
        forcePadding(helper);
        ChunkPos missing = firstNotTicking(helper);
        helper.assertTrue(missing == null, "setup: padded chunk " + missing + " is not entity-ticking");
    }

    /**
     * Sends {@code player} to a prepared pocket in the Nether directly above the player's own coordinates (the same
     * x, y and z), so the pocket covers the fang snapshot's columns: 5×4×5 air inside a stone shell
     * (floor, walls and ceiling), so the player lands alive whatever the terrain there and no lava flows in. Every
     * chunk the pocket touches is forced and loaded first, and every block the shell and pocket replace is recorded.
     * When the test ends, passed or failed ({@link GameTestSupport#whenDone}), the fangs and vexes near the pocket are
     * discarded, the blocks restored, the chunks unforced and the player moved back to {@link #STAND}. Tests in a
     * batch run at the same time, but each has its own absolute position, so their pockets are disjoint.
     *
     * @return the player's feet block in the Nether
     */
    static BlockPos toNether(GameTestHelper helper, ServerPlayer player) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw helper.assertionException("no Nether on the GameTest server: move this test to the playtest");
        }
        BlockPos here = player.blockPosition();
        BlockPos feet = here;
        BlockPos low = feet.offset(-3, -1, -3);
        BlockPos high = feet.offset(3, 4, 3);
        List<ChunkPos> chunks = new ArrayList<>();
        int minCx = SectionPos.blockToSectionCoord(low.getX());
        int maxCx = SectionPos.blockToSectionCoord(high.getX());
        int minCz = SectionPos.blockToSectionCoord(low.getZ());
        int maxCz = SectionPos.blockToSectionCoord(high.getZ());
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        chunks.forEach(chunk -> {
            nether.setChunkForced(chunk.x(), chunk.z(), true);
            nether.getChunk(chunk.x(), chunk.z()); // loaded now, so the carving below lands
        });
        Map<BlockPos, BlockState> was = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            was.put(pos.immutable(), nether.getBlockState(pos));
        }
        was.keySet().forEach(pos -> {
            boolean shell = pos.getX() == low.getX()
                    || pos.getX() == high.getX()
                    || pos.getY() == low.getY()
                    || pos.getY() == high.getY()
                    || pos.getZ() == low.getZ()
                    || pos.getZ() == high.getZ();
            nether.setBlock(pos, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
        });
        whenDone(helper, () -> {
            AABB around = new AABB(feet).inflate(16.0);
            nether.getEntitiesOfClass(EvokerFangs.class, around).forEach(Entity::discard);
            nether.getEntitiesOfClass(Vex.class, around).forEach(Entity::discard);
            was.forEach((pos, state) -> nether.setBlock(pos, state, Block.UPDATE_ALL));
            chunks.forEach(chunk -> nether.setChunkForced(chunk.x(), chunk.z(), false));
            MockPlayers.moveTo(helper, player, STAND);
        });
        player.teleportTo(nether, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), 0.0F, 0.0F, false);
        helper.assertValueEqual(player.level().dimension(), Level.NETHER, "setup: the player is in the Nether");
        helper.assertTrue(
                player.isAlive() && player.hasEffect(ModEffects.EVOKING),
                "setup: alive with Evoking in the Nether, so only the dimension guard can stop the cast");
        return feet;
    }

    /** Lays stone under every cell of the square a caster at {@link #FEET} facing {@code yaw} covers. */
    static void floor(GameTestHelper helper, float yaw) {
        FangSquare square = FangSquare.of(yaw);
        for (int dz = square.minDz(); dz <= square.maxDz(); dz++) {
            for (int dx = square.minDx(); dx <= square.maxDx(); dx++) {
                helper.setBlock(FEET.offset(dx, -1, dz), Blocks.STONE);
            }
        }
    }

    /** Every fang within 14 blocks of the caster's block (the padding keeps other tests' fangs farther away). */
    static List<EvokerFangs> fangs(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(FEET);
        AABB box = new AABB(feet).inflate(14.0, 14.0, 14.0);
        return helper.getLevel().getEntitiesOfClass(EvokerFangs.class, box);
    }

    /** A pig that stays where it is put. */
    static Pig pig(GameTestHelper helper, BlockPos relative) {
        Pig pig = helper.spawn(EntityTypes.PIG, relative);
        pig.setNoAi(true);
        return pig;
    }

    /** The int {@code key} of {@code entity}'s saved NBT, or {@link Integer#MIN_VALUE} when absent. */
    static int savedInt(Entity entity, String key) {
        return saved(entity).getIntOr(key, Integer.MIN_VALUE);
    }

    static void assertInside(GameTestHelper helper, EvokerFangs fang, int minDx, int maxDx, int minDz, int maxDz) {
        BlockPos feet = helper.absolutePos(FEET);
        BlockPos pos = fang.blockPosition();
        int dx = pos.getX() - feet.getX();
        int dz = pos.getZ() - feet.getZ();
        helper.assertTrue(
                dx >= minDx && dx <= maxDx && dz >= minDz && dz <= maxDz,
                "fang at (" + dx + ", " + dz + ") outside the square");
        helper.assertFalse(dx == 0 && dz == 0, "a fang on the caster's cell");
    }

    static void e1CarpetNorth(GameTestHelper helper) {
        floor(helper, NORTH);
        BlockPos carpet = FEET.offset(3, 0, -4); // on the floor layer: its fang stands on the carpet's collision top
        helper.setBlock(carpet, Blocks.MOSS_CARPET);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        MobEffectInstance casting = requireEffect(helper, player, ModEffects.CASTING_FANGS);
        helper.assertValueEqual(casting.getDuration(), EvokerSpell.FANGS.castingTicks(), "casting ticks");
        helper.assertTrue(casting.isVisible(), "visible: the particle syncs");
        helper.assertFalse(casting.showIcon(), "no HUD icon");
        helper.assertTrue(fangs(helper).isEmpty(), "no fangs before the warmup");
        // ChunkMap's sync pass (updateDataBeforeSync) refreshes the list early in the next tick; 2, not 1, for margin.
        helper.runAfterDelay(
                2,
                () -> helper.assertTrue(
                        player.getEntityData()
                                .get(LivingEntity.DATA_EFFECT_PARTICLES)
                                .contains(ModParticles.CASTING_FANGS.get()),
                        "the synced particle list carries the casting particle"));
        helper.runAfterDelay(WARMUP, () -> {
            step(player); // the perform is due now: the fangs are placed by this call and have not ticked yet
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), FangSquare.CELL_COUNT, "one fang per cell");
            EvokerFangs nearest = null;
            EvokerFangs farthest = null;
            BlockPos feet = helper.absolutePos(FEET);
            for (EvokerFangs fang : fangs) {
                assertInside(helper, fang, -6, 6, -12, 0);
                helper.assertTrue(
                        fang.getOwner() != null && fang.getOwner().getUUID().equals(player.getUUID()),
                        "the caster owns the fang");
                BlockPos pos = fang.blockPosition();
                if (pos.equals(feet.north())) {
                    nearest = fang;
                }
                if (pos.equals(feet.offset(-6, 0, -12))) {
                    farthest = fang;
                }
            }
            EvokerFangs onCarpet = null;
            for (EvokerFangs fang : fangs) {
                if (fang.blockPosition().equals(helper.absolutePos(carpet))) {
                    onCarpet = fang;
                }
            }
            if (onCarpet == null) {
                throw helper.assertionException("the carpet's cell holds a fang");
            }
            helper.assertTrue(
                    Math.abs(onCarpet.getY() - (helper.absolutePos(carpet).getY() + 1.0 / 16.0)) < 1.0E-6,
                    "the fang on the carpet stands at the floor's collision top, one sixteenth up: " + onCarpet.getY());
            if (nearest == null || farthest == null) {
                throw helper.assertionException("the cell north of the caster and the far corner both hold a fang");
            }
            helper.assertValueEqual(
                    savedInt(nearest, "Warmup"), 0, "the fang beside the caster has no warmup"); // none has ticked
            helper.assertValueEqual(savedInt(farthest, "Warmup"), 11, "the far corner's fang waits 11 ticks");
            helper.succeed();
        });
    }

    /**
     * A caster facing north-east gets the corner square (a target on its far corner counts: the edge is inside), and
     * the square is the one at the press, though the caster walks on during the warmup.
     */
    static void e2CornerNorthEast(GameTestHelper helper) {
        floor(helper, NORTH_EAST);
        ServerPlayer player = caster(helper, NORTH_EAST);
        pig(helper, FEET.offset(12, 0, -12)); // the far corner of the NE square
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        // Walks on during the warmup, onto the square's floor; assertInside measures from FEET, not the player, so a
        // perform from the live position (fangs over x 3...15) fails it.
        MockPlayers.moveTo(helper, player, STAND.add(3, 0, 0), NORTH_EAST);
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), FangSquare.CELL_COUNT, "one fang per cell");
            fangs.forEach(fang -> assertInside(helper, fang, 0, 12, -12, 0));
            helper.succeed();
        });
    }

    /**
     * Targets outside the square or the vertical window do not count: the press only schedules the notice, a second
     * press moves it, and it arrives once, one warmup after the second press.
     */
    static void e3NoTargetsNotice(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.south(3)); // behind the caster: the square holds dz -12...0
        pig(helper, FEET.offset(0, 0, -13)); // one block past the far edge
        pig(helper, FEET.offset(7, 0, 0)); // one block past the east edge (dx -6...6)
        pig(helper, FEET.offset(0, 8, -3)); // above the window: y in [feet-6, feet+7)
        drainActionBar(player); // discard what joining the level sent
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "request");
        helper.assertFalse(player.hasEffect(ModEffects.CASTING_FANGS), "no arms");
        helper.runAfterDelay(
                5,
                () -> helper.assertValueEqual(
                        EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "a second press"));
        // The first press set the notice for +20; the second moved it to +25. Stepping at +22 must send nothing.
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player);
            helper.assertTrue(drainActionBar(player).isEmpty(), "no notice yet: the second press moved it");
        });
        helper.runAfterDelay(WARMUP + 7, () -> {
            step(player); // +27: past the moved notice time, so this step sends it
            assertActionBarKey(helper, player, EvokingCaster.NO_TARGETS_KEY);
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs");
            helper.succeed();
        });
    }

    /** A tamed wolf (owner on no team), a pig on the caster's team and the caster's own summon are not targets. */
    static void e4AlliesAreNotTargets(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        Wolf pet = helper.spawn(EntityTypes.WOLF, FEET.north(3));
        pet.setNoAi(true);
        pet.tame(player);
        Vex summon = summonedVex(helper, FEET.north(5), player.getUUID());
        helper.assertValueEqual(
                EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "pet and summon are no targets");
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        withTeam(scoreboard, "spellcraft_fangs_e4", team -> {
            Pig teammate = pig(helper, FEET.north(7));
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
            helper.assertValueEqual(
                    EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "a teammate is no target");
        });
        summon.discard();
        helper.succeed();
    }

    /** Creative and spectator players are not targets; a survival player is. */
    static void e5PlayerModes(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        ServerPlayer creative = MockPlayers.inLevel(helper, GameType.CREATIVE);
        MockPlayers.moveTo(helper, creative, STAND.add(0, 0, -4));
        ServerPlayer spectator = MockPlayers.inLevel(helper, GameType.SPECTATOR);
        MockPlayers.moveTo(helper, spectator, STAND.add(0, 0, -6));
        helper.assertValueEqual(
                EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "creative and spectator");
        ServerPlayer survival = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveTo(helper, survival, STAND.add(0, 0, -8));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "survival");
        helper.succeed();
    }

    /**
     * A fang owned by the player bites a stranger pig and a vex nobody summoned, and spares the player's summoned vex.
     */
    static void e6SpareSummons(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        ServerLevel level = helper.getLevel();
        Vex summon = summonedVex(helper, FEET.north(3), player.getUUID());
        Pig stranger = pig(helper, FEET.north(6));
        float vexHealth = summon.getHealth();
        float pigHealth = stranger.getHealth();
        Vex strangerVex = helper.spawn(EntityTypes.VEX, FEET.north(9));
        strangerVex.setNoAi(true);
        float strangerVexHealth = strangerVex.getHealth();
        for (Mob victim : List.of(summon, stranger, strangerVex)) {
            level.addFreshEntity(new EvokerFangs(level, victim.getX(), victim.getY(), victim.getZ(), 0.0F, 0, player));
        }
        // No cast here: the fangs are made directly, so there is nothing to step. They bite on their 8th tick.
        helper.runAfterDelay(12, () -> {
            helper.assertValueEqual(summon.getHealth(), vexHealth, "the summon's health");
            helper.assertTrue(stranger.getHealth() < pigHealth, "the stranger pig is bitten");
            helper.assertTrue(
                    strangerVex.getHealth() < strangerVexHealth,
                    "a vex nobody summoned is bitten: the guard, not vanilla, spares the summon");
            summon.discard();
            strangerVex.discard();
            helper.succeed();
        });
    }

    /** Fangs, then vexes while the arms are up (BUSY), fangs within the interval (COOLING), fangs after it. */
    static void e7BusyAndCooling(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, FEET.north(5));
        golem.setNoAi(true);
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "first");
        helper.runAfterDelay(5, () -> {
            step(player); // nothing is due yet; the arms are still up
            helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.BUSY, "arms up");
        });
        helper.runAfterDelay(50, () -> {
            step(player); // performs the first cast (due at +20): the arms are down, the interval still runs
            helper.assertFalse(fangs(helper).isEmpty(), "the first cast performed");
            helper.assertValueEqual(
                    EvokingCaster.request(player, EvokerSpell.FANGS), Request.COOLING, "within the interval");
        });
        helper.runAfterDelay(EvokerSpell.FANGS.intervalTicks(), () -> {
            step(player);
            helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "after it");
            helper.succeed();
        });
    }

    static void e13LostEvokingCancels(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        player.removeEffect(ModEffects.EVOKING);
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the perform time: without Evoking the step drops the entry instead
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs without Evoking");
            helper.succeed();
        });
    }

    /** Without Evoking, or as a spectator: no effect, no fangs, no notice (the spec row E14). */
    static void e14IgnoredRequests(GameTestHelper helper) {
        floor(helper, NORTH);
        loadPadding(helper);
        pig(helper, FEET.north(5)); // a target, so only the ignoring can explain "no fangs"
        ServerPlayer withoutEvoking = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveTo(helper, withoutEvoking, STAND, NORTH);
        drainActionBar(withoutEvoking); // discard what joining the level sent
        helper.assertValueEqual(
                EvokingCaster.request(withoutEvoking, EvokerSpell.FANGS), Request.IGNORED, "without Evoking");
        helper.assertValueEqual(
                EvokingCaster.request(withoutEvoking, EvokerSpell.VEXES), Request.IGNORED, "without Evoking");
        ServerPlayer spectator = MockPlayers.inLevel(helper, GameType.SPECTATOR);
        spectator.addEffect(EvokingGameTests.evoking());
        MockPlayers.moveTo(helper, spectator, STAND, NORTH);
        drainActionBar(spectator);
        helper.assertValueEqual(EvokingCaster.request(spectator, EvokerSpell.FANGS), Request.IGNORED, "spectator");
        helper.assertFalse(spectator.hasEffect(ModEffects.CASTING_FANGS), "no casting effect");
        helper.runAfterDelay(WARMUP + 7, () -> {
            step(withoutEvoking);
            step(spectator);
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs");
            helper.assertTrue(drainActionBar(withoutEvoking).isEmpty(), "no notice without Evoking");
            helper.assertTrue(drainActionBar(spectator).isEmpty(), "no notice for the spectator");
            helper.succeed();
        });
    }

    static void e15LogoutDropsTheCast(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        EvokingCaster.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the perform time, but the logout dropped the entry: nothing to perform
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs after a logout");
            helper.succeed();
        });
    }

    /** A press in the Overworld, a teleport to the Nether before the perform: no fangs anywhere. */
    static void e16DimensionChange(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        BlockPos netherFeet = toNether(helper, player);
        ServerLevel nether = player.level();
        helper.runAfterDelay(WARMUP + 2, () -> {
            helper.assertTrue(
                    player.isAlive() && player.hasEffect(ModEffects.EVOKING),
                    "setup: still alive with Evoking, so only the dimension guard can stop the fangs");
            helper.assertValueEqual(player.level().dimension(), Level.NETHER, "setup: the player is in the Nether");
            step(player); // past the perform time: the perform runs now, and the dimension guard stops it
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs in the Overworld");
            // A perform without the guard would place them at the snapshot's coordinates, in the Nether.
            AABB snapshotArea = new AABB(helper.absolutePos(FEET)).inflate(14.0, 14.0, 14.0);
            helper.assertTrue(
                    nether.getEntitiesOfClass(EvokerFangs.class, snapshotArea).isEmpty(),
                    "no fangs in the Nether at the snapshot's coordinates");
            AABB pocket = new AABB(netherFeet).inflate(14.0, 14.0, 14.0);
            helper.assertTrue(
                    nether.getEntitiesOfClass(EvokerFangs.class, pocket).isEmpty(), "no fangs around the player");
            helper.succeed();
        });
    }

    /** A pillar cell gets its fang on top; a pig's shallow hole gets one at its floor; a deep hole gets none. */
    static void e17UnevenFloor(GameTestHelper helper) {
        floor(helper, NORTH);
        BlockPos pillar = FEET.offset(2, 0, -3);
        BlockPos shallowHole = FEET.offset(-2, 0, -3);
        BlockPos deepHole = FEET.offset(0, 0, -6);
        helper.setBlock(pillar, Blocks.STONE);
        helper.setBlock(shallowHole.below(), Blocks.AIR);
        helper.setBlock(shallowHole.below(3), Blocks.STONE);
        helper.setBlock(deepHole.below(), Blocks.AIR);
        helper.setBlock(deepHole.below(6), Blocks.STONE);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, shallowHole.below(2));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, pillar.above());
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, shallowHole.below(2));
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, deepHole);
            helper.assertValueEqual(fangs(helper).size(), FangSquare.CELL_COUNT - 1, "every other cell has a fang");
            helper.succeed();
        });
    }

    static void e18ArmorStand(GameTestHelper helper) {
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, FEET.north(4));
        helper.assertValueEqual(
                EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "an armor stand is no target");
        stand.discard();
        helper.succeed();
    }

    /**
     * When the server posts the player-tick event after the warmup, should the real subscriber perform the cast:
     * one fang per cell.
     *
     * <p>Method under test: {@link EvokingCaster#onPlayerTick}
     */
    static void e19PlayerTickEvent(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            // Act
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));

            // Assert
            helper.assertValueEqual(fangs(helper).size(), FangSquare.CELL_COUNT, "one fang per cell");
            helper.succeed();
        });
    }

    /**
     * When the server stops between the press and the perform, should the cast be dropped: no fangs once the
     * warmup has passed (the map is transient, like {@code TrailLayer}'s trackers).
     *
     * <p>Method under test: {@link EvokingCaster#onServerStopped}
     */
    static void serverStopDropsTheCast(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");

        // Act
        EvokingCaster.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));

        // Assert
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the perform time, but the entry is gone: nothing to perform
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs after the server stopped");
            helper.succeed();
        });
    }

    /**
     * When a request is written and read back through its stream codec, should it come back equal for both spells,
     * with no byte left over.
     *
     * <p>Method under test: {@link EvokingRequest#STREAM_CODEC}
     */
    static void requestCodecRoundTrip(GameTestHelper helper) {
        for (EvokerSpell spell : EvokerSpell.values()) {
            // Arrange
            EvokingRequest request = new EvokingRequest(spell);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);

            // Act
            EvokingRequest.STREAM_CODEC.encode(buf, request);
            EvokingRequest decoded = EvokingRequest.STREAM_CODEC.decode(buf);

            // Assert
            helper.assertValueEqual(decoded, request, "round trip of " + spell);
            helper.assertValueEqual(buf.readableBytes(), 0, "bytes left after " + spell);
            buf.release();
        }
        helper.succeed();
    }
}
