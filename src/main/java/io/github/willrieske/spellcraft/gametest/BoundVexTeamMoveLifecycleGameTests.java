package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.beside;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.bigHusk;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.flyingVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.foe;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.frozenVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.hangPoint;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BoundVexEvents;
import io.github.willrieske.spellcraft.evoking.Dice;
import io.github.willrieske.spellcraft.evoking.FleeCreeperGoal;
import io.github.willrieske.spellcraft.evoking.TeamMoveGoal;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.ShieldScene;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** GameTests of how a team move runs and ends (Parts 6-7 spec section 2.5). */
final class BoundVexTeamMoveLifecycleGameTests {
    private BoundVexTeamMoveLifecycleGameTests() {}

    /** Registers the tests. */
    static void registerAll() {
        FangGameTests.addPadded(SHARED, "team_move_end_enemy_dies", BoundVexTeamMoveLifecycleGameTests::enemyDies);
        FangGameTests.addPadded(
                SHARED, "team_move_end_enemy_removed", BoundVexTeamMoveLifecycleGameTests::enemyRemoved);
        FangGameTests.addPadded(
                SHARED, "team_move_end_enemy_beyond_48", BoundVexTeamMoveLifecycleGameTests::enemyBeyond48);
        FangGameTests.addPadded(SHARED, "team_move_end_time_limit", BoundVexTeamMoveLifecycleGameTests::timeLimit);
        FangGameTests.addPadded(
                SHARED, "team_move_end_evader_time_limit", BoundVexTeamMoveLifecycleGameTests::evaderTimeLimit);
        FangGameTests.addPadded(SHARED, "team_move_end_ward_healed", BoundVexTeamMoveLifecycleGameTests::wardHealed);
        FangGameTests.addPadded(SHARED, "team_move_end_ward_dies", BoundVexTeamMoveLifecycleGameTests::wardDies);
        FangGameTests.addPadded(SHARED, "team_move_end_roster_empty", BoundVexTeamMoveLifecycleGameTests::rosterEmpty);
        FangGameTests.addPadded(
                SHARED, "team_move_end_summoner_dies", BoundVexTeamMoveLifecycleGameTests::summonerDies);
        FangGameTests.addPadded(
                SHARED, "team_move_end_summoner_spectates", BoundVexTeamMoveLifecycleGameTests::summonerSpectates);
        FangGameTests.addPadded(
                SHARED,
                "team_move_end_summoner_changes_level",
                BoundVexTeamMoveLifecycleGameTests::summonerChangesLevel);
        FangGameTests.addPadded(
                SHARED, "team_move_end_cooldown_per_kind", BoundVexTeamMoveLifecycleGameTests::cooldownPerKind);
        FangGameTests.addPadded(
                SHARED,
                "team_move_end_cooldown_lasts_thirty_seconds",
                BoundVexTeamMoveLifecycleGameTests::cooldownLastsThirtySeconds);
        FangGameTests.addPadded(
                SHARED, "team_move_end_logout_releases_kits", BoundVexTeamMoveLifecycleGameTests::logoutReleasesKits);
        FangGameTests.addPadded(
                SHARED,
                "team_move_end_player_tick_event_steps_the_move",
                BoundVexTeamMoveLifecycleGameTests::playerTickEventStepsTheMove);
        FangGameTests.addPadded(
                SHARED,
                "team_move_end_dead_member_drops_no_kit",
                BoundVexTeamMoveLifecycleGameTests::deadMemberDropsNoKit);
        FangGameTests.addPadded(
                SHARED,
                "team_move_goal_runs_for_a_listed_member",
                BoundVexTeamMoveLifecycleGameTests::goalRunsForAListedMember);
        FangGameTests.addPadded(
                SHARED,
                "team_move_goal_stop_keeps_the_kit_while_listed",
                BoundVexTeamMoveLifecycleGameTests::goalStopKeepsTheKitWhileListed);
        FangGameTests.addPadded(
                SHARED,
                "team_move_goal_puts_a_replaced_item_back",
                BoundVexTeamMoveLifecycleGameTests::goalPutsAReplacedItemBack);
        FangGameTests.addPadded(
                SHARED,
                "team_move_goal_runs_in_the_selector",
                BoundVexTeamMoveLifecycleGameTests::goalRunsInTheSelector);
        FangGameTests.addPadded(
                SHARED,
                "team_move_end_member_marked_none_is_dropped",
                BoundVexTeamMoveLifecycleGameTests::memberMarkedNoneIsDropped);
        FangGameTests.addPadded(
                SHARED,
                "team_move_end_removed_member_is_dropped",
                BoundVexTeamMoveLifecycleGameTests::removedMemberIsDropped);
        FangGameTests.addPadded(
                SHARED,
                "team_move_end_multishot_continues_and_drops_no_kit",
                BoundVexTeamMoveLifecycleGameTests::multishotContinuesAndDropsNoKit);
        FangGameTests.addPadded(
                SHARED,
                "team_move_end_raid_continues_and_drops_no_kit",
                BoundVexTeamMoveLifecycleGameTests::raidContinuesAndDropsNoKit);
        FangGameTests.addPadded(
                SHARED,
                "team_move_goal_flee_keeps_the_kit_and_resumes",
                300,
                BoundVexTeamMoveLifecycleGameTests::fleeKeepsTheKitAndResumes);
        FangGameTests.addPadded(
                "team_move_server_stop",
                "team_move_end_server_stop_clears_records",
                BoundVexTeamMoveLifecycleGameTests::serverStopClearsRecords);
    }

