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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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

/**
 * GameTests of the Multishot team move (Parts 6-7 spec section 4.2).
 *
 * <p>Functional spec §11.9.
 */
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
                "team_move_multishot_holds_on_its_slot",
                300,
                BoundVexTeamMoveMultishotGameTests::holdsOnItsSlot);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_flies_back_from_far_above_its_slot",
                300,
                BoundVexTeamMoveMultishotGameTests::fliesBackFromFarAboveItsSlot);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_in_line_members_separate_and_keep_firing",
                300,
                BoundVexTeamMoveMultishotGameTests::inLineMembersSeparateAndKeepFiring);
        FangGameTests.addPadded(
                SHARED,
                "team_move_multishot_fires_past_a_fellow_member",
                200,
                BoundVexTeamMoveMultishotGameTests::firesPastAFellowMember);
    }

    /** The arrows in flight around the hang point that one of {@code vexes} owns (a neighbor's arrows are not ours). */
    private static List<AbstractArrow> volleyArrows(GameTestHelper helper, List<BoundVex> vexes) {
        Set<UUID> ours = vexes.stream().map(Entity::getUUID).collect(Collectors.toSet());
        return helper.getLevel()
                .getEntitiesOfClass(
                        AbstractArrow.class,
                        new AABB(hangPoint(helper), hangPoint(helper)).inflate(16.0),
                        arrow -> arrow.getOwner() != null
                                && ours.contains(arrow.getOwner().getUUID()));
    }

    /** One arrow as it was first seen: its velocity and whether anybody can pick it up. */
    private record Shot(Vec3 velocity, AbstractArrow.Pickup pickup) {}

    /**
     * Records, every tick, each of {@code vexes}' arrows once (arrows are told apart by UUID): per owner, the game time
     * it first fired ({@code firstTick}) and the arrows of that first volley as {@link Shot}s ({@code firstVolley};
     * a volley's three arrows appear in one tick).
     */
    private static void recordFirstVolleys(
            GameTestHelper helper, List<BoundVex> vexes, Map<UUID, Long> firstTick, Map<UUID, List<Shot>> firstVolley) {
        Set<UUID> seen = new HashSet<>();
        BoundVexTestSupport.everyTick(helper, () -> {
            long now = helper.getLevel().getGameTime();
            for (AbstractArrow arrow : volleyArrows(helper, vexes)) {
                if (seen.add(arrow.getUUID())) {
                    UUID owner = arrow.getOwner().getUUID();
                    if (firstTick.putIfAbsent(owner, now) == null || firstTick.get(owner) == now) {
                        firstVolley
                                .computeIfAbsent(owner, id -> new ArrayList<>())
                                .add(new Shot(arrow.getDeltaMovement(), arrow.pickup));
                    }
                }
            }
        });
    }

    /** The angle in degrees between the velocities of two arrows. */
    private static double angleBetween(Shot a, Shot b) {
        double cos = a.velocity().normalize().dot(b.velocity().normalize());
        return Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0)));
    }

    /**
     * When three vexes start seven blocks from a husk, should every shooter's first volley be exactly three arrows
     * owned by that vex, not pickable, spread over about 20 degrees, and leave its crossbow unharmed and empty.
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
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper, vexes).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        Map<UUID, Long> firstTick = new HashMap<>();
        Map<UUID, List<Shot>> firstVolley = new HashMap<>();
        recordFirstVolleys(helper, vexes, firstTick, firstVolley);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(firstVolley.size(), 3, "every member has fired"))
                .thenExecute(() -> {
                    firstVolley.forEach((shooter, shots) -> {
                        helper.assertValueEqual(shots.size(), 3, "three arrows from one crossbow");
                        shots.forEach(shot -> helper.assertValueEqual(
                                shot.pickup(), AbstractArrow.Pickup.DISALLOWED, "an arrow nobody can pick up"));
                        double widest = 0.0;
                        for (int i = 0; i < shots.size(); i++) {
                            for (int j = i + 1; j < shots.size(); j++) {
                                widest = Math.max(widest, angleBetween(shots.get(i), shots.get(j)));
                            }
                        }
                        // 20 degrees of fan, plus noise of a few degrees (a vex's inaccuracy is 14 minus 4 a
                        // difficulty)
                        helper.assertTrue(
                                widest >= 6.0 && widest <= 34.0, "spread over about 20 degrees, not " + widest);
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
        // (the summoner is 1.5 blocks from the husk: there all three arc slots' lines pass within a block of it)
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -1.5));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 5.5));
        MockPlayers.moveTo(helper, player, Vec3.atBottomCenterOf(BoundVexTestSupport.AIR_SPOT), NORTH); // on the line
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper, vexes).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        60,
                        () -> helper.assertTrue(volleyArrows(helper, vexes).isEmpty(), "nobody shoots past a friend"))
                .thenExecute(() -> MockPlayers.moveTo(helper, player, FangGameTests.STAND, NORTH))
                .thenWaitUntil(() -> helper.assertFalse(
                        volleyArrows(helper, vexes).isEmpty(), "a volley flies once the way is clear"))
                .thenSucceed();
    }

    /**
     * When three vexes start twelve blocks from a husk in a row one block apart, should each of the three have fired a
     * first volley by 20 ticks after all three are in the 6 to 8 block band (the bound is one-sided: a member may have
     * fired long before, and the wait is on the condition, not on a tick count).
     *
     * <p>Method under test: {@code MultishotMove.tick}: the arc slots, the roster ignored as friends in the way
     */
    static void allThreeFireFromTheirSlots(GameTestHelper helper) {
        // Arrange (twelve blocks is outside the band, so the members travel to their slots)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -6.0));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 6.0));
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper, vexes).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        Map<UUID, Long> firstTick = new HashMap<>();
        recordFirstVolleys(helper, vexes, firstTick, new HashMap<>());
        AtomicLong arrived = new AtomicLong(-1L);
        BoundVexTestSupport.everyTick(helper, () -> {
            if (arrived.get() < 0
                    && vexes.stream().allMatch(vex -> vex.distanceTo(husk) >= 5.5 && vex.distanceTo(husk) <= 8.5)) {
                arrived.set(helper.getLevel().getGameTime());
            }
        });

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        arrived.get() >= 0 && firstTick.size() == 3, "every member is in the band and has fired"))
                .thenExecute(() -> firstTick.forEach((owner, tick) -> helper.assertTrue(
                        tick - arrived.get() <= 20,
                        "a member's first volley came " + (tick - arrived.get()) + " ticks after all arrived")))
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
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.0));
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper, vexes).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        Map<UUID, List<Long>> volleys = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        BoundVexTestSupport.everyTick(helper, () -> {
            long now = helper.getLevel().getGameTime();
            for (AbstractArrow arrow : volleyArrows(helper, vexes)) {
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
     * Counts, every tick, how many ticks in a row none of {@code vexes} has moved more than 0.02 blocks sideways
     * (up and down is left out: a vex bobs by itself).
     */
    private static AtomicInteger countStillTicks(GameTestHelper helper, List<BoundVex> vexes) {
        List<Vec3> last = new ArrayList<>(vexes.stream().map(Entity::position).toList());
        AtomicInteger stillTicks = new AtomicInteger();
        BoundVexTestSupport.everyTick(helper, () -> {
            boolean still = true;
            for (int i = 0; i < vexes.size(); i++) {
                still &= vexes.get(i).position().subtract(last.get(i)).horizontalDistance() <= 0.02;
                last.set(i, vexes.get(i).position());
            }
            stillTicks.set(still ? stillTicks.get() + 1 : 0);
        });
        return stillTicks;
    }

    /**
     * When the vexes have settled on their arc slots (none moved more than 0.02 blocks sideways in a tick for 20
     * ticks running; a member coasts for a while after each flight), should no member move sideways more than half a block in the next 20 ticks (a member within a
     * block of its slot holds; it bobs up and down by itself, so only the sideways movement is measured).
     *
     * <p>Method under test: {@code MultishotMove.tick}: the hold within {@code SLOT_TOLERANCE}
     */
    static void holdsOnItsSlot(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -3.5));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.5));
        MockPlayers.moveTo(helper, player, FangGameTests.STAND, NORTH);
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper, vexes).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        AtomicInteger stillTicks = countStillTicks(helper, vexes);
        List<Vec3> before = new ArrayList<>();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stillTicks.get() >= 20, "the members have settled"))
                .thenExecute(() -> vexes.forEach(vex -> before.add(vex.position())))
                .thenExecuteAfter(20, () -> {
                    for (int i = 0; i < vexes.size(); i++) {
                        helper.assertTrue(
                                vexes.get(i).position().subtract(before.get(i)).horizontalDistance() <= 0.5,
                                "member " + i + " held its slot");
                    }
                })
                .thenSucceed();
    }

    /**
     * When a settled member is lifted eight blocks straight up (still on its slot sideways), should it fly back down to
     * within three blocks of its slot height within 40 ticks (the vex sinks only slowly by itself, so a member that
     * does not fly back would still be above that after 40 ticks).
     *
     * <p>Method under test: {@code MultishotMove.tick}: the vertical slot tolerance
     */
    static void fliesBackFromFarAboveItsSlot(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -3.5));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.5));
        MockPlayers.moveTo(helper, player, FangGameTests.STAND, NORTH);
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper, vexes).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        AtomicInteger stillTicks = countStillTicks(helper, vexes);
        BoundVex lifted = vexes.get(0);
        AtomicReference<Double> slotY = new AtomicReference<>();
        AtomicLong liftedAt = new AtomicLong();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stillTicks.get() >= 20, "the members have settled"))
                .thenExecute(() -> {
                    slotY.set(lifted.getY());
                    lifted.setPos(lifted.getX(), lifted.getY() + 8.0, lifted.getZ());
                    liftedAt.set(helper.getTick());
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        Math.abs(lifted.getY() - slotY.get()) <= 3.0, "the lifted member flies back toward its slot"))
                .thenExecute(() -> helper.assertTrue(
                        helper.getTick() - liftedAt.get() <= 40,
                        "and does so quickly, not in " + (helper.getTick() - liftedAt.get()) + " ticks"))
                .thenSucceed();
    }

    /**
     * When two members start in the band one behind the other on the same line to the husk, should they end up at
     * least a block and a half apart (each flies to its own arc slot, not only when outside the band), and should all
     * three fire a fresh volley within 45 ticks once they have settled.
     *
     * <p>Method under test: {@code MultishotMove.tick}: the arc slots; the roster ignored as friends in the way
     */
    static void inLineMembersSeparateAndKeepFiring(GameTestHelper helper) {
        // Arrange (the husk is at the origin of the line; the members at 6.5 and 7.5 blocks are both in the band)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -6.5));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang);
        Vec3 eye = husk.getEyePosition();
        vexes.get(0).setPos(eye.x, eye.y, husk.getZ() + 6.5);
        vexes.get(1).setPos(eye.x, eye.y, husk.getZ() + 7.5);
        vexes.get(2).setPos(eye.x + 6.0, eye.y, husk.getZ() + 3.0);
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper, vexes).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        Set<UUID> settledArrows = new HashSet<>();
        Set<UUID> fresh = new HashSet<>();

        // Act & Assert
        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    for (int i = 0; i < vexes.size(); i++) {
                        for (int j = i + 1; j < vexes.size(); j++) {
                            helper.assertTrue(
                                    vexes.get(i).distanceTo(vexes.get(j)) >= 1.5,
                                    "members " + i + " and " + j + " are on slots of their own");
                        }
                    }
                    volleyArrows(helper, vexes).forEach(arrow -> settledArrows.add(arrow.getUUID()));
                })
                .thenExecuteFor(
                        45,
                        () -> volleyArrows(helper, vexes).stream()
                                .filter(arrow -> !settledArrows.contains(arrow.getUUID()))
                                .forEach(arrow -> fresh.add(arrow.getOwner().getUUID())))
                .thenExecute(() -> helper.assertValueEqual(fresh.size(), 3, "all three fired a fresh volley"))
                .thenSucceed();
    }

    /**
     * When a fellow roster member stands on the line from a shooter to the husk, should the shooter still fire (a
     * roster member is not a friend in the way; the control for {@link #holdsFireForAFriend}, where the summoner is).
     *
     * <p>Method under test: {@code MultishotMove.tick}: {@code VexShots.friendInTheWay} with the roster ignored
     */
    static void firesPastAFellowMember(GameTestHelper helper) {
        // Arrange (everything above the barrier shell)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        Vec3 hang = hangPoint(helper);
        Husk husk = bigHusk(helper, hang.add(0.0, 0.0, -4.0));
        husk.setPermanentlyInvulnerable(true);
        List<BoundVex> vexes = flyingVexes(helper, player, 3, hang.add(0.0, 0.0, 3.0));
        MockPlayers.moveTo(helper, player, FangGameTests.STAND, NORTH);
        GameTestSupport.whenDone(helper, () -> volleyArrows(helper, vexes).forEach(Entity::discard));
        helper.assertTrue(TeamMoves.start(player, TeamMove.MULTISHOT, husk), "setup: the Multishot starts");
        TeamMoveTestSupport.stepEveryTick(helper, player);
        List<BoundVex> roster = TeamMoves.roster(player);
        BoundVex shooter = roster.get(0);
        BoundVex fellow = roster.get(1);
        fellow.setNoAi(true);
        BoundVex third = roster.get(2);
        third.setNoAi(true);
        third.setPos(third.position().add(0.0, 6.0, 0.0));
        // The fellow member is held on the middle of the line from the shooter's eyes to the husk, every tick
        Runnable hold = () -> fellow.setPos(shooter.getEyePosition()
                .add(new Vec3(husk.getX(), husk.getY(1.0 / 3.0), husk.getZ()))
                .scale(0.5)
                .subtract(0.0, fellow.getBbHeight() / 2.0, 0.0));
        hold.run();
        BoundVexTestSupport.everyTick(helper, hold);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        volleyArrows(helper, vexes).stream()
                                .anyMatch(arrow -> arrow.getOwner().equals(shooter)),
                        "the shooter fires past its fellow member"))
                .thenSucceed();
    }
}
