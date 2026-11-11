package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.flyingVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.foe;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.hangPoint;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** GameTests of the Evader team move (Parts 6-7 spec section 4.3). */
final class BoundVexTeamMoveEvaderGameTests {
    private BoundVexTeamMoveEvaderGameTests() {}

    /** Registers the tests. */
    static void registerAll() {
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_orbits_at_two_blocks",
                200,
                BoundVexTeamMoveEvaderGameTests::orbitsAtTwoBlocks);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_shoves_a_hostile_within_three",
                200,
                BoundVexTeamMoveEvaderGameTests::shovesAHostileWithinThree);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_shoves_a_hostile_once_per_twenty_ticks",
                200,
                BoundVexTeamMoveEvaderGameTests::shovesOncePerTwentyTicks);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_reverses_a_skeletons_arrow",
                200,
                BoundVexTeamMoveEvaderGameTests::reversesASkeletonsArrow);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_leaves_the_summoners_arrow_alone",
                200,
                BoundVexTeamMoveEvaderGameTests::leavesTheSummonersArrowAlone);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_leaves_a_ghast_fireball_alone",
                200,
                BoundVexTeamMoveEvaderGameTests::leavesAGhastFireballAlone);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_does_not_reverse_twice",
                200,
                BoundVexTeamMoveEvaderGameTests::doesNotReverseTwice);
        FangGameTests.addPadded(
                SHARED, "team_move_evader_leaves_a_pig_alone", 200, BoundVexTeamMoveEvaderGameTests::leavesAPigAlone);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_follows_a_moving_ward",
                200,
                BoundVexTeamMoveEvaderGameTests::followsAMovingWard);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_reverses_a_fast_arrow",
                200,
                BoundVexTeamMoveEvaderGameTests::reversesAFastArrow);
        FangGameTests.addPadded(
                SHARED,
                "team_move_evader_keeps_up_with_a_steadily_moving_ward",
                200,
                BoundVexTeamMoveEvaderGameTests::keepsUpWithASteadilyMovingWard);
    }

    /** The parts of a running Evader scene: the summoner, the three flying members and the ward. */
    private record Scene(ServerPlayer player, List<BoundVex> vexes, LivingEntity ward) {}

    /** An Evader warding the hurt summoner with three flying vexes, stepped every tick. */
    private static Scene guardingTheSummoner(GameTestHelper helper) {
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        player.setHealth(8.0F);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hangPoint(helper));
        helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, player), "setup: the Evader starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        return new Scene(player, vexes, player);
    }

    /** The point an Evader member circles around: the ward's middle. */
    private static Vec3 center(LivingEntity ward) {
        return ward.position().add(0.0, ward.getBbHeight() / 2.0, 0.0);
    }

    /** An arrow of {@code owner} two blocks east of the ward, flying straight at it, added to the level. */
    private static Arrow arrowAt(GameTestHelper helper, LivingEntity owner, LivingEntity ward) {
        Arrow arrow = new Arrow(helper.getLevel(), owner, new ItemStack(Items.ARROW), null);
        arrow.setNoGravity(true);
        arrow.setPos(center(ward).add(2.0, 0.0, 0.0));
        arrow.setDeltaMovement(-0.5, 0.0, 0.0);
        helper.getLevel().addFreshEntity(arrow);
        GameTestSupport.whenDone(helper, arrow::discard);
        return arrow;
    }

    /** Whether every two of the vexes are at least two blocks apart. */
    private static boolean spread(List<BoundVex> vexes) {
        for (int i = 0; i < vexes.size(); i++) {
            for (int j = i + 1; j < vexes.size(); j++) {
                if (vexes.get(i).distanceTo(vexes.get(j)) < 2.0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A point 2.5 blocks east of the summoner and 3 up: over the box's barrier shell (a foe in the shell takes wall
     * damage), still within 3 blocks of the summoner's box.
     */
    private static Vec3 overTheShell(ServerPlayer player) {
        return player.position().add(2.5, 3.0, 0.0);
    }

    /**
     * When three vexes guard the summoner, should each settle about two blocks from the summoner's middle and the three
     * be spread round the circle (at least two blocks apart).
     *
     * <p>Method under test: {@code EvaderMove.tick}: the orbit
     */
    static void orbitsAtTwoBlocks(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);

        // Act & Assert (the members start in a row, so wait until they have spread round the circle too)
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            scene.vexes().stream()
                                    .allMatch(vex ->
                                            Math.abs(vex.position().distanceTo(center(scene.ward())) - 2.0) <= 0.7),
                            "every member is about two blocks out");
                    helper.assertTrue(spread(scene.vexes()), "and the members are spread");
                })
                .thenExecuteFor(20, () -> {
                    helper.assertTrue(
                            scene.vexes().stream()
                                    .allMatch(vex ->
                                            Math.abs(vex.position().distanceTo(center(scene.ward())) - 2.0) <= 0.7),
                            "every member stays about two blocks out");
                    helper.assertTrue(spread(scene.vexes()), "and the members stay spread");
                })
                .thenSucceed();
    }

    /**
     * When a husk stands 2.5 blocks from the summoner, should it be shoved away within a few ticks, without damage. The
     * husk has no AI, so the shove shows in its velocity: a no-AI mob's velocity does not decay
     * ({@code LivingEntity.aiStep} scales it only when {@code !canSimulateMovement()}).
     *
     * <p>Method under test: {@code EvaderMove.guard}: the shove
     */
    static void shovesAHostileWithinThree(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);
        Husk husk = foe(helper, EntityTypes.HUSK, overTheShell(scene.player()));

        // Act & Assert (knockback(2.0, ...) sets x to 0 / 2 + 2.0; 2.0 * 0.98^5 = 1.8 is above 1.0 either way)
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(
                            husk.getDeltaMovement().x > 1.0,
                            "the husk was shoved away from the summoner, velocity " + husk.getDeltaMovement());
                    helper.assertValueEqual(husk.getHealth(), husk.getMaxHealth(), "and not hurt");
                })
                .thenSucceed();
    }

    /**
     * When a husk is held within three blocks of the summoner, should it be shoved once in the next 19 ticks and again
     * shortly after 20 (the shield-block sound counts the shoves).
     *
     * <p>Method under test: {@code EvaderMove.guard}: once per hostile per 20 ticks
     */
    static void shovesOncePerTwentyTicks(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);
        Husk husk = foe(helper, EntityTypes.HUSK, overTheShell(scene.player()));
        BoundVexTestSupport.holdAt(helper, husk, husk.position());

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(
                        19,
                        () -> helper.assertValueEqual(
                                GameTestSupport.drainSounds(scene.player()).stream()
                                        .filter(sound -> sound.equals(SoundEvents.SHIELD_BLOCK.value()))
                                        .count(),
                                1L,
                                "one shove in the first 19 ticks"))
                .thenExecuteAfter(
                        5,
                        () -> helper.assertValueEqual(
                                GameTestSupport.drainSounds(scene.player()).stream()
                                        .filter(sound -> sound.equals(SoundEvents.SHIELD_BLOCK.value()))
                                        .count(),
                                1L,
                                "and the next one after twenty"))
                .thenSucceed();
    }

    /**
     * When a skeleton's arrow flies two blocks from the summoner straight at them, should a member reverse it and own
     * it.
     *
     * <p>Method under test: {@code EvaderMove.guard}: the deflection
     */
    static void reversesASkeletonsArrow(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);
        Mob skeleton =
                foe(helper, EntityTypes.SKELETON, scene.player().position().add(9.0, 0.0, 0.0));
        Arrow arrow = arrowAt(helper, skeleton, scene.ward());

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        helper.assertTrue(arrow.getOwner() instanceof BoundVex, "a vex owns the arrow now");
        helper.assertTrue(arrow.getDeltaMovement().x > 0.0, "and it flies away from the summoner");
        helper.succeed();
    }

    /**
     * When the summoner's own arrow flies at a hurt villager the Evader guards, should no member touch it, while a
     * skeleton's arrow in the same tick is reversed (the control: the move is running and guarding).
     *
     * <p>Method under test: {@code EvaderMove.guard}: only projectiles from outside the summoner's side
     */
    static void leavesTheSummonersArrowAlone(GameTestHelper helper) {
        // Arrange (the villager stands in plain padded air, not in the barrier-shell layers below the hang point)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Villager villager = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(0.0, 0.0, 2.0));
        villager.setHealth(villager.getMaxHealth() * 0.4F);
        flyingVexes(helper, player, 2, hangPoint(helper));
        helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, villager), "setup: the Evader starts");
        Mob skeleton = foe(helper, EntityTypes.SKELETON, villager.position().add(0.0, 0.0, 9.0));
        Arrow mine = arrowAt(helper, player, villager);
        Arrow theirs = arrowAt(helper, skeleton, villager);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(player.equals(mine.getOwner()), "the summoner still owns the arrow");
        helper.assertTrue(mine.getDeltaMovement().x < 0.0, "and it still flies at the villager");
        helper.assertTrue(theirs.getOwner() instanceof BoundVex, "the skeleton's arrow was reversed the same tick");
        helper.succeed();
    }

    /**
     * When a ghast's fireball flies at the summoner, should the Evader leave it alone (Part 5's return owns those): its
     * owner and velocity are unchanged.
     *
     * <p>Method under test: {@code EvaderMove.guard}: the {@code LargeFireball} exclusion
     */
    static void leavesAGhastFireballAlone(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);
        Mob skeleton =
                foe(helper, EntityTypes.SKELETON, scene.player().position().add(9.0, 0.0, 0.0));
        LargeFireball fireball = new LargeFireball(helper.getLevel(), skeleton, new Vec3(-0.5, 0.0, 0.0), 1);
        fireball.setPos(center(scene.ward()).add(2.0, 0.0, 0.0));
        helper.getLevel().addFreshEntity(fireball);
        GameTestSupport.whenDone(helper, fireball::discard);
        Vec3 before = fireball.getDeltaMovement();

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        helper.assertTrue(skeleton.equals(fireball.getOwner()), "the owner is unchanged");
        helper.assertValueEqual(fireball.getDeltaMovement(), before, "and the velocity is unchanged");
        helper.succeed();
    }

    /**
     * When the Evader has reversed a skeleton's arrow and steps again, should the arrow keep flying away and stay the
     * vex's: a vex-owned arrow is never deflected twice.
     *
     * <p>Method under test: {@code EvaderMove.guard}: the owner is on the summoner's side after one reversal
     */
    static void doesNotReverseTwice(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);
        Mob skeleton =
                foe(helper, EntityTypes.SKELETON, scene.player().position().add(9.0, 0.0, 0.0));
        Arrow arrow = arrowAt(helper, skeleton, scene.ward());

        // Act
        TeamMoves.tick(scene.player());
        arrow.setPos(center(scene.ward()).add(1.5, 0.0, 0.0));
        arrow.setDeltaMovement(-0.5, 0.0, 0.0); // aimed at the ward again, but it is now the vex's
        TeamMoves.tick(scene.player());

        // Assert
        helper.assertTrue(arrow.getOwner() instanceof BoundVex, "the vex still owns the arrow");
        helper.assertTrue(arrow.getDeltaMovement().x < 0.0, "and it is not reversed a second time");
        helper.succeed();
    }

    /**
     * When a pig stands 2.5 blocks from the summoner, should the Evader leave it alone (not a hostile): its velocity
     * stays zero.
     *
     * <p>Method under test: {@code EvaderMove.guard}: the {@code huntable} filter
     */
    static void leavesAPigAlone(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);
        Pig pig = foe(helper, EntityTypes.PIG, overTheShell(scene.player()));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(
                        5, () -> helper.assertValueEqual(pig.getDeltaMovement(), Vec3.ZERO, "the pig is not shoved"))
                .thenSucceed();
    }

    /**
     * When the ward moves six blocks, should every member follow and settle about two blocks from the new middle.
     *
     * <p>Method under test: {@code EvaderMove.tick}: the orbit follows the ward
     */
    static void followsAMovingWard(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);

        // Act
        Vec3 target = helper.relativeVec(scene.player().position()).add(6.0, 0.0, 0.0);
        MockPlayers.moveTo(helper, scene.player(), target, NORTH);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        scene.vexes().stream().allMatch(vex -> vex.position().distanceTo(center(scene.ward())) <= 2.7),
                        "every member is within 2.7 blocks of the new middle"))
                .thenSucceed();
    }

    /**
     * When a skeleton's arrow flies at the summoner at three blocks a tick (a full-draw player arrow) from five blocks
     * out, so that no tick's position falls inside the two-block window, should a member still reverse it.
     *
     * <p>Method under test: {@code EvaderMove.guard}: the look-ahead along the arrow's next step
     */
    static void reversesAFastArrow(GameTestHelper helper) {
        // Arrange
        Scene scene = guardingTheSummoner(helper);
        Mob skeleton =
                foe(helper, EntityTypes.SKELETON, scene.player().position().add(9.0, 0.0, 0.0));
        Arrow arrow = arrowAt(helper, skeleton, scene.ward());
        arrow.setPos(center(scene.ward()).add(5.0, 0.0, 0.0));
        arrow.setDeltaMovement(-3.0, 0.0, 0.0);

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        helper.assertTrue(arrow.getOwner() instanceof BoundVex, "a vex owns the arrow now");
        helper.assertTrue(arrow.getDeltaMovement().x > 0.0, "and it flies away from the summoner");
        helper.succeed();
    }

    /**
     * When a hurt villager (not a player: its delta movement is its real motion) walks steadily east at 0.1 blocks a
     * tick, should every member stay within 3.2 blocks of its middle for 20 ticks once the walk is under way (the
     * orbit follows the ward's motion, it does not trail it).
     *
     * <p>Method under test: {@code EvaderMove.tick}: the ward's known movement feeds the steering
     */
    static void keepsUpWithASteadilyMovingWard(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Villager villager = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(0.0, 0.0, 2.0));
        villager.setHealth(villager.getMaxHealth() * 0.4F);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hangPoint(helper));
        helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, villager), "setup: the Evader starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        BoundVexTestSupport.everyTick(helper, () -> {
            if (helper.getTick() < 90) {
                villager.setPos(villager.position().add(0.1, 0.0, 0.0));
                villager.setDeltaMovement(0.1, 0.0, 0.0);
            }
        });

        // Act & Assert
        helper.startSequence()
                .thenIdle(60)
                .thenExecuteFor(
                        20,
                        () -> vexes.forEach(vex -> helper.assertTrue(
                                vex.position().distanceTo(center(villager)) <= 3.2,
                                "a member trails the ward by " + vex.position().distanceTo(center(villager)))))
                .thenSucceed();
    }
}
