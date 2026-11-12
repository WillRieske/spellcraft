package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.effect.ModEffects;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * While a scroll mob has Ghostrail and a target, flies straight at the target's middle at {@link #SPEED} blocks per
 * tick (Parts 6-7 spec §7.3). Pathfinding would walk around the wall Ghostrail is for, and {@code Mob.serverAiStep}
 * overwrites a wanted position set from outside the goals, so the goal stops the navigation and sets the velocity
 * itself; the mob has no gravity and no collision while it carries the effect ({@code Ghostrail.tick}), so it can rise
 * and sink.
 *
 * <p>Functional spec §14.8, §15.2.
 */
public final class GhostrailChaseGoal extends Goal {
    /** Blocks per tick. */
    static final double SPEED = 0.3;

    /** The mob that chases. */
    private final Mob mob;

    /**
     * Creates the goal for one mob (it only runs while the mob has Ghostrail and a target).
     *
     * @param mob the mob
     */
    GhostrailChaseGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return mob.hasEffect(ModEffects.GHOSTRAIL) && target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getNavigation().stop();
        Vec3 gap = target.getBoundingBox()
                .getCenter()
                .subtract(mob.getBoundingBox().getCenter());
        double length = gap.length();
        if (length > 1.0E-3) {
            mob.setDeltaMovement(gap.scale(Math.min(SPEED, length) / length));
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }
}
