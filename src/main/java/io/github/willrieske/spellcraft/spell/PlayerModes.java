package io.github.willrieske.spellcraft.spell;

import io.github.willrieske.spellcraft.spell.rules.PlayerMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/** Maps vanilla's game mode onto the pure core's {@link PlayerMode}. */
public final class PlayerModes {
    private PlayerModes() {}

    public static PlayerMode of(Player player) {
        return of(player.gameMode());
    }

    /**
     * A client player whose tab-list entry hasn't arrived has no game mode yet; treat it as survival. This only
     * affects the client's swing prediction: the server decides.
     */
    static PlayerMode of(@Nullable GameType type) {
        if (type == null) {
            return PlayerMode.SURVIVAL;
        }
        return switch (type) {
            case SURVIVAL -> PlayerMode.SURVIVAL;
            case ADVENTURE -> PlayerMode.ADVENTURE;
            case CREATIVE -> PlayerMode.CREATIVE;
            case SPECTATOR -> PlayerMode.SPECTATOR;
        };
    }
}
