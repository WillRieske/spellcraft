package io.github.willrieske.spellcraft.gametest;

import com.mojang.serialization.MapCodec;
import io.github.willrieske.spellcraft.Spellcraft;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers Spellcraft's GameTests. Run them with {@code ./gradlew runGameTestServer}. */
@EventBusSubscriber(modid = Spellcraft.MODID)
public final class SpellcraftGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, Spellcraft.MODID);

    /**
     * The types of the test environments this mod defines. A definition whose type is not registered cannot be
     * encoded, and the test-environment registry is sent to every joining client.
     */
    public static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition<?>>> ENVIRONMENT_TYPES =
            DeferredRegister.create(BuiltInRegistries.TEST_ENVIRONMENT_DEFINITION_TYPE, Spellcraft.MODID);

    static {
        ENVIRONMENT_TYPES.register("overworld_time", () -> OverworldTime.CODEC);
    }

    /** Tests that change no global state share this environment (one batch). */
    static final String SHARED = "shared";

    /** Tests that need the Overworld dark; a batch of its own. */
    static final String NIGHT = "night";

    private static final Identifier EMPTY_STRUCTURE = Spellcraft.id("empty");
    private static final int MAX_TICKS = 20;

    /** One registered test function with its own run limits. */
    private record Registered(
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks, int padding) {}

    /**
     * Test functions by environment, in registration order. Batches run one after another, so a test that
     * mutates global state (config, game rules) gets an environment of its own.
     */
    private static final Map<String, List<Registered>> BY_ENVIRONMENT = new LinkedHashMap<>();

    static {
        UpsideDownGameTests.registerAll();
        InfinityGameTests.registerAll();
        InfinityAbilityGameTests.registerAll();
        InfinityDeathGameTests.registerAll();
        ScribingGameTests.registerAll();
        TrailingGameTests.registerAll();
        TrailGameTests.registerAll();
        TrailEdgeCaseGameTests.registerAll();
        TrailLayerGameTests.registerAll();
        EvokingGameTests.registerAll();
        AlliesGameTests.registerAll();
        FangGameTests.registerAll();
        VexGameTests.registerAll();
        BoundVexCapGameTests.registerAll();
        BoundVexFollowGameTests.registerAll();
        BoundVexTargetGameTests.registerAll();
        BoundVexJobGameTests.registerAll();
        BoundVexStayGameTests.registerAll();
        BoundVexGearGameTests.registerAll();
        BoundVexCureGameTests.registerAll();
        BoundVexBribeGameTests.registerAll();
        BoundVexWorldGameTests.registerAll();
        BoundVexPostureGameTests.registerAll();
        BoundVexLavaGameTests.registerAll();
        BoundVexDrowningGameTests.registerAll();
        BoundVexVoidGameTests.registerAll();
        BoundVexFireballGameTests.registerAll();
        BoundVexCrystalGameTests.registerAll();
        BoundVexCritGameTests.registerAll();
        BoundVexRescueJobGameTests.registerAll();
        BoundVexRescueLifecycleGameTests.registerAll();
        BoundVexTeamMoveRosterGameTests.registerAll();
        BoundVexTeamMoveLifecycleGameTests.registerAll();
        GolemGameTests.registerAll();
    }

    private SpellcraftGameTests() {}

    static void add(String environment, String name, Consumer<GameTestHelper> body) {
        add(environment, name, MAX_TICKS, 0, body);
    }

    /**
     * Registers a test with its own tick limit and padding. Padding spaces the test's structure from its neighbors
     * by that many blocks on every side (and clears that box to air, below included): for tests whose fangs or vexes
     * would otherwise reach another test's mobs.
     */
    static void add(String environment, String name, int maxTicks, int padding, Consumer<GameTestHelper> body) {
        BY_ENVIRONMENT
                .computeIfAbsent(environment, e -> new ArrayList<>())
                .add(new Registered(TEST_FUNCTIONS.register(name, () -> body), maxTicks, padding));
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        BY_ENVIRONMENT.forEach((environment, registered) -> {
            int time = environment.equals(NIGHT) ? 18000 : 6000;
            Holder<TestEnvironmentDefinition<?>> holder =
                    event.registerEnvironment(Spellcraft.id(environment), new OverworldTime(time));
            registered.forEach(entry -> test(event, holder, entry));
        });
    }

    private static void test(
            RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, Registered entry) {
        event.registerTest(
                entry.function().getId(),
                new FunctionGameTestInstance(
                        entry.function().getKey(),
                        // Arguments, in order: environment, dimension, structure, maxTicks, setupTicks, required,
                        // rotation, manualOnly, maxAttempts, requiredSuccesses, skyAccess, padding (see TestData).
                        new TestData<>(
                                environment,
                                Level.OVERWORLD,
                                EMPTY_STRUCTURE,
                                entry.maxTicks(),
                                0,
                                true,
                                Rotation.NONE,
                                false,
                                1,
                                1,
                                false,
                                entry.padding())));
    }
}
