package io.github.willrieske.spellcraft.spell.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One player's trail as a fuse: flames oldest first, and a countdown until the oldest fades. Once the countdown
 * runs out the oldest flame is removed and the countdown restarts for the next one, so a trail shrinks from its far
 * end at one flame per {@link #FADE_TICKS} (or {@link #LINGER_TICKS} for a lingering flame).
 */
public final class Trail {
    /** Ticks a plain flame burns once it is the oldest. */
    public static final int FADE_TICKS = 20;
    /** Ticks a lingering flame (over soul sand or soul soil) burns once it is the oldest. */
    public static final int LINGER_TICKS = 40;

    /**
     * One flame.
     *
     * @param pos the block position, packed as by {@code BlockPos.asLong()}
     * @param lingers whether the flame takes {@link #LINGER_TICKS} rather than {@link #FADE_TICKS} to fade
     */
    public record Flame(long pos, boolean lingers) {}

    private final ArrayDeque<Flame> flames = new ArrayDeque<>();
    private int countdown;

    /** An empty trail. */
    public Trail() {}

    /**
     * Rebuilds a saved trail. The countdown is clamped into {@code 1 .. fadeTicks(oldest)} so corrupt data can never
     * stall or skip a fade; an empty trail has countdown 0.
     *
     * @param flames the flames, oldest first
     * @param countdown the saved countdown for the oldest flame
     */
    public Trail(Collection<Flame> flames, int countdown) {
        Objects.requireNonNull(flames, "flames").forEach(this::add);
        if (!this.flames.isEmpty()) {
            this.countdown = Math.clamp(countdown, 1, fadeTicks(this.flames.peekFirst()));
        }
    }

    /** How many ticks {@code flame} burns once it is the oldest. */
    public static int fadeTicks(Flame flame) {
        return flame.lingers() ? LINGER_TICKS : FADE_TICKS;
    }

    /** Appends a flame. The countdown starts only if the trail was empty. */
    public void add(Flame flame) {
        Objects.requireNonNull(flame, "flame");
        if (flames.isEmpty()) {
            countdown = fadeTicks(flame);
        }
        flames.addLast(flame);
    }

    /**
     * Advances one tick.
     *
     * @return the oldest flame if it faded this tick, otherwise empty
     */
    public Optional<Flame> tick() {
        if (flames.isEmpty()) {
            return Optional.empty();
        }
        countdown--;
        if (countdown > 0) {
            return Optional.empty();
        }
        Flame faded = flames.removeFirst();
        restartCountdown();
        return Optional.of(faded);
    }

    /**
     * Removes the oldest flames until no more than {@code max} remain. If the oldest flame changed, the countdown
     * restarts for the new oldest.
     *
     * @param max the most flames to keep (≥ 0)
     * @return the removed flames, oldest first
     */
    public List<Flame> trim(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max must be >= 0: " + max);
        }
        List<Flame> removed = new ArrayList<>();
        while (flames.size() > max) {
            removed.add(flames.removeFirst());
        }
        if (!removed.isEmpty()) {
            restartCountdown();
        }
        return removed;
    }

    private void restartCountdown() {
        countdown = flames.isEmpty() ? 0 : fadeTicks(flames.peekFirst());
    }

    /** The flames, oldest first (a copy). */
    public List<Flame> flames() {
        return List.copyOf(flames);
    }

    /** Ticks left until the oldest flame fades; 0 when empty. */
    public int countdown() {
        return countdown;
    }

    /** How many flames the trail holds. */
    public int size() {
        return flames.size();
    }

    /** Whether the trail holds no flames. */
    public boolean isEmpty() {
        return flames.isEmpty();
    }
}
