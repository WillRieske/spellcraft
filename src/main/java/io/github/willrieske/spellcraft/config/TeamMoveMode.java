package io.github.willrieske.spellcraft.config;

/** Whether Bound Vexes start team moves by themselves (Parts 6-7 spec section 3.1). */
public enum TeamMoveMode {
    /** The vexes start team moves when a trigger fires; the J and K keys only say so. */
    AUTOMATIC,
    /** No trigger runs; J picks a move and K starts it. */
    MANUAL
}
