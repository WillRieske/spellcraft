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

    /** Ticks up to {@code times} (stopping once the trail is empty) and returns each fade keyed by its tick number. */
    private Map<Integer, Flame> fadesWithin(int times) {
        Map<Integer, Flame> fadedAtTick = new LinkedHashMap<>();
        for (int tick = 1; tick <= times && !trail.isEmpty(); tick++) {
            int thisTick = tick;
            trail.tick().ifPresent(flame -> fadedAtTick.put(thisTick, flame));
        }
        return fadedAtTick;
    }

    @Nested
    class Constructor {
        /**
         * When a trail is rebuilt from another's flames and countdown, it should behave identically from then on.
         *
         * <p>Method under test: {@link Trail#Trail(java.util.Collection, int)}
         */
        @Test
        void t8RebuiltFromFlamesAndCountdownBehavesIdentically() {
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
        @Test
        void clampsTheCountdownIntoTheOldestFlamesRange() {
            // Arrange & Act & Assert
            assertThat(new Trail(List.of(PLAIN_A), 0).countdown()).isEqualTo(1);
            assertThat(new Trail(List.of(PLAIN_A), -4).countdown()).isEqualTo(1);
            assertThat(new Trail(List.of(PLAIN_A), 1).countdown()).isEqualTo(1);
            assertThat(new Trail(List.of(PLAIN_A), 20).countdown()).isEqualTo(20);
            assertThat(new Trail(List.of(PLAIN_A), 99).countdown()).isEqualTo(Trail.FADE_TICKS);
            assertThat(new Trail(List.of(LINGERING_C), 33).countdown()).isEqualTo(33);
            assertThat(new Trail(List.of(LINGERING_C), 99).countdown()).isEqualTo(Trail.LINGER_TICKS);
            // The clamp uses the OLDEST flame's fade, not the newest or the longest.
            assertThat(new Trail(List.of(PLAIN_A, LINGERING_C), 30).countdown()).isEqualTo(Trail.FADE_TICKS);
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
         * When a flame is added while the oldest is counting down, add should leave that countdown alone.
         *
         * <p>Method under test: {@link Trail#add(Flame)}
         */
        @Test
        void t5AddingWhileCountingDownLeavesTheOldestCountdownAlone() {
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
         * When the trail is empty, tick should return nothing and leave the countdown at zero.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Test
        void t1OnAnEmptyTrailReturnsNothingAndLeavesTheCountdown() {
            // Arrange & Act & Assert
            assertThat(trail.tick()).isEmpty();
            assertThat(trail.countdown()).isZero();
        }

        /**
         * When a plain flame is the only one, tick should return it on the twentieth tick and leave the trail empty.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Test
        void t2APlainFlameFadesOnTheTwentiethTick() {
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
         * When a lingering flame is the only one, tick should return it on the fortieth tick.
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Test
        void t3ALingeringFlameFadesOnTheFortiethTick() {
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
         *
         * <p>Method under test: {@link Trail#tick()}
         */
        @Test
        void t4PlainThenLingeringFadeInOrderWithEachOwnDelay() {
            // Arrange
            trail.add(PLAIN_A);
            trail.add(LINGERING_C);
            // Act
            Map<Integer, Flame> fadedAtTick = fadesWithin(100);
            // Assert
            assertThat(fadedAtTick).containsExactly(entry(20, PLAIN_A), entry(60, LINGERING_C));
        }
    }

    @Nested
    class Trim {
        /**
         * When the maximum is below the size, trim should remove the oldest flames first and restart the countdown
         * for the new oldest.
         *
         * <p>Method under test: {@link Trail#trim(int)}
         */
        @Test
        void t6BelowSizeRemovesTheOldestFirstAndRestartsTheCountdown() {
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
         * When the maximum is at or above the size, trim should remove nothing and keep the countdown.
         *
         * <p>Method under test: {@link Trail#trim(int)}
         */
        @Test
        void t7AtOrAboveSizeRemovesNothingAndKeepsTheCountdown() {
            // Arrange
            trail.add(PLAIN_A);
            trail.add(PLAIN_B);
            tickQuietly(3);
            // Act
            List<Flame> removed = trail.trim(2);
            // Assert
            assertThat(removed).isEmpty();
            assertThat(trail.countdown()).isEqualTo(Trail.FADE_TICKS - 3);
        }

        /**
         * When the maximum is above the size, trim should remove nothing and keep the countdown.
         *
         * <p>Method under test: {@link Trail#trim(int)}
         */
        @Test
        void t7AboveSizeRemovesNothingAndKeepsTheCountdown() {
            // Arrange
            trail.add(PLAIN_A);
            trail.add(PLAIN_B);
            tickQuietly(3);
            // Act
            List<Flame> removed = trail.trim(5);
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

    @Nested
    class FlameRecord {
        /**
         * When a flame is built, it should report the position and flag it was given.
         *
         * <p>Method under test: {@link Flame}
         */
        @Test
        void keepsItsPositionAndItsLingerFlag() {
            // Arrange & Act
            Flame flame = new Flame(-7L, true);
            // Assert
            assertThat(flame.pos()).isEqualTo(-7L);
            assertThat(flame.lingers()).isTrue();
        }
    }
}
