package io.github.willrieske.spellcraft.client.evoking;

/**
 * What an entity is casting, for the casting look (Parts 6-7 spec §6.4); client-only, separate from the packet's
 * {@code EvokerSpell}.
 */
public enum CastLook {
    /** A fang cast: the evoker's dark fang color. */
    FANGS,
    /** A vex summon: the evoker's pale vex color. */
    VEXES,
    /** A scroll cast by a Caster or a villager ghost: purple. */
    SCROLL
}
