package io.github.willrieske.spellcraft.evoking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * What a Bound Vex launches (Evoking II Part 3 spec §4.3): a copy of the item in its main hand, never the item itself,
 * so nothing is used up (spec §4.5). The aim and the spawn follow the skeleton ({@code
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

    /** Potion inaccuracy. */
    static final float POTION_INACCURACY = 8.0F;

    private VexShots() {}

    /**
     * Shoots or throws a copy of the main-hand item at {@code at}; the held stack is untouched. Does nothing for an item
     * that is not launchable, or on the client.
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

    /** The skeleton's aim: the target's lower body, lifted by a fifth of the flat distance for the arrow's drop. */
    private static void shootArrow(
            ServerLevel level, BoundVex vex, AbstractArrow arrow, ItemStack ammo, LivingEntity at) {
        double dx = at.getX() - vex.getX();
        double dy = at.getY(1.0 / 3.0) - arrow.getY();
        double dz = at.getZ() - vex.getZ();
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
        Projectile.spawnProjectileUsingShoot(thrown, level, potion, dx, dy + flat * 0.2, dz, speed, POTION_INACCURACY);
        vex.playSound(lingering ? SoundEvents.LINGERING_POTION_THROW : SoundEvents.SPLASH_POTION_THROW, 1.0F, 1.0F);
    }
}
