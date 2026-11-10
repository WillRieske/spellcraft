package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import io.github.willrieske.spellcraft.spell.rules.RescuePush;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * A Bound Vex bats a ghast's fireball back (Parts 4–5 spec §6.3). The return hangs on the impact itself: a fireball
 * covers 1.2 to 1.9 blocks a tick, so a look once per tick would miss most of them. The return is Nether only; the
 * pass-through of its owner's own vexes holds everywhere.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VexFireballs {
    /** An impact this near a player's body (or on them) is theirs to have returned, in blocks. */
    public static final double NEAR_PLAYER = 4.0;

    private VexFireballs() {}

    /**
     * Handles the impact of a large fireball on the server (a client level is ignored).
     *
     * <p>First, a fireball whose owner is a player passes through that player's own Bound Vexes: a hit on one of them
     * is canceled and nothing else happens. A returned fireball would otherwise strike a sibling vex on its way to the
     * ghast, and its owner being the player hides it from the friendly-fire backstop. A canceled impact does not stop
     * the fireball: {@code AbstractHurtingProjectile.tick} moves it on by its full velocity instead of to the hit
     * point. Next tick its ray starts either inside the vex's box or already beyond it, and either way it cannot enter
     * the box through a face ({@code AABB.clip} counts only the faces a ray enters through, at a positive distance
     * along it, and {@code ProjectileUtil.getEntityHitResult} takes the nearest such hit), so the same vex is not
     * struck again. This holds in every dimension; only the return below is Nether only.
     *
     * <p>Then, in the Nether only: cancels the impact of a fireball that is about to hit a player, or land within
     * {@link #NEAR_PLAYER} blocks of the player's body (the distance from the hit to their bounding box, not to their
     * feet), when a Bound Vex summoned by that player is within reach of the fireball, and sends it back. A fireball
     * the player already owns, or one near a spectator, is left alone.
     *
     * @param event the impact, before vanilla explodes the fireball
     */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof LargeFireball fireball)
                || !(fireball.level() instanceof ServerLevel level)) {
            return;
        }
        HitResult hit = event.getRayTraceResult();
        if (strikesOwnersVex(fireball, hit)) {
            event.setCanceled(true);
            return;
        }
        if (!level.dimension().equals(Level.NETHER)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.equals(fireball.getOwner())) {
                continue;
            }
            if (player.getBoundingBox().distanceToSqr(hit.getLocation()) > NEAR_PLAYER * NEAR_PLAYER) {
                continue;
            }
            Optional<BoundVex> batter = nearestVexOf(level, fireball, player);
            if (batter.isPresent()) {
                sendBack(level, fireball, batter.get(), player);
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Whether {@code hit} is on a Bound Vex summoned by the player who owns {@code fireball}. */
    private static boolean strikesOwnersVex(LargeFireball fireball, HitResult hit) {
        return fireball.getOwner() instanceof Player owner
                && hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof BoundVex vex
                && vex.isSummonedBy(owner.getUUID());
    }

    /** The living Bound Vex of {@code player} nearest to {@code fireball}, if one is within the guard reach of it. */
    private static Optional<BoundVex> nearestVexOf(ServerLevel level, LargeFireball fireball, ServerPlayer player) {
        return level
                .getEntitiesOfClass(
                        BoundVex.class,
                        fireball.getBoundingBox().inflate(GuardArea.REACH),
                        vex -> vex.isAlive()
                                && vex.isSummonedBy(player.getUUID())
                                && vex.distanceTo(fireball) <= GuardArea.REACH)
                .stream()
                .min(Comparator.comparingDouble(vex -> vex.distanceToSqr(fireball)));
    }

    /**
     * The return: the vex jumps to the fireball, the knockback sound plays, and the fireball flies at its old speed
     * toward the ghast that shot it (alive and in the vex's sight), or straight back. The summoner becomes its owner:
     * vanilla kills a ghast in one hit only with a fireball a player returned.
     */
    private static void sendBack(ServerLevel level, LargeFireball fireball, BoundVex vex, ServerPlayer player) {
        Vec3 at = fireball.position();
        vex.setPos(at.x, at.y, at.z);
        vex.setDeltaMovement(Vec3.ZERO);
        vex.getMoveControl().setWait();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, vex.getSoundSource(), 1.0F, 1.0F);
        Vec3 old = fireball.getDeltaMovement();
        Vec3 heading = fireball.getOwner() instanceof Ghast ghast && ghast.isAlive() && vex.hasLineOfSight(ghast)
                ? ghast.getEyePosition().subtract(at)
                : Vec3.ZERO;
        RescuePush.Vector back =
                RescuePush.returned(new RescuePush.Vector(old.x, old.y, old.z), heading.x, heading.y, heading.z);
        fireball.deflect(
                (projectile, _, _, _) -> {
                    projectile.setDeltaMovement(back.x(), back.y(), back.z());
                    projectile.needsSync = true;
                },
                vex,
                EntityReference.of(player),
                true,
                1.0);
    }
}
