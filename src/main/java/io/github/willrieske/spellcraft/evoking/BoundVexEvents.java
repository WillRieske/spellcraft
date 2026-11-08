package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Bound Vexes follow their summoner through a dimension change (Evoking II spec §3.3): the living, non-staying ones
 * within {@link #PORTAL_RANGE} blocks of the summoner when the move starts land on ring cells around the summoner when
 * it ends. Two events, because the arrival event no longer knows where the player left (docs/api-notes.md §21): the
 * followers are noted on {@code EntityTravelToDimensionEvent} and moved on {@code PlayerChangedDimensionEvent}. Staying
 * vexes stay; a moved vex is a new entity instance that keeps its mark (an attachment) and its stay flag (saved as
 * NBT). Each follower draws its ring cell on its own, so two may share one (harmless for vexes, which clip).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class BoundVexEvents {
    /** How far from the leaving summoner a following vex comes along. */
    public static final double PORTAL_RANGE = 16.0;

    /**
     * Per player changing dimension: the vexes to bring. Transient, like {@code EvokingCaster}'s map: an entry lives
     * from the travel event to the changed-dimension event of the same {@code ServerPlayer.teleport} call, and logout
     * and server stop drop it.
     */
    private static final Map<UUID, List<UUID>> FOLLOWERS = new HashMap<>();

    private BoundVexEvents() {}

    /**
     * Notes the followers while the player still stands where they leave. The event is posted before every teleport,
     * same-dimension ones (pearls, chorus fruit, {@code /tp}) included (docs/api-notes.md §21); those, which no
     * changed-dimension event follows, only drop an old note.
     *
     * @param event the travel event
     */
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        FOLLOWERS.remove(id);
        ServerLevel level = player.level();
        if (event.getDimension().equals(level.dimension())) {
            return;
        }
        List<UUID> followers = level
                .getEntitiesOfClass(
                        BoundVex.class,
                        player.getBoundingBox().inflate(PORTAL_RANGE),
                        vex -> vex.isAlive()
                                && !vex.isStaying()
                                && vex.isSummonedBy(id)
                                && vex.distanceTo(player) <= PORTAL_RANGE)
                .stream()
                .map(Entity::getUUID)
                .toList();
        if (!followers.isEmpty()) {
            FOLLOWERS.put(id, followers);
        }
    }

    /**
     * Brings the noted followers to ring cells one block up around the arrived player.
     *
     * @param event the changed-dimension event, posted after the player's move
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<UUID> followers = FOLLOWERS.remove(player.getUUID());
        ServerLevel to = player.level();
        ServerLevel from = to.getServer().getLevel(event.getFrom());
        if (followers == null || from == null) {
            return;
        }
        RandomSource random = to.getRandom();
        BlockPos feet = player.blockPosition();
        for (UUID id : followers) {
            if (from.getEntities().get(id) instanceof BoundVex vex && vex.isAlive()) {
                VexRing.Offset offset = VexRing.offset(random.nextInt(VexRing.COUNT));
                Vec3 spot = Vec3.atCenterOf(feet.offset(offset.dx(), 1, offset.dz()));
                vex.teleport(new TeleportTransition(
                        to, spot, Vec3.ZERO, vex.getYRot(), vex.getXRot(), TeleportTransition.DO_NOTHING));
            }
        }
    }

    /**
     * Drops a logged-out player's note (a cancelled travel event can leave one).
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FOLLOWERS.remove(event.getEntity().getUUID());
    }

    /**
     * Drops every note when the server stops (single-player loads one server per world in the same JVM).
     *
     * @param event the server-stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FOLLOWERS.clear();
    }
}
