package io.github.willrieske.spellcraft.gametest;

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

    /** Tests that change no global state share this environment (one batch). */
    static final String SHARED = "shared";

    private static final Identifier EMPTY_STRUCTURE = Spellcraft.id("empty");
    private static final int MAX_TICKS = 20;

    /**
     * Test functions by environment, in registration order. Batches run one after another, so a test that
     * mutates global state (config, game rules) gets an environment of its own.
     */
    private static final Map<String, List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>>>
            BY_ENVIRONMENT = new LinkedHashMap<>();

    static {
        UpsideDownGameTests.registerAll();
        InfinityGameTests.registerAll();
        InfinityAbilityGameTests.registerAll();
        InfinityDeathGameTests.registerAll();
    }

    private SpellcraftGameTests() {}

    static void add(String environment, String name, Consumer<GameTestHelper> body) {
        BY_ENVIRONMENT
                .computeIfAbsent(environment, e -> new ArrayList<>())
                .add(TEST_FUNCTIONS.register(name, () -> body));
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        BY_ENVIRONMENT.forEach((environment, functions) -> {
            Holder<TestEnvironmentDefinition<?>> holder = event.registerEnvironment(Spellcraft.id(environment));
            functions.forEach(function -> test(event, holder, function));
        });
    }

    private static void test(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function) {
        event.registerTest(
                function.getId(),
                new FunctionGameTestInstance(
                        function.getKey(), new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, 0, true)));
    }
}
