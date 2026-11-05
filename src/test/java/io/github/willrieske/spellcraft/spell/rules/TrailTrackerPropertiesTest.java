package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalLong;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TrailTrackerPropertiesTest {
    /** One observation: a position (few distinct values, so repeats are common) and whether the player was grounded. */
    record Step(int pos, boolean grounded) {}

    @Provide
    Arbitrary<List<Step>> steps() {
        Arbitrary<Step> step = Combinators.combine(Arbitraries.integers().between(0, 3), Arbitraries.of(true, false))
                .as(Step::new);
        return step.list().ofMinSize(1).ofMaxSize(30);
    }

    /**
     * For any sequence of observations, observe should light the previous position exactly when the position changed
     * and the player was grounded at some point while on the previous block.
     *
     * <p>Method under test: {@link TrailTracker#observe(long, boolean)}
     */
    @Property
    void lightsThePreviousBlockIffThePlayerWasEverGroundedOnIt(@ForAll("steps") List<Step> steps) {
        // Arrange
        TrailTracker tracker = new TrailTracker();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            // Act
            OptionalLong lit = tracker.observe(step.pos(), step.grounded());
            // Assert
            OptionalLong expected = OptionalLong.empty();
            if (i > 0 && steps.get(i - 1).pos() != step.pos()) {
                int previousPos = steps.get(i - 1).pos();
                int runStart = i - 1;
                while (runStart > 0 && steps.get(runStart - 1).pos() == previousPos) {
                    runStart--;
                }
                if (steps.subList(runStart, i).stream().anyMatch(Step::grounded)) {
                    expected = OptionalLong.of(previousPos);
                }
            }
            assertThat(lit).as("steps %s at index %d", steps, i).isEqualTo(expected);
        }
    }

    /**
     * For any history, a reset tracker should answer the next observations exactly like a brand-new tracker.
     *
     * <p>Method under test: {@link TrailTracker#reset()}
     */
    @Property
    void resetMakesATrackerFresh(@ForAll("steps") List<Step> history, @ForAll("steps") List<Step> next) {
        // Arrange
        TrailTracker used = new TrailTracker();
        history.forEach(step -> used.observe(step.pos(), step.grounded()));
        TrailTracker fresh = new TrailTracker();
        // Act
        used.reset();
        // Assert
        for (Step step : next) {
            assertThat(used.observe(step.pos(), step.grounded())).isEqualTo(fresh.observe(step.pos(), step.grounded()));
        }
    }
}
