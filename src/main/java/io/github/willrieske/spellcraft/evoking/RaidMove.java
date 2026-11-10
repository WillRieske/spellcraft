package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.rules.TeamMoveRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;

/**
 * The Raid (Parts 6-7 spec section 4.4). Gather, 40 ticks: the 12 members take the cells of a 3 x 4 grid, 1.5 blocks
 * apart, centered 8 blocks from the enemy toward the summoner, rows facing the enemy, at the enemy's eye height, each
 * flying there at normal speed and looking at the enemy. Charge, to the end: melee members fly at the enemy and hit it
 * with {@code doHurtTarget} when their box meets its box, each on a 20-tick attack clock (the charge goal has none; a
 * member pressed against the enemy would hit every tick); rocket members (a crossbow in the main hand) keep 8 blocks
 * (hold between 7 and 9) and every 40 ticks, with line of sight and no friend in the way, fire a rocket. A rocketeer
 * does not count its fellow members as friends in the way (the roster is passed to
 * {@code VexShots.friendInTheWay}): once one melee member sits in the enemy's box, every rocketeer would otherwise
 * hold fire for the rest of the move. The summoner, allies and Bound Vexes outside the roster still hold its fire. The
 * blast's damage source names the vex, so the friendly-fire backstop spares the summoner and everything the vex may
 * not fight. A Raid whose roster falls below 12 continues.
 */
public final class RaidMove {
    /** Ticks the gather lasts before the charge. */
    static final int GATHER_TICKS = 40;

    /** How far from the enemy, toward the summoner, the grid is centered. */
    static final double GRID_DISTANCE = 8.0;

    /** Ticks between one melee member's hits. */
    static final int ATTACK_INTERVAL = 20;

    /** Closer than this and a rocketeer backs off. */
    static final double ROCKET_NEAR = 7.0;

    /** Farther than this and a rocketeer closes in. */
    static final double ROCKET_FAR = 9.0;

    /** The distance a rocketeer that moves aims for. */
    static final double ROCKET_HOLD = 8.0;

    /** Ticks between one rocketeer's rockets. */
    static final int ROCKET_INTERVAL = 40;

    /** Within this many blocks of the enemy's middle a charging member settles instead of flying straight at it. */
    private static final double SETTLE_RANGE = 3.0;

    /** Settling: how strongly a member is pushed toward the enemy's middle, per block of gap. */
    private static final double SETTLE_POSITION_GAIN = 0.06;

    /** Settling: how strongly a member is pushed to match the enemy's velocity, per block a tick of difference. */
    private static final double SETTLE_VELOCITY_GAIN = 0.3;

    /** The push a vex gets toward its wanted point at speed 1.0, per tick (the move control's {@code 0.05}). */
    private static final double VEX_PUSH_PER_SPEED = 0.05;

    /** Settling: how far ahead of the member, in the push's direction, the wanted point is set. */
    private static final double SETTLE_REACH = 2.0;

    private RaidMove() {}

    /**
     * The position of a grid cell. Row 0 is the front row, nearest the enemy; column 0 is the right-hand one as the
     * grid faces the enemy.
     *
     * @param enemyEye the enemy's eye position
     * @param summoner the summoner's position: the grid lies on the line from the enemy toward them
     * @param cell the cell, 0 to 11 in roster order
     * @return where a member of that cell flies to during the gather
     */
    public static Vec3 cell(Vec3 enemyEye, Vec3 summoner, int cell) {
        Vec3 toward = new Vec3(summoner.x - enemyEye.x, 0.0, summoner.z - enemyEye.z);
        Vec3 forward = toward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : toward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        return enemyEye.add(forward.scale(GRID_DISTANCE + TeamMoveRules.gridDepth(cell)))
                .add(right.scale(TeamMoveRules.gridAcross(cell)));
    }

    /**
     * Moves the record from the gather to the charge once the gather's 40 ticks are up.
     *
     * @param record the running move
     * @param now the game time
     */
    static void advance(TeamMoves.Move record, long now) {
        if (record.phase == TeamMoves.Phase.GATHER && now - record.start >= GATHER_TICKS) {
            record.phase = TeamMoves.Phase.CHARGE;
        }
    }

