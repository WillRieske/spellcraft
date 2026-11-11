package io.github.willrieske.spellcraft.effect;

import io.github.willrieske.spellcraft.evoking.MirrorImages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Strength of Mirror (Parts 6-7 spec §7.2). Milk-curable. {@link #onEffectAdded} spawns the images and
 * {@link #onMobRemoved} poofs them.
 */
public final class MirrorEffect extends MobEffect {
    /** The effect's color, as the Mirror icon's ink. */
    public static final int COLOR = 0xE066FF;

    /** Makes the effect: beneficial, in the Mirror color. */
    public MirrorEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

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
     */
    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        MirrorImages.poofAll(mob);
    }
}
