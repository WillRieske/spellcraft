package io.github.willrieske.spellcraft.spell.rules;

/**
 * The evoker's two spells with vanilla's timings (spec §4): how long the arms stay up and how long until the same
 * spell can be cast again, both counted from the accepted press.
 *
 * <p>Functional spec §2.2, §2.3.
 */
public enum EvokerSpell {
    /** Fangs: arms up 2 s, castable every 5 s. Functional spec §2.2, §2.3. */
    FANGS(40, 100),
    /** Vexes: arms up 5 s, castable every 17 s. Functional spec §2.2, §2.3. */
    VEXES(100, 340);

    /**
     * Ticks between an accepted press and the perform, shared by both spells (vanilla's cast warmup). Functional spec
     * §2.2, §3.3.
     */
    public static final int WARMUP_TICKS = 20;

    private final int castingTicks;
    private final int intervalTicks;

    EvokerSpell(int castingTicks, int intervalTicks) {
        this.castingTicks = castingTicks;
        this.intervalTicks = intervalTicks;
    }

    /** How long the arms stay up after the press; no other cast is accepted meanwhile. */
    public int castingTicks() {
        return castingTicks;
    }

    /** How long after the press this spell can be pressed again. */
    public int intervalTicks() {
        return intervalTicks;
    }
}
