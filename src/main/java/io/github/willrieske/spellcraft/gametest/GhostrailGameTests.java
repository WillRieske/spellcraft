package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.hostileAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.EvokingAttachments;
import io.github.willrieske.spellcraft.evoking.Ghostrail;
import io.github.willrieske.spellcraft.infinity.AbilitySync;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** GameTests of Ghostrail's damage rule, trail, ending and mob carriers (Parts 6-7 spec §7.3, §10). */
@SuppressWarnings("deprecation") // The ending tests read Abilities#mayfly; see AbilitySync#sync for why.
public final class GhostrailGameTests {
    /** How far from the player the big stone cubes reach sideways and down (the push-out search radius). */
    private static final int REACH = 8;

    /** How far up the big stone cubes reach: the search radius plus the box's second block. */
    private static final int REACH_UP = REACH + 1;

    /** How far the start-spot test lifts the player, past the search radius so the start spot stays free. */
    private static final int LIFT = 10;

    private GhostrailGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(SHARED, "ghostrail_damage_is_cancelled", GhostrailGameTests::damageIsCancelled);
        FangGameTests.addPadded(SHARED, "ghostrail_kill_still_lands", GhostrailGameTests::killStillLands);
        FangGameTests.addPadded(SHARED, "ghostrail_void_damage_still_lands", GhostrailGameTests::voidDamageStillLands);
        FangGameTests.addPadded(SHARED, "ghostrail_trail_particles_are_sent", GhostrailGameTests::trailIsSent);
        FangGameTests.addPadded(
                SHARED, "ghostrail_ending_inside_stone_moves_to_a_free_spot", GhostrailGameTests::endingInStone);
        FangGameTests.addPadded(SHARED, "ghostrail_ending_in_the_open_stays_put", GhostrailGameTests::endingInTheOpen);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_ending_deep_in_stone_goes_to_the_start_spot",
                GhostrailGameTests::endingGoesToTheStartSpot);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_ending_deep_in_stone_with_no_start_spot_stays",
                GhostrailGameTests::endingStaysWithNoStartSpot);
        FangGameTests.addPadded(
                SHARED, "ghostrail_mob_carrier_gets_no_physics_and_no_gravity", GhostrailGameTests::mobGetsNoPhysics);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_mob_carrier_gets_its_gravity_back_at_the_end",
                GhostrailGameTests::mobGetsGravityBack);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_mob_carrier_keeps_an_original_no_gravity",
                GhostrailGameTests::mobKeepsOriginalNoGravity);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_mob_carrier_recast_before_the_end_keeps_the_original_gravity",
                GhostrailGameTests::recastKeepsTheOriginal);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_dead_carrier_is_not_pushed_out_of_the_stone",
                GhostrailGameTests::deadCarrierIsNotPushed);
        FangGameTests.addPadded(SHARED, "ghostrail_vex_carrier_is_left_alone", GhostrailGameTests::vexIsLeftAlone);
        SpellcraftGameTests.add(
                SHARED, "ghostrail_infinite_duration_is_vanillas", GhostrailGameTests::infiniteIsVanillas);
        FangGameTests.addPadded(
                SHARED,
                "ghostrail_recast_in_stone_keeps_the_earlier_start_spot",
                GhostrailGameTests::recastInStoneKeepsTheStart);
        FangGameTests.addPadded(SHARED, "ghostrail_mob_mark_is_saved", GhostrailGameTests::mobMarkIsSaved);
    }

    /** Fills the box between two relative corners with stone; puts air back when the test ends. */
    private static void stoneBox(GameTestHelper helper, BlockPos from, BlockPos to) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> filled = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(from), helper.absolutePos(to))) {
            BlockPos fixed = pos.immutable();
            level.setBlock(fixed, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            filled.add(fixed);
        }
        GameTestSupport.whenDone(
                helper,
                () -> filled.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
    }

    /** Fills stone {@link #REACH} blocks around {@code center} sideways and down, and {@link #REACH_UP} up. */
    private static void stoneAround(GameTestHelper helper, BlockPos center) {
        stoneBox(helper, center.offset(-REACH, -REACH, -REACH), center.offset(REACH, REACH_UP, REACH));
    }

    /** A husk with Ghostrail for a hostile mob's time, standing at the player's standing spot. */
    private static Husk ghostHusk(GameTestHelper helper) {
        Husk husk = hostileAt(helper, ScrollTestSupport.at(helper, 0, 0, 0));
        Spells.cast(husk, Spells.GHOSTRAIL, GhostrailDuration.HOSTILE_TICKS);
        return husk;
    }

    /**
     * When a player takes 5 damage from a generic source, should their health drop without Ghostrail (the control)
     * and not change with it.
     *
     * <p>Method under test: {@code Ghostrail#onIncomingDamage}.
     */
    static void damageIsCancelled(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        ServerLevel level = helper.getLevel();
        float full = player.getHealth();
        player.hurtServer(level, level.damageSources().generic(), 5.0F);
        helper.assertValueEqual(player.getHealth(), full - 5.0F, "control: without Ghostrail the hit lands");
        player.setHealth(full);
        player.setInvulnerableTime(0); // the hit above started the invulnerability frames
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        // Act
        player.hurtServer(level, level.damageSources().generic(), 5.0F);
        // Assert
        helper.assertValueEqual(player.getHealth(), full, "no damage");
        helper.succeed();
    }

    /**
     * When a Ghostrail player is hit by /kill's damage, should they die.
     *
     * <p>Method under test: {@code Ghostrail#onIncomingDamage}.
     */
    static void killStillLands(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        ServerLevel level = helper.getLevel();
        // Act
        player.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
        // Assert
        helper.assertFalse(player.isAlive(), "/kill still kills");
        helper.succeed();
    }

    /**
     * When a Ghostrail player is hit by the void's damage, should they die.
     *
     * <p>Method under test: {@code Ghostrail#onIncomingDamage}.
     */
    static void voidDamageStillLands(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        ServerLevel level = helper.getLevel();
        // Act
        player.hurtServer(level, level.damageSources().fellOutOfWorld(), 1000.0F);
        // Assert
        helper.assertFalse(player.isAlive(), "the void still kills");
        helper.succeed();
    }

    /**
     * When a Ghostrail player is stepped once, should white smoke be sent.
     *
     * <p>Method under test: {@code Ghostrail#tick}: the trail.
     */
    static void trailIsSent(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        GameTestSupport.drainShown(player);
        // Act
        Ghostrail.tick(player);
        // Assert
        helper.assertTrue(
                GameTestSupport.drainShown(player).particles().contains(ParticleTypes.WHITE_SMOKE),
                "the trail is white smoke");
        helper.succeed();
    }

    /**
     * When Ghostrail ends with the player's box inside stone, should they move to a free spot, fall distance 0, no
     * mayfly.
     *
     * <p>Method under test: {@code Ghostrail#tick}: the push-out.
     */
    static void endingInStone(GameTestHelper helper) {
        // Arrange: a 7 x 7 x 7 block of stone with the player's feet in it, over a floor
        ServerPlayer player = plainPlayer(helper);
        ServerLevel level = helper.getLevel();
        stoneBox(helper, FangGameTests.FEET.offset(-3, -1, -3), FangGameTests.FEET.offset(3, 5, 3));
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        AbilitySync.sync(player);
        Ghostrail.tick(player);
        player.fallDistance = 12.0;
        // Act
        player.removeEffect(ModEffects.GHOSTRAIL);
        AbilitySync.sync(player);
        Ghostrail.tick(player);
        // Assert
        helper.assertTrue(level.noCollision(player, player.getBoundingBox()), "the player's box is free");
        helper.assertTrue(
                player.blockPosition().distManhattan(helper.absolutePos(FangGameTests.FEET)) <= 24,
                "within the search");
        helper.assertValueEqual(player.fallDistance, 0.0, "no fall damage to come");
        helper.assertFalse(player.getAbilities().mayfly, "no flight left");
        helper.succeed();
    }

    /**
     * When Ghostrail ends with the player's box in the open, should they stay where they are, fall distance 0.
     *
     * <p>Method under test: {@code Ghostrail#tick}: the end.
     */
    static void endingInTheOpen(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        Ghostrail.tick(player);
        player.fallDistance = 5.0;
        Vec3 before = player.position();
        // Act
        player.removeEffect(ModEffects.GHOSTRAIL);
        Ghostrail.tick(player);
        // Assert
        helper.assertValueEqual(player.position(), before, "the player stays put");
        helper.assertValueEqual(player.fallDistance, 0.0, "no fall damage to come");
        helper.succeed();
    }

    /**
     * When Ghostrail ends deep in stone (nothing free within the search) and began at a standable spot, should the
     * player go to that start spot.
     *
     * <p>Method under test: {@code Ghostrail#begin} and the push-out's last resort.
     */
    static void endingGoesToTheStartSpot(GameTestHelper helper) {
        // Arrange: a floor, the cast at the standing spot, then a lift past the search radius into solid stone
        ServerPlayer player = plainPlayer(helper);
        ScrollTestSupport.floor(helper, 3);
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        BlockPos start = player.blockPosition();
        helper.assertValueEqual(
                player.getExistingData(EvokingAttachments.GHOSTRAIL_START).orElse(null), start, "the start is kept");
        MockPlayers.moveTo(helper, player, FangGameTests.STAND.add(0, LIFT, 0), 0.0F);
        stoneAround(helper, FangGameTests.FEET.offset(0, LIFT, 0));
        player.removeEffect(ModEffects.GHOSTRAIL);
        // Act
        Ghostrail.tick(player);
        // Assert
        helper.assertValueEqual(player.blockPosition(), start, "back at the start spot");
        helper.succeed();
    }

    /**
     * When Ghostrail began inside stone (no start spot) and ends deep in stone, should the player stay where they are.
     *
     * <p>Method under test: {@code Ghostrail#begin} and the push-out's last resort.
     */
    static void endingStaysWithNoStartSpot(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        stoneAround(helper, FangGameTests.FEET);
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        helper.assertTrue(
                player.getExistingData(EvokingAttachments.GHOSTRAIL_START).isEmpty(), "no start spot was recorded");
        Vec3 before = player.position();
        player.removeEffect(ModEffects.GHOSTRAIL);
        // Act
        Ghostrail.tick(player);
        // Assert
        helper.assertValueEqual(player.position(), before, "the player stays");
        helper.succeed();
    }

    /**
     * When a husk has Ghostrail and is stepped, should it have noPhysics and no gravity.
     *
     * <p>Method under test: {@code Ghostrail#tick}: the mob hold.
     */
    static void mobGetsNoPhysics(GameTestHelper helper) {
        // Arrange
        Husk husk = ghostHusk(helper);
        // Act
        Ghostrail.tick(husk);
        // Assert
        helper.assertTrue(husk.noPhysics && husk.isNoGravity(), "no physics and no gravity");
        helper.succeed();
    }

    /**
     * When a husk with gravity has Ghostrail removed by /effect clear (the mark remains), should the next step give it
     * physics and gravity back and clear the mark.
     *
     * <p>Method under test: {@code Ghostrail#tick}: the end of a mob carrier.
     */
    static void mobGetsGravityBack(GameTestHelper helper) {
        // Arrange
        Husk husk = hostileAt(helper, ScrollTestSupport.at(helper, 0, 0, 0));
        husk.setNoGravity(false);
        Spells.cast(husk, Spells.GHOSTRAIL, GhostrailDuration.HOSTILE_TICKS);
        Ghostrail.tick(husk);
        // Act
        husk.removeAllEffects();
        Ghostrail.tick(husk);
        // Assert
        helper.assertTrue(!husk.noPhysics && !husk.isNoGravity(), "physics and gravity are back");
        helper.assertFalse(husk.hasData(EvokingAttachments.GHOSTRAIL_MARK), "the mark is gone");
        helper.succeed();
    }

    /**
     * When a husk that already had no gravity ends Ghostrail, should it still have no gravity and no noPhysics.
     *
     * <p>Method under test: {@code Ghostrail#tick}: the original NoGravity.
     */
    static void mobKeepsOriginalNoGravity(GameTestHelper helper) {
        // Arrange
        Husk husk = ghostHusk(helper);
        Ghostrail.tick(husk);
        // Act
        husk.removeAllEffects();
        Ghostrail.tick(husk);
        // Assert
        helper.assertTrue(husk.isNoGravity(), "its own no-gravity stays");
        helper.assertFalse(husk.noPhysics, "physics are back");
        helper.succeed();
    }

    /**
     * When Ghostrail is removed and cast again on a husk before the end ran, should the original gravity still be
     * restored at the real end.
     *
     * <p>Method under test: {@code Ghostrail#begin}: the mark keeps the first original.
     */
    static void recastKeepsTheOriginal(GameTestHelper helper) {
        // Arrange
        Husk husk = hostileAt(helper, ScrollTestSupport.at(helper, 0, 0, 0));
        husk.setNoGravity(false);
        Spells.cast(husk, Spells.GHOSTRAIL, GhostrailDuration.HOSTILE_TICKS);
        Ghostrail.tick(husk);
        husk.removeEffect(ModEffects.GHOSTRAIL);
        Spells.cast(husk, Spells.GHOSTRAIL, GhostrailDuration.HOSTILE_TICKS);
        Ghostrail.tick(husk);
        // Act
        husk.removeEffect(ModEffects.GHOSTRAIL);
        Ghostrail.tick(husk);
        // Assert
        helper.assertFalse(husk.isNoGravity(), "gravity is restored");
        helper.succeed();
    }

    /**
     * When a husk with Ghostrail inside stone is killed, should the next step not move it, but clear its mark and
     * physics flag.
     *
     * <p>Method under test: {@code Ghostrail#tick}: a dead carrier.
     */
    static void deadCarrierIsNotPushed(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        stoneBox(helper, FangGameTests.FEET.offset(-2, -2, -2), FangGameTests.FEET.offset(2, 3, 2));
        Husk husk = ghostHusk(helper);
        Ghostrail.tick(husk);
        husk.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
        husk.removeAllEffects(); // the effect runs out during the death animation: the end must not move a corpse
        Vec3 before = husk.position();
        // Act
        Ghostrail.tick(husk);
        // Assert
        helper.assertValueEqual(husk.position(), before, "a corpse is not teleported");
        helper.assertFalse(husk.hasData(EvokingAttachments.GHOSTRAIL_MARK), "the mark is gone");
        helper.assertFalse(husk.noPhysics, "physics are back");
        helper.succeed();
    }

    /**
     * When a Bound Vex has Ghostrail, should a step leave its noPhysics alone and its no-gravity as it was, also after
     * the end.
     *
     * <p>Method under test: {@code Ghostrail#flies}.
     */
    static void vexIsLeftAlone(GameTestHelper helper) {
        // Arrange
        BoundVex vex = BoundVexTestSupport.unmarkedVex(helper, FangGameTests.FEET);
        boolean gravity = vex.isNoGravity();
        Spells.cast(vex, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        // Act
        Ghostrail.tick(vex);
        // Assert
        helper.assertFalse(vex.noPhysics, "the vex's own flag is untouched between its ticks");
        // Act
        vex.removeEffect(ModEffects.GHOSTRAIL);
        Ghostrail.tick(vex);
        // Assert
        helper.assertValueEqual(vex.isNoGravity(), gravity, "its gravity is as before");
        helper.succeed();
    }

    /**
     * When the infinite sentinel is compared with vanilla's, should they be equal.
     *
     * <p>Method under test: {@code GhostrailDuration#INFINITE}.
     */
    static void infiniteIsVanillas(GameTestHelper helper) {
        helper.assertValueEqual(GhostrailDuration.INFINITE, MobEffectInstance.INFINITE_DURATION, "the sentinel");
        helper.succeed();
    }

    /**
     * When Ghostrail is cast again on a player whose block is now stone, should the earlier start spot be kept.
     *
     * <p>Method under test: {@code Ghostrail#begin}: a start spot is only replaced by another standable one.
     */
    static void recastInStoneKeepsTheStart(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        ScrollTestSupport.floor(helper, 3);
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        BlockPos start = player.blockPosition();
        stoneBox(helper, FangGameTests.FEET, FangGameTests.FEET.above());
        player.removeEffect(ModEffects.GHOSTRAIL);
        // Act
        Spells.cast(player, Spells.GHOSTRAIL, GhostrailDuration.PLAYER_TICKS);
        // Assert
        helper.assertValueEqual(
                player.getExistingData(EvokingAttachments.GHOSTRAIL_START).orElse(null), start, "the start is kept");
        helper.succeed();
    }

    /**
     * When a husk with Ghostrail is saved and reloaded, should the copy keep the mark.
     *
     * <p>Method under test: {@code EvokingAttachments#GHOSTRAIL_MARK} (saved).
     */
    static void mobMarkIsSaved(GameTestHelper helper) {
        // Arrange
        Husk husk = ghostHusk(helper);
        // Act
        Husk loaded = (Husk) GameTestSupport.reloaded(helper.getLevel(), husk);
        // Assert
        helper.assertTrue(loaded.hasData(EvokingAttachments.GHOSTRAIL_MARK), "the mark survives a save and load");
        helper.succeed();
    }
}
