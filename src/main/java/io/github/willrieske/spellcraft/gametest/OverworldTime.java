package io.github.willrieske.spellcraft.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

/**
 * A GameTest environment that pins the Overworld's time of day. Vanilla's {@code ClockTime} needs the clock's holder
 * when it is built, and {@code RegisterGameTestsEvent.registerEnvironment} runs before any registry is at hand, so
 * this looks the holder up from the level's own registry when the environment is set up (docs/api-notes.md §24).
 *
 * @param time the clock's total ticks to set; the Overworld day is 24000 ticks long, so this is the time of day
 */
record OverworldTime(int time) implements TestEnvironmentDefinition<Long> {
    /** The codec only serves data packs, which never hold this environment; it is a unit codec. */
    private static final MapCodec<OverworldTime> CODEC = MapCodec.unit(() -> new OverworldTime(0));

    /**
     * Sets the Overworld clock to {@link #time()}.
     *
     * @param level any level of the server
     * @return the clock's previous total ticks, for {@link #teardown}
     */
    @Override
    public Long setup(ServerLevel level) {
        Holder<WorldClock> clock = overworldClock(level);
        long previous = level.getServer().clockManager().getInstance(clock).totalTicks();
        level.getServer().clockManager().setTotalTicks(clock, time);
        return previous;
    }

    /**
     * Puts the Overworld clock back.
     *
     * @param level any level of the server
     * @param previous what {@link #setup} returned
     */
    @Override
    public void teardown(ServerLevel level, Long previous) {
        level.getServer().clockManager().setTotalTicks(overworldClock(level), previous);
    }

    @Override
    public MapCodec<OverworldTime> codec() {
        return CODEC;
    }

    /** The Overworld clock's holder from the level's own registry, the only holder the clock manager accepts. */
    private static Holder<WorldClock> overworldClock(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
    }
}
