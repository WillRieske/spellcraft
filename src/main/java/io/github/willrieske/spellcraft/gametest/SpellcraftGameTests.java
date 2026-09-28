package io.github.willrieske.spellcraft.gametest;

import io.github.willrieske.spellcraft.Spellcraft;
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

    private static final Identifier EMPTY_STRUCTURE = Spellcraft.id("empty");
    private static final int MAX_TICKS = 20;

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EFFECT_IS_NEUTRAL_MARKER =
            TEST_FUNCTIONS.register("effect_is_neutral_marker", () -> UpsideDownGameTests::effectIsANeutralMarker);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MILK_CLEARS_EFFECT =
            TEST_FUNCTIONS.register("milk_clears_effect", () -> UpsideDownGameTests::milkClearsTheEffect);

    private SpellcraftGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> shared = event.registerEnvironment(Spellcraft.id("shared"));
        test(event, shared, EFFECT_IS_NEUTRAL_MARKER);
        test(event, shared, MILK_CLEARS_EFFECT);
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
