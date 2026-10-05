package io.github.willrieske.spellcraft.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Small helpers shared by the GameTest classes. */
public final class GameTestSupport {
    private GameTestSupport() {}

    public static void hold(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    /** Uses the main-hand item the way a right-click does (after vanilla's cooldown gate). */
    public static InteractionResult useHeld(GameTestHelper helper, ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return held.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    /**
     * Uses the main-hand item through {@code ServerPlayerGameMode#useItem}, the path a real right-click packet takes
     * (cooldown gate, creative handling and held-stack bookkeeping included), unlike {@link #useHeld}.
     */
    public static InteractionResult useHeldViaServer(ServerPlayer player) {
        return player.gameMode.useItem(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    /**
     * Empties the mock player's outbound packet buffer (its connection is an {@code EmbeddedChannel}, see
     * {@link MockPlayers}) and returns the action-bar messages it held.
     */
    public static List<Component> drainActionBar(ServerPlayer player) {
        List<Component> messages = new ArrayList<>();
        EmbeddedChannel channel =
                (EmbeddedChannel) player.connection.getConnection().channel();
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
                messages.add(chat.content());
            }
        }
        return messages;
    }

    public static int heldCount(ServerPlayer player) {
        return player.getMainHandItem().getCount();
    }

    public static MobEffectInstance requireEffect(
            GameTestHelper helper, ServerPlayer player, Holder<MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance == null) {
            throw helper.assertionException("expected " + effect.getRegisteredName() + " to be active");
        }
        return instance;
    }

    /** Runs {@code body} with a global server config value set to {@code temporary}, restoring it afterwards. */
    public static <T> void withConfig(ModConfigSpec.ConfigValue<T> value, T temporary, Runnable body) {
        T saved = value.get();
        value.set(temporary);
        try {
            body.run();
        } finally {
            value.set(saved);
        }
    }
}
