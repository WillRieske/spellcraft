package io.github.willrieske.spellcraft.evoking;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.rules.SegmentDistance;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a Bound Vex launches (Evoking II Part 3 spec §4.3). {@link #launch} shoots a copy of the item in its main hand,
 * never the item itself, so nothing is used up (spec §4.5); {@link #volley} and {@link #rocket} (Parts 6-7 spec §4.2,
 * §4.4) fire from the conjured crossbow, which is Unbreakable: the volley loads it and shoots through it, the rocket
 * only plays its sound. The aim and the spawn follow the skeleton ({@code
 * AbstractSkeleton.performRangedAttack}) for arrows and the witch ({@code Witch.performRangedAttack}) for potions,
 * through the two {@code Projectile.spawnProjectileUsingShoot} helpers (docs/api-notes.md §22).
 */
final class VexShots {
    /** Arrow launch speed: the skeleton's. */
    static final float ARROW_SPEED = 1.6F;

    /** Arrow inaccuracy: a vex is a fair shot (the skeleton's is {@code 14 - 4 * difficulty}). */
    static final float ARROW_INACCURACY = 2.0F;

    /** Potion throw speed: the witch's. */
    static final float POTION_SPEED = 0.75F;

    /** Potion throw speed within two blocks: the witch's. */
    static final float POTION_SPEED_CLOSE = 0.45F;

    /** Potion inaccuracy at an enemy: the witch's. */
    static final float POTION_INACCURACY = 8.0F;

    /**
     * Potion inaccuracy at a friend. A healer has to hit: a splash heals by how near it bursts, and a throw that
     * misses a friend in the open bursts wherever it lands, so support throws are aimed as tightly as the arrows.
     */
    static final float SUPPORT_INACCURACY = 2.0F;

    /** Rocket launch speed: a crossbow's own. */
    static final float ROCKET_SPEED = 1.6F;

    /** Rocket inaccuracy: a rocket is aimed tightly, since its one explosion does the damage. */
    static final float ROCKET_INACCURACY = 1.0F;

    /** How close to the line of an arrow a friend's middle may be before the vex holds its fire (Part 3 spec §4.3). */
    static final double LINE_CLEARANCE = 1.0;

    /** How close to its target a friend may be before the vex holds back a splash potion (Part 3 spec §4.3). */
    static final double SPLASH_CLEARANCE = 4.0;

    private VexShots() {}

    /**
     * Shoots or throws a copy of the main-hand item at {@code at}; the held stack is untouched. Does nothing for an
     * item that is not launchable, or on the client.
     *
     * @param vex the shooter
     * @param at the target (an enemy, or a friend to heal)
     */
    static void launch(BoundVex vex, LivingEntity at) {
        if (!(vex.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = vex.getMainHandItem().copy();
        switch (VexGear.kindOf(held)) {
            case LAUNCHER -> {
                ItemStack ammo = new ItemStack(Items.ARROW);
                shootArrow(level, vex, ProjectileUtil.getMobArrow(vex, ammo, 1.0F, held), ammo, at);
                vex.playSound(
                        held.getItem() instanceof CrossbowItem ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT,
                        1.0F,
                        1.0F);
            }
            case ARROW -> {
                ItemStack ammo = held.copyWithCount(1);
                shootArrow(level, vex, ProjectileUtil.getMobArrow(vex, ammo, 1.0F, null), ammo, at);
                vex.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F);
            }
            case THROWABLE -> throwPotion(level, vex, held.copyWithCount(1), at);
            default -> {
                // not a launchable item: nothing happens
            }
        }
    }

    /**
     * The point an arrow is aimed at: the target's lower body, a third of the way up its box (the skeleton's aim).
     * {@link RangedGearGoal} measures its friendly-fire line to this point, so the two cannot drift apart.
     *
     * @param at the target
     * @return the aim point
     */
    static Vec3 aimPoint(LivingEntity at) {
        return new Vec3(at.getX(), at.getY(1.0 / 3.0), at.getZ());
    }

    /** A volley: three plain arrows, loaded by hand, fired through the crossbow (Parts 6-7 spec §4.2). */
    static void volley(BoundVex vex, LivingEntity at) {
        ItemStack weapon = vex.getMainHandItem();
        if (!(vex.level() instanceof ServerLevel level) || !(weapon.getItem() instanceof CrossbowItem crossbow)) {
            return;
        }
        weapon.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(
                        List.of(new ItemStack(Items.ARROW), new ItemStack(Items.ARROW), new ItemStack(Items.ARROW))));
        // The Multishot enchantment on the stack gives the spread (0 and +-10 degrees); vanilla's draw, which would add
        // the extra two projectiles, is not reachable from performShooting (docs/api-notes.md section 25).
        crossbow.performShooting(level, vex, InteractionHand.MAIN_HAND, weapon, ARROW_SPEED, inaccuracy(level), at);
    }

    /**
     * One rocket with one explosion (Parts 6-7 spec §4.4); a rocket with none does no damage. The crossbow in the main
     * hand is the kit item and the sound; the rocket itself is spawned the way {@link #shootArrow} spawns an arrow,
     * aimed straight at {@link #aimPoint}. {@code CrossbowItem.performShooting} with a target would lift the aim by a
     * fifth of the flat distance for an arrow's drop, and a rocket shot at an angle has no gravity, so it would fly
     * that lifted line over a short enemy.
     */
    static void rocket(BoundVex vex, LivingEntity at) {
        if (!(vex.level() instanceof ServerLevel level)
                || !(vex.getMainHandItem().getItem() instanceof CrossbowItem)) {
            return;
        }
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(FireworkExplosion.DEFAULT)));
        FireworkRocketEntity entity =
                new FireworkRocketEntity(level, rocket, vex, vex.getX(), vex.getEyeY() - 0.15F, vex.getZ(), true);
        Vec3 aim = aimPoint(at);
        Projectile.spawnProjectileUsingShoot(
                entity,
                level,
                rocket,
                aim.x - entity.getX(),
                aim.y - entity.getY(),
                aim.z - entity.getZ(),
                ROCKET_SPEED,
                ROCKET_INACCURACY);
        vex.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F);
    }

    /** What a pillager's crossbow uses: {@code 14 - difficulty * 4}. */
    private static float inaccuracy(ServerLevel level) {
        return 14.0F - level.getDifficulty().getId() * 4.0F;
    }

    /**
     * Whether a friend (the summoner, an ally of the summoner, or another Bound Vex of the same summoner; never the
     * vex itself or its target) stands where the shot would hurt it (Part 3 spec §4.3 "Friendly fire"): for an arrow,
     * a friend whose bounding-box center is within {@link #LINE_CLEARANCE} of the segment from the vex's eyes to the
     * point the arrow is aimed at ({@link #aimPoint}: the target's lower body, not its eyes); for a thrown potion, a
     * friend within {@link #SPLASH_CLEARANCE} of the target. Only the center line is checked: a Multishot volley fans
     * out to +-10 degrees, so a friend 1.2 to 1.5 blocks off the line at 7 blocks can stand in a flank arrow's path;
     * the friendly-fire backstop cancels that damage, so such an arrow is wasted, not harmful.
     */
    static boolean friendInTheWay(BoundVex vex, LivingEntity target) {
        return friendInTheWay(vex, target, Set.of());
    }

    /**
     * {@link #friendInTheWay(BoundVex, LivingEntity)}, except that a Bound Vex whose UUID is in {@code ignoring} is not
     * a friend in the way; the summoner, allies and Bound Vexes outside {@code ignoring} still hold fire.
     */
    static boolean friendInTheWay(BoundVex vex, LivingEntity target, Collection<UUID> ignoring) {
        ServerPlayer owner = vex.summoner().orElse(null);
        if (owner == null) {
            return false;
        }
        boolean thrown = VexGear.kindOf(vex.getMainHandItem()) == VexGear.Kind.THROWABLE;
        Vec3 from = vex.getEyePosition();
        Vec3 to = aimPoint(target);
        AABB box = thrown ? target.getBoundingBox().inflate(SPLASH_CLEARANCE + 2.0) : new AABB(from, to).inflate(4.0);
        return !vex.level()
                .getEntitiesOfClass(LivingEntity.class, box, friend -> {
                    if (friend.equals(vex) || friend.equals(target) || !friend.isAlive()) {
                        return false;
                    }
                    // checked first: Allies.isAllyOf counts every Bound Vex of the summoner, which would hide this
                    if (friend instanceof BoundVex other && ignoring.contains(other.getUUID())) {
                        return false;
                    }
                    boolean isFriend = friend.getUUID().equals(owner.getUUID())
                            || Allies.isAllyOf(owner, friend)
                            || (friend instanceof BoundVex other && other.isSummonedBy(owner.getUUID()));
                    if (!isFriend) {
                        return false;
                    }
                    return thrown
                            ? friend.distanceTo(target) <= SPLASH_CLEARANCE
                            : lineDistance(friend.getBoundingBox().getCenter(), from, to) <= LINE_CLEARANCE;
                })
                .isEmpty();
    }

    /** {@link SegmentDistance#distanceToSegment} for {@code Vec3}s. */
    private static double lineDistance(Vec3 point, Vec3 a, Vec3 b) {
        return SegmentDistance.distanceToSegment(point.x, point.y, point.z, a.x, a.y, a.z, b.x, b.y, b.z);
    }

    /** The skeleton's aim: {@link #aimPoint}, lifted by a fifth of the flat distance for the arrow's drop. */
    private static void shootArrow(
            ServerLevel level, BoundVex vex, AbstractArrow arrow, ItemStack ammo, LivingEntity at) {
        Vec3 aim = aimPoint(at);
        double dx = aim.x - vex.getX();
        double dy = aim.y - arrow.getY();
        double dz = aim.z - vex.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        Projectile.spawnProjectileUsingShoot(
                arrow, level, ammo, dx, dy + flat * 0.2F, dz, ARROW_SPEED, ARROW_INACCURACY);
    }

    /**
     * The witch's aim: the target's position plus its motion, 1.1 blocks below its eyes, lifted by a fifth of the flat
     * distance. The height is measured from the potion's own start (a vex's eyes are low), not from the thrower's feet.
     */
    private static void throwPotion(ServerLevel level, BoundVex vex, ItemStack potion, LivingEntity at) {
        boolean lingering = potion.is(Items.LINGERING_POTION);
        Projectile thrown =
                lingering ? new ThrownLingeringPotion(level, vex, potion) : new ThrownSplashPotion(level, vex, potion);
        Vec3 motion = at.getDeltaMovement();
        double dx = at.getX() + motion.x - vex.getX();
        double dy = at.getEyeY() - 1.1F - thrown.getY();
        double dz = at.getZ() + motion.z - vex.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        float speed = flat <= 2.0 ? POTION_SPEED_CLOSE : POTION_SPEED;
        float inaccuracy = VexGear.isSupport(potion) ? SUPPORT_INACCURACY : POTION_INACCURACY;
        Projectile.spawnProjectileUsingShoot(thrown, level, potion, dx, dy + flat * 0.2, dz, speed, inaccuracy);
        vex.playSound(lingering ? SoundEvents.LINGERING_POTION_THROW : SoundEvents.SPLASH_POTION_THROW, 1.0F, 1.0F);
    }
}
