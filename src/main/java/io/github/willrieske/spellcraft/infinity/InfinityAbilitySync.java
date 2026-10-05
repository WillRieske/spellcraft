package io.github.willrieske.spellcraft.infinity;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.PlayerModes;
import io.github.willrieske.spellcraft.spell.rules.InfinityAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps each player's creative abilities in line with Infinity, every server tick. Vanilla's abilities packet
 * syncs the client, which then opens the creative inventory by itself (docs/api-notes.md §11).
 */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class InfinityAbilitySync {
    private InfinityAbilitySync() {}

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
     * Grants, revokes or releases the player's creative abilities to match Infinity. Public so GameTests can run
     * one sync without waiting for a tick.
     *
     * @param player the player to sync
     */
    @SuppressWarnings("deprecation") // Abilities#mayfly is @Deprecated (-Xlint:all -Werror). Granting/revoking it
    // directly is the per-flag control we need: IPlayerExtension#mayFly() only reads, and folds in
    // CREATIVE_FLIGHT-attribute flight that we neither grant nor revoke.
    public static void sync(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        InfinityAbilities.Action action = InfinityAbilities.decide(new InfinityAbilities.State(
                PlayerModes.of(player),
                player.hasEffect(ModEffects.INFINITY),
                player.getData(InfinityAttachments.GRANTED),
                abilities.mayfly,
                abilities.instabuild));
        switch (action) {
            case GRANT -> {
                abilities.mayfly = true;
                abilities.instabuild = true;
                player.setData(InfinityAttachments.GRANTED, true);
                player.onUpdateAbilities();
            }
            case REVOKE -> {
                // The mode's defaults; survival and adventure also stop flight.
                player.gameMode().updatePlayerAbilities(abilities);
                player.setData(InfinityAttachments.GRANTED, false);
                player.onUpdateAbilities();
            }
            case RELEASE -> player.setData(InfinityAttachments.GRANTED, false);
            case NONE -> {}
        }
    }
}
