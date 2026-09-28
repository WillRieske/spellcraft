package io.github.willrieske.spellcraft.client.upsidedown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.willrieske.spellcraft.client.upsidedown.UpsideDownRollState.Phase;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class UpsideDownRollStateTest {
    private static final int T = 20;
    private static final Offset<Double> EXACT = within(1e-9);

    private static void tick(UpsideDownRollState s, int times, boolean present) {
        for (int i = 0; i < times; i++) {
            s.tick(present, false, T);
        }
    }

    private static UpsideDownRollState flipped() {
        UpsideDownRollState s = new UpsideDownRollState();
        tick(s, T + 1, true);
        assertThat(s.phase()).isEqualTo(Phase.FLIPPED);
        return s;
    }

    @Test
    void startsUpright() {
        UpsideDownRollState s = new UpsideDownRollState();
        assertThat(s.phase()).isEqualTo(Phase.UPRIGHT);
        assertThat(s.rollDegrees(0.5f)).isCloseTo(0.0, EXACT);
    }

    @Test
    void effectAppearingStartsRollingInFromUpright() {
        UpsideDownRollState s = new UpsideDownRollState();
        s.tick(true, false, T);
        assertThat(s.phase()).isEqualTo(Phase.ROLLING_IN);
        assertThat(s.rollDegrees(0f)).isCloseTo(0.0, EXACT);
        assertThat(s.rollDegrees(1f)).isGreaterThan(0.0);
    }

    @Test
    void rollInIsHalfwayAtTheMidpoint() {
        UpsideDownRollState s = new UpsideDownRollState();
        tick(s, T / 2 + 1, true);
        assertThat(s.rollDegrees(0f)).isCloseTo(90.0, EXACT);
    }

    @Test
    void rollInSettlesFlippedAfterTheTransition() {
        UpsideDownRollState s = new UpsideDownRollState();
        tick(s, T, true);
        assertThat(s.phase()).isEqualTo(Phase.ROLLING_IN);
        assertThat(s.rollDegrees(1f)).isCloseTo(180.0, EXACT);
        s.tick(true, false, T);
        assertThat(s.phase()).isEqualTo(Phase.FLIPPED);
        assertThat(s.rollDegrees(0.3f)).isCloseTo(180.0, EXACT);
    }

    @Test
    void rollOutContinuesInTheSameDirectionTo360() {
        UpsideDownRollState s = flipped();
        s.tick(false, false, T);
        assertThat(s.phase()).isEqualTo(Phase.ROLLING_OUT);
        assertThat(s.rollDegrees(0f)).isCloseTo(180.0, EXACT);
        tick(s, T / 2, false);
        assertThat(s.rollDegrees(0f)).isCloseTo(270.0, EXACT);
        tick(s, T / 2 - 1, false);
        assertThat(s.rollDegrees(1f)).isCloseTo(360.0, EXACT);
        s.tick(false, false, T);
        assertThat(s.phase()).isEqualTo(Phase.UPRIGHT);
        assertThat(s.rollDegrees(0f)).isCloseTo(0.0, EXACT);
    }

    @Test
    void aFullCastIsOneMonotonicBarrelRoll() {
        UpsideDownRollState s = new UpsideDownRollState();
        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < T + 1; i++) {
            s.tick(true, false, T);
            angles.add(s.rollDegrees(0f));
        }
        for (int i = 0; i < T; i++) {
            s.tick(false, false, T);
            angles.add(s.rollDegrees(0f));
        }
        angles.add(s.rollDegrees(1f));
        assertThat(angles).isSorted();
        assertThat(angles.getLast()).isCloseTo(360.0, EXACT);
    }

    @Test
    void castDuringRollOutReversesSmoothlyBackToFlipped() {
        UpsideDownRollState s = flipped();
        tick(s, T / 2 + 1, false);
        double before = s.rollDegrees(1f);
        s.tick(true, false, T);
        assertThat(s.phase()).isEqualTo(Phase.ROLLING_IN);
        assertThat(s.rollDegrees(0f)).isCloseTo(before, EXACT);
        double previous = s.rollDegrees(0f);
        for (int i = 0; i < T + 1 && s.phase() == Phase.ROLLING_IN; i++) {
            s.tick(true, false, T);
            assertThat(s.rollDegrees(0f)).isLessThanOrEqualTo(previous);
            previous = s.rollDegrees(0f);
        }
        assertThat(s.phase()).isEqualTo(Phase.FLIPPED);
    }

    @Test
    void effectEndingDuringRollInReversesBackToUpright() {
        UpsideDownRollState s = new UpsideDownRollState();
        tick(s, T / 2 + 1, true);
        double before = s.rollDegrees(1f);
        s.tick(false, false, T);
        assertThat(s.phase()).isEqualTo(Phase.ROLLING_OUT);
        assertThat(s.rollDegrees(0f)).isCloseTo(before, EXACT);
        double previous = s.rollDegrees(0f);
        for (int i = 0; i < T + 1 && s.phase() == Phase.ROLLING_OUT; i++) {
            s.tick(false, false, T);
            assertThat(s.rollDegrees(0f)).isLessThanOrEqualTo(previous);
            previous = s.rollDegrees(0f);
        }
        assertThat(s.phase()).isEqualTo(Phase.UPRIGHT);
        assertThat(s.rollDegrees(0f)).isCloseTo(0.0, EXACT);
    }

    @Test
    void recastWhileFlippedStaysFlipped() {
        UpsideDownRollState s = flipped();
        tick(s, 5, true);
        assertThat(s.phase()).isEqualTo(Phase.FLIPPED);
        assertThat(s.rollDegrees(0.7f)).isCloseTo(180.0, EXACT);
    }

    @Test
    void loginWithEffectSnapsStraightToFlipped() {
        UpsideDownRollState s = new UpsideDownRollState();
        s.tick(true, true, T);
        assertThat(s.phase()).isEqualTo(Phase.FLIPPED);
        assertThat(s.rollDegrees(0f)).isCloseTo(180.0, EXACT);
    }

    @Test
    void respawnWithoutEffectSnapsStraightToUpright() {
        UpsideDownRollState s = flipped();
        s.tick(false, true, T);
        assertThat(s.phase()).isEqualTo(Phase.UPRIGHT);
        assertThat(s.rollDegrees(0f)).isCloseTo(0.0, EXACT);
    }

    @Test
    void effectSyncArrivingWithinTheGraceWindowSnaps() {
        UpsideDownRollState s = new UpsideDownRollState();
        s.tick(false, true, T);
        tick(s, UpsideDownRollState.SNAP_GRACE_TICKS - 2, false);
        s.tick(true, false, T);
        assertThat(s.phase()).isEqualTo(Phase.FLIPPED);
    }

    @Test
    void effectArrivingAfterTheGraceWindowAnimates() {
        UpsideDownRollState s = new UpsideDownRollState();
        s.tick(false, true, T);
        tick(s, UpsideDownRollState.SNAP_GRACE_TICKS - 1, false);
        s.tick(true, false, T);
        assertThat(s.phase()).isEqualTo(Phase.ROLLING_IN);
    }

    @Test
    void zeroTransitionSnapsBothWays() {
        UpsideDownRollState s = new UpsideDownRollState();
        s.tick(true, false, 0);
        assertThat(s.phase()).isEqualTo(Phase.FLIPPED);
        s.tick(false, false, 0);
        assertThat(s.phase()).isEqualTo(Phase.UPRIGHT);
    }
}
