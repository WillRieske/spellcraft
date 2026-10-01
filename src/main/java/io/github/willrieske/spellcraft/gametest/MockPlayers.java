package io.github.willrieske.spellcraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * GameTest players. Vanilla's {@code makeMockServerPlayerInLevel()} hard-codes {@code gameMode()} to CREATIVE
 * (docs/api-notes.md §14), and the cast rules depend on the game mode, so this copies its connection setup
 * without that override.
 */
public final class MockPlayers {
    private MockPlayers() {}

    /** A connected player (effects and abilities send packets) in the test level, in {@code mode}. */
    public static ServerPlayer inLevel(GameTestHelper helper, GameType mode) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "spellcraft-mock"), false);
        ServerPlayer player =
                new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // The channel installs the connection as its handler; packets sent to the mock go nowhere.
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        return player;
    }

    /** Moves {@code player} into this test's area, so its drops don't mix with other tests' drops. */
    public static void moveIntoTest(GameTestHelper helper, ServerPlayer player) {
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.teleportTo(helper.getLevel(), pos.x, pos.y, pos.z, Set.of(), 0.0F, 0.0F, false);
    }
}
