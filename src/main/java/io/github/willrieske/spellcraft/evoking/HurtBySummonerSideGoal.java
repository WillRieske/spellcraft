package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.jspecify.annotations.Nullable;

/**
 * The grudge goal (Evoking II spec §3.4, first in the target selector): on each scan ({@code BoundVex.scanDue}), the
 * first fresh memory in this order that the vex may take ({@code BoundVex.keepsTarget}): the mob that last hurt this
 * vex, the mob that last hurt the summoner, the mob the summoner last hit, the mob that last hurt an ally inside the
 * guard area. The order is the spec's priority (self-defense first, the ally scan only when nothing nearer matches) and
 * costs one pass instead of comparing four ages (Divergence 6). A memory is fresh for {@link #MEMORY_TICKS} on its
 * owner's own clock ({@code rememberer.tickCount - timestamp}): vanilla forgets {@code lastHurtByMob} after 100 ticks
 * but never forgets {@code lastHurtMob} while it lives (docs/api-notes.md §21), so the window is checked here for all
 * four. A creeper is allowed here: it provoked.
 */
final class HurtBySummonerSideGoal extends TargetGoal {
    /** How old a hurt memory may be, in the remembering entity's ticks. */
    static final int MEMORY_TICKS = 100;

    private final BoundVex vex;

    @Nullable
    private LivingEntity found;

    HurtBySummonerSideGoal(BoundVex vex) {
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
        found = remembered(vex, vex.getLastHurtByMob(), vex.getLastHurtByMobTimestamp());
        if (found == null) {
            found = remembered(owner, owner.getLastHurtByMob(), owner.getLastHurtByMobTimestamp());
        }
        if (found == null) {
            found = remembered(owner, owner.getLastHurtMob(), owner.getLastHurtMobTimestamp());
        }
        if (found == null) {
            found = allyGrudge(owner);
        }
        return found != null;
    }

    /** {@code remembered} when the memory is fresh on {@code rememberer}'s clock and the vex may take it. */
    @Nullable
    private LivingEntity remembered(LivingEntity rememberer, @Nullable LivingEntity remembered, int timestamp) {
        if (remembered == null || rememberer.tickCount - timestamp > MEMORY_TICKS) {
            return null;
        }
        return vex.keepsTarget(remembered) ? remembered : null;
    }

    /** The attacker of the first ally in the guard area that remembers one. */
    @Nullable
    private LivingEntity allyGrudge(ServerPlayer owner) {
        for (Mob ally : vex.alliesInGuardArea(owner)) {
            LivingEntity attacker = remembered(ally, ally.getLastHurtByMob(), ally.getLastHurtByMobTimestamp());
            if (attacker != null) {
                return attacker;
            }
        }
        return null;
    }

    /** {@code TargetGoal.start()} does not set the target (docs/api-notes.md §21), so this does. */
    @Override
    public void start() {
        vex.setTarget(found);
        super.start();
    }

    /** {@code keepsTarget} alone: this goal has no sight rule (spec §3.4 item 1), so {@code super}'s checks are not run. */
    @Override
    public boolean canContinueToUse() {
        return vex.keepsTarget(vex.getTarget());
    }
}
