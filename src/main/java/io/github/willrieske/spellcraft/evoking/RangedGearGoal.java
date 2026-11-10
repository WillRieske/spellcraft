package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A Bound Vex holding ranged gear (a launcher, or a throwable or arrow that is not support gear, Part 3 spec §4.3
 * "Ranged") keeps between {@link #MIN_DISTANCE} and {@link #MAX_DISTANCE} blocks from its target, looks at it, and
 * every {@link #SHOT_INTERVAL} ticks with line of sight shoots or throws a copy of the held item ({@link
 * VexShots#launch}). Priority 3, flags {@code MOVE} and {@code LOOK}: it takes the place of the charge goal, which the
 * same main-hand test keeps out. It holds its fire while a friend is in the way ({@link
 * VexShots#friendInTheWay(BoundVex, LivingEntity)}, spec §4.3 "Friendly fire"). A staying vex may back out of its stay
 * area to hold the band; it returns through the post goal once the target is gone (spec §2.2).
 */
public final class RangedGearGoal extends Goal {
    /** Closer than this and the vex flies away (spec §4.3). */
    public static final double MIN_DISTANCE = 6.0;

    /** Farther than this and the vex flies in (spec §4.3). */
    public static final double MAX_DISTANCE = 10.0;

    /**
     * How far past {@link #MAX_DISTANCE} a following vex still keeps its target ({@code BoundVex.keepsTarget}, Part 3
     * spec §4.3). The vex flies to the middle of the band and coasts on after the goal's last order, and the goal turns
     * it back only once it is beyond {@link #MAX_DISTANCE}: with the limit exactly there, the coast alone carried it a
     * hair past and the target was dropped for good (the guard box does not take it again from that far). The margin
     * lets the goal turn the vex back before the target goes.
     */
    public static final double KEEP_MARGIN = 2.0;

    /** Ticks between shots (spec §4.3). */
    public static final int SHOT_INTERVAL = 40;

    /** How close to the line of an arrow a friend's middle may be before the vex holds its fire (spec §4.3). */
    public static final double LINE_CLEARANCE = 1.0;

    /** How close to its target a friend may be before the vex holds back a splash potion (spec §4.3). */
    public static final double SPLASH_CLEARANCE = 4.0;

    private final BoundVex vex;

    /**
     * Ticks until the next shot. Kept across stop and start (a goal that is interrupted and resumes must not shoot at
     * once or wait a whole interval again), starting at half an interval, and counted down only while the goal runs.
     */
    private int cooldown = SHOT_INTERVAL / 2;

    RangedGearGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = vex.getTarget();
        return target != null && target.isAlive() && VexGear.isRanged(vex.getMainHandItem());
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
        double distance = vex.distanceTo(target);
        Vec3 eye = target.getEyePosition();
        vex.getLookControl().setLookAt(eye.x, eye.y, eye.z, 30.0F, 30.0F);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            // A point on the line through the target and the vex, in the middle of the band, at the target's eye
            // height.
            Vec3 away = vex.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
            Vec3 direction = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            Vec3 spot = target.position().add(direction.scale((MIN_DISTANCE + MAX_DISTANCE) / 2.0));
            vex.getMoveControl().setWantedPosition(spot.x, eye.y, spot.z, 1.0);
        }
        if (cooldown > 0) {
            cooldown--;
        } else if (distance <= MAX_DISTANCE && vex.hasLineOfSight(target) && !VexShots.friendInTheWay(vex, target)) {
            // Holding fire leaves the cooldown at zero: the shot goes as soon as the way is clear.
            VexShots.launch(vex, target);
            cooldown = SHOT_INTERVAL;
        }
    }
}
