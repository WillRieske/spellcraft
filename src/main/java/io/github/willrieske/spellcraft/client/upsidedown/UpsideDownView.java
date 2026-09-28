package io.github.willrieske.spellcraft.client.upsidedown;

/**
 * Pure math for the Upside-Down view: easing, roll interpolation and input compensation.
 * No Minecraft types, so every function is unit-testable.
 */
public final class UpsideDownView {
    /** A mouse turn delta, in the units {@code LocalPlayer.turn(dx, dy)} takes. */
    public record MouseDelta(double dx, double dy) {}

    private static final double RIGHT_ANGLE = 90.0;
    private static final double FULL_TURN = 360.0;

    private UpsideDownView() {}

    /** Smoothstep ease-in-out: f(0)=0, f(1)=1, monotonic, no overshoot. Input is clamped. */
    public static double ease(double t) {
        double c = Math.clamp(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    /** The roll angle {@code progress} of the way from {@code fromDegrees} to {@code toDegrees}. */
    public static double rollFor(double fromDegrees, double toDegrees, double progress) {
        return fromDegrees + (toDegrees - fromDegrees) * ease(progress);
    }

    /**
     * Compensates a mouse delta for the camera roll, so that moving the mouse right always pans
     * the view right on screen. Derivation: {@code docs/api-notes.md} (Camera roll) and
     * {@code UpsideDownViewPropertiesTest}.
     */
    public static MouseDelta turn(double dx, double dy, double rollDegrees) {
        double r = Math.toRadians(rollDegrees);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        return new MouseDelta(dx * cos - dy * sin, dx * sin + dy * cos);
    }

    /** Strafe input stays screen-relative: its sign flips once the view is 90° or more from upright. */
    public static double strafe(double x, double rollDegrees) {
        return degreesFromUpright(rollDegrees) < RIGHT_ANGLE ? x : -x;
    }

    private static double degreesFromUpright(double rollDegrees) {
        double normalized = ((rollDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN;
        return Math.min(normalized, FULL_TURN - normalized);
    }
}
