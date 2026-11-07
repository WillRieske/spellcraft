package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.particle.ModParticles;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;

/**
 * Which spell an entity is casting, read from vanilla's synced effect-particle list (spec §7): the server puts a
 * casting effect on the caster, and the effect's particle, one of {@link ModParticles}', reaches every client that
 * sees the caster. The accessor is opened by the access transformer (docs/api-notes.md §19).
 */
public final class CastingState {
    private CastingState() {}

    /** The spell {@code entity} is casting, if any. */
    public static Optional<EvokerSpell> of(LivingEntity entity) {
        List<ParticleOptions> particles = entity.getEntityData().get(LivingEntity.DATA_EFFECT_PARTICLES);
        if (particles.contains(ModParticles.CASTING_FANGS.get())) {
            return Optional.of(EvokerSpell.FANGS);
        }
        if (particles.contains(ModParticles.CASTING_VEXES.get())) {
            return Optional.of(EvokerSpell.VEXES);
        }
        return Optional.empty();
    }
}
