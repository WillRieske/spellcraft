package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.Trail.Flame;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class TrailPropertiesTest {
    private static final int GUARD = 10_000;

    private static Arbitrary<Flame> flame() {
        return Combinators.combine(Arbitraries.longs(), Arbitraries.of(true, false))
                .as(Flame::new);
    }

    @Provide
    Arbitrary<List<Flame>> flames() {
        return flame().list().ofMaxSize(12);
    }

    @Provide
    Arbitrary<List<Flame>> someFlames() {
        return flame().list().ofMinSize(1).ofMaxSize(12);
    }

    private static Trail trailOf(List<Flame> flames) {
        Trail trail = new Trail();
        flames.forEach(trail::add);
        return trail;
    }

    // jqwik refuses static @Group classes ("@Group classes must not be static"), so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Tick {
        /**
         * For any list of flames, ticking a trail to empty should return the flames in the order they were added.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Property
        void flamesFadeInTheOrderTheyWereAdded(@ForAll("flames") List<Flame> flames) {
            // Arrange
            Trail trail = trailOf(flames);
            List<Flame> faded = new ArrayList<>();
            // Act
            int guard = 0;
            while (!trail.isEmpty() && guard++ < GUARD) {
                trail.tick().ifPresent(faded::add);
            }
            // Assert
            assertThat(faded).isEqualTo(flames);
        }

        /**
         * For any list of flames, the ticks needed to empty the trail should equal the sum of the flames' fade ticks.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Property
        void ticksToEmptyEqualTheSumOfFadeTicks(@ForAll("flames") List<Flame> flames) {
            // Arrange
            Trail trail = trailOf(flames);
            // Act
            int ticks = 0;
            while (!trail.isEmpty() && ticks <= GUARD) {
                trail.tick();
                ticks++;
            }
            // Assert
            assertThat(ticks)
                    .isEqualTo(flames.stream().mapToInt(Trail::fadeTicks).sum());
        }
    }

    @Group
    @SuppressWarnings("ClassCanBeStatic") // jqwik refuses static @Group classes
    class Trim {
        /**
         * For any list of flames and maximum, trim should leave at most the maximum, removing the oldest first.
         *
         * <p>Method under test: {@link Trail#trim(int)}
         */
        @Property
        void neverLeavesMoreThanMaxAndRemovesTheOldest(
                @ForAll("flames") List<Flame> flames, @ForAll @IntRange(min = 0, max = 15) int max) {
            // Arrange
            Trail trail = trailOf(flames);
            // Act
            List<Flame> removed = trail.trim(max);
            // Assert
            assertThat(trail.size()).isLessThanOrEqualTo(max);
            int expectedRemoved = Math.max(0, flames.size() - max);
            assertThat(removed).isEqualTo(flames.subList(0, expectedRemoved));
            assertThat(trail.flames()).isEqualTo(flames.subList(expectedRemoved, flames.size()));
        }
    }

    /** The countdown's contract is stated across the operations that move it, so these properties live together. */
    @Group
    @SuppressWarnings("ClassCanBeStatic") // jqwik refuses static @Group classes
    class Countdown {
        /**
         * For any non-empty list of flames and number of ticks, the countdown should stay within 1..fadeTicks(oldest)
         * while flames remain, and be 0 once the trail is empty.
         *
         * <p>Method under test: {@link Trail#countdown()}, after {@link Trail#tick()}
         */
        @Property
        void staysWithinTheOldestFlamesFadeWhileNonEmpty(
                @ForAll("someFlames") List<Flame> flames, @ForAll @IntRange(min = 0, max = 500) int ticks) {
            // Arrange
            Trail trail = trailOf(flames);
            for (int i = 0; i < ticks && !trail.isEmpty(); i++) {
                // Act
                trail.tick();
                // Assert
                if (!trail.isEmpty()) {
                    assertThat(trail.countdown())
                            .isBetween(1, Trail.fadeTicks(trail.flames().get(0)));
                }
            }
            if (trail.isEmpty()) {
                assertThat(trail.countdown()).isZero();
            }
        }

        /**
         * For any mix of add, tick and trim operations, the countdown should stay within the oldest flame's fade, or
         * be 0 when the trail is empty.
         *
         * <p>Method under test: {@link Trail#countdown()}, after {@link Trail#add(Flame)}, {@link Trail#tick()} and
         * {@link Trail#trim(int)}
         */
        @Property
        void invariantHoldsAfterAnyMixOfAddTickAndTrim(
                @ForAll("flames") List<Flame> flames, @ForAll List<@IntRange(min = 0, max = 5) Integer> ops) {
            // Arrange
            Trail trail = new Trail();
            Iterator<Flame> next = flames.iterator();
            for (int op : ops) {
                // Act
                if (op <= 1) {
                    if (next.hasNext()) {
                        trail.add(next.next());
                    }
                } else if (op <= 3) {
                    trail.tick();
                } else {
                    trail.trim(op - 4); // op 4 trims to 0, op 5 to 1
                }
                // Assert
                if (trail.isEmpty()) {
                    assertThat(trail.countdown()).isZero();
                } else {
                    assertThat(trail.countdown())
                            .isBetween(1, Trail.fadeTicks(trail.flames().get(0)));
                }
            }
        }
    }
}
