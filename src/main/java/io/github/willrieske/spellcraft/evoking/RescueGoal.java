package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps a rescuing Bound Vex at its summoner's side (Parts 4–5 spec §6.2) for as long as {@link VexRescues} names it
 * the rescuer, the tail included. Priority −1 on {@code MOVE} and {@code LOOK}: it replaces whatever the vex was
 * doing (a flight from a creeper, a cure, a charge), and that goal's own {@code stop} cleans up after it. The push
 * itself is not this goal's work.
 */
final class RescueGoal extends Goal {
    /** The rescuing vex. */
    private final BoundVex vex;

    /**
     * Creates the goal for {@code vex}.
     *
     * @param vex the vex that may be named a rescuer
     */
    RescueGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return VexRescues.rescuedBy(vex).isPresent();
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
        vex.getMoveControl().setWait();
    }

    @Override
    public void tick() {
        VexRescues.rescuedBy(vex).ifPresent(player -> {
            VexRescues.placeBeside(vex, player);
            vex.getLookControl().setLookAt(player);
        });
    }
}
