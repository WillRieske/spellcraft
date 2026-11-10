package io.github.willrieske.spellcraft.evoking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

/**
 * What the job goals ({@link RepairGolemGoal}, {@link CureZombieVillagerGoal} and {@link BribeGoal}) share: the flight
 * constants and the claim map. A job goal makes a Bound Vex do one task on one subject; it claims the subject so two
 * vexes never share one.
 */
final class Jobs {
    /** How near its subject the vex works from (the golem's chest, the zombie villager's head, the other vex). */
    static final double REACH = 1.5;

    /** The move speed the vex is ordered to fly at. */
    static final double SPEED = 1.0;

    /** The most ticks an approach may take before the goal gives up. */
    static final int APPROACH_LIMIT = 100;

    /**
     * Ticks after giving up an approach during which a vex starts no new job of that kind. The abort and the next scan
     * can fall on the same goal-selector pass, so without it the vex would claim the same unreachable subject again at
     * once and it would never be released.
     */
    static final int RETRY_DELAY = 200;

    private Jobs() {}

    /**
     * A claim map: subject UUID to the Bound Vex holding the claim and the game time it was made. A claim counts only
     * while its holder is alive (a vex that dies, despawns or is unloaded never runs its goal's {@code stop}) and, when
     * the map has an age limit, while it is not older than that (a vex alive in a chunk that stopped ticking never
     * finishes). Not thread-safe: the server thread only.
     */
    static final class Claims {
        private record Claim(BoundVex holder, long time) {}

        private final Map<UUID, Claim> claims = new HashMap<>();
        private final int maxAgeTicks;

        /** A map whose claims never go stale. */
        Claims() {
            this(Integer.MAX_VALUE);
        }

        /**
         * A map whose claims go stale.
         *
         * @param maxAgeTicks a claim older than this many ticks counts as free
         */
        Claims(int maxAgeTicks) {
            this.maxAgeTicks = maxAgeTicks;
        }

        /**
         * Records {@code holder}'s claim on {@code subject}, made now (replacing any other claim on it).
         *
         * @param subject what is claimed
         * @param holder who claims it
         */
        void claim(Entity subject, BoundVex holder) {
            claims.put(subject.getUUID(), new Claim(holder, subject.level().getGameTime()));
        }

        /**
         * Whether anyone holds a live claim on {@code subject}.
         *
         * @param subject any entity
         * @return true while its holder lives and the claim is not stale
         */
        boolean held(Entity subject) {
            Claim claim = claims.get(subject.getUUID());
            return claim != null && live(claim, subject.level().getGameTime());
        }

        /**
         * Whether a live Bound Vex other than {@code asker} holds a live claim on {@code subject}.
         *
         * @param subject any entity
         * @param asker the vex that wants it
         * @return true if another vex has it
         */
        boolean heldByAnother(Entity subject, BoundVex asker) {
            Claim claim = claims.get(subject.getUUID());
            return claim != null
                    && live(claim, subject.level().getGameTime())
                    && !claim.holder().getUUID().equals(asker.getUUID());
        }

        /**
         * Whether {@code holder} itself holds a live claim on {@code subject}.
         *
         * @param subject any entity
         * @param holder the vex asking
         * @return true if the live claim on it is {@code holder}'s
         */
        boolean heldBy(Entity subject, BoundVex holder) {
            Claim claim = claims.get(subject.getUUID());
            return claim != null
                    && live(claim, subject.level().getGameTime())
                    && claim.holder().getUUID().equals(holder.getUUID());
        }

        /**
         * Frees {@code holder}'s claim on {@code subject}; never another vex's.
         *
         * @param subject what was claimed
         * @param holder the vex releasing it
         */
        void release(Entity subject, BoundVex holder) {
            UUID self = holder.getUUID();
            claims.computeIfPresent(
                    subject.getUUID(), (id, claim) -> claim.holder().getUUID().equals(self) ? null : claim);
        }

        /** Drops the claims that no longer count, so the map does not keep a dead vex (and its level) reachable. */
        void prune() {
            claims.values()
                    .removeIf(claim -> !live(claim, claim.holder().level().getGameTime()));
        }

        /** Forgets every claim (single-player loads one server per world in the same JVM). */
        void clear() {
            claims.clear();
        }

        /**
         * Makes the claim on {@code subject} {@code ticks} ticks older; for GameTests, never called in play.
         *
         * @param subject what is claimed
         * @param ticks how much older the claim becomes
         */
        void age(Entity subject, long ticks) {
            claims.computeIfPresent(subject.getUUID(), (id, claim) -> new Claim(claim.holder(), claim.time() - ticks));
        }

        private boolean live(Claim claim, long now) {
            return claim.holder().isAlive() && now - claim.time() <= maxAgeTicks;
        }
    }
}
