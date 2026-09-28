package io.github.willrieske.spellcraft.client.upsidedown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.willrieske.spellcraft.client.upsidedown.UpsideDownView.MouseDelta;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class UpsideDownViewTest {
    private static final Offset<Double> EXACT = within(1e-9);

    @Test
    void easeStartsAtZeroEndsAtOneAndIsCentred() {
        assertThat(UpsideDownView.ease(0.0)).isCloseTo(0.0, EXACT);
        assertThat(UpsideDownView.ease(0.5)).isCloseTo(0.5, EXACT);
        assertThat(UpsideDownView.ease(1.0)).isCloseTo(1.0, EXACT);
        // smoothstep 3t^2 - 2t^3: a linear (un-eased) mutant would give 0.25 here
        assertThat(UpsideDownView.ease(0.25)).isCloseTo(0.15625, EXACT);
        assertThat(UpsideDownView.ease(0.75)).isCloseTo(0.84375, EXACT);
    }

    @Test
    void easeClampsOutOfRangeProgress() {
        assertThat(UpsideDownView.ease(-0.5)).isCloseTo(0.0, EXACT);
        assertThat(UpsideDownView.ease(1.5)).isCloseTo(1.0, EXACT);
    }

    @Test
    void easeStartsAndEndsSlowly() {
        assertThat(UpsideDownView.ease(0.1)).isLessThan(0.1);
        assertThat(UpsideDownView.ease(0.9)).isGreaterThan(0.9);
    }

    @Test
    void rollForInterpolatesThroughTheEasing() {
        assertThat(UpsideDownView.rollFor(0, 180, 0)).isCloseTo(0.0, EXACT);
        assertThat(UpsideDownView.rollFor(0, 180, 1)).isCloseTo(180.0, EXACT);
        assertThat(UpsideDownView.rollFor(180, 360, 0.5)).isCloseTo(270.0, EXACT);
        assertThat(UpsideDownView.rollFor(270, 180, 1)).isCloseTo(180.0, EXACT);
        // off-centre progress: only the eased value passes (linear would give 45.0 / 315.0)
        assertThat(UpsideDownView.rollFor(0, 180, 0.25)).isCloseTo(28.125, EXACT);
        assertThat(UpsideDownView.rollFor(360, 180, 0.25)).isCloseTo(331.875, EXACT);
    }

    @Test
    void turnIsIdentityWhenUpright() {
        assertThat(UpsideDownView.turn(3, -2, 0)).isEqualTo(new MouseDelta(3, -2));
    }

    @Test
    void turnRotatesByTheRoll() {
        assertDelta(UpsideDownView.turn(1, 0, 90), 0, 1);
        assertDelta(UpsideDownView.turn(1, 0, 180), -1, 0);
        assertDelta(UpsideDownView.turn(1, 0, 270), 0, -1);
        assertDelta(UpsideDownView.turn(0, 1, 90), -1, 0);
        assertDelta(UpsideDownView.turn(0, 1, 180), 0, -1);
    }

    @Test
    void strafeKeepsSignWithinNinetyDegreesOfUpright() {
        assertThat(UpsideDownView.strafe(1, 0)).isEqualTo(1);
        assertThat(UpsideDownView.strafe(1, 89.9)).isEqualTo(1);
        assertThat(UpsideDownView.strafe(1, 270.1)).isEqualTo(1);
        assertThat(UpsideDownView.strafe(1, 360)).isEqualTo(1);
        assertThat(UpsideDownView.strafe(1, -45)).isEqualTo(1);
    }

    @Test
    void strafeFlipsFromNinetyDegreesOnward() {
        assertThat(UpsideDownView.strafe(1, 90)).isEqualTo(-1);
        assertThat(UpsideDownView.strafe(1, 180)).isEqualTo(-1);
        assertThat(UpsideDownView.strafe(1, 270)).isEqualTo(-1);
        assertThat(UpsideDownView.strafe(-0.5, 180)).isEqualTo(0.5);
        assertThat(UpsideDownView.strafe(1, 450)).isEqualTo(-1);
        assertThat(UpsideDownView.strafe(1, -90)).isEqualTo(-1);
        assertThat(UpsideDownView.strafe(1, -180)).isEqualTo(-1);
        assertThat(UpsideDownView.strafe(1, -450)).isEqualTo(-1);
    }

    private static void assertDelta(MouseDelta actual, double dx, double dy) {
        assertThat(actual.dx()).isCloseTo(dx, EXACT);
        assertThat(actual.dy()).isCloseTo(dy, EXACT);
    }
}
