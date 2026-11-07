package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.EvokingCast.Due;
import io.github.willrieske.spellcraft.spell.rules.EvokingCast.Readiness;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class EvokingCastPropertiesTest {
    /** One key press at a time: which spell, and whether fangs found targets (ignored for vexes). */
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

    private static void assertNoticeOnTime(long now, long noticeAt) {
        assertThat(now)
                .as("a notice comes exactly 20 ticks after the latest no-targets press, and"
                        + " only if no start dropped it")
                .isEqualTo(noticeAt);
    }

    /**
     * For any sequence of presses, each one handled as the caster does (readiness, then start or noTargets), and
     * with due asked every tick, each PERFORM should come exactly once, exactly 20 ticks after its start, and
     * isCasting should be false once the casting ticks have passed.
     *
     * <p>Method under test: {@link EvokingCast#due(long)}
     */
    @Property
    void everyStartPerformsOnceAfterTheWarmup(@ForAll("presses") List<Press> presses) {
        // Arrange
        EvokingCast cast = new EvokingCast();
        long now = 0;
        int performs = 0;
        int starts = 0;
        long lastStart = Long.MIN_VALUE;
        long noticeAt = -1;
        EvokerSpell lastSpell = EvokerSpell.FANGS;
        for (Press press : presses) {
            for (int i = 0; i < press.gap(); i++) {
                now++;
                // Act
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
                    assertNoticeOnTime(now, noticeAt);
                    noticeAt = -1;
                }
                assertThat(cast.isCasting(now)).isEqualTo(now < lastStart + lastSpell.castingTicks());
            }
            if (cast.readiness(press.spell(), now) == Readiness.READY) {
                if (press.spell() == EvokerSpell.FANGS && !press.targets()) {
                    cast.noTargets(now);
                    noticeAt = now + EvokerSpell.WARMUP_TICKS;
                    assertThat(cast.readiness(press.spell(), now))
                            .as("no cooldown after a no-targets press")
                            .isEqualTo(Readiness.READY);
                } else {
                    cast.start(press.spell(), now);
                    noticeAt = -1;
                    starts++;
                    lastStart = now;
                    lastSpell = press.spell();
                }
            }
        }
        for (int i = 0; i < 400; i++) {
            now++;
            Optional<Due> due = cast.due(now);
            if (due.isPresent() && due.get().kind() == Due.Kind.PERFORM) {
                performs++;
            }
            if (due.isPresent() && due.get().kind() == Due.Kind.NOTIFY_NO_TARGETS) {
                assertNoticeOnTime(now, noticeAt);
                noticeAt = -1;
            }
        }
        assertThat(performs).as("one perform per start").isEqualTo(starts);
        assertThat(noticeAt).as("every scheduled notice was delivered").isEqualTo(-1);
    }

    /**
     * For any press sequence, readiness should never be READY for a spell within its interval after its own start,
     * nor for any spell within the casting ticks of the latest start.
     *
     * <p>Method under test: {@link EvokingCast#readiness(EvokerSpell, long)}
     */
    @Property
    void readinessHonorsIntervalsAndCastingTicks(@ForAll("presses") List<Press> presses) {
        // Arrange
        EvokingCast cast = new EvokingCast();
        long now = 0;
        long castingUntil = Long.MIN_VALUE;
        long fangsReadyAt = Long.MIN_VALUE;
        long vexesReadyAt = Long.MIN_VALUE;
        for (Press press : presses) {
            now += press.gap();
            // Act
            Readiness readiness = cast.readiness(press.spell(), now);
            // Assert
            long readyAt = press.spell() == EvokerSpell.FANGS ? fangsReadyAt : vexesReadyAt;
            Readiness expected =
                    now < castingUntil ? Readiness.BUSY : now < readyAt ? Readiness.COOLING : Readiness.READY;
            assertThat(readiness).isEqualTo(expected);
            if (readiness == Readiness.READY) {
                cast.start(press.spell(), now);
                castingUntil = now + press.spell().castingTicks();
                if (press.spell() == EvokerSpell.FANGS) {
                    fangsReadyAt = now + press.spell().intervalTicks();
                } else {
                    vexesReadyAt = now + press.spell().intervalTicks();
                }
            }
        }
    }
}
