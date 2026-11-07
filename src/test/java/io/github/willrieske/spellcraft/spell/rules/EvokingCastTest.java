package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.willrieske.spellcraft.spell.rules.EvokingCast.Due;
import io.github.willrieske.spellcraft.spell.rules.EvokingCast.Readiness;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EvokingCastTest {
    private final EvokingCast cast = new EvokingCast();

    @Nested
    class ReadinessOf {
        /**
         * When nothing has been cast, readiness should be READY for both spells at any time.
         *
         * <p>Method under test: {@link EvokingCast#readiness(EvokerSpell, long)}
         */
        @Test
        void freshCastIsReady() {
            // Act & Assert
            assertThat(cast.readiness(EvokerSpell.FANGS, 0)).isEqualTo(Readiness.READY);
            assertThat(cast.readiness(EvokerSpell.VEXES, 100)).isEqualTo(Readiness.READY);
        }

        /**
         * When fangs started at 100, readiness should be BUSY for either spell until the 40 casting ticks end (C1,
         * C2).
         *
         * <p>Method under test: {@link EvokingCast#readiness(EvokerSpell, long)}
         */
        @Test
        void busyWhileTheCastingTicksRun() {
            // Arrange
            cast.start(EvokerSpell.FANGS, 100);
            // Act & Assert
            assertThat(cast.readiness(EvokerSpell.VEXES, 120)).isEqualTo(Readiness.BUSY);
            assertThat(cast.readiness(EvokerSpell.FANGS, 139)).isEqualTo(Readiness.BUSY);
            assertThat(cast.readiness(EvokerSpell.VEXES, 140))
                    .as("vexes are free once the arms drop")
                    .isEqualTo(Readiness.READY);
        }

        /**
         * When fangs started at 100, readiness for fangs should be COOLING until the 100-tick interval has passed
         * (C3).
         *
         * <p>Method under test: {@link EvokingCast#readiness(EvokerSpell, long)}
         */
        @Test
        void coolingUntilTheIntervalPasses() {
            // Arrange
            cast.start(EvokerSpell.FANGS, 100);
            // Act & Assert
            assertThat(cast.readiness(EvokerSpell.FANGS, 150)).isEqualTo(Readiness.COOLING);
            assertThat(cast.readiness(EvokerSpell.FANGS, 199)).isEqualTo(Readiness.COOLING);
            assertThat(cast.readiness(EvokerSpell.FANGS, 200)).isEqualTo(Readiness.READY);
        }

        /**
         * When vexes started at 0, readiness for fangs should be READY at 100: the vexes' casting ticks have ended
         * and the fangs' own interval is free (C4).
         *
         * <p>Method under test: {@link EvokingCast#readiness(EvokerSpell, long)}
         */
        @Test
        void eachSpellHasItsOwnInterval() {
            // Arrange
            cast.start(EvokerSpell.VEXES, 0);
            // Act & Assert
            assertThat(cast.readiness(EvokerSpell.FANGS, 99)).isEqualTo(Readiness.BUSY);
            assertThat(cast.readiness(EvokerSpell.FANGS, 100)).isEqualTo(Readiness.READY);
            assertThat(cast.readiness(EvokerSpell.VEXES, 100)).isEqualTo(Readiness.COOLING);
        }
    }

    @Nested
    class Start {
        /**
         * When a cast is started while readiness is not READY, start should throw (C10).
         *
         * <p>Method under test: {@link EvokingCast#start(EvokerSpell, long)}
         */
        @Test
        void refusesToStartWhileNotReady() {
            // Arrange
            cast.start(EvokerSpell.FANGS, 100);
            // Act & Assert
            assertThatThrownBy(() -> cast.start(EvokerSpell.VEXES, 110))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BUSY");
            assertThatThrownBy(() -> cast.start(EvokerSpell.FANGS, 150))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COOLING");
        }

        /**
         * When a cast starts after a no-targets notice was scheduled, start should drop the notice (C9).
         *
         * <p>Method under test: {@link EvokingCast#start(EvokerSpell, long)}
         */
        @Test
        void dropsAScheduledNotice() {
            // Arrange
            cast.noTargets(100);
            // Act
            cast.start(EvokerSpell.FANGS, 105);
            // Assert
            assertThat(cast.due(120)).as("the notice is gone").isEmpty();
            assertThat(cast.due(125)).contains(new Due(Due.Kind.PERFORM, EvokerSpell.FANGS));
        }
    }

    @Nested
    class NoTargets {
        /**
         * When no targets were found, noTargets should neither start a cooldown nor raise the arms: both spells stay
         * READY and isCasting stays false (spec section 4 step 3).
         *
         * <p>Method under test: {@link EvokingCast#noTargets(long)}
         */
        @Test
        void startsNoCooldownAndRaisesNoArms() {
            // Arrange
            cast.noTargets(100);
            // Act & Assert
            assertThat(cast.readiness(EvokerSpell.FANGS, 100)).isEqualTo(Readiness.READY);
            assertThat(cast.readiness(EvokerSpell.VEXES, 100)).isEqualTo(Readiness.READY);
            assertThat(cast.isCasting(100)).isFalse();
        }
    }

    @Nested
    class IsCasting {
        /**
         * When fangs started at 100, isCasting should be true through tick 139 and false from tick 140 (C1).
         *
         * <p>Method under test: {@link EvokingCast#isCasting(long)}
         */
        @Test
        void trueForTheCastingTicksOnly() {
            // Arrange
            cast.start(EvokerSpell.FANGS, 100);
            // Act & Assert
            assertThat(cast.isCasting(100)).isTrue();
            assertThat(cast.isCasting(139)).isTrue();
            assertThat(cast.isCasting(140)).isFalse();
        }

        /**
         * When nothing was cast, isCasting should be false.
         *
         * <p>Method under test: {@link EvokingCast#isCasting(long)}
         */
        @Test
        void falseWhenFresh() {
            assertThat(cast.isCasting(0)).isFalse();
        }
    }

    @Nested
    class DueAt {
        /**
         * When fangs started at 100, due should be empty before tick 120, PERFORM(FANGS) at 120, and empty after
         * (C5).
         *
         * <p>Method under test: {@link EvokingCast#due(long)}
         */
        @Test
        void performsOnceAtTheWarmupEnd() {
            // Arrange
            cast.start(EvokerSpell.FANGS, 100);
            // Act & Assert
            assertThat(cast.due(119)).isEmpty();
            assertThat(cast.due(120)).contains(new Due(Due.Kind.PERFORM, EvokerSpell.FANGS));
            assertThat(cast.due(121)).isEmpty();
            assertThat(cast.due(500)).isEmpty();
        }

        /**
         * When due is first asked well after the warmup ended, it should still perform exactly once (C6).
         *
         * <p>Method under test: {@link EvokingCast#due(long)}
         */
        @Test
        void performsOnceEvenWhenAskedLate() {
            // Arrange
            cast.start(EvokerSpell.VEXES, 100);
            // Act & Assert
            assertThat(cast.due(150)).contains(new Due(Due.Kind.PERFORM, EvokerSpell.VEXES));
            assertThat(cast.due(151)).isEmpty();
        }

        /**
         * When no targets were found at 100, due should notify once at 120 (C7).
         *
         * <p>Method under test: {@link EvokingCast#due(long)}
         */
        @Test
        void notifiesOnceAfterTheWarmup() {
            // Arrange
            cast.noTargets(100);
            // Act & Assert
            assertThat(cast.due(119)).isEmpty();
            assertThat(cast.due(120)).contains(new Due(Due.Kind.NOTIFY_NO_TARGETS, EvokerSpell.FANGS));
            assertThat(cast.due(121)).isEmpty();
        }

        /**
         * When no targets were found twice, due should notify once, 20 ticks after the later press (C8).
         *
         * <p>Method under test: {@link EvokingCast#due(long)}
         */
        @Test
        void aLaterPressMovesTheNotice() {
            // Arrange
            cast.noTargets(100);
            cast.noTargets(110);
            // Act & Assert
            assertThat(cast.due(120)).isEmpty();
            assertThat(cast.due(130)).contains(new Due(Due.Kind.NOTIFY_NO_TARGETS, EvokerSpell.FANGS));
            assertThat(cast.due(131)).isEmpty();
        }

        /**
         * When both a perform and a notice are overdue, due should return the perform first and the notice on the
         * next call. The scenario (a late {@code due} call while the fangs cast is still busy) is outside the
         * caller's contract; it pins the order the class documents.
         *
         * <p>Method under test: {@link EvokingCast#due(long)}
         */
        @Test
        void performsBeforeNotifyingWhenBothAreOverdue() {
            // Arrange
            cast.start(EvokerSpell.FANGS, 0);
            cast.noTargets(10);
            // Act & Assert
            assertThat(cast.due(100)).contains(new Due(Due.Kind.PERFORM, EvokerSpell.FANGS));
            assertThat(cast.due(101)).contains(new Due(Due.Kind.NOTIFY_NO_TARGETS, EvokerSpell.FANGS));
            assertThat(cast.due(102)).isEmpty();
        }

        /**
         * When nothing is pending, due should be empty.
         *
         * <p>Method under test: {@link EvokingCast#due(long)}
         */
        @Test
        void emptyWhenFresh() {
            assertThat(cast.due(0)).isEmpty();
        }
    }
}
