package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.EvokingCast.Due;
import io.github.willrieske.spellcraft.spell.rules.EvokingCast.Readiness;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Functional spec §2.2, §2.3, §3.3. */
class EvokingCastPropertiesTest {
    /**
     * One key press: how many ticks pass first, which spell, and whether fangs found targets (ignored for vexes).
     *
     * @param spell the spell pressed
     * @param gap the ticks that pass, each with due asked, before the press
     * @param targets whether a fang request finds targets
     */
    record Press(EvokerSpell spell, int gap, boolean targets) {}

    @Provide
    Arbitrary<List<Press>> presses() {
        Arbitrary<Press> press = Combinators.combine(
                        Arbitraries.of(EvokerSpell.class),
                        Arbitraries.integers().between(0, 60),
                        Arbitraries.of(true, false))
                .as(Press::new);
        return press.list().ofMinSize(1).ofMaxSize(40);
    }

    /** A cast plus the bookkeeping the due property checks every tick against. */
    private static final class Run {
        final EvokingCast cast = new EvokingCast();
        long now;
        int performs;
        int starts;
        long lastStart = Long.MIN_VALUE;
        long noticeAt = -1;
        EvokerSpell lastSpell = EvokerSpell.FANGS;

        /**
         * Advances one tick, asks due, and checks that a perform comes exactly 20 ticks after its start for the
         * latest spell, that a notice comes exactly 20 ticks after the latest no-targets press (and only if no start
         * dropped it), and that isCasting matches the latest start's casting ticks.
         */
        void tick() {
            // Act
            now++;
            Optional<Due> due = cast.due(now);
            // Assert
            if (due.isPresent() && due.get().kind() == Due.Kind.PERFORM) {
                performs++;
                assertThat(now)
                        .as("a perform comes exactly 20 ticks after its start")
                        .isEqualTo(lastStart + EvokerSpell.WARMUP_TICKS);
                assertThat(due.get().spell()).isEqualTo(lastSpell);
            }
            if (due.isPresent() && due.get().kind() == Due.Kind.NOTIFY_NO_TARGETS) {
                assertThat(now)
                        .as("a notice comes exactly 20 ticks after the latest no-targets press, and"
                                + " only if no start dropped it")
                        .isEqualTo(noticeAt);
                noticeAt = -1;
            }
            assertThat(cast.isCasting(now)).isEqualTo(now < lastStart + lastSpell.castingTicks());
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class DueProperties {
        /**
         * For any sequence of presses, each one handled as the caster does (readiness, then start or noTargets), and
         * with due asked every tick, each PERFORM should come exactly once, exactly 20 ticks after its start, and
         * isCasting should be false once the casting ticks have passed; the same holds while the sequence drains.
         *
         * <p>Method under test: {@link EvokingCast#due(long)}
         */
        @Property
        void everyStartPerformsOnceAfterTheWarmup(@ForAll("presses") List<Press> presses) {
            // Arrange
            Run run = new Run();
            for (Press press : presses) {
                for (int i = 0; i < press.gap(); i++) {
                    run.tick();
                }
                if (run.cast.readiness(press.spell(), run.now) == Readiness.READY) {
                    if (press.spell() == EvokerSpell.FANGS && !press.targets()) {
                        run.cast.noTargets(run.now);
                        run.noticeAt = run.now + EvokerSpell.WARMUP_TICKS;
                        assertThat(run.cast.readiness(press.spell(), run.now))
                                .as("no cooldown after a no-targets press")
                                .isEqualTo(Readiness.READY);
                    } else {
                        run.cast.start(press.spell(), run.now);
                        run.noticeAt = -1;
                        run.starts++;
                        run.lastStart = run.now;
                        run.lastSpell = press.spell();
                    }
                }
            }
            // Act
            for (int i = 0; i < 400; i++) {
                run.tick();
            }
            // Assert
            assertThat(run.performs).as("one perform per start").isEqualTo(run.starts);
            assertThat(run.noticeAt).as("every scheduled notice was delivered").isEqualTo(-1);
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class ReadinessProperties {
        /**
         * For any press sequence, readiness should never be READY for a spell within its interval after its own
         * start, nor for any spell within the casting ticks of the latest start.
         *
         * <p>Method under test: {@link EvokingCast#readiness(EvokerSpell, long)}
         */
        @Property
        void readinessHonorsIntervalsAndCastingTicks(@ForAll("presses") List<Press> presses) {
            // Arrange
            EvokingCast cast = new EvokingCast();
            long now = 0;
            long castingUntil = Long.MIN_VALUE;
            Map<EvokerSpell, Long> readyAt = new EnumMap<>(EvokerSpell.class);
            for (Press press : presses) {
                now += press.gap();
                // Act
                Readiness readiness = cast.readiness(press.spell(), now);
                // Assert
                long spellReadyAt = readyAt.getOrDefault(press.spell(), Long.MIN_VALUE);
                Readiness expected =
                        now < castingUntil ? Readiness.BUSY : now < spellReadyAt ? Readiness.COOLING : Readiness.READY;
                assertThat(readiness).isEqualTo(expected);
                if (readiness == Readiness.READY) {
                    cast.start(press.spell(), now);
                    castingUntil = now + press.spell().castingTicks();
                    readyAt.put(press.spell(), now + press.spell().intervalTicks());
                }
            }
        }
    }
}
