package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.boundVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.followOnly;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.husk;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.parked;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.spell.rules.GuardArea;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

/**
 * Bound Vex GameTests (Evoking II Part 3 spec §2, §8): the post, the hover and the stay area. Every test flies a vex
 * or places hostiles several blocks out, so each registers through {@code FangGameTests.addPadded} (padding 13).
 * Shared constants and helpers live in {@link BoundVexTestSupport}.
 */
public final class BoundVexStayGameTests {
    /**
     * Where the tests hang a vex: four blocks above the structure's middle, over the barrier shell (which
     * surrounds the box and blocks the patrol goal's line of sight), so a hostile at the same height is in view.
     */
    private static final BlockPos POST_SPOT = FEET.above(4);

    private BoundVexStayGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(
                SHARED, "bound_vex_stay_returns_to_its_post", BoundVexStayGameTests::returnsToItsPostAndHovers);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_stay_attacks_a_hostile_inside_the_area",
                BoundVexStayGameTests::attacksAHostileInsideTheArea);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_stay_ignores_a_hostile_outside_the_area",
                BoundVexStayGameTests::ignoresAHostileOutsideTheArea);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_stay_area_is_measured_from_the_post",
                BoundVexStayGameTests::areaIsMeasuredFromThePostNotTheVex);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_stay_drops_a_target_that_leaves_the_area",
                BoundVexStayGameTests::dropsATargetThatLeavesTheArea);
        FangGameTests.addPadded(
                SHARED, "bound_vex_stay_off_drops_a_far_target", BoundVexStayGameTests::stayOffDropsAFarTarget);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_stay_old_save_gets_the_new_follow_range",
                BoundVexStayGameTests::oldSaveGetsTheNewFollowRange);
    }

    /** The vex's post point, or a failed test when it has no post. */
    private static Vec3 postOf(GameTestHelper helper, BoundVex vex) {
        BlockPos origin = vex.getBoundOrigin();
        if (origin == null) {
            throw helper.assertionException("a staying vex has a post");
        }
        return Vec3.atBottomCenterOf(origin);
    }

    /**
     * A husk with no AI and no gravity, placed {@code dx} blocks east and {@code dz} blocks south of {@code post} (world
     * axes) at the post's height. It is spawned inside the structure and then moved by absolute position: the test
     * structure may be rotated, and a rotated spawn position lands up to a block off the intended cell. No gravity
     * keeps it at the post's height in the padded box, which is air.
     */
    private static Husk huskBeside(GameTestHelper helper, Vec3 post, int dx, int dz) {
        Husk husk = husk(helper, FEET);
        husk.setNoGravity(true);
        husk.setPos(post.add(dx, 0.0, dz));
        return husk;
    }

    private static boolean targets(BoundVex vex, LivingEntity expected) {
        LivingEntity target = vex.getTarget();
        return target != null && target.getUUID().equals(expected.getUUID());
    }

    /**
     * When a staying Bound Vex is moved three blocks off its post, should fly back to within the post tolerance and
     * then hang there: over the next sixty ticks it never leaves the tolerance (no wander).
     *
     * <p>Method under test: {@code BoundVex.HoldPostGoal}, through the goal selector
     */
    static void returnsToItsPostAndHovers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, POST_SPOT, player);
        vex.setStaying(true);
        Vec3 post = postOf(helper, vex);
        vex.setPos(post.add(3.0, 0.0, 0.0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.position().distanceTo(post) <= BoundVex.POST_TOLERANCE,
                        "back at the post, got " + vex.position().distanceTo(post)))
                .thenExecuteFor(
                        60,
                        () -> helper.assertTrue(
                                vex.position().distanceTo(post) <= BoundVex.POST_TOLERANCE + 0.5,
                                "hovers at the post, got " + vex.position().distanceTo(post)))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex hangs at its post and a hostile stands six blocks east of the post at the post's height
     * (outside the following box, inside the stay area), should take it as its target.
     *
     * <p>Method under test: {@code NearestHostileGoal}, through {@link BoundVex#inGuardArea} and
     * {@link BoundVex#guardArea}
     */
    static void attacksAHostileInsideTheArea(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, POST_SPOT, player);
        vex.setStaying(true);
        Husk husk = huskBeside(helper, postOf(helper, vex), 6, 0);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "the husk is the target"))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex hangs at its post and a hostile stands eight blocks east of it, should leave it alone for
     * forty ticks: it is outside the 13-block stay area.
     *
     * <p>Method under test: {@code NearestHostileGoal}, through {@link BoundVex#inGuardArea}
     */
    static void ignoresAHostileOutsideTheArea(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, POST_SPOT, player);
        vex.setStaying(true);
        huskBeside(helper, postOf(helper, vex), 8, 0);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        40,
                        () -> helper.assertTrue(
                                vex.getTarget() == null, "no target outside the area, got " + vex.getTarget()))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex hangs five blocks west of its post, should measure the stay area from the post and not
     * from itself: a husk four blocks west of the vex (nine from the post) is outside, one six blocks east of the post
     * (eleven from the vex) is inside; with stay switched off the area is the following box around the vex, and the two
     * swap.
     *
     * <p>Method under test: {@link BoundVex#inGuardArea}
     */
    static void areaIsMeasuredFromThePostNotTheVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, POST_SPOT, player);
        vex.setStaying(true);
        Vec3 post = postOf(helper, vex);
        vex.setPos(post.add(-5.0, 0.0, 0.0));
        Husk westHusk = huskBeside(helper, post, -9, 0);
        Husk eastHusk = huskBeside(helper, post, 6, 0);

        // Act & Assert: staying
        helper.assertFalse(vex.inGuardArea(westHusk), "staying: nine blocks west of the post is outside");
        helper.assertTrue(vex.inGuardArea(eastHusk), "staying: six blocks east of the post is inside");

        // Act & Assert: following again, where it hangs
        vex.setStaying(false);
        helper.assertTrue(vex.inGuardArea(westHusk), "following: four blocks west of the vex is inside");
        helper.assertFalse(vex.inGuardArea(eastHusk), "following: eleven blocks east of the vex is outside");
        helper.succeed();
    }

    /**
     * When a staying Bound Vex has a hostile as its target and the hostile moves to nine blocks east of the post, should
     * drop it within thirty ticks: it left the stay area.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code ScanTargetGoal.canContinueToUse}
     */
    static void dropsATargetThatLeavesTheArea(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, POST_SPOT, player);
        vex.setStaying(true);
        Vec3 post = postOf(helper, vex);
        Husk husk = huskBeside(helper, post, 5, 0);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the husk is the target"))
                .thenExecute(() -> husk.setPos(post.add(9.0, 0.0, 0.0)))
                .thenWaitUntil(
                        () -> helper.assertTrue(vex.getTarget() == null, "the target outside the area is dropped"))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex hangs at the west edge of its area with a hostile as its target at the east edge (twelve
     * blocks away) and stay is switched off, should drop it within thirty ticks: beyond the following reach of 7.25.
     *
     * <p>Method under test: {@link BoundVex#keepsTarget}, through {@code ScanTargetGoal.canContinueToUse}
     */
    static void stayOffDropsAFarTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, POST_SPOT, player);
        vex.setStaying(true);
        Vec3 post = postOf(helper, vex);
        vex.setPos(post.add(-6.0, 0.0, 0.0));
        Husk husk = huskBeside(helper, post, 6, 0);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(targets(vex, husk), "setup: the far husk is the target"))
                .thenExecute(() -> vex.setStaying(false))
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() == null, "the far target is dropped"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex saved with the Part 2 follow range (the reach, 7.25) is loaded, should come back with the new
     * follow range: a staying vex's fights would otherwise be cut short at the old bound.
     *
     * <p>Method under test: {@code BoundVex.readAdditionalSaveData} (protected), through
     * {@link GameTestSupport#reloaded}
     */
    static void oldSaveGetsTheNewFollowRange(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, POST_SPOT, player);
        var range = vex.getAttribute(Attributes.FOLLOW_RANGE);
        if (range == null) {
            throw helper.assertionException("a Bound Vex has a follow range");
        }
        range.setBaseValue(GuardArea.REACH);

        // Act
        if (!(GameTestSupport.reloaded(helper.getLevel(), vex) instanceof BoundVex copy)) {
            throw helper.assertionException("the reloaded copy is not a Bound Vex");
        }

        // Assert
        helper.assertValueEqual(
                copy.getAttributeBaseValue(Attributes.FOLLOW_RANGE), GuardArea.FOLLOW_RANGE, "the new follow range");
        helper.succeed();
    }
}
