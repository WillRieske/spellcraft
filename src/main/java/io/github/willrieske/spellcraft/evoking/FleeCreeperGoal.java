package io.github.willrieske.spellcraft.evoking;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Bound Vex flies straight away from a hissing creeper (Evoking II spec §3.5): when a creeper within
 * {@link #NOTICE_RANGE} blocks (feet to feet) is swelling ({@code getSwellDir() > 0}), the vex flies directly away from
 * it to {@link #SAFE_DISTANCE} blocks and holds there until the creeper stops swelling, dies or explodes. The goal
 * outranks the charge goal (both hold the MOVE flag), so the target is kept and the charge resumes afterwards. The
 * selector ticks a goal right after starting it, so {@code start} needs no move of its own.
 */
public final class FleeCreeperGoal extends Goal {
    /** How near a swelling creeper must be to be noticed. */
    public static final double NOTICE_RANGE = 8.0;

    /** How far from the creeper the vex flies. */
    public static final double SAFE_DISTANCE = 8.0;

    private static final double SPEED = 1.0;

    private final BoundVex vex;

    @Nullable
    private Creeper creeper;

    FleeCreeperGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        creeper = nearestSwelling();
        return creeper != null;
    }

    /** {@code BoundVex.nearby} makes the range the sphere the spec names, not the search box's cube. */
    @Nullable
    private Creeper nearestSwelling() {
        return vex
                .nearby(Creeper.class, NOTICE_RANGE, candidate -> candidate.isAlive() && candidate.getSwellDir() > 0)
                .stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .orElse(null);
    }

    @Override
    public boolean canContinueToUse() {
        return creeper != null && creeper.isAlive() && creeper.getSwellDir() > 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * Aims for the point {@link #SAFE_DISTANCE} + 1 blocks from the creeper, straight away from it, at the vex's
     * height, and stops steering once the vex is {@link #SAFE_DISTANCE} away. The extra block is deliberate: the move
     * control treats a spot within the vex's own size as reached, so aiming one block past the safe distance lets the
     * vex coast to at least the safe distance rather than stopping short of it.
     */
    @Override
    public void tick() {
        if (creeper == null) {
            return;
        }
        Vec3 away = vex.position().subtract(creeper.position());
        if (away.length() >= SAFE_DISTANCE) {
            return;
        }
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 spot = creeper.position()
                .add(flat.normalize().scale(SAFE_DISTANCE + 1.0))
                .add(0.0, vex.getY() - creeper.getY(), 0.0);
        vex.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, SPEED);
    }

    @Override
    public void stop() {
        creeper = null;
    }
}
