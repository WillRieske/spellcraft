package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.rules.VoidRule;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The void catch (Parts 4–5 spec §6.2): in the End, a falling summoner is dismounted, stilled and teleported to the
 * nearest standable column or the last ground they stood on, and fed the enchanted golden apple's effects, and the
 * rescuer shows the apple as the tail. {@link VexRescues} is the dispatcher: it calls the hooks here from its tick, its
 * logout handler and its server-stop clear, and keeps the rescue itself. This class holds what only the catch needs:
 * the need test, the landing search with its pause, and the two per-player maps. All state is transient.
 */
final class VoidCatch {
    /** How far down a falling player is checked for ground. */
    private static final double GROUND_CHECK = 64.0;

    /**
     * After a void catch found no landing and no last ground, or its teleport was refused, the landing search pauses
     * this long.
     */
    private static final int NO_LANDING_PAUSE = 20;

    /**
     * The last standable ground of each player in the End, where they stood. Recorded only in the End, because the
     * only reader is the void catch, which acts only there; an entry of a player who left the End stays until they
     * log out.
     */
    private static final Map<UUID, Vec3> LAST_GROUND = new HashMap<>();

    /**
     * The game time before which the landing search is skipped for each player (the last one found nothing, or its
     * teleport was refused).
     */
    private static final Map<UUID, Long> NO_LANDING_UNTIL = new HashMap<>();

    private VoidCatch() {}

    /**
     * Notes where {@code player} stands when it is a standable spot in the End on another block than the last one
     * noted. Called once per player tick.
     *
     * @param player the player
     */
    static void recordGround(ServerPlayer player) {
        ServerLevel level = player.level();
        if (level.dimension().equals(Level.END)
                && player.onGround()
                && StandableSpots.standable(level, player.blockPosition())
                && moved(player.getUUID(), player)) {
            LAST_GROUND.put(player.getUUID(), player.position());
        }
    }

    /**
     * Forgets a player (they logged out): their last ground and their landing pause.
     *
     * @param id the player's id
     */
    static void forget(UUID id) {
        LAST_GROUND.remove(id);
        NO_LANDING_UNTIL.remove(id);
    }

    /** Forgets every player; called when the server stops. */
    static void clear() {
        LAST_GROUND.clear();
        NO_LANDING_UNTIL.clear();
    }

    /**
     * Whether {@code player} is in the End and {@link VoidRule} says the void is about to take them.
     *
     * @param player the player
     * @return true when a catch is needed
     */
    static boolean needsCatch(ServerPlayer player) {
        ServerLevel level = player.level();
        return level.dimension().equals(Level.END)
                && VoidRule.needsCatch(
                        player.getY(),
                        level.getMinY(),
                        player.onGround(),
                        player.getKnownMovement().y < 0,
                        player.isFallFlying() || player.getAbilities().flying,
                        () -> groundBelow(player));
    }

    /**
     * The void catch: when a rescuer is within {@link VexRescues#VOID_RESCUER_RANGE} and there is somewhere to land,
     * the player is dismounted, stilled and teleported there, fed the enchanted golden apple's effects, and the
     * rescuer shows the apple as the tail. No cooldown.
     *
     * @param player the falling player
     */
    static void catchFromVoid(ServerPlayer player) {
        Optional<BoundVex> found = VexRescues.nearestRescuer(player, VexRescues.VOID_RESCUER_RANGE);
        if (found.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Optional<Vec3> landing = voidLanding(player, now);
        if (landing.isEmpty()) {
            return;
        }
        Vec3 at = landing.get();
        // The teleport dismounts the player and sends the position with zero velocity (docs/api-notes.md §24), but
        // it does not reset the fall distance, so the next landing would still count the fall into the void.
        player.resetFallDistance();
        if (!player.teleportTo(level, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), false)) {
            // Refused (an event canceled it): no rescue, no apple, and no new search or attempt for a while.
            NO_LANDING_UNTIL.put(player.getUUID(), now + NO_LANDING_PAUSE);
            return;
        }
        ItemStack apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        Consumable consumable = Objects.requireNonNull(
                apple.get(DataComponents.CONSUMABLE), "the enchanted golden apple has a consumable component");
        consumable.onConsume(level, player, apple);
        VexRescues.startTail(
                player, found.get(), VexRescues.Kind.VOID, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), now);
    }

    /** Whether a ray straight down from the player's feet hits a block within {@link #GROUND_CHECK} blocks. */
    private static boolean groundBelow(ServerPlayer player) {
        Vec3 feet = player.position();
        return player.level()
                        .clip(new ClipContext(
                                feet,
                                feet.subtract(0, GROUND_CHECK, 0),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                player))
                        .getType()
                != HitResult.Type.MISS;
    }

    /**
     * Where the void catch puts {@code player}: the bottom center of the nearest standable column within the landing
     * range, else the spot they last stood on if it is still standable (a broken bridge is not a landing). A search
     * that finds neither is not repeated for {@link #NO_LANDING_PAUSE} ticks.
     */
    private static Optional<Vec3> voidLanding(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        ServerLevel level = player.level();
        boolean search = now >= NO_LANDING_UNTIL.getOrDefault(id, 0L);
        if (search) {
            Optional<Vec3> landing =
                    StandableSpots.landing(level, player.position()).map(Vec3::atBottomCenterOf);
            if (landing.isPresent()) {
                return landing;
            }
        }
        Optional<Vec3> ground = Optional.ofNullable(LAST_GROUND.get(id)).filter(last -> groundUsable(level, last));
        if (search && ground.isEmpty()) {
            NO_LANDING_UNTIL.put(id, now + NO_LANDING_PAUSE);
        }
        return ground;
    }

    /**
     * Whether the last ground at {@code position} can still be used. A ground whose chunk is not loaded is trusted as
     * recorded: reading its blocks would load or generate the chunk on the server thread, and a failing check would
     * repeat that every {@link #NO_LANDING_PAUSE} ticks, while the teleport loads the chunk anyway. A loaded chunk is
     * read, so a broken bridge is not a landing.
     */
    private static boolean groundUsable(ServerLevel level, Vec3 position) {
        BlockPos feet = BlockPos.containing(position);
        return VoidRule.lastGroundUsable(
                level.hasChunk(
                        SectionPos.blockToSectionCoord(feet.getX()), SectionPos.blockToSectionCoord(feet.getZ())),
                () -> StandableSpots.standable(level, feet));
    }

    /** Whether the noted last ground of {@code id} is on another block than {@code player} stands on. */
    private static boolean moved(UUID id, ServerPlayer player) {
        Vec3 stored = LAST_GROUND.get(id);
        return stored == null || !BlockPos.containing(stored).equals(player.blockPosition());
    }
}
