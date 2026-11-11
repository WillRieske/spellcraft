package io.github.willrieske.spellcraft.spell.rules;

import java.util.Locale;
import java.util.Optional;

/**
 * A team move of the Bound Vexes (Parts 6–7 spec §2). {@link #NONE} is last on purpose: the packet argument of a
 * team-move request is the ordinal (spec §3.2, "Shield 0 … Raid 3"), and {@link #NONE} is what a vex that takes part
 * in no move carries (spec §2.1), so it can never be requested.
 */
public enum TeamMove {
    /** A few vexes bash a big enemy with conjured shields (spec §4.1). */
    SHIELD,
    /** Three vexes fire three arrows at once at a strong enemy that hunts one of them (spec §4.2). */
    MULTISHOT,
    /** Up to three vexes circle a hurt summoner or ally and shove and deflect (spec §4.3). */
    EVADER,
    /** All twelve vexes gear up, line up and charge a giant (spec §4.4). */
    RAID,
    /** No move. */
    NONE;

    private static final TeamMove[] PLAYABLE = {SHIELD, MULTISHOT, EVADER, RAID};

    /**
     * The move's name in lower case: the end of its lang keys ({@code message.spellcraft.team_move_<id>},
     * {@code team_move.spellcraft.<id>}).
     *
     * @return the id
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The packet argument for this move.
     *
     * @return the ordinal, 0 to 3 for a real move
     */
    @SuppressWarnings("EnumOrdinal") // the ordinal is the packet argument by design (spec §3.2); NONE is last
    public byte arg() {
        return (byte) ordinal();
    }

    /**
     * The next move in the J key's cycle: Shield, Multishot, Evader, Raid, Shield.
     *
     * @return the next move; {@link #SHIELD} after {@link #NONE}
     */
    @SuppressWarnings("EnumOrdinal") // indexes PLAYABLE, whose order is the declaration order of the four moves
    public TeamMove next() {
        return this == NONE ? SHIELD : PLAYABLE[(ordinal() + 1) % PLAYABLE.length];
    }

    /**
     * The move a packet argument names.
     *
     * @param arg the byte from the request, widened
     * @return the move, or empty for an argument outside 0 to 3
     */
    public static Optional<TeamMove> fromArg(int arg) {
        return arg >= 0 && arg < PLAYABLE.length ? Optional.of(PLAYABLE[arg]) : Optional.empty();
    }
}
