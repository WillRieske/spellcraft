package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.willrieske.spellcraft.spell.rules.GrantedAbilities.Plan;
import io.github.willrieske.spellcraft.spell.rules.GrantedAbilities.State;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Functional spec §14.2, §14.6, §14.7. */
class GrantedAbilitiesTest {
    /** The plan that changes nothing and keeps no mark. */
    private static final Plan NOTHING = new Plan(false, false, false, false, false, false);

    @Nested
    class Decide {
        /**
         * When only Infinity is involved (the old table of {@code InfinityAbilities}), decide should grant, hold,
         * release or revoke exactly as that table did.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @ParameterizedTest(name = "{0} infinity={1} marked={2} mayfly={3} instabuild={4} -> {5}")
        @MethodSource("infinityRows")
        void infinityRowsKeepTheirMeaning(
                PlayerMode mode, boolean infinity, boolean marked, boolean mayfly, boolean instabuild, Plan expected) {
            assertThat(GrantedAbilities.decide(
                            new State(mode, infinity, false, marked, false, mayfly, instabuild, false)))
                    .isEqualTo(expected);
        }

        /** The rows of the old Infinity table: mode, active, marked, mayfly, instabuild and the expected plan. */
        static Stream<Arguments> infinityRows() {
            Plan grant = new Plan(false, true, true, false, true, false);
            Plan hold = new Plan(false, false, false, false, true, false);
            Plan revoke = new Plan(true, false, false, false, false, false);
            return Stream.of(
                    Arguments.of(PlayerMode.SURVIVAL, true, false, false, false, grant),
                    Arguments.of(PlayerMode.ADVENTURE, true, false, false, false, grant),
                    Arguments.of(PlayerMode.SURVIVAL, true, true, true, true, hold),
                    Arguments.of(PlayerMode.ADVENTURE, true, true, true, true, hold),
                    // Vanilla reset a flag (a game mode round trip): grant again.
                    Arguments.of(PlayerMode.SURVIVAL, true, true, false, true, grant),
                    Arguments.of(PlayerMode.SURVIVAL, true, true, true, false, grant),
                    // Flags from elsewhere while Infinity is active: take ownership so removal revokes them.
                    Arguments.of(PlayerMode.SURVIVAL, true, false, true, true, grant),
                    // Creative and spectator own their flags: drop the mark, change nothing.
                    Arguments.of(PlayerMode.CREATIVE, true, true, true, true, NOTHING),
                    Arguments.of(PlayerMode.SPECTATOR, true, true, true, false, NOTHING),
                    Arguments.of(PlayerMode.CREATIVE, true, false, true, true, NOTHING),
                    // Infinity gone: revoke only what we granted.
                    Arguments.of(PlayerMode.SURVIVAL, false, true, true, true, revoke),
                    Arguments.of(PlayerMode.CREATIVE, false, true, true, true, revoke),
                    Arguments.of(PlayerMode.SURVIVAL, false, false, true, false, NOTHING),
                    Arguments.of(PlayerMode.ADVENTURE, false, false, false, false, NOTHING));
        }

        /**
         * When Ghostrail is involved, decide should grant flight (mayfly and flying, no instabuild), keep it while it
         * is whole, and re-assert flying when the player stopped flying.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @ParameterizedTest(name = "{0} marked={1} mayfly={2} flying={3} -> {4}")
        @MethodSource("ghostrailRows")
        void ghostrailRows(PlayerMode mode, boolean marked, boolean mayfly, boolean flying, Plan expected) {
            assertThat(GrantedAbilities.decide(new State(mode, false, true, false, marked, mayfly, false, flying)))
                    .isEqualTo(expected);
        }

        /** The Ghostrail rows: mode, marked, mayfly, flying and the expected plan. */
        static Stream<Arguments> ghostrailRows() {
            return Stream.of(
                    Arguments.of(
                            PlayerMode.SURVIVAL, false, false, false, new Plan(false, true, false, true, false, true)),
                    Arguments.of(
                            PlayerMode.ADVENTURE, false, false, false, new Plan(false, true, false, true, false, true)),
                    // The player toggled flying off: put it back, every tick.
                    Arguments.of(
                            PlayerMode.SURVIVAL, true, true, false, new Plan(false, true, false, true, false, true)),
                    Arguments.of(
                            PlayerMode.SURVIVAL, true, true, true, new Plan(false, false, false, false, false, true)),
                    // Creative and spectator own their flags.
                    Arguments.of(PlayerMode.CREATIVE, false, true, false, NOTHING),
                    Arguments.of(PlayerMode.SPECTATOR, false, true, true, NOTHING));
        }

        /**
         * When Ghostrail ends, decide should reset the abilities (and clear its mark); with Infinity still active the
         * reset is followed by Infinity's grant, so Infinity keeps its flight and instant building.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Test
        void ghostrailEndingKeepsInfinitys() {
            // Arrange
            State s = new State(PlayerMode.SURVIVAL, true, false, true, true, true, true, true);
            // Act & Assert
            assertThat(GrantedAbilities.decide(s)).isEqualTo(new Plan(true, true, true, false, true, false));
        }

        /**
         * When Infinity ends with Ghostrail still active, decide should reset, then grant mayfly and flying again.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Test
        void infinityEndingKeepsGhostrails() {
            // Arrange
            State s = new State(PlayerMode.SURVIVAL, false, true, true, true, true, true, true);
            // Act & Assert
            assertThat(GrantedAbilities.decide(s)).isEqualTo(new Plan(true, true, false, true, false, true));
        }

        /**
         * When both start at once, decide should grant all three flags and set both marks.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Test
        void bothAtOnce() {
            assertThat(GrantedAbilities.decide(
                            new State(PlayerMode.SURVIVAL, true, true, false, false, false, false, false)))
                    .isEqualTo(new Plan(false, true, true, true, true, true));
        }

        /**
         * When Ghostrail ends in creative (its mark was set in survival), decide should reset to creative's own
         * abilities, which keep flight.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Test
        void endingInCreativeResetsToCreative() {
            assertThat(GrantedAbilities.decide(
                            new State(PlayerMode.CREATIVE, false, false, false, true, true, true, true)))
                    .isEqualTo(new Plan(true, false, false, false, false, false));
        }

        /**
         * When the state has no mode, the State constructor should refuse.
         *
         * <p>Method under test: {@link GrantedAbilities.State}
         */
        @Test
        void stateRequiresAMode() {
            assertThatThrownBy(() -> new State(null, true, false, false, false, false, false, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("mode");
        }
    }

    @Nested
    class ChangesAbilities {
        /**
         * When a plan resets or sets any flag, changesAbilities should be true; a plan that only moves marks, false.
         *
         * <p>Method under test: {@link GrantedAbilities.Plan#changesAbilities()}
         */
        @Test
        void isTrueForAnyFlagChange() {
            assertThat(new Plan(true, false, false, false, false, false).changesAbilities())
                    .isTrue();
            assertThat(new Plan(false, true, false, false, false, false).changesAbilities())
                    .isTrue();
            assertThat(new Plan(false, false, true, false, false, false).changesAbilities())
                    .isTrue();
            assertThat(new Plan(false, false, false, true, false, false).changesAbilities())
                    .isTrue();
            assertThat(new Plan(false, false, false, false, true, true).changesAbilities())
                    .isFalse();
        }
    }
}
