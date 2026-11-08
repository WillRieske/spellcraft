package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Bound Vex (Evoking II spec §3): a vex a player summons on V that lives until it dies. Vanilla's {@code Vex} gives it
 * the no-clip flight, the move control, the charging texture and the iron sword; this class owns the goals (vanilla's
 * are private classes, docs/api-notes.md §21), the stay flag, the summoner lookup, the prey rule, and the three small
 * rules every goal shares: {@link #scanDue} (the clock), {@link #inGuardArea} (the box) and {@link #keepsTarget}.
 * Without a summoner online, alive and in its level it idles. The summoner is the {@link AllyAttachments#SUMMONER}
 * mark, so {@code Allies}, {@code GolemMercy} and the Trailing flame already treat it as the summoner's.
 */
public class BoundVex extends Vex {
    /** Synced: whether the vex holds its post instead of following (spec §3.3). */
    private static final EntityDataAccessor<Boolean> STAYING =
            SynchedEntityData.defineId(BoundVex.class, EntityDataSerializers.BOOLEAN);

    private static final String STAYING_TAG = "staying";

    /** Ticks between the target goals' scans (spec §3.4 "every 10 ticks"); read through {@link #scanDue}. */
    static final int SCAN_INTERVAL = 10;

    /** How far the summoner's wolves hear the call to a fight (spec §3.4), feet to feet. */
    static final double WOLF_CALL_RANGE = 16.0;

    public BoundVex(EntityType<? extends BoundVex> type, Level level) {
        super(type, level);
    }

    /**
     * Vanilla's vex attributes with the reach as the follow range (spec §3.1).
     *
     * @return the attribute builder {@code ModEntities} registers
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Vex.createAttributes().add(Attributes.FOLLOW_RANGE, GuardArea.REACH);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(STAYING, false);
    }

    /**
     * The goals (spec §3.3). Not {@code super.registerGoals()}: vanilla's are private classes (docs/api-notes.md §21),
     * so the charge and wander goals are this class's copies, and a Bound Vex has no raider grudge, no owner to copy a
     * target from and no player hunt. Its three target goals are {@link HurtBySummonerSideGoal} (grudges),
     * {@link AllyFightGoal} (an ally's fight) and {@link NearestHostileGoal} (the patrol). Task 6 adds the flee, dodge
     * and repair goals here.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FleeCreeperGoal(this));
        goalSelector.addGoal(0, new DodgeGolemGoal(this));
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RepairGolemGoal(this));
        goalSelector.addGoal(3, new ChargeGoal());
        goalSelector.addGoal(4, new FollowSummonerGoal(this));
        goalSelector.addGoal(5, new StayWanderGoal());
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        targetSelector.addGoal(1, new HurtBySummonerSideGoal(this));
        targetSelector.addGoal(2, new AllyFightGoal(this));
        targetSelector.addGoal(3, new NearestHostileGoal(this));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean(STAYING_TAG, isStaying());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(STAYING, input.getBooleanOr(STAYING_TAG, false));
    }

    /** Never removed for being far from players (spec §3.1); Peaceful still removes it through the type. */
    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /**
     * Vanilla's spawn (the iron sword), then the random follow-range bonus {@code Mob.finalizeSpawn} adds (the inherited
     * {@code RANDOM_SPAWN_BONUS_ID}, docs/api-notes.md §21) goes, so the reach is exactly {@link GuardArea#REACH}
     * (docs/api-notes.md §19: the targeting goal reads the attribute).
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason reason,
            @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        AttributeInstance range = getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) {
            range.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        return data;
    }

    /** Whether the vex holds its post instead of following. */
    public boolean isStaying() {
        return entityData.get(STAYING);
    }

    /**
     * Turns staying on or off; on, the block it stands at becomes the post the wander goal drifts around (spec §3.3).
     *
     * @param staying true to stay, false to follow again
     */
    public void setStaying(boolean staying) {
        entityData.set(STAYING, staying);
        if (staying) {
            setBoundOrigin(blockPosition());
        }
    }

    /** The UUID in the summoner mark, or empty for a vex nobody summoned ({@code /summon}). */
    public Optional<UUID> summonerId() {
        return getExistingData(AllyAttachments.SUMMONER);
    }

    /**
     * Whether {@code player} is this vex's summoner.
     *
     * @param player a player's UUID
     * @return true if the summoner mark holds it
     */
    public boolean isSummonedBy(UUID player) {
        return summonerId().filter(player::equals).isPresent();
    }

    /**
     * The summoner while online, alive and in this vex's level (spec §3.1); empty otherwise, and the vex idles.
     *
     * @return the summoner, or empty
     */
    public Optional<ServerPlayer> summoner() {
        if (!(level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return summonerId()
                .map(level::getPlayerByUUID)
                .filter(player -> player instanceof ServerPlayer && player.isAlive())
                .map(ServerPlayer.class::cast);
    }

    /**
     * Whether a Bound Vex of {@code summoner} may fight {@code target} (spec §3.4; round one's rule): anything
     * attackable that is no ally of the summoner, no player the summoner cannot harm ({@code canHarmPlayer}: the
     * {@code pvp} game rule; teammates are already allies), no mob another player summoned unless the summoner may
     * harm that player (Divergence 15: with {@code pvp} off, two players' vexes leave each other alone; an offline
     * summoner's mobs follow the {@code pvp} rule alone), and nothing while the summoner is not in {@code level}
     * (offline or in another dimension). Public for its GameTests.
     *
     * @param level the level the vex is in
     * @param summoner the UUID in the vex's summoner mark
     * @param target the candidate
     * @return whether the vex may take {@code target} as its target
     */
    public static boolean isPrey(ServerLevel level, UUID summoner, LivingEntity target) {
        if (!target.attackable()) {
            return false;
        }
        Player owner = level.getPlayerByUUID(summoner);
        if (owner == null || Allies.isAllyOf(owner, target)) {
            return false;
        }
        if (target instanceof Player other) {
            return owner.canHarmPlayer(other);
        }
        Optional<UUID> othersSummoner = target.getExistingData(AllyAttachments.SUMMONER);
        if (othersSummoner.isPresent()) {
            Player other = level.getServer().getPlayerList().getPlayer(othersSummoner.get());
            return other != null ? owner.canHarmPlayer(other) : level.isPvpAllowed();
        }
        return true;
    }

    /**
     * {@link #isPrey(ServerLevel, UUID, LivingEntity)} for this vex: false on a client level, for a vex with no mark,
     * and while the summoner is dead, offline or in another level (spec §10: "as offline until respawn"), so a vex
     * without a {@link #summoner()} takes and keeps no target.
     *
     * @param target the candidate
     * @return whether this vex may fight it
     */
    public boolean isPrey(LivingEntity target) {
        return level() instanceof ServerLevel level
                && summoner()
                        .filter(owner -> isPrey(level, owner.getUUID(), target))
                        .isPresent();
    }

    /**
     * Whether a target is worth taking or keeping (spec §3.4): alive, attackable by this vex ({@code Mob.canAttack}:
     * false for a creative or spectator player, an invulnerable mob or a ghast, the targets {@code setTarget} would
     * not hold), still prey, and within {@link GuardArea#REACH}. Every target goal's {@code canContinueToUse}, which
     * replaces {@code TargetGoal}'s and never calls {@code super} (docs/api-notes.md §21), and every target goal's
     * candidate filter.
     */
    boolean keepsTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && canAttack(target) && isPrey(target) && withinReach(target);
    }

    /** Whether {@code other} is within {@link GuardArea#REACH} of this vex, feet to feet. */
    boolean withinReach(Entity other) {
        Vec3 offset = other.position().subtract(position());
        return GuardArea.withinReach(offset.x, offset.y, offset.z);
    }

    /**
     * Whether {@code other}'s position is inside this vex's guard box (spec §3.4: 8×8×7, faces included, feet to feet,
     * {@link GuardArea#contains}). The one box test every goal uses: entity queries take {@link #guardArea()} as the
     * broad phase and keep only what this accepts. Public for its GameTest.
     *
     * @param other the entity to test
     * @return true when its feet are in the box
     */
    public boolean inGuardArea(Entity other) {
        Vec3 offset = other.position().subtract(position());
        return GuardArea.contains(offset.x, offset.y, offset.z);
    }

    /**
     * The broad-phase box for entity queries around this vex: {@code getEntitiesOfClass} returns what this box
     * <em>intersects</em>, which is more than {@link #inGuardArea} accepts, so filter every query's hits through it.
     */
    AABB guardArea() {
        return new AABB(position(), position())
                .inflate(GuardArea.HALF_WIDTH, GuardArea.HALF_HEIGHT, GuardArea.HALF_WIDTH);
    }

    /**
     * The summoner's allies ({@code Allies.isAllyOf}) whose feet are inside this vex's guard area, this vex excepted:
     * a vex is its own summoner's ally, and counting itself would hand its own target to the ally goal (spec §3.4).
     *
     * @param owner the summoner
     * @return the allies in the area
     */
    List<Mob> alliesInGuardArea(ServerPlayer owner) {
        UUID self = getUUID();
        return level().getEntitiesOfClass(
                        Mob.class,
                        guardArea(),
                        mob -> !mob.getUUID().equals(self) && inGuardArea(mob) && Allies.isAllyOf(owner, mob));
    }

    /**
     * The goal clock (spec §3.4 "every 10 ticks"): true once every {@code interval} ticks. {@code Mob.serverAiStep}
     * asks a goal's {@code canUse} only when {@code tickCount + getId()} is even (docs/api-notes.md §21, "The goal
     * clock"), so a plain {@code tickCount % 10} gate never opens for an odd id; keyed the same way, an even interval
     * always lands on a tick where {@code canUse} runs. Every scanning goal reads its cadence here.
     *
     * @param interval ticks between scans; even
     * @return whether this tick is a scan tick
     */
    boolean scanDue(int interval) {
        return (tickCount + getId()) % interval == 0;
    }

    /**
     * The living Bound Vexes {@code summoner} has in every loaded level (spec §3.2); vexes in unloaded chunks are not
     * counted (spec §13).
     *
     * @param server the server
     * @param summoner the summoner's UUID
     * @return the count
     */
    public static int countOwnedBy(MinecraftServer server, UUID summoner) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(ModEntities.BOUND_VEX.get(), vex -> vex.isAlive() && vex.isSummonedBy(summoner))
                    .size();
        }
        return count;
    }

    /**
     * The summoner's empty-handed, un-sneaked main-hand click toggles stay (spec §3.3), with the vex's own sound;
     * everything else passes (the sneaking click is Part 3's take-back). Only the server decides, since the client does
     * not know the summoner (the mark is not synced). The client still answers {@code SUCCESS_SERVER} for every
     * empty-handed, un-sneaked main-hand click: a client {@code PASS} would send {@code Minecraft.startUseItem} on to
     * the off hand and throw a pearl or raise a shield on each toggle (docs/api-notes.md §21, Divergence 9). The swing
     * comes from the server; a stranger's click is answered {@code PASS} there and changes nothing.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!isSummonedBy(player.getUUID())) {
            return InteractionResult.PASS;
        }
        setStaying(!isStaying());
        playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Takes the target and, when it is a new one, calls the summoner's tamed, idle, un-sitting wolves within
     * {@link #WOLF_CALL_RANGE} that want to attack it ({@code Wolf.wantsToAttack}: never a creeper, a ghast or a
     * player the summoner may not harm) onto it (spec §3.4). "New" means it differs from the target held just before:
     * {@code TargetGoal.canContinueToUse} re-sets the same target on every full tick (docs/api-notes.md §21), and those
     * re-sets call nobody. A change {@code GolemMercy} cancels, a target {@code getTarget} refuses, and clearing a
     * target call nobody either.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity before = getTarget();
        super.setTarget(target);
        LivingEntity set = getTarget();
        if (target == null
                || set == null
                || !set.getUUID().equals(target.getUUID())
                || (before != null && before.getUUID().equals(target.getUUID()))
                || !(level() instanceof ServerLevel level)) {
            return;
        }
        summoner().ifPresent(owner -> {
            for (Wolf wolf : level.getEntitiesOfClass(
                    Wolf.class,
                    getBoundingBox().inflate(WOLF_CALL_RANGE),
                    wolf -> wolf.isTame()
                            && wolf.isOwnedBy(owner)
                            && wolf.getTarget() == null
                            && !wolf.isOrderedToSit()
                            && wolf.distanceToSqr(this) <= WOLF_CALL_RANGE * WOLF_CALL_RANGE
                            && wolf.wantsToAttack(target, owner))) {
                wolf.setTarget(target);
            }
        });
    }

    /**
     * Vanilla's {@code Vex.VexChargeAttackGoal}, copied because it is private (docs/api-notes.md §21): fly at the
     * target's eyes and hit it; this is how a Bound Vex uses the sword it holds.
     */
    private final class ChargeGoal extends Goal {
        ChargeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = BoundVex.this.getTarget();
            return target != null
                    && target.isAlive()
                    && !BoundVex.this.getMoveControl().hasWanted()
                    && BoundVex.this.random.nextInt(reducedTickDelay(7)) == 0
                    && BoundVex.this.distanceToSqr(target) > 4.0;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = BoundVex.this.getTarget();
            return BoundVex.this.getMoveControl().hasWanted()
                    && BoundVex.this.isCharging()
                    && target != null
                    && target.isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = BoundVex.this.getTarget();
            if (target != null) {
                Vec3 eye = target.getEyePosition();
                BoundVex.this.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.0);
            }
            BoundVex.this.setIsCharging(true);
            BoundVex.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            BoundVex.this.setIsCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = BoundVex.this.getTarget();
            if (target == null) {
                return;
            }
            if (BoundVex.this.getBoundingBox().intersects(target.getBoundingBox())) {
                BoundVex.this.doHurtTarget(getServerLevel(BoundVex.this.level()), target);
                BoundVex.this.setIsCharging(false);
            } else if (BoundVex.this.distanceToSqr(target) < 9.0) {
                Vec3 eye = target.getEyePosition();
                BoundVex.this.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.0);
            }
        }
    }

    /**
     * Vanilla's {@code Vex.VexRandomMoveGoal}, copied because it is private, and run only while staying (spec §3.3):
     * drift to a random air block within 7 blocks sideways and 5 up or down of the post.
     */
    private final class StayWanderGoal extends Goal {
        StayWanderGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return BoundVex.this.isStaying()
                    && !BoundVex.this.getMoveControl().hasWanted()
                    && BoundVex.this.random.nextInt(reducedTickDelay(7)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void tick() {
            BlockPos origin = BoundVex.this.getBoundOrigin();
            if (origin == null) {
                origin = BoundVex.this.blockPosition();
            }
            for (int attempt = 0; attempt < 3; attempt++) {
                BlockPos spot = origin.offset(
                        BoundVex.this.random.nextInt(15) - 7,
                        BoundVex.this.random.nextInt(11) - 5,
                        BoundVex.this.random.nextInt(15) - 7);
                if (BoundVex.this.level().isEmptyBlock(spot)) {
                    BoundVex.this
                            .getMoveControl()
                            .setWantedPosition(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 0.25);
                    if (BoundVex.this.getTarget() == null) {
                        BoundVex.this
                                .getLookControl()
                                .setLookAt(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 180.0F, 20.0F);
                    }
                    break;
                }
            }
        }
    }
}
