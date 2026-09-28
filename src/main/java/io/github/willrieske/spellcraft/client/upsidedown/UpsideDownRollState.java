package io.github.willrieske.spellcraft.client.upsidedown;

/**
 * Client-side roll animation for Strength of Upside-Down. Pure: driven only by the arguments
 * to {@link #tick}, so it is unit-testable without Minecraft. The roll-in goes 0→180°, the
 * roll-out continues 180→360° in the same direction: one full barrel roll per cast.
 */
public final class UpsideDownRollState {
    public enum Phase {
        UPRIGHT,
        ROLLING_IN,
        FLIPPED,
        ROLLING_OUT
    }

    public static final double FLIPPED_DEGREES = 180.0;
    public static final double FULL_TURN_DEGREES = 360.0;

    /**
     * Ticks after a resync (login, respawn, level change) during which changes snap instead of
     * animating: the effect-sync packet can land a few ticks after the new player object.
     */
    public static final int SNAP_GRACE_TICKS = 20;

    private static final double EPSILON = 1e-9;

    private Phase phase = Phase.UPRIGHT;
    private double fromDegrees;
    private double toDegrees;
    private int durationTicks;
    private int elapsedTicks;
    private int graceTicks;

    public Phase phase() {
        return phase;
    }

    /** Call once per client tick. {@code transitionTicks <= 0} snaps. */
    public void tick(boolean effectPresent, boolean resync, int transitionTicks) {
        if (resync) {
            graceTicks = SNAP_GRACE_TICKS;
        }
        boolean snap = graceTicks > 0 || transitionTicks <= 0;
        if (graceTicks > 0) {
            graceTicks--;
        }
        if (snap) {
            settle(effectPresent ? Phase.FLIPPED : Phase.UPRIGHT);
            return;
        }
        advance();
        if (effectPresent && (phase == Phase.UPRIGHT || phase == Phase.ROLLING_OUT)) {
            startRoll(Phase.ROLLING_IN, FLIPPED_DEGREES, transitionTicks);
        } else if (!effectPresent && (phase == Phase.FLIPPED || phase == Phase.ROLLING_IN)) {
            double target = rollDegrees(0f) >= FLIPPED_DEGREES ? FULL_TURN_DEGREES : 0.0;
            startRoll(Phase.ROLLING_OUT, target, transitionTicks);
        }
    }

    /** The roll to render, interpolated within the current tick. In [0, 360]; 360 looks like 0. */
    public double rollDegrees(float partialTick) {
        return switch (phase) {
            case UPRIGHT -> 0.0;
            case FLIPPED -> FLIPPED_DEGREES;
            case ROLLING_IN, ROLLING_OUT ->
                UpsideDownView.rollFor(fromDegrees, toDegrees, (elapsedTicks + (double) partialTick) / durationTicks);
        };
    }

    private void advance() {
        if (phase != Phase.ROLLING_IN && phase != Phase.ROLLING_OUT) {
            return;
        }
        elapsedTicks++;
        if (elapsedTicks >= durationTicks) {
            settle(phase == Phase.ROLLING_IN ? Phase.FLIPPED : Phase.UPRIGHT);
        }
    }

    private void startRoll(Phase rolling, double target, int transitionTicks) {
        double from = rollDegrees(0f);
        double distance = Math.abs(target - from);
        if (distance < EPSILON) {
            settle(rolling == Phase.ROLLING_IN ? Phase.FLIPPED : Phase.UPRIGHT);
            return;
        }
        phase = rolling;
        fromDegrees = from;
        toDegrees = target;
        durationTicks = Math.max(1, (int) Math.round(transitionTicks * distance / FLIPPED_DEGREES));
        elapsedTicks = 0;
    }

    private void settle(Phase settled) {
        phase = settled;
        elapsedTicks = 0;
        durationTicks = 0;
    }
}
