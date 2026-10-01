package io.github.willrieske.spellcraft.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A used scroll withers away: a puff of dark smoke at the caster's hand and a short fizzle (docs/api-notes.md §17). */
final class ScrollFx {
    private static final int SMOKE_COUNT = 12;
    private static final double SMOKE_SPREAD = 0.15;
    private static final double SMOKE_SPEED = 0.01;
    private static final double HAND_REACH = 0.5;
    private static final double HAND_DROP = 0.3;

    private ScrollFx() {}

    static void wither(ServerLevel level, Player player) {
        Vec3 look = player.getLookAngle();
        double x = player.getX() + look.x * HAND_REACH;
        double y = player.getEyeY() - HAND_DROP + look.y * HAND_REACH;
        double z = player.getZ() + look.z * HAND_REACH;
        level.sendParticles(
                ParticleTypes.LARGE_SMOKE, x, y, z, SMOKE_COUNT, SMOKE_SPREAD, SMOKE_SPREAD, SMOKE_SPREAD, SMOKE_SPEED);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS,
                0.5F,
                1.4F);
    }
}
