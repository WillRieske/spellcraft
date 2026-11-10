package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.bigHusk;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.flyingVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.foe;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.hangPoint;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.setBase;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.Dice;
import io.github.willrieske.spellcraft.evoking.RaidMove;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** GameTests of the Raid team move (Parts 6-7 spec §4.4). */
final class BoundVexTeamMoveRaidGameTests {
    private BoundVexTeamMoveRaidGameTests() {}

    /** Registers the tests. */
    static void registerAll() {
        FangGameTests.addPadded(
                SHARED,
                "team_move_raid_members_reach_their_grid_cells",
                300,
                BoundVexTeamMoveRaidGameTests::membersReachTheirCells);
        FangGameTests.addPadded(
                SHARED,
                "team_move_raid_melee_members_hit_the_enemy",
                300,
                BoundVexTeamMoveRaidGameTests::meleeMembersHitTheEnemy);
        FangGameTests.addPadded(SHARED, "team_move_raid_grid_geometry", BoundVexTeamMoveRaidGameTests::gridGeometry);
        FangGameTests.addPadded(
                "team_move_dice",
                "team_move_raid_dice_kits_and_rocket_blast",
                400,
                BoundVexTeamMoveRaidGameTests::diceKitsAndRocketBlast);
        FangGameTests.addPadded(
                "team_move_dice",
                "team_move_raid_rocketeer_fires_past_a_fellow_member",
                400,
                BoundVexTeamMoveRaidGameTests::rocketeerFiresPastAFellowMember);
        FangGameTests.addPadded(
                "team_move_dice",
                "team_move_raid_melee_hits_every_twenty_ticks",
                400,
                BoundVexTeamMoveRaidGameTests::meleeHitsEveryTwentyTicks);
        FangGameTests.addPadded(
                "team_move_dice",
                "team_move_raid_charge_starts_at_forty",
                400,
                BoundVexTeamMoveRaidGameTests::chargeStartsAtForty);
    }

    /** The parts of a running Raid scene: the summoner, the twelve flying members and the ravager. */
    private record Scene(ServerPlayer player, List<BoundVex> vexes, Ravager ravager) {}

