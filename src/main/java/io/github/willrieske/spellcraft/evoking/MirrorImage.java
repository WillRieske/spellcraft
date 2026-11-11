package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One of the thirteen copies a Mirror makes (Parts 6-7 spec §7.2). A {@code LivingEntity}, not a mob: health 10, no
 * attack, no pathfinding. It wanders within {@link MirrorRule#WANDER_RADIUS} blocks of its caster by walking at a
 * random point and jumping one block, lives {@link MirrorRule#LIFE_TICKS} ticks of its own, and poofs when its caster
 * is loaded in its level and no longer has Mirror. For a player caster it carries the player's summoner mark, so
 * {@code Allies} treat it as the player's own. The client draws it as a stand-in of {@link #copiedType()} (Task 14).
 */
public final class MirrorImage extends LivingEntity {
    /** Synced: the registry id of the type the image copies, e.g. {@code minecraft:player}. */
    private static final EntityDataAccessor<String> COPIED_TYPE =
            SynchedEntityData.defineId(MirrorImage.class, EntityDataSerializers.STRING);

    /** Synced: the profile of a player caster (the skin), else {@link Mannequin#DEFAULT_PROFILE}. */
    private static final EntityDataAccessor<ResolvableProfile> PROFILE =
            SynchedEntityData.defineId(MirrorImage.class, EntityDataSerializers.RESOLVABLE_PROFILE);

    /** Save key of the caster's UUID. */
    private static final String CASTER_TAG = "caster";

    /** Save key of the ticks left of the image's own life. */
    private static final String LIFE_TAG = "life_ticks";

    /** Save key of the copied entity type's registry id. */
    private static final String TYPE_TAG = "copied_type";

    /** Save key of the player profile. */
    private static final String PROFILE_TAG = "profile";

    /** The copied type's id when nothing else is known. */
    private static final String DEFAULT_TYPE = "minecraft:player";

    /** Fewest ticks an image keeps one wander target (it idles there once it arrives). */
    private static final int WANDER_MIN_TICKS = 60;

    /** Extra ticks, at most, on top of {@link #WANDER_MIN_TICKS}. */
    private static final int WANDER_SPREAD_TICKS = 61;

    /** The speed an image walks at, as {@code setSpeed} takes it. */
    private static final float WALK_SPEED = 0.1F;

    /** The caster's UUID, or null for an image nobody made (it discards itself on its first tick). */
    private @Nullable UUID casterId;

    /** Ticks left of this image's own life. */
    private int lifeTicks = MirrorRule.LIFE_TICKS;

    /** Ticks until the image picks a new wander target. */
    private int wanderTicks;

    /** The point the image is walking to (idling at, once there), or null before its first pick. */
    private @Nullable Vec3 wanderTo;

    /**
     * Creates an image (the entity type's factory; also used when a save is loaded).
     *
     * @param type the entity type
     * @param level the level
     */
    public MirrorImage(EntityType<? extends MirrorImage> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(COPIED_TYPE, DEFAULT_TYPE);
        entityData.define(PROFILE, Mannequin.DEFAULT_PROFILE);
    }

    /**
     * The caster this image copies.
     *
     * @return the caster's UUID, if this image has one
     */
    public Optional<UUID> casterId() {
        return Optional.ofNullable(casterId);
    }

    /**
     * The type this image looks like.
     *
     * @return the type this image copies; the player when the id is unknown
     */
    public EntityType<?> copiedType() {
        Identifier id = Identifier.tryParse(entityData.get(COPIED_TYPE));
        return id == null
                ? EntityTypes.PLAYER
                : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(EntityTypes.PLAYER);
    }

    /**
     * The skin this image wears.
     *
     * @return the skin profile of a player caster, else the default profile
     */
    public ResolvableProfile profile() {
        return entityData.get(PROFILE);
    }

    /**
     * How long this image still lives on its own.
     *
     * @return the ticks left of this image's own life
     */
    public int lifeTicks() {
        return lifeTicks;
    }

    /**
     * Sets the ticks left of this image's own life (tests shorten it).
     *
     * @param ticks the new number of ticks left
     */
    public void setLifeTicks(int ticks) {
        lifeTicks = ticks;
    }

    /**
     * Sends the image to walk to {@code point} (a test hook: setting the image's own position makes it idle there,
     * facing where its caster faces, until the next pick).
     *
     * @param point the new wander target
     */
    public void setWanderTo(Vec3 point) {
        wanderTo = point;
        wanderTicks = WANDER_MIN_TICKS;
    }

    /**
     * Makes this image a copy of {@code caster}: the caster id, the copied type and profile, copies of both hands, and,
     * for a player, the player's summoner mark. Call once, before the image is added to the level.
     *
     * @param caster whoever has the Mirror effect
     */
    public void bind(LivingEntity caster) {
        casterId = caster.getUUID();
        entityData.set(
                COPIED_TYPE,
                BuiltInRegistries.ENTITY_TYPE.getKey(caster.getType()).toString());
        if (caster instanceof Player player) {
            entityData.set(PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
            setData(AllyAttachments.SUMMONER, player.getUUID());
        }
        setItemSlot(EquipmentSlot.MAINHAND, caster.getMainHandItem().copy());
        setItemSlot(EquipmentSlot.OFFHAND, caster.getOffhandItem().copy());
        refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return copiedType().getDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (COPIED_TYPE.equals(accessor)) {
            refreshDimensions();
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    /**
     * Images are not pushable: they overlap each other and the carrier freely, nothing shoves them, and they do not
     * count toward {@code MAX_ENTITY_CRAMMING}. (Overriding {@code doPush} and {@code push} is not enough: a player's
     * {@code doPush(image)} calls {@code image.push(player)}, which moves both.)
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    /** Ends the image when it has no caster, no life left, or a caster that lost Mirror. */
    private void serverTick(ServerLevel server) {
        if (casterId == null) {
            discard();
            return;
        }
        if (--lifeTicks <= 0) {
            poof();
            MirrorImages.afterEnd(server, casterId, this);
            return;
        }
        if (server.getEntities().get(casterId) instanceof LivingEntity caster && !caster.hasEffect(ModEffects.MIRROR)) {
            poof();
        }
    }

    /** Walks toward a random point near the caster; faces where the caster faces when it has arrived. */
    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        LivingEntity caster = casterId != null
                        && level() instanceof ServerLevel server
                        && server.getEntities().get(casterId) instanceof LivingEntity found
                ? found
                : null;
        Vec3 center = caster != null ? caster.position() : position();
        // A new target only when the timer runs out: an image that has arrived idles (facing the caster) until then.
        if (wanderTo == null || --wanderTicks <= 0) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = random.nextDouble() * MirrorRule.WANDER_RADIUS;
            wanderTo = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            wanderTicks = WANDER_MIN_TICKS + random.nextInt(WANDER_SPREAD_TICKS);
        }
        double dx = wanderTo.x - getX();
        double dz = wanderTo.z - getZ();
        if (dx * dx + dz * dz < 1.0) {
            zza = 0.0F;
            setJumping(false);
            if (caster != null) {
                setYRot(caster.getYRot());
                yBodyRot = getYRot();
                yHeadRot = getYRot();
            }
            return;
        }
        setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        setSpeed(WALK_SPEED);
        zza = 1.0F;
        setJumping(horizontalCollision && onGround());
    }

    /** Ends the image with a puff and no sound. */
    public void poof() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(
                    ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2.0, getZ(), 8, 0.2, 0.3, 0.2, 0.02);
        }
        discard();
    }

    /** A dead image may have been the last one: then the caster's Mirror ends with it. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server && casterId != null) {
            MirrorImages.afterEnd(server, casterId, this);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (casterId != null) {
            output.store(CASTER_TAG, UUIDUtil.CODEC, casterId);
        }
        output.putInt(LIFE_TAG, lifeTicks);
        output.putString(TYPE_TAG, entityData.get(COPIED_TYPE));
        output.store(PROFILE_TAG, ResolvableProfile.CODEC, entityData.get(PROFILE));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        casterId = input.read(CASTER_TAG, UUIDUtil.CODEC).orElse(null);
        lifeTicks = input.getIntOr(LIFE_TAG, MirrorRule.LIFE_TICKS);
        entityData.set(COPIED_TYPE, input.getStringOr(TYPE_TAG, DEFAULT_TYPE));
        input.read(PROFILE_TAG, ResolvableProfile.CODEC).ifPresent(profile -> entityData.set(PROFILE, profile));
        refreshDimensions();
    }
}
