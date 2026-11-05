package io.github.willrieske.spellcraft.spell.rules;

import java.util.OptionalLong;

/**
 * Decides which block a trailing player just left. One per player. Positions are packed longs (the plumbing uses
 * {@code BlockPos.asLong()}); "grounded" is supplied by the plumbing as
 * {@code onGround() && !isPassenger() && !isSpectator()}. A block counts as stood on if the player was grounded at
 * any point while on it, so a jump (airborne first, then off the block) still lights the take-off block.
 */
public final class TrailTracker {
    private boolean observed;
    private long lastPos;
    private boolean lastGrounded;

    /**
     * Records where the player is now and returns the block to light: the previous position, when there was one,
     * the player was grounded at some point while on it, and the position changed. Otherwise empty.
     *
     * @param pos the player's current block position, packed
     * @param grounded whether the player is on the ground and not riding or spectating
     * @return the previous position to light, or empty
     */
    public OptionalLong observe(long pos, boolean grounded) {
        boolean moved = observed && pos != lastPos;
        OptionalLong toLight = moved && lastGrounded ? OptionalLong.of(lastPos) : OptionalLong.empty();
        // Leaving the ground before leaving the block must not forget that the player stood here.
        lastGrounded = (observed && !moved) ? lastGrounded || grounded : grounded;
        observed = true;
        lastPos = pos;
        return toLight;
    }

    /** Forgets the last observation (the effect started, or the player changed level). */
    public void reset() {
        observed = false;
    }
}
