package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.at;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.casterVex;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.floor;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.hostileAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.images;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.raidScene;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.RaidMove;
import io.github.willrieske.spellcraft.evoking.TeamFlight;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.gametest.ScrollTestSupport.RaidScene;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.CasterRules;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.EnumSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** GameTests of a Caster's casting and of a Caster in a Raid (Parts 6-7 spec §6.2). */
final class CasterSpellGameTests {
    /** Ticks the Raid tests wait for the Raid to settle after the gather. */
    private static final int SETTLE_TICKS = 150;

    /** The Raid Caster must stay at least this many blocks from the enemy's box: it never closes in to hit. */
    private static final double MIN_BOX_GAP = 1.0;

    /** How near its grid cell a Caster must be after settling, in blocks. */
    private static final double CELL_TOLERANCE = 2.0;

    /** Ticks, after the Raid has settled, over which the Caster must keep its cell. */
    private static final int HOLD_TICKS = 40;

    /** A summoner's health low enough (under a quarter of 20) to need Ghostrail. */
    private static final float HURT_HEALTH = 4.0F;

    /** The most ticks a Raid Caster may take to cast Mirror: it casts at once, with no 20-tick wind-up. */
    private static final int RAID_CAST_TICKS = 5;

    /** Blocks of floor around the standing spot: wide enough that wandering images do not walk off and fall. */
    private static final int FLOOR_RADIUS = 8;

    private CasterSpellGameTests() {}

    /** Registers every Caster casting and Raid GameTest. */
    static void registerAll() {
        FangGameTests.addPadded(SHARED, "caster_casts_mirror", 200, CasterSpellGameTests::castsMirror);
        FangGameTests.addPadded(SHARED, "caster_wind_up_look", 200, CasterSpellGameTests::windUpLook);
        FangGameTests.addPadded(SHARED, "caster_gives_the_item_back", 200, CasterSpellGameTests::givesTheItemBack);
        FangGameTests.addPadded(
                SHARED, "caster_cooldown_blocks_a_second", 200, CasterSpellGameTests::cooldownBlocksASecond);
        FangGameTests.addPadded(
                SHARED, "caster_one_hostile_is_not_enough", 200, CasterSpellGameTests::oneHostileIsNotEnough);
        FangGameTests.addPadded(
                SHARED, "caster_ghostrail_on_the_hurt_summoner", 200, CasterSpellGameTests::ghostrailOnTheHurtSummoner);
        FangGameTests.addPadded(
                SHARED, "caster_ghostrail_on_a_hurt_vex", 200, CasterSpellGameTests::ghostrailOnAHurtVex);
        FangGameTests.addPadded(SHARED, "caster_ghostrail_in_a_wall", 200, CasterSpellGameTests::ghostrailInAWall);
        FangGameTests.addPadded(
                SHARED,
                "caster_no_ghostrail_for_a_creative_summoner",
                200,
                CasterSpellGameTests::noGhostrailForCreative);
        FangGameTests.addPadded(
                SHARED, "caster_interruption_spends_nothing", 200, CasterSpellGameTests::interruptionSpendsNothing);
        FangGameTests.addPadded(SHARED, "caster_two_casters_one_cast", 200, CasterSpellGameTests::twoCastersOneCast);
        FangGameTests.addPadded(
                SHARED, "caster_ghostrail_before_mirror", 200, CasterSpellGameTests::ghostrailBeforeMirror);
        FangGameTests.addPadded(
                SHARED, "caster_ghostrail_cooldown_is_4800", 200, CasterSpellGameTests::ghostrailCooldownIs4800);
        FangGameTests.addPadded(SHARED, "caster_ghostrail_range_is_16", 200, CasterSpellGameTests::ghostrailRangeIs16);
        FangGameTests.addPadded(
                SHARED, "caster_no_mirror_when_summoner_has_it", 200, CasterSpellGameTests::noMirrorWhenSummonerHasIt);
        FangGameTests.addPadded(
                SHARED, "caster_raid_dismisses_a_wind_up", 300, CasterSpellGameTests::raidDismissesAWindUp);
        FangGameTests.addPadded(
                SHARED, "caster_raid_on_the_last_wind_up_tick", 300, CasterSpellGameTests::raidOnTheLastWindUpTick);
        FangGameTests.addPadded(
                SHARED,
                "caster_discarded_on_the_last_wind_up_tick",
                300,
                CasterSpellGameTests::discardedOnTheLastWindUpTick);
        FangGameTests.addPadded(SHARED, "caster_raid_casts_mirror", 300, CasterSpellGameTests::raidCastsMirror);
        FangGameTests.addPadded(SHARED, "caster_raid_skips_the_cast", 300, CasterSpellGameTests::raidSkipsTheCast);
        FangGameTests.addPadded(SHARED, "caster_raid_holds_its_cell", 300, CasterSpellGameTests::raidHoldsItsCell);
        FangGameTests.addPadded(
                SHARED, "caster_raid_caster_never_attacks", 300, CasterSpellGameTests::raidCasterNeverAttacks);
    }

