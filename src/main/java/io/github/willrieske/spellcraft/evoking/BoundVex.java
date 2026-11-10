package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
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
 * The Bound Vex (Evoking II spec §3): a vex a player summons on V that lives until it dies. Vanilla's {@code Vex} gives
 * it the no-clip flight, the move control, the charging texture and the iron sword; this class owns the goals
 * (vanilla's are private classes, docs/api-notes.md §21), the stay flag, the summoner lookup, the prey rule, and the
 * small rules every goal shares (a staying vex hovers at its post point and guards the stay area around it, Part 3
 * spec §2): {@link #scanDue} (the clock), {@link #inGuardArea} (the box), {@link #keepsTarget}
 * and {@link #huntable}.
 * Without a summoner online, alive, in its level and not spectating it idles. The summoner is the
 * {@link AllyAttachments#SUMMONER} mark, so {@code Allies}, {@code GolemMercy} and the Trailing flame already treat it as the summoner's.
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

    /**
     * Set by {@link #restoreFrom} on the new copy a cross-dimension teleport makes of a staying vex, and spent by
     * {@link #onAddedToLevel}. A plain field: the copy is built fresh, so it is false on a chunk load.
     */
    private boolean repostOnArrival;

    /** The allies found on {@link #alliesTick}; see {@link #alliesInGuardArea}. */
    private List<Mob> alliesCache = List.of();

    private int alliesTick = -1;

    public BoundVex(EntityType<? extends BoundVex> type, Level level) {
        super(type, level);
        // No experience: summon-and-kill would farm 3 XP per vex, 3 vexes per press (vanilla's vex gives 3).
        xpReward = 0;
    }

    /**
     * Vanilla's vex attributes with the follow-range bound as the follow range (Part 3 spec §2.3): wide enough for a
     * staying vex and a target at opposite corners of the stay box; the box and the reach are the real limits.
     *
     * @return the attribute builder {@code ModEntities} registers
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Vex.createAttributes().add(Attributes.FOLLOW_RANGE, GuardArea.FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(STAYING, false);
    }

    /**
     * The goals (spec §3.3). Not {@code super.registerGoals()}: vanilla's are private classes (docs/api-notes.md §21),
     * so the charge goal is this class's copy, the post-holding goal is its own, and a Bound Vex has no raider grudge, no owner to copy a
     * target from and no player hunt. Its three target goals are {@link HurtBySummonerSideGoal} (grudges),
     * {@link AllyFightGoal} (an ally's fight) and {@link NearestHostileGoal} (the patrol). The job goals sit beside
     * them: {@link FleeCreeperGoal} and {@link DodgeGolemGoal} outrank everything on the move flag,
     * {@link RepairGolemGoal} outranks the charge and the follow.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FleeCreeperGoal(this));
        goalSelector.addGoal(0, new DodgeGolemGoal(this));
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RepairGolemGoal(this));
        goalSelector.addGoal(3, new ChargeGoal());
        goalSelector.addGoal(4, new FollowSummonerGoal(this));
        goalSelector.addGoal(5, new HoldPostGoal());
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
        // A vex saved by Part 2 carries the old base value (7.25, the reach), which would cut a staying vex's fights
        // short: TargetGoal.canContinueToUse reads the attribute live (docs/api-notes.md §22).
        AttributeInstance range = getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) {
            range.setBaseValue(GuardArea.FOLLOW_RANGE);
        }
    }

    /**
     * A cross-dimension teleport ({@code Entity.teleport}: a portal, {@code /execute in ... run tp}) builds a new vex
     * and calls this on it <em>before</em> it has its arrival position (docs/api-notes.md §21), so a staying vex only
     * records here that its post must be re-posted once it arrives ({@link #onAddedToLevel}): the saved post is the
     * old dimension's coordinates.
     */
    @Override
    public void restoreFrom(Entity oldEntity) {
        super.restoreFrom(oldEntity);
        repostOnArrival = isStaying();
    }

    /** Re-posts a staying vex that just arrived from another level at the block it arrived in (spec §3.3). */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (repostOnArrival) {
            repostOnArrival = false;
            setBoundOrigin(blockPosition());
        }
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
     * Vanilla's spawn (the iron sword), then the random follow-range bonus {@code Mob.finalizeSpawn} adds (the
     * inherited {@code RANDOM_SPAWN_BONUS_ID}, docs/api-notes.md §21) goes, so the attribute is exactly
     * {@link GuardArea#FOLLOW_RANGE} (docs/api-notes.md §19: the targeting goal reads the attribute).
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
     * Turns staying on or off; on, the block it stands at becomes the post the vex hovers at (Part 3 spec §2.1).
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
     * The summoner while online, alive, in this vex's level and not a spectator (spec §3.1); empty otherwise, and the
     * vex idles. A spectator counts as absent, as vanilla pets ignore a spectating owner
     * ({@code TamableAnimal.unableToMoveToOwner}): twelve visible vexes trailing a spectator would give its position
     * away.
     *
     * @return the summoner, or empty
     */
    public Optional<ServerPlayer> summoner() {
        if (!(level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return summonerId()
                .map(level::getPlayerByUUID)
                .filter(player -> player instanceof ServerPlayer && player.isAlive() && !player.isSpectator())
                .map(ServerPlayer.class::cast);
    }

    /**
     * Whether a Bound Vex of {@code summoner} may fight {@code target} (spec §3.4; round one's rule): anything
     * attackable that is no ally of the summoner, no player the summoner cannot harm ({@code canHarmPlayer}: the
     * {@code pvp} game rule; teammates are already allies), no mob another player summoned unless the summoner may
     * harm that player (with {@code pvp} off, two players' vexes leave each other alone; an offline summoner's mobs
     * follow the {@code pvp} rule alone), and nothing while the summoner is not in {@code level}
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
     * Whether a target is worth taking or keeping (spec §3.4): in this vex's level (a summoner's memory can name a
     * mob it left behind in another dimension, whose raw coordinates may line up), alive, attackable by this vex
     * ({@code Mob.canAttack}: false for a creative or spectator player, an invulnerable mob or a ghast, the targets
     * {@code setTarget} would not hold), still prey, and, following, within {@link GuardArea#REACH}, or, staying, inside the stay area
     * ({@link #inGuardArea}, Part 3 spec §2.3). The alive clause is a cheap
     * guard: {@code canAttack} already implies it for a living target ({@code LivingEntity.canBeSeenByAnyone}). Every
     * target goal's {@code canContinueToUse}, which replaces {@code TargetGoal}'s and never calls {@code super}
     * (docs/api-notes.md §21), and every target goal's candidate filter.
     */
    boolean keepsTarget(@Nullable LivingEntity target) {
        return target != null
                && target.level().dimension().equals(level().dimension())
                && target.isAlive()
                && canAttack(target)
                && isPrey(target)
                && (isStaying() ? inGuardArea(target) : withinReach(target));
    }

    /** Whether {@code other} is within {@link GuardArea#REACH} of this vex, feet to feet. */
    boolean withinReach(Entity other) {
        Vec3 offset = other.position().subtract(position());
        return GuardArea.withinReach(offset.x, offset.y, offset.z);
    }

    /**
     * Whether {@code other}'s position is inside this vex's guard box, faces included, feet to feet. Following (spec
     * §3.4): 8×8×7 around the vex ({@link GuardArea#contains}); staying (Part 3 spec §2.3): 13×13×7 around the post
     * point, wherever the vex hangs ({@link GuardArea#containsStaying}). The one box test every goal uses: entity
     * queries take {@link #guardArea()} as the broad phase and keep only what this accepts. Public for its GameTest.
     *
     * @param other the entity to test
     * @return true when its feet are in the box
     */
    public boolean inGuardArea(Entity other) {
        if (isStaying()) {
            Vec3 offset = other.position().subtract(postPoint());
            return GuardArea.containsStaying(offset.x, offset.y, offset.z);
        }
        Vec3 offset = other.position().subtract(position());
        return GuardArea.contains(offset.x, offset.y, offset.z);
    }

    /**
     * The broad-phase box for entity queries: following, the box around the vex; staying, the stay box around the post
     * point. {@code getEntitiesOfClass} returns what this box <em>intersects</em>, which is more than
     * {@link #inGuardArea} accepts, so filter every query's hits through it.
     */
    AABB guardArea() {
        if (isStaying()) {
            Vec3 post = postPoint();
            return new AABB(post, post)
                    .inflate(GuardArea.STAY_HALF_WIDTH, GuardArea.HALF_HEIGHT, GuardArea.STAY_HALF_WIDTH);
        }
        return new AABB(position(), position())
                .inflate(GuardArea.HALF_WIDTH, GuardArea.HALF_HEIGHT, GuardArea.HALF_WIDTH);
    }

    /**
     * How far from its post point a staying, idle vex may hang before it flies back (Part 3 spec §2.2). Public for the
     * GameTests, which live in another package.
     */
    public static final double POST_TOLERANCE = 1.0;

    /** The return flight's speed: the follow goal's coasting speed. */
    private static final double POST_SPEED = 0.7;

    /**
     * The point a staying vex hovers at and its stay area is centered on: the middle of the post block's floor
     * (Part 3 spec glossary). The post is vanilla's bound origin, set by {@link #setStaying}; a vex without one
     * (never the case for a staying vex) answers with its own block.
     *
     * @return the post point
     */
    Vec3 postPoint() {
        BlockPos post = getBoundOrigin();
        return Vec3.atBottomCenterOf(post != null ? post : blockPosition());
    }

    /**
     * Whether a patrol or an ally's fight may pull this vex onto {@code target} (spec §3.4 items 2 and 3): hostile
     * ({@code Enemy}), no creeper (a creeper is fought only when it provoked, through the grudge goal, and the flee
     * goal handles a hissing one), and carrying no other player's summoner mark (another player's summons are treated
     * like that player, who is no {@code Enemy}: they are prey for the grudge goal under the PvP rule, so a vex answers
     * a hit, but never a patrol or ally-fight target). {@link #keepsTarget} is a separate test.
     *
     * @param target the candidate
     * @return whether the patrol and ally goals may pick it
     */
    boolean huntable(LivingEntity target) {
        return target instanceof Enemy
                && !(target instanceof Creeper)
                && target.getExistingData(AllyAttachments.SUMMONER)
                        .filter(mark -> !isSummonedBy(mark))
                        .isEmpty();
    }

    /**
     * The entities of {@code type} whose feet are within {@code range} blocks of this vex (a sphere, not the cube
     * {@code getEntitiesOfClass} returns for the box around it) and that pass {@code filter}.
     *
     * @param type the class to look for
     * @param range the sphere's radius, feet to feet
     * @param filter a further test
     * @param <T> the entity class
     * @return the matches, in query order
     */
    <T extends Entity> List<T> nearby(Class<T> type, double range, Predicate<? super T> filter) {
        double limit = range * range;
        return level().getEntitiesOfClass(
                        type,
                        new AABB(position(), position()).inflate(range),
                        entity -> distanceToSqr(entity) <= limit && filter.test(entity));
    }

    /**
     * The summoner's allies ({@code Allies.isAllyOf}) whose feet are inside this vex's guard area, this vex excepted:
     * a vex is its own summoner's ally, and counting itself would hand its own target to the ally goal (spec §3.4).
     * The answer is kept for the tick, so the grudge goal and the ally goal, which scan on the same tick, share one
     * entity query. The list is cached for the current tick, so a second call in the same tick returns the first call's
     * list, and the owner argument is assumed to be this vex's summoner on every call.
     *
     * @param owner the summoner
     * @return the allies in the area
     */
    List<Mob> alliesInGuardArea(ServerPlayer owner) {
        if (alliesTick != tickCount) {
            UUID self = getUUID();
            alliesCache = level().getEntitiesOfClass(
                            Mob.class,
                            guardArea(),
                            mob -> !mob.getUUID().equals(self) && inGuardArea(mob) && Allies.isAllyOf(owner, mob));
            alliesTick = tickCount;
        }
        return alliesCache;
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
     * The summoner's main-hand clicks (Part 3 spec §3). With an empty hand: sneaking toggles stay, with the vex's own
     * sound; not sneaking is the weapon take-back, which Task 5 of the Part 3 plan adds. Only the server decides, since
     * the client does not know the summoner (the mark is not synced). The client answers {@code SUCCESS_SERVER} for
     * every empty-handed main-hand click: a client {@code PASS} would send {@code Minecraft.startUseItem} on to the
     * off hand and throw a pearl or raise a shield on each click (docs/api-notes.md §22). The swing comes from the
     * server; a stranger's click is answered {@code PASS} there and changes nothing.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!isSummonedBy(player.getUUID())) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            setStaying(!isStaying());
            playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Takes the target and, when it is a new one, calls the summoner's tamed, idle, un-sitting wolves within
     * {@link #WOLF_CALL_RANGE} that want to attack it ({@code Wolf.wantsToAttack}: never a creeper, a ghast or a
     * player the summoner may not harm) onto it (spec §3.4). "New" means it differs from the target held just before:
     * {@code TargetGoal.canContinueToUse} re-sets the same target on every full tick (docs/api-notes.md §21), and those
     * re-sets call nobody. A target {@code getTarget} refuses, a change any listener cancels
     * ({@code LivingChangeTargetEvent}; nothing in this mod cancels a Bound Vex's) and clearing a target call nobody
     * either: the check that the target was really set guards the cancelled change.
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
     * Brings a staying, idle vex back to its post point (Part 3 spec §2.2): one flight per use, started when the vex
     * hangs farther than {@link #POST_TOLERANCE} from it and the move control is free. The tolerance is wider than
     * the distance at which vanilla's move control calls a move finished (about 0.53, docs/api-notes.md §22), so the
     * goal never re-issues a move the control just ended. A vex has no gravity, so once there it hangs still.
     */
    private final class HoldPostGoal extends Goal {
        HoldPostGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return BoundVex.this.isStaying()
                    && BoundVex.this.getTarget() == null
                    && !BoundVex.this.getMoveControl().hasWanted()
                    && BoundVex.this.position().distanceToSqr(BoundVex.this.postPoint())
                            > POST_TOLERANCE * POST_TOLERANCE;
        }

        /** One move per use. */
        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Vec3 post = BoundVex.this.postPoint();
            BoundVex.this.getMoveControl().setWantedPosition(post.x, post.y, post.z, POST_SPEED);
        }
    }
}
