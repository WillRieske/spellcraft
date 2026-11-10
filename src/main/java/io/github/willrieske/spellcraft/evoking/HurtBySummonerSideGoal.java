package io.github.willrieske.spellcraft.evoking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * The grudge goal (Evoking II spec §3.4, first in the target selector): on each scan ({@code BoundVex.scanDue}), the
 * first fresh memory in this order that the vex may take ({@code BoundVex.takesTarget}): the mob that last hurt this
 * vex, the mob that last hurt the summoner, the mob the summoner last hit, the mob that last hurt an ally inside the
 * guard area. The order is the spec's priority (self-defense first, the ally scan only when nothing nearer matches) and
 * costs one pass instead of comparing four ages. A memory is fresh for {@link #MEMORY_TICKS} on its owner's own clock
 * ({@code rememberer.tickCount - timestamp}): vanilla forgets {@code lastHurtByMob} after 100 ticks but never forgets
 * {@code lastHurtMob} while it lives (docs/api-notes.md §21), so the window is checked here for all four. A creeper
 * is allowed here: it provoked, and so is another player's summon (spec §3.4, the PvP rule in {@code isPrey}).
 */
public final class HurtBySummonerSideGoal extends ScanTargetGoal {
    /** How old a hurt memory may be, in the remembering entity's ticks; public for the GameTests. */
    public static final int MEMORY_TICKS = 100;

    HurtBySummonerSideGoal(BoundVex vex) {
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

    /** {@code remembered} when the memory is fresh on {@code rememberer}'s clock and the vex may take it
     * ({@code takesTarget}: a following vex takes only what is within the reach, whatever it holds). */
    @Nullable
    private LivingEntity remembered(LivingEntity rememberer, @Nullable LivingEntity remembered, int timestamp) {
        if (remembered == null || rememberer.tickCount - timestamp > MEMORY_TICKS) {
            return null;
        }
        return vex.takesTarget(remembered) ? remembered : null;
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
}