    /** A summoner, a parked Caster 3 blocks above them holding a golden sword, and {@code hostiles} husks beside it. */
    private static Scene scene(GameTestHelper helper, int hostiles) {
        ServerPlayer player = plainPlayer(helper);
        floor(helper, FLOOR_RADIUS);
        // 3 blocks above the standing spot: clearly inside GuardPosture.WIDE_LIMIT (4.0), and above the barrier shell
        Vec3 perch = at(helper, 0.0, 3.0, 0.0);
        BoundVex caster = casterVex(helper, player, perch);
        caster.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        for (int i = 0; i < hostiles; i++) {
            hostileAt(helper, perch.add(i == 0 ? 2.0 : -2.0, 0.0, 0.0));
        }
        return new Scene(player, caster);
    }

    /** The cast scene's summoner and Caster. */
    private record Scene(ServerPlayer player, BoundVex caster) {}

    /**
     * Starts a Raid and steps it every tick. The kit rolls are left random: no Caster Raid test depends on the
     * members' swords or armor (the Caster's own kit is the Mirror scroll), so scripting them would assert nothing.
     */
    private static RaidScene startedRaid(GameTestHelper helper, boolean mirrorFirst) {
        RaidScene scene = raidScene(helper);
        startRaid(helper, scene, mirrorFirst);
        return scene;
    }

    /** Starts the Raid of {@code scene} as {@link #startedRaid} does, and steps it every tick. */
    private static void startRaid(GameTestHelper helper, RaidScene scene, boolean mirrorFirst) {
        if (mirrorFirst) {
            Spells.cast(scene.player(), Spells.MIRROR, MirrorRule.DURATION_TICKS);
        }
        boolean started = TeamMoves.start(scene.player(), TeamMove.RAID, scene.husk());
        helper.assertTrue(started, "setup: the Raid starts");
        TeamMoveTestSupport.stepEveryTick(helper, scene.player());
    }

