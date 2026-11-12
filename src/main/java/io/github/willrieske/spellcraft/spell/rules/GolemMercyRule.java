package io.github.willrieske.spellcraft.spell.rules;

/**
 * Mercy for iron golems (Evoking II spec §2.3): the player's side never takes a golem under {@link #MERCY_HEALTH}, and
 * a golem left there forgives. Pure arithmetic; {@code spell.GolemMercy} applies it to the damage events.
 *
 * <p>Functional spec §4.4, §4.5.
 */
public final class GolemMercyRule {
    /** The health a golem keeps at least, against the player's side. Functional spec §4.4, §4.5. */
    public static final float MERCY_HEALTH = 25.0F;

    private GolemMercyRule() {}

    /**
     * The damage to deal: {@code min(damage, max(0, health − 25))}. Total, because it runs inside a damage event: a
     * negative or NaN damage counts as 0, a NaN health as no room, and infinities follow the arithmetic (infinite
     * damage lands the whole room; infinite health leaves the hit whole).
     *
     * @param health the golem's health before the hit
     * @param damage the damage the hit would deal
     * @return the damage that leaves the golem at or above {@link #MERCY_HEALTH}, or 0 if it already is
     */
    public static float clamp(float health, float damage) {
        if (Float.isNaN(health) || Float.isNaN(damage)) {
            return 0.0F;
        }
        float room = Math.max(0.0F, health - MERCY_HEALTH);
        return Math.min(Math.max(0.0F, damage), room);
    }

    /**
     * Whether a golem at {@code health} is at or under the mercy line, so it stops being angry. NaN is not.
     *
     * @param health the golem's health after the hit
     * @return true at or under {@link #MERCY_HEALTH}
     */
    public static boolean forgives(float health) {
        return health <= MERCY_HEALTH;
    }
}
