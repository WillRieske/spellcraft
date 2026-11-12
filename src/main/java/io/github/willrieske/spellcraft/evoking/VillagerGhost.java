package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.Spell;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.CasterRules;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The spirit of a villager that a monster killed (Parts 6-7 spec §9). It floats to a player in need, casts Mirror or
 * Ghostrail on them with a 20-tick wind-up (the casting effect lights its hands; the villager model's arms are one
 * crossed part, so there is no arms-up pose), and fades when the fight is over, ready to come back next time. It
 * flies by setting its own position (no gravity, no collision), cannot be hurt, targeted or picked, and is never
 * saved. It carries an infinite Ghostrail from the spawn.
 */
public final class VillagerGhost extends LivingEntity {
    /** Synced: the villager's type, profession and level, for the look. */
    private static final EntityDataAccessor<VillagerData> VILLAGER_DATA =
            SynchedEntityData.defineId(VillagerGhost.class, EntityDataSerializers.VILLAGER_DATA);

    /** The distance from the player at which the ghost hovers. */
    private static final double HOVER = 2.0;

    /** How close to the player the ghost must be to start a cast. */
    private static final double CAST_RANGE = 3.0;

    /** How far from the player a ghost stays before it gives up. */
    private static final double LEAVE_DISTANCE = 64.0;

    /** Ticks with no hostile near the player before the ghost fades. */
    private static final int QUIET_LIMIT = 200;

    /** Ticks between two counts of the hostiles near the player (a 200-tick rule needs no finer count). */
    private static final int HOSTILE_CHECK_TICKS = 5;

    /** The share of the remaining gap a ghost closes per tick when it is nearly there (the easing in). */
    private static final double EASE = 0.2;

    /** The record this ghost is the spirit of, or null for a ghost nobody sent (it discards itself). */
    private @Nullable UUID recordId;

    /** The player this ghost helps, or null for a ghost nobody sent. */
    private @Nullable UUID helped;

    /** Ticks since a hostile was last near the player, counted in steps of {@link #HOSTILE_CHECK_TICKS}. */
    private int quietTicks;

    /** Ticks into the wind-up of the cast in progress. */
    private int windUp;

    /** The spell being wound up, or null. */
    private @Nullable Spell casting;

    /**
     * Creates a ghost (the entity type's factory). It is invulnerable from the start, so vanilla's
     * {@code canBeSeenAsEnemy} is false for it.
     *
     * @param type the entity type
     * @param level the level
     */
    public VillagerGhost(EntityType<? extends VillagerGhost> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setPermanentlyInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(VILLAGER_DATA, Villager.createDefaultVillagerData());
    }

    /**
     * The look of the villager this ghost is the spirit of.
     *
     * @return the villager's type, profession and level
     */
    public VillagerData villagerData() {
        return entityData.get(VILLAGER_DATA);
    }

    /**
     * The record this ghost is the spirit of.
     *
     * @return the record's id, if this ghost was sent for one
     */
    public Optional<UUID> recordId() {
        return Optional.ofNullable(recordId);
    }

    /**
     * The player this ghost helps.
     *
     * @return the player's UUID, if this ghost was sent to one
     */
    public Optional<UUID> helpedId() {
        return Optional.ofNullable(helped);
    }