    /**
     * When two hostiles stand in a Caster's guard area with the summoner near, should the Caster cast Mirror after the
     * 20-tick wind-up: the summoner gets the effect and 13 images, the Caster's cooldown starts and the scroll is gone.
     *
     * <p>Method under test: {@code CasterSpellGoal}: Mirror
     */
    static void castsMirror(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 2);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(scene.player().hasEffect(ModEffects.MIRROR), "the summoner has Mirror"))
                .thenExecute(() -> {
                    helper.assertValueEqual(images(scene.player()).size(), MirrorRule.IMAGES, "13 images");
                    helper.assertTrue(
                            scene.caster().mirrorReadyAt() > helper.getLevel().getGameTime(), "the cooldown started");
                    helper.assertFalse(scene.caster().isConjured(EquipmentSlot.MAINHAND), "the scroll is dismissed");
                })
                .thenSucceed();
    }

    /**
     * When a Caster winds up, should it hold the Mirror scroll in its main hand and carry the casting effect, and have
     * neither once the spell lands.
     *
     * <p>Method under test: {@code CasterSpellGoal}: the wind-up's look
     */
    static void windUpLook(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 2);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        scene.caster().getMainHandItem().is(ModItems.MIRROR_SCROLL.get()), "the scroll is in hand"))
                .thenExecute(() -> helper.assertTrue(
                        scene.caster().hasEffect(ModEffects.CASTING_SCROLL), "the casting effect is on"))
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.MIRROR), "the spell lands"))
                .thenExecute(() -> helper.assertFalse(
                        scene.caster().hasEffect(ModEffects.CASTING_SCROLL), "the casting effect is gone"))
                .thenSucceed();
    }

    /**
     * When a Caster's cast has landed, should its main hand be the golden sword it held before and the slot no longer
     * conjured.
     *
     * <p>Method under test: {@code CasterSpellGoal}: the stash returns
     */
    static void givesTheItemBack(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 2);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.MIRROR), "the spell lands"))
                .thenExecute(() -> {
                    helper.assertTrue(scene.caster().getMainHandItem().is(Items.GOLDEN_SWORD), "the sword is back");
                    helper.assertFalse(scene.caster().isConjured(EquipmentSlot.MAINHAND), "the slot is not conjured");
                })
                .thenSucceed();
    }

    /**
     * When the summoner is hurt and two hostiles stand in the guard area, should the Caster cast Ghostrail first and
     * Mirror not yet: Ghostrail is checked before Mirror.
     *
     * <p>Method under test: {@code CasterSpellGoal.choose}: Ghostrail before Mirror
     */
    static void ghostrailBeforeMirror(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 2);
        scene.player().setHealth(HURT_HEALTH);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.GHOSTRAIL), "Ghostrail"))
                .thenExecute(() -> {
                    helper.assertFalse(scene.player().hasEffect(ModEffects.MIRROR), "no Mirror yet");
                    helper.assertValueEqual(scene.caster().mirrorReadyAt(), 0L, "no Mirror cooldown spent");
                })
                .thenSucceed();
    }

    /**
     * When a Caster casts Ghostrail, should its saved ready time be the game time of the cast plus 4800.
     *
     * <p>Method under test: {@code CasterSpellGoal.finish}: the Ghostrail cooldown
     */
    static void ghostrailCooldownIs4800(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 0);
        scene.player().setHealth(HURT_HEALTH);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.GHOSTRAIL), "Ghostrail"))
                .thenExecute(() -> {
                    long now = helper.getLevel().getGameTime();
                    long castAt = scene.caster().ghostrailReadyAt() - 4800L;
                    helper.assertTrue(castAt <= now && castAt >= now - 5L, "the ready time is the cast plus 4800");
                })
                .thenSucceed();
    }

    /**
     * When the hurt summoner is 17 blocks from the Caster, should the Caster cast nothing: its range is 16.
     *
     * <p>Method under test: {@code CasterSpellGoal.emergency}: the 16-block range
     */
    static void ghostrailRangeIs16(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, FLOOR_RADIUS);
        BoundVex caster = casterVex(helper, player, at(helper, -9.0, 3.0, 0.0));
        MockPlayers.moveTo(helper, player, FangGameTests.STAND.add(8.0, 0.0, 0.0));
        player.setHealth(HURT_HEALTH);
        helper.assertTrue(player.distanceTo(caster) > 16.0, "setup: the summoner is out of range");

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(80, () -> {
                    helper.assertFalse(player.hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail out of range");
                    helper.assertValueEqual(caster.ghostrailReadyAt(), 0L, "no cooldown spent");
                })
                .thenSucceed();
    }

    /**
     * When the summoner already has Mirror and two hostiles stand in the guard area, should the Caster not cast.
     *
     * <p>Method under test: {@code CasterSpellGoal.choose}: Mirror needs a summoner without it
     */
    static void noMirrorWhenSummonerHasIt(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 2);
        Spells.cast(scene.player(), Spells.MIRROR, MirrorRule.DURATION_TICKS);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(80, () -> {
                    helper.assertValueEqual(scene.caster().mirrorReadyAt(), 0L, "no cooldown spent");
                    helper.assertFalse(scene.caster().isConjured(EquipmentSlot.MAINHAND), "no scroll was conjured");
                    helper.assertValueEqual(images(scene.player()).size(), MirrorRule.IMAGES, "no second set");
                })
                .thenSucceed();
    }

    /**
     * When a Raid starts while a Caster winds up Ghostrail, should the scroll be dismissed, no cooldown spent, no
     * Ghostrail landed, and the Raid kit (a Mirror scroll) be in hand instead.
     *
     * <p>Method under test: {@code CasterSpellGoal.stop}: a Raid preempts the wind-up
     */
    static void raidDismissesAWindUp(GameTestHelper helper) {
        // Arrange
        RaidScene scene = raidScene(helper);
        scene.player().setHealth(HURT_HEALTH);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        ItemStack.isSameItem(scene.caster().getMainHandItem(), ModItems.scrollFor(Spells.GHOSTRAIL)),
                        "winding up Ghostrail"))
                .thenExecute(() -> startRaid(helper, scene, false))
                .thenExecuteAfter(5, () -> {
                    helper.assertFalse(scene.player().hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail landed");
                    helper.assertValueEqual(scene.caster().ghostrailReadyAt(), 0L, "no cooldown spent");
                    helper.assertFalse(scene.caster().hasEffect(ModEffects.CASTING_SCROLL), "the casting look is gone");
                    helper.assertTrue(
                            scene.caster().getMainHandItem().is(ModItems.MIRROR_SCROLL.get()),
                            "the Raid kit's Mirror scroll is in hand");
                })
                .thenSucceed();
    }

    /**
     * Starts the Raid of {@code scene} from inside a wait condition on the tick the Caster's wind-up has run 19 ticks,
     * so the Caster's 20th tick (the one that would land the spell) is the next one.
     */
    private static void startRaidOnTheLastWindUpTick(
            GameTestHelper helper, RaidScene scene, boolean[] startedOnTick19, Runnable atTick19) {
        helper.assertTrue(
                scene.caster().casterWindUpTicks() == CasterRules.WIND_UP_TICKS - 1
                        && scene.caster().isConjured(EquipmentSlot.MAINHAND),
                "waiting for wind-up tick 19");
        startedOnTick19[0] = true;
        atTick19.run();
    }

    /**
     * When a Raid starts on the Caster's wind-up tick 19, should the 20th tick not land the spell: vanilla ticks an
     * every-tick goal on in-between ticks without asking {@code canContinueToUse}, so the goal checks the team move
     * itself. No Ghostrail lands, no cooldown is spent, and the Raid kit's Mirror scroll is in the main hand.
     *
     * <p>Method under test: {@code CasterSpellGoal.tick}: a Raid preempts the last wind-up tick
     */
    static void raidOnTheLastWindUpTick(GameTestHelper helper) {
        // Arrange
        RaidScene scene = raidScene(helper);
        scene.player().setHealth(HURT_HEALTH);
        boolean[] started = new boolean[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        startRaidOnTheLastWindUpTick(helper, scene, started, () -> startRaid(helper, scene, false)))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(started[0], "the Raid started on wind-up tick 19");
                    helper.assertFalse(scene.player().hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail landed");
                    helper.assertValueEqual(scene.caster().ghostrailReadyAt(), 0L, "no cooldown spent");
                    helper.assertFalse(scene.caster().hasEffect(ModEffects.CASTING_SCROLL), "the casting look is gone");
                    helper.assertTrue(
                            scene.caster().getMainHandItem().is(ModItems.MIRROR_SCROLL.get()),
                            "the Raid kit's Mirror scroll is in hand");
                })
                .thenSucceed();
    }

    /**
     * When a Raid starts on the Caster's wind-up tick 19 and the Caster is discarded in the same tick, should no spell
     * land and nothing throw: the goal's next tick, if any, finds a removed vex and a started Raid.
     *
     * <p>Method under test: {@code CasterSpellGoal.tick}: a discarded Caster at the last wind-up tick
     */
    static void discardedOnTheLastWindUpTick(GameTestHelper helper) {
        // Arrange
        RaidScene scene = raidScene(helper);
        scene.player().setHealth(HURT_HEALTH);
        boolean[] started = new boolean[1];

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> startRaidOnTheLastWindUpTick(helper, scene, started, () -> {
                    startRaid(helper, scene, false);
                    scene.caster().discard();
                }))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(started[0], "the Raid started on wind-up tick 19");
                    helper.assertTrue(scene.caster().isRemoved(), "the Caster is gone");
                    helper.assertFalse(scene.player().hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail landed");
                    helper.assertValueEqual(scene.caster().ghostrailReadyAt(), 0L, "no cooldown spent");
                })
                .thenSucceed();
    }

    /**
     * When the summoner loses the Mirror right after a cast while the hostiles are still there, should the Caster not
     * cast again, its cooldown holding.
     *
     * <p>Method under test: {@code CasterSpellGoal}: the Mirror cooldown
     */
    static void cooldownBlocksASecond(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 2);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.MIRROR), "the first cast"))
                .thenExecute(() -> scene.player().removeEffect(ModEffects.MIRROR))
                .thenExecuteAfter(
                        60, () -> helper.assertFalse(scene.player().hasEffect(ModEffects.MIRROR), "no second Mirror"))
                .thenSucceed();
    }

    /**
     * When only one hostile stands in the guard area, should the Caster not cast Mirror.
     *
     * <p>Method under test: {@code CasterSpellGoal}: Mirror needs two hostiles
     */
    static void oneHostileIsNotEnough(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 1);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(
                        80, () -> helper.assertFalse(scene.player().hasEffect(ModEffects.MIRROR), "no Mirror"))
                .thenSucceed();
    }

    /**
     * When the summoner is below a quarter of its health, should the Caster cast Ghostrail on it.
     *
     * <p>Method under test: {@code CasterSpellGoal}: Ghostrail on the summoner
     */
    static void ghostrailOnTheHurtSummoner(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 0);
        scene.player().setHealth(HURT_HEALTH);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.GHOSTRAIL), "Ghostrail"))
                .thenSucceed();
    }

    /**
     * When a sibling vex is below a quarter of its health and the summoner is well, should the Caster cast Ghostrail
     * on the sibling and not on the summoner.
     *
     * <p>Method under test: {@code CasterSpellGoal}: Ghostrail on a sibling vex
     */
    static void ghostrailOnAHurtVex(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 0);
        BoundVex sibling = BoundVexTestSupport.noTargets(
                BoundVexTestSupport.boundVex(helper, BoundVexTestSupport.AIR_SPOT, scene.player()));
        sibling.setHealth(1.0F);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(sibling.hasEffect(ModEffects.GHOSTRAIL), "the sibling flies"))
                .thenExecute(() ->
                        helper.assertFalse(scene.player().hasEffect(ModEffects.GHOSTRAIL), "the summoner does not"))
                .thenSucceed();
    }

    /**
     * When the summoner's eyes are inside a block, should the Caster cast Ghostrail on it.
     *
     * <p>Method under test: {@code CasterSpellGoal}: Ghostrail for a summoner in a wall
     */
    static void ghostrailInAWall(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 0);
        helper.setBlock(FangGameTests.FEET.above(), Blocks.STONE);
        helper.assertTrue(scene.player().isInWall(), "setup: the summoner is in a wall");

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.GHOSTRAIL), "Ghostrail"))
                .thenSucceed();
    }

    /**
     * When the summoner is a hurt creative player, should the Caster cast no Ghostrail and keep its own item and
     * cooldown untouched; the control is {@code ghostrailOnTheHurtSummoner}, the same scene in survival.
     *
     * <p>Method under test: {@code CasterSpellGoal}: {@code Spells.refusedFor}
     */
    static void noGhostrailForCreative(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 0);
        scene.player().setGameMode(GameType.CREATIVE);
        scene.player().setHealth(HURT_HEALTH);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(80, () -> {
                    helper.assertFalse(scene.player().hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail");
                    helper.assertValueEqual(scene.caster().ghostrailReadyAt(), 0L, "no cooldown spent");
                    helper.assertTrue(scene.caster().getMainHandItem().is(Items.GOLDEN_SWORD), "the sword is held");
                })
                .thenSucceed();
    }

    /**
     * When a higher-priority goal takes the Caster's move flag mid wind-up, should the scroll be dismissed and nothing
     * spent: no cooldown and no spell.
     *
     * <p>Method under test: {@code CasterSpellGoal.stop}
     */
    static void interruptionSpendsNothing(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 2);
        Goal blocker = new Goal() {
            {
                setFlags(EnumSet.of(Goal.Flag.MOVE));
            }

            @Override
            public boolean canUse() {
                return true;
            }
        };

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        scene.caster().getMainHandItem().is(ModItems.MIRROR_SCROLL.get()), "winding up"))
                .thenExecute(() -> scene.caster().goalSelector.addGoal(0, blocker))
                .thenExecuteAfter(5, () -> {
                    helper.assertFalse(scene.caster().isConjured(EquipmentSlot.MAINHAND), "the scroll is dismissed");
                    helper.assertValueEqual(scene.caster().mirrorReadyAt(), 0L, "no cooldown spent");
                    helper.assertFalse(scene.player().hasEffect(ModEffects.MIRROR), "no spell");
                    scene.caster().goalSelector.removeGoal(blocker);
                })
                .thenSucceed();
    }

    /**
     * When two Casters stand beside the same summoner and two hostiles, should only one set of images appear and
     * exactly one Caster spend its Mirror cooldown.
     *
     * <p>Method under test: {@code CasterSpellGoal}: the subject must still qualify at the end of the wind-up
     */
    static void twoCastersOneCast(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper, 2);
        BoundVex second =
                casterVex(helper, scene.player(), scene.caster().position().add(0.0, 0.0, 1.0));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.MIRROR), "a cast"))
                .thenExecuteAfter(60, () -> {
                    helper.assertValueEqual(images(scene.player()).size(), MirrorRule.IMAGES, "one set of images");
                    int spent = (scene.caster().mirrorReadyAt() > 0 ? 1 : 0) + (second.mirrorReadyAt() > 0 ? 1 : 0);
                    helper.assertValueEqual(spent, 1, "exactly one Caster spent its cooldown");
                })
                .thenSucceed();
    }

    /**
     * When a Raid starts and the summoner has no Mirror, should the Caster cast it at once, with no wind-up, set its
     * Mirror cooldown, hold the Mirror scroll through the gather and empty its hand when the charge starts, the slot
     * still conjured.
     *
     * <p>Method under test: {@code RaidMove.tick}: the Caster's cast and kit
     */
    static void raidCastsMirror(GameTestHelper helper) {
        // Arrange
        long start = helper.getLevel().getGameTime();
        RaidScene scene = startedRaid(helper, false);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.player().hasEffect(ModEffects.MIRROR), "Mirror at once"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getLevel().getGameTime() - start <= RAID_CAST_TICKS, "no 20-tick wind-up");
                    helper.assertTrue(scene.caster().mirrorReadyAt() > 0, "the cooldown started");
                    helper.assertTrue(
                            scene.caster().getMainHandItem().is(ModItems.MIRROR_SCROLL.get()),
                            "the scroll is still held through the gather");
                })
                .thenWaitUntil(
                        () -> helper.assertTrue(scene.caster().getMainHandItem().isEmpty(), "hand emptied"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            helper.getLevel().getGameTime() - start >= RaidMove.GATHER_TICKS - 10,
                            "emptied only when the charge starts");
                    helper.assertTrue(scene.caster().isConjured(EquipmentSlot.MAINHAND), "the slot is still conjured");
                })
                .thenSucceed();
    }

    /**
     * When the summoner already has Mirror as a Raid starts, should the Caster skip the cast: it holds the scroll, its
     * cooldown stays 0 and no second set of images appears.
     *
     * <p>Method under test: {@code RaidMove.tick}: the Caster skips a cast the summoner does not need
     */
    static void raidSkipsTheCast(GameTestHelper helper) {
        // Arrange
        RaidScene scene = startedRaid(helper, true);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(
                            scene.caster().getMainHandItem().is(ModItems.MIRROR_SCROLL.get()), "the scroll is held");
                    helper.assertValueEqual(scene.caster().mirrorReadyAt(), 0L, "no cooldown spent");
                    helper.assertValueEqual(images(scene.player()).size(), MirrorRule.IMAGES, "no second set");
                })
                .thenSucceed();
    }

    /**
     * When a Raid has run well past its gather, should the Caster sit near its grid cell on every one of the next 40
     * ticks (it holds the cell without circling) with an empty main hand, the husk still alive so the move cannot have
     * ended.
     *
     * <p>Method under test: {@code RaidMove.tick}: the Caster holds its cell
     */
    static void raidHoldsItsCell(GameTestHelper helper) {
        // Arrange
        RaidScene scene = startedRaid(helper, false);

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(SETTLE_TICKS, () -> {
                    helper.assertTrue(scene.husk().isAlive(), "the husk lives, so the move is still running");
                    helper.assertTrue(scene.caster().getMainHandItem().isEmpty(), "its hand is empty");
                })
                .thenExecuteFor(HOLD_TICKS, () -> {
                    int cell = TeamMoves.roster(scene.player()).indexOf(scene.caster());
                    Vec3 spot = RaidMove.cell(
                            scene.husk().getEyePosition(), scene.player().position(), cell);
                    helper.assertTrue(
                            scene.caster().position().distanceTo(spot) <= CELL_TOLERANCE,
                            "the Caster holds its cell on every tick");
                })
                .thenSucceed();
    }

    /**
     * When a Raid charges, should the Caster never come within a block of the enemy's box while the other members
     * reach and hurt it.
     *
     * <p>Method under test: {@code RaidMove.tick}: a Caster never attacks
     */
    static void raidCasterNeverAttacks(GameTestHelper helper) {
        // Arrange
        RaidScene scene = startedRaid(helper, false);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        SETTLE_TICKS,
                        () -> helper.assertTrue(
                                TeamFlight.boxGap(scene.caster(), scene.husk()) >= MIN_BOX_GAP,
                                "the Caster stays outside the enemy's box"))
                .thenExecute(() -> helper.assertTrue(
                        scene.husk().getHealth() < scene.husk().getMaxHealth(), "the others did charge in"))
                .thenSucceed();
    }
}
