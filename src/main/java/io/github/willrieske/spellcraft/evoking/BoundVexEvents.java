package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Bound Vexes follow their summoner through a dimension change (Evoking II spec §3.3): the living, non-staying ones
 * within {@link #PORTAL_RANGE} blocks of the summoner when the move starts land on ring cells around the summoner when
 * it ends. Two events, because the arrival event no longer knows where the player left (docs/api-notes.md §21): the
 * followers are noted on {@code EntityTravelToDimensionEvent} and moved on {@code PlayerChangedDimensionEvent}. The
 * first exit from the End (the credits path) is a respawn, not a teleport, and posts neither of those: the followers
 * are noted on {@code PlayerRespawnPositionEvent} (the old player, still where it left) and moved on
 * {@code PlayerRespawnEvent} when it says the End was conquered. A respawn after a death brings nothing. Staying vexes
 * stay; a moved vex is a new entity instance that keeps its mark (an attachment) and its stay flag (saved as NBT).
 * Each follower draws its ring cell on its own, so two may share one (harmless for vexes, which clip). The same class
 * resets the job goals' claims when the server stops ({@link #onServerStopped}).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class BoundVexEvents {
    /** How far from the leaving summoner a following vex comes along. */
    public static final double PORTAL_RANGE = 16.0;

    /** In the End every melee hit by a Bound Vex does this many times its damage (Parts 4–5 spec §7.2). */
    public static final float END_CRIT = 3.0F;

    /** How many critical-hit particles a crit shows. */
    private static final int CRIT_PARTICLES = 6;

    /** The followers of one move: the level they leave and their ids (the level is not known at arrival). */
    private record Note(ResourceKey<Level> from, List<UUID> vexes) {}

    /**
     * Per player changing dimension: the vexes to bring. Transient, like {@code EvokingCaster}'s map: an entry lives
     * from the travel event (or the respawn-position event of the End exit) to the changed-dimension event (or the
     * respawn event) of the same move, and logout and server stop drop it.
     */
    private static final Map<UUID, Note> FOLLOWERS = new HashMap<>();

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
        FOLLOWERS.remove(player.getUUID());
        if (event.getDimension().equals(player.level().dimension())) {
            return;
        }
        note(player);
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
        Note note = FOLLOWERS.remove(player.getUUID());
        if (note != null) {
            bring(player, note.vexes(), event.getFrom());
        }
    }

    /**
     * Notes the followers when the player leaves the End through the exit portal for the first time: the credits
     * path respawns the player ({@code PlayerList.respawn(player, true, ...)}), which posts neither of the two events
     * above (docs/api-notes.md §21). This event carries the <em>old</em> player, removed from its level but still
     * holding its level and position, before the new player exists. Last priority, so any other listener's change of
     * the destination is already in the transition. A respawn after a death (the flag is false) takes no note and
     * drops a stale one.
     *
     * @param event the respawn-position event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FOLLOWERS.remove(player.getUUID());
        if (event.isFromEndFight()
                && !event.getTeleportTransition()
                        .newLevel()
                        .dimension()
                        .equals(player.level().dimension())) {
            note(player);
        }
    }

    /**
     * Brings the followers noted for the End exit to ring cells around the respawned player.
     *
     * @param event the respawn event; only one that says the End was conquered brings anything
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Note note = FOLLOWERS.remove(player.getUUID());
        if (note != null && event.isEndConquered()) {
            bring(player, note.vexes(), note.from());
        }
    }

    /**
     * The friendly-fire backstop (Part 3 spec §4.3): damage whose attacker is a Bound Vex that has a summoner present
     * is cancelled when the victim is that summoner or a living thing the vex may not fight ({@link BoundVex#isPrey}:
     * allies, the summoner's other vexes, a calm golem, a player the summoner cannot harm). The summoner case is
     * explicit: {@code isPrey} already refuses it (the player is its own ally), but the rule must not rest on that. A
     * vex's legitimate hits are prey by the same test, so they stand: hostiles, a provoked creeper, a golem that is mad
     * at the summoner. Covers melee, arrows and potions, whose damage sources all name the vex as the causing entity.
     *
     * <p>A vex with a summoner mark whose summoner is absent (offline, dead, spectating or in another level) takes no
     * targets, but an arrow or potion it launched earlier may still be in flight, and {@code isPrey} cannot be asked
     * without the summoner. The rule then is the conservative one: <em>indirect</em> damage (the direct entity is not
     * the vex itself, so a projectile or a splash) is cancelled for any victim that is no {@code Enemy} (the summoner's
     * pets, villagers, golems and players) and for any victim carrying the same summoner mark (a sibling vex). The
     * vex's own melee is not touched: it cannot hit while it has no target. A vex with no summoner mark
     * ({@code /summon}) does nothing here.
     *
     * <p>A blast whose direct entity is an end crystal never hurts a Bound Vex, whoever set it off (spec §7.1); the
     * vex that broke the crystal names itself as the causing entity, so the rules above spare its side.
     *
     * <p>After the backstop, in the End, a hit not canceled whose direct entity is the vex itself (its melee, not an
     * arrow or a potion) does {@link #END_CRIT} times its damage (spec §7.2), whether or not its summoner is present.
     * That is before armor and before golem mercy's clamp ({@code LivingDamageEvent.Pre}, later in the pipeline), so
     * mercy still stops a golem at 25.
     *
     * @param event the incoming-damage event, before armor and reductions
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof BoundVex && event.getSource().getDirectEntity() instanceof EndCrystal) {
            event.setCanceled(true); // a crystal's blast never hurts a Bound Vex, whoever set it off (spec §7.1)
            return;
        }
        if (!(event.getSource().getEntity() instanceof BoundVex vex)) {
            return;
        }
        backstop(event, vex);
        if (!event.isCanceled()
                && vex.equals(event.getSource().getDirectEntity())
                && vex.level().dimension().equals(Level.END)) {
            crit(event, event.getEntity());
        }
    }

    /** The friendly-fire backstop of {@link #onIncomingDamage}, for damage {@code vex} caused. */
    private static void backstop(LivingIncomingDamageEvent event, BoundVex vex) {
        ServerPlayer owner = vex.summoner().orElse(null);
        LivingEntity victim = event.getEntity();
        if (owner != null) {
            if (spares(vex, owner, victim)) {
                event.setCanceled(true);
            }
        } else if (!vex.equals(event.getSource().getDirectEntity()) && sparesWithoutOwner(vex, victim)) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether the absent-summoner branch of the friendly-fire backstop spares {@code victim} from indirect damage by
     * {@code vex}, whose summoner is not here: the vex has a summoner mark, and the victim is no {@code Enemy} or
     * carries the same mark. Shared by the damage handler and the knockback handler, so a blast moves exactly those
     * it does not hurt.
     */
    private static boolean sparesWithoutOwner(BoundVex vex, LivingEntity victim) {
        return vex.summonerId().isPresent()
                && (!(victim instanceof Enemy)
                        || victim.getExistingData(AllyAttachments.SUMMONER).equals(vex.summonerId()));
    }

    /** Triples the hit and shows it: vanilla's critical-hit particles and sound at the victim. */
    private static void crit(LivingIncomingDamageEvent event, LivingEntity victim) {
        event.setAmount(event.getAmount() * END_CRIT);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.CRIT,
                    victim.getX(),
                    victim.getY(0.5),
                    victim.getZ(),
                    CRIT_PARTICLES,
                    0.3,
                    0.3,
                    0.3,
                    0.1);
            level.playSound(
                    null,
                    victim.getX(),
                    victim.getY(),
                    victim.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.NEUTRAL,
                    1.0F,
                    1.0F);
        }
    }

    /**
     * Whether the friendly-fire backstop spares {@code victim} from {@code vex}, whose summoner {@code owner} is here:
     * the summoner themselves, or anything the vex may not fight ({@link BoundVex#isPrey}).
     */
    private static boolean spares(BoundVex vex, ServerPlayer owner, LivingEntity victim) {
        return victim.getUUID().equals(owner.getUUID()) || !vex.isPrey(victim);
    }

    /**
     * Stops a blast from throwing those it may not hurt (Parts 4–5 spec §7.1): vanilla pushes everything in range
     * even when the damage was canceled. A crystal's blast never moves a Bound Vex; a blast a Bound Vex caused never
     * moves its summoner or anyone the friendly-fire backstop spares. When the vex's summoner is absent, that is
     * exactly the victims whose damage the absent-summoner branch cancels ({@link #sparesWithoutOwner}).
     *
     * @param event the knockback for one entity, after the damage and before the push
     */
    @SubscribeEvent
    public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        ServerExplosion explosion = event.getExplosion();
        Entity affected = event.getAffectedEntity();
        if (explosion.getDirectSourceEntity() instanceof EndCrystal && affected instanceof BoundVex) {
            event.setKnockbackVelocity(Vec3.ZERO);
            return;
        }
        if (explosion.getDamageSource().getEntity() instanceof BoundVex vex
                && affected instanceof LivingEntity victim) {
            boolean spared = vex.summoner()
                    .map(owner -> spares(vex, owner, victim))
                    .orElseGet(() -> sparesWithoutOwner(vex, victim));
            if (spared) {
                event.setKnockbackVelocity(Vec3.ZERO);
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
     * Drops every note, every repair claim, every cure claim, every bribe claim, every crystal claim, every scripted
     * bribe die and every running rescue ({@link VexRescues#clear}) when the server stops
     * (single-player loads one server per world in the same JVM, so none may outlive its world).
     *
     * @param event the server-stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FOLLOWERS.clear();
        RepairGolemGoal.clearClaims();
        CureZombieVillagerGoal.clearClaims();
        BribeGoal.clearClaims();
        AttackCrystalGoal.clearClaims();
        VexRescues.clear();
    }

    /**
     * A bribed vex that joins a level (loaded from a save, or arrived from another dimension, which reports
     * {@code loadedFromDisk() == false}, docs/api-notes.md §23) has its goals stripped again and the flee goal put
     * back (spec §3.2); its 200 ticks start over. Keyed on the saved mark, server side only.
     *
     * @param event the join event
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Vex vex && AllyAttachments.isBribed(vex)) {
            FleeFromViewGoal.install(vex);
        }
    }

    /**
     * The one "note" step: the living, non-staying Bound Vexes of {@code player} within {@link #PORTAL_RANGE} of it in
     * the level it stands in now, kept for {@link #bring}. Replaces the player's earlier note.
     */
    private static void note(ServerPlayer player) {
        if (player.isSpectator()) {
            return; // a spectating summoner counts as absent (spec §3.1): nothing follows it
        }
        UUID id = player.getUUID();
        ServerLevel level = player.level();
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
            FOLLOWERS.put(id, new Note(level.dimension(), followers));
        }
    }

    /**
     * The one "bring" step: each still-living noted vex in {@code from} lands on a ring cell one block up around the
     * arrived {@code player}.
     */
    private static void bring(ServerPlayer player, List<UUID> followers, ResourceKey<Level> fromDimension) {
        ServerLevel to = player.level();
        ServerLevel from = to.getServer().getLevel(fromDimension);
        if (from == null) {
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
}
