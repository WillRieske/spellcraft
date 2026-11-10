package io.github.willrieske.spellcraft.evoking;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex breaks the end crystals of a living dragon's fight (Parts 4–5 spec §7.1): on each scan
 * ({@code BoundVex.scanDue}), in the End while the dragon lives, when the vex has no target and its summoner is here,
 * the nearest end crystal that is alive, breakable and not claimed by another living Bound Vex is taken: within
 * {@link #RANGE} blocks of a following vex, inside the stay area of a staying one. The vex flies to the point one
 * block above the crystal and, within {@link #STRIKE} blocks of it, hits it once; the crystal explodes (the blast's
 * damage and push are held back by {@link BoundVexEvents}). A vex that cannot get there gives the crystal up after
 * {@link #LIMIT} ticks and takes none for {@link Jobs#RETRY_DELAY} ticks, so another vex can have it. The goal ends
 * when the crystal is gone, the vex gets a target, its summoner leaves or the dragon dies.
 */
public final class AttackCrystalGoal extends Goal {
    /** How far a following vex looks for a crystal. */
    static final double RANGE = 32.0;

    /** How near the point above the crystal the vex strikes from. */
    static final double STRIKE = 2.0;

    /** A vex gives a crystal up after this long. */
    static final int LIMIT = 200;

    /** How far above the crystal's feet the vex flies to. */
    private static final double ABOVE = 1.0;

    /**
     * The crystals being attacked: crystal UUID to the claiming vex, so two vexes never share one. A claim whose vex is
     * dead or removed is free, because such a vex never runs {@code stop()}. Cleared when the server stops.
     */
    private static final Jobs.Claims CLAIMS = new Jobs.Claims();

    /** Scripted answers to "does the dragon live", per vex (its UUID), for GameTests; empty in play. */
    private static final Map<UUID, Boolean> SCRIPTED_DRAGON = new HashMap<>();

    private final BoundVex vex;

    @Nullable
    private EndCrystal crystal;

    private int ticks;
    private boolean finished;

    /** The game time before which this vex takes no crystal ({@link Jobs#RETRY_DELAY}); kept across {@link #stop}. */
    private long blockedUntil;

    AttackCrystalGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Forgets every claim and every scripted answer (single-player loads one server per world in the same JVM). */
    static void clearClaims() {
        CLAIMS.clear();
        SCRIPTED_DRAGON.clear();
    }

    /**
     * Whether a Bound Vex holds a live claim on {@code crystal}. Public so GameTests can show which crystal a vex
     * went for.
     *
     * @param crystal the crystal
     * @return true while a living vex has it
     */
    public static boolean isClaimed(EndCrystal crystal) {
        return CLAIMS.held(crystal);
    }

    /**
     * Whether the level's dragon fight has a living dragon: crystals are fought only then, so the ones a player places
     * to bring the dragon back are left alone.
     *
     * @param level the level
     * @return true in the End while its dragon lives
     */
    public static boolean livingDragon(ServerLevel level) {
        EnderDragonFight fight = level.getDragonFight();
        if (fight == null) {
            return false;
        }
        UUID dragon = fight.dragonUUID();
        return dragon != null && level.getEntity(dragon) instanceof LivingEntity living && living.isAlive();
    }

    /**
     * Replaces the answer to "does the dragon live" for {@code vex} until {@link #unscriptDragon}. Exists for
     * GameTests: the test End has no dragon to kill or keep.
     *
     * @param vex the vex
     * @param alive the answer
     */
    public static void scriptDragon(BoundVex vex, boolean alive) {
        SCRIPTED_DRAGON.put(vex.getUUID(), alive);
    }

    /**
     * Removes the scripted answer of {@code vex}.
     *
     * @param vex the vex
     */
    public static void unscriptDragon(BoundVex vex) {
        SCRIPTED_DRAGON.remove(vex.getUUID());
    }

    /** The answer to "does the dragon live" for this vex: the scripted one if a test set it, else the real one. */
    private boolean dragonLives(ServerLevel level) {
        Boolean scripted = SCRIPTED_DRAGON.get(vex.getUUID());
        return scripted != null ? scripted : livingDragon(level);
    }

    @Override
    public boolean canUse() {
        if (!vex.scanDue(vex.scanInterval())
                || vex.level().getGameTime() < blockedUntil
                || vex.getTarget() != null
                || !(vex.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.END)
                || vex.summoner().isEmpty()
                || !dragonLives(level)) {
            return false;
        }
        // A vex that died, was discarded or unloaded never ran stop(); drop its claim so the map does not keep it
        // (and its level) reachable. Such a claim already counts as free in Jobs.Claims.heldByAnother.
        CLAIMS.prune();
        Predicate<EndCrystal> free = candidate ->
                candidate.isAlive() && !candidate.isInvulnerable() && !CLAIMS.heldByAnother(candidate, vex);
        List<EndCrystal> crystals = vex.isStaying()
                ? level.getEntitiesOfClass(
                        EndCrystal.class,
                        vex.guardArea(),
                        candidate -> vex.inGuardArea(candidate) && free.test(candidate))
                : vex.nearby(EndCrystal.class, RANGE, free);
        crystal = crystals.stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .orElse(null);
        return crystal != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        if (crystal == null) {
            return;
        }
        CLAIMS.claim(crystal, vex);
        ticks = 0;
        finished = false;
    }

    @Override
    public boolean canContinueToUse() {
        return !finished
                && crystal != null
                && crystal.isAlive()
                && vex.getTarget() == null
                && vex.summoner().isPresent()
                && vex.level() instanceof ServerLevel level
                && dragonLives(level)
                && ticks < LIMIT;
    }

    /** Flies to the point above the crystal; there, hits it once. */
    @Override
    public void tick() {
        if (crystal == null || finished || !(vex.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        Vec3 above = crystal.position().add(0.0, ABOVE, 0.0);
        if (vex.position().distanceTo(above) > STRIKE) {
            vex.getMoveControl().setWantedPosition(above.x, above.y, above.z, Jobs.SPEED);
            return;
        }
        finished = true; // tick can run once after the end condition (docs/api-notes.md §21)
        crystal.hurtServer(level, vex.damageSources().mobAttack(vex), 1.0F);
    }

    /** Ends the goal for any reason: the claim is released, and a give-up on the limit blocks the next crystal. */
    @Override
    public void stop() {
        if (crystal != null) {
            CLAIMS.release(crystal, vex);
        }
        if (!finished && ticks >= LIMIT) {
            blockedUntil = vex.level().getGameTime() + Jobs.RETRY_DELAY;
        }
        crystal = null;
        ticks = 0;
        finished = false;
    }
}
