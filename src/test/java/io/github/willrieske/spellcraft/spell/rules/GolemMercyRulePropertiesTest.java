package io.github.willrieske.spellcraft.spell.rules;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;

class GolemMercyRulePropertiesTest {
    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Clamp {
        /**
         * For any health above 25 and any damage, the health after the clamped hit should never be under 25, and the
         * clamped damage should never exceed the asked damage or go negative.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @Property
        void neverTakesAGolemUnderTwentyFive(
                @ForAll @FloatRange(min = 25.0f, max = 100.0f) float health,
                @ForAll @FloatRange(min = 0.0f, max = 200.0f) float damage) {
            // Act
            float dealt = GolemMercyRule.clamp(health, damage);
            // Assert
            assertThat(dealt).isBetween(0.0f, damage);
            assertThat(health - dealt).isGreaterThanOrEqualTo(GolemMercyRule.MERCY_HEALTH);
        }

        /**
         * For any health at or under 25, clamp should answer 0 whatever the damage.
         *
         * <p>Method under test: {@link GolemMercyRule#clamp(float, float)}
         */
        @Property
        void aGolemAtOrUnderTwentyFiveTakesNothing(
                @ForAll @FloatRange(min = -10.0f, max = 25.0f) float health,
                @ForAll @FloatRange(min = 0.0f, max = 200.0f) float damage) {
            assertThat(GolemMercyRule.clamp(health, damage)).isZero();
        }
    }

    // jqwik refuses static @Group classes, so ClassCanBeStatic cannot apply.
    @Group
    @SuppressWarnings("ClassCanBeStatic")
    class Forgives {
        /**
         * For any golem above 25 hit hard enough to reach 25 (damage at least health − 25), the health left after the
         * clamped hit should be forgiven: the clamp and the forgiveness line agree.
         *
         * <p>Method under test: {@link GolemMercyRule#forgives(float)}
         */
        @Property
        void aClampedHitThatReachesTheLineIsForgiven(
                @ForAll @FloatRange(min = 25.0f, max = 100.0f) float health,
                @ForAll @FloatRange(min = 0.0f, max = 200.0f) float extra) {
            // Arrange
            float damage = (health - GolemMercyRule.MERCY_HEALTH) + extra;
            // Act
            float after = health - GolemMercyRule.clamp(health, damage);
            // Assert
            assertThat(GolemMercyRule.forgives(after)).isTrue();
        }

        /**
         * For any hit that leaves the golem strictly above 25, forgives should answer false.
         *
         * <p>Method under test: {@link GolemMercyRule#forgives(float)}
         */
        @Property
        void aHitThatLeavesMoreThanTwentyFiveIsNotForgiven(
                @ForAll @FloatRange(min = 26.0f, max = 100.0f) float health,
                @ForAll @FloatRange(min = 0.0f, max = 0.5f) float fraction) {
            // Arrange: damage strictly less than the room above 25
            float damage = (health - GolemMercyRule.MERCY_HEALTH) * fraction;
            // Act
            float after = health - GolemMercyRule.clamp(health, damage);
            // Assert
            assertThat(GolemMercyRule.forgives(after)).isFalse();
        }
    }
}
