package io.github.willrieske.spellcraft.client;

import io.github.willrieske.spellcraft.Spellcraft;
import io.github.willrieske.spellcraft.client.upsidedown.UpsideDownRollState;
import io.github.willrieske.spellcraft.client.upsidedown.UpsideDownView;
import io.github.willrieske.spellcraft.effect.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/**
 * Thin plumbing between Minecraft and the pure Upside-Down core. Reads the vanilla-synced
 * effect; never decides state.
 */
@EventBusSubscriber(modid = Spellcraft.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    /** Mutable client state, held in one final instance (Error Prone: NonFinalStaticField). */
    private static final class Tracker {
        final UpsideDownRollState roll = new UpsideDownRollState();

        @Nullable
        LocalPlayer lastPlayer;

        @Nullable
        Level lastLevel;

        double renderedRoll;
        boolean stamped;
        long rollStampNanos;
    }

    private static final long STALE_AFTER_NANOS = 500_000_000L;

    private static final Tracker TRACKER = new Tracker();

    private ClientEvents() {}

    /**
     * The roll most recently applied to the camera: what the player sees right now. The camera
     * event is not posted on every camera path (the experimental new-minecart lerp skips it), so a
     * value older than 500 ms of wall-clock time is stale and reads as 0. A frame gap under 500 ms
     * keeps compensation; the camera event being absent for longer (e.g. the new-minecart lerp path)
     * reads as not flipped, so controls stay normal whenever the flip is not being drawn.
     */
    public static double renderedRoll() {
        boolean fresh = TRACKER.stamped && System.nanoTime() - TRACKER.rollStampNanos <= STALE_AFTER_NANOS;
        return fresh ? TRACKER.renderedRoll : 0.0;
    }

    @SubscribeEvent
    // Identity is the point: a new LocalPlayer object means login, respawn or a dimension change
    // (Entity.equals compares entity ids, which is not what we want here).
    @SuppressWarnings("ReferenceEquality")
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            TRACKER.lastPlayer = null;
            TRACKER.lastLevel = null;
            TRACKER.roll.tick(false, true, 0);
            TRACKER.renderedRoll = 0.0;
            return;
        }
        Level level = player.level();
        boolean resync = player != TRACKER.lastPlayer || level != TRACKER.lastLevel;
        TRACKER.lastPlayer = player;
        TRACKER.lastLevel = level;
        TRACKER.roll.tick(player.hasEffect(ModEffects.UPSIDE_DOWN), resync, ClientConfig.transitionTicks());
    }

    @SubscribeEvent
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double roll = TRACKER.roll.rollDegrees(event.getPartialTick());
        TRACKER.renderedRoll = roll;
        TRACKER.rollStampNanos = System.nanoTime();
        TRACKER.stamped = true;
        if (roll != 0.0) {
            // Add, don't replace: another mod's roll is preserved (spec §3).
            event.setRoll(event.getRoll() + (float) roll);
        }
    }

    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        ClientInput input = event.getInput();
        Vec2 move = input.moveVector;
        float strafe = (float) UpsideDownView.strafe(move.x, renderedRoll());
        if (strafe == move.x) {
            return; // not inverted, or not strafing
        }
        // KeyboardInput.tick() rebuilds both fields every tick, so this never accumulates.
        input.moveVector = new Vec2(strafe, move.y);
        Input keys = input.keyPresses;
        input.keyPresses = new Input(
                keys.forward(), keys.backward(), keys.right(), keys.left(), keys.jump(), keys.shift(), keys.sprint());
    }
}
