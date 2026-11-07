package io.github.willrieske.spellcraft.client.evoking;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.EvokingRequest;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * The two casting keys (spec §2, §7). A press while the local player has Evoking sends an {@link EvokingRequest};
 * the server decides what it does. While Evoking is on, a casting key bound to the same key as vanilla's
 * swap-offhand (either of the two, if the user rebinds) swallows the swap: {@code ClientTickEvent.Pre} runs before
 * {@code Minecraft.handleKeybinds}, so clicks consumed here never reach it (docs/api-notes.md §19). The swallow rule:
 * the swap-offhand clicks are drained only on a tick where a casting key that is bound to the same key as swap
 * consumed at least one click, so with Fangs rebound to Shift+F a plain F press still swaps.
 */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class EvokingKeys {
    /** The "spellcraft:spells" controls category both casting keys are listed under. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Spellcraft.id("spells"));

    /** Default F: vanilla's swap-offhand key, taken over only while Evoking is on. */
    public static final KeyMapping FANGS = new KeyMapping(
            "key.spellcraft.fangs",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F,
            CATEGORY);

    /** Default V: vanilla's F3-chord debug key, which needs F3 held. */
    public static final KeyMapping VEXES = new KeyMapping(
            "key.spellcraft.vexes",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_V,
            CATEGORY);

    private EvokingKeys() {}

    /**
     * Registers the Evoking key category and the two casting keys.
     *
     * @param event the mod-bus event
     */
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(FANGS);
        event.register(VEXES);
    }

    /**
     * Sends one request per click of a casting key while Evoking is on, and swallows the swap-offhand clicks on a
     * tick where a casting key that is bound to the swap key ({@code KeyMapping.same}) consumed a click, so that
     * press casts instead of swapping. A key that does not share the swap key never swallows it.
     *
     * @param event the game-bus event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean evoking = player != null && player.hasEffect(ModEffects.EVOKING);
        KeyMapping swap = minecraft.options.keySwapOffhand;
        int swallow = 0;
        int fangs = send(FANGS, EvokerSpell.FANGS, evoking);
        if (FANGS.same(swap)) {
            swallow += fangs;
        }
        int vexes = send(VEXES, EvokerSpell.VEXES, evoking);
        if (VEXES.same(swap)) {
            swallow += vexes;
        }
        if (evoking && swallow > 0) {
            while (swap.consumeClick()) {
                // swallowed: a casting key cast instead of swapping while Evoking is on
            }
        }
    }

    /**
     * Drains the mapping's clicks; each one is a request while Evoking is on, and nothing otherwise.
     *
     * @return the number of clicks that sent a request this tick (zero when Evoking is off)
     */
    private static int send(KeyMapping key, EvokerSpell spell, boolean evoking) {
        int sent = 0;
        while (key.consumeClick()) {
            if (evoking) {
                ClientPacketDistributor.sendToServer(new EvokingRequest(spell));
                sent++;
            }
        }
        return sent;
    }
}
