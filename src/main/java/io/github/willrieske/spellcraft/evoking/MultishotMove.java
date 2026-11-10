package io.github.willrieske.spellcraft.evoking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A Multishot member's part (Parts 6-7 spec §4.2): keep 6 to 8 blocks from the enemy (closer: back off; farther:
 * close in; between: hold, looking at it) and every 40 ticks, with line of sight and no friend in the way, fire a
 * volley of three arrows from the conjured crossbow. A member that has to move flies to its own slot on an arc round
 * the enemy (25 degrees a slot, centered on the enemy-to-summoner direction) rather than along its own line, so the
 * shooters do not stand in each other's lines of fire.
 */
final class MultishotMove {
    /** Closer than this and a member backs off. */
    static final double NEAR = 6.0;

    /** Farther than this and a member closes in. */
    static final double FAR = 8.0;

    /** The distance a member that moves aims for. */
    static final double HOLD = 7.0;

    /** Ticks between one member's volleys. */
    static final int SHOT_INTERVAL = 40;

    /** The angle between neighboring slots on the arc, in degrees (about 3 blocks apart at {@link #HOLD}). */
    static final double ARC_DEGREES = 25.0;

    private MultishotMove() {}

    /** One tick of one member's Multishot. */
    static void tick(BoundVex vex, TeamMoves.Move record, TeamMoves.Member member, ServerLevel level) {
        LivingEntity enemy = TeamMoves.subjectOf(level, record);
        if (enemy == null || !enemy.isAlive()) {
            return;
        }
        TeamFlight.lookAt(vex, enemy);
        double distance = vex.distanceTo(enemy);
        if (distance < NEAR || distance > FAR) {
            int slot = Math.max(0, record.roster.indexOf(vex.getUUID()));
            Vec3 toward = vex.summoner().map(ServerPlayer::position).orElse(vex.position());
            TeamFlight.flyTo(vex, slotSpot(enemy, toward, slot, record.roster.size()), TeamFlight.NORMAL);
        }
        long now = level.getGameTime();
        if (now >= member.nextAction && vex.hasLineOfSight(enemy) && !VexShots.friendInTheWay(vex, enemy)) {
            // Holding fire leaves the clock at zero: the volley goes as soon as the way is clear.
            VexShots.volley(vex, enemy);
            member.nextAction = now + SHOT_INTERVAL;
        }
    }

    /**
     * The spot of slot {@code slot} of {@code size} on the arc round {@code enemy}: {@link #HOLD} blocks out, at the
     * enemy's eye height, rotated {@link #ARC_DEGREES} a slot round the enemy from the direction toward
     * {@code toward}, so the middle slot is straight toward the summoner.
     */
    private static Vec3 slotSpot(LivingEntity enemy, Vec3 toward, int slot, int size) {
        Vec3 base = TeamFlight.ringPoint(enemy.position(), toward, HOLD, enemy.getEyeY());
        double radians = Math.toRadians((slot - (size - 1) / 2.0) * ARC_DEGREES);
        double dx = base.x - enemy.getX();
        double dz = base.z - enemy.getZ();
        return new Vec3(
                enemy.getX() + dx * Math.cos(radians) - dz * Math.sin(radians),
                enemy.getEyeY(),
                enemy.getZ() + dx * Math.sin(radians) + dz * Math.cos(radians));
    }
}
