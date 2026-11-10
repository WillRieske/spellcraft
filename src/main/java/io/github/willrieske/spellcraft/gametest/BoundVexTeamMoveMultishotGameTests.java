package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.bigHusk;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.flyingVexes;
import static io.github.willrieske.spellcraft.gametest.TeamMoveTestSupport.hangPoint;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.TeamMoves;
import io.github.willrieske.spellcraft.spell.rules.TeamMove;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** GameTests of the Multishot team move (Parts 6-7 spec section 4.2). */
final class BoundVexTeamMoveMultishotGameTests {
    private BoundVexTeamMoveMultishotGameTests() {}

    /** Registers the tests. */
    static void registerAll() {
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_volley_is_three_arrows",
                200,
                BoundVexTeamMoveMultishotGameTests::volleyIsThreeArrows);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_closes_in_from_twelve",
                200,
                BoundVexTeamMoveMultishotGameTests::closesInFromTwelve);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_backs_off_from_three",
                200,
                BoundVexTeamMoveMultishotGameTests::backsOffFromThree);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_holds_fire_for_a_friend",
                200,
                BoundVexTeamMoveMultishotGameTests::holdsFireForAFriend);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_all_three_fire_from_their_arc_slots",
                200,
                BoundVexTeamMoveMultishotGameTests::allThreeFireFromTheirSlots);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_second_volley_waits_forty_ticks",
                200,
                BoundVexTeamMoveMultishotGameTests::secondVolleyWaitsFortyTicks);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_holds_inside_the_band",
                200,
                BoundVexTeamMoveMultishotGameTests::holdsInsideTheBand);
    }

    /** The arrows in flight that a Bound Vex owns, around the hang point. */
    private static List<AbstractArrow> volleyArrows(GameTestHelper helper) {
        return helper.getLevel()
                .getEntitiesOfClass(
                        AbstractArrow.class,
                        new AABB(hangPoint(helper), hangPoint(helper)).inflate(16.0),
                        arrow -> arrow.getOwner() instanceof BoundVex);
    }

    /** The angle in degrees between the velocities of two arrows. */
    private static double angleBetween(AbstractArrow a, AbstractArrow b) {
        double cos = a.getDeltaMovement().normalize().dot(b.getDeltaMovement().normalize());
        return Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0)));
    }

    /**
     * When three vexes keep seven blocks from a husk, should each shooter's first volley be exactly three arrows owned
     * by that vex, not pickable, spread over about 20 degrees, and leave its crossbow unharmed and empty.
     *
     * <p>Method under test: {@code MultishotMove.tick}, {@code VexShots.volley}
     */
    static void volleyIsThreeArrows(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -4.0));
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.0));
        // Arrows that miss stay stuck in the ground: discard them when the test is done so they do not leak into other
        // tests' counts
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(volleyArrows(helper).isEmpty(), "a volley is fired"))
                .thenExecute(() -> {
                    Map<UUID, List<AbstractArrow>> byShooter = volleyArrows(helper).stream()
                            .collect(Collectors.groupingBy(
                                    arrow -> arrow.getOwner().getUUID()));
                    byShooter.forEach((shooter, arrows) -> {
                        helper.assertValueEqual(arrows.size(), 3, "three arrows from one crossbow");
                        arrows.forEach(arrow -> helper.assertValueEqual(
                                arrow.pickup, AbstractArrow.Pickup.DISALLOWED, "an arrow nobody can pick up"));
                        double widest = Math.max(
                                angleBetween(arrows.get(0), arrows.get(1)),
                                Math.max(
                                        angleBetween(arrows.get(0), arrows.get(2)),
                                        angleBetween(arrows.get(1), arrows.get(2))));
                        helper.assertTrue(
                                widest >= 10.0 && widest <= 32.0, "spread over about 20 degrees, not " + widest);
                        BoundVex vex = vexes.stream()
                                .filter(v -> v.getUUID().equals(shooter))
                                .findFirst()
                                .orElseThrow();
                        ItemStack crossbow = vex.getMainHandItem();
                        helper.assertFalse(CrossbowItem.isCharged(crossbow), "the crossbow is empty after the shot");
                        helper.assertValueEqual(crossbow.getDamageValue(), 0, "and unharmed");
                        helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "and still conjured");
                    });
                })
                .thenSucceed();
    }

    /**
     * When the vexes start twelve blocks from an (invulnerable) husk, should every member close in to between 5.5 and
     * 8.5 blocks and stay there.
     *
     * <p>Method under test: {@code MultishotMove.tick}: the 6 to 8 band
     */
    static void closesInFromTwelve(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -6.0));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 6.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        vexes.stream().allMatch(vex -> vex.distanceTo(husk) <= 8.5),
                        "every member is within 8.5 blocks"))
                .thenExecuteFor(
                        20,
                        () -> vexes.forEach(vex -> {
                            double distance = vex.distanceTo(husk);
                            helper.assertTrue(distance >= 5.5 && distance <= 8.8, "holds the band, not " + distance);
                        }))
                .thenSucceed();
    }

    /**
     * When the vexes start three blocks from an (invulnerable) husk, should every member back off to at least 5.5
     * blocks.
     *
     * <p>Method under test: {@code MultishotMove.tick}: closer than 6 backs off
     */
    static void backsOffFromThree(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -2.0));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 1.0));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        vexes.stream().allMatch(vex -> vex.distanceTo(husk) >= 5.5), "every member backed off"))
                .thenSucceed();
    }

    /**
     * When the summoner stands on the line between the vexes and the husk, should no member shoot for 60 ticks (the
     * Part 3 hold-fire rule), and when the summoner steps aside, should a volley fly (the control that proves the
     * silence was the friend and not a scene that cannot shoot).
     *
     * <p>Method under test: {@code MultishotMove.tick}: {@code VexShots.friendInTheWay}
     */
    static void holdsFireForAFriend(GameTestHelper helper) {
        // Arrange (everything above the barrier shell, which blocks line of sight; the summoner is on the line)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -3.5));
        husk.setPermanentlyInvulnerable(true);
        flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.5));
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(BoundVexTestSupport.AIR_SPOT), NORTH); // on the line
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        60, () -> helper.assertTrue(volleyArrows(helper).isEmpty(), "nobody shoots past a friend"))
                .thenExecute(() -> MockPlayers.moveTo(helper, player, FangGameTests.STAND, NORTH))
                .thenWaitUntil(() ->
                        helper.assertFalse(volleyArrows(helper).isEmpty(), "a volley flies once the way is clear"))
                .thenSucceed();
    }

    /**
     * When three vexes start twelve blocks from a husk in a row one block apart, should each of the three fire a first
     * volley within about 20 ticks of all three reaching the 6 to 8 block band (each flies to its own arc slot, 25
     * degrees apart round the enemy, so the shooters are not in each other's lines).
     *
     * <p>Method under test: {@code MultishotMove.tick}: the arc slots
     */
    static void allThreeFireFromTheirSlots(GameTestHelper helper) {
        // Arrange (twelve blocks is outside the band, so the members travel to their slots)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -6.0));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 6.0));
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        AtomicLong arrived = new AtomicLong();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        vexes.stream().allMatch(vex -> vex.distanceTo(husk) <= 8.2), "every member is in the band"))
                .thenExecute(() -> arrived.set(helper.getTick()))
                .thenWaitUntil(() -> helper.assertValueEqual(
                        volleyArrows(helper).stream()
                                .map(arrow -> arrow.getOwner().getUUID())
                                .distinct()
                                .count(),
                        3L,
                        "all three members have fired"))
                .thenExecute(() ->
                        helper.assertTrue(helper.getTick() - arrived.get() <= 20, "and within 20 ticks of arriving"))
                .thenSucceed();
    }

    /**
     * When a member has fired its first volley, should its second not come before 40 ticks later (and come within 44).
     *
     * <p>Method under test: {@code MultishotMove.tick}: the 40-tick cadence
     */
    static void secondVolleyWaitsFortyTicks(GameTestHelper helper) {
        // Arrange (a volley's three arrows appear in one tick, so arrows of one owner within 3 ticks of its last volley
        // belong to that volley; volley times are recorded per owner, every tick)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -4.0));
        husk.setPermanentlyInvulnerable(true);
        flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.0));
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        Map<UUID, List<Long>> volleys = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        BoundVexTestSupport.everyTick(helper, () -> {
            long now = helper.getLevel().getGameTime();
            for (AbstractArrow arrow : volleyArrows(helper)) {
                if (seen.add(arrow.getUUID())) {
                    List<Long> times = volleys.computeIfAbsent(arrow.getOwner().getUUID(), id -> new ArrayList<>());
                    if (times.isEmpty() || now - times.get(times.size() - 1) > 3) {
                        times.add(now);
                    }
                }
            }
        });

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        volleys.values().stream().anyMatch(times -> times.size() >= 2),
                        "a member fires a second volley"))
                .thenExecute(() -> {
                    List<Long> times = volleys.values().stream()
                            .filter(list -> list.size() >= 2)
                            .findFirst()
                            .orElseThrow();
                    long gap = times.get(1) - times.get(0);
                    helper.assertTrue(
                            gap >= 40 && gap <= 44, "the second volley came " + gap + " ticks after the first");
                })
                .thenSucceed();
    }

    /**
     * When the vexes start seven blocks from an (invulnerable) husk, inside the 6 to 8 block band, should no member
     * move more than half a block in the next 20 ticks (between 6 and 8: hold).
     *
     * <p>Method under test: {@code MultishotMove.tick}: the hold inside the band
     */
    static void holdsInsideTheBand(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -3.5));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.5));
        MockPlayers.moveTo(helper, player, FangGameTests.STAND, NORTH);
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        List<Vec3> before = vexes.stream().map(BoundVex::position).toList();

        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    for (int i = 0; i < vexes.size(); i++) {
                        helper.assertTrue(
                                vexes.get(i).position().distanceTo(before.get(i)) <= 0.5,
                                "member " + i + " held its place inside the band");
                    }
                })
                .thenSucceed();
    }
}
