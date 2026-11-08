package io.github.willrieske.spellcraft.evoking;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex darts out of an iron golem's swing (Evoking II spec §3.6): when a golem within {@link #NOTICE_RANGE} is
 * mid-swing ({@code getAttackAnimationTick() > 0}) and its head faces the vex (the dot of its head look,
 * {@code getHeadLookAngle()}, and the direction from its eyes to the vex's middle is over {@link #FACING}), the vex
 * moves {@link #DART} blocks sideways, perpendicular to that look, in one move, every time. The head, not the body:
 * a golem winding up a swing turns its head to its target first (docs/api-notes.md §21). One dart per swing: the goal
 * waits out the swing's ten ticks before it can start again.
 */
final class DodgeGolemGoal extends Goal {
    /** How near a swinging golem must be: the half-size of the box searched around the vex. */
    static final double NOTICE_RANGE = 3.0;

    /** How squarely the golem must face the vex: the dot of two unit vectors. */
    static final double FACING = 0.5;

    /** How far the vex darts. */
    static final double DART = 3.0;

    /** A golem's swing animation lasts this many ticks. */
    private static final int SWING_TICKS = 10;

    private static final double SPEED = 1.5;

    private final BoundVex vex;
    private int readyAt;

    @Nullable
    private IronGolem golem;

    DodgeGolemGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (vex.tickCount < readyAt) {
            return false;
        }
        AABB around = new AABB(vex.position(), vex.position()).inflate(NOTICE_RANGE);
        golem = vex
                .level()
                .getEntitiesOfClass(
                        IronGolem.class, around, candidate -> candidate.isAlive() && isSwingingAtVex(candidate))
                .stream()
                .findFirst()
                .orElse(null);
        return golem != null;
    }

    private boolean isSwingingAtVex(IronGolem candidate) {
        if (candidate.getAttackAnimationTick() <= 0) {
            return false;
        }
        Vec3 toVex = vex.getBoundingBox().getCenter().subtract(candidate.getEyePosition());
        return toVex.lengthSqr() > 1.0E-4 && candidate.getHeadLookAngle().dot(toVex.normalize()) > FACING;
    }

    /** One dart per use. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (golem == null) {
            return;
        }
        Vec3 look = golem.getHeadLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0, look.x);
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize().scale(vex.getRandom().nextBoolean() ? DART : -DART);
        Vec3 spot = vex.position().add(side);
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, SPEED);
        readyAt = vex.tickCount + SWING_TICKS;
    }

    @Override
    public void stop() {
        golem = null;
    }
}
