package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.casterVex;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.raidScene;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.Dice;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.evoking.VexSummons;
import io.github.willrieske.spellcraft.gametest.ScrollTestSupport.RaidScene;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import io.github.willrieske.spellcraft.spell.rules.TeamMoveRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** GameTests of the Caster flag, its roll and its goal (Parts 6-7 spec §6.1, §6.2). */
final class CasterGameTests {
    /** How many batches of three the statistical test summons. */
    private static final int BATCHES = 100;

    /** Five standard deviations of 300 rolls at 1 in 3 is 41; the share must fall within 100 plus or minus that. */
    private static final int SHARE_LOW = 59;

    /** The top of the statistical test's accepted range. */
    private static final int SHARE_HIGH = 141;

    /** Squared distance inside which a vex at rest beside an unhurt frozen husk counts as stalled. */
    private static final double STALL_DISTANCE_SQR = 9.0;

    /** Squared speed (blocks per tick) under which a vex counts as at rest. */
    private static final double AT_REST_SPEED_SQR = 1.0E-4;

    private CasterGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(
                "caster_dice", "caster_scripted_roll_makes_a_caster", CasterGameTests::scriptedRollMakesACaster);
        FangGameTests.addPadded(
                "caster_dice", "caster_scripted_roll_makes_a_plain_vex", CasterGameTests::scriptedRollMakesAPlainVex);
        FangGameTests.addPadded(SHARED, "caster_flag_survives_save_and_load", CasterGameTests::flagSurvivesSaveAndLoad);
        FangGameTests.addPadded(SHARED, "caster_command_vex_is_not_a_caster", CasterGameTests::commandVexIsNotACaster);
        FangGameTests.addPadded(
                "caster_dice_real", "caster_share_is_about_a_third", CasterGameTests::shareIsAboutAThird);
        FangGameTests.addPadded(SHARED, "caster_never_hurts_its_target", CasterGameTests::neverHurtsItsTarget);
        FangGameTests.addPadded(SHARED, "caster_keeps_its_distance", 200, CasterGameTests::keepsItsDistance);
        FangGameTests.addPadded(SHARED, "caster_backs_off", 200, CasterGameTests::backsOff);
        FangGameTests.addPadded(SHARED, "caster_never_shoots", CasterGameTests::neverShoots);
        FangGameTests.addPadded(SHARED, "caster_plain_vex_charges", 200, CasterGameTests::plainVexCharges);
        FangGameTests.addPadded(SHARED, "caster_plain_vex_shoots", 200, CasterGameTests::plainVexShoots);
        FangGameTests.addPadded(SHARED, "caster_not_free_for_ordinary_moves", CasterGameTests::notFreeForOrdinaryMoves);
        FangGameTests.addPadded(SHARED, "caster_free_for_a_raid", CasterGameTests::freeForARaid);
        FangGameTests.addPadded(
                SHARED, "caster_counts_toward_a_raid_trigger", CasterGameTests::countsTowardARaidTrigger);
        // Scripts Dice.RAID_KIT, so it runs in the environment Part 6's Raid tests use for the same die.
        FangGameTests.addPadded("team_move_dice", "caster_raid_kit", 300, CasterGameTests::raidKit);
    }

    /**
     * When the Caster die rolls 0, 1 and 2 on a summon of three, should only the first vex be a Caster, as the synced
     * flag the client reads.
     *
     * <p>Method under test: {@code VexSummons.summon}: the Caster roll
     */
    static void scriptedRollMakesACaster(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);

        // Act
        List<BoundVex> vexes;
        try {
            Dice.CASTER_ROLL.script(0, 1, 2);
            vexes = VexSummons.summon(helper.getLevel(), player, 3);
        } finally {
            Dice.unscriptAll();
        }
        vexes.forEach(vex -> GameTestSupport.whenDone(helper, vex::discard));

        // Assert
        helper.assertTrue(vexes.get(0).isCaster(), "roll 0 makes a Caster");
        helper.assertFalse(vexes.get(1).isCaster(), "roll 1 does not");
        helper.assertFalse(vexes.get(2).isCaster(), "roll 2 does not");
        helper.succeed();
    }

    /**
     * When the Caster die rolls 1 on a summon, should the vex be a plain vex.
     *
     * <p>Method under test: {@code VexSummons.summon}: the Caster roll
     */
    static void scriptedRollMakesAPlainVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);

        // Act
        List<BoundVex> vexes;
        try {
            Dice.CASTER_ROLL.script(1);
            vexes = VexSummons.summon(helper.getLevel(), player, 1);
        } finally {
            Dice.unscriptAll();
        }
        vexes.forEach(vex -> GameTestSupport.whenDone(helper, vex::discard));

        // Assert
        helper.assertValueEqual(vexes.size(), 1, "one vex");
        helper.assertFalse(vexes.get(0).isCaster(), "a plain vex");
        helper.succeed();
    }

    /**
     * When a Caster with two ready times is saved and loaded, should the copy keep the flag and both times.
     *
     * <p>Method under test: {@code BoundVex.addAdditionalSaveData} and {@code readAdditionalSaveData}
     */
    static void flagSurvivesSaveAndLoad(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        BoundVex vex = BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player);
        vex.setCaster(true);
        vex.setMirrorReadyAt(5000L);
        vex.setGhostrailReadyAt(6000L);

        // Act
        BoundVex copy = (BoundVex) GameTestSupport.reloaded(helper.getLevel(), vex);

        // Assert
        helper.assertTrue(copy.isCaster(), "still a Caster");
        helper.assertValueEqual(copy.mirrorReadyAt(), 5000L, "Mirror ready time");
        helper.assertValueEqual(copy.ghostrailReadyAt(), 6000L, "Ghostrail ready time");
        helper.succeed();
    }

    /**
     * When a vex is made the way the command makes one (no summoner roll), should it not be a Caster.
     *
     * <p>Method under test: {@code BoundVex.isCaster}: the default
     */
    static void commandVexIsNotACaster(GameTestHelper helper) {
        // Arrange & Act
        BoundVex vex = BoundVexTestSupport.unmarkedVex(helper, BoundVexTestSupport.AIR_SPOT);

        // Assert
        helper.assertFalse(vex.isCaster(), "not a Caster");
        helper.succeed();
    }

    /**
     * When 300 vexes are summoned with the real dice, should the number of Casters be within five standard deviations
     * of a third.
     *
     * <p>Method under test: {@code VexSummons.summon}: the Caster roll
     */
    static void shareIsAboutAThird(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        ServerLevel level = helper.getLevel();

        // Act
        int casters = 0;
        for (int batch = 0; batch < BATCHES; batch++) {
            List<BoundVex> vexes = VexSummons.summon(level, player, 3);
            casters += (int) vexes.stream().filter(BoundVex::isCaster).count();
            vexes.forEach(BoundVex::discard);
        }

        // Assert
        helper.assertTrue(casters >= SHARE_LOW && casters <= SHARE_HIGH, "about 100 Casters in 300, got " + casters);
        helper.succeed();
    }

    /**
     * When a Caster is told to target a husk beside it, should the husk keep all its health for 60 ticks.
     *
     * <p>Method under test: {@code CasterGoal}: the Caster never attacks
     */
    static void neverHurtsItsTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        BoundVex vex = casterVex(helper, player, hang);
        Husk husk = TeamMoveTestSupport.foe(helper, EntityTypes.HUSK, hang.add(1.0, 0.0, 0.0));
        vex.setTarget(husk);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        60, () -> helper.assertValueEqual(husk.getHealth(), husk.getMaxHealth(), "the husk is unhurt"))
                .thenSucceed();
    }

    /**
     * When a Caster targets a husk 9 blocks away, should it come into the 4 to 6 block band and then stay within a
     * block of it.
     *
     * <p>Method under test: {@code CasterGoal.tick}: closing in and holding
     */
    static void keepsItsDistance(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        Husk husk = TeamMoveTestSupport.foe(helper, EntityTypes.HUSK, hang);
        BoundVex vex = casterVex(helper, player, hang.add(9.0, 0.0, 0.0));
        vex.setTarget(husk);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    double distance = vex.distanceTo(husk);
                    helper.assertTrue(distance >= 3.5 && distance <= 6.5, "in the band, not " + distance);
                })
                .thenExecuteFor(20, () -> {
                    double distance = vex.distanceTo(husk);
                    helper.assertTrue(distance >= 3.0 && distance <= 7.0, "still near the band, not " + distance);
                })
                .thenSucceed();
    }

    /**
     * When a Caster targets a husk 2 blocks away, should it back off to at least 3.5 blocks.
     *
     * <p>Method under test: {@code CasterGoal.tick}: backing off
     */
    static void backsOff(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        Husk husk = TeamMoveTestSupport.foe(helper, EntityTypes.HUSK, hang);
        BoundVex vex = casterVex(helper, player, hang.add(2.0, 0.0, 0.0));
        vex.setTarget(husk);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.distanceTo(husk) >= 3.5, "backed off"))
                .thenSucceed();
    }

    /**
     * When a Caster holds a bow and a husk stands 5 blocks away, should it shoot nothing in 120 ticks.
     *
     * <p>Method under test: {@code RangedGearGoal.canUse}: a Caster does not shoot
     */
    static void neverShoots(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        Husk husk = TeamMoveTestSupport.foe(helper, EntityTypes.HUSK, hang);
        BoundVex vex = casterVex(helper, player, hang.add(5.0, 0.0, 0.0));
        vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        vex.setTarget(husk);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(120, () -> {
                    helper.assertValueEqual(husk.getHealth(), husk.getMaxHealth(), "the husk is unhurt");
                    helper.assertTrue(
                            helper.getLevel()
                                    .getEntitiesOfClass(
                                            AbstractArrow.class,
                                            vex.getBoundingBox().inflate(20.0))
                                    .isEmpty(),
                            "no arrow was shot");
                })
                .thenSucceed();
    }

    /**
     * When a plain vex (the control) targets a husk, should it charge and hurt it.
     *
     * <p>Method under test: {@code BoundVex.ChargeGoal.canUse}: a plain vex still fights
     */
    static void plainVexCharges(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        Husk husk = TeamMoveTestSupport.foe(helper, EntityTypes.HUSK, hang);
        BoundVex vex = BoundVexTestSupport.noTargets(
                BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player));
        BoundVexTestSupport.pauseFollowing(vex);
        Vec3 start = hang.add(5.0, 0.0, 0.0);
        vex.setPos(start);
        vex.setTarget(husk);
        // A charge at a husk that never moves can end with the vex at rest near it but outside its box: the vex flies
        // at the husk's eyes and the move control stops it within about half a block of that point, which can leave
        // its box just over the husk's head (a real target moves, so vanilla never meets this). A vex at rest that
        // close to an unhurt husk is put back where it began.
        BoundVexTestSupport.everyTick(helper, () -> {
            if (vex.distanceToSqr(husk) <= STALL_DISTANCE_SQR
                    && vex.getDeltaMovement().lengthSqr() < AT_REST_SPEED_SQR
                    && husk.getHealth() >= husk.getMaxHealth()) {
                vex.setPos(start);
                vex.setDeltaMovement(Vec3.ZERO);
            }
        });

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "a plain vex hurts it"))
                .thenSucceed();
    }

    /**
     * When a plain vex (the control) holds a bow and a husk stands 5 blocks away, should it shoot an arrow or hurt the
     * husk.
     *
     * <p>Method under test: {@code RangedGearGoal.canUse}: a plain vex still shoots
     */
    static void plainVexShoots(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        Husk husk = TeamMoveTestSupport.foe(helper, EntityTypes.HUSK, hang);
        BoundVex vex = BoundVexTestSupport.noTargets(
                BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, player));
        BoundVexTestSupport.pauseFollowing(vex);
        vex.setPos(hang.add(5.0, 0.0, 0.0));
        vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        vex.setTarget(husk);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        husk.getHealth() < husk.getMaxHealth()
                                || !helper.getLevel()
                                        .getEntitiesOfClass(
                                                AbstractArrow.class,
                                                vex.getBoundingBox().inflate(20.0))
                                        .isEmpty(),
                        "an arrow flies or the husk is hurt"))
                .thenSucceed();
    }

    /**
     * When three plain vexes and a Caster are near their summoner, should the Caster not be free for an ordinary move
     * and a Shield take only the three.
     *
     * <p>Method under test: {@code TeamMoves.isFree} and {@code freeVexes} without the Raid flag
     */
    static void notFreeForOrdinaryMoves(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        TeamMoveTestSupport.frozenVexes(helper, player, 3);
        BoundVex caster = casterVex(helper, player, hang.add(0.0, 0.0, 2.0));
        Husk husk = TeamMoveTestSupport.bigHusk(helper, TeamMoveTestSupport.beside(helper));

        // Act
        boolean free = TeamMoves.isFree(caster, player);
        int count = TeamMoves.freeVexes(player).size();
        boolean started = TeamMoves.start(player, TeamMove.SHIELD, husk);

        // Assert
        helper.assertFalse(free, "a Caster is not free");
        helper.assertValueEqual(count, 3, "three free vexes");
        helper.assertTrue(started, "the Shield starts");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 3, "with the three");
        helper.assertFalse(TeamMoves.roster(player).contains(caster), "not the Caster");
        helper.succeed();
    }

    /**
     * When a Caster stands near its summoner, should it be free for a Raid and counted among the free vexes.
     *
     * <p>Method under test: {@code TeamMoves.isFree(BoundVex, ServerPlayer, boolean)} and {@code freeVexes}
     */
    static void freeForARaid(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        TeamMoveTestSupport.frozenVexes(helper, player, 3);
        BoundVex caster = casterVex(helper, player, hang.add(0.0, 0.0, 2.0));

        // Act
        boolean free = TeamMoves.isFree(caster, player, true);
        int count = TeamMoves.freeVexes(player, true).size();

        // Assert
        helper.assertTrue(free, "free for a Raid");
        helper.assertValueEqual(count, 4, "four free vexes for a Raid");
        helper.succeed();
    }

    /**
     * When eleven plain vexes and one Caster are free beside a ravager, should the Caster make the twelfth of a Raid,
     * holding the Mirror scroll; the control is that eleven alone are below a Raid.
     *
     * <p>Method under test: {@code TeamMoveTriggers.scan}: the Raid count includes Casters
     */
    static void countsTowardARaidTrigger(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Vec3 hang = TeamMoveTestSupport.hangPoint(helper);
        TeamMoveTestSupport.frozenVexes(helper, player, 11);
        TeamMoveTestSupport.foe(helper, EntityTypes.RAVAGER, TeamMoveTestSupport.beside(helper));
        helper.assertValueEqual(TeamMoves.freeVexes(player, true).size(), 11, "control: eleven free without a Caster");
        helper.assertValueEqual(
                TeamMoveRules.rosterSize(TeamMove.RAID, 11), 0, "control: eleven are too few for a Raid");
        BoundVex caster = casterVex(helper, player, hang.add(0.0, 0.0, 2.0));

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertValueEqual(TeamMoves.running(player), Optional.of(TeamMove.RAID), "a Raid starts");
        helper.assertValueEqual(TeamMoves.roster(player).size(), 12, "with all twelve");
        helper.assertTrue(TeamMoves.roster(player).contains(caster), "the Caster is in it");
        helper.assertTrue(
                caster.getMainHandItem().is(ModItems.scrollFor(Spells.MIRROR).getItem()),
                "the Caster holds the Mirror scroll");
        helper.succeed();
    }

    /**
     * When a Raid starts with every kit roll scripted to 0, should the Caster hold the Mirror scroll, a shield and
     * netherite head, chest and legs, and an ordinary member carry Raid gear.
     *
     * <p>Method under test: {@code TeamMoveKits.kit}: the Caster's Raid kit
     */
    static void raidKit(GameTestHelper helper) {
        // Arrange
        RaidScene scene = raidScene(helper);

        // Act
        boolean started;
        try {
            // 11 ordinary members take 2 rolls each and the Caster takes 1: all zero is a sword and netherite.
            Dice.RAID_KIT.script(new int[23]);
            started = TeamMoves.start(scene.player(), TeamMove.RAID, scene.husk());
        } finally {
            Dice.unscriptAll();
        }

        // Assert
        BoundVex caster = scene.caster();
        helper.assertTrue(started, "the Raid starts");
        helper.assertTrue(
                caster.getMainHandItem().is(ModItems.scrollFor(Spells.MIRROR).getItem()), "the Mirror scroll");
        helper.assertTrue(caster.getOffhandItem().is(Items.SHIELD), "a shield");
        helper.assertTrue(caster.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET), "netherite helmet");
        helper.assertTrue(
                caster.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE), "netherite chestplate");
        helper.assertTrue(caster.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS), "netherite leggings");
        BoundVex member = TeamMoves.roster(scene.player()).stream()
                .filter(vex -> !vex.equals(caster))
                .findFirst()
                .orElseThrow();
        TeamMoveTestSupport.assertRaidGear(helper, member);
        helper.succeed();
    }
}
