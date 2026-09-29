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

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CAST_GRANTS_FULL_DURATION =
            TEST_FUNCTIONS.register("cast_grants_full_duration", () -> UpsideDownGameTests::castGrantsTheFullDuration);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RECAST_RESETS_DURATION =
            TEST_FUNCTIONS.register("recast_resets_duration", () -> UpsideDownGameTests::recastResetsToTheFullDuration);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SNEAK_CAST_ACTIVE_REMOVES =
            TEST_FUNCTIONS.register(
                    "sneak_cast_active_removes", () -> UpsideDownGameTests::sneakCastWhileActiveRemovesTheEffect);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SNEAK_CAST_INACTIVE_CASTS =
            TEST_FUNCTIONS.register(
                    "sneak_cast_inactive_casts", () -> UpsideDownGameTests::sneakCastWhileInactiveCasts);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CONFIG_IS_HONORED =
            TEST_FUNCTIONS.register("config_is_honored", () -> UpsideDownGameTests::configValuesAreHonored);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CAST_REFUSED_ON_COOLDOWN =
            TEST_FUNCTIONS.register("cast_refused_on_cooldown", () -> UpsideDownGameTests::castIsRefusedDuringCooldown);

    private SpellcraftGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> shared = event.registerEnvironment(Spellcraft.id("shared"));
        test(event, shared, EFFECT_IS_NEUTRAL_MARKER);
        test(event, shared, MILK_CLEARS_EFFECT);
        test(event, shared, CAST_GRANTS_FULL_DURATION);
        test(event, shared, RECAST_RESETS_DURATION);
        test(event, shared, SNEAK_CAST_ACTIVE_REMOVES);
        test(event, shared, SNEAK_CAST_INACTIVE_CASTS);
        // Batches run one after another, so a separate environment isolates the config mutation.
        Holder<TestEnvironmentDefinition<?>> configMutation =
                event.registerEnvironment(Spellcraft.id("config_mutation"));
        test(event, configMutation, CONFIG_IS_HONORED);
        Holder<TestEnvironmentDefinition<?>> cooldownMutation =
                event.registerEnvironment(Spellcraft.id("cooldown_mutation"));
        test(event, cooldownMutation, CAST_REFUSED_ON_COOLDOWN);
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
