package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.GrantedAbilities.Plan;
import io.github.willrieske.spellcraft.spell.rules.GrantedAbilities.State;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class GrantedAbilitiesPropertiesTest {
    /** Test model of the plumbing: what applying {@code plan} leaves behind (vanilla mode defaults on a reset). */
    private static State after(State s, Plan p) {
        boolean modeFlies = s.mode() == PlayerMode.CREATIVE || s.mode() == PlayerMode.SPECTATOR;
        boolean mayfly = s.mayfly();
        boolean instabuild = s.instabuild();
        boolean flying = s.flying();
        if (p.reset()) {
            mayfly = modeFlies;
            instabuild = s.mode() == PlayerMode.CREATIVE;
            flying = s.mode() == PlayerMode.SPECTATOR || (modeFlies && flying);
        }
        return new State(
                s.mode(),
                s.infinity(),
                s.ghostrail(),
                p.infinityMarked(),
                p.ghostrailMarked(),
                mayfly || p.mayfly(),
                instabuild || p.instabuild(),
                flying || p.flying());
    }

    /**
     * Whether the game mode leaves abilities to granted sources (survival and adventure, not creative or spectator).
     */
    private static boolean granting(PlayerMode mode) {
        return mode == PlayerMode.SURVIVAL || mode == PlayerMode.ADVENTURE;
    }

    /**
     * For any state, one plan should settle it: deciding again on the state the plan leaves behind changes nothing.
     *
     * <p>Method under test: {@link GrantedAbilities#decide(State)}
     */
    @Property
    void oneDecisionSettlesTheState(
            @ForAll PlayerMode mode,
            @ForAll boolean infinity,
            @ForAll boolean ghostrail,
            @ForAll boolean iMark,
            @ForAll boolean gMark,
            @ForAll boolean mayfly,
            @ForAll boolean instabuild,
            @ForAll boolean flying) {
        State s = new State(mode, infinity, ghostrail, iMark, gMark, mayfly, instabuild, flying);
        Plan again = GrantedAbilities.decide(after(s, GrantedAbilities.decide(s)));
        assertThat(again.changesAbilities()).isFalse();
    }

    /**
     * For any state, a source that is still active should find its abilities in place after the plan, whatever the
     * other source did: ending one never removes what the other grants.
     *
     * <p>Method under test: {@link GrantedAbilities#decide(State)}
     */
    @Property
    void anActiveSourceKeepsItsAbilities(
            @ForAll PlayerMode mode,
            @ForAll boolean infinity,
            @ForAll boolean ghostrail,
            @ForAll boolean iMark,
            @ForAll boolean gMark,
            @ForAll boolean mayfly,
            @ForAll boolean instabuild,
            @ForAll boolean flying) {
        // Arrange
        State s = new State(mode, infinity, ghostrail, iMark, gMark, mayfly, instabuild, flying);
        // Act
        State result = after(s, GrantedAbilities.decide(s));
        // Assert
        if (infinity && granting(mode)) {
            assertThat(result.mayfly() && result.instabuild()).isTrue();
        }
        if (ghostrail && granting(mode)) {
            assertThat(result.mayfly() && result.flying()).isTrue();
        }
    }

    /** Properties that bind {@code decide} itself for the two game modes that own their abilities. */
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class CreativeAndSpectator {
        /** The two game modes whose abilities the game mode owns. */
        @Provide
        Arbitrary<PlayerMode> ownModes() {
            return Arbitraries.of(PlayerMode.CREATIVE, PlayerMode.SPECTATOR);
        }

        /**
         * For any creative or spectator state (any flags, any marks), decide should never set mayfly, instabuild or
         * flying: those modes' own abilities are never granted over.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Property
        void neverSetsAFlag(
                @ForAll("ownModes") PlayerMode mode,
                @ForAll boolean infinity,
                @ForAll boolean ghostrail,
                @ForAll boolean iMark,
                @ForAll boolean gMark,
                @ForAll boolean mayfly,
                @ForAll boolean instabuild,
                @ForAll boolean flying) {
            // Arrange
            State s = new State(mode, infinity, ghostrail, iMark, gMark, mayfly, instabuild, flying);
            // Act
            Plan p = GrantedAbilities.decide(s);
            // Assert
            assertThat(p.mayfly() || p.instabuild() || p.flying()).isFalse();
        }

        /**
         * For any creative or spectator state with neither mark set, decide should do nothing at all (the plan is
         * exactly {@code NOTHING}): it never resets what it did not grant.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Property
        void doesNothingWithoutAMark(
                @ForAll("ownModes") PlayerMode mode,
                @ForAll boolean infinity,
                @ForAll boolean ghostrail,
                @ForAll boolean mayfly,
                @ForAll boolean instabuild,
                @ForAll boolean flying) {
            // Arrange
            State s = new State(mode, infinity, ghostrail, false, false, mayfly, instabuild, flying);
            // Act & Assert
            assertThat(GrantedAbilities.decide(s)).isEqualTo(new Plan(false, false, false, false, false, false));
        }

        /**
         * For a creative or spectator player who starts with the game mode's own abilities and any marks, applying the
         * plan should leave mayfly set, and instabuild too in creative.
         *
         * <p>Method under test: {@link GrantedAbilities#decide(State)}
         */
        @Property
        void keepsItsOwnAbilities(
                @ForAll("ownModes") PlayerMode mode,
                @ForAll boolean infinity,
                @ForAll boolean ghostrail,
                @ForAll boolean iMark,
                @ForAll boolean gMark) {
            // Arrange
            boolean creative = mode == PlayerMode.CREATIVE;
            State s = new State(mode, infinity, ghostrail, iMark, gMark, true, creative, !creative);
            // Act
            State result = after(s, GrantedAbilities.decide(s));
            // Assert
            assertThat(result.mayfly()).isTrue();
            assertThat(result.instabuild()).isEqualTo(creative);
        }
    }

    /**
     * For any state, a mark should be set only while its source is active in survival or adventure.
     *
     * <p>Method under test: {@link GrantedAbilities#decide(State)}
     */
    @Property
    void marksFollowActiveSources(
            @ForAll PlayerMode mode,
            @ForAll boolean infinity,
            @ForAll boolean ghostrail,
            @ForAll boolean iMark,
            @ForAll boolean gMark,
            @ForAll boolean mayfly,
            @ForAll boolean instabuild,
            @ForAll boolean flying) {
        Plan p =
                GrantedAbilities.decide(new State(mode, infinity, ghostrail, iMark, gMark, mayfly, instabuild, flying));
        assertThat(p.infinityMarked()).isEqualTo(infinity && granting(mode));
        assertThat(p.ghostrailMarked()).isEqualTo(ghostrail && granting(mode));
    }
}