    /**
     * A Raid against a ravager four blocks north of the hang point, with twelve flying vexes south of it, stepped
     * every tick.
     */
    private static Scene scene(GameTestHelper helper) {
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Ravager ravager = foe(helper, EntityTypes.RAVAGER, hang.add(0.0, 0.0, -4.0));
        List<BoundVex> vexes = flyingVexes(helper, player, 12, hang.add(0.0, 0.0, 3.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.RAID, ravager), "setup: the Raid starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        return new Scene(player, vexes, ravager);
    }

    /**
     * When a Raid gathers, should every member reach its own grid cell (within 1.3 blocks) at some tick of the
     * 40-tick gather.
     *
     * <p>Method under test: {@code RaidMove.tick}: the gather
     */
    static void membersReachTheirCells(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<BoundVex> roster = TeamMoves.roster(scene.player());
                    for (int i = 0; i < roster.size(); i++) {
                        Vec3 cell = RaidMove.cell(
                                scene.ravager().getEyePosition(), scene.player().position(), i);
                        helper.assertTrue(
                                roster.get(i).position().distanceTo(cell) <= 1.3,
                                "member " + i + " is at its cell, not "
                                        + roster.get(i).position().distanceTo(cell) + " off");
                    }
                })
                .thenSucceed();
    }

    /**
     * When the Raid has charged, should the enemy lose health to the melee members within 150 ticks.
     *
     * <p>Method under test: {@code RaidMove.tick}: the charge
     */
    static void meleeMembersHitTheEnemy(GameTestHelper helper) {
        // Arrange
        Scene scene = scene(helper);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        scene.ravager().getHealth() < scene.ravager().getMaxHealth(), "the charge hurts the ravager"))
                .thenSucceed();
    }

    /**
     * When the Raid's dice are scripted, should each member get the scripted weapon and armor material in roster
     * order, and the rocket crossbow of member 0 fire a rocket that explodes and hurts the ravager but not the
     * summoner standing three blocks from it. The scene hangs above the barrier shell and the mock player is moved up
     * beside it: the shell blocks line of sight and explosion clips, so a scene inside it would never fire a rocket,
     * and a blast would be shielded by the barrier, not by the Part 3 backstop. Members 1 to 11 are frozen out of the
     * rocketeer's line, so the rocket is the only possible source of damage.
     *
     * <p>Method under test: {@code TeamMoveKits.raid}, {@code RaidMove.tick}, {@code VexShots.rocket}
     */
    static void diceKitsAndRocketBlast(GameTestHelper helper) {
        // Arrange: member k rolls (weapon, material); weapon 6 is the rocket crossbow, material 0 netherite, 1 diamond
        int[] weapons = {6, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4};
        Item[] items = {
            Items.NETHERITE_SWORD,
            Items.DIAMOND_SWORD,
            Items.NETHERITE_AXE,
            Items.DIAMOND_AXE,
            Items.MACE,
            Items.TRIDENT,
            Items.CROSSBOW
        };
        int[] script = new int[24];
        for (int k = 0; k < 12; k++) {
            script[2 * k] = weapons[k];
            script[2 * k + 1] = k % 2;
        }
        Dice.RAID_KIT.script(script);
        GameTestSupport.whenDone(helper, Dice.RAID_KIT::unscript);
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Ravager ravager = foe(helper, EntityTypes.RAVAGER, hang.add(0.0, 0.0, -4.0));
        // The summoner hangs three blocks from the ravager, above the barrier shell (a mock player is never ticked,
        // so it does not fall).
        MockPlayers.moveTo(
                helper,
                player,
                Vec3.atBottomCenterOf(BoundVexTestSupport.AIR_SPOT).add(0.0, 0.0, -1.0),
                NORTH);
        flyingVexes(helper, player, 12, hang.add(0.0, 0.0, 3.0));

        // Act
        helper.assertTrue(TeamMoves.start(player, TeamMove.RAID, ravager), "setup: the Raid starts");
        Dice.RAID_KIT.unscript();
        TeamMoveTestSupport.stepEveryTick(helper, player);

        // Assert: the kits
        List<BoundVex> roster = TeamMoves.roster(player);
        for (int k = 0; k < 12; k++) {
            BoundVex vex = roster.get(k);
            helper.assertTrue(
                    vex.getMainHandItem().is(items[weapons[k]]), "member " + k + " holds " + items[weapons[k]]);
            helper.assertTrue(
                    vex.getItemBySlot(EquipmentSlot.HEAD)
                            .is(k % 2 == 0 ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET),
                    "member " + k + " wears the scripted material");
        }
        BoundVex rocketeer = roster.get(0);
        for (int k = 1; k < roster.size(); k++) {
            // they keep their kit and stay in the roster; only the rocketeer acts, and none stands near its line
            roster.get(k).setNoAi(true);
            roster.get(k).setPos(roster.get(k).position().add(0.0, 6.0, 0.0));
        }

        // Assert: the rocket
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertFalse(rockets(helper, rocketeer).isEmpty(), "member 0 fires a rocket"))
                .thenExecute(() -> helper.assertTrue(
                        rocketeer.distanceTo(ravager) >= 6.5 && rocketeer.distanceTo(ravager) <= 9.5,
                        "a rocket member keeps about 7 to 9 blocks, not " + rocketeer.distanceTo(ravager)))
                .thenWaitUntil(
                        () -> helper.assertTrue(rockets(helper, rocketeer).isEmpty(), "and it explodes"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            ravager.getHealth() < ravager.getMaxHealth(), "the ravager was hurt by the rocket");
                    helper.assertValueEqual(
                            player.getHealth(),
                            player.getMaxHealth(),
                            "the summoner, three blocks from the blast, was not");
                })
                .thenSucceed();
    }

    /**
     * When a fellow member of the same Raid hangs on the line between the rocketeer and the ravager, should the
     * rocketeer still fire (a fellow member is not a friend in its way); the summoner stands well off the line.
     *
     * <p>Method under test: {@code RaidMove.tick}: {@code VexShots.friendInTheWay} with the roster ignored
     */
    static void rocketeerFiresPastAFellowMember(GameTestHelper helper) {
        // Arrange (member 0 is the rocket crossbow, the rest swords; everything above the barrier shell)
        int[] script = new int[24];
        script[0] = 6;
        Dice.RAID_KIT.script(script);
        GameTestSupport.whenDone(helper, Dice.RAID_KIT::unscript);
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Ravager ravager = foe(helper, EntityTypes.RAVAGER, hang.add(0.0, 0.0, -4.0));
        MockPlayers.moveTo(helper, player, FangGameTests.STAND, NORTH);
        flyingVexes(helper, player, 12, hang.add(0.0, 0.0, 3.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.RAID, ravager), "setup: the Raid starts");
        Dice.RAID_KIT.unscript();
        TeamMoveTestSupport.stepEveryTick(helper, player);
        List<BoundVex> roster = TeamMoves.roster(player);
        BoundVex rocketeer = roster.get(0);
        BoundVex fellow = roster.get(1);
        fellow.setNoAi(true);
        for (int k = 2; k < roster.size(); k++) {
            roster.get(k).setNoAi(true);
            roster.get(k).setPos(roster.get(k).position().add(0.0, 6.0, 0.0));
        }
        // The fellow member is held on the middle of the line from the rocketeer's eyes to the ravager, every tick
        // (the rocketeer is still flying to its cell during the gather)
        BoundVexTestSupport.everyTick(
                helper,
                () -> fellow.setPos(rocketeer
                        .getEyePosition()
                        .add(ravager.getBoundingBox().getCenter())
                        .scale(0.5)));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(
                        rockets(helper, rocketeer).isEmpty(), "member 0 fires a rocket past its fellow member"))
                .thenSucceed();
    }

    /** The game times of each drop in the husk's health, and the game time the Raid started. */
    private record MeleeRun(long started, List<Long> drops, Husk enemy) {}

    /**
     * A Raid whose twelve members all hold the scripted netherite sword; members 1 to 11 are frozen, so member 0
     * alone fights a 1000-health husk. Every tick the husk's health is compared with the last tick's and each drop is
     * recorded.
     */
    private static MeleeRun meleeRun(GameTestHelper helper) {
        Dice.RAID_KIT.script(new int[24]);
        GameTestSupport.whenDone(helper, Dice.RAID_KIT::unscript);
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -4.0));
        setBase(husk, Attributes.MAX_HEALTH, 1000.0);
        husk.setHealth(1000.0F);
        flyingVexes(helper, player, 12, hang.add(0.0, 0.0, 3.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.RAID, husk), "setup: the Raid starts");
        Dice.RAID_KIT.unscript();
        List<BoundVex> roster = TeamMoves.roster(player);
        for (int k = 1; k < roster.size(); k++) {
            roster.get(k).setNoAi(true);
        }
        TeamMoveTestSupport.stepEveryTick(helper, player);
        List<Long> drops = new ArrayList<>();
        float[] last = {husk.getHealth()};
        BoundVexTestSupport.everyTick(helper, () -> {
            if (husk.getHealth() < last[0]) {
                drops.add(helper.getLevel().getGameTime());
            }
            last[0] = husk.getHealth();
        });
        return new MeleeRun(helper.getLevel().getGameTime(), drops, husk);
    }

    /**
     * When one melee member is pressed against the enemy, should consecutive hits be 20 ticks apart (plus or minus
     * one): without the attack clock a member pressed against the enemy would hit every tick.
     *
     * <p>Method under test: {@code RaidMove.tick}: the 20-tick attack clock
     */
    static void meleeHitsEveryTwentyTicks(GameTestHelper helper) {
        // Arrange
        MeleeRun run = meleeRun(helper);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(run.drops().size() >= 3, "three hits have landed"))
                .thenExecute(() -> {
                    for (int i = 1; i < 3; i++) {
                        long gap = run.drops().get(i) - run.drops().get(i - 1);
                        helper.assertTrue(gap >= 19 && gap <= 21, "hits " + gap + " ticks apart");
                    }
                })
                .thenSucceed();
    }

    /**
     * When a Raid starts, should no member hit the enemy before the 40-tick gather is over: the first hit comes at or
     * after the 40th tick.
     *
     * <p>Method under test: {@code RaidMove.advance}: the gather lasts {@code GATHER_TICKS}
     */
    static void chargeStartsAtForty(GameTestHelper helper) {
        // Arrange
        MeleeRun run = meleeRun(helper);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(run.drops().isEmpty(), "the first hit lands"))
                .thenExecute(() -> helper.assertTrue(
                        run.drops().get(0) - run.started() >= 40,
                        "the first hit came " + (run.drops().get(0) - run.started()) + " ticks after the start"))
                .thenSucceed();
    }

    /**
     * When the grid is computed for an enemy at eye height 10 and a summoner due south and due east, 3 rows by 4
     * columns should lie 1.5 apart, centered 8 blocks from the enemy toward the summoner at the enemy's eye height,
     * row 0 nearest the enemy and column 0 the right-hand one as the grid faces the enemy; a summoner on top of the
     * enemy falls back to +z with no NaN.
     *
     * <p>Method under test: {@link RaidMove#cell}: the geometry the spec fixes (§4.4), independent of the code under
     * test
     */
    static void gridGeometry(GameTestHelper helper) {
        // Arrange
        Vec3 eye = new Vec3(0.0, 10.0, 0.0);

        // Act & Assert
        for (Vec3 summoner : List.of(new Vec3(0.0, 0.0, 20.0), new Vec3(20.0, 0.0, 0.0))) {
            Vec3 toward = new Vec3(summoner.x, 0.0, summoner.z).normalize();
            Vec3 sum = Vec3.ZERO;
            for (int cell = 0; cell < 12; cell++) {
                Vec3 at = RaidMove.cell(eye, summoner, cell);
                double along = at.subtract(eye).dot(toward);
                helper.assertTrue(Math.abs(at.y - 10.0) < 1.0E-9, "cell " + cell + " is at the enemy's eye height");
                helper.assertTrue(
                        Math.abs(along - (8.0 + (cell / 4 - 1) * 1.5)) < 1.0E-9,
                        "cell " + cell + " is in its row: row 0 at 6.5, row 2 at 9.5, not " + along);
                if (cell % 4 != 3) {
                    helper.assertTrue(
                            Math.abs(at.distanceTo(RaidMove.cell(eye, summoner, cell + 1)) - 1.5) < 1.0E-9,
                            "neighbors in a row are 1.5 apart");
                }
                sum = sum.add(at);
            }
            Vec3 mean = sum.scale(1.0 / 12.0);
            helper.assertTrue(
                    mean.distanceTo(eye.add(toward.scale(8.0))) < 1.0E-9,
                    "the grid is centered 8 blocks toward the summoner");
        }
        helper.assertTrue(
                RaidMove.cell(eye, new Vec3(0.0, 0.0, 20.0), 0).x > 0.0, "column 0 is the right-hand one facing north");
        helper.assertTrue(RaidMove.cell(eye, new Vec3(20.0, 0.0, 0.0), 0).z < 0.0, "and facing west");
        Vec3 coincident = RaidMove.cell(eye, eye, 0);
        helper.assertFalse(
                Double.isNaN(coincident.x) || Double.isNaN(coincident.z), "no NaN when the summoner is on the enemy");
        helper.assertTrue(coincident.z > 0.0, "and the grid falls back to +z");
        helper.succeed();
    }

    /** The rockets of {@code vex} still in flight. */
    private static List<FireworkRocketEntity> rockets(GameTestHelper helper, BoundVex vex) {
        return helper.getLevel()
                .getEntitiesOfClass(
                        FireworkRocketEntity.class,
                        new AABB(vex.position(), vex.position()).inflate(30.0),
                        rocket -> vex.equals(rocket.getOwner()));
    }
}
