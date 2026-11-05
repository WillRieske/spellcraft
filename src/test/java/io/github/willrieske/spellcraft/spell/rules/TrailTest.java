package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.github.willrieske.spellcraft.spell.rules.Trail.Flame;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TrailTest {
    private static final Flame PLAIN_A = new Flame(1L, false);
    private static final Flame PLAIN_B = new Flame(2L, false);
    private static final Flame LINGERING_C = new Flame(3L, true);

    private final Trail trail = new Trail();

    /** Ticks {@code times} and asserts nothing fades on the way. */
    private void tickQuietly(int times) {
        for (int i = 0; i < times; i++) {
            assertThat(trail.tick()).as("tick %d of %d", i + 1, times).isEmpty();
        }
    }

    @Nested
    class Constructor {
        /**
         * When a trail is rebuilt from another's flames and countdown, it should behave identically from then on. Spec
         * section 6, T8.
         *
         * <p>Method under test: {@link Trail#Trail(java.util.Collection, int)}
         */
        @Test
        void rebuiltFromFlamesAndCountdownBehavesIdentically() {
            // Arrange
            trail.add(PLAIN_A);
            trail.add(LINGERING_C);
            tickQuietly(7);
            // Act
            Trail rebuilt = new Trail(trail.flames(), trail.countdown());
            // Assert
            assertThat(rebuilt.flames()).isEqualTo(trail.flames());
            assertThat(rebuilt.countdown()).isEqualTo(trail.countdown());
            for (int i = 0; i < 60; i++) {
                assertThat(rebuilt.tick()).as("tick %d", i).isEqualTo(trail.tick());
            }
        }

        /**
         * When the saved countdown is outside 1..fadeTicks(oldest), the constructor should clamp it; in-range values
         * and the bounds should be kept as they are.
         *
         * <p>Method under test: {@link Trail#Trail(java.util.Collection, int)}
         */
        @ParameterizedTest(name = "[{index}] flames {0}, saved {1} -> {2}")
        @CsvSource({
            "PLAIN, 0, 1",
            "PLAIN, -4, 1",
            "PLAIN, 1, 1",
            "PLAIN, 20, 20",
            "PLAIN, 99, 20",
            "LINGERING, 33, 33",
            "LINGERING, 99, 40",
            // The clamp uses the OLDEST flame's fade, not the newest or the longest.
            "PLAIN_THEN_LINGERING, 30, 20"
        })
        void clampsTheCountdownIntoTheOldestFlamesRange(String flames, int saved, int expected) {
            // Arrange
            List<Flame> saveFlames = switch (flames) {
                case "PLAIN" -> List.of(PLAIN_A);
                case "LINGERING" -> List.of(LINGERING_C);
                default -> List.of(PLAIN_A, LINGERING_C);
            };
            // Act
            Trail rebuilt = new Trail(saveFlames, saved);
            // Assert
            assertThat(rebuilt.countdown()).isEqualTo(expected);
        }

        /**
         * When rebuilt from a null flame collection, the constructor should refuse it.
         *
         * <p>Method under test: {@link Trail#Trail(java.util.Collection, int)}
         */
        @Test
        void rejectsNullFlames() {
            assertThatThrownBy(() -> new Trail(null, 5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("flames");
        }

        /**
         * When rebuilt with no flames, the trail should be empty with a zero countdown whatever was saved.
         *
         * <p>Method under test: {@link Trail#Trail(java.util.Collection, int)}
         */
        @Test
        void emptyFlamesGiveAnEmptyTrailWithAZeroCountdown() {
            // Arrange & Act
            Trail empty = new Trail(List.of(), 17);
            // Assert
            assertThat(empty.isEmpty()).isTrue();
            assertThat(empty.countdown()).isZero();
        }
    }

    @Nested
    class FadeTicks {
        /**
         * When asked for a flame's fade, fadeTicks should give 20 for a plain flame and 40 for a lingering one.
         *
         * <p>Method under test: {@link Trail#fadeTicks(Flame)}
         */
        @Test
        void dependsOnLingering() {
            // Arrange & Act & Assert
            assertThat(Trail.fadeTicks(PLAIN_A)).isEqualTo(20);
            assertThat(Trail.fadeTicks(LINGERING_C)).isEqualTo(40);
        }
    }

    @Nested
    class Add {
        /**
         * When a flame is added while the oldest is counting down, add should leave that countdown alone. Spec
         * section 6, T5.
         *
         * <p>Method under test: {@link Trail#add(Flame)}
         */
        @Test
        void addingWhileCountingDownLeavesTheOldestCountdownAlone() {
            // Arrange
            trail.add(PLAIN_A);
            tickQuietly(5);
            // Act
            trail.add(LINGERING_C);
            // Assert
            assertThat(trail.countdown()).isEqualTo(Trail.FADE_TICKS - 5);
            tickQuietly(14);
            assertThat(trail.tick()).contains(PLAIN_A);
        }

        /**
         * When flames are added after the trail emptied, add should start the countdown for the first new flame.
         *
         * <p>Method under test: {@link Trail#add(Flame)}
         */
        @Test
        void addingToATrailThatEmptiedStartsAFreshCountdown() {
            // Arrange
            trail.add(PLAIN_A);
            tickQuietly(19);
            trail.tick();
            // Act
            trail.add(LINGERING_C);
            // Assert
            assertThat(trail.countdown()).isEqualTo(Trail.LINGER_TICKS);
        }

        /**
         * When flames are added after trim emptied the trail, add should start the countdown for the first new flame.
         *
         * <p>Method under test: {@link Trail#add(Flame)}
         */
        @Test
        void addingToATrailThatTrimEmptiedStartsAFreshCountdown() {
            // Arrange
            trail.add(LINGERING_C);
            trail.trim(0);
            // Act
            trail.add(PLAIN_B);
            // Assert
            assertThat(trail.countdown()).isEqualTo(Trail.FADE_TICKS);
        }

        /**
         * When given null, add should refuse it.
         *
         * <p>Method under test: {@link Trail#add(Flame)}
         */
        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> trail.add(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("flame");
        }
    }

    @Nested
    class Tick {
        /**
         * When the trail is empty, tick should return nothing and leave the countdown at zero. Spec section 6, T1.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Test
        void onAnEmptyTrailReturnsNothingAndLeavesTheCountdown() {
            // Arrange & Act & Assert
            assertThat(trail.tick()).isEmpty();
            assertThat(trail.countdown()).isZero();
        }

        /**
         * When a plain flame is the only one, tick should return it on the twentieth tick and leave the trail empty.
         * Spec section 6, T2.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Test
        void aPlainFlameFadesOnTheTwentiethTick() {
            // Arrange
            trail.add(PLAIN_A);
            assertThat(trail.countdown()).isEqualTo(Trail.FADE_TICKS);
            tickQuietly(19);
            // Act
            Optional<Flame> faded = trail.tick();
            // Assert
            assertThat(faded).contains(PLAIN_A);
            assertThat(trail.isEmpty()).isTrue();
            assertThat(trail.countdown()).isZero();
        }

        /**
         * When a lingering flame is the only one, tick should return it on the fortieth tick. Spec section 6, T3.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Test
        void aLingeringFlameFadesOnTheFortiethTick() {
            // Arrange
            trail.add(LINGERING_C);
            tickQuietly(39);
            // Act
            Optional<Flame> faded = trail.tick();
            // Assert
            assertThat(faded).contains(LINGERING_C);
        }

        /**
         * When a plain flame is followed by a lingering one, tick should fade them in order, each with its own delay.
         * Spec section 6, T4.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Test
        void plainThenLingeringFadeInOrderWithEachOwnDelay() {
            // Arrange
            trail.add(PLAIN_A);
            trail.add(LINGERING_C);
            // Act
            Map<Integer, Flame> fadedAtTick = new LinkedHashMap<>();
            for (int tick = 1; tick <= 100 && !trail.isEmpty(); tick++) {
                int thisTick = tick;
                trail.tick().ifPresent(flame -> fadedAtTick.put(thisTick, flame));
            }
            // Assert
            assertThat(fadedAtTick).containsExactly(entry(20, PLAIN_A), entry(60, LINGERING_C));
        }
    }

    @Nested
    class Trim {
        /**
         * When the maximum is below the size, trim should remove the oldest flames first and restart the countdown
         * for the new oldest. Spec section 6, T6.
         *
         * <p>Method under test: {@link Trail#trim(int)}
         */
        @Test
        void belowSizeRemovesTheOldestFirstAndRestartsTheCountdown() {
            // Arrange
            trail.add(PLAIN_A);
            trail.add(PLAIN_B);
            trail.add(LINGERING_C);
            tickQuietly(5);
            // Act
            List<Flame> removed = trail.trim(1);
            // Assert
            assertThat(removed).containsExactly(PLAIN_A, PLAIN_B);
            assertThat(trail.flames()).containsExactly(LINGERING_C);
            assertThat(trail.countdown()).isEqualTo(Trail.LINGER_TICKS);
        }

        /**
         * When the maximum is at or above the size (here 2 flames, maximum 2 or 5), trim should remove nothing and keep
         * the countdown. Spec section 6, T7.
         *
         * <p>Method under test: {@link Trail#trim(int)}
         */
        @ParameterizedTest
        @ValueSource(ints = {2, 5})
        void atOrAboveSizeRemovesNothingAndKeepsTheCountdown(int max) {
            // Arrange
            trail.add(PLAIN_A);
            trail.add(PLAIN_B);
            tickQuietly(3);
            // Act
            List<Flame> removed = trail.trim(max);
            // Assert
            assertThat(removed).isEmpty();
            assertThat(trail.countdown()).isEqualTo(Trail.FADE_TICKS - 3);
        }

        /**
         * When the maximum is zero, trim should empty the trail and zero the countdown.
         *
         * <p>Method under test: {@link Trail#trim(int)}
         */
        @Test
        void toZeroEmptiesTheTrail() {
            // Arrange
            trail.add(PLAIN_A);
            // Act
            List<Flame> removed = trail.trim(0);
            // Assert
            assertThat(removed).containsExactly(PLAIN_A);
            assertThat(trail.isEmpty()).isTrue();
            assertThat(trail.countdown()).isZero();
        }

        /**
         * When the maximum is negative, trim should refuse it.
         *
         * <p>Method under test: {@link Trail#trim(int)}
         */
        @Test
        void rejectsANegativeMaximum() {
            assertThatThrownBy(() -> trail.trim(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max");
        }
    }

    @Nested
    class Flames {
        /**
         * When flames() is called, it should return a copy that later adds do not change.
         *
         * <p>Method under test: {@link Trail#flames()}
         */
        @Test
        void isACopy() {
            // Arrange
            trail.add(PLAIN_A);
            List<Flame> snapshot = trail.flames();
            // Act
            trail.add(PLAIN_B);
            // Assert
            assertThat(snapshot).containsExactly(PLAIN_A);
            assertThat(trail.size()).isEqualTo(2);
        }
    }
}
