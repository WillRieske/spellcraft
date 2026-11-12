package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A Bound Vex holding support gear (a splash or lingering potion whose effects are all beneficial, Part 3 spec §4.3
 * "Support") does not fight. Every {@link #INTERVAL} ticks (on the vex's shared scan clock) the nearest friend within
 * {@link #RANGE} blocks, in sight and below half health, gets a copy of the potion thrown at it. Friends that healing
 * would hurt ({@code isInvertedHealAndHarm}: the undead) are skipped. A friend is the summoner or an ally of the
 * summoner ({@code Allies.isAllyOf}, which counts the summoner's other Bound Vexes), the vex itself excepted. The goal
 * has no flags and never moves the vex, so the follow and post goals keep running beside it. It looks on the fixed
 * {@code BoundVex.SCAN_INTERVAL} clock, not the posture's: its 40-tick throw cooldown makes a faster look pointless.
 * (The cure potion a vex conjures, Parts 4-5 spec §2.2, is not support gear: it has no effects, and
 * {@code VexGear.kindOf} gives an effect-free potion {@code NONE}.)
 *
 * <p>Functional spec §8.8.
 */
final class SupportGearGoal extends Goal {
    /** How far a friend may be (spec §4.3). Functional spec §8.8. */
    static final double RANGE = 10.0;

    /** Ticks between throws (spec §4.3). Functional spec §8.8. */
    static final int INTERVAL = 40;

    private final BoundVex vex;
    private int cooldown;

    SupportGearGoal(BoundVex vex) {
        this.vex = vex;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return VexGear.isSupport(vex.getMainHandItem()) && vex.summoner().isPresent();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!vex.scanDue(BoundVex.SCAN_INTERVAL)) {
            return;
        }
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return;
        }
        vex.nearby(LivingEntity.class, RANGE, friend -> isHurtFriend(owner, friend)).stream()
                .min(Comparator.comparingDouble(vex::distanceToSqr))
                .ifPresent(friend -> {
                    VexShots.launch(vex, friend);
                    cooldown = INTERVAL;
                });
    }

    /**
     * The summoner or an ally of theirs, alive, in sight and below half health; never the vex itself.
     * Functional spec §8.8.
     */
    private boolean isHurtFriend(ServerPlayer owner, LivingEntity friend) {
        return !friend.getUUID().equals(vex.getUUID())
                && friend.isAlive()
                && !friend.isInvertedHealAndHarm()
                && friend.getHealth() < friend.getMaxHealth() / 2.0F
                && (friend.getUUID().equals(owner.getUUID()) || Allies.isAllyOf(owner, friend))
                && vex.hasLineOfSight(friend);
    }
}
