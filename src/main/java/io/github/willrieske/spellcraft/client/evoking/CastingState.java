package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.particle.ModParticles;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;

/**
 * Which look an entity's casting has, read from vanilla's synced effect-particle list (spec §7): the server puts a
 * casting effect on the caster, and the effect's particle, one of {@link ModParticles}', reaches every client that
 * sees the caster. The accessor is opened by the access transformer (docs/api-notes.md §19).
 *
 * <p>Functional spec §2.5.
 */
public final class CastingState {
    private CastingState() {}

    /**
     * The look of the spell {@code entity} is casting, if any.
     *
     * @param entity any living entity, local or remote
     * @return the casting look, or empty
     */
    public static Optional<CastLook> of(LivingEntity entity) {
        List<ParticleOptions> particles = entity.getEntityData().get(LivingEntity.DATA_EFFECT_PARTICLES);
        if (particles.contains(ModParticles.CASTING_FANGS.get())) {
            return Optional.of(CastLook.FANGS);
        }
        if (particles.contains(ModParticles.CASTING_VEXES.get())) {
            return Optional.of(CastLook.VEXES);
        }
        return particles.contains(ModParticles.CASTING_SCROLL.get()) ? Optional.of(CastLook.SCROLL) : Optional.empty();
    }
}
