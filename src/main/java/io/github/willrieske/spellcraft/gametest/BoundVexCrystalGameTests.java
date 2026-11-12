package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.CRIT_HEALTH;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.critHusk;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.endVoid;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.huskIn;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.padded;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.plainVictim;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.voidPlayer;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.FEET;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.AttackCrystalGoal;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * GameTests of the End crystal goal: a Bound Vex flies to a crystal and breaks it while a dragon lives, the
 * blast spares the summoner's side, and how the claims on crystals are given up and cleared.
 *
 * <p>Functional spec §5.5, §7.9, §10.8, §10.9, §11.2.
 */
public final class BoundVexCrystalGameTests {
    /** The environment (a batch of its own) of the server-stop crystal test: the stop handler clears every claim. */
    private static final String SERVER_STOP_CRYSTALS = "bound_vex_world_server_stop_crystals";

    /** How many ticks the give-up test's body may take: the 200-tick limit, the retry delay's start and a flight. */
    private static final int CRYSTAL_LONG_BODY = 500;

    /** How far the crystal tests' places reach: every crystal and vex stands within 30 blocks of the slot. */
    private static final int CRYSTAL_REACH = 48;

    /** How close to full health a vex counts as unhurt. */
    private static final float UNHURT = 0.001F;

    private BoundVexCrystalGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_crystal_broken", BoundVexCrystalGameTests::crystalBroken);
        padded("bound_vex_world_crystal_spares_your_side", BoundVexCrystalGameTests::crystalSparesYourSide);
        padded(
                "bound_vex_world_crystal_invulnerable_left_alone",
                BoundVexCrystalGameTests::crystalInvulnerableLeftAlone);
        padded("bound_vex_world_crystal_removed_on_the_way", BoundVexCrystalGameTests::crystalRemovedOnTheWay);
        padded("bound_vex_world_crystal_needs_a_dragon", BoundVexCrystalGameTests::crystalNeedsADragon);
        padded("bound_vex_world_crystal_one_each", BoundVexCrystalGameTests::crystalOneEach);
        padded("bound_vex_world_crystal_only_in_the_end", BoundVexCrystalGameTests::crystalOnlyInTheEnd);
        padded(
                "bound_vex_world_crystal_needs_a_summoner_at_the_start",
                BoundVexCrystalGameTests::crystalNeedsASummonerAtTheStart);
        padded(
                "bound_vex_world_crystal_summoner_lost_mid_flight",
                BoundVexCrystalGameTests::crystalSummonerLostMidFlight);
        padded("bound_vex_world_crystal_dragon_dies_mid_flight", BoundVexCrystalGameTests::crystalDragonDiesMidFlight);
        padded("bound_vex_world_crystal_neighbors_both_go", BoundVexCrystalGameTests::crystalNeighborsBothGo);
        padded(
                "bound_vex_world_crystal_blast_spares_your_side_without_you",
                BoundVexCrystalGameTests::crystalBlastSparesYourSideWithoutYou);
        padded("bound_vex_world_crystal_not_while_fighting", BoundVexCrystalGameTests::crystalNotWhileFighting);
        padded(
                "bound_vex_world_crystal_staying_vex_only_in_its_area",
                BoundVexCrystalGameTests::crystalStayingVexOnlyInItsArea);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_world_crystal_gives_up_after_200_ticks",
                CRYSTAL_LONG_BODY,
                BoundVexCrystalGameTests::crystalGivesUpAfter200Ticks);
        padded("bound_vex_crystal_attacking_vex_is_not_free", BoundVexCrystalGameTests::crystalVexIsNotFree);
        padded("bound_vex_world_crystal_blast_by_a_player", BoundVexCrystalGameTests::crystalBlastByAPlayer);
        padded(
                "bound_vex_world_crystal_blast_spares_another_players_vex",
                BoundVexCrystalGameTests::crystalBlastSparesAnotherPlayersVex);
        FangGameTests.addPadded(
                SERVER_STOP_CRYSTALS,
                "bound_vex_world_crystal_claims_cleared_on_server_stop",
                BoundVexCrystalGameTests::crystalClaimsClearedOnServerStop);
    }

    /**
     * The point {@code east} blocks east and {@code south} blocks south of the slot's block, at the slot's height. A
     * crystal there floats in the void with nothing under it, so the fire it sets each tick in a dragon fight
     * ({@code EndCrystal.tick}) finds no support and is never placed.
     */
    private static Vec3 crystalSpot(BlockPos slot, double east, double south) {
        return new Vec3(slot.getX() + 0.5 + east, slot.getY(), slot.getZ() + 0.5 + south);
    }

    /** An end crystal at {@code at} in {@code end}, discarded when the test ends. */
    private static EndCrystal crystalIn(GameTestHelper helper, ServerLevel end, Vec3 at) {
        EndCrystal crystal = EntityTypes.END_CRYSTAL.create(end, EntitySpawnReason.COMMAND);
        if (crystal == null) {
            throw helper.assertionException("setup: could not create an end crystal");
        }
        crystal.setPos(at);
        end.addFreshEntity(crystal);
        whenDone(helper, crystal::discard);
        return crystal;
    }

    /**
     * A vex of {@code player} with its full AI and no target goals, 2 blocks above {@code at}; when {@code dragon} is
     * set, the dragon counts as alive for it (undone when the test ends).
     */
    private static BoundVex crystalVex(
            GameTestHelper helper, ServerLevel end, ServerPlayer player, Vec3 at, boolean dragon) {
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, end, at.add(0.0, 2.0, 0.0), player));
        if (dragon) {
            AttackCrystalGoal.scriptDragon(vex, true);
            whenDone(helper, () -> AttackCrystalGoal.unscriptDragon(vex));
        }
        return vex;
    }

    /** Fails the test unless {@code entity} has its full health. */
    private static void assertUnhurt(GameTestHelper helper, LivingEntity entity, String who) {
        helper.assertTrue(
                entity.getMaxHealth() - entity.getHealth() < UNHURT,
                who + " is unhurt, health " + entity.getHealth() + " of " + entity.getMaxHealth());
    }

    /**
     * When a following vex has a crystal 20 blocks away and the dragon lives, should it fly there and break it, and
     * come away unhurt.
     *
     * <p>Method under test: {@code AttackCrystalGoal}: the approach and the strike
     *
     * <p>Functional spec §10.8, §10.9.
     */
    static void crystalBroken(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(crystal.isRemoved(), "the crystal is broken"))
                .thenExecute(() -> {
                    helper.assertTrue(vex.isAlive(), "the vex lives");
                    assertUnhurt(helper, vex, "the vex");
                })
                .thenSucceed();
    }

    /**
     * When a vex breaks a crystal two blocks from its summoner, a tamed wolf and a second vex of the summoner, should
     * none of them be hurt or thrown, and neither the breaking vex.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage} and {@code onExplosionKnockback}
     *
     * <p>Functional spec §10.9.
     */
    static void crystalSparesYourSide(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        Vec3 spot = crystalSpot(slot, 2, 0);
        BoundVex breaker = crystalVex(helper, end, player, crystalSpot(slot, -10, 0), true);
        BoundVex second = BoundVexTestSupport.noTargets(vexIn(helper, end, spot.add(0.0, 0.0, 2.0), player));
        second.setNoAi(true);
        Wolf wolf = EntityTypes.WOLF.create(end, EntitySpawnReason.COMMAND);
        if (wolf == null) {
            throw helper.assertionException("setup: could not create a wolf");
        }
        wolf.setNoAi(true);
        wolf.setNoGravity(true);
        wolf.setPos(spot.add(0.0, 0.0, -2.0));
        end.addFreshEntity(wolf);
        wolf.tame(player);
        whenDone(helper, wolf::discard);
        EndCrystal crystal = crystalIn(helper, end, spot);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(crystal.isRemoved(), "the crystal is broken"))
                .thenExecute(() -> {
                    assertUnhurt(helper, player, "the player");
                    helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "the player is not thrown");
                    assertUnhurt(helper, wolf, "the wolf");
                    helper.assertValueEqual(wolf.getDeltaMovement(), Vec3.ZERO, "the wolf is not thrown");
                    assertUnhurt(helper, second, "the second vex");
                    helper.assertValueEqual(second.getDeltaMovement(), Vec3.ZERO, "the second vex is not thrown");
                    assertUnhurt(helper, breaker, "the breaking vex");
                })
                .thenSucceed();
    }

    /**
     * When the only crystal is invulnerable, should the vex leave it alone for 60 ticks and stay by its summoner.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: breakable crystals only
     */
    static void crystalInvulnerableLeftAlone(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 15, 0));
        crystal.setPermanentlyInvulnerable(true);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                    helper.assertTrue(vex.distanceTo(player) <= 6.0, "the vex never left, " + vex.distanceTo(player));
                })
                .thenSucceed();
    }

    /**
     * When the crystal a vex flies to disappears on the way, should the vex's goal end and start again for the other
     * crystal, and break it. (The crystals stand 16 and 17 blocks off, not 30: after five ticks of flight the second
     * must still be within the 32-block search.)
     *
     * <p>Method under test: {@code AttackCrystalGoal.canContinueToUse}, then {@code canUse}
     *
     * <p>Functional spec §10.8.
     */
    static void crystalRemovedOnTheWay(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal near = crystalIn(helper, end, crystalSpot(slot, 16, 0));
        EndCrystal far = crystalIn(helper, end, crystalSpot(slot, -17, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(AttackCrystalGoal.isClaimed(near), "setup: heading for the near one"))
                .thenExecute(() -> {
                    helper.assertFalse(AttackCrystalGoal.isClaimed(far), "setup: not for the far one");
                    near.discard();
                })
                .thenExecuteAfter(
                        2,
                        () -> helper.assertFalse(
                                AttackCrystalGoal.isClaimed(near), "the removed crystal's claim is given up"))
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(far), "the far one is next"))
                .thenWaitUntil(() -> helper.assertTrue(far.isRemoved(), "the far crystal is broken"))
                .thenSucceed();
    }

    /**
     * When the dragon does not live (scripted), should the vex leave a crystal alone for 60 ticks; and in the
     * Overworld test level, where there is no fight, should {@code livingDragon} be false.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}, {@code AttackCrystalGoal.livingDragon}
     *
     * <p>Functional spec §10.8.
     */
    static void crystalNeedsADragon(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, end, crystalSpot(slot, 0, 2), player));
        AttackCrystalGoal.scriptDragon(vex, false);
        whenDone(helper, () -> AttackCrystalGoal.unscriptDragon(vex));
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 10, 0));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                    helper.assertFalse(
                            AttackCrystalGoal.livingDragon(helper.getLevel()), "the Overworld has no living dragon");
                    helper.assertFalse(AttackCrystalGoal.livingDragon(end), "the test End has no living dragon");
                })
                .thenSucceed();
    }

    /**
     * When two vexes of one player have two crystals six blocks apart, should each go for a different one (both
     * crystals are claimed at the same time), both crystals break and both vexes live.
     *
     * <p>Method under test: {@code AttackCrystalGoal}: the claim map
     *
     * <p>Functional spec §10.8, §10.9.
     */
    static void crystalOneEach(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex first = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVex second = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal a = crystalIn(helper, end, crystalSpot(slot, 15, -3));
        EndCrystal b = crystalIn(helper, end, crystalSpot(slot, 15, 3));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        AttackCrystalGoal.isClaimed(a) && AttackCrystalGoal.isClaimed(b), "each crystal has a vex"))
                .thenWaitUntil(() -> helper.assertTrue(a.isRemoved() && b.isRemoved(), "both crystals are broken"))
                .thenExecute(() -> {
                    helper.assertTrue(first.isAlive() && second.isAlive(), "both vexes live");
                    assertUnhurt(helper, first, "the first vex");
                    assertUnhurt(helper, second, "the second vex");
                })
                .thenSucceed();
    }

    /**
     * When the vex has a target (a husk with 1000 health three blocks away), should it leave a crystal ten blocks away
     * alone for 60 ticks.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: no target
     *
     * <p>Functional spec §10.8.
     */
    static void crystalNotWhileFighting(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        Husk husk = huskIn(end, crystalSpot(slot, -3, 0).add(0.0, 2.0, 0.0));
        TeamMoveTestSupport.setBase(husk, Attributes.MAX_HEALTH, 1000.0);
        husk.setHealth(1000.0F);
        whenDone(helper, husk::discard);
        vex.setTarget(husk);
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 10, 0));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(vex.getTarget() != null, "setup: the vex still fights");
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                })
                .thenSucceed();
    }

    /**
     * When a vex stays at a post, should it break a crystal five blocks from the post but leave one twelve blocks away
     * (on the other side) alone for 100 ticks.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: the stay area
     *
     * <p>Functional spec §10.8.
     */
    static void crystalStayingVexOnlyInItsArea(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        vex.setStaying(true);
        EndCrystal inside = crystalIn(helper, end, crystalSpot(slot, 5, 0).add(0.0, 2.0, 0.0));
        EndCrystal outside = crystalIn(helper, end, crystalSpot(slot, -12, 0).add(0.0, 2.0, 0.0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(inside.isRemoved(), "the near crystal is broken"))
                .thenExecuteAfter(100, () -> helper.assertTrue(outside.isAlive(), "the far crystal is left alone"))
                .thenSucceed();
    }

    /**
     * When a Bound Vex holds a live claim on a crystal (an attack job), should it not be free and not be listed.
     *
     * <p>Method under test: {@code Jobs.busy} (the crystal wiring), through {@link TeamMoves#isFree}
     *
     * <p>Functional spec §11.2.
     */
    static void crystalVexIsNotFree(GameTestHelper helper) {
        // Arrange (as crystalGivesUpAfter200Ticks)
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex worker = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the vex has it"))
                .thenExecute(() -> {
                    worker.setStaying(false);
                    TeamMoveTestSupport.assertFreeButForJobs(helper, worker, player);
                    helper.assertFalse(TeamMoves.isFree(worker, player), "a vex mid-attack is not free");
                    helper.assertFalse(TeamMoves.freeVexes(player).contains(worker), "and is not listed");
                })
                .thenSucceed();
    }

    /**
     * When a vex is held away from its crystal, should it give up after 200 ticks, hold no claim and take no crystal
     * for the retry delay, so a second vex added then breaks the crystal.
     *
     * <p>Method under test: {@code AttackCrystalGoal.stop}: the limit and the retry delay
     *
     * <p>Functional spec §10.8.
     */
    static void crystalGivesUpAfter200Ticks(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex held = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVexTestSupport.holdAt(helper, held, held.position());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the vex has it"))
                .thenWaitUntil(() -> helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the claim is given up"))
                .thenExecute(() -> helper.assertTrue(crystal.isAlive(), "the crystal is still there"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the held vex takes none for a while");
                    crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
                })
                .thenWaitUntil(() -> helper.assertTrue(crystal.isRemoved(), "the second vex breaks it"))
                .thenSucceed();
    }

    /**
     * When a player breaks a crystal (no scripted dragon) with a vex two blocks from it and the player ten blocks
     * away, should the vex be unhurt and unmoved, and the player thrown as vanilla does. (The player takes no damage
     * from a blast they caused themselves: the server's PvP rule blocks it, so health is not asserted.)
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage} and {@code onExplosionKnockback}: a crystal's blast
     * never hurts or moves a Bound Vex, whoever set it off
     *
     * <p>Functional spec §10.9.
     */
    static void crystalBlastByAPlayer(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        Vec3 spot = crystalSpot(slot, 10, 0);
        EndCrystal crystal = crystalIn(helper, end, spot);
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, end, spot.add(0.0, 0.0, 2.0), player));
        vex.setNoAi(true);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    crystal.hurtServer(end, end.damageSources().playerAttack(player), 1.0F);
                    helper.assertTrue(crystal.isRemoved(), "the crystal blew up");
                    assertUnhurt(helper, vex, "the vex");
                    helper.assertValueEqual(vex.getDeltaMovement(), Vec3.ZERO, "the vex is not thrown");
                    helper.assertTrue(player.getDeltaMovement().length() > 0.01, "the player is thrown");
                })
                .thenSucceed();
    }

    /**
     * When one player's vex breaks a crystal two blocks from another player's vex, should the other vex be unhurt and
     * unmoved.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: a crystal's blast never hurts a Bound Vex
     *
     * <p>Functional spec §10.9.
     */
    static void crystalBlastSparesAnotherPlayersVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerPlayer other = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        Vec3 spot = crystalSpot(slot, 10, 0);
        crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVex theirs = BoundVexTestSupport.noTargets(vexIn(helper, end, spot.add(0.0, 0.0, 2.0), other));
        theirs.setNoAi(true);
        EndCrystal crystal = crystalIn(helper, end, spot);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(crystal.isRemoved(), "the crystal is broken"))
                .thenExecute(() -> {
                    assertUnhurt(helper, theirs, "the other player's vex");
                    helper.assertValueEqual(theirs.getDeltaMovement(), Vec3.ZERO, "the other vex is not thrown");
                })
                .thenSucceed();
    }

    /**
     * When the server stops while a held vex has a crystal claimed, should the claim be free as soon as the stop
     * handler has run. The handler runs in an environment of its own ({@link #SERVER_STOP_CRYSTALS}): it clears every
     * test's state.
     *
     * <p>Method under test: {@code BoundVexEvents.onServerStopped}, through {@code AttackCrystalGoal}'s claim map
     */
    static void crystalClaimsClearedOnServerStop(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVexTestSupport.holdAt(helper, vex, vex.position());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the claim stands"))
                .thenExecute(() -> {
                    BoundVexEvents.onServerStopped(
                            new ServerStoppedEvent(helper.getLevel().getServer()));
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the stop handler freed the claim");
                })
                .thenSucceed();
    }

    /**
     * When a vex with a scripted living dragon is in the Overworld test level and a crystal stands eight blocks away,
     * should the crystal be alive and unclaimed after 60 ticks (the job is for the End only).
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: the End clause
     *
     * <p>Functional spec §10.8.
     */
    static void crystalOnlyInTheEnd(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        BoundVex vex =
                BoundVexTestSupport.noTargets(BoundVexTestSupport.boundVex(helper, FEET.offset(0, 3, 0), player));
        AttackCrystalGoal.scriptDragon(vex, true);
        whenDone(helper, () -> AttackCrystalGoal.unscriptDragon(vex));
        EndCrystal crystal =
                crystalIn(helper, helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(FEET.offset(8, 3, 0))));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "no vex claimed it");
                })
                .thenSucceed();
    }

    /**
     * When a vex's summoner mark names nobody who is here from the start and a crystal stands 15 blocks away, should
     * the crystal be alive and unclaimed after 60 ticks.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canUse}: a summoner must be present
     *
     * <p>Functional spec §5.5.
     */
    static void crystalNeedsASummonerAtTheStart(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        vex.setData(AllyAttachments.SUMMONER, UUID.randomUUID());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 15, 0));

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "no vex claimed it");
                })
                .thenSucceed();
    }

    /**
     * When a held vex has claimed a crystal and its summoner mark then names nobody who is here, should the claim be
     * given up within 2 ticks.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canContinueToUse}: the summoner must stay present
     *
     * <p>Functional spec §5.5.
     */
    static void crystalSummonerLostMidFlight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVexTestSupport.holdAt(helper, vex, vex.position());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the vex has it"))
                .thenExecute(() -> vex.setData(AllyAttachments.SUMMONER, UUID.randomUUID()))
                .thenExecuteAfter(2, () -> {
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the claim is given up");
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                })
                .thenSucceed();
    }

    /**
     * When a held vex has claimed a crystal and the dragon (scripted) then dies, should the claim be given up within
     * 2 ticks and the crystal survive.
     *
     * <p>Method under test: {@code AttackCrystalGoal.canContinueToUse}: the dragon must stay alive
     *
     * <p>Functional spec §10.8.
     */
    static void crystalDragonDiesMidFlight(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = voidPlayer(helper);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        BoundVex vex = crystalVex(helper, end, player, crystalSpot(slot, 0, 0), true);
        BoundVexTestSupport.holdAt(helper, vex, vex.position());
        EndCrystal crystal = crystalIn(helper, end, crystalSpot(slot, 20, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(AttackCrystalGoal.isClaimed(crystal), "setup: the vex has it"))
                .thenExecute(() -> AttackCrystalGoal.scriptDragon(vex, false))
                .thenExecuteAfter(2, () -> {
                    helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the claim is given up");
                    helper.assertTrue(crystal.isAlive(), "the crystal is still there");
                })
                .thenSucceed();
    }

    /**
     * When one vex breaks a crystal that has another crystal three blocks beside it and the summoner (vulnerable)
     * stands two blocks from the first, should both crystals be gone, as vanilla does it (the neighbor is removed by
     * the blast, without a second explosion of its own), and the summoner be unhurt with no velocity.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage} and {@code onExplosionKnockback}: a crystal chain
     *
     * <p>Functional spec §10.9.
     */
    static void crystalNeighborsBothGo(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, CRYSTAL_REACH);
        BlockPos slot = player.blockPosition();
        crystalVex(helper, end, player, crystalSpot(slot, -10, 0), true);
        EndCrystal first = crystalIn(helper, end, crystalSpot(slot, 2, 0));
        EndCrystal second = crystalIn(helper, end, crystalSpot(slot, 5, 0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(first.isRemoved() && second.isRemoved(), "both crystals are gone"))
                .thenExecute(() -> {
                    assertUnhurt(helper, player, "the summoner");
                    helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "the summoner is not thrown");
                })
                .thenSucceed();
    }

    /**
     * When a vex whose summoner mark names nobody here is the cause of a crystal's blast, with another player (not
     * its summoner, vulnerable) two blocks from the crystal and a husk beside them, should the player be unhurt with
     * no velocity and the husk hurt.
     *
     * <p>Method under test: {@code BoundVexEvents.onExplosionKnockback}: the absent-summoner case
     *
     * <p>Functional spec §7.9.
     */
    static void crystalBlastSparesYourSideWithoutYou(GameTestHelper helper) {
        // Arrange
        ServerPlayer owner = voidPlayer(helper);
        ServerLevel end = endVoid(helper, owner, CRYSTAL_REACH);
        BlockPos slot = owner.blockPosition();
        Vec3 spot = crystalSpot(slot, 10, 0);
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, end, spot.add(0.0, 0.0, -6.0), owner));
        vex.setNoAi(true);
        vex.setData(AllyAttachments.SUMMONER, UUID.randomUUID());
        ServerPlayer bystander = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        whenDone(helper, () -> MockPlayers.moveTo(helper, bystander, FangGameTests.STAND));
        Vec3 at = spot.add(0.0, 0.0, 2.0);
        bystander.teleportTo(end, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, false);
        bystander.hasChangedDimension(); // a mock player is never ticked, which would end the move's immunity
        plainVictim(bystander);
        Husk husk = critHusk(helper, end, spot.add(0.0, 0.0, 3.0));
        EndCrystal crystal = crystalIn(helper, end, spot);

        // Act
        crystal.hurtServer(end, end.damageSources().mobAttack(vex), 1.0F);

        // Assert
        helper.assertTrue(crystal.isRemoved(), "the crystal blew up");
        helper.assertValueEqual(bystander.getHealth(), CRIT_HEALTH, "the bystander is unhurt");
        helper.assertValueEqual(bystander.getDeltaMovement(), Vec3.ZERO, "the bystander is not thrown");
        helper.assertTrue(husk.getHealth() < CRIT_HEALTH, "the husk is hurt, health " + husk.getHealth());
        helper.succeed();
    }
}
