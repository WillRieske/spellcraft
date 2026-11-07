package io.github.willrieske.spellcraft.client.evoking;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.particle.ModParticles;
import io.github.willrieske.spellcraft.spell.rules.EvokerPose;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * What a casting player looks like, apart from the arms (spec §7): nothing for the synced casting particles
 * themselves (their providers return null, so vanilla's random swirl draws nothing), the evoker's two hand particles
 * every tick for every casting player in view, and no "Casting" rows in the inventory.
 */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class CastingParticles {
    /** The evoker's spell colors ({@code SpellcasterIllager.IllagerSpell}): fangs, then vexes. */
    private static final ColorParticleOption FANGS_COLOR =
            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.4F, 0.3F, 0.35F);

    private static final ColorParticleOption VEXES_COLOR =
            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7F, 0.7F, 0.8F);

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
    }

    /**
     * Hides the two casting effects' HUD and inventory entries.
     *
     * @param event the mod-bus event
     */
    @SubscribeEvent
    public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(HIDDEN, ModEffects.CASTING_FANGS, ModEffects.CASTING_VEXES);
    }

    /**
     * Adds the two hand particles for every casting player in the level, skipping the tick while the game is paused
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
        for (AbstractClientPlayer player : level.players()) {
            CastingState.of(player).ifPresent(spell -> handParticles(level, player, spell));
        }
    }

    /** The evoker's two particles, one at each hand, following the body and swaying with the tick. */
    private static void handParticles(ClientLevel level, AbstractClientPlayer player, EvokerSpell spell) {
        ColorParticleOption color = spell == EvokerSpell.FANGS ? FANGS_COLOR : VEXES_COLOR;
        float scale = player.getScale();
        EvokerPose.HandOffset hand = EvokerPose.handOffset(player.yBodyRot, player.tickCount);
        double y = player.getY() + EvokerPose.HAND_HEIGHT * scale;
        level.addParticle(color, player.getX() + hand.dx() * scale, y, player.getZ() + hand.dz() * scale, 0, 0, 0);
        level.addParticle(color, player.getX() - hand.dx() * scale, y, player.getZ() - hand.dz() * scale, 0, 0, 0);
    }
}
