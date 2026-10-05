package io.github.willrieske.spellcraft.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side (per-world) settings. Read at cast time, so edits apply to the next cast. */
public final class ServerConfig {
    private static final int TICKS_PER_SECOND = 20;
    /** Keeps seconds × 20 well inside int range (≈11.5 days). */
    private static final int MAX_SECONDS = 1_000_000;

    public static final ModConfigSpec.IntValue UPSIDE_DOWN_DURATION_SECONDS;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> TOP_TIER_CHANCE_PERCENT;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("upsideDown");
        UPSIDE_DOWN_DURATION_SECONDS = builder.comment("How long Strength of Upside-Down lasts, in seconds.")
                .defineInRange("durationSeconds", 180, 1, MAX_SECONDS);
        builder.pop();
        builder.push("scribing");
        TOP_TIER_CHANCE_PERCENT = builder.comment(
                        "Chance (0-100) that scribing option 1, 2, 3 draws an extreme-tier spell. Exactly 3 values.")
                .defineList(
                        List.of("topTierChancePercent"),
                        () -> List.of(25, 50, 75),
                        () -> 50,
                        ServerConfig::isPercent,
                        ModConfigSpec.Range.of(3, 3));
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int upsideDownDurationTicks() {
        return UPSIDE_DOWN_DURATION_SECONDS.getAsInt() * TICKS_PER_SECOND;
    }

    /** The configured chances, index = option (range-checked by NeoForge: exactly 3, each 0-100). */
    public static List<Integer> scribingTopTierChances() {
        return List.copyOf(TOP_TIER_CHANCE_PERCENT.get());
    }

    private static boolean isPercent(Object value) {
        return value instanceof Integer percent && percent >= 0 && percent <= 100;
    }
}