    /**
     * Makes this ghost the spirit of {@code record}, helping {@code player}; infinite Ghostrail from now on. Call it
     * after positioning the ghost ({@code snapTo}): the Ghostrail it casts probes the block at the ghost's position
     * for a start spot, and an unpositioned entity sits at (0, 0, 0), which would load or generate that chunk.
     *
     * @param record the record the ghost is the spirit of
     * @param player the player it comes to help
     */
    public void bind(VillagerGhosts.Record record, ServerPlayer player) {
        recordId = record.id();
        helped = player.getUUID();
        entityData.set(VILLAGER_DATA, record.data());
        Spells.cast(this, Spells.GHOSTRAIL, GhostrailDuration.of(GhostrailDuration.Kind.VILLAGER_GHOST));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** Not a target for a player's attack ({@code Entity.isAttackable}). */
    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Not a target for mobs and fangs ({@code LivingEntity.attackable}, a different method from the above). */
    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    /** One server tick: the end conditions, the flight, the cast. */
    private void serverTick(ServerLevel level) {
        VillagerGhosts ghosts = VillagerGhosts.get(level);
        ServerPlayer player =
                helped == null ? null : level.getServer().getPlayerList().getPlayer(helped);
        if (recordId == null
                || ghosts.find(recordId).isEmpty()
                || player == null
                || !player.level().equals(level)
                || !player.isAlive()
                || player.isSpectator()
                || distanceTo(player) > LEAVE_DISTANCE) {
            fade();
            return;
        }
        if (tickCount % HOSTILE_CHECK_TICKS == 0) {
            boolean quiet = VillagerGhosts.hostilesWithin(level, player, CasterRules.RANGE)
                    .isEmpty();
            quietTicks = quiet ? quietTicks + HOSTILE_CHECK_TICKS : 0;
        }
        if (quietTicks >= QUIET_LIMIT) {
            fade();
            return;
        }
        flyToward(player);
        if (casting != null) {
            windUpTick(level, ghosts, player);
        } else if (distanceTo(player) <= CAST_RANGE) {
            startCast(level, ghosts, player);
        }
    }

    /** Moves, with no physics, toward the spot {@link #HOVER} blocks from the player at the player's eye height. */
    private void flyToward(ServerPlayer player) {
        Vec3 away = position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
        Vec3 side = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        Vec3 spot = new Vec3(player.getX() + side.x * HOVER, player.getEyeY(), player.getZ() + side.z * HOVER);
        Vec3 gap = spot.subtract(position());
        double step = Math.min(getAttributeValue(Attributes.MOVEMENT_SPEED), gap.length() * EASE);
        if (gap.length() > 1.0E-3) {
            setPos(position().add(gap.normalize().scale(step)));
        }
        setDeltaMovement(Vec3.ZERO);
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** Picks the spell (Ghostrail first when both apply) and starts the wind-up. */
    private void startCast(ServerLevel level, VillagerGhosts ghosts, ServerPlayer player) {
        VillagerGhosts.Record record = ghosts.find(recordId).orElseThrow();
        long now = level.getGameTime();
        if (!player.hasEffect(ModEffects.GHOSTRAIL)
                && !Spells.refusedFor(player, Spells.GHOSTRAIL)
                && CasterRules.ready(now, record.ghostrailReadyAt())
                && CasterRules.wantsGhostrail(player.getHealth(), player.getMaxHealth(), player.isInWall(), true)) {
            casting = Spells.GHOSTRAIL;
        } else if (!player.hasEffect(ModEffects.MIRROR) && CasterRules.ready(now, record.mirrorReadyAt())) {
            casting = Spells.MIRROR;
        } else {
            return;
        }
        windUp = 0;
        ScrollCasting.windUp(this);
    }

    /** Counts the wind-up; at the end casts if the player still qualifies and writes the cooldown to the record. */
    private void windUpTick(ServerLevel level, VillagerGhosts ghosts, ServerPlayer player) {
        if (++windUp < CasterRules.WIND_UP_TICKS) {
            return;
        }
        Spell spell = casting;
        casting = null;
        ScrollCasting.windDown(this);
        if (spell == null || player.hasEffect(spell.effect())) {
            return;
        }
        if (ScrollCasting.land(this, player, spell, GhostrailDuration.Kind.PLAYER)) {
            long readyAt = level.getGameTime() + ScrollCasting.cooldownFor(spell);
            if (spell.equals(Spells.GHOSTRAIL)) {
                ghosts.setGhostrailReadyAt(recordId, readyAt);
            } else {
                ghosts.setMirrorReadyAt(recordId, readyAt);
            }
        }
    }

    /** Ends the visit: a puff, a quiet vex death sound and {@code discard}. The record stays. */
    public void fade() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(
                    ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2.0, getZ(), 8, 0.2, 0.3, 0.2, 0.02);
        }
        playSound(SoundEvents.VEX_DEATH, 0.3F, 1.0F);
        discard();
    }
}
