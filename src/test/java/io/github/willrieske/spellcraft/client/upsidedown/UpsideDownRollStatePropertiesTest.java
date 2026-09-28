package io.github.willrieske.spellcraft.client.upsidedown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class UpsideDownRollStatePropertiesTest {

    /**
     * Expands alternating runs (effect present for N ticks, absent for M ticks, ...). Independent
     * random booleans flip about every 2 ticks, so a transition of more than a few ticks would
     * almost never complete; runs of up to 60 ticks let it settle and reverse mid-roll.
     */
    private static List<Boolean> expand(boolean startPresent, List<Integer> runs) {
        List<Boolean> ticks = new ArrayList<>();
        boolean present = startPresent;
        for (int run : runs) {
            for (int i = 0; i < run; i++) {
                ticks.add(present);
            }
            present = !present;
        }
        return ticks;
    }

    /** No visible jump between the last frame before a tick and the first frame after it. */
    @Property
    void rollIsContinuousAcrossTicks(
            @ForAll boolean startPresent,
            @ForAll @Size(max = 12) List<@IntRange(min = 1, max = 60) Integer> runs,
            @ForAll @IntRange(min = 1, max = 200) int transitionTicks) {
        UpsideDownRollState state = new UpsideDownRollState();
        for (boolean present : expand(startPresent, runs)) {
            double before = state.rollDegrees(1f);
            state.tick(present, false, transitionTicks);
            assertThat(angularGap(before, state.rollDegrees(0f))).isLessThan(1e-9);
        }
    }

    @Property
    void rollStaysWithinOneTurn(
            @ForAll boolean startPresent,
            @ForAll @Size(max = 12) List<@IntRange(min = 1, max = 60) Integer> runs,
            @ForAll @IntRange(min = 0, max = 200) int transitionTicks) {
        UpsideDownRollState state = new UpsideDownRollState();
        for (boolean present : expand(startPresent, runs)) {
            state.tick(present, false, transitionTicks);
            for (float partial : new float[] {0f, 0.5f, 1f}) {
                assertThat(state.rollDegrees(partial)).isBetween(0.0, 360.0);
            }
        }
    }

    /** A resync always snaps: after it the roll is exactly upright or exactly flipped. */
    @Property
    void resyncSnapsToTheEffectState(
            @ForAll boolean startPresent,
            @ForAll @Size(max = 12) List<@IntRange(min = 1, max = 60) Integer> runs,
            @ForAll boolean presentAtResync,
            @ForAll @IntRange(min = 1, max = 200) int transitionTicks) {
        UpsideDownRollState state = new UpsideDownRollState();
        for (boolean present : expand(startPresent, runs)) {
            state.tick(present, false, transitionTicks);
        }
        state.tick(presentAtResync, true, transitionTicks);
        assertThat(state.phase())
                .isEqualTo(presentAtResync ? UpsideDownRollState.Phase.FLIPPED : UpsideDownRollState.Phase.UPRIGHT);
        assertThat(state.rollDegrees(0.5f)).isEqualTo(presentAtResync ? 180.0 : 0.0);
    }

    private static double angularGap(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return Math.min(d, 360.0 - d);
    }
}
