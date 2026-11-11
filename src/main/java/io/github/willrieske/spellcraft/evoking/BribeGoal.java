package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.BribeRule;
import io.github.willrieske.spellcraft.spell.rules.ScrollDraw;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex offers an enemy vex an emerald before it fights it (Evoking II Parts 4-5 spec §3.1). On each scan
 * ({@code BoundVex.scanDue}), when the vex's summoner is here, the nearest vanilla vex (exactly
 * {@code EntityTypes.VEX}: never a Bound Vex) whose feet are in the guard area, that is alive, carries neither the
 * bribed nor the bribe-tried mark, is not courted by another living Bound Vex and whose target is the summoner or an
 * ally of the summoner is taken; whether this vex has a target of its own does not matter. The vex flies to within
 * {@link Jobs#REACH} of it (at most {@link Jobs#APPROACH_LIMIT} ticks), holds a conjured emerald out for
 * {@link #OFFER_TICKS} ticks (the villager "hmm" once at the start, the head turned to the other vex) and rolls
 * {@link BribeRule}: two in three the other vex is bribed ({@link AllyAttachments#BRIBED}, its target cleared,
 * {@link FleeFromViewGoal} its only goal), one in three it refuses ({@link AllyAttachments#BRIBE_TRIED}) and is prey as
 * before.
 *
 * <p>From the claim until the roll the vex is <em>courted</em> ({@link #isCourted}): {@link BoundVex#isPrey} answers
 * false for it, for every Bound Vex, so the summoner's other vexes drop it and the friendly-fire backstop cancels a
 * hit already on its way. Every early end (the other vex dies or leaves the guard area, the summoner goes, a higher
 * goal such as {@link FleeCreeperGoal} replaces this one) leaves no mark and frees the claim. An approach that times
 * out also keeps this vex from starting another bribe for {@link #RETRY_DELAY} ticks (a departure from the spec: see
 * that constant). The emerald is conjured ({@code BoundVex.conjure}), so nothing drops if the vex dies. The goal is
 * interruptable. The vanilla facts are docs/api-notes.md §23.
 *
 * <p>The goal does not re-check the enemy vex's target mid-offer: a vex that retargets (or loses its target) after the
 * claim still gets the emerald and the roll. The roll happens exactly once: vanilla ticks a running goal with
 * {@code requiresUpdateEveryTick} on ticks its {@code canContinueToUse} is not asked, so {@link #tick} returns at
 * once when {@code finished} is set, and the roll is reached only from {@link #tick}. A vanilla vex carries at most one
 * of the two marks.
 */
public final class BribeGoal extends Goal {
    /** The most ticks the approach may take before the goal gives up ({@link Jobs#APPROACH_LIMIT}). */
    public static final int APPROACH_LIMIT = Jobs.APPROACH_LIMIT;

    /** Ticks the emerald is held out before the roll. */
    public static final int OFFER_TICKS = 20;

    /**
     * Ticks after giving up an approach during which this vex starts no bribe ({@link Jobs#RETRY_DELAY}; the spec says
     * "it may try again", and this is when).
     */
    public static final int RETRY_DELAY = Jobs.RETRY_DELAY;

    /**
     * Ticks past the longest honest courtship ({@link #APPROACH_LIMIT} plus {@link #OFFER_TICKS}) after which a claim
     * counts as free: a Bound Vex alive in a chunk that stopped ticking would otherwise hold its claim until the chunk
     * ticks or unloads.
     */
    private static final int STALE_SLACK = 20;

    /** The two steps before the roll (the roll itself takes no time). */
    private enum Step {
        APPROACH,
        OFFER
    }

    /**
     * Vexes being courted: the vanilla vex's UUID to the claiming Bound Vex, so two Bound Vexes never court one and
     * {@link #isCourted} can answer. A claim whose Bound Vex is dead or removed, or that is older than
     * {@link Jobs#APPROACH_LIMIT} + {@link #OFFER_TICKS} + {@value #STALE_SLACK} ticks, is free, because a vex that
     * dies, despawns or is unloaded never runs {@code stop()} and one in a chunk that stopped ticking never finishes.
     * Cleared when the server stops.
     */
    private static final Jobs.Claims CLAIMS = new Jobs.Claims(Jobs.APPROACH_LIMIT + OFFER_TICKS + STALE_SLACK);

    /**
     * The dice a GameTest scripted for one Bound Vex (its UUID); transient, never saved, empty in play. Cleared with
     * the claims.
     */
    private static final Map<UUID, ScrollDraw.Dice> SCRIPTED = new HashMap<>();

    private final BoundVex vex;

    @Nullable
    private Vex other;

    private Step step = Step.APPROACH;
    private int stepTicks;
    private int approachTicks;
    private boolean finished;

    /** The game time before which this vex starts no bribe ({@link #RETRY_DELAY}); kept across {@link #stop}. */
    private long blockedUntil;

    BribeGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * Whether {@code vex} holds a live claim of this job.
     *
     * @param vex a Bound Vex
     * @return true while it does
     */
    static boolean holds(BoundVex vex) {
        return CLAIMS.heldBy(vex);
    }

    /** Forgets every claim and every scripted die (single-player loads one server per world in the same JVM). */
    static void clearClaims() {
        CLAIMS.clear();
        SCRIPTED.clear();
    }

    /**
     * Whether a living Bound Vex is courting {@code target} right now: from its claim until its roll (spec §3.1).
     * {@link BoundVex#isPrey} reads it.
     *
     * @param target any entity
     * @return true while a Bound Vex that is alive and in the world holds a claim that is not stale
     */
    public static boolean isCourted(Entity target) {
        return CLAIMS.held(target);
    }

    /**
     * Makes the claim on {@code target} {@code ticks} ticks older. Exists for GameTests (a claim cannot otherwise be
     * made stale without waiting); never called in play.
     *
     * @param target the courted vex
     * @param ticks how much older the claim becomes
     */
    public static void ageClaim(Entity target, long ticks) {
        CLAIMS.age(target, ticks);
    }

    /**
     * Replaces the roll of {@code vex} with {@code dice} until {@link #unscriptDice}. Exists for GameTests: the dice
     * are per Bound Vex, so tests running side by side cannot affect each other. Never called in play.
     *
     * @param vex the Bound Vex whose rolls are scripted
     * @param dice what it rolls
     */
    public static void scriptDice(BoundVex vex, ScrollDraw.Dice dice) {
        SCRIPTED.put(vex.getUUID(), dice);
    }

    /**
     * Gives {@code vex} its own random source again. Exists for GameTests, which call it when they end.
     *
     * @param vex the Bound Vex whose script is dropped
     */
    public static void unscriptDice(BoundVex vex) {
        SCRIPTED.remove(vex.getUUID());
    }

    @Override
    public boolean canUse() {
        // A vex that died, was discarded or unloaded never ran stop(); drop its claim so the map does not keep it
        // (and its level) reachable. Such a claim already counts as free everywhere it is read.
        CLAIMS.prune();
        if (!vex.scanDue(vex.scanInterval()) || vex.level().getGameTime() < blockedUntil) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        other = vex
                .level()
                .getEntitiesOfClass(
                        Vex.class,
                        vex.guardArea(),
                        candidate -> vex.inGuardArea(candidate) && offerable(owner, candidate))
                .stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .orElse(null);
        return other != null;
    }

    /**
     * Whether {@code candidate} may be offered an emerald now: a vanilla vex, alive, unmarked, not courted by another
     * living Bound Vex, and attacking the summoner's side.
     */
    private boolean offerable(ServerPlayer owner, Vex candidate) {
        if (!EntityTypes.VEX.equals(candidate.getType())
                || !candidate.isAlive()
                || AllyAttachments.isBribed(candidate)
                || AllyAttachments.wasOfferedBribe(candidate)
                || CLAIMS.heldByAnother(candidate, vex)) {
            return false;
        }
        LivingEntity theirs = candidate.getTarget();
        return theirs != null && Allies.isAllyOf(owner, theirs);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        if (other == null) {
            return;
        }
        CLAIMS.claim(other, vex);
        step = Step.APPROACH;
        stepTicks = 0;
        approachTicks = 0;
        finished = false;
    }

    /**
     * Continues while the other vex is alive and in the guard area, the summoner is here, the roll has not been made
     * and the approach has not timed out.
     */
    @Override
    public boolean canContinueToUse() {
        return other != null
                && other.isAlive()
                && vex.inGuardArea(other)
                && vex.summoner().isPresent()
                && !finished
                && !(step == Step.APPROACH && approachTicks >= APPROACH_LIMIT);
    }

    @Override
    public void tick() {
        if (other == null || finished) {
            return;
        }
        boolean near = vex.position().distanceTo(other.position()) <= Jobs.REACH;
        if (!near) {
            // The approach flies there; the offer flies back if the other vex drifted off.
            vex.getMoveControl().setWantedPosition(other.getX(), other.getY(), other.getZ(), Jobs.SPEED);
        }
        vex.getLookControl().setLookAt(other);
        if (step == Step.APPROACH) {
            if (!near) {
                approachTicks++;
                return;
            }
            step = Step.OFFER;
            stepTicks = 0;
        }
        offer();
    }

    /** Step 2: the emerald in hand, the villager sound once at the start; after {@link #OFFER_TICKS}, the roll. */
    private void offer() {
        if (!vex.isConjured(EquipmentSlot.MAINHAND) || !vex.getMainHandItem().is(Items.EMERALD)) {
            vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD));
        }
        if (stepTicks == 0) {
            vex.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, 1.0F);
        }
        stepTicks++;
        if (stepTicks >= OFFER_TICKS) {
            roll();
        }
    }

    /**
     * Step 3: one roll of {@link BribeRule}, from the dice scripted for this vex or its own random source. The hand
     * goes and the claim is released at once, so a refused vex is prey again from the next target pick.
     */
    private void roll() {
        Vex target = other;
        if (target == null) {
            return;
        }
        vex.dismiss(EquipmentSlot.MAINHAND);
        release();
        if (!target.isAlive() || AllyAttachments.isBribed(target) || AllyAttachments.wasOfferedBribe(target)) {
            // Gone, or already decided: no roll, no mark. A vanilla vex never carries both marks.
            finished = true;
            return;
        }
        ScrollDraw.Dice dice =
                SCRIPTED.getOrDefault(vex.getUUID(), bound -> vex.getRandom().nextInt(bound));
        if (BribeRule.roll(dice)) {
            target.setData(AllyAttachments.BRIBED, true);
            FleeFromViewGoal.install(target);
        } else {
            target.setData(AllyAttachments.BRIBE_TRIED, true);
        }
        finished = true;
    }

    /** Frees this vex's claim on the other vex; never another vex's. */
    private void release() {
        if (other != null) {
            CLAIMS.release(other, vex);
        }
    }

    /**
     * Ends the goal for any reason: the hand is dismissed and the claim released. No mark is written here; the marks
     * belong to the roll.
     */
    @Override
    public void stop() {
        if (!finished && step == Step.APPROACH && approachTicks >= APPROACH_LIMIT) {
            blockedUntil = vex.level().getGameTime() + RETRY_DELAY;
        }
        vex.dismiss(EquipmentSlot.MAINHAND);
        release();
        other = null;
        step = Step.APPROACH;
        stepTicks = 0;
        approachTicks = 0;
        finished = false;
    }
}
