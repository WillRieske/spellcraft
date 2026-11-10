package io.github.willrieske.spellcraft.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

/**
 * A GameTest environment that pins the Overworld's time of day. Vanilla's {@code ClockTime} needs the clock's holder
 * when it is built, and {@code RegisterGameTestsEvent.registerEnvironment} runs before any registry is at hand, so
 * this looks the holder up from the level's own registry when the environment is set up (docs/api-notes.md §24).
 *
 * <p>The test-environment registry is one of the registries a server sends to every joining client, so this type
 * must encode: its codec is registered as {@code spellcraft:overworld_time}
 * ({@code SpellcraftGameTests.ENVIRONMENT_TYPES}). An unregistered codec fails that send, and the client then waits
 * on "Loading terrain" forever.
 *
 * @param time the clock's total ticks to set; the Overworld day is 24000 ticks long, so this is the time of day
 */
record OverworldTime(int time) implements TestEnvironmentDefinition<Long> {
    /** Encodes the one field; registered as this definition's type, so the registry can be sent to clients. */
    static final MapCodec<OverworldTime> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("time").forGetter(OverworldTime::time))
            .apply(instance, OverworldTime::new));

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
