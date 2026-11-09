package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.jspecify.annotations.Nullable;

/**
 * What the Bound Vex's two scanning target goals ({@link HurtBySummonerSideGoal} and {@link AllyFightGoal}) share: a
 * scan leaves its pick in {@link #found}, {@code start} takes it, and the goal holds it for as long as the vex may
 * ({@code BoundVex.keepsTarget}). The sight rule of {@code TargetGoal} is not used here (spec §3.4 items 1 and 2), so
 * {@code canContinueToUse} never calls {@code super}.
 */
abstract class ScanTargetGoal extends TargetGoal {
    /** The vex this goal belongs to. */
    final BoundVex vex;

    /** The target the last successful {@code canUse} picked. */
    @Nullable
    LivingEntity found;

    ScanTargetGoal(BoundVex vex) {
        super(vex, false);
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    /** {@code TargetGoal.start()} does not set the target (docs/api-notes.md §21), so this does. */
    @Override
    public void start() {
        vex.setTarget(found);
        super.start();
    }

    /** {@code keepsTarget} alone: neither subclass has a sight rule, so {@code super}'s checks are not run. */
    @Override
    public boolean canContinueToUse() {
        return vex.keepsTarget(vex.getTarget());
    }
}
