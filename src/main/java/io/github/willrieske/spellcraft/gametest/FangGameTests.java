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
import io.github.willrieske.spellcraft.evoking.Fangs;
import io.github.willrieske.spellcraft.particle.ModParticles;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
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
 * the request codec and E22 for the server stop) and Evoking II Part 1 (the {@code fangs_ii_*} tests: fangs only under
 * targets, the 14-down scan and the chase fang), driven through the public {@link EvokingCaster#request}. A mock
 * player gets no player tick from the server (docs/api-notes.md §19: no player-tick event, no effect countdown),
 * so the tests drive the caster with {@link #step} inside each delayed callback, and E19 posts the player-tick
 * event itself. Every test that places fangs registers with padding 13 and {@link #PADDED_MAX_TICKS} (the body's 200
 * ticks plus the wait for the padded chunks; its padded box is cleared to air, so {@link #floor} lays stone under the
 * square). The caster stands at {@link #FEET}, the structure's middle; targets are pigs or a ravager (zombies burn in
 * daylight; a calm iron golem is an ally).
 */
public final class FangGameTests {
    /** The caster's block (relative): the middle of the 3×3×3 structure, one above its floor layer. */
    static final BlockPos FEET = new BlockPos(1, 1, 1);

    /** Where the caster stands (relative): the middle of the {@link #FEET} block; tests send a player back here. */
    static final Vec3 STAND = new Vec3(1.5, 1, 1.5);

    /** The yaw a caster faces to look north; the vex GameTests share it. */
    static final float NORTH = 180.0F;

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

    /** The {@code pvp} game rule is global, so the test that turns it off runs alone in this environment. */
    private static final String PVP_OFF = "evoking_pvp_off";

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
        addPadded(SHARED, "fangs_e23_death_cancels", FangGameTests::e23DeathCancels);
        addPadded(SHARED, "fangs_e23_death_cancels_the_notice", FangGameTests::e23DeathCancelsTheNotice);
        addPadded(SHARED, "fangs_e23_dead_request_ignored", FangGameTests::e23DeadRequestIgnored);
        addPadded(PVP_OFF, "fangs_e24_pvp_off_notice", FangGameTests::e24PvpOffNotice);
        addPadded(SHARED, "fangs_ii_footprints", FangGameTests::iiFootprints);
        addPadded(SHARED, "fangs_ii_scan_window", FangGameTests::iiScanWindow);
        addPadded(SHARED, "fangs_ii_floating_targets", FangGameTests::iiFloatingTargets);
        addPadded(SHARED, "fangs_ii_chase", FangGameTests::iiChase);
        addPadded(SHARED, "fangs_ii_chase_once_per_target", FangGameTests::iiChaseOncePerTarget);
        addPadded(SHARED, "fangs_ii_chase_range", FangGameTests::iiChaseRange);
        addPadded(SHARED, "fangs_ii_chase_dead_target", FangGameTests::iiChaseDeadTarget);
        addPadded(SHARED, "fangs_ii_chase_no_floor", FangGameTests::iiChaseNoFloor);
        addPadded(SHARED, "fangs_ii_chase_record_leaves_with_the_fang", FangGameTests::iiChaseRecordLeaves);
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
     * Like {@link #addPadded(String, String, Consumer)} for a body that runs longer than {@link #LONG} ticks.
     *
     * @param environment the test environment
     * @param name the test's name
     * @param bodyTicks how many ticks the body itself may take, after the wait of {@link #padded}
     * @param body the test body
     */
    static void addPadded(String environment, String name, int bodyTicks, Consumer<GameTestHelper> body) {
        int polls = (int) (PADDING_DEADLINE_MILLIS / POLL_MILLIS);
        SpellcraftGameTests.add(environment, name, bodyTicks + polls, PADDING, padded(body));
    }

    /**
     * Steps the caster as the server's player tick would: a mock player gets no player tick (docs/api-notes.md §19).
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
        return fangs(helper, 14.0);
    }

    /** Every fang within 14 blocks of the caster's block sideways and {@code vertical} blocks up or down. */
    static List<EvokerFangs> fangs(GameTestHelper helper, double vertical) {
        BlockPos feet = helper.absolutePos(FEET);
        AABB box = new AABB(feet).inflate(14.0, vertical, 14.0);
        return helper.getLevel().getEntitiesOfClass(EvokerFangs.class, box);
    }

    /**
     * Teleports {@code entity} to {@code (base.x + dx, base.y, base.z + dz)}, relative coordinates.
     *
     * @param helper the test helper
     * @param entity the entity to move
     * @param base the block the offsets count from
     * @param dx the east offset in blocks
     * @param dz the south offset in blocks
     */
    static void moveTo(GameTestHelper helper, Entity entity, BlockPos base, double dx, double dz) {
        Vec3 target = helper.absoluteVec(new Vec3(base.getX() + dx, base.getY(), base.getZ() + dz));
        entity.teleportTo(target.x, target.y, target.z);
    }

    /** A ravager that stays where it is put and is discarded when the test ends, passed or failed. */
    static Ravager ravager(GameTestHelper helper, BlockPos relative) {
        Ravager ravager = helper.spawn(EntityTypes.RAVAGER, relative);
        ravager.setNoAi(true);
        whenDone(helper, ravager::discard);
        return ravager;
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

    /**
     * When a caster facing north presses the key with a pig on a carpet in the square, should the cast start, the
     * casting effect sync its particle, and one warmup later exactly one fang stand under the pig, on the carpet's
     * collision top, owned by the caster, with no warmup delay.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e1CarpetNorth(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        BlockPos carpet = FEET.offset(3, 0, -4); // on the floor layer: its fang stands on the carpet's collision top
        helper.setBlock(carpet, Blocks.MOSS_CARPET);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, carpet);

        // Act & Assert
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
            step(player); // the perform is due now: the fang is placed by this call and has not ticked yet
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), 1, "one fang, under the pig");
            EvokerFangs fang = fangs.get(0);
            assertInside(helper, fang, -6, 6, -12, 0);
            helper.assertValueEqual(fang.blockPosition(), helper.absolutePos(carpet), "the fang is on the pig's block");
            helper.assertTrue(
                    fang.getOwner() != null && fang.getOwner().getUUID().equals(player.getUUID()),
                    "the caster owns the fang");
            helper.assertTrue(
                    Math.abs(fang.getY() - (helper.absolutePos(carpet).getY() + 1.0 / 16.0)) < 1.0E-6,
                    "the fang stands at the carpet's collision top, one sixteenth up: " + fang.getY());
            helper.assertValueEqual(savedInt(fang, "Warmup"), 0, "no ripple: the fang has no warmup"); // not ticked yet
            helper.succeed();
        });
    }

    /**
     * When a caster facing north-east presses the key with a target on the square's far corner and then walks on,
     * should the one fang stand on the corner cell taken at the press, not one from the live position.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e2CornerNorthEast(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH_EAST);
        ServerPlayer player = caster(helper, NORTH_EAST);
        pig(helper, FEET.offset(12, 0, -12)); // the far corner of the NE square

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        // Walks on during the warmup, onto the square's floor; assertInside measures from FEET, not the player, so a
        // perform from the live position (fangs over x 3...15) fails it.
        MockPlayers.moveTo(helper, player, STAND.add(3, 0, 0), NORTH_EAST);
        helper.runAfterDelay(WARMUP, () -> {
            step(player);

            // Assert
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), 1, "one fang, under the pig on the corner");
            fangs.forEach(fang -> assertInside(helper, fang, 0, 12, -12, 0));
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, FEET.offset(12, 0, -12));
            helper.succeed();
        });
    }

    /**
     * When every pig stands outside the square or the vertical window (behind, past an edge, above, below), should
     * the press only schedule the notice, a second press move it, and the notice arrive once, one warmup after the
     * second press.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e3NoTargetsNotice(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.south(3)); // behind the caster: the square holds dz -12...0
        pig(helper, FEET.offset(0, 0, -13)); // one block past the far edge
        pig(helper, FEET.offset(7, 0, 0)); // one block past the east edge (dx -6...6)
        pig(helper, FEET.offset(0, 8, -3)); // above the window: y in [feet-14, feet+7)
        helper.assertTrue(
                helper.absolutePos(FEET).getY() - 17 > helper.getLevel().getMinY(),
                "setup: the world has room 17 blocks under the caster");
        helper.setBlock(FEET.offset(0, -17, -3), Blocks.STONE); // a floor for the pig below the window
        pig(helper, FEET.offset(0, -16, -3)); // below the window: its top (0.9 tall) is under feet-14
        drainActionBar(player); // discard what joining the level sent

        // Act & Assert
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

    /**
     * When the only living things in the square are a tamed wolf, a pig on the caster's team and the caster's own
     * summoned vex, should each press find no target.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e4AlliesAreNotTargets(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        Wolf pet = helper.spawn(EntityTypes.WOLF, FEET.north(3));
        pet.setNoAi(true);
        pet.tame(player);
        Vex summon = summonedVex(helper, FEET.north(5), player.getUUID());

        // Act & Assert
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

    /**
     * When the square holds a creative and a spectator player, should the press find no target; when it also holds a
     * survival player (with {@code pvp} on), should the cast start.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e5PlayerModes(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        ServerPlayer creative = MockPlayers.inLevel(helper, GameType.CREATIVE);
        MockPlayers.moveTo(helper, creative, STAND.add(0, 0, -4));
        ServerPlayer spectator = MockPlayers.inLevel(helper, GameType.SPECTATOR);
        MockPlayers.moveTo(helper, spectator, STAND.add(0, 0, -6));

        // Act & Assert
        helper.assertValueEqual(
                EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "creative and spectator");
        ServerPlayer survival = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveTo(helper, survival, STAND.add(0, 0, -8));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "survival");
        helper.succeed();
    }

    /**
     * When fangs owned by the player land on a stranger pig, a vex nobody summoned and the player's summoned vex,
     * should they bite the first two and spare the summon.
     *
     * <p>Method under test: {@link Fangs#onIncomingDamage}
     */
    static void e6SpareSummons(GameTestHelper helper) {
        // Arrange
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

        // Act
        for (Mob victim : List.of(summon, stranger, strangerVex)) {
            level.addFreshEntity(new EvokerFangs(level, victim.getX(), victim.getY(), victim.getZ(), 0.0F, 0, player));
        }
        // No cast here: the fangs are made directly, so there is nothing to step. They bite on their 8th tick.
        helper.runAfterDelay(12, () -> {
            // Assert
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

    /**
     * When a caster presses fangs, then vexes while the arms are up, then fangs within the interval, then fangs after
     * it, should the answers be BUSY, COOLING and STARTED in turn.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e7BusyAndCooling(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));

        // Act & Assert
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

    /**
     * When the caster loses Evoking between the press and the perform, should the step drop the cast: no fangs.
     *
     * <p>Method under test: {@link EvokingCaster#tick}
     */
    static void e13LostEvokingCancels(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");

        // Act
        player.removeEffect(ModEffects.EVOKING);
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the perform time: without Evoking the step drops the entry instead

            // Assert
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs without Evoking");
            helper.succeed();
        });
    }

    /**
     * When a player without Evoking, or a spectator with it, presses either key with a target in the square, should
     * the request be ignored: no effect, no fangs, no notice (spec row E14).
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e14IgnoredRequests(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        loadPadding(helper);
        pig(helper, FEET.north(5)); // a target, so only the ignoring can explain "no fangs"
        ServerPlayer withoutEvoking = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveTo(helper, withoutEvoking, STAND, NORTH);
        drainActionBar(withoutEvoking); // discard what joining the level sent
        ServerPlayer spectator = MockPlayers.inLevel(helper, GameType.SPECTATOR);
        spectator.addEffect(EvokingGameTests.evoking());
        MockPlayers.moveTo(helper, spectator, STAND, NORTH);
        drainActionBar(spectator);

        // Act & Assert
        helper.assertValueEqual(
                EvokingCaster.request(withoutEvoking, EvokerSpell.FANGS), Request.IGNORED, "without Evoking");
        helper.assertValueEqual(
                EvokingCaster.request(withoutEvoking, EvokerSpell.VEXES), Request.IGNORED, "without Evoking");
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

    /**
     * When the caster logs out between the press and the perform (the logout event posted on the bus), should the
     * cast be dropped: no fangs.
     *
     * <p>Method under test: {@link EvokingCaster#onLogout}
     */
    static void e15LogoutDropsTheCast(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");

        // Act
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the perform time, but the logout dropped the entry: nothing to perform

            // Assert
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs after a logout");
            helper.succeed();
        });
    }

    /**
     * When the caster presses in the Overworld and is teleported to the Nether before the perform, should the
     * dimension guard stop the cast: no fangs in the Overworld, none at the snapshot's coordinates in the Nether, none
     * around the player.
     *
     * <p>Method under test: {@link Fangs#perform}
     */
    static void e16DimensionChange(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");

        // Act
        BlockPos netherFeet = toNether(helper, player);
        ServerLevel nether = player.level();
        helper.runAfterDelay(WARMUP + 2, () -> {
            helper.assertTrue(
                    player.isAlive() && player.hasEffect(ModEffects.EVOKING),
                    "setup: still alive with Evoking, so only the dimension guard can stop the fangs");
            helper.assertValueEqual(player.level().dimension(), Level.NETHER, "setup: the player is in the Nether");
            step(player); // past the perform time: the perform runs now, and the dimension guard stops it

            // Assert
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

    /**
     * When the square has a pig on a one-block pillar, a pig at the bottom of a two-deep hole and a pig at the bottom
     * of a five-deep hole (all within the 14-down scan), should each get one fang at its own floor height and no other
     * cell get one.
     *
     * <p>Method under test: {@link Fangs#perform}
     */
    static void e17UnevenFloor(GameTestHelper helper) {
        // Arrange
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
        pig(helper, pillar.above());
        pig(helper, shallowHole.below(2));
        pig(helper, deepHole.below(5));

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);

            // Assert
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, pillar.above());
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, shallowHole.below(2));
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, deepHole.below(5));
            helper.assertValueEqual(fangs(helper).size(), 3, "one fang per pig and none elsewhere");
            helper.succeed();
        });
    }

    /**
     * When the only thing in the square is an armor stand, should the press find no target.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e18ArmorStand(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, FEET.north(4));

        // Act & Assert
        helper.assertValueEqual(
                EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "an armor stand is no target");
        stand.discard();
        helper.succeed();
    }

    /**
     * When the server posts the player-tick event after the warmup, should the real subscriber perform the cast:
     * one fang under the pig.
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
            helper.assertValueEqual(fangs(helper).size(), 1, "one fang, under the pig");
            helper.succeed();
        });
    }

    /**
     * When the caster dies between the press and the perform, should the step place no fangs: Evoking survives death,
     * so the perform-time alive check is the only thing that stops the cast.
     *
     * <p>Method under test: {@link EvokingCaster#tick}
     */
    static void e23DeathCancels(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");

        // Act
        player.kill(helper.getLevel());

        // Assert
        helper.assertTrue(
                !player.isAlive() && player.hasEffect(ModEffects.EVOKING),
                "setup: dead with Evoking, so only the alive check can stop the cast");
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the perform time: the entry is due, and the alive check stops it
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs from a dead caster");
            helper.succeed();
        });
    }

    /**
     * When the caster dies after a no-targets press, should no notice arrive once the delay has passed.
     *
     * <p>Method under test: {@link EvokingCaster#tick}
     */
    static void e23DeathCancelsTheNotice(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        drainActionBar(player); // discard what joining the level sent
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "request");

        // Act
        player.kill(helper.getLevel());
        drainActionBar(player); // discard what dying sent

        // Assert
        helper.assertTrue(
                !player.isAlive() && player.hasEffect(ModEffects.EVOKING),
                "setup: dead with Evoking, so only the alive check can stop the notice");
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the notice time: the entry is due, and the alive check stops it
            helper.assertTrue(drainActionBar(player).isEmpty(), "no notice for a dead player");
            helper.succeed();
        });
    }

    /**
     * When a dead player presses the key with a target in the square, should the request be ignored: no casting
     * effect, and no fangs after the warmup.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e23DeadRequestIgnored(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5)); // a target, so only the alive check can explain "ignored"
        player.kill(helper.getLevel());
        helper.assertTrue(
                !player.isAlive() && player.hasEffect(ModEffects.EVOKING),
                "setup: dead with Evoking, so only the alive check can ignore the request");

        // Act
        Request result = EvokingCaster.request(player, EvokerSpell.FANGS);

        // Assert
        helper.assertValueEqual(result, Request.IGNORED, "request from a dead player");
        helper.assertFalse(player.hasEffect(ModEffects.CASTING_FANGS), "no casting effect");
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player);
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs");
            helper.succeed();
        });
    }

    /**
     * When {@code pvp} is off and the only living thing in the square is another player, should the press find no
     * target: the notice arrives and no fang is placed. E5 is the positive control with {@code pvp} on.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void e24PvpOffNotice(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        ServerPlayer other = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveTo(helper, other, STAND.add(0, 0, -4));
        GameRules rules = helper.getLevel().getGameRules();
        boolean saved = rules.get(GameRules.PVP);
        MinecraftServer server = helper.getLevel().getServer();
        whenDone(helper, () -> rules.set(GameRules.PVP, saved, server));
        rules.set(GameRules.PVP, false, server);
        drainActionBar(player); // discard what joining the level sent

        // Act
        Request result = EvokingCaster.request(player, EvokerSpell.FANGS);

        // Assert
        helper.assertValueEqual(result, Request.NO_TARGETS, "a player is no target with pvp off");
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player);
            assertActionBarKey(helper, player, EvokingCaster.NO_TARGETS_KEY);
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs");
            helper.succeed();
        });
    }

    /**
     * When the server stops between the press and the perform, should the cast be dropped: no fangs once the
     * warmup has passed (the map is transient, like {@code TrailLayer}'s trackers). When it stops after a fang with a
     * chase record was placed, should the record be gone too.
     *
     * <p>Method under test: {@link EvokingCaster#onServerStopped}, and {@link Fangs#onServerStopped}
     */
    static void serverStopDropsTheCast(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        ServerStoppedEvent stopped = new ServerStoppedEvent(helper.getLevel().getServer());
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");

        // Act
        EvokingCaster.onServerStopped(stopped);

        // Assert
        helper.runAfterDelay(WARMUP + 2, () -> {
            step(player); // past the perform time, but the entry is gone: nothing to perform
            helper.assertTrue(fangs(helper).isEmpty(), "no fangs after the server stopped");
        });
        // Arrange: a second cast that performs, so a fang carries a chase record
        helper.runAfterDelay(WARMUP + EvokerSpell.FANGS.intervalTicks(), () -> {
            step(player);
            helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "second cast");
        });
        helper.runAfterDelay(2 * WARMUP + EvokerSpell.FANGS.intervalTicks(), () -> {
            step(player);
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), 1, "the second cast placed its fang");
            helper.assertTrue(Fangs.hasChaseRecord(fangs.get(0)), "setup: the fang has a chase record");

            // Act
            Fangs.onServerStopped(stopped);

            // Assert
            helper.assertFalse(Fangs.hasChaseRecord(fangs.get(0)), "the stop dropped the chase record");
            helper.succeed();
        });
    }

    /**
     * When the square holds a ravager (1.95 wide, on a block center), a pig, two pigs stacked in one column on one
     * floor, and two pigs in one column on different floors (one on the ground, one on a stone ledge whose top is
     * three blocks up), should one fang rise under every block the ravager covers (3×3), one under the lone pig, one
     * for the stacked pair (same spot: duplicates collapse), two for the ledge pair (different floors), and none on an
     * empty cell: thirteen in all.
     *
     * <p>Method under test: {@link Fangs#snapshot}, through {@link EvokingCaster#request}
     */
    static void iiFootprints(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos ravagerBlock = FEET.offset(0, 0, -6);
        ravager(helper, ravagerBlock);
        BlockPos lonePig = FEET.offset(4, 0, -3);
        pig(helper, lonePig);
        BlockPos stacked = FEET.offset(-4, 0, -3);
        pig(helper, stacked);
        pig(helper, stacked.above()); // its box covers the same column and its floor search finds the same floor
        BlockPos ground = FEET.offset(-4, 0, -8);
        BlockPos ledge = ground.above(2); // a stone ledge two blocks over the ground pig's head
        helper.setBlock(ledge, Blocks.STONE);
        pig(helper, ground);
        pig(helper, ledge.above()); // standing on the ledge: a different floor in the same column

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);

            // Assert
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, ravagerBlock.offset(dx, 0, dz));
                }
            }
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, lonePig);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, stacked);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, ground);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, ledge.above());
            helper.assertValueEqual(
                    fangs(helper).size(), 13, "9 under the ravager, 1 under the pig, 1 for the stack, 2 for the ledge");
            helper.succeed();
        });
    }

    /**
     * When one pig stands 14 blocks below the caster and one 6 above (the last blocks inside the scan), while one
     * stands 15 below and one 7 above (the first blocks outside), should the two outside ones alone give the
     * no-targets answer, and with the two inside ones added the cast start and place a fang under each, at its own
     * height.
     *
     * <p>Method under test: {@link Fangs#snapshot}, through {@link EvokingCaster#request}
     */
    static void iiScanWindow(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        helper.assertTrue(
                helper.absolutePos(FEET).getY() - 17 > helper.getLevel().getMinY(),
                "setup: the world has room 17 blocks under the caster");
        ServerPlayer player = caster(helper, NORTH);
        BlockPos tooDeep = FEET.offset(-3, -15, -3);
        BlockPos tooHigh = FEET.offset(3, 7, -3);
        helper.setBlock(tooDeep.below(), Blocks.STONE);
        helper.setBlock(tooHigh.below(), Blocks.STONE);
        pig(helper, tooDeep); // its top is under feet-14
        pig(helper, tooHigh); // its feet are at feet+7, the window's open end

        // Act & Assert: the two outside the window
        helper.assertValueEqual(
                EvokingCaster.request(player, EvokerSpell.FANGS), Request.NO_TARGETS, "15 down and 7 up are outside");

        // Arrange: the two inside
        BlockPos deep = FEET.offset(-3, -14, -5);
        BlockPos high = FEET.offset(3, 6, -5);
        helper.setBlock(deep.below(), Blocks.STONE);
        helper.setBlock(high.below(), Blocks.STONE);
        pig(helper, deep);
        pig(helper, high);

        // Act & Assert: the cast finds them and the fangs stand at their heights
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "14 down and 6 up");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            List<EvokerFangs> fangs = fangs(helper, 20.0);
            helper.assertValueEqual(fangs.size(), 2, "one fang per pig inside the window");
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, deep);
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, high);
            helper.succeed();
        });
    }

    /**
     * When a pig hangs two blocks over the floor (inside the floor window) and another three blocks over it (outside),
     * should the first get a fang on the floor under it and the second none: the floor search runs from one block
     * above a target's feet to two below.
     *
     * <p>Method under test: {@link Fangs#snapshot}
     */
    static void iiFloatingTargets(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos twoUp = FEET.offset(-4, 2, -3); // feet two blocks over the floor at FEET.y
        BlockPos threeUp = FEET.offset(4, 3, -3);
        pig(helper, twoUp); // a no-AI pig does not fall
        pig(helper, threeUp);

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);

            // Assert
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, FEET.offset(-4, 0, -3));
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, FEET.offset(4, 0, -3));
            helper.assertValueEqual(fangs(helper).size(), 1, "only the pig within two blocks of a floor gets a fang");
            helper.succeed();
        });
    }

    /**
     * When a pig steps three blocks east after its fang is placed and before it bites, should one chase fang rise under
     * the pig's new block on the bite tick, a second move give no further fang, and a pig that became the caster's
     * ally after the press get no chase.
     *
     * <p>Method under test: {@link Fangs#onEntityTick}
     */
    static void iiChase(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos start = FEET.north(5);
        Pig runner = pig(helper, start);
        BlockPos allyStart = FEET.offset(-3, 0, -3);
        Pig turnedAlly = pig(helper, allyStart);
        float runnerHealth = runner.getHealth();

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertValueEqual(fangs(helper).size(), 2, "one fang under each pig");
            // Both pigs move before the bite (the fangs' 8th tick); one has become an ally meanwhile.
            moveTo(helper, runner, start, 3.5, 0.5);
            turnedAlly.setData(AllyAttachments.SUMMONER, player.getUUID());
            moveTo(helper, turnedAlly, allyStart, 2.5, 0.5);
        });
        helper.runAfterDelay(WARMUP + Fangs.BITE_TICK + 2, () -> {
            // Assert: one chase fang, under the runner's new block, none for the ally
            helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, start.east(3));
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, allyStart.east(2));
            List<EvokerFangs> all = fangs(helper);
            helper.assertValueEqual(all.size(), 3, "the two first fangs and one chase fang");
            BlockPos chaseCell = helper.absolutePos(start.east(3));
            EvokerFangs chaseFang = all.stream()
                    .filter(fang -> fang.blockPosition().equals(chaseCell))
                    .findFirst()
                    .orElseThrow();
            helper.assertFalse(Fangs.hasChaseRecord(chaseFang), "the chase fang carries no record: it never chases");
            // Act: the runner moves again before the chase fang bites
            moveTo(helper, runner, start, 5.5, 0.5);
        });
        helper.runAfterDelay(WARMUP + 2 * Fangs.BITE_TICK + 4, () -> {
            // Assert: no second chase; the first fangs may have died by now, so check only the new cell
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, start.east(5));
            helper.assertValueEqual(runner.getHealth(), runnerHealth, "a chase fang never chases; the runner escaped");
            helper.succeed();
        });
    }

    /**
     * When a ravager with nine fangs under it steps four blocks east before they bite, should exactly nine chase fangs
     * rise under its new footprint (the nine first fangs settle one chase, not nine); when a pig shifts within its own
     * block, should no chase fang rise (it still stands on its column).
     *
     * <p>Method under test: {@link Fangs#onEntityTick}
     */
    static void iiChaseOncePerTarget(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos ravagerBlock = FEET.offset(-2, 0, -6);
        Ravager ravager = ravager(helper, ravagerBlock);
        BlockPos pigBlock = FEET.offset(4, 0, -3);
        Pig shifter = pig(helper, pigBlock);

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertValueEqual(fangs(helper).size(), 10, "nine under the ravager, one under the pig");
            moveTo(helper, ravager, ravagerBlock, 4.5, 0.5);
            moveTo(helper, shifter, pigBlock, 0.8, 0.2); // still inside its block
        });
        helper.runAfterDelay(WARMUP + Fangs.BITE_TICK + 2, () -> {
            // Assert
            for (int dx = 3; dx <= 5; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertEntityPresent(EntityTypes.EVOKER_FANGS, ravagerBlock.offset(dx, 0, dz));
                }
            }
            helper.assertValueEqual(fangs(helper).size(), 19, "ten first fangs and exactly nine chase fangs");
            helper.succeed();
        });
    }

    /**
     * When the caster has walked more than 16 blocks from a pig by the time its fang bites, and the pig has stepped off
     * its block, should no chase fang rise.
     *
     * <p>Method under test: {@link Fangs#onEntityTick}
     */
    static void iiChaseRange(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos start = FEET.north(5);
        Pig runner = pig(helper, start);

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertValueEqual(fangs(helper).size(), 1, "one fang under the pig");
            // The caster walks 12 blocks south (still inside the padded, ticking box): 17 blocks from the pig.
            MockPlayers.moveTo(helper, player, STAND.add(0, 0, 12), NORTH);
            moveTo(helper, runner, start, 2.5, 0.5);
            helper.assertTrue(player.distanceTo(runner) > Fangs.CHASE_RANGE, "setup: the pig is out of chase range");
        });
        helper.runAfterDelay(WARMUP + Fangs.BITE_TICK + 2, () -> {
            // Assert
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, start.east(2));
            helper.assertValueEqual(fangs(helper).size(), 1, "no chase fang beyond 16 blocks");
            helper.succeed();
        });
    }

    /**
     * When a pig is killed and moved off its column after its fang is placed and before the fang bites, should no
     * chase fang rise for the corpse (the chase needs a living target).
     *
     * <p>Method under test: {@link Fangs#onEntityTick}
     */
    static void iiChaseDeadTarget(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos start = FEET.north(5);
        Pig runner = pig(helper, start);

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertValueEqual(fangs(helper).size(), 1, "one fang under the pig");
            runner.kill(helper.getLevel());
            moveTo(helper, runner, start, 3.5, 0.5);
            helper.assertFalse(runner.isAlive(), "setup: the pig is dead");
        });
        helper.runAfterDelay(WARMUP + Fangs.BITE_TICK + 2, () -> {
            // Assert
            helper.assertEntityNotPresent(EntityTypes.EVOKER_FANGS, start.east(3));
            helper.assertValueEqual(fangs(helper).size(), 1, "no chase fang for a dead target");
            helper.succeed();
        });
    }

    /**
     * When a pig is lifted six blocks into the air (over no floor in the floor window) after its fang is placed and
     * before it bites, should no chase fang rise: the floor search finds nothing, so nothing is placed.
     *
     * <p>Method under test: {@link Fangs#onEntityTick}
     */
    static void iiChaseNoFloor(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        BlockPos start = FEET.north(5);
        Pig runner = pig(helper, start); // a no-AI pig does not fall

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            helper.assertValueEqual(fangs(helper).size(), 1, "one fang under the pig");
            Vec3 up = helper.absoluteVec(new Vec3(start.getX() + 3.5, start.getY() + 6, start.getZ() + 0.5));
            runner.teleportTo(up.x, up.y, up.z);
        });
        helper.runAfterDelay(WARMUP + Fangs.BITE_TICK + 2, () -> {
            // Assert
            helper.assertValueEqual(fangs(helper, 20.0).size(), 1, "no chase fang over no floor");
            helper.succeed();
        });
    }

    /**
     * When a fang with a chase record bites and then dies of age, should its record exist while it lives and be gone
     * once the fang is removed.
     *
     * <p>Method under test: {@link Fangs#onEntityLeave}
     */
    static void iiChaseRecordLeaves(GameTestHelper helper) {
        // Arrange
        floor(helper, NORTH);
        ServerPlayer player = caster(helper, NORTH);
        pig(helper, FEET.north(5));
        EvokerFangs[] placed = new EvokerFangs[1];

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.FANGS), Request.STARTED, "request");
        helper.runAfterDelay(WARMUP, () -> {
            step(player);
            List<EvokerFangs> fangs = fangs(helper);
            helper.assertValueEqual(fangs.size(), 1, "one fang");
            placed[0] = fangs.get(0);
            helper.assertTrue(Fangs.hasChaseRecord(placed[0]), "the fang has a chase record while it lives");
        });
        // A fang with delay 0 bites on its 8th tick and discards itself on its 23rd (docs/api-notes.md §20).
        helper.runAfterDelay(WARMUP + 40, () -> {
            // Assert
            helper.assertTrue(placed[0].isRemoved(), "the fang died of age");
            helper.assertFalse(Fangs.hasChaseRecord(placed[0]), "its record left with it");
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
