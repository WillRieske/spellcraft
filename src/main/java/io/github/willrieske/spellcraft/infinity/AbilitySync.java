package io.github.willrieske.spellcraft.infinity;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.PlayerModes;
import io.github.willrieske.spellcraft.spell.rules.GrantedAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps each player's creative-style abilities in line with Infinity and Ghostrail, every server tick (Parts 6-7
 * spec §7.3; formerly {@code AbilitySync}). Vanilla's abilities packet syncs the client, which then opens the
 * creative inventory by itself (docs/api-notes.md §11). The decision is the pure {@link GrantedAbilities}. This
 * class is the one driver of the sync: {@code Ghostrail.tick} (Task 7) does not call it, and the two do not depend
 * on each other's order in the Post event (a GameTest that steps {@code Ghostrail.tick} by hand and needs the
 * abilities settled calls {@link #sync} itself).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class AbilitySync {
    private AbilitySync() {}

    /**
     * Syncs the abilities of every server-side player after each tick.
     *
     * @param event the post-tick event for a player
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    /**
     * Applies one {@link GrantedAbilities} plan: a reset to the game mode's abilities when a source ended, the
     * grants of what is still active, both marks. Public so GameTests can run one sync without waiting for a tick.
     *
     * @param player the player to sync
     */
    @SuppressWarnings("deprecation") // Abilities#mayfly is @Deprecated (-Xlint:all -Werror). Granting/revoking it
    // directly is the per-flag control we need: IPlayerExtension#mayFly() only reads, and folds in
    // CREATIVE_FLIGHT-attribute flight that we neither grant nor revoke.
    public static void sync(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        GrantedAbilities.Plan plan = GrantedAbilities.decide(new GrantedAbilities.State(
                PlayerModes.of(player),
                player.hasEffect(ModEffects.INFINITY),
                player.hasEffect(ModEffects.GHOSTRAIL),
                player.getData(InfinityAttachments.GRANTED),
                player.getData(InfinityAttachments.GHOSTRAIL_GRANTED),
                abilities.mayfly,
                abilities.instabuild,
                abilities.flying));
        if (plan.reset()) {
            // The mode's defaults; survival and adventure also stop flight.
            player.gameMode().updatePlayerAbilities(abilities);
        }
        abilities.mayfly |= plan.mayfly();
        abilities.instabuild |= plan.instabuild();
        if (plan.flying()) {
            // noPhysics skips collision, so a player who is not flying would sink through the floor
            // (as in spectator mode)
            abilities.flying = true;
        }
        if (plan.infinityMarked() != player.getData(InfinityAttachments.GRANTED)) {
            player.setData(InfinityAttachments.GRANTED, plan.infinityMarked());
        }
        if (plan.ghostrailMarked() != player.getData(InfinityAttachments.GHOSTRAIL_GRANTED)) {
            player.setData(InfinityAttachments.GHOSTRAIL_GRANTED, plan.ghostrailMarked());
        }
        if (plan.changesAbilities()) {
            player.onUpdateAbilities();
        }
    }
}
