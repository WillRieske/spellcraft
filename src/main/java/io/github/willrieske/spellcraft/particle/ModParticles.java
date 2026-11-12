package io.github.willrieske.spellcraft.particle;

import io.github.willrieske.spellcraft.Spellcraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The casting particles (spec §7). Each is the particle of one casting effect: vanilla syncs it in the caster's
 * effect-particle list to every client, which is how clients learn that a player is casting. The client draws
 * nothing for them (its providers return null) and adds the evoker's hand particles itself.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Spellcraft.MODID);

    /** Carried by {@code ModEffects.CASTING_FANGS}: the player is casting fangs. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CASTING_FANGS =
            PARTICLE_TYPES.register("casting_fangs", () -> new SimpleParticleType(false));

    /** Carried by {@code ModEffects.CASTING_VEXES}: the player is summoning vexes. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CASTING_VEXES =
            PARTICLE_TYPES.register("casting_vexes", () -> new SimpleParticleType(false));

    /** Carried by {@code ModEffects.CASTING_SCROLL}: a Caster or a villager ghost is casting a scroll. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CASTING_SCROLL =
            PARTICLE_TYPES.register("casting_scroll", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
