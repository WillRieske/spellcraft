package io.github.willrieske.spellcraft.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
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
        // Packets sent during a server tick are written but not flushed until the tick ends (MinecraftServer suspends
        // flushing for every player), so a message sent earlier in this tick is not readable until flushed here.
        player.connection.getConnection().flushChannel();
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
                messages.add(chat.content());
            }
        }
        return messages;
    }

    /**
     * {@code entity} saved the way a chunk save writes it (the {@code TagValueOutput} pattern), for reading its NBT
     * fields in a test.
     */
    public static CompoundTag saved(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.save(output);
        return output.buildResult();
    }

    /**
     * Saves {@code entity} and loads the save into a fresh entity of the same type, as a chunk load does: the copy
     * keeps the UUID, the attributes and the serialized data attachments. The copy is created, not added to the
     * level, so discard the original before adding it.
     */
    public static Entity reloaded(ServerLevel level, Entity entity) {
        return EntityType.create(
                        entity.getType(),
                        TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved(entity)),
                        level,
                        EntitySpawnReason.LOAD)
                .orElseThrow(() -> new IllegalStateException("could not reload " + entity.getType()));
    }

    /**
     * Runs {@code cleanup} once when the test ends, passed or failed. Not {@code GameTestHelper.runBeforeTestEnd}: that
     * runs only if the test is still going one tick before its timeout, and {@code GameTestInfo.tick} runs no
     * scheduled callback once a test has succeeded or failed, so it never runs after an early {@code succeed()}.
     */
    public static void whenDone(GameTestHelper helper, Runnable cleanup) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {}

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                cleanup.run();
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                cleanup.run();
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
        });
    }

    /**
     * A vex whose saved {@code owner} is {@code owner}: vanilla stores the owner but never calls the vex an ally of
     * it, and {@code Vex.setOwner} takes a mob, so the owner is loaded from the smallest tag that carries it. The
     * vex is created, not added to the level.
     */
    public static Vex vexOwnedBy(ServerLevel level, UUID owner) {
        Vex vex = EntityTypes.VEX.create(level, EntitySpawnReason.LOAD);
        if (vex == null) {
            throw new IllegalStateException("could not create a vex");
        }
        CompoundTag tag = new CompoundTag();
        tag.store("owner", UUIDUtil.CODEC, owner);
        vex.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        return vex;
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
