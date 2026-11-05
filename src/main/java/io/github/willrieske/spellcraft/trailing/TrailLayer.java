package io.github.willrieske.spellcraft.trailing;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.rules.TrailTracker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Lays each trailing player's flames (every server player tick) and fades every trail (every server level tick).
 * Trackers are transient: dropped on logout and when the server stops, so a relog or level change simply starts a
 * fresh observation.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class TrailLayer {
    /** A player's tracker and the level it observes; a level change replaces the tracker with a fresh one. */
    private record Tracked(ResourceKey<Level> dimension, TrailTracker tracker) {}

    private static final Map<UUID, Tracked> TRACKERS = new HashMap<>();

    private TrailLayer() {}

    /**
     * Runs one trail-laying step for every server-side player after each tick.
     *
     * @param event the post-tick event for a player
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            track(player);
        }
    }

    /**
     * Fades every trail in a server level by one tick.
     *
     * @param event the post-tick event for a level
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            TrailFires.of(level).fadeTick(level);
        }
    }

    /**
     * Drops the tracker of a player who left.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKERS.remove(event.getEntity().getUUID());
    }

    /**
     * Drops every tracker when the server stops (single-player loads one server per world in the same JVM).
     *
     * @param event the server-stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKERS.clear();
    }

    /**
     * One trail-laying step: observe where the player stands and light the block they just left. Without the effect
     * the player's tracker is dropped. The previous block is lit only while its chunk is loaded (after a long
     * teleport it may not be; no chunk is ever loaded to lay or put out a flame, spec §4). Public so GameTests can run
     * a step without waiting for a tick.
     *
     * @param player the server-side player to track
     */
    public static void track(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!player.hasEffect(ModEffects.TRAILING)) {
            TRACKERS.remove(id);
            return;
        }
        ServerLevel level = player.level();
        Tracked tracked = TRACKERS.get(id);
        if (tracked == null || !tracked.dimension().equals(level.dimension())) {
            tracked = new Tracked(level.dimension(), new TrailTracker());
            TRACKERS.put(id, tracked);
        }
        boolean grounded = player.onGround() && !player.isPassenger() && !player.isSpectator();
        tracked.tracker().observe(player.blockPosition().asLong(), grounded).ifPresent(pos -> {
            BlockPos spot = BlockPos.of(pos);
            if (level.isLoaded(spot)) {
                TrailFires.of(level).light(level, player, spot);
            }
        });
    }
}
