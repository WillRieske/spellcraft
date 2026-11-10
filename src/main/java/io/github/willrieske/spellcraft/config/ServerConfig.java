package io.github.willrieske.spellcraft.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side (per-world) settings. The durations are read at cast time; {@code maxTrailBlocks} is read each time a
 * flame is laid.
 */
public final class ServerConfig {
    private static final int TICKS_PER_SECOND = 20;
    /** Keeps seconds × 20 well inside int range (≈11.5 days). */
    private static final int MAX_SECONDS = 1_000_000;
    /** Upper bound for one player's trail; keeps a config typo from holding millions of flames. */
    private static final int MAX_TRAIL_BLOCKS = 65_536;

    public static final ModConfigSpec.IntValue UPSIDE_DOWN_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue TRAILING_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue TRAILING_MAX_FLAMES;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> TOP_TIER_CHANCE_PERCENT;
    /** The {@code evoking.teamMoves} setting: automatic triggers, or the J and K keys only. */
    public static final ModConfigSpec.EnumValue<TeamMoveMode> TEAM_MOVES;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("upsideDown");
        UPSIDE_DOWN_DURATION_SECONDS = builder.comment("How long Strength of Upside-Down lasts, in seconds.")
                .defineInRange("durationSeconds", 180, 1, MAX_SECONDS);
        builder.pop();
        builder.push("trailing");
        TRAILING_DURATION_SECONDS = builder.comment("How long Strength of Trailing lasts, in seconds.")
                .defineInRange("durationSeconds", 480, 1, MAX_SECONDS);
        TRAILING_MAX_FLAMES = builder.comment(
                        "The most flames one player's trail holds. Beyond it the oldest flames go out at once.")
                .defineInRange("maxTrailBlocks", 1024, 1, MAX_TRAIL_BLOCKS);
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
        builder.push("evoking");
        TEAM_MOVES = builder.comment(
                        "AUTOMATIC: Bound Vexes start team moves (Shield, Multishot, Evader, Raid) by themselves. "
                                + "MANUAL: only the J key (pick) and the K key (start) start them.")
                .defineEnum("teamMoves", TeamMoveMode.AUTOMATIC);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int upsideDownDurationTicks() {
        return UPSIDE_DOWN_DURATION_SECONDS.getAsInt() * TICKS_PER_SECOND;
    }

    public static int trailingDurationTicks() {
        return TRAILING_DURATION_SECONDS.getAsInt() * TICKS_PER_SECOND;
    }

    /** The most flames one player's trail holds (range-checked by NeoForge: 1-65536). */
    public static int trailingMaxFlames() {
        return TRAILING_MAX_FLAMES.getAsInt();
    }

    /** The configured chances, index = option (range-checked by NeoForge: exactly 3, each 0-100). */
    public static List<Integer> scribingTopTierChances() {
        return List.copyOf(TOP_TIER_CHANCE_PERCENT.get());
    }

    /** Whether the automatic team-move triggers run. */
    public static TeamMoveMode teamMoveMode() {
        return TEAM_MOVES.get();
    }

    private static boolean isPercent(Object value) {
        return value instanceof Integer percent && percent >= 0 && percent <= 100;
    }
}
