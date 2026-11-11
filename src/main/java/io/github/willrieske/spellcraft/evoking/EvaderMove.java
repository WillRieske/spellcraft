package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.TeamMoveRules;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Evader (Parts 6-7 spec §4.3). Each member circles the ward at 2 blocks, spread evenly, the circle turning 6
 * degrees a tick ({@link #tick}). The guard ({@link #guard}) runs once a tick for the whole record: the nearest member
 * shoves any hostile whose box comes within 3 blocks of the ward's (knockback 2 away from the ward, no damage, each
 * hostile at most once per 20 ticks) and reverses any projectile within 2 blocks of the ward (or about to be, after its
 * next step) that flies toward it and was not shot from the summoner's side (not a large fireball: Part 5's return owns
 * those). The reversed projectile's new owner is the vex, so it is never deflected twice and the friendly-fire backstop
 * covers it.
 */
final class EvaderMove {
    /** How far from the ward's middle a member circles. */
    static final double ORBIT_RADIUS = 2.0;

    /** A hostile whose box is within this many blocks of the ward's box is shoved. */
    static final double SHOVE_RANGE = 3.0;

    /** Knockback power of a shove. */
    static final double SHOVE_KNOCKBACK = 2.0;

    /** Ticks before the same hostile may be shoved again. */
    static final int SHOVE_PAUSE = 20;

    /** A projectile whose box is within this many blocks of the ward's box is deflected. */
    static final double DEFLECT_RANGE = 2.0;

    /**
     * The farthest a projectile is expected to travel in a tick (a full-draw player arrow moves 3 blocks): the guard
     * looks this far past the deflection range, so a shot that would cross the range between two samples is caught.
     */
    static final double SHOT_STEP = 4.0;

    /** The deflection power: 2.0 is a full reversal. */
    static final double DEFLECT_POWER = 2.0;

    /** Steering: how strongly a member is pushed toward its ring spot, per block of gap. */
    private static final double STEER_POSITION_GAIN = 0.02;

    /** Steering: how strongly a member is pushed to match its ring spot's velocity, per block a tick of difference. */
    private static final double STEER_VELOCITY_GAIN = 0.3;

    /** Steering: the push that cancels the drag of a vex's flight (it keeps 0.91 of its speed a tick). */
    private static final double DRAG_FEED_FORWARD = 0.1;

    private EvaderMove() {}

    /**
     * One tick of one member's orbit.
     *
     * @param vex the member
     * @param record the running move
     * @param member the member's own state
     * @param level the vex's level
     */
    static void tick(BoundVex vex, TeamMoves.Move record, TeamMoves.Member member, ServerLevel level) {
        LivingEntity ward = TeamMoves.subjectOf(level, record);
        if (ward == null || !ward.isAlive()) {
            return;
        }
        int slot = Math.max(0, record.roster.indexOf(vex.getUUID()));
        int count = Math.max(1, record.roster.size());
        long elapsed = level.getGameTime() - record.start;
        Vec3 middle = ward.position().add(0.0, ward.getBbHeight() / 2.0, 0.0);
        Vec3 spot = ringSpot(slot, count, elapsed);
        Vec3 next = ringSpot(slot, count, elapsed + 1);
        Vec3 after = ringSpot(slot, count, elapsed + 2);
        Vec3 spotVelocity = next.subtract(spot).add(ward.getKnownMovement());
        Vec3 spotAcceleration = after.subtract(next.scale(2.0)).add(spot);
        Vec3 velocity = vex.getDeltaMovement();
        // A vex only accelerates toward its wanted point (0.05 a tick at normal speed) and coasts, so a plain
        // "fly to the spot" overshoots and circles wide. Steer instead: track the spot's place and velocity, and
        // feed forward the spot's own acceleration and the drag (a vex keeps 0.91 of its speed each tick). The ward's
        // own motion is its known movement: a player's delta movement stays near zero on the server.
        Vec3 push = middle.add(spot)
                .subtract(vex.position())
                .scale(STEER_POSITION_GAIN)
                .add(spotVelocity.subtract(velocity).scale(STEER_VELOCITY_GAIN))
                .add(spotAcceleration)
                .add(spotVelocity.scale(DRAG_FEED_FORWARD));
        TeamFlight.steer(vex, push);
        TeamFlight.lookAt(vex, ward);
    }

