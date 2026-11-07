package io.github.willrieske.spellcraft.effect;

import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A short effect the server puts on a casting player (spec §2): its only job is to carry a mod particle type in the
 * player's synced effect-particle list, so every client knows the player is casting and which spell. Applied with
 * {@code visible = true} (the sync) and {@code showIcon = false} (no HUD); the client hides it from the inventory.
 */
public final class CastingEffect extends MobEffect {
    /** The Evoking mint, shared with {@code ModEffects.EVOKING}. The icon script writes it as RGB. */
    static final int MINT = 0xD2F8EA;

    /**
     * @param particle the casting particle, looked up lazily (NeoForge's particle-factory constructor), so the
     *     particle registry need not be filled before the effect registry
     */
    public CastingEffect(Supplier<? extends ParticleOptions> particle) {
        super(MobEffectCategory.NEUTRAL, MINT, instance -> particle.get());
    }
}
