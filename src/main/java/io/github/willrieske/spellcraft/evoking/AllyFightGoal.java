package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.Nullable;

/**
 * Vexes join fights their allies start (Evoking II spec §3.4, second in the target selector): on each scan, the
 * nearest hostile ({@code Enemy}) that an ally mob inside the guard area (an iron golem, a wolf, another Bound Vex; never
 * this vex itself, {@code BoundVex.alliesInGuardArea}) has as its target, if the vex may take it
 * ({@code BoundVex.keepsTarget}).
 */
final class AllyFightGoal extends TargetGoal {
    private final BoundVex vex;

    @Nullable
    private LivingEntity found;

    AllyFightGoal(BoundVex vex) {
        super(vex, false);
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!vex.scanDue(BoundVex.SCAN_INTERVAL)) {
            return false;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        found = null;
        double nearest = Double.MAX_VALUE;
        for (Mob ally : vex.alliesInGuardArea(owner)) {
            LivingEntity target = ally.getTarget();
            if (target instanceof Enemy && vex.keepsTarget(target)) {
                double distance = vex.distanceToSqr(target);
                if (distance < nearest) {
                    nearest = distance;
                    found = target;
                }
            }
        }
        return found != null;
    }

    /** {@code TargetGoal.start()} does not set the target (docs/api-notes.md §21), so this does. */
    @Override
    public void start() {
        vex.setTarget(found);
        super.start();
    }

    /** {@code keepsTarget} alone: this goal has no sight rule (spec §3.4 item 2), so {@code super}'s checks are not run. */
    @Override
    public boolean canContinueToUse() {
        return vex.keepsTarget(vex.getTarget());
    }
}
