package io.github.willrieske.spellcraft.spell.rules;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * One player's casting timers (spec §4): whether a spell may start now, when the pending perform or the no-targets
 * notice is due, and whether the arms are up. Times are game-time ticks supplied by the caller. Callers ask
 * {@link #readiness} before {@link #start} or {@link #noTargets}.
 */
public final class EvokingCast {
    /** Whether a spell may start now. */
    public enum Readiness {
        /** The spell may start now. */
        READY,
        /** Another cast's casting ticks are still running. */
        BUSY,
        /** This spell's interval has not passed. */
        COOLING
    }

    /**
     * Something the caster must do now: perform the pending spell, or tell the player no targets were found.
     *
     * @param kind what to do
     * @param spell the spell to perform; {@link EvokerSpell#FANGS} for the notice (only fangs look for targets)
     */
    public record Due(Kind kind, EvokerSpell spell) {
        /** Whether to run the pending perform or only tell the player. */
        public enum Kind {
            /** Run the pending spell. */
            PERFORM,
            /** Tell the player no targets were found. */
            NOTIFY_NO_TARGETS
        }
    }

    private static final long NEVER = Long.MIN_VALUE;

    private long castingUntil = NEVER;
    private final Map<EvokerSpell, Long> readyAt = new EnumMap<>(EvokerSpell.class);
    private Optional<EvokerSpell> pending = Optional.empty();
    private long performAt = NEVER;
    private boolean noticePending;
    private long noticeAt = NEVER;

    /**
     * Whether {@code spell} may start at {@code now}: {@code BUSY} while any cast's casting ticks run, else
     * {@code COOLING} while this spell's interval runs, else {@code READY}.
     */
    public Readiness readiness(EvokerSpell spell, long now) {
        if (now < castingUntil) {
            return Readiness.BUSY;
        }
        if (now < readyAt.getOrDefault(spell, NEVER)) {
            return Readiness.COOLING;
        }
        return Readiness.READY;
    }

    /**
     * Starts {@code spell} at {@code now}: the arms go up for its casting ticks, the perform is due after the warmup,
     * the spell's interval starts, and any scheduled notice is dropped.
     *
     * @throws IllegalStateException if {@link #readiness} is not {@code READY}
     */
    public void start(EvokerSpell spell, long now) {
        Readiness readiness = readiness(spell, now);
        if (readiness != Readiness.READY) {
            throw new IllegalStateException("cannot start " + spell + " at " + now + ": " + readiness);
        }
        castingUntil = now + spell.castingTicks();
        performAt = now + EvokerSpell.WARMUP_TICKS;
        pending = Optional.of(spell);
        readyAt.put(spell, now + spell.intervalTicks());
        noticePending = false;
    }

    /** Schedules the no-targets notice for one warmup after {@code now}; a later call moves it. */
    public void noTargets(long now) {
        noticePending = true;
        noticeAt = now + EvokerSpell.WARMUP_TICKS;
    }

    /**
     * What is due at {@code now}, each returned once: the pending perform once its warmup has passed, else the
     * notice once its delay has passed.
     */
    public Optional<Due> due(long now) {
        if (pending.isPresent() && now >= performAt) {
            EvokerSpell spell = pending.get();
            pending = Optional.empty();
            return Optional.of(new Due(Due.Kind.PERFORM, spell));
        }
        if (noticePending && now >= noticeAt) {
            noticePending = false;
            return Optional.of(new Due(Due.Kind.NOTIFY_NO_TARGETS, EvokerSpell.FANGS));
        }
        return Optional.empty();
    }

    /**
     * Whether the arms are up at {@code now}: the pure model of the casting-effect window (the GameTests observe it
     * through the synced effect).
     */
    public boolean isCasting(long now) {
        return now < castingUntil;
    }
}
