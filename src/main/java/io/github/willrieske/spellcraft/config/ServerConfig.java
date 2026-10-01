package io.github.willrieske.spellcraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side (per-world) settings. Read at cast time, so edits apply to the next cast. */
public final class ServerConfig {
    private static final int TICKS_PER_SECOND = 20;
    /** Keeps seconds × 20 well inside int range (≈11.5 days). */
    private static final int MAX_SECONDS = 1_000_000;

    public static final ModConfigSpec.IntValue UPSIDE_DOWN_DURATION_SECONDS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("upsideDown");
        UPSIDE_DOWN_DURATION_SECONDS = builder.comment("How long Strength of Upside-Down lasts, in seconds.")
                .defineInRange("durationSeconds", 180, 1, MAX_SECONDS);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int upsideDownDurationTicks() {
        return UPSIDE_DOWN_DURATION_SECONDS.getAsInt() * TICKS_PER_SECOND;
    }
}
