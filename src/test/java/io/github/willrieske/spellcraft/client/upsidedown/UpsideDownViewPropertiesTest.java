package io.github.willrieske.spellcraft.client.upsidedown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.willrieske.spellcraft.client.upsidedown.UpsideDownView.MouseDelta;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

class UpsideDownViewPropertiesTest {

    @Property
    void mouseMovementIsScreenRelativeAtAnyRoll(
            @ForAll @DoubleRange(min = -720, max = 720) double roll,
            @ForAll @DoubleRange(min = -1000, max = 1000) double dx,
            @ForAll @DoubleRange(min = -1000, max = 1000) double dy) {
        MouseDelta turned = UpsideDownView.turn(dx, dy, roll);
        double r = Math.toRadians(roll);
        double rightX = Math.cos(r);
        double rightY = -Math.sin(r);
        double upX = Math.sin(r);
        double upY = Math.cos(r);
        double gazeX = turned.dx();
        double gazeY = -turned.dy();
        double movedRightOnScreen = gazeX * rightX + gazeY * rightY;
        double movedUpOnScreen = gazeX * upX + gazeY * upY;
        assertThat(movedRightOnScreen).isCloseTo(dx, within(1e-6));
        assertThat(movedUpOnScreen).isCloseTo(-dy, within(1e-6));
    }

    @Property
    void easeNeverOvershoots(@ForAll @DoubleRange(min = -10, max = 10) double t) {
        assertThat(UpsideDownView.ease(t)).isBetween(0.0, 1.0);
    }

    @Property
    void easeIsMonotonic(
            @ForAll @DoubleRange(min = 0, max = 1) double a, @ForAll @DoubleRange(min = 0, max = 1) double b) {
        double lo = Math.min(a, b);
        double hi = Math.max(a, b);
        assertThat(UpsideDownView.ease(lo)).isLessThanOrEqualTo(UpsideDownView.ease(hi));
    }

    @Property
    void strafeKeepsTheSignNearUprightAndFlipsItNearInverted(
            @ForAll @DoubleRange(min = -1, max = 1) double x, @ForAll @DoubleRange(min = -720, max = 720) double roll) {
        double angle = ((roll % 360) + 360) % 360;
        double strafed = UpsideDownView.strafe(x, roll);
        if (angle < 89.999 || angle > 270.001) {
            assertThat(strafed).isEqualTo(x);
        } else if (angle > 90.001 && angle < 269.999) {
            assertThat(strafed).isEqualTo(-x);
        }
    }

    @Property
    void strafeIsPeriodicAndSymmetricInTheRoll(
            @ForAll @DoubleRange(min = -1, max = 1) double x, @ForAll @DoubleRange(min = 0, max = 360) double roll) {
        double angle = ((roll % 360) + 360) % 360;
        // stay away from the 90/270 boundaries, where floating-point rounding may legitimately differ
        if (Math.abs(angle - 90) < 0.001 || Math.abs(angle - 270) < 0.001) {
            return;
        }
        assertThat(UpsideDownView.strafe(x, roll)).isEqualTo(UpsideDownView.strafe(x, roll + 360));
        assertThat(UpsideDownView.strafe(x, roll)).isEqualTo(UpsideDownView.strafe(x, -roll));
    }
}
