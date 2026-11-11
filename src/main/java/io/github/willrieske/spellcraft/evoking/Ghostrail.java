package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The per-tick driver of Ghostrail (Parts 6-7 spec §7.3). The player's physics are the Mixin's and the flight is
 * {@code AbilitySync}'s (the one driver of the abilities sync); this runs the rest: no damage (but {@code /kill} and
 * the void still land), the white-smoke trail, the hold on a mob carrier ({@code noPhysics}, no gravity), and the
 * end (push out of blocks, give gravity back). Nothing here depends on the order of the two Post-tick handlers:
 * the end only moves the carrier and never touches abilities.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class Ghostrail {
    /** How far from a carrier that ends inside blocks to look for a free spot. */
    static final int SEARCH_RADIUS = 8;

    /** Trail particles per tick, at the carrier's previous position. */
    private static final int TRAIL_COUNT = 2;

    private Ghostrail() {}

    /**
     * Whether {@code carrier} flies through blocks by itself: a Vex or a villager ghost (a Vex's {@code noPhysics} is
     * true only inside its own tick, so the flag cannot be the test; a ghost sets its own position and has no physics
     * at all).
     *
     * @param carrier any living entity
     * @return true when Ghostrail must not touch its physics
     */
    static boolean flies(LivingEntity carrier) {
        return carrier instanceof Vex || carrier instanceof VillagerGhost;
    }

    /**
     * Notes that Ghostrail began on {@code carrier} (the effect's {@code onEffectAdded}): the mark with its original
     * {@code NoGravity} (only when no mark is set yet: if the effect was removed and cast again before the end ran,
     * the hold has already set {@code noGravity}, and the stored original must survive), and the block it stands in
     * if that is a standable spot (a recast before the end keeps the earlier start when this one is not standable).
     *
     * @param carrier the new carrier, on a server level
     */
    public static void begin(LivingEntity carrier) {
        boolean recast = carrier.hasData(EvokingAttachments.GHOSTRAIL_MARK);
        if (!recast) {
            carrier.setData(EvokingAttachments.GHOSTRAIL_MARK, carrier.isNoGravity());
        }
        if (StandableSpots.standable(carrier.level(), carrier.blockPosition())) {
            carrier.setData(EvokingAttachments.GHOSTRAIL_START, carrier.blockPosition());
        } else if (!recast) {
            // A fresh start with no standable spot has none; a recast before the end keeps the earlier one
            carrier.removeData(EvokingAttachments.GHOSTRAIL_START);
        }
    }

    /**
     * One step for {@code carrier}. Public so GameTests step a mock player or a mob by hand.
     *
     * @param carrier a player or a mob that may carry Ghostrail
     */
    public static void tick(LivingEntity carrier) {
        if (!(carrier.level() instanceof ServerLevel level)) {
            return;
        }
        boolean has = carrier.hasEffect(ModEffects.GHOSTRAIL);
        boolean marked = carrier.hasData(EvokingAttachments.GHOSTRAIL_MARK);
        if (!carrier.isAlive()) {
            // A killed carrier keeps its effects until it is removed (a player until the respawn): clear the mark and
            // give gravity back, but never teleport a corpse
            if (marked) {
                end(level, carrier, false);
            }
            return;
        }
        if (has) {
            if (!marked) {
                // The effect came back from a save or a forced add, which skip the hook
                carrier.setData(EvokingAttachments.GHOSTRAIL_MARK, carrier.isNoGravity());
            }
            if (!(carrier instanceof Player) && !flies(carrier)) {
                carrier.noPhysics = true;
                carrier.setNoGravity(true);
            }
            level.sendParticles(
                    ParticleTypes.WHITE_SMOKE,
                    carrier.xOld,
                    carrier.yOld + carrier.getBbHeight() / 2.0,
                    carrier.zOld,
                    TRAIL_COUNT,
                    0.1,
                    0.1,
                    0.1,
                    0.0);
        } else if (marked) {
            end(level, carrier, true);
        }
    }

    /** Ends Ghostrail on {@code carrier}: gravity back, out of the blocks (when {@code moveOut}), no fall to come. */
    private static void end(ServerLevel level, LivingEntity carrier, boolean moveOut) {
        boolean originalNoGravity =
                carrier.getExistingData(EvokingAttachments.GHOSTRAIL_MARK).orElse(false);
        carrier.removeData(EvokingAttachments.GHOSTRAIL_MARK);
        if (!(carrier instanceof Player) && !flies(carrier)) {
            carrier.noPhysics = false;
            carrier.setNoGravity(originalNoGravity);
        }
        if (moveOut) {
            pushOut(level, carrier);
        }
        carrier.removeData(EvokingAttachments.GHOSTRAIL_START);
        carrier.fallDistance = 0.0;
    }

    /**
     * When the carrier's box collides with blocks: the nearest standable spot its box fits on within
     * {@link #SEARCH_RADIUS} blocks, else the nearest free spot, else the spot the effect began at if that is still
     * free, else it stays (and suffocates as vanilla would; accepted, a playtest note).
     */
    private static void pushOut(ServerLevel level, LivingEntity carrier) {
        if (level.noCollision(carrier, carrier.getBoundingBox())) {
            return;
        }
        BlockPos here = carrier.blockPosition();
        Optional<BlockPos> spot = search(level, carrier, here, true).or(() -> search(level, carrier, here, false));
        if (spot.isEmpty()) {
            spot = carrier.getExistingData(EvokingAttachments.GHOSTRAIL_START)
                    .filter(start -> fits(level, carrier, start));
        }
        spot.ifPresent(pos -> teleport(level, carrier, Vec3.atBottomCenterOf(pos)));
    }

    /** The first spot outward from {@code from}, ring by ring, where the box fits (and, if asked, a floor is below). */
    private static Optional<BlockPos> search(
            ServerLevel level, LivingEntity carrier, BlockPos from, boolean standable) {
        for (int ring = 1; ring <= SEARCH_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != ring) {
                            continue;
                        }
                        BlockPos pos = from.offset(dx, dy, dz);
                        if (fits(level, carrier, pos) && (!standable || StandableSpots.standable(level, pos))) {
                            return Optional.of(pos);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Whether the carrier's box, with its feet at the bottom middle of {@code pos}, touches no block. */
    private static boolean fits(ServerLevel level, LivingEntity carrier, BlockPos pos) {
        AABB box = carrier.getDimensions(carrier.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos));
        return level.noCollision(carrier, box);
    }

    /**
     * Moves {@code carrier} to {@code spot}: a player through the full teleport (it syncs the client), a mob by
     * position.
     */
    private static void teleport(ServerLevel level, LivingEntity carrier, Vec3 spot) {
        if (carrier instanceof ServerPlayer player) {
            player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), player.getXRot(), false);
        } else {
            carrier.teleportTo(spot.x, spot.y, spot.z);
        }
    }

    /**
     * Drives every server player's Ghostrail after its tick.
     *
     * @param event the player's post-tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /**
     * Drives every non-player carrier (an entity that holds the saved mark) after its tick.
     *
     * @param event the entity's post-tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !(living instanceof Player)
                && living.hasData(EvokingAttachments.GHOSTRAIL_MARK)) {
            tick(living);
        }
    }

    /**
     * Cancels every hit on a carrier except {@code /kill} and the void, so a carrier that dives under the world
     * still dies there.
     *
     * @param event the incoming-damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (event.getEntity().hasEffect(ModEffects.GHOSTRAIL)
                && !source.is(DamageTypes.GENERIC_KILL)
                && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
        }
    }
}