    /** A running Shield: three frozen vexes and a 40-health husk beside them. */
    private static ShieldScene shield(GameTestHelper helper) {
        return TeamMoveTestSupport.startedShield(helper);
    }

    /** Fails unless the Shield is over: unmarked members with their swords back and the Shield cooling. */
    private static void assertOver(GameTestHelper helper, ShieldScene scene) {
        helper.assertTrue(TeamMoves.running(scene.player()).isEmpty(), "the move is over");
        for (BoundVex vex : scene.vexes()) {
            helper.assertValueEqual(vex.teamMove(), TeamMove.NONE, "unmarked");
            helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
            helper.assertFalse(vex.isConjured(EquipmentSlot.OFFHAND), "the shield is gone");
            helper.assertTrue(vex.getOffhandItem().isEmpty(), "the off hand is empty again");
        }
        helper.assertTrue(TeamMoves.cooling(scene.player(), TeamMove.SHIELD), "the Shield cools down");
    }

    /**
     * When the move enemy dies, should the next tick end the move: kits gone, given gear back, vexes unmarked, no
     * message, the Shield cooling.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the enemy-dead rule
     */
    static void enemyDies(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        GameTestSupport.drainActionBar(scene.player());
        scene.husk().kill(helper.getLevel());

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        assertOver(helper, scene);
        helper.assertTrue(GameTestSupport.drainActionBar(scene.player()).isEmpty(), "no message at the end");
        helper.succeed();
    }

    /**
     * When the move enemy is removed from the level, should the next tick end the move.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the enemy-removed rule
     */
    static void enemyRemoved(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        scene.husk().discard();

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        assertOver(helper, scene);
        helper.succeed();
    }

    /**
     * When the enemy is 47 blocks from the summoner, should the move go on; at 49 blocks (straight up, where the chunk
     * is loaded) the next tick should end it.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the 48-block rule
     */
    static void enemyBeyond48(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        Husk husk = scene.husk();
        Vec3 flat = husk.position();

        // Act & Assert
        husk.setPos(flat.x, scene.player().getY() + 47.0, flat.z);
        TeamMoves.tick(scene.player());
        helper.assertTrue(TeamMoves.running(scene.player()).isPresent(), "47 blocks off: still running");
        husk.setPos(flat.x, scene.player().getY() + 49.0, flat.z);
        TeamMoves.tick(scene.player());
        assertOver(helper, scene);
        helper.succeed();
    }

    /**
     * When 2399 ticks have passed, should the move still run; one more tick and it should end (the limit is 2400).
     *
     * <p>Method under test: {@link TeamMoves#tick}: the time limit
     */
    static void timeLimit(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);

