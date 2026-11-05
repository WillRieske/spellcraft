package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.A;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.B;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.C;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.floors;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.forget;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.ledger;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.standAt;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.trailFire;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.trailOf;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.trailingPlayer;

import io.github.willrieske.spellcraft.trailing.TrailFires;
import io.github.willrieske.spellcraft.trailing.TrailLayer;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Tracker-lifecycle GameTests for {@link TrailLayer}: logout, server stop, an unloaded chunk and a dimension change
 * (spec §6, table G1–G28). Uses the helpers in {@link TrailGameTests}. Registered in {@link SpellcraftGameTests}.
 */
public final class TrailLayerGameTests {
    /** A spot far from every test structure, in a chunk nothing loads (G22). */
    private static final BlockPos FAR = new BlockPos(100_000, 64, 100_000);

    /** Two standing spots in the Nether, away from the Infinity Nether test at the origin (G28). */
    private static final BlockPos NETHER_B = new BlockPos(16, 100, 16);

    private static final BlockPos NETHER_C = NETHER_B.east(2);

    private TrailLayerGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "trail_g20_logout_drops_the_tracker", TrailLayerGameTests::g20Logout);
        SpellcraftGameTests.add(SHARED, "trail_g21_server_stop_drops_trackers", TrailLayerGameTests::g21ServerStop);
        SpellcraftGameTests.add(
                SHARED, "trail_g22_unloaded_spot_is_not_lit", TrailLayerGameTests::g22UnloadedSpotIsNotLit);
        SpellcraftGameTests.add(
                SHARED, "trail_g28_dimension_change_resets", TrailLayerGameTests::g28DimensionChangeResets);
    }

    static void g20Logout(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, true);
        TrailLayer.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        standAt(helper, player, B, true);
        standAt(helper, player, C, true);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.assertBlockPresent(trailFire(), B);
        forget(helper, player);
        helper.succeed();
    }

    static void g21ServerStop(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, true);
        TrailLayer.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));
        standAt(helper, player, B, true);
        standAt(helper, player, C, true);
        helper.assertBlockNotPresent(trailFire(), A);
        helper.assertBlockPresent(trailFire(), B);
        forget(helper, player);
        helper.succeed();
    }

    /**
     * The block just left is in an unloaded chunk: it is not lit, and nothing loads that chunk. NeoForge's patch to
     * {@code Entity.setPosRaw} loads the chunk an in-level entity moves into (docs/api-notes.md §18), so teleporting
     * the mock player to X would load X itself; a removed player (whose moves load nothing) stands in for one who
     * left X by a long teleport. {@code TrailLayer.track} reads only its effect, level, ground state and position.
     */
    static void g22UnloadedSpotIsNotLit(GameTestHelper helper) {
        floors(helper);
        ServerLevel level = helper.getLevel();
        helper.assertFalse(level.isLoaded(FAR), "setup: the far chunk is not loaded");
        ServerPlayer player = trailingPlayer(helper);
        player.discard();
        placeRemoved(player, Vec3.atBottomCenterOf(FAR));
        helper.assertValueEqual(player.blockPosition(), FAR, "setup: the player stands at the far spot");
        helper.assertFalse(level.isLoaded(FAR), "setup: placing the player loaded nothing");
        TrailLayer.track(player);
        placeRemoved(player, Vec3.atBottomCenterOf(helper.absolutePos(A)));
        TrailLayer.track(player);
        // Reading the far block would load its chunk, so "not lit" is read from the ledger.
        helper.assertValueEqual(ledger(helper).ownerOf(FAR), Optional.empty(), "owner of the far spot");
        helper.assertTrue(trailOf(helper, player).isEmpty(), "nothing was lit");
        helper.assertFalse(level.isLoaded(FAR), "no chunk was loaded");
        TrailLayer.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player)); // drop its tracker
        forget(helper, player);
        helper.succeed();
    }

    /** Moves a removed player (absolute coordinates) and grounds it; neither step loads a chunk. */
    private static void placeRemoved(ServerPlayer player, Vec3 feet) {
        player.setPos(feet.x, feet.y, feet.z);
        player.setOnGround(true);
    }

    /** The same player in another level starts a fresh observation: the overworld spot is never lit from there. */
    static void g28DimensionChangeResets(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw helper.assertionException("no Nether on the GameTest server: move G28 to the playtest");
        }
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        standAt(helper, player, A, true);
        for (BlockPos spot : new BlockPos[] {NETHER_B, NETHER_C}) {
            nether.setBlock(spot.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            nether.setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        standInNether(player, nether, NETHER_B);
        helper.assertValueEqual(player.level().dimension(), Level.NETHER, "setup: the player is in the Nether");
        standInNether(player, nether, NETHER_C);
        TrailFires netherLedger = TrailFires.of(nether);
        try {
            helper.assertBlockNotPresent(trailFire(), A);
            helper.assertTrue(nether.getBlockState(NETHER_B).is(trailFire()), "B is lit in the Nether");
            helper.assertValueEqual(netherLedger.ownerOf(NETHER_B), Optional.of(player.getUUID()), "owner of B");
        } finally {
            netherLedger.forget(player.getUUID());
            nether.setBlock(NETHER_B, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        forget(helper, player);
        helper.succeed();
    }

    private static void standInNether(ServerPlayer player, ServerLevel nether, BlockPos spot) {
        Vec3 feet = Vec3.atBottomCenterOf(spot);
        player.teleportTo(nether, feet.x, feet.y, feet.z, Set.of(), 0.0F, 0.0F, false);
        player.setOnGround(true);
        TrailLayer.track(player);
    }
}
