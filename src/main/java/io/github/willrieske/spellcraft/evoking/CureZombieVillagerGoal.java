package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex cures a zombie villager (Evoking II Parts 4-5 spec §2.2): on each scan ({@code BoundVex.scanDue}), when
 * the vex's summoner is here, the nearest zombie villager whose feet are in the guard area and that can be taken is
 * taken. It can be taken if it is alive, not converting, not already {@code NoAI} (a map-made statue is left alone),
 * carries no cure mark (the mark is what keeps a second vex off a subject: a claim counts only while the mark is
 * there). It must also meet one more condition: either the vex has no target, or the zombie villager's own target is
 * the summoner or an ally (an attacker is stopped even mid-fight). The vex claims it, freezes it ({@code setNoAi(true)}) and gives it the cure mark
 * ({@link AllyAttachments#CURE}). The vex then flies to within {@link Jobs#REACH} of the zombie villager's head (at
 * most {@link Jobs#APPROACH_LIMIT} ticks), feeds it a golden apple for {@link #APPLE_TICKS} ticks (the eat sound and
 * crumbs), turns its head away and throws the cure potion at its feet for {@link #POTION_TICKS} ticks, and then begins
 * vanilla's conversion for {@link #CONVERSION_TICKS} ticks in the summoner's name, so the cured villager gives the
 * summoner vanilla's discount. The smoke, the freeze and its release belong to {@link CureEvents}, because the
 * conversion outlives this goal.
 *
 * <p>A new target for the vex does not end the goal ({@code canContinueToUse} does not look at it): a half-cured zombie
 * villager left standing is worse than one vex out of the fight for a few seconds. The goal is interruptable, though:
 * {@link FleeCreeperGoal}, {@link DodgeGolemGoal} and Part 5's rescue outrank it on the move flag, and {@link #stop}
 * before step 4 is then the abort (the zombie villager is unfrozen and unmarked, the hand restored). An approach that
 * times out also keeps this vex from starting another cure for {@link #RETRY_DELAY} ticks (a departure from the spec:
 * see that constant). The apple and the potion are conjured ({@code BoundVex.conjure}), so nothing drops if the vex
 * dies. The vanilla facts (the private {@code startConverting}, opened by the access transformer; the potion stack;
 * the particle options) are docs/api-notes.md §23.
 */
public final class CureZombieVillagerGoal extends Goal {
    /** The most ticks the approach may take before the goal gives up ({@link Jobs#APPROACH_LIMIT}). */
    public static final int APPROACH_LIMIT = Jobs.APPROACH_LIMIT;

    /** Ticks of the apple step. */
    public static final int APPLE_TICKS = 30;

    /** Ticks of the potion step. */
    public static final int POTION_TICKS = 20;

    /** The tick of the potion step on which the potion is thrown. */
    public static final int THROW_TICK = 10;

    /** Ticks vanilla's conversion takes once begun (20 seconds). */
    public static final int CONVERSION_TICKS = 400;

    /** Ticks after giving up an approach during which this vex starts no cure ({@link Jobs#RETRY_DELAY}). */
    public static final int RETRY_DELAY = Jobs.RETRY_DELAY;

    /** Ticks between the eat sound and crumbs in the apple step. */
    private static final int CRUMB_INTERVAL = 4;

    /** Crumb particles sent at the zombie villager's mouth each time. */
    private static final int CRUMBS = 5;

    /** How far the vex turns its head away from the zombie villager before the throw, in degrees. */
    private static final double TURN_DEGREES = 60.0;

    /** The speed the potion leaves the vex's hand at. */
    private static final float POTION_SPEED = 0.45F;

    /** How far in front of its face the crumbs appear. */
    private static final double MOUTH_REACH = 0.3;

    /** The mouth's height as a fraction of the zombie villager's eye height. */
    private static final double MOUTH_HEIGHT = 0.8;

    /**
     * How far from a claimed zombie villager an iron golem is told to stop fighting it: a golem that had it as a target
     * before the mark would otherwise keep hitting it ({@code GolemMercy} cancels only a new target).
     */
    private static final double GOLEM_CALM_RADIUS = 24.0;

    /** The first three steps of spec §2.2; step 4 is {@link #begin}. */
    private enum Step {
        APPROACH,
        APPLE,
        POTION
    }

    /**
     * Zombie villagers under a cure: zombie villager UUID to the claiming vex, so two vexes never share one (spec §4).
     * A claim whose vex is dead or removed is free, because a vex that dies, despawns or is unloaded never runs
     * {@code stop()}. So is a claim whose zombie villager no longer carries the cure mark (the 300-tick rule in
     * {@link CureEvents} released it): a vex that stops ticking mid-job holds nothing, and {@link #canContinueToUse}
     * drops its job when it wakes. Cleared when the server stops. Like {@code RepairGolemGoal}'s map, the prune in
     * {@code canUse} only frees memory.
     */
    private static final Jobs.Claims CLAIMS = new Jobs.Claims();

    private final BoundVex vex;

    @Nullable
    private ZombieVillager subject;

    private Step step = Step.APPROACH;
    private int stepTicks;
    private int approachTicks;
    private boolean finished;

    /** The game time before which this vex starts no cure ({@link #RETRY_DELAY}); kept across {@link #stop}. */
    private long blockedUntil;

    CureZombieVillagerGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** Forgets every claim (single-player loads one server per world in the same JVM). */
    static void clearClaims() {
        CLAIMS.clear();
    }

    /**
     * Whether a live Bound Vex holds a claim on {@code subject} right now: its holder lives and the zombie villager
     * still carries the cure mark. Exists for the GameTests, which read the claim map through it.
     *
     * @param subject any entity
     * @return true while the claim counts
     */
    public static boolean isClaimed(Entity subject) {
        return AllyAttachments.isBeingCured(subject) && CLAIMS.held(subject);
    }

    /**
     * The cure potion (spec §2.2): a splash potion with no effects and weakness's color, so the throw, the glass break
     * and the gray splash look like a weakness potion while nobody standing near is weakened (vanilla's
     * {@code startConverting} removes the zombie villager's weakness anyway). Stacks have no value {@code equals}:
     * compare with {@code ItemStack.matches}.
     *
     * @return a new stack
     */
    public static ItemStack curePotion() {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(
                DataComponents.POTION_CONTENTS,
                new PotionContents(
                        Optional.empty(),
                        Optional.of(MobEffects.WEAKNESS.value().getColor()),
                        List.of(),
                        Optional.empty()));
        return stack;
    }

    @Override
    public boolean canUse() {
        // A vex that died, was discarded or unloaded never ran stop(); drop its claim so the map does not keep it
        // (and its level) reachable. Such a claim already counts as free in Jobs.Claims.heldByAnother.
        CLAIMS.prune();
        if (!vex.scanDue(BoundVex.SCAN_INTERVAL) || vex.level().getGameTime() < blockedUntil) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        subject = vex
                .level()
                .getEntitiesOfClass(
                        ZombieVillager.class,
                        vex.guardArea(),
                        candidate -> vex.inGuardArea(candidate) && curable(owner, candidate))
                .stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .orElse(null);
        return subject != null;
    }

    /**
     * Whether {@code candidate} may be taken now: alive, not converting, not a statue, not marked (the mark is what
     * keeps a second vex off a subject), and either this vex has no target or the candidate is attacking the summoner's
     * side.
     */
    private boolean curable(ServerPlayer owner, ZombieVillager candidate) {
        if (!candidate.isAlive()
                || candidate.isConverting()
                || candidate.isNoAi()
                || AllyAttachments.isBeingCured(candidate)) {
            return false;
        }
        if (vex.getTarget() == null) {
            return true;
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
        if (subject == null) {
            return;
        }
        subject.setNoAi(true);
        subject.setData(AllyAttachments.CURE, subject.level().getGameTime());
        CLAIMS.claim(subject, vex);
        calmGolems(subject);
        step = Step.APPROACH;
        stepTicks = 0;
        approachTicks = 0;
        finished = false;
    }

    /**
     * Ends every iron golem's fight with {@code claimed} within {@link #GOLEM_CALM_RADIUS} blocks: its target is
     * cleared. The mark is already set, so {@code GolemMercy} cancels the golem's target goals picking it again (a
     * null target is never canceled).
     */
    private static void calmGolems(ZombieVillager claimed) {
        UUID id = claimed.getUUID();
        for (IronGolem golem : claimed.level()
                .getEntitiesOfClass(IronGolem.class, claimed.getBoundingBox().inflate(GOLEM_CALM_RADIUS), candidate -> {
                    LivingEntity target = candidate.getTarget();
                    return target != null && target.getUUID().equals(id);
                })) {
            golem.setTarget(null);
        }
    }

    /**
     * Continues while the subject is alive, still carries the cure mark and no other vex has claimed it, the summoner
     * is here, the steps are not finished and the approach has not timed out. Deliberately not the vex's target (spec
     * §2.2). A subject that lost its mark was released by {@link CureEvents} (and another vex may have claimed it
     * since): a vex that wakes after that drops the job.
     */
    @Override
    public boolean canContinueToUse() {
        return holdsSubject()
                && vex.summoner().isPresent()
                && !finished
                && !(step == Step.APPROACH && approachTicks >= APPROACH_LIMIT);
    }

    /** Whether the subject is still alive, still carries the cure mark and no other vex holds the claim. */
    private boolean holdsSubject() {
        return subject != null
                && subject.isAlive()
                && AllyAttachments.isBeingCured(subject)
                && !CLAIMS.heldByAnother(subject, vex);
    }

    @Override
    public void tick() {
        if (subject == null || finished) {
            return;
        }
        if (!holdsSubject()) {
            // Vanilla runs a goal's tick on odd ticks without asking canContinueToUse first, so a vex that stalled
            // and wakes after the release (or after another vex took over) gets here before the check drops the job.
            finished = true;
            return;
        }
        Vec3 head = new Vec3(subject.getX(), subject.getEyeY(), subject.getZ());
        boolean near = vex.position().distanceTo(head) <= Jobs.REACH;
        if (near) {
            // Vanilla's vex move control flies to within about half a block of its wanted point, so a vex left
            // flying would end inside the head (the head turn would degenerate and the throw go straight down):
            // stop it where it came within reach, and the apple and potion steps hold that distance.
            vex.getMoveControl().setWait();
            vex.setDeltaMovement(Vec3.ZERO);
        } else {
            // Step 1 flies there; the later steps fly back if the vex drifted off.
            vex.getMoveControl().setWantedPosition(head.x, head.y, head.z, Jobs.SPEED);
        }
        vex.getLookControl().setLookAt(head.x, head.y, head.z);
        if (step == Step.APPROACH) {
            if (!near) {
                approachTicks++;
                return;
            }
            step = Step.APPLE;
            stepTicks = 0;
        }
        if (step == Step.APPLE) {
            apple();
        } else {
            potion();
        }
    }

    /** Step 2: the golden apple in hand; every few ticks the eat sound and the crumbs at the mouth. */
    private void apple() {
        if (!vex.isConjured(EquipmentSlot.MAINHAND) || !vex.getMainHandItem().is(Items.GOLDEN_APPLE)) {
            vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_APPLE));
        }
        if (stepTicks % CRUMB_INTERVAL == 0 && subject != null && subject.level() instanceof ServerLevel level) {
            level.playSound(null, subject, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F, 1.0F);
            Vec3 forward = Vec3.directionFromRotation(0.0F, subject.getYHeadRot());
            level.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, Items.GOLDEN_APPLE),
                    subject.getX() + forward.x * MOUTH_REACH,
                    subject.getY() + subject.getEyeHeight() * MOUTH_HEIGHT,
                    subject.getZ() + forward.z * MOUTH_REACH,
                    CRUMBS,
                    0.1,
                    0.1,
                    0.1,
                    0.05);
        }
        stepTicks++;
        if (stepTicks >= APPLE_TICKS) {
            step = Step.POTION;
            stepTicks = 0;
        }
    }

    /**
     * Steps 3 and 4: the cure potion in hand, the head turned away for the first {@link #THROW_TICK} ticks, the throw
     * at the zombie villager's feet on the tenth, and at the end of {@link #POTION_TICKS} the conversion begins.
     */
    private void potion() {
        if (subject == null) {
            return;
        }
        if (!vex.isConjured(EquipmentSlot.MAINHAND) || !vex.getMainHandItem().is(Items.SPLASH_POTION)) {
            vex.conjure(EquipmentSlot.MAINHAND, curePotion());
        }
        if (stepTicks < THROW_TICK) {
            Vec3 aside = turned(subject.position().subtract(vex.position()));
            Vec3 eyes = vex.getEyePosition();
            vex.getLookControl().setLookAt(eyes.x + aside.x, eyes.y, eyes.z + aside.z);
        } else if (stepTicks == THROW_TICK && vex.level() instanceof ServerLevel level) {
            throwPotion(level, subject);
        }
        stepTicks++;
        if (stepTicks >= POTION_TICKS) {
            begin(subject);
        }
    }

    /** The horizontal part of {@code line} turned {@link #TURN_DEGREES} about the vertical. */
    private static Vec3 turned(Vec3 line) {
        double radians = Math.toRadians(TURN_DEGREES);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(line.x * cos - line.z * sin, 0.0, line.z * cos + line.x * sin);
    }

    /** Throws the cure potion at {@code at}'s feet with the spawn helper docs/api-notes.md §22 records. */
    private void throwPotion(ServerLevel level, ZombieVillager at) {
        ItemStack potion = curePotion();
        ThrownSplashPotion thrown = new ThrownSplashPotion(level, vex, potion);
        double dx = at.getX() - vex.getX();
        double dy = at.getY() - thrown.getY();
        double dz = at.getZ() - vex.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        Projectile.spawnProjectileUsingShoot(thrown, level, potion, dx, dy + flat * 0.2, dz, POTION_SPEED, 0.0F);
        vex.playSound(SoundEvents.SPLASH_POTION_THROW, 1.0F, 1.0F);
    }

    /**
     * Step 4, reached only while {@link #tick} found the vex still holding its subject: unless a player fed the
     * zombie villager by hand meanwhile (vanilla's conversion and that player's discount are left as they are), begin
     * the conversion in the summoner's name; then the hand goes and the goal ends.
     */
    private void begin(ZombieVillager target) {
        if (!target.isConverting()) {
            vex.summonerId().ifPresent(owner -> target.startConverting(owner, CONVERSION_TICKS));
        }
        finished = true;
        vex.dismiss(EquipmentSlot.MAINHAND);
    }

    /**
     * Ends the goal. Before step 4 this is the abort: the hand is dismissed, the claim released, and a zombie villager
     * that still exists and is not converting is unfrozen and loses its cure mark. After step 4 the mark and the freeze
     * stay for {@link CureEvents} to end with the conversion.
     */
    @Override
    public void stop() {
        if (!finished && step == Step.APPROACH && approachTicks >= APPROACH_LIMIT) {
            blockedUntil = vex.level().getGameTime() + RETRY_DELAY;
        }
        vex.dismiss(EquipmentSlot.MAINHAND);
        if (subject != null) {
            // A subject that lost its mark was released by CureEvents, and one claimed since is another vex's job:
            // either way nothing here is this vex's to undo.
            boolean mine = AllyAttachments.isBeingCured(subject) && !CLAIMS.heldByAnother(subject, vex);
            CLAIMS.release(subject, vex);
            if (mine && !finished && !subject.isRemoved() && !subject.isConverting()) {
                subject.setNoAi(false);
                subject.removeData(AllyAttachments.CURE);
            }
        }
        subject = null;
        step = Step.APPROACH;
        stepTicks = 0;
        approachTicks = 0;
        finished = false;
    }
}
