package io.github.willrieske.spellcraft.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player visual settings. */
public final class ClientConfig {
    private static final int TICKS_PER_SECOND = 20;

    public static final ModConfigSpec.DoubleValue UPSIDE_DOWN_TRANSITION_SECONDS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("upsideDown");
        UPSIDE_DOWN_TRANSITION_SECONDS = builder.comment(
                        "How long the camera takes to roll over, in seconds. 0 = snap.")
                .defineInRange("transitionSeconds", 1.0, 0.0, 10.0);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static int transitionTicks() {
        return (int) Math.round(UPSIDE_DOWN_TRANSITION_SECONDS.getAsDouble() * TICKS_PER_SECOND);
    }
}
