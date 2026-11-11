package io.github.willrieske.spellcraft.evoking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A Multishot member's part (Parts 6-7 spec §4.2): stay on its own slot on an arc round the enemy, {@link #HOLD} blocks
 * out (25 degrees a slot, centered on the enemy-to-summoner direction), so the shooters do not stand in each other's
 * lines of fire: a member more than {@link #SLOT_TOLERANCE} sideways from its slot flies to it, one nearer holds,
 * looking at the enemy. Every 40 ticks, with line of sight and no friend in the way, it fires a volley of three arrows
 * from the conjured crossbow. The roster's other members are not friends in the way (the slots keep them off each
 * other's lines; a Bound Vex outside the roster, the summoner and allies still hold the fire).
 */
final class MultishotMove {
    /** The distance of a member's slot from the enemy. */
    static final double HOLD = 7.0;

    /** A member farther than this many blocks from its slot, sideways (a vex bobs up and down), flies to it. */
    static final double SLOT_TOLERANCE = 1.0;

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
        int slot = Math.max(0, record.roster.indexOf(vex.getUUID()));
        Vec3 toward = vex.summoner().map(ServerPlayer::position).orElse(vex.position());
        Vec3 spot = slotSpot(enemy, toward, slot, record.roster.size());
        if (vex.position().subtract(spot).horizontalDistance() > SLOT_TOLERANCE) {
            TeamFlight.flyTo(vex, spot, TeamFlight.NORMAL);
        }
        long now = level.getGameTime();
        if (now >= member.nextAction
                && vex.hasLineOfSight(enemy)
                && !VexShots.friendInTheWay(vex, enemy, record.roster)) {
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
