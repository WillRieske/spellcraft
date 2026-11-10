package io.github.willrieske.spellcraft.evoking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Vexes join fights their allies start (Evoking II spec §3.4, second in the target selector): on each scan, the
 * nearest {@code BoundVex.huntable} mob (hostile, no creeper, no other player's summon) that an ally mob inside the
 * guard area (an iron golem, a wolf, another Bound Vex; never this vex itself, {@code BoundVex.alliesInGuardArea}) has
 * as its target, if the vex may take it ({@code BoundVex.takesTarget}).
 */
final class AllyFightGoal extends ScanTargetGoal {
    AllyFightGoal(BoundVex vex) {
        super(vex);
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
            if (target != null && vex.huntable(target) && vex.takesTarget(target)) {
                double distance = vex.distanceToSqr(target);
                if (distance < nearest) {
                    nearest = distance;
                    found = target;
                }
            }
        }
        return found != null;
    }
}
