package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.infinity.AbilitySync;
import io.github.willrieske.spellcraft.infinity.InfinityAttachments;
import io.github.willrieske.spellcraft.spell.Spell;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** GameTests of a Ghostrail player's physics and flight (Parts 6-7 spec §7.3). */
@SuppressWarnings("deprecation") // Every test reads Abilities#mayfly; see AbilitySync#sync for why.
public final class GhostrailFlightGameTests {
    /** The most a flying mock player may sink over 20 ticks, in blocks (damped gravity; a free fall is ~3 blocks). */
    private static final double DRIFT = 0.5;

    /** The rounding allowed when comparing a height after a move. */
    private static final double EPSILON = 1.0E-6;

    private GhostrailFlightGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_flight_player_gets_no_physics_through_the_mixin",
                GhostrailFlightGameTests::noPhysicsThroughTheMixin);
        FangGameTests.addPadded(
                SHARED, "ghostrail_flight_no_effect_means_physics", GhostrailFlightGameTests::noEffectMeansPhysics);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_flight_spectator_still_has_no_physics",
                GhostrailFlightGameTests::spectatorStillHasNoPhysics);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_flight_player_on_stone_stays_aloft_and_passes_through_it",
                GhostrailFlightGameTests::staysAloftOverStone);
        SpellcraftGameTests.add(
                SHARED, "ghostrail_flight_grants_mayfly_and_flying", GhostrailFlightGameTests::grantsFlight);
        SpellcraftGameTests.add(
                SHARED, "ghostrail_flight_flying_is_reasserted_each_sync", GhostrailFlightGameTests::reassertsFlying);
        SpellcraftGameTests.add(
                SHARED,
                "ghostrail_flight_creative_player_keeps_physics",
                GhostrailFlightGameTests::creativeKeepsPhysics);
        SpellcraftGameTests.add(
                SHARED, "ghostrail_flight_ending_revokes_the_flight", GhostrailFlightGameTests::endingRevokes);
        SpellcraftGameTests.add(
                SHARED,
                "ghostrail_flight_ending_with_infinity_keeps_infinitys_flight",
                GhostrailFlightGameTests::endingKeepsInfinity);
        SpellcraftGameTests.add(
                SHARED,
                "ghostrail_flight_infinity_ending_with_ghostrail_keeps_flight",
                GhostrailFlightGameTests::infinityEndingKeepsGhostrail);
        SpellcraftGameTests.add(SHARED, "ghostrail_flight_mark_is_saved", GhostrailFlightGameTests::markIsSaved);
        SpellcraftGameTests.add(
                SHARED,
                "ghostrail_flight_creative_player_keeps_creative_flight",
                GhostrailFlightGameTests::creativeKeepsFlight);
    }

    /** A player of {@code mode} inside this test's area. */
    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        MockPlayers.moveIntoTest(helper, player);
        return player;
    }

    /** Gives {@code player} Infinity for good, as the scroll does. */
    private static void castInfinity(ServerPlayer player) {
        Spells.cast(player, Spells.INFINITY, Spell.INFINITE.getAsInt());
    }

    /** Gives {@code player} Ghostrail for a player's full time. */
    private static void castGhostrail(ServerPlayer player) {
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
    }

    /**
     * When a survival player with Ghostrail runs a full player tick, should have noPhysics true afterwards (the
     * Mixin).
     *
     * <p>Method under test: {@code PlayerGhostrailMixin#spellcraft$ghostrailNoPhysics}.
     */
    static void noPhysicsThroughTheMixin(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        castGhostrail(player);
        // Act
        player.doTick();
        // Assert
        helper.assertTrue(player.noPhysics, "noPhysics follows the effect");
        helper.succeed();
    }

    /**
     * When a creative player with Ghostrail runs a full player tick, should keep noPhysics false (they get no forced
     * flying, so passing through blocks would sink them through the floor).
     *
     * <p>Method under test: {@code PlayerGhostrailMixin#spellcraft$ghostrailNoPhysics}.
     */
    static void creativeKeepsPhysics(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.CREATIVE);
        castGhostrail(player);
        // Act
        player.doTick();
        // Assert
        helper.assertFalse(player.noPhysics, "a creative player keeps physics");
        helper.succeed();
    }

    /**
     * When a survival player without Ghostrail runs a full player tick, should keep noPhysics false (the control).
     *
     * <p>Method under test: {@code PlayerGhostrailMixin#spellcraft$ghostrailNoPhysics}.
     */
    static void noEffectMeansPhysics(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        // Act
        player.doTick();
        // Assert
        helper.assertFalse(player.noPhysics, "physics as usual");
        helper.succeed();
    }

    /**
     * When a spectator runs a full player tick, should still have noPhysics true (the original answer is kept).
     *
     * <p>Method under test: {@code PlayerGhostrailMixin#spellcraft$ghostrailNoPhysics}.
     */
    static void spectatorStillHasNoPhysics(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SPECTATOR);
        // Act
        player.doTick();
        // Assert
        helper.assertTrue(player.noPhysics, "a spectator passes through blocks");
        helper.succeed();
    }

    /**
     * When a survival player standing on stone gets Ghostrail and is ticked 20 times, should only drift
     * down into the stone while flying with noPhysics, and then move through the stone floor.
     *
     * <p>Method under test: {@code PlayerGhostrailMixin#spellcraft$ghostrailNoPhysics} together with
     * {@code AbilitySync#onPlayerTick}.
     */
    static void staysAloftOverStone(GameTestHelper helper) {
        // Arrange
        ScrollTestSupport.floor(helper, 6);
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        double y = player.getY();
        castGhostrail(player);
        // Act
        player.doTick();
        // Assert
        helper.assertTrue(player.noPhysics, "the Mixin sets noPhysics at the top of the tick");
        helper.assertTrue(player.getAbilities().flying, "the sync sets flying in the Post event of the same tick");
        helper.startSequence()
                .thenExecuteFor(19, player::doTick)
                .thenExecute(() -> {
                    // A mock player's tick runs aiStep, so flying's damped gravity still pulls it down a little (the
                    // first tick, before the sync sets flying, pulls a full tick of gravity); a player stopped by the
                    // stone would stay at y, and one pushed out of it would rise.
                    helper.assertTrue(player.getY() < y, "the player sinks into the stone, not held up by it");
                    helper.assertTrue(player.getY() > y - DRIFT, "and only drifts: it is not falling freely");
                    helper.assertTrue(player.getAbilities().flying, "still flying");
                    helper.assertTrue(player.getAbilities().mayfly, "still may fly");
                    helper.assertTrue(player.noPhysics, "still no physics");
                    double before = player.getY();
                    player.move(MoverType.SELF, new Vec3(0.0, -1.5, 0.0));
                    helper.assertTrue(
                            Math.abs(player.getY() - (before - 1.5)) < EPSILON,
                            "the move passes through the stone floor");
                })
                .thenSucceed();
    }

    /**
     * When a survival player gets Ghostrail and is synced, should have mayfly and flying and no instabuild.
     *
     * <p>Method under test: {@code AbilitySync#sync}.
     */
    static void grantsFlight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        castGhostrail(player);
        // Act
        AbilitySync.sync(player);
        // Assert
        Abilities abilities = player.getAbilities();
        helper.assertTrue(abilities.mayfly && abilities.flying && !abilities.instabuild, "flight, no creative build");
        helper.succeed();
    }

    /**
     * When a Ghostrail player's flying flag is cleared, should have it set again by the next sync.
     *
     * <p>Method under test: {@code AbilitySync#sync}.
     */
    static void reassertsFlying(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        castGhostrail(player);
        AbilitySync.sync(player);
        player.getAbilities().flying = false;
        // Act
        AbilitySync.sync(player);
        // Assert
        helper.assertTrue(player.getAbilities().flying, "flying is back");
        helper.succeed();
    }

    /**
     * When Ghostrail ends on a survival player, should take both mayfly and flying away at the next sync.
     *
     * <p>Method under test: {@code AbilitySync#sync}.
     */
    static void endingRevokes(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        castGhostrail(player);
        AbilitySync.sync(player);
        // Act
        player.removeEffect(ModEffects.GHOSTRAIL);
        AbilitySync.sync(player);
        // Assert
        Abilities abilities = player.getAbilities();
        helper.assertFalse(abilities.mayfly, "no mayfly left");
        helper.assertFalse(abilities.flying, "no flying left");
        helper.succeed();
    }

    /**
     * When Ghostrail ends on a player who still has Infinity, should keep Infinity's mayfly and instabuild and stop
     * flying.
     *
     * <p>Method under test: {@code AbilitySync#sync}.
     */
    static void endingKeepsInfinity(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        castInfinity(player);
        castGhostrail(player);
        AbilitySync.sync(player);
        // Act
        player.removeEffect(ModEffects.GHOSTRAIL);
        AbilitySync.sync(player);
        // Assert
        Abilities abilities = player.getAbilities();
        helper.assertTrue(abilities.mayfly && abilities.instabuild && !abilities.flying, "Infinity's flight stays");
        helper.succeed();
    }

    /**
     * When Infinity ends on a player who still has Ghostrail, should keep mayfly and flying and lose instabuild.
     *
     * <p>Method under test: {@code AbilitySync#sync}.
     */
    static void infinityEndingKeepsGhostrail(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        castInfinity(player);
        castGhostrail(player);
        AbilitySync.sync(player);
        // Act
        player.removeEffect(ModEffects.INFINITY);
        AbilitySync.sync(player);
        // Assert
        Abilities abilities = player.getAbilities();
        helper.assertTrue(abilities.mayfly && abilities.flying && !abilities.instabuild, "Ghostrail's flight stays");
        helper.succeed();
    }

    /**
     * When a synced Ghostrail player is saved and loaded into a fresh player, should keep the granted mark.
     *
     * <p>Method under test: {@code AbilitySync#sync} (the mark) and {@code InfinityAttachments#GHOSTRAIL_GRANTED}.
     */
    static void markIsSaved(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        castGhostrail(player);
        AbilitySync.sync(player);
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        player.saveWithoutId(output);
        ServerPlayer loaded = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        helper.assertFalse(
                loaded.getData(InfinityAttachments.GHOSTRAIL_GRANTED), "precondition: a fresh player is unmarked");
        // Act
        loaded.load(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        // Assert
        helper.assertTrue(player.getData(InfinityAttachments.GHOSTRAIL_GRANTED), "the player is marked");
        helper.assertTrue(loaded.getData(InfinityAttachments.GHOSTRAIL_GRANTED), "the mark survives a save and load");
        helper.succeed();
    }

    /**
     * When a creative player with Ghostrail and a cleared flying flag is synced, should keep creative's flags (no
     * forced flying), and still may fly after the effect ends.
     *
     * <p>Method under test: {@code AbilitySync#sync}.
     */
    static void creativeKeepsFlight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = player(helper, GameType.CREATIVE);
        castGhostrail(player);
        Abilities abilities = player.getAbilities();
        abilities.flying = false;
        // Act
        AbilitySync.sync(player);
        // Assert
        helper.assertTrue(abilities.mayfly && abilities.instabuild, "creative flags are untouched");
        helper.assertFalse(abilities.flying, "creative owns its flying flag");
        // Act
        player.removeEffect(ModEffects.GHOSTRAIL);
        AbilitySync.sync(player);
        // Assert
        helper.assertTrue(abilities.mayfly, "creative still may fly");
        helper.succeed();
    }
}