    /**
     * One tick of one member's Raid.
     *
     * @param vex the member
     * @param record the running move
     * @param member the member's own state
     * @param level the vex's level
     */
    static void tick(BoundVex vex, TeamMoves.Move record, TeamMoves.Member member, ServerLevel level) {
        // Part 7: a Caster only gathers, casts Mirror, then holds its cell
        LivingEntity enemy = TeamMoves.subjectOf(level, record);
        if (enemy == null || !enemy.isAlive()) {
            return;
        }
        TeamFlight.lookAt(vex, enemy);
        long now = level.getGameTime();
        if (record.phase == TeamMoves.Phase.GATHER) {
            Vec3 summoner = vex.summoner().map(Entity::position).orElse(vex.position());
            TeamFlight.flyTo(vex, cell(enemy.getEyePosition(), summoner, member.cell), TeamFlight.NORMAL);
        } else if (vex.getMainHandItem().getItem() instanceof CrossbowItem) {
            rocketeer(vex, record, member, enemy, now);
        } else {
            charger(vex, member, enemy, level, now);
        }
    }

    /** A melee member's charge: fly at the enemy and hit it when the boxes meet, once per attack clock. */
    private static void charger(
            BoundVex vex, TeamMoves.Member member, LivingEntity enemy, ServerLevel level, long now) {
        closeIn(vex, enemy);
        if (vex.getBoundingBox().intersects(enemy.getBoundingBox()) && now >= member.nextAction) {
            vex.doHurtTarget(level, enemy);
            vex.swingForAttack(InteractionHand.MAIN_HAND);
            member.nextAction = now + ATTACK_INTERVAL;
        }
    }

    /**
     * Flies the vex's middle to the enemy's middle and holds it there. A vex only accelerates toward its wanted point
     * and coasts, so a plain "fly to the point" overshoots and swings in and out of the enemy's box; inside
     * {@link #SETTLE_RANGE} the push is a spring to the middle with damping on the vex's own velocity instead, the way
     * {@code EvaderMove} steers.
     */
    private static void closeIn(BoundVex vex, LivingEntity enemy) {
        Vec3 middle = enemy.getBoundingBox().getCenter().subtract(0.0, vex.getBbHeight() / 2.0, 0.0);
        Vec3 gap = middle.subtract(vex.position());
        if (gap.length() > SETTLE_RANGE) {
            TeamFlight.flyTo(vex, middle, TeamFlight.NORMAL);
            return;
        }
        // The enemy's real step, not its velocity field: a mob that was knocked back keeps a stale velocity
        Vec3 enemyStep = enemy.position().subtract(enemy.xOld, enemy.yOld, enemy.zOld);
        Vec3 push = gap.scale(SETTLE_POSITION_GAIN)
                .add(enemyStep.subtract(vex.getDeltaMovement()).scale(SETTLE_VELOCITY_GAIN));
        double strength = push.length();
        Vec3 direction = strength < 1.0E-6 ? Vec3.ZERO : push.scale(1.0 / strength);
        TeamFlight.flyTo(
                vex,
                vex.position().add(direction.scale(SETTLE_REACH)),
                Math.min(TeamFlight.NORMAL, strength / VEX_PUSH_PER_SPEED));
    }

    /**
     * A rocket member's charge: hold 7 to 9 blocks from the enemy and fire a rocket every 40 ticks with line of sight
     * and no friend in the way (fellow members of the roster are not friends in the way).
     */
    private static void rocketeer(
            BoundVex vex, TeamMoves.Move record, TeamMoves.Member member, LivingEntity enemy, long now) {
        double distance = vex.distanceTo(enemy);
        if (distance < ROCKET_NEAR || distance > ROCKET_FAR) {
            Vec3 spot = TeamFlight.ringPoint(enemy.position(), vex.position(), ROCKET_HOLD, enemy.getEyeY());
            TeamFlight.flyTo(vex, spot, TeamFlight.NORMAL);
        }
        if (now >= member.nextAction
                && vex.hasLineOfSight(enemy)
                && !VexShots.friendInTheWay(vex, enemy, record.roster)) {
            VexShots.rocket(vex, enemy);
            member.nextAction = now + ROCKET_INTERVAL;
        }
    }
}
