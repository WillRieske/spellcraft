package io.github.willrieske.spellcraft.evoking;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * The scriptable dice of the team moves (Parts 6-7 spec §5, "Dice"): the same shape as the bribe's per-vex dice,
 * but global, because the vexes a roll is for are chosen at the start. In play every roll is the random source's; a
 * GameTest scripts rolls with {@link #script} and must {@link #unscript} (a test that scripts one die runs in an
 * environment of its own, since the script is shared).
 *
 * <p>Exact scripts: while a die has an active script (scripted and not yet unscripted) running out of rolls throws
 * {@link IllegalStateException} naming the die, and {@link #unscript}/{@link #unscriptAll} throw when rolls were left
 * unconsumed, so a test scripts exactly what it consumes. Put the check in the {@code finally} right after the
 * consuming call; {@link #reset}/{@link #resetAll} clear silently and are for {@code whenDone} cleanups (which must
 * not mask the original failure) and server stop. A die that was never scripted behaves as in play.
 * {@code TeamMoves.clear()} (server stop) resets every die. A new die must be added to the {@code ALL} list; the
 * GameTest {@code dice_unscript_all_clears_every_die} fails when one is forgotten.
 *
 * <p>Not {@code ScrollDraw.Dice}: that one is a per-vex roll function; this one is a global scriptable source.
 *
 * <p>Functional spec §11.11, §12.1, §13.4, §15.1.
 */
public final class Dice {
    /**
     * The Raid kit's rolls: per member, the main-hand item (0-6) then the armor material (0-1). Functional spec §11.11.
     */
    public static final Dice RAID_KIT = new Dice("RAID_KIT");

    /**
     * The Caster roll at each summon: a roll of 0 in {@code [0, 3)} makes a Caster (1 in 3). Functional spec §12.1.
     */
    public static final Dice CASTER_ROLL = new Dice("CASTER_ROLL");

    /**
     * Scroll mobs: per spawning mob the "carries one" roll, then (for a carrier) the spell: 0 Mirror, 1 Ghostrail.
     * Functional spec §15.1.
     */
    public static final Dice SCROLL_MOB = new Dice("SCROLL_MOB");

    /** The Mirror confusion roll, {@code [0, MirrorRule.CONFUSION_BOUND)}. Functional spec §13.4. */
    public static final Dice MIRROR_CONFUSION = new Dice("MIRROR_CONFUSION");

    /** Every instance. A new die must be added here. */
    private static final List<Dice> ALL = List.of(RAID_KIT, CASTER_ROLL, SCROLL_MOB, MIRROR_CONFUSION);

    /** The name in messages. */
    private final String name;

    /** The rolls queued by {@link #script}, returned in order. */
    private final Deque<Integer> scripted = new ArrayDeque<>();

    /** Whether {@link #script} has been called since the last {@link #unscript} or {@link #reset}. */
    private boolean active;

    private Dice(String name) {
        this.name = name;
    }

    /**
     * The next roll: while the die has an active script, the scripted one (running out is a test bug and throws);
     * a die that was never scripted returns the random source's.
     *
     * @param random the source for an unscripted roll
     * @param bound the exclusive upper bound
     * @return a value in {@code [0, bound)}
     * @throws IllegalStateException if a scripted roll is outside the range, or the active script ran out
     */
    public int below(RandomSource random, int bound) {
        if (!active) {
            return random.nextInt(bound);
        }
        Integer next = scripted.poll();
        if (next == null) {
            throw new IllegalStateException("the script of " + name + " ran out: the test scripted too few rolls");
        }
        if (next < 0 || next >= bound) {
            throw new IllegalStateException("scripted roll " + next + " is outside [0, " + bound + ")");
        }
        return next;
    }

    /**
     * Queues rolls to be returned in order; from the first call until {@link #unscript} (or {@link #reset}) the die
     * never falls back to random.
     *
     * @param rolls the rolls
     */
    public void script(int... rolls) {
        active = true;
        for (int roll : rolls) {
            scripted.add(roll);
        }
    }

    /**
     * Ends the script and checks that every scripted roll was consumed.
     *
     * @throws IllegalStateException naming the die and the leftover count, if rolls were left unconsumed (the die is
     *     cleared first either way)
     */
    public void unscript() {
        int left = scripted.size();
        reset();
        if (left > 0) {
            throw new IllegalStateException(name + " has " + left + " scripted rolls left unconsumed");
        }
    }

    /** Ends the script and drops every queued roll silently; for cleanups, which must not mask a failure. */
    public void reset() {
        scripted.clear();
        active = false;
    }

    /**
     * Ends the script of every instance, then fails if any had rolls left (it clears all before it throws).
     *
     * @throws IllegalStateException naming the first die with leftover rolls and the count
     */
    public static void unscriptAll() {
        IllegalStateException first = null;
        for (Dice die : ALL) {
            try {
                die.unscript();
            } catch (IllegalStateException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /** Silently ends the script of every instance; the form for {@code whenDone} cleanups and server stop. */
    public static void resetAll() {
        for (Dice die : ALL) {
            die.reset();
        }
    }
}
