package io.github.willrieske.spellcraft.evoking;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.util.RandomSource;

/**
 * The scriptable dice of the team moves (Parts 6-7 spec §5, "Dice"): the same shape as the bribe's per-vex dice,
 * but global, because the vexes a roll is for are chosen at the start. In play every roll is the random source's; a
 * GameTest scripts rolls with {@link #script} and must {@link #unscript} (a test that scripts one die runs in an
 * environment of its own, since the script is shared). {@link #unscriptAll} names every instance, and
 * {@code TeamMoves.clear()} (server stop) calls it, so a leaked script cannot outlive a world. A new die must be added
 * to {@link #unscriptAll}; the GameTest {@code dice_unscript_all_clears_every_die} fails when one is forgotten.
 *
 * <p>Not {@code ScrollDraw.Dice}: that one is a per-vex roll function; this one is a global scriptable source.
 */
public final class Dice {
    /** The Raid kit's rolls: per member, the main-hand item (0-6) then the armor material (0-1). */
    public static final Dice RAID_KIT = new Dice();

    /** The Caster roll at each summon: a roll of 0 in {@code [0, 3)} makes a Caster (1 in 3). */
    public static final Dice CASTER_ROLL = new Dice();

    /** Scroll mobs: per spawning mob the "carries one" roll, then (for a carrier) the spell: 0 Mirror, 1 Ghostrail. */
    public static final Dice SCROLL_MOB = new Dice();

    /** The Mirror confusion roll, {@code [0, MirrorRule.CONFUSION_BOUND)}. */
    public static final Dice MIRROR_CONFUSION = new Dice();

    /** The rolls queued by {@link #script}, returned in order before any random one. */
    private final Deque<Integer> scripted = new ArrayDeque<>();

    private Dice() {}

    /**
     * The next roll: the scripted one while any is left, else the random source's.
     *
     * @param random the source for an unscripted roll
     * @param bound the exclusive upper bound
     * @return a value in {@code [0, bound)}
     * @throws IllegalStateException if a scripted roll is outside the range
     */
    public int below(RandomSource random, int bound) {
        Integer next = scripted.poll();
        if (next == null) {
            return random.nextInt(bound);
        }
        if (next < 0 || next >= bound) {
            throw new IllegalStateException("scripted roll " + next + " is outside [0, " + bound + ")");
        }
        return next;
    }

    /**
     * Queues rolls to be returned in order before any random one.
     *
     * @param rolls the rolls
     */
    public void script(int... rolls) {
        for (int roll : rolls) {
            scripted.add(roll);
        }
    }

    /** Drops every queued roll. */
    public void unscript() {
        scripted.clear();
    }

    /** Drops every queued roll of every instance. */
    public static void unscriptAll() {
        RAID_KIT.unscript();
        CASTER_ROLL.unscript();
        SCROLL_MOB.unscript();
        MIRROR_CONFUSION.unscript();
    }
}
