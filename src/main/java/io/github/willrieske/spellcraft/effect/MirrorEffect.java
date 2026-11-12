package io.github.willrieske.spellcraft.effect;

import io.github.willrieske.spellcraft.evoking.MirrorImages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Strength of Mirror (Parts 6-7 spec §7.2). Milk-curable. {@link #onEffectAdded} spawns the images and
 * {@link #onMobRemoved} poofs them.
 *
 * <p>Functional spec §13.1, §13.2, §13.6.
 */
public final class MirrorEffect extends MobEffect {
    /** The effect's color, as the Mirror icon's ink. */
    public static final int COLOR = 0xE066FF;

    /**
     * Ticks a player may have been in the world before a Mirror with no image of theirs ends: {@code tickCount}
     * restarts on login, so this lets the chunk's entities load first. Functional spec §13.6.
     */
    public static final int LOGIN_GRACE_TICKS = 100;

    /** Makes the effect: beneficial, in the Mirror color. */
    public MirrorEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    // Functional spec §13.2
    @Override
    public void onEffectAdded(LivingEntity mob, int amplifier) {
        super.onEffectAdded(mob, amplifier);
        // The client never reaches this hook (the effect packet goes through forceAddEffect); the test is a guard.
        if (mob.level() instanceof ServerLevel) {
            MirrorImages.spawn(mob);
        }
    }

    /**
     * A mob carrier that is killed or discarded loses its effects in {@code LivingEntity.remove}, which calls this hook
     * (api-notes §26): its images go with it. A carrier removed any other way (unloaded) keeps them until their own
     * life ends, as the spec says.
     *
     * <p>Functional spec §13.6.
     */
    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        MirrorImages.poofAll(mob);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        return tickCount % 20 == 0;
    }

    /**
     * Ends the effect of a player carrier that has none of their images alive after the login grace: images poof on
     * their own life while their owner is offline, and effect timers do not run offline, so a player could otherwise
     * return with Mirror and no images (spec §10). A mob carrier is not checked: it has no login, and its
     * images end the effect themselves.
     *
     * <p>Functional spec §13.6.
     *
     * @return false (ends the effect) for such a player, else true
     */
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
        return !(mob instanceof Player player)
                || player.tickCount <= LOGIN_GRACE_TICKS
                || !MirrorImages.of(level, player.getUUID()).isEmpty();
    }
}
