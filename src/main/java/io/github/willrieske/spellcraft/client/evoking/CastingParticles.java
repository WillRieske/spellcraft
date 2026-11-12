package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.particle.ModParticles;
import io.github.willrieske.spellcraft.spell.rules.EvokerPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * What a casting entity looks like, apart from the arms (spec §7): nothing for the synced casting particles
 * themselves (their providers return null, so vanilla's random swirl draws nothing), the evoker's two hand particles
 * every tick for every casting living entity in view, and no "Casting" rows in the inventory.
 */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class CastingParticles {
    /** The evoker's spell colors ({@code SpellcasterIllager.IllagerSpell}): fangs, vexes, then a scroll's purple. */
    private static final ColorParticleOption FANGS_COLOR =
            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.4F, 0.3F, 0.35F);

    private static final ColorParticleOption VEXES_COLOR =
            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7F, 0.7F, 0.8F);

    private static final ColorParticleOption SCROLL_COLOR =
            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFFB24CFF);

    /** A player's standing height in blocks: {@code EvokerPose.HAND_HEIGHT} and the hand offset are for this size. */
    private static final float PLAYER_HEIGHT = 1.8F;

    private static final IClientMobEffectExtensions HIDDEN = new IClientMobEffectExtensions() {
        @Override
        public boolean isVisibleInInventory(MobEffectInstance instance) {
            return false;
        }

        @Override
        public boolean isVisibleInGui(MobEffectInstance instance) {
            return false;
        }
    };

    private CastingParticles() {}

    /**
     * Registers a provider for each casting particle type that returns null (not "no provider"), which
     * {@code ParticleEngine.createParticle} tolerates (ParticleEngine.java:59-71). They only mark the state in the
     * synced effect list, so vanilla's effect swirl and {@code /particle} draw nothing for these types.
     *
     * @param event the mod-bus event
     */
    @SubscribeEvent
    public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        event.registerSpecial(ModParticles.CASTING_FANGS.get(), (options, level, x, y, z, xa, ya, za, random) -> null);
        event.registerSpecial(ModParticles.CASTING_VEXES.get(), (options, level, x, y, z, xa, ya, za, random) -> null);
        event.registerSpecial(ModParticles.CASTING_SCROLL.get(), (options, level, x, y, z, xa, ya, za, random) -> null);
    }

    /**
     * Hides the three casting effects' HUD and inventory entries.
     *
     * @param event the mod-bus event
     */
    @SubscribeEvent
    public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(HIDDEN, ModEffects.CASTING_FANGS, ModEffects.CASTING_VEXES, ModEffects.CASTING_SCROLL);
    }

    /**
     * Adds the two hand particles for every casting living entity in the level, skipping the tick while the game is
     * paused
     * or the level's tick rate is frozen (the particle engine does not tick then, so the particles would pile up).
     *
     * @param event the game-bus event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || !level.tickRateManager().runsNormally()) {
            // Post fires while paused (Minecraft.java:1941) and under /tick freeze; the particle engine does not tick
            // in either case, but players keep ticking, so adding would pile up.
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living) {
                CastingState.of(living).ifPresent(look -> handParticles(level, living, look));
            }
        }
    }

    /**
     * The evoker's two particles, one at each hand, following the body and swaying with the tick, scaled to the entity.
     * The scale is the standing size, not the current box: a sneaking or swimming caster keeps Part 5's particles.
     */
    private static void handParticles(ClientLevel level, LivingEntity caster, CastLook look) {
        ColorParticleOption color = switch (look) {
            case FANGS -> FANGS_COLOR;
            case VEXES -> VEXES_COLOR;
            case SCROLL -> SCROLL_COLOR;
        };
        float size = caster.getScale() * caster.getType().getHeight() / PLAYER_HEIGHT;
        EvokerPose.HandOffset hand = EvokerPose.handOffset(caster.yBodyRot, caster.tickCount);
        double y = caster.getY() + EvokerPose.HAND_HEIGHT * size;
        level.addParticle(color, caster.getX() + hand.dx() * size, y, caster.getZ() + hand.dz() * size, 0, 0, 0);
        level.addParticle(color, caster.getX() - hand.dx() * size, y, caster.getZ() - hand.dz() * size, 0, 0, 0);
    }
}
