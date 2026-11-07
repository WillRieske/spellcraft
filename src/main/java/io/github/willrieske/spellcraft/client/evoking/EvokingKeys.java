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
 * {@code Minecraft.handleKeybinds}, so clicks consumed here never reach it (docs/api-notes.md §19).
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

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(FANGS);
        event.register(VEXES);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean evoking = player != null && player.hasEffect(ModEffects.EVOKING);
        send(FANGS, EvokerSpell.FANGS, evoking);
        send(VEXES, EvokerSpell.VEXES, evoking);
        KeyMapping swap = minecraft.options.keySwapOffhand;
        if (evoking && (FANGS.same(swap) || VEXES.same(swap))) {
            while (swap.consumeClick()) {
                // swallowed: a casting key casts instead of swapping while Evoking is on
            }
        }
    }

    /** Drains the mapping's clicks; each one is a request while Evoking is on, and nothing otherwise. */
    private static void send(KeyMapping key, EvokerSpell spell, boolean evoking) {
        while (key.consumeClick()) {
            if (evoking) {
                ClientPacketDistributor.sendToServer(new EvokingRequest(spell));
            }
        }
    }
}
