package io.github.willrieske.spellcraft.client.evoking;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.EvokingRequest;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.jspecify.annotations.Nullable;

/**
 * The four Evoking keys: two casting keys (Evoking I spec §2, §7; Evoking II §2.1) and J and K for team moves (Part 6
 * spec §3.3). A casting-key press while the local player has Evoking sends an {@link EvokingRequest}, and so does a
 * K press whether or not it has Evoking; the server decides what each does. Fangs default to Y (an unbound key in
 * vanilla), vexes to V, J cycles the selected team move and K starts it. A key bound to the same key as vanilla's
 * swap-offhand (if the user rebinds it to F) swallows the swap: {@code ClientTickEvent.Pre} runs before
 * {@code Minecraft.handleKeybinds}, so clicks consumed here never reach it (docs/api-notes.md §19). The swallow
 * rule: the swap-offhand clicks are drained only on a tick where such a key consumed at least one click that did
 * something (a casting key only while Evoking is on; J and K always), so with Fangs rebound to Shift+F a plain F
 * press still swaps. With the defaults it never fires.
 *
 * <p>Functional spec §2.1, §11.12.
 */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class EvokingKeys {
    /** The "spellcraft:spells" controls category all four keys are listed under. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Spellcraft.id("spells"));

    /**
     * Default Y (scancode 28; no vanilla binding uses it, docs/api-notes.md §20). Named {@code fangs_y} rather than
     * {@code fangs} so a saved {@code options.txt} from round one, which pinned the old name to F, falls back to the
     * new
     * default: vanilla stores bindings by mapping name.
     */
    public static final KeyMapping FANGS = new KeyMapping(
            "key.spellcraft.fangs_y",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Y,
            CATEGORY);

    /** Default V: vanilla's F3-chord debug key, which needs F3 held. */
    public static final KeyMapping VEXES = new KeyMapping(
            "key.spellcraft.vexes",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_V,
            CATEGORY);

    /** Default J (scancode 13): no vanilla binding uses it, docs/api-notes.md §25. Cycles the selected team move. */
    public static final KeyMapping TEAM_MOVE_CYCLE = new KeyMapping(
            "key.spellcraft.team_move_cycle",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_J,
            CATEGORY);

    /** Default K (scancode 14): no vanilla binding uses it. Asks the server to start the selected team move. */
    public static final KeyMapping TEAM_MOVE_GO = new KeyMapping(
            "key.spellcraft.team_move_go",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K,
            CATEGORY);

    /** The move J has selected. Client-side only; starts at Shield each session (spec §3.3). */
    private static TeamMove selected = TeamMove.SHIELD;

    private EvokingKeys() {}

    /**
     * Registers the Evoking key category, the two casting keys and the two team-move keys.
     *
     * @param event the mod-bus event
     */
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(FANGS);
        event.register(VEXES);
        event.register(TEAM_MOVE_CYCLE);
        event.register(TEAM_MOVE_GO);
    }

    /**
     * Sends one request per click of a casting key while Evoking is on, and swallows the swap-offhand clicks on a
     * tick where a casting key that is bound to the swap key ({@code KeyMapping.same}) consumed a click, so that
     * press casts instead of swapping. A key that does not share the swap key never swallows it. Functional spec §2.1.
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
        int teamMoves = teamMoveKeys(player, swap);
        if ((evoking && swallow > 0) || teamMoves > 0) {
            while (swap.consumeClick()) {
                // swallowed: a key acted instead of swapping (a casting key while Evoking is on, J and K always)
            }
        }
    }

    /**
     * J cycles the selection and shows it on the action bar; K sends the selected move. Neither needs Evoking (the
     * server decides, and does not need it either). In automatic mode J still cycles and the server's answer to K is
     * "Team moves are automatic": the client cannot see the server's setting. Functional spec §11.12.
     *
     * @return the number of clicks of a key that is bound to the same key as {@code swap}, whose swap clicks the
     *     caller swallows (zero at the defaults)
     */
    private static int teamMoveKeys(@Nullable LocalPlayer player, KeyMapping swap) {
        int shared = 0;
        while (TEAM_MOVE_CYCLE.consumeClick()) {
            if (TEAM_MOVE_CYCLE.same(swap)) {
                shared++;
            }
            selected = selected.next();
            if (player != null) {
                player.sendOverlayMessage(Component.translatable(
                        "message.spellcraft.team_move_selected",
                        Component.translatable("team_move.spellcraft." + selected.id())));
            }
        }
        while (TEAM_MOVE_GO.consumeClick()) {
            if (TEAM_MOVE_GO.same(swap)) {
                shared++;
            }
            if (player != null) {
                ClientPacketDistributor.sendToServer(EvokingRequest.teamMove(selected));
            }
        }
        return shared;
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
                ClientPacketDistributor.sendToServer(EvokingRequest.cast(spell));
                sent++;
            }
        }
        return sent;
    }
}
