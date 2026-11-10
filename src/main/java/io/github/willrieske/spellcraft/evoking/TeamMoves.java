package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * Team moves of a summoner's Bound Vexes (Parts 6-7 spec section 2). This slice holds the test for a free vex; the
 * record, the triggers and the rest arrive in the next tasks.
 */
public final class TeamMoves {
    /** A vex farther than this from its summoner is not free (spec section 2.2). */
    public static final double FREE_RANGE = 32.0;

    private TeamMoves() {}

    /**
     * Whether {@code vex} may join a move of {@code owner} (spec section 2.2): alive, theirs, in their level, within
     * {@link #FREE_RANGE} blocks of them, following (not staying), in no move, in no job ({@link Jobs#busy}) and not
     * the player's rescuer.
     *
     * @param vex a Bound Vex
     * @param owner the summoner
     * @return true if it is free
     */
    public static boolean isFree(BoundVex vex, ServerPlayer owner) {
        // Part 7: and not a Caster (a Caster is free only for a Raid)
        return vex.isAlive()
                && vex.isSummonedBy(owner.getUUID())
                && vex.level().equals(owner.level())
                && !vex.isStaying()
                && vex.teamMove() == TeamMove.NONE
                && vex.distanceTo(owner) <= FREE_RANGE
                && !Jobs.busy(vex)
                && !VexRescues.rescuer(owner).map(vex::equals).orElse(false);
    }

    /**
     * The free vexes of {@code owner} in their level.
     *
     * @param owner the summoner
     * @return the free vexes, in query order
     */
    public static List<BoundVex> freeVexes(ServerPlayer owner) {
        return owner.level()
                .getEntitiesOfClass(
                        BoundVex.class, owner.getBoundingBox().inflate(FREE_RANGE), vex -> isFree(vex, owner));
    }
}
