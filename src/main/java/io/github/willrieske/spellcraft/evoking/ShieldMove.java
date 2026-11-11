package io.github.willrieske.spellcraft.evoking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A Shield member's part (Parts 6-7 spec §4.1), each on its own 40-tick bash clock that starts ready. Farther
 * than 7 blocks from the enemy it flies in at half speed; within 7 and ready it lunges at normal speed and, within 1.5
 * blocks of the enemy's box, bashes: 4 damage as a mob attack from the vex (so mercy, the backstop and End crits
 * apply), knockback 1.5 away from the vex, the shield-block sound at the enemy, a swing. A lunge that has not landed
 * after 40 ticks gives up. After either it flies off to 6 blocks and holds there, looking at the enemy, until the
 * clock is ready.
 */
final class ShieldMove {
    /** Beyond this many blocks from the enemy a member flies in at half speed. */
    static final double APPROACH_RANGE = 7.0;

    /** A member that has bashed (or given up) flies off to this distance and holds there. */
    static final double BACK_OFF = 6.0;

    /**
     * The back-off aims one block past {@link #BACK_OFF}: the vex's move control coasts to a stop near the wanted
     * point, so it settles at about 6 to 7 blocks; the hold begins at 6.
     */
    private static final double BACK_OFF_SPOT = BACK_OFF + 1.0;

    /** A lunging member bashes when its box is within this many blocks of the enemy's box. */
    static final double BASH_REACH = 1.5;

    /** Health a bash takes. */
    static final float BASH_DAMAGE = 4.0F;

    /** Knockback power of a bash. */
    static final double BASH_KNOCKBACK = 1.5;

    /** Ticks between one member's bashes, and the longest a lunge may last. */
    static final int BASH_INTERVAL = 40;

    private ShieldMove() {}

    /** One tick of one member's Shield. */
    static void tick(BoundVex vex, TeamMoves.Move record, TeamMoves.Member member, ServerLevel level) {
        LivingEntity enemy = TeamMoves.subjectOf(level, record);
        if (enemy == null || !enemy.isAlive()) {
            return;
        }
        long now = level.getGameTime();
        TeamFlight.lookAt(vex, enemy);
        double distance = vex.distanceTo(enemy);
        switch (member.stage) {
            case READY -> {
                if (distance > APPROACH_RANGE) {
                    TeamFlight.flyTo(vex, enemy.getBoundingBox().getCenter(), TeamFlight.HALF);
                } else if (now >= member.nextAction) {
                    member.stage = TeamMoves.Member.Stage.LUNGING;
                    member.lungeStart = now;
                }
            }
            case LUNGING -> {
                if (TeamFlight.boxGap(vex, enemy) <= BASH_REACH) {
                    bash(vex, enemy, level);
                    backOff(member, now);
                } else if (now - member.lungeStart > BASH_INTERVAL) {
                    backOff(member, now);
                } else {
                    TeamFlight.flyTo(vex, enemy.getBoundingBox().getCenter(), TeamFlight.NORMAL);
                }
            }
            case BACKING -> {
                if (distance < BACK_OFF) {
                    Vec3 spot = TeamFlight.ringPoint(enemy.position(), vex.position(), BACK_OFF_SPOT, enemy.getY(0.5));
                    TeamFlight.flyTo(vex, spot, TeamFlight.NORMAL);
                } else if (now >= member.nextAction) {
                    member.stage = TeamMoves.Member.Stage.READY;
                }
            }
        }
    }

    /** Sends the member into its back-off with a fresh bash clock. */
    private static void backOff(TeamMoves.Member member, long now) {
        member.stage = TeamMoves.Member.Stage.BACKING;
        member.nextAction = now + BASH_INTERVAL;
    }

    /** The bash: damage, knockback away from the vex, the sound at the enemy, the swing. */
    private static void bash(BoundVex vex, LivingEntity enemy, ServerLevel level) {
        DamageSource source = vex.damageSources().mobAttack(vex);
        enemy.hurtServer(level, source, BASH_DAMAGE);
        enemy.knockback(BASH_KNOCKBACK, vex.getX() - enemy.getX(), vex.getZ() - enemy.getZ(), source, BASH_DAMAGE);
        level.playSound(
                null,
                enemy.getX(),
                enemy.getY(),
                enemy.getZ(),
                SoundEvents.SHIELD_BLOCK.value(),
                vex.getSoundSource(),
                1.0F,
                1.0F);
        vex.swingForAttack(InteractionHand.MAIN_HAND);
    }
}
