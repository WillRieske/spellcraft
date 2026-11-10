package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A Bound Vex's part in a team move (Parts 6-7 spec section 2.5). Priority 1 on {@code MOVE} and {@code LOOK}: above
 * the job, charge, ranged and follow goals, below the flee and dodge goals (0) and the rescue (-1), which pause it.
 * While {@link TeamMoves#membership} lists the vex it keeps the kit on ({@link TeamMoveKits#restore}: when anything
 * dismisses a kit slot, such as a preempted job goal's own {@code stop}, the kit is conjured again). Its {@code stop}
 * dismisses the kit only when the record no longer lists the vex, so a pause keeps the kit. {@link #tick} runs the
 * member's part of the running move (one class per move).
 */
public final class TeamMoveGoal extends Goal {
    /** The vex this goal belongs to. */
    private final BoundVex vex;

    /**
     * Creates the goal for one vex.
     *
     * @param vex the Bound Vex
     */
    TeamMoveGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return TeamMoves.membership(vex).isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        TeamMoves.membership(vex).ifPresent(membership -> TeamMoveKits.restore(vex, membership.member().kit));
    }

    @Override
    public void tick() {
        TeamMoves.Membership membership = TeamMoves.membership(vex).orElse(null);
        // A summoner who is away ends the move on the next TeamMoves.tick; the vex does nothing until then
        if (membership == null || vex.summoner().isEmpty() || !(vex.level() instanceof ServerLevel level)) {
            return;
        }
        TeamMoveKits.restore(vex, membership.member().kit);
        switch (membership.record().move) {
            case SHIELD -> ShieldMove.tick(vex, membership.record(), membership.member(), level);
            case MULTISHOT -> MultishotMove.tick(vex, membership.record(), membership.member(), level);
            case EVADER -> EvaderMove.tick(vex, membership.record(), membership.member(), level);
            default -> {
                // the other moves are added by the tasks that build them
            }
        }
    }

    @Override
    public void stop() {
        if (TeamMoves.membership(vex).isEmpty()) {
            TeamMoves.release(vex);
        }
    }
}
