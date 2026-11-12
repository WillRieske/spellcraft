package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.rules.CasterRules;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A Caster with a target keeps {@link CasterRules#KEEP_MIN} to {@link CasterRules#KEEP_MAX} blocks from it, looking at
 * it, and never melees or shoots (Parts 6-7 spec §6.2). Priority 3: above the follow goal, below the team move, job and
 * casting goals.
 *
 * <p>Functional spec §12.2.
 */
final class CasterGoal extends Goal {
    /** The distance the Caster flies to when it has to move: the middle of the band. */
    private static final double HOLD_DISTANCE = (CasterRules.KEEP_MIN + CasterRules.KEEP_MAX) / 2.0;

    /** The Caster this goal flies. */
    private final BoundVex vex;

    /**
     * Creates the goal for one vex (it only runs while the vex {@code isCaster()}).
     *
     * @param vex the vex
     */
    CasterGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = vex.getTarget();
        return vex.isCaster() && target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = vex.getTarget();
        if (target == null) {
            return;
        }
        TeamFlight.lookAt(vex, target);
        Vec3 spot = TeamFlight.ringPoint(target.position(), vex.position(), HOLD_DISTANCE, target.getEyeY());
        boolean hold = CasterRules.approach(vex.distanceTo(target)) == CasterRules.Approach.HOLD;
        // Inside the band the Caster only damps its own drift (settling at its own position); out of it, it flies to
        // the spot and settles there (TeamFlight.holdAt)
        TeamFlight.holdAt(vex, hold ? vex.position() : spot, Vec3.ZERO);
    }
}
