package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.bigHusk;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.flyingVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.foe;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.frozenVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.hangPoint;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.ShieldScene;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of the Shield team move (Parts 6-7 spec section 4.1).
 *
 * <p>Functional spec §8.9, §11.8.
 */
final class BoundVexTeamMoveShieldGameTests {
    private BoundVexTeamMoveShieldGameTests() {}

    /** Registers the tests. */
    static void registerAll() {
        FangGameTests.addPadded(
                SHARED,
                "team_move_shield_bashes_for_four_and_knocks_back",
                200,
                BoundVexTeamMoveShieldGameTests::bashesForFourAndKnocksBack);
        FangGameTests.addPadded(
                SHARED,
                "team_move_shield_backs_off_to_about_six",
                200,
                BoundVexTeamMoveShieldGameTests::backsOffToAboutSix);
        FangGameTests.addPadded(
                SHARED,
                "team_move_shield_bashes_again_after_forty_ticks",
                200,
                BoundVexTeamMoveShieldGameTests::bashesAgainAfterFortyTicks);
        FangGameTests.addPadded(
                SHARED,
                "team_move_shield_lunge_gives_up_after_forty_ticks",
                200,
                BoundVexTeamMoveShieldGameTests::lungeGivesUp);
        FangGameTests.addPadded(
                SHARED,
                "team_move_shield_blocks_a_hit_from_the_front",
                BoundVexTeamMoveShieldGameTests::blocksAHitFromTheFront);
        FangGameTests.addPadded(
                SHARED,
                "team_move_shield_takes_a_hit_from_behind",
                BoundVexTeamMoveShieldGameTests::takesAHitFromBehind);
    }