        // Act & Assert
        TeamMoves.age(scene.player(), 2399);
        TeamMoves.tick(scene.player());
        helper.assertTrue(TeamMoves.running(scene.player()).isPresent(), "2399 ticks: still running");
        TeamMoves.age(scene.player(), 1);
        TeamMoves.tick(scene.player());
        assertOver(helper, scene);
        helper.succeed();
    }

    /**
     * When an Evader has run 1199 ticks, should it go on; one more and it should end (its limit is 1200) and the Evader
     * start cooling.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the Evader time limit
     */
    static void evaderTimeLimit(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 2);
        player.setHealth(8.0F);
        helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, player), "setup: the Evader starts");

        // Act & Assert
        TeamMoves.age(player, 1199);
        TeamMoves.tick(player);
        helper.assertTrue(TeamMoves.running(player).isPresent(), "1199 ticks: still running");
        TeamMoves.age(player, 1);
        TeamMoves.tick(player);
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "1200 ticks: over");
        helper.assertTrue(TeamMoves.cooling(player, TeamMove.EVADER), "the Evader cools down");
        helper.succeed();
    }

    /**
     * When the ward of an Evader is healed to half health, should the next tick end the move.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the ward-healed rule
     */
    static void wardHealed(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 2);
        player.setHealth(8.0F);
        helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, player), "setup: the Evader starts");
        TeamMoves.tick(player);
        helper.assertTrue(TeamMoves.running(player).isPresent(), "setup: still hurt, still running");
        player.setHealth(10.0F);

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "healed to half: over");
        helper.assertTrue(vexes.stream().allMatch(vex -> vex.teamMove() == TeamMove.NONE), "unmarked");
        helper.succeed();
    }

    /**
     * When every member dies, should the next tick end the move with the husk still alive.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the empty-roster rule
     */
    static void rosterEmpty(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        scene.vexes().forEach(vex -> vex.kill(helper.getLevel()));

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        helper.assertTrue(TeamMoves.running(scene.player()).isEmpty(), "no members, no move");
        helper.assertTrue(scene.husk().isAlive(), "the enemy lives");
        helper.assertTrue(TeamMoves.cooling(scene.player(), TeamMove.SHIELD), "the Shield cools down");
        helper.succeed();
    }

    /**
     * When the summoner dies (a dead player is still ticked), should the next tick end the move.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the summoner-absent rule
     */
    static void summonerDies(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        scene.player().kill(helper.getLevel());
        helper.assertFalse(scene.player().isAlive(), "setup: the summoner is dead");

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        assertOver(helper, scene);
        helper.succeed();
    }

    /**
     * When the summoner turns spectator, should the next tick end the move.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the summoner-absent rule
     */
    static void summonerSpectates(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        scene.player().setGameMode(GameType.SPECTATOR);

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        assertOver(helper, scene);
        helper.succeed();
    }

    /**
     * When the summoner moves to the Nether mid-move, should the next tick put every member's kit away (the members,
     * lifted past the 16-block follow range, stay behind alive in the test level) and end the move.
     *
     * <p>Method under test: {@link TeamMoves#tick}: members in another level leave the roster
     */
    static void summonerChangesLevel(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        // Members within 16 blocks follow the summoner through a dimension change (BoundVexEvents.note/bring): lift
        // them out of range.
        scene.vexes().forEach(vex -> vex.setPos(vex.position().add(0.0, 20.0, 0.0)));
        BoundVexWorldTestSupport.room(helper, scene.player(), Level.NETHER, 3, 4);

        // Act
        TeamMoves.tick(scene.player());

        // Assert (alive on the old objects: the test proves the "other level" branch, not the "removed" branch)
        scene.vexes().forEach(vex -> helper.assertTrue(vex.isAlive(), "setup: the member stayed behind alive"));
        assertOver(helper, scene);
        helper.succeed();
    }

    /**
     * When a Shield has ended, should the same trigger not start another Shield, the cooldown being per kind, and an
     * Evader still be able to start.
     *
     * <p>Method under test: {@link TeamMoves#tick}, {@link TeamMoves#start}: the 30-second cooldown
     */
    static void cooldownPerKind(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        scene.husk().kill(helper.getLevel());
        TeamMoves.tick(scene.player());
        assertOver(helper, scene);
        Husk another = bigHusk(helper, hangPoint(helper).add(3.0, 0.0, 1.0));
        scene.player().setHealth(8.0F);

        // Act & Assert
        helper.assertFalse(TeamMoves.start(scene.player(), TeamMove.SHIELD, another), "the same kind is still cooling");
        helper.assertFalse(TeamMoves.cooling(scene.player(), TeamMove.EVADER), "another kind is not");
        helper.assertTrue(TeamMoves.start(scene.player(), TeamMove.EVADER, scene.player()), "an Evader starts");
        helper.succeed();
    }

    /**
     * When the summoner logs out mid-move, should every member's kit be dismissed, its mark cleared and its record
     * dropped, with no cooldown remembered.
     *
     * <p>Method under test: {@link TeamMoves#onLogout}
     */
    static void logoutReleasesKits(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);

        // Act
        TeamMoves.onLogout(new PlayerEvent.PlayerLoggedOutEvent(scene.player()));

        // Assert
        helper.assertTrue(TeamMoves.running(scene.player()).isEmpty(), "the record is gone");
        for (BoundVex vex : scene.vexes()) {
            helper.assertValueEqual(vex.teamMove(), TeamMove.NONE, "unmarked");
            helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back");
            helper.assertFalse(vex.isConjured(EquipmentSlot.OFFHAND), "the shield is gone");
        }
        helper.assertFalse(TeamMoves.cooling(scene.player(), TeamMove.SHIELD), "a logout starts no cooldown");
        helper.succeed();
    }

    /**
     * When a member dies, should it drop its given sword but no shield, and the next tick should take it out of the
     * roster while the move goes on.
     *
     * <p>Method under test: {@link TeamMoves#tick}: pruning the roster; {@code BoundVex#dropCustomDeathLoot}
     */
    static void deadMemberDropsNoKit(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        BoundVex dying = scene.vexes().get(0);
        AABB around = new AABB(dying.position(), dying.position()).inflate(3.0);
        GameTestSupport.whenDone(
                helper,
                () -> helper.getLevel()
                        .getEntitiesOfClass(ItemEntity.class, around)
                        .forEach(ItemEntity::discard));
        helper.assertTrue(TeamMoveTestSupport.goalOf(dying).canUse(), "setup: listed while alive");

        // Act
        dying.kill(helper.getLevel());
        TeamMoves.tick(scene.player());

        // Assert
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
        helper.assertTrue(
                items.stream().anyMatch(item -> item.getItem().is(Items.IRON_SWORD)), "the given sword drops");
        helper.assertFalse(items.stream().anyMatch(item -> item.getItem().is(Items.SHIELD)), "no shield drops");
        // Through the goal, which reads membership and so record.members: roster() filters on isAlive and would pass
        // anyway
        helper.assertFalse(TeamMoveTestSupport.goalOf(dying).canUse(), "the dead member is off the roster");
        helper.assertValueEqual(TeamMoves.roster(scene.player()).size(), 2, "two members are left");
        helper.assertTrue(TeamMoves.running(scene.player()).isPresent(), "and the move goes on");
        helper.succeed();
    }

    /**
     * When a vex is on the roster, should its goal run; a vex that is not should not.
     *
     * <p>Method under test: {@link TeamMoveGoal#canUse}
     */
    static void goalRunsForAListedMember(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 4);
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, bigHusk(helper, beside(helper))), "setup");
        BoundVex member = TeamMoves.roster(player).get(0);
        BoundVex left = vexes.stream()
                .filter(vex -> vex.teamMove() == TeamMove.NONE)
                .findFirst()
                .orElseThrow();

        // Act & Assert
        helper.assertTrue(TeamMoveTestSupport.goalOf(member).canUse(), "a listed member runs the goal");
        helper.assertFalse(TeamMoveTestSupport.goalOf(left).canUse(), "an unlisted vex does not");
        helper.succeed();
    }

    /**
     * When a goal stops while the record still lists the vex (a higher-priority goal took the move control), should
     * the kit stay.
     *
     * <p>Method under test: {@link TeamMoveGoal#stop}
     */
    static void goalStopKeepsTheKitWhileListed(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        BoundVex member = scene.vexes().get(0);

        // Act
        TeamMoveTestSupport.goalOf(member).stop();

        // Assert
        helper.assertValueEqual(member.teamMove(), TeamMove.SHIELD, "still marked");
        helper.assertTrue(member.isConjured(EquipmentSlot.MAINHAND), "the kit stays");
        helper.succeed();
    }

    /**
     * When anything dismissed a kit slot and gave a member its sword back (a preempted job goal's {@code stop}
     * dismisses its hand), should the next goal tick conjure the shield again and stash the sword again.
     *
     * <p>Method under test: {@link TeamMoveGoal#tick}: putting the kit back
     */
    static void goalPutsAReplacedItemBack(GameTestHelper helper) {
        // Arrange (the synthetic dismiss stands in for a job goal's own dismiss(MAINHAND) in its stop)
        ShieldScene scene = shield(helper);
        BoundVex member = scene.vexes().get(0);
        member.dismiss(EquipmentSlot.MAINHAND);
        helper.assertTrue(BoundVexTestSupport.swordBack(member), "setup: the sword is back in the hand");

        // Act
        TeamMoveTestSupport.goalOf(member).tick();

        // Assert
        helper.assertTrue(
                member.isConjured(EquipmentSlot.MAINHAND)
                        && member.getMainHandItem().is(Items.SHIELD),
                "the shield is back");
        helper.assertTrue(
                member.stashed(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "and the sword is stashed again");
        helper.succeed();
    }

    /**
     * When the ward of an Evader dies, should the next tick end the move with the summoner alive and the Evader
     * cooling.
     *
     * <p>Method under test: {@link TeamMoves#tick}: the ward-dead rule (spec section 2.5)
     */
    static void wardDies(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, 2);
        Villager ally = foe(helper, EntityTypes.VILLAGER, hangPoint(helper).add(2.0, 0.0, 2.0));
        ally.setHealth(ally.getMaxHealth() * 0.4F);
        helper.assertTrue(TeamMoves.start(player, TeamMove.EVADER, ally), "setup: the Evader starts for the ally");
        ally.kill(helper.getLevel());

        // Act
        TeamMoves.tick(player);

        // Assert
        helper.assertTrue(TeamMoves.running(player).isEmpty(), "the ward is dead: over");
        helper.assertTrue(TeamMoves.cooling(player, TeamMove.EVADER), "the Evader cools down");
        helper.assertTrue(player.isAlive(), "the summoner is untouched");
        helper.succeed();
    }

    /**
     * When a Shield has ended, should its cooldown still hold after 599 more ticks and be over after 600 (so the same
     * kind can start again).
     *
     * <p>Method under test: {@link TeamMoves#age}, {@link TeamMoves#cooling}: the 30-second cooldown ends
     */
    static void cooldownLastsThirtySeconds(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        scene.husk().kill(helper.getLevel());
        TeamMoves.tick(scene.player());
        assertOver(helper, scene);
        Husk another = bigHusk(helper, hangPoint(helper).add(3.0, 0.0, 1.0));

        // Act & Assert
        TeamMoves.age(scene.player(), 599);
        helper.assertTrue(TeamMoves.cooling(scene.player(), TeamMove.SHIELD), "599 ticks on: still cooling");
        TeamMoves.age(scene.player(), 1);
        helper.assertFalse(TeamMoves.cooling(scene.player(), TeamMove.SHIELD), "600 ticks on: over");
        helper.assertTrue(TeamMoves.start(scene.player(), TeamMove.SHIELD, another), "the same kind starts again");
        helper.succeed();
    }

    /**
     * When a post-tick event for the summoner is posted on the game bus, should the subscriber step the move (a
     * Shield whose husk died is over).
     *
     * <p>Method under test: {@link TeamMoves#onPlayerTick} and its {@code @SubscribeEvent} registration
     */
    static void playerTickEventStepsTheMove(GameTestHelper helper) {
        // Arrange (mock players get no PlayerTickEvent, so post one, as BoundVexLavaGameTests does for the logout)
        ShieldScene scene = shield(helper);
        scene.husk().kill(helper.getLevel());

        // Act
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(scene.player()));

        // Assert
        assertOver(helper, scene);
        helper.succeed();
    }

    /**
     * When a Shield starts for vexes with their full AI, should the team-move goal sit at priority 1 holding the move
     * and look flags, and be running within a few ticks with the kit still on.
     *
     * <p>Method under test: {@code BoundVex#registerGoals}, {@link TeamMoveGoal}
     */
    static void goalRunsInTheSelector(GameTestHelper helper) {
        // Arrange (no TeamMoves.tick is needed; flyingVexes has full AI)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        TeamMoveTestSupport.flyingVexes(helper, player, 3, hangPoint(helper));
        helper.assertTrue(
                TeamMoves.start(
                        player,
                        TeamMove.SHIELD,
                        bigHusk(helper, hangPoint(helper).add(3.0, 0.0, 0.0))),
                "setup: the Shield starts");
        BoundVex member = TeamMoves.roster(player).get(0);
        WrappedGoal wrapped = member.goalSelector.getAvailableGoals().stream()
                .filter(candidate -> candidate.getGoal() instanceof TeamMoveGoal)
                .findFirst()
                .orElseThrow();

        // Act & Assert
        helper.assertValueEqual(wrapped.getPriority(), 1, "priority 1");
        helper.assertValueEqual(wrapped.getFlags(), EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK), "MOVE and LOOK");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(wrapped.isRunning(), "the goal runs in the selector"))
                .thenExecute(() -> helper.assertTrue(member.isConjured(EquipmentSlot.MAINHAND), "the kit is still on"))
                .thenSucceed();
    }

    /**
     * When the server stops, should every record be dropped, the goal of a former member refuse to run, and its stop
     * dismiss the kit the record can no longer look after.
     *
     * <p>Method under test: {@code BoundVexEvents.onServerStopped}, {@code TeamMoves#clear},
     * {@link TeamMoveGoal#stop}
     */
    static void serverStopClearsRecords(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        BoundVex member = scene.vexes().get(0);
        Dice.RAID_KIT.script(5);
        GameTestSupport.whenDone(helper, Dice::resetAll);

        // Act
        BoundVexEvents.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));

        // Assert
        helper.assertTrue(TeamMoves.running(scene.player()).isEmpty(), "no record is left");
        helper.assertFalse(TeamMoveTestSupport.goalOf(member).canUse(), "the goal no longer runs");
        TeamMoveTestSupport.goalOf(member).stop();
        helper.assertValueEqual(member.teamMove(), TeamMove.NONE, "its stop unmarks the vex");
        helper.assertTrue(BoundVexTestSupport.swordBack(member), "and puts the sword back");
        // A still-scripted 5 is outside [0, 1) and would throw: the stop dropped the Raid dice script
        helper.assertValueEqual(
                Dice.RAID_KIT.below(helper.getLevel().getRandom(), 1), 0, "and unscripts the Raid dice");
        helper.succeed();
    }

    /**
     * When a listed member is replaced by a vex marked NONE (what a portal or a save and load leaves), should the next
     * tick drop it from the roster and the move go on with the others.
     *
     * <p>Method under test: {@link TeamMoves#tick}: pruning a member that is no longer marked
     */
    static void memberMarkedNoneIsDropped(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        BoundVex copy = scene.vexes().get(0);
        copy.setTeamMove(TeamMove.NONE);

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        helper.assertFalse(TeamMoves.roster(scene.player()).contains(copy), "the copy is off the roster");
        helper.assertFalse(TeamMoveTestSupport.goalOf(copy).canUse(), "and its goal does not run");
        helper.assertValueEqual(TeamMoves.roster(scene.player()).size(), 2, "two members are left");
        helper.assertTrue(TeamMoves.running(scene.player()).isPresent(), "and the move goes on");
        helper.succeed();
    }

    /**
     * When a listed member is removed from the world without dying (an unloaded chunk, a discard), should the next tick
     * drop it from the roster (the level cannot find it) and the move go on.
     *
     * <p>Method under test: {@link TeamMoves#tick}: pruning a member the level cannot find
     */
    static void removedMemberIsDropped(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = shield(helper);
        BoundVex gone = scene.vexes().get(0);
        gone.discard();

        // Act
        TeamMoves.tick(scene.player());

        // Assert
        helper.assertValueEqual(TeamMoves.roster(scene.player()).size(), 2, "two members are left");
        helper.assertTrue(TeamMoves.running(scene.player()).isPresent(), "and the move goes on");
        helper.succeed();
    }

    /**
     * Kills {@code kills} of the roster's members of a running {@code move}, steps the move and checks that it goes on
     * with the others and that the dead members dropped only the iron sword the kit had stashed, none of the kit.
     */
    private static void membersDieAndTheMoveGoesOn(
            GameTestHelper helper, TeamMove move, int count, LivingEntity enemy, int kills) {
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        frozenVexes(helper, player, count);
        helper.assertTrue(TeamMoves.start(player, move, enemy), "setup: the move starts");
        List<BoundVex> dying = TeamMoves.roster(player).subList(0, kills);
        AABB around = new AABB(hangPoint(helper), hangPoint(helper)).inflate(8.0);
        GameTestSupport.whenDone(
                helper,
                () -> helper.getLevel()
                        .getEntitiesOfClass(ItemEntity.class, around)
                        .forEach(ItemEntity::discard));
        dying.forEach(vex -> vex.kill(helper.getLevel()));

        TeamMoves.tick(player);

        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
        helper.assertTrue(
                items.stream().anyMatch(item -> item.getItem().is(Items.IRON_SWORD)), "the given sword drops");
        helper.assertTrue(
                items.stream().allMatch(item -> item.getItem().is(Items.IRON_SWORD)),
                "and nothing of the kit does, only the sword");
        helper.assertValueEqual(TeamMoves.roster(player).size(), count - kills, "the others are left");
        helper.assertTrue(TeamMoves.running(player).isPresent(), "and the move goes on");
        helper.succeed();
    }

    /**
     * When a member of a running Multishot dies, should it drop only its given sword, never the crossbow, and the move
     * go on with the other two.
     *
     * <p>Method under test: {@link TeamMoves#tick}: pruning; {@code BoundVex#dropCustomDeathLoot} for a Multishot kit
     */
    static void multishotContinuesAndDropsNoKit(GameTestHelper helper) {
        membersDieAndTheMoveGoesOn(helper, TeamMove.MULTISHOT, 3, bigHusk(helper, beside(helper)), 1);
    }

    /**
     * When five members of a running Raid die, should they drop only their given swords, never a raid weapon, shield
     * or armor piece, and the Raid go on with the other seven.
     *
     * <p>Method under test: {@link TeamMoves#tick}: pruning; {@code BoundVex#dropCustomDeathLoot} for a Raid kit
     */
    static void raidContinuesAndDropsNoKit(GameTestHelper helper) {
        membersDieAndTheMoveGoesOn(helper, TeamMove.RAID, 12, foe(helper, EntityTypes.RAVAGER, beside(helper)), 5);
    }

    /** Whether the vex's goal selector is running a goal of the given class. */
    private static boolean runs(BoundVex vex, Class<? extends Goal> goal) {
        return vex.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.isRunning() && goal.isInstance(wrapped.getGoal()));
    }

    /**
     * When a swelling creeper comes within eight blocks of a member of a running Shield, should the member flee (the
     * priority-0 goal pauses the team-move goal) and keep its mark and its kit, and when the creeper stops swelling,
     * should its team-move goal run again with the kit still on.
     *
     * <p>Method under test: {@link TeamMoveGoal} against {@code FleeCreeperGoal}, in the real goal selector
     */
    static void fleeKeepsTheKitAndResumes(GameTestHelper helper) {
        // Arrange (full-AI vexes, so the selector decides)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hangPoint(helper));
        Husk husk = bigHusk(helper, beside(helper).add(0.0, 0.0, -4.0));
        husk.setPermanentlyInvulnerable(true);
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, husk), "setup: the Shield starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        BoundVex member = TeamMoves.roster(player).get(0);
        // A patient creeper: its fuse is so long that it hisses for as long as the vex needs to flee, never exploding
        Creeper creeper = BoundVexTestSupport.patient(
                helper, foe(helper, EntityTypes.CREEPER, member.position().add(2.0, 0.0, 0.0)));

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(runs(member, TeamMoveGoal.class), "setup: it runs the move"))
                .thenExecute(() -> creeper.setSwellDir(1))
                .thenWaitUntil(() -> helper.assertTrue(runs(member, FleeCreeperGoal.class), "it flees the creeper"))
                .thenExecuteFor(10, () -> {
                    helper.assertFalse(runs(member, TeamMoveGoal.class), "the move is paused while it flees");
                    helper.assertValueEqual(member.teamMove(), TeamMove.SHIELD, "still marked");
                    helper.assertTrue(member.isConjured(EquipmentSlot.MAINHAND), "and the kit stays on");
                    helper.assertTrue(TeamMoves.roster(player).contains(member), "and it stays on the roster");
                })
                .thenExecute(() -> creeper.setSwellDir(-1))
                .thenWaitUntil(() -> helper.assertTrue(runs(member, TeamMoveGoal.class), "the move resumes"))
                .thenExecute(() -> {
                    helper.assertTrue(member.isConjured(EquipmentSlot.MAINHAND), "with the kit on");
                    helper.assertTrue(vexes.contains(member), "setup: the same vex");
                })
                .thenSucceed();
    }
}
