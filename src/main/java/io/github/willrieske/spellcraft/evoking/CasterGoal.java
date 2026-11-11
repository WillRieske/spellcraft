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
 */
final class CasterGoal extends Goal {
    /** The distance the Caster flies to when it has to move: the middle of the band. */
    private static final double HOLD_DISTANCE = (CasterRules.KEEP_MIN + CasterRules.KEEP_MAX) / 2.0;

    /** Inside this distance from the spot the push is a spring to the spot instead of a flight to it. */
    private static final double SETTLE_RANGE = 3.0;

    /** The spring's pull toward the spot, per block of gap (the value {@code RaidMove} uses for the same job). */
    private static final double SETTLE_POSITION_GAIN = 0.06;

    /** The damping on the vex's own velocity (the value {@code RaidMove} uses for the same job). */
    private static final double SETTLE_VELOCITY_GAIN = 0.3;

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
        Vec3 gap = spot.subtract(vex.position());
        boolean hold = CasterRules.approach(vex.distanceTo(target)) == CasterRules.Approach.HOLD;
        if (!hold && gap.length() > SETTLE_RANGE) {
            TeamFlight.flyTo(vex, spot, TeamFlight.NORMAL);
            return;
        }
        // A vex only accelerates toward its wanted point and coasts, so a plain "fly to the spot" overshoots the
        // band: near the spot (or inside the band) the push is a spring to the spot, none when holding, with
        // damping on the vex's own velocity, as RaidMove.closeIn does.
        Vec3 spring = hold ? Vec3.ZERO : gap.scale(SETTLE_POSITION_GAIN);
        TeamFlight.steer(vex, spring.subtract(vex.getDeltaMovement().scale(SETTLE_VELOCITY_GAIN)));
    }
}