    /**
     * A Shield against a 40-health husk, with three flying vexes nine blocks south of it, stepped every tick. The husk
     * has no AI, so it never moves: a knockback shows in its delta movement, and the 5 to 8.5 and 38 to 75 windows
     * below are not disturbed by it being pushed.
     */
    private static ShieldScene scene(GameTestHelper helper) {
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -4.0));
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 5.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, husk), "setup: the Shield starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        return new ShieldScene(player, vexes, husk);
    }

    /**
     * When three vexes in a Shield come at a husk from the south, should the first bash cost exactly 4 health and push
     * the husk north, away from them, harder than a plain hit. The husk has no AI, so a knockback shows in its delta
     * movement, not its position.
     *
     * <p>Method under test: {@code ShieldMove.tick}: approach, lunge and bash
     *
     * <p>Functional spec §11.8.
     */
    static void bashesForFourAndKnocksBack(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = scene(helper);

        // Act & Assert (a vanilla hit's knockback 0.4 plus the bash's 1.5, plus up to two more bashes at once, gives z
        // of about -1.7 to -2.7; below -1.0 separates 1.5 from 0.4)
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.husk().getHealth() < 40.0F, "the first bash lands"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            Math.abs(scene.husk().getHealth() - 36.0F) < 0.01F,
                            "a bash costs 4, health is " + scene.husk().getHealth());
                    helper.assertTrue(
                            scene.husk().getDeltaMovement().z < -1.0,
                            "knocked north, away from the vexes, harder than a plain hit: "
                                    + scene.husk().getDeltaMovement());
                })
                .thenSucceed();
    }

    /**
     * When the first bash has landed, should every member be back at about 6 blocks from the husk 30 ticks later
     * (5 to 8.5).
     *
     * <p>Method under test: {@code ShieldMove.tick}: the back-off
     *
     * <p>Functional spec §11.8.
     */
    static void backsOffToAboutSix(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = scene(helper);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.husk().getHealth() < 40.0F, "the first bash lands"))
                .thenExecuteAfter(30, () -> {
                    for (BoundVex vex : scene.vexes()) {
                        double distance = vex.distanceTo(scene.husk());
                        helper.assertTrue(distance >= 5.0 && distance <= 8.5, "backed off to about 6, not " + distance);
                    }
                })
                .thenSucceed();
    }

    /**
     * When a member has bashed, should its next bash come 38 to 75 ticks after its first: the member's own 40-tick
     * clock, read from the member's clocks (the husk's health cannot tell the members' bashes apart, since its
     * invulnerability frames swallow some). A member that has bashed also waits 30 to 40 ticks on that clock, and no
     * member lunges before its own clock is ready. A gap counts only when the clock was set in the {@code BACKING}
     * stage (the stage a bash or a give-up sets it in), so a clock that changed in any other stage is not read as a
     * bash.
     *
     * <p>Method under test: {@code ShieldMove.tick}: the bash clock
     *
     * <p>Functional spec §11.8.
     */
    static void bashesAgainAfterFortyTicks(GameTestHelper helper) {
        // Arrange
        ShieldScene scene = scene(helper);
        Map<UUID, Long> firstClock = new HashMap<>();
        List<Long> gaps = new ArrayList<>();
        List<String> earlyLunges = new ArrayList<>();
        BoundVexTestSupport.everyTick(helper, () -> {
            long now = helper.getLevel().getGameTime();
            for (BoundVex vex : scene.vexes()) {
                TeamMoves.Clocks clocks = TeamMoves.clocksOf(vex).orElseThrow();
                if (clocks.stage().equals("LUNGING") && now < clocks.nextAction()) {
                    earlyLunges.add("a member lunged at " + now + " with its clock at " + clocks.nextAction());
                }
                long next = clocks.nextAction();
                if (next == 0L || !clocks.stage().equals("BACKING")) {
                    continue;
                }
                Long first = firstClock.putIfAbsent(vex.getUUID(), next);
                if (first != null && first != next) {
                    gaps.add(next - first);
                    firstClock.put(vex.getUUID(), next);
                }
            }
        });

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(scene.husk().getHealth() < 40.0F, "the first bash lands"))
                .thenExecute(() -> {
                    long now = helper.getLevel().getGameTime();
                    for (BoundVex vex : scene.vexes()) {
                        TeamMoves.Clocks clocks = TeamMoves.clocksOf(vex).orElseThrow();
                        if (clocks.stage().equals("BACKING")) {
                            long wait = clocks.nextAction() - now;
                            helper.assertTrue(
                                    wait >= 30 && wait <= 40, "a member that bashed waits on its own clock: " + wait);
                        }
                    }
                })
                .thenWaitUntil(() -> helper.assertFalse(gaps.isEmpty(), "a member bashes a second time"))
                .thenExecute(() -> {
                    gaps.forEach(gap -> helper.assertTrue(
                            gap >= 38 && gap <= 75, "the second bash came " + gap + " ticks after the first"));
                    helper.assertTrue(earlyLunges.isEmpty(), "no member lunges before its clock: " + earlyLunges);
                })
                .thenSucceed();
    }

    /**
     * When a lunging member's enemy is moved about 40 blocks away, should the member give up its lunge and be backing
     * off within 45 ticks of first lunging (the lunge limit is 40).
     *
     * <p>Method under test: {@code ShieldMove.tick}: the lunge give-up (spec section 4.1)
     */
    static void lungeGivesUp(GameTestHelper helper) {
        // Arrange (as scene; the husk has no AI, so it stays where it is put; 40 blocks is within the 48-block end
        // rule)
        ShieldScene scene = scene(helper);
        BoundVex member = scene.vexes().get(0);
        AtomicLong lungeSeen = new AtomicLong();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        TeamMoves.clocksOf(member).orElseThrow().stage().equals("LUNGING"), "a member lunges"))
                .thenExecute(() -> {
                    lungeSeen.set(helper.getTick());
                    scene.husk().setPos(scene.husk().position().add(0.0, 40.0, 0.0));
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        TeamMoves.clocksOf(member).orElseThrow().stage().equals("BACKING"), "the lunge is given up"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getTick() - lungeSeen.get() <= 45, "it gave up within about forty ticks");
                    helper.assertTrue(
                            member.distanceTo(scene.husk()) > 5.0,
                            "and it gave up far from the husk (a bash would have left it beside it), not "
                                    + member.distanceTo(scene.husk()));
                })
                .thenSucceed();
    }

    /**
     * When a husk hits a Shield member from the front, should the shield block it: the hit returns false and health
     * does not change.
     *
     * <p>Method under test: {@code BoundVex.hurtServer}: the Part 3 block, with the conjured off-hand shield
     *
     * <p>Functional spec §8.9, §11.8.
     */
    static void blocksAHitFromTheFront(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 3);
        Husk enemy = bigHusk(helper, hangPoint(helper).add(0.0, 0.0, -6.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, enemy), "setup: the Shield starts");
        BoundVex vex = vexes.get(0);
        vex.setYRot(-90.0F);
        vex.setYHeadRot(-90.0F);
        Husk attacker = foe(helper, EntityTypes.HUSK, vex.position().add(2.0, 0.0, 0.0));
        float before = vex.getHealth();

        // Act
        boolean hurt = vex.hurtServer(
                helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);

        // Assert
        helper.assertFalse(hurt, "the hit from the front is blocked");
        helper.assertValueEqual(vex.getHealth(), before, "health is unchanged");
        helper.succeed();
    }

    /**
     * When a husk hits a Shield member from behind, should the shield not help: the hit lands.
     *
     * <p>Method under test: {@code BoundVex.hurtServer}: a block covers the front only
     *
     * <p>Functional spec §8.9, §11.8.
     */
    static void takesAHitFromBehind(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        List<BoundVex> vexes = frozenVexes(helper, player, 3);
        Husk enemy = bigHusk(helper, hangPoint(helper).add(0.0, 0.0, -6.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.SHIELD, enemy), "setup: the Shield starts");
        BoundVex vex = vexes.get(0);
        vex.setYRot(-90.0F);
        vex.setYHeadRot(-90.0F);
        Husk attacker = foe(helper, EntityTypes.HUSK, vex.position().add(-2.0, 0.0, 0.0));
        float before = vex.getHealth();

        // Act
        vex.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);

        // Assert
        helper.assertTrue(vex.getHealth() < before, "the hit from behind lands");
        helper.succeed();
    }
}
