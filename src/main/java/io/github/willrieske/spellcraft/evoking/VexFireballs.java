package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import io.github.willrieske.spellcraft.spell.rules.RescuePush;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.monster.Ghast;
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
 * covers 1.2 to 1.9 blocks a tick, so a look once per tick would miss most of them. Nether only.
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class VexFireballs {
    /** An impact this near a player (or on them) is theirs to have returned. */
    public static final double NEAR_PLAYER = 4.0;

    private VexFireballs() {}

    /**
     * Cancels the impact of a large fireball that is about to hit a player, or near one, when a Bound Vex of that
     * player is within reach of it, and sends it back. Only the server's fireballs count (a client level is ignored),
     * and only in the Nether; a fireball a player already owns, or one near a spectator, is left alone.
     *
     * @param event the impact, before vanilla explodes the fireball
     */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof LargeFireball fireball)
                || !(fireball.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.NETHER)) {
            return;
        }
        HitResult hit = event.getRayTraceResult();
        Entity struck = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.equals(fireball.getOwner())) {
                continue;
            }
            if (!player.equals(struck) && hit.getLocation().distanceTo(player.position()) > NEAR_PLAYER) {
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
