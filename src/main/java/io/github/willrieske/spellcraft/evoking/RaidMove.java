package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import io.github.willrieske.spellcraft.spell.rules.TeamMoveRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Raid (Parts 6-7 spec §4.4). Gather, 40 ticks: the 12 members take the cells of a 3 x 4 grid, 1.5 blocks apart,
 * centered 8 blocks from the enemy toward the summoner, rows facing the enemy, at the enemy's eye height, each flying
 * there at normal speed and looking at the enemy. Charge, to the end: melee members fly at the enemy and hit it with
 * {@code doHurtTarget} when their box meets its box, each on a 20-tick attack clock (the charge goal has none; a member
 * pressed against the enemy would hit every tick); rocket members (the kit's main-hand item is a crossbow, noted at the
 * start) keep 8 blocks (hold between 7 and 9) and every 40 ticks, with line of sight and no friend in the way, fire a
 * rocket. A rocketeer does not count its fellow members as friends in the way (the roster is passed to
 * {@code VexShots.friendInTheWay}): once one melee member sits in the enemy's box, every rocketeer would otherwise hold
 * fire for the rest of the move. The summoner, allies and Bound Vexes outside the roster still hold its fire. The
 * blast's damage source names the vex, so the friendly-fire backstop spares the summoner and everything the vex may not
 * fight. A Raid whose roster falls below 12 continues.
 *
 * <p>Functional spec §11.11.
 */
public final class RaidMove {
    /** Ticks the gather lasts before the charge. Functional spec §11.11. */
    public static final int GATHER_TICKS = 40;

    /** How far from the enemy, toward the summoner, the grid is centered. Functional spec §11.11. */
    static final double GRID_DISTANCE = 8.0;

    /** Ticks between one melee member's hits. Functional spec §11.11. */
    static final int ATTACK_INTERVAL = 20;

    /** Closer than this and a rocketeer backs off. */
    static final double ROCKET_NEAR = 7.0;

    /** Farther than this and a rocketeer closes in. */
    static final double ROCKET_FAR = 9.0;

    /** The distance a rocketeer that moves aims for. Functional spec §11.11. */
    static final double ROCKET_HOLD = 8.0;

    /** Ticks between one rocketeer's rockets. Functional spec §11.11. */
    static final int ROCKET_INTERVAL = 40;

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
        LivingEntity enemy = TeamMoves.subjectOf(level, record);
        if (enemy == null || !enemy.isAlive()) {
            return;
        }
        TeamFlight.lookAt(vex, enemy);
        long now = level.getGameTime();
        if (vex.isCaster()) {
            casterTick(vex, record, member, enemy, now);
            return;
        }
        if (record.phase == TeamMoves.Phase.GATHER) {
            Vec3 summoner = vex.summoner().map(Entity::position).orElse(vex.position());
            TeamFlight.flyTo(vex, cell(enemy.getEyePosition(), summoner, member.cell), TeamFlight.NORMAL);
        } else if (member.rocketeer) {
            rocketeer(vex, record, member, enemy, now);
        } else {
            charger(vex, member, enemy, level, now);
        }
    }

    /**
     * A Caster in a Raid: it flies to its grid cell and holds it for the whole move, and never attacks. At the start of
     * the gather it casts Mirror on the summoner at once (no wind-up) if the summoner has none, ignoring its own Mirror
     * cooldown and then setting it. It holds the scroll through the gather (spec §4.4: the scroll is its gather kit)
     * and empties the conjured main hand only when the charge starts. Functional spec §11.11.
     */
    private static void casterTick(
            BoundVex vex, TeamMoves.Move record, TeamMoves.Member member, LivingEntity enemy, long now) {
        Vec3 summoner = vex.summoner().map(Entity::position).orElse(vex.position());
        TeamFlight.holdAt(vex, cell(enemy.getEyePosition(), summoner, member.cell), Vec3.ZERO);
        if (!member.casterDone) {
            member.casterDone = true;
            vex.summoner().filter(owner -> !owner.hasEffect(ModEffects.MIRROR)).ifPresent(owner -> {
                if (ScrollCasting.land(vex, owner, Spells.MIRROR, GhostrailDuration.Kind.PLAYER)) {
                    vex.setMirrorReadyAt(now + ScrollCasting.cooldownFor(Spells.MIRROR));
                }
            });
        }
        if (record.phase == TeamMoves.Phase.CHARGE && !vex.getMainHandItem().isEmpty()) {
            member.kit.put(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            vex.conjure(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
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
     * {@link TeamFlight#SETTLE_RANGE} the push is a spring to the middle with damping on the vex's own velocity
     * instead, the way {@code EvaderMove} steers.
     */
    private static void closeIn(BoundVex vex, LivingEntity enemy) {
        Vec3 middle = enemy.getBoundingBox().getCenter().subtract(0.0, vex.getBbHeight() / 2.0, 0.0);
        // The enemy's real step, not its velocity field: a mob that was knocked back keeps a stale velocity
        Vec3 enemyStep = enemy.position().subtract(enemy.xOld, enemy.yOld, enemy.zOld);
        TeamFlight.holdAt(vex, middle, enemyStep);
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
