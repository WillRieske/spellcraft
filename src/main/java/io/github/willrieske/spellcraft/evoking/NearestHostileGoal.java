package io.github.willrieske.spellcraft.evoking;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.phys.AABB;

/**
 * The patrol goal (Evoking II spec §3.4, third in the target selector): vanilla's nearest-target search, with line of
 * sight, for anything {@code BoundVex.huntable} (hostile, no creeper, no other player's summon) whose feet are inside
 * the guard box and that the vex may take ({@code BoundVex.takesTarget}). It scans on the shared clock ({@code BoundVex.scanDue}), not on vanilla's
 * dice: the six-argument constructor with a random interval of 1 makes the roll always pass (docs/api-notes.md §21).
 * To keep a target it runs {@code super}'s checks (sight: dropped after 60 unseen ticks; they end by re-setting the
 * target, which calls no wolves for an unchanged target) and then {@code BoundVex.keepsTarget}, the wider test for ranged gear (a target that became
 * an ally, died or left reach is dropped), because vanilla's never re-checks the selector.
 *
 * <p>Functional spec §7.1, §7.5.
 */
final class NearestHostileGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final BoundVex vex;

    NearestHostileGoal(BoundVex vex) {
        super(
                vex,
                LivingEntity.class,
                1,
                true,
                false,
                (target, level) -> vex.huntable(target) && vex.inGuardArea(target) && vex.takesTarget(target));
        this.vex = vex;
    }

    /** Only on a scan tick; then vanilla's search, whose roll always passes with a random interval of 1. */
    @Override
    public boolean canUse() {
        return vex.scanDue(vex.scanInterval()) && super.canUse();
    }

    /** The broad phase of the search; the selector keeps only what {@code inGuardArea} accepts. */
    @Override
    protected AABB getTargetSearchArea(double followDistance) {
        return vex.guardArea();
    }

    @Override
    public boolean canContinueToUse() {
        return vex.keepsTarget(mob.getTarget()) && super.canContinueToUse();
    }
}
