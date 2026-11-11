package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import io.github.willrieske.spellcraft.spell.rules.GuardPosture;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
 * spec §2): {@link #scanDue} (the clock), {@link #inGuardArea} (the box), {@link #takesTarget}, {@link #keepsTarget}
 * and {@link #huntable}.
 * Without a summoner online, alive, in its level and not spectating it idles. The summoner is the
 * {@link AllyAttachments#SUMMONER} mark, so {@code Allies}, {@code GolemMercy} and the Trailing flame already treat it
 * as the summoner's.
 */
public class BoundVex extends Vex {
    /** Synced: whether the vex holds its post instead of following (spec §3.3). */
    private static final EntityDataAccessor<Boolean> STAYING =
            SynchedEntityData.defineId(BoundVex.class, EntityDataSerializers.BOOLEAN);

    private static final String STAYING_TAG = "staying";

    /** The slots a Bound Vex can hold gear in (Part 3 spec §4.1, §5.1). */
    public static final List<EquipmentSlot> GEAR_SLOTS = List.of(
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS);

    /** Lang key of the notice for an empty-hand click on a vex in a team move (Parts 6-7 spec section 2.6). */
    public static final String BUSY_KEY = "message.spellcraft.vex_busy";

    private static final String STASH_TAG_PREFIX = "stash_";
    private static final String CONJURED_TAG_PREFIX = "conjured_";

    /**
     * The wide posture's scan interval (spec §3.4 "every 10 ticks") and the fixed cadence of {@link SupportGearGoal}.
     * Every other scanning goal reads {@link #scanInterval()}; all of them go through {@link #scanDue}.
     */
    static final int SCAN_INTERVAL = GuardPosture.WIDE.scanInterval();

    /** How far the summoner's wolves hear the call to a fight (spec §3.4), feet to feet. */
    static final double WOLF_CALL_RANGE = 16.0;

    /**
     * How far from its post point a staying, idle vex may hang before it flies back (Part 3 spec §2.2). Public for the
     * GameTests, which live in another package.
     */
    public static final double POST_TOLERANCE = 1.0;

    /** The return flight's speed when the vex is farther than {@link #POST_NEAR} from the post point. */
    private static final double POST_SPEED = 0.7;

    /**
     * The return flight's speed within {@link #POST_NEAR} of the post point: slow enough that a pass cannot overshoot.
     */
    private static final double POST_APPROACH_SPEED = 0.25;

    /** The distance from the post point inside which the return flight uses {@link #POST_APPROACH_SPEED}. */
    private static final double POST_NEAR = 3.0;

    /**
     * Squared speed (blocks per tick) below which a vex counts as still, so the return flight waits out a coast.
     * Public for the GameTests, which live in another package.
     */
    public static final double STILL_SPEED_SQR = 0.01;

    /**
     * Set by {@link #restoreFrom} on the new copy a cross-dimension teleport makes of a staying vex, and spent by
     * {@link #onAddedToLevel}. A plain field: the copy is built fresh, so it is false on a chunk load.
     */
    private boolean repostOnArrival;

    /** The allies found on {@link #alliesTick}; see {@link #alliesInGuardArea}. */
    private List<Mob> alliesCache = List.of();

    private int alliesTick = -1;

    /** Given items set aside while a conjured item sits in their slot (Part 3 spec §5.1). */
    private final Map<EquipmentSlot, ItemStack> stash = new EnumMap<>(EquipmentSlot.class);

    /** The slots that hold a conjured item right now: never dropped, never taken (Part 3 spec §5.1). */
    private final Set<EquipmentSlot> conjured = EnumSet.noneOf(EquipmentSlot.class);

    /**
     * The team move this vex takes part in, {@link TeamMove#NONE} when it takes part in none (Parts 6-7 spec section
     * 2.1). Server-only: not synced and not saved, because nothing on the client reads it and a vex that loads, or
     * comes through a portal, is a new entity whose kit {@code readAdditionalSaveData} has already dismissed.
     */
    private TeamMove teamMove = TeamMove.NONE;

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
     * so the charge goal is this class's copy, the post-holding goal is its own, and a Bound Vex has no raider grudge,
     * no owner to copy a target from and no player hunt. Its three target goals are {@link HurtBySummonerSideGoal}
     * (grudges), {@link AllyFightGoal} (an ally's fight) and {@link NearestHostileGoal} (the patrol). The job goals sit
     * beside them: {@link FleeCreeperGoal} and {@link DodgeGolemGoal} outrank everything on the move flag,
     * {@link RepairGolemGoal}, {@link CureZombieVillagerGoal}, {@link BribeGoal} and {@link AttackCrystalGoal} outrank
     * the charge and the follow; {@link RescueGoal} (priority -1) outranks them all while {@link VexRescues} names the
     * vex a rescuer. {@link TeamMoveGoal} (priority 1) runs a team move's part and keeps its kit on, above the job,
     * charge, ranged and follow goals and below the flee, dodge and rescue goals.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(-1, new RescueGoal(this));
        goalSelector.addGoal(0, new FleeCreeperGoal(this));
        goalSelector.addGoal(0, new DodgeGolemGoal(this));
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TeamMoveGoal(this));
        goalSelector.addGoal(2, new RepairGolemGoal(this));
        goalSelector.addGoal(2, new CureZombieVillagerGoal(this));
        goalSelector.addGoal(2, new BribeGoal(this));
        goalSelector.addGoal(2, new AttackCrystalGoal(this));
        goalSelector.addGoal(3, new RangedGearGoal(this));
        goalSelector.addGoal(3, new ChargeGoal());
        goalSelector.addGoal(3, new SupportGearGoal(this));
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
        for (EquipmentSlot slot : GEAR_SLOTS) {
            // ItemStack.CODEC rejects an empty stack, so only a real stash is written.
            if (!stashed(slot).isEmpty()) {
                output.store(STASH_TAG_PREFIX + slot.getName(), ItemStack.CODEC, stashed(slot));
            }
            if (isConjured(slot)) {
                output.putBoolean(CONJURED_TAG_PREFIX + slot.getName(), true);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(STAYING, input.getBooleanOr(STAYING_TAG, false));
        // A staying vex with no saved post (hand-edited, or /summon with NBT) would carry a post that moves with it.
        // The position is read before this method (Entity.load), so the vex's own block is available here.
        if (isStaying() && getBoundOrigin() == null) {
            setBoundOrigin(blockPosition());
        }
        // A vex saved by Part 2 carries the old base value (7.25, the reach), which would cut a staying vex's fights
        // short: TargetGoal.canContinueToUse reads the attribute live (docs/api-notes.md §22).
        AttributeInstance range = getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) {
            range.setBaseValue(GuardArea.FOLLOW_RANGE);
        }
        stash.clear();
        conjured.clear();
        for (EquipmentSlot slot : GEAR_SLOTS) {
            input.read(STASH_TAG_PREFIX + slot.getName(), ItemStack.CODEC).ifPresent(stack -> stash.put(slot, stack));
            if (input.getBooleanOr(CONJURED_TAG_PREFIX + slot.getName(), false)) {
                conjured.add(slot);
            }
        }
        // The goal that conjured the item is not running after a load or a cross-dimension copy (Part 3 spec §5.1).
        GEAR_SLOTS.forEach(this::dismiss);
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

    /**
     * Puts a conjured item in {@code slot} (Part 3 spec §5.1). If the slot is not conjured yet, what it holds moves to
     * the stash first; conjuring over a conjured item just replaces it. The item is set silently
     * ({@code setItemSlot(slot, stack, true)} skips {@code LivingEntity.onEquipItem}, so no equip sound plays,
     * docs/api-notes.md §22).
     *
     * @param slot a gear slot
     * @param stack the conjured item
     */
    public void conjure(EquipmentSlot slot, ItemStack stack) {
        if (conjured.add(slot)) {
            stash.put(slot, getItemBySlot(slot).copy());
        }
        setItemSlot(slot, stack, true);
    }

    /**
     * Ends the conjure in {@code slot}: the conjured item goes and the stashed item returns (Part 3 spec §5.1). Does
     * nothing for a slot that is not conjured. The stashed item is restored silently, like the conjure.
     *
     * @param slot a gear slot
     */
    public void dismiss(EquipmentSlot slot) {
        if (conjured.remove(slot)) {
            ItemStack back = stash.remove(slot);
            setItemSlot(slot, back != null ? back : ItemStack.EMPTY, true);
        }
    }

    /**
     * Whether {@code slot} holds a conjured item right now.
     *
     * @param slot a gear slot
     * @return true while a conjure is in effect
     */
    public boolean isConjured(EquipmentSlot slot) {
        return conjured.contains(slot);
    }

    /**
     * The item set aside for {@code slot} while a conjured item sits there.
     *
     * @param slot a gear slot
     * @return a copy of the stashed stack, or {@link ItemStack#EMPTY}; changing it does not change the stash
     */
    public ItemStack stashed(EquipmentSlot slot) {
        ItemStack stashed = stash.get(slot);
        return stashed != null ? stashed.copy() : ItemStack.EMPTY;
    }

    /**
     * Puts a gift in the stash of a conjured slot, to come back when the conjure ends (Part 3 spec §5.1). Public
     * for the give click (Part 3 plan Task 5) and the GameTests in another package. The stash keeps a copy of
     * {@code gift}, so a caller that changes its stack afterwards does not change the stash. A gift for a slot that is
     * not conjured would never be restored, saved or dropped, so it is refused.
     *
     * @param slot a conjured gear slot
     * @param gift the item given
     * @return what the stash held before, or {@link ItemStack#EMPTY}
     * @throws IllegalStateException if {@code slot} is not conjured
     */
    public ItemStack stashGift(EquipmentSlot slot, ItemStack gift) {
        if (!isConjured(slot)) {
            throw new IllegalStateException("cannot stash a gift for a slot that is not conjured: " + slot);
        }
        ItemStack before = stash.put(slot, gift.copy());
        return before != null ? before : ItemStack.EMPTY;
    }

    /**
     * Drops everything the vex was given (Part 3 spec §5.2): conjured items vanish and their stashed items return to
     * their slots, then every gear slot is marked a guaranteed drop, which vanilla drops undamaged whoever the killer
     * was (docs/api-notes.md §22). The marks are set here, not at spawn: vanilla zeroes the main hand's chance at
     * spawn and saves the chances.
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        for (EquipmentSlot slot : GEAR_SLOTS) {
            dismiss(slot);
            setGuaranteedDrop(slot);
        }
        super.dropCustomDeathLoot(level, source, killedByPlayer);
    }

    /**
     * On Peaceful, vanilla discards the vex with no death and no drops (docs/api-notes.md §22), so its gear is dropped
     * here first (Part 3 spec §5.2), under the rules of the death path: nothing drops when the {@code mob_drops} game
     * rule is off ({@code shouldDropLoot}), and a stack with Curse of Vanishing ({@code PREVENT_EQUIPMENT_DROP}, as in
     * {@code Mob.dropCustomDeathLoot}) is not dropped. A stack that is not dropped is cleared with the vex.
     */
    @Override
    public void checkDespawn() {
        if (level() instanceof ServerLevel level
                && level.getDifficulty() == Difficulty.PEACEFUL
                && !getType().isAllowedInPeaceful()) {
            boolean dropsLoot = shouldDropLoot(level);
            for (EquipmentSlot slot : GEAR_SLOTS) {
                dismiss(slot);
                ItemStack stack = getItemBySlot(slot);
                if (dropsLoot
                        && !stack.isEmpty()
                        && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    spawnAtLocation(level, stack);
                }
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        super.checkDespawn();
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
     * {@code pvp} game rule; teammates are already allies), no {@code ZombieVillager} (the cure stops one instead,
     * Parts 4-5 spec §2.1), no bribed vex (§3.2), no vex a Bound Vex is courting right now
     * ({@link BribeGoal#isCourted}, §3.1), no mob another player summoned unless the summoner may
     * harm that player (with {@code pvp} off, two players' vexes leave each other alone; an offline summoner's mobs
     * follow the {@code pvp} rule alone), and nothing while the summoner is not in {@code level}
     * (offline or in another dimension). A courted vex is spared by every player's Bound Vexes, not only the
     * claimant's, for at most the claim's lifetime (a claim older than the longest courtship counts as free). Public
     * for its GameTests.
     *
     * @param level the level the vex is in
     * @param summoner the UUID in the vex's summoner mark
     * @param target the candidate
     * @return whether the vex may take {@code target} as its target
     */
    public static boolean isPrey(ServerLevel level, UUID summoner, LivingEntity target) {
        if (!target.attackable()
                || target instanceof ZombieVillager
                || AllyAttachments.isBribed(target)
                || BribeGoal.isCourted(target)) {
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
     * Whether a target is worth <em>taking</em> (spec §3.4; Part 3 spec §4.3): the test every target goal's pick runs.
     * In this vex's level (a summoner's memory can name a mob it left behind in another dimension, whose raw
     * coordinates may line up), alive, attackable by this vex ({@code Mob.canAttack}: false for a creative or spectator
     * player, an invulnerable mob or a ghast, the targets {@code setTarget} would not hold), still prey, and,
     * following,
     * within {@link GuardArea#REACH} ({@link #withinReach}, whatever the vex holds), or, staying, inside the stay area
     * ({@link #inGuardArea}, Part 3 spec §2.3). The alive clause is a cheap guard: {@code canAttack} already implies it
     * for a living target ({@code LivingEntity.canBeSeenByAnyone}). {@link #keepsTarget} is the same test with a wider
     * reach for ranged gear; it alone ends a fight.
     *
     * @param target the candidate, or null
     * @return whether a target goal may pick it
     */
    boolean takesTarget(@Nullable LivingEntity target) {
        return canFight(target) && (isStaying() ? inGuardArea(target) : withinReach(target));
    }

    /**
     * Whether a target already held is worth <em>keeping</em> (spec §3.4; Part 3 spec §4.3): {@link #takesTarget}'s
     * clauses, except that a following vex holding ranged gear keeps a target out to
     * {@link RangedGearGoal#MAX_DISTANCE} plus {@link RangedGearGoal#KEEP_MARGIN} instead of {@link GuardArea#REACH}
     * ({@link #withinKeepRange}). Every target goal's {@code canContinueToUse}, which replaces {@code TargetGoal}'s and
     * never calls {@code super} (docs/api-notes.md §21), runs this and nothing else; no goal picks with it.
     *
     * @param target the held target, or null
     * @return whether the vex keeps it
     */
    boolean keepsTarget(@Nullable LivingEntity target) {
        return canFight(target) && (isStaying() ? inGuardArea(target) : withinKeepRange(target));
    }

    /** The clauses {@link #takesTarget} and {@link #keepsTarget} share: everything but the distance. */
    private boolean canFight(@Nullable LivingEntity target) {
        return target != null
                && target.level().dimension().equals(level().dimension())
                && target.isAlive()
                && canAttack(target)
                && isPrey(target);
    }

    /**
     * Whether a following vex still holds {@code target}: within {@link GuardArea#REACH}, except that a vex holding
     * ranged gear keeps a target within {@link RangedGearGoal#MAX_DISTANCE} plus {@link RangedGearGoal#KEEP_MARGIN}
     * (Part 3 spec §4.3). {@link RangedGearGoal} holds such a vex 6 to 10 blocks from its target, beyond the reach;
     * with the reach as the limit it would back off, lose the target, return, take it again and never settle. Only the
     * keeping widens: {@link #takesTarget} stays with the reach.
     */
    private boolean withinKeepRange(Entity target) {
        if (VexGear.isRanged(getMainHandItem())) {
            double limit = RangedGearGoal.MAX_DISTANCE + RangedGearGoal.KEEP_MARGIN;
            return distanceToSqr(target) <= limit * limit;
        }
        return withinReach(target);
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

    /** Returns the team move this vex takes part in, or {@link TeamMove#NONE}. */
    public TeamMove teamMove() {
        return teamMove;
    }

    /**
     * Marks the vex as a member of {@code move} ({@link TeamMove#NONE} frees it). {@link TeamMoves} is the only caller
     * in play; the GameTests mark vexes directly.
     *
     * @param move the move
     */
    public void setTeamMove(TeamMove move) {
        teamMove = Objects.requireNonNull(move, "move");
    }

    /**
     * Whether a patrol or an ally's fight may pull this vex onto {@code target} (spec §3.4 items 2 and 3): hostile
     * ({@code Enemy}), no creeper (a creeper is fought only when it provoked, through the grudge goal, and the flee
     * goal handles a hissing one), and carrying no other player's summoner mark (another player's summons are treated
     * like that player, who is no {@code Enemy}: they are prey for the grudge goal under the PvP rule, so a vex answers
     * a hit, but never a patrol or ally-fight target). {@link #takesTarget} is a separate test.
     *
     * @param target the candidate
     * @return whether the patrol and ally goals may pick it
     */
    boolean huntable(LivingEntity target) {
        return huntable(summonerId().orElse(null), target);
    }

    /**
     * {@link #huntable(LivingEntity)} for a summoner that need not have a vex at hand: the team-move scan and request
     * ask it for the summoner alone.
     *
     * @param summoner the summoner's UUID, or null for a vex nobody summoned
     * @param target the candidate
     * @return whether the patrol and ally goals of that summoner's vexes may pick it
     */
    static boolean huntable(@Nullable UUID summoner, LivingEntity target) {
        return target instanceof Enemy
                && !(target instanceof Creeper)
                && target.getExistingData(AllyAttachments.SUMMONER)
                        .filter(mark -> !mark.equals(summoner))
                        .isEmpty();
    }

    /**
     * Whether {@code target} could be the enemy of a team move of {@code summoner}: alive, {@link #huntable} and
     * {@link #isPrey}. The team-move scan and the manual request share it.
     *
     * @param level the summoner's level
     * @param summoner the summoner's UUID
     * @param target the candidate
     * @return whether a team move may be fought against it
     */
    static boolean isSubject(ServerLevel level, UUID summoner, LivingEntity target) {
        return target.isAlive() && huntable(summoner, target) && isPrey(level, summoner, target);
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
     * list, and the owner argument is assumed to be this vex's summoner on every call. A zombie villager being cured
     * ({@code AllyAttachments.isBeingCured}) is excluded although {@code Allies.isAllyOf} counts it: it is an ally only
     * so that fangs and golems spare it, and counting it here would make the grudge and ally goals avenge it on whoever
     * hurt it (another player with PvP on).
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
                            mob -> !mob.getUUID().equals(self)
                                    && !AllyAttachments.isBeingCured(mob)
                                    && inGuardArea(mob)
                                    && Allies.isAllyOf(owner, mob));
            alliesTick = tickCount;
        }
        return alliesCache;
    }

    /**
     * The goal clock (spec §3.4): true once every {@code interval} ticks, the posture's ({@link #scanInterval()}).
     * {@code Mob.serverAiStep} asks a goal's {@code canUse} only when {@code tickCount + getId()} is even
     * (docs/api-notes.md §21, "The goal clock"), so a plain {@code tickCount % 10} gate never opens for an odd id;
     * keyed the same way, an even interval always lands on a tick where {@code canUse} runs. Every scanning goal reads
     * its cadence here.
     *
     * @param interval ticks between scans; even
     * @return whether this tick is a scan tick
     */
    boolean scanDue(int interval) {
        return (tickCount + getId()) % interval == 0;
    }

    /**
     * Where this vex hovers and how often it scans, by where it is (Parts 4–5 spec §6.1): the Overworld by day and
     * any modded dimension keep the wide posture; the dark Overworld, the Nether and the End close in overhead.
     *
     * @return the posture for this vex's level, now
     */
    public GuardPosture posture() {
        ResourceKey<Level> dimension = level().dimension();
        GuardPosture.Place place = dimension.equals(Level.OVERWORLD)
                ? GuardPosture.Place.OVERWORLD
                : dimension.equals(Level.NETHER)
                        ? GuardPosture.Place.NETHER
                        : dimension.equals(Level.END) ? GuardPosture.Place.END : GuardPosture.Place.OTHER;
        return GuardPosture.of(place, level().isDarkOutside());
    }

    /**
     * Ticks between this vex's scans: the posture's (10, 4 or 2). Even, so it lands on the goal clock
     * ({@link #scanDue}).
     *
     * @return the scan interval
     */
    public int scanInterval() {
        return posture().scanInterval();
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
     * The summoner's main-hand clicks (Part 3 spec §3, §4.2, §4.4). A gear item ({@link VexGear#slotFor}), sneaking or
     * not, is given; an empty hand on a vex in a team move answers {@link #BUSY_KEY} and changes nothing; an empty hand
     * sneaking toggles stay, with the vex's own sound; an empty hand not sneaking takes the main-hand weapon back;
     * anything else is {@code PASS}. Only the server decides, since the client does not know the
     * summoner (the mark is not synced). The client answers {@code SUCCESS_SERVER} for an empty hand and for a gear
     * item: a client {@code PASS} would send {@code Minecraft.startUseItem} on to the off hand and throw a pearl or
     * raise a shield on each click (docs/api-notes.md §22). The swing comes from the server; a stranger's click is
     * answered {@code PASS} there and changes nothing.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getMainHandItem();
        Optional<EquipmentSlot> slot = VexGear.slotFor(held);
        if (!held.isEmpty() && slot.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!isSummonedBy(player.getUUID())) {
            return InteractionResult.PASS;
        }
        if (slot.isPresent()) {
            give(player, held, slot.get());
        } else if (teamMove != TeamMove.NONE) {
            // A member of a team move holds its post: the empty-hand clicks change nothing (Parts 6-7 spec section
            // 2.6).
            player.sendOverlayMessage(Component.translatable(BUSY_KEY));
        } else if (player.isShiftKeyDown()) {
            setStaying(!isStaying());
            playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
        } else {
            takeBackWeapon(player);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Takes one item from the summoner's hand into {@code slot} and hands back what was there (Part 3 spec §4.2). While
     * the slot holds a conjured item the gift waits in the stash and the previously stashed item comes back instead.
     * What does not fit the player's inventory is dropped at the player. {@code setItemSlot} plays the equip sound of
     * armor and of a shield in the off hand itself ({@code LivingEntity.onEquipItem}, docs/api-notes.md §22), so only
     * the silent cases get the vex's own sound: the main hand, a gift that goes to the stash, and a gift identical to
     * the piece it replaces (vanilla plays no sound when the item and its components do not change).
     */
    private void give(Player player, ItemStack held, EquipmentSlot slot) {
        ItemStack gift = held.consumeAndReturn(1, player);
        ItemStack back;
        boolean silent = slot == EquipmentSlot.MAINHAND;
        if (isConjured(slot)) {
            back = stashGift(slot, gift);
            silent = true;
        } else {
            back = getItemBySlot(slot).copy();
            setItemSlot(slot, gift);
            // Vanilla's onEquipItem plays nothing when the new piece is the same item with the same components.
            silent |= ItemStack.isSameItemSameComponents(back, gift);
        }
        if (!back.isEmpty() && !player.getInventory().add(back)) {
            player.drop(back, false, Prediction.SERVER_ONLY);
        }
        if (silent) {
            playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
        }
    }

    /** Hands the main-hand item to the summoner's empty hand; a conjured item is not handed over (spec §4.4). */
    private void takeBackWeapon(Player player) {
        ItemStack weapon = getMainHandItem();
        if (weapon.isEmpty() || isConjured(EquipmentSlot.MAINHAND)) {
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        playSound(SoundEvents.VEX_AMBIENT, 1.0F, 1.0F);
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
     * A shield in the off hand blocks from the front (Part 3 spec §4.3). Vanilla's own blocking needs the entity to be
     * using the shield, which a mob never is, so this repeats its geometry (docs/api-notes.md §22): no block without
     * a source position (fire, a fall), for a shield-bypassing damage type or a piercing arrow; otherwise the flat
     * view vector from the head's yaw against the flat direction to the source (a hit from exactly 90° to the side is
     * blocked, as in vanilla). A hit that would not hurt anyway is no block (it falls through to {@code super}); a
     * block records its attacker through {@code setLastHurtByMob}, so the grudge goal answers it. No durability is
     * spent.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // A hit that would not have hurt anyway (a dead or dying vex, damage it is immune to, zero damage) is no block.
        if (!isDeadOrDying() && !isInvulnerableTo(level, source) && damage > 0.0F && blocksWithShield(source)) {
            playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.0F);
            // Vanilla's blocking records the attacker too (resolveMobResponsibleForDamage); the grudge goal reads it.
            if (source.getEntity() instanceof LivingEntity attacker) {
                setLastHurtByMob(attacker);
            }
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    /** Whether the off-hand shield stops {@code source} (see {@link #hurtServer}). */
    private boolean blocksWithShield(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null
                || VexGear.kindOf(getOffhandItem()) != VexGear.Kind.SHIELD
                || source.is(DamageTypeTags.BYPASSES_SHIELD)
                || (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0)) {
            return false;
        }
        Vec3 toSource = from.subtract(position()).multiply(1.0, 0.0, 1.0);
        if (toSource.lengthSqr() < 1.0E-8) {
            return true;
        }
        return calculateViewVector(0.0F, getYHeadRot()).dot(toSource.normalize()) >= 0.0;
    }

    /**
     * The weapon is never worn down (Part 3 spec §4.5): the hit runs on the stack and a copy of the stack as it was is
     * put back after, so durability and count are restored (only if the vex is still alive after the hit).
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        ItemStack before = getMainHandItem().copy();
        boolean hurt = super.doHurtTarget(level, target);
        if (isAlive() && !ItemStack.matches(before, getMainHandItem())) {
            setItemSlot(EquipmentSlot.MAINHAND, before, true);
        }
        return hurt;
    }

    /**
     * Vanilla's {@code Vex.VexChargeAttackGoal}, copied because it is private (docs/api-notes.md §21): fly at the
     * target's eyes and hit it; this is how a Bound Vex uses the sword it holds. It runs only for a vex that fights in
     * melee: the main-hand item picks the fighting style (Part 3 spec §4.3), so ranged and support gear leave it to
     * {@link RangedGearGoal} and {@link SupportGearGoal}.
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
                    && !VexGear.isRanged(BoundVex.this.getMainHandItem())
                    && !VexGear.isSupport(BoundVex.this.getMainHandItem())
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
     * hangs farther than {@link #POST_TOLERANCE} from it, the move control is free and the vex is nearly still.
     * Vanilla's move control only halves the velocity when it comes within about 0.53 of the wanted point
     * (docs/api-notes.md §22), so after a long flight the vex coasts a couple of blocks past the post; the goal
     * therefore waits until the coast is over ({@link #STILL_SPEED_SQR}) before it flies again, and flies at the slow
     * {@link #POST_APPROACH_SPEED} within {@link #POST_NEAR}, so that a second pass cannot overshoot the tolerance. A
     * vex has no gravity, so once there it hangs still.
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
                    && BoundVex.this.getDeltaMovement().lengthSqr() < STILL_SPEED_SQR
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
            double speed = BoundVex.this.position().distanceToSqr(post) > POST_NEAR * POST_NEAR
                    ? POST_SPEED
                    : POST_APPROACH_SPEED;
            BoundVex.this.getMoveControl().setWantedPosition(post.x, post.y, post.z, speed);
        }
    }
}