    /** The offset from the ward's middle of the ring spot of slot {@code slot} after {@code elapsed} ticks. */
    private static Vec3 ringSpot(int slot, int count, long elapsed) {
        double radians = Math.toRadians(TeamMoveRules.orbitDegrees(slot, count, elapsed));
        return new Vec3(Math.cos(radians) * ORBIT_RADIUS, 0.0, Math.sin(radians) * ORBIT_RADIUS);
    }

    /**
     * The shoves and the deflections of one tick.
     *
     * @param owner the summoner
     * @param record the running move
     * @param now the game time
     */
    static void guard(ServerPlayer owner, TeamMoves.Move record, long now) {
        ServerLevel level = owner.level();
        LivingEntity ward = TeamMoves.subjectOf(level, record);
        if (ward == null || !ward.isAlive()) {
            return;
        }
        List<BoundVex> members = TeamMoves.members(level, record);
        if (members.isEmpty()) {
            return;
        }
        record.shoved.values().removeIf(until -> until <= now);
        for (LivingEntity hostile : level.getEntitiesOfClass(
                LivingEntity.class,
                ward.getBoundingBox().inflate(SHOVE_RANGE),
                other -> !other.equals(ward)
                        && other.isAlive()
                        && !record.shoved.containsKey(other.getUUID())
                        && TeamFlight.boxGap(other, ward) <= SHOVE_RANGE
                        && BoundVex.huntable(owner.getUUID(), other))) {
            BoundVex member = TeamMoves.nearest(members, hostile.position());
            if (member.isPrey(hostile)) {
                shove(member, hostile, ward);
                record.shoved.put(hostile.getUUID(), now + SHOVE_PAUSE);
            }
        }
        for (Projectile projectile : level.getEntitiesOfClass(
                Projectile.class,
                ward.getBoundingBox().inflate(DEFLECT_RANGE + SHOT_STEP),
                shot -> shot.isAlive()
                        && !(shot instanceof LargeFireball)
                        && reaches(shot, ward)
                        && approaches(shot, ward)
                        && !onTheirSide(owner, shot.getOwner()))) {
            BoundVex member = TeamMoves.nearest(members, projectile.position());
            projectile.deflect(ProjectileDeflection.REVERSE, member, EntityReference.of(member), false, DEFLECT_POWER);
            member.playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.0F);
        }
    }

    /** The shove: knockback away from the ward, no damage, the sound and the swing. */
    private static void shove(BoundVex member, LivingEntity hostile, LivingEntity ward) {
        hostile.knockback(
                SHOVE_KNOCKBACK,
                ward.getX() - hostile.getX(),
                ward.getZ() - hostile.getZ(),
                member.damageSources().mobAttack(member),
                0.0F);
        member.playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.0F);
        member.swingForAttack(InteractionHand.MAIN_HAND);
    }

    /**
     * Whether the projectile is within {@link #DEFLECT_RANGE} of the ward's box, or will be after its next step: the
     * segment from where it is to where it goes next crosses the box inflated by that range. A fast arrow (3 blocks a
     * tick) can otherwise jump from outside the range to the ward between two samples.
     */
    private static boolean reaches(Projectile shot, LivingEntity ward) {
        if (TeamFlight.boxGap(shot, ward) <= DEFLECT_RANGE) {
            return true;
        }
        Vec3 from = shot.position();
        return ward.getBoundingBox()
                .inflate(DEFLECT_RANGE)
                .clip(from, from.add(shot.getDeltaMovement()))
                .isPresent();
    }

    /** Whether the projectile's velocity points toward the ward's middle. */
    private static boolean approaches(Projectile shot, LivingEntity ward) {
        return shot.getDeltaMovement().dot(ward.getBoundingBox().getCenter().subtract(shot.position())) > 0.0;
    }

    /** Whether the shooter is the summoner, one of their allies or a mob carrying their mark. */
    private static boolean onTheirSide(ServerPlayer summoner, @Nullable Entity shooter) {
        return shooter != null
                && (shooter.getUUID().equals(summoner.getUUID())
                        || Allies.isAllyOf(summoner, shooter)
                        || shooter.getExistingData(AllyAttachments.SUMMONER)
                                .filter(summoner.getUUID()::equals)
                                .isPresent());
    }
}
