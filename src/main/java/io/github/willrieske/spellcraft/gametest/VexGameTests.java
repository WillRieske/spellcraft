package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.isSummonedBy;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.requireEffect;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.summonedVex;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.withTeam;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.EvokingCaster;
import io.github.willrieske.spellcraft.evoking.EvokingCaster.Request;
import io.github.willrieske.spellcraft.evoking.VexSummons;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import io.github.willrieske.spellcraft.trailing.TrailFires;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;

/**
 * Vex GameTests for Strength of Evoking (spec §9, table E8–E12, E16 and E18's vex halves, plus the prey rule). The
 * summoning tests register through {@code FangGameTests.addPadded} (padding 13), so a vex wandering ±7 blocks around
 * its origin reaches no other test. The two tests that add a scoreboard team run in the {@code team_mutation} batch,
 * as the Trail tests do.
 */
public final class VexGameTests {
    private static final BlockPos FEET = FangGameTests.FEET;
    private static final float NORTH = 180.0F;
    private static final int WARMUP = EvokerSpell.WARMUP_TICKS;
    /**
     * The structure is a 3x3x3 box inside a barrier shell (a wall one block outside it, a ceiling on top), and a
     * barrier blocks a vex's line of sight. So the targeting tests stay inside the box: the vex hovers in its top
     * north-west corner, the prey in the opposite bottom corner (farther than the summoner at {@link #FEET}), and
     * the armor stand under the vex (nearer than the prey).
     */
    private static final BlockPos VEX_SPOT = new BlockPos(0, 2, 0);

    private static final BlockPos PREY_SPOT = new BlockPos(2, 1, 2);
    private static final BlockPos STAND_SPOT = new BlockPos(0, 1, 0);
    /** For the range limit, which needs more room than the box: a vex outside the shell, beyond its north wall. */
    private static final BlockPos OUTSIDE_VEX_SPOT = FEET.north(4);

    private static final int RANGE_WINDOW = 40;

    private VexGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(SHARED, "vexes_e8_three_beside_the_caster", VexGameTests::e8ThreeBesideTheCaster);
        SpellcraftGameTests.add("team_mutation", "vexes_e9_join_the_team", VexGameTests::e9JoinTheTeam);
        FangGameTests.addPadded(SHARED, "vexes_e10_targeting", VexGameTests::e10Targeting);
        FangGameTests.addPadded(SHARED, "vexes_e10_range_limit", VexGameTests::e10RangeLimit);
        FangGameTests.addPadded(SHARED, "vexes_e11_goals_on_join", VexGameTests::e11GoalsOnJoin);
        SpellcraftGameTests.add(SHARED, "vexes_e12_trail_spares_summons", VexGameTests::e12TrailSparesSummons);
        FangGameTests.addPadded(SHARED, "vexes_e16_summoned_in_the_nether", VexGameTests::e16SummonedInTheNether);
        FangGameTests.addPadded(SHARED, "vexes_e18_armor_stand", VexGameTests::e18ArmorStand);
        SpellcraftGameTests.add("team_mutation", "vexes_prey_rule", VexGameTests::preyRule);
        FangGameTests.addPadded(SHARED, "vexes_target_recheck", VexGameTests::targetRecheck);
        // Sets the server difficulty, which is global, so it runs alone in its own batch.
        FangGameTests.addPadded("peaceful", "vexes_peaceful_notice", VexGameTests::peacefulNotice);
    }

    /** Every vex within 14 blocks of {@code center} in {@code level}. */
    static List<Vex> vexesAround(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(Vex.class, new AABB(center).inflate(14.0, 14.0, 14.0));
    }

    static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    /**
     * Discards every vex around the caster's block when the test ends, whether it passes, fails or times out, so no
     * summoned vex flies on into the next batch.
     */
    static void discardLeftoverVexes(GameTestHelper helper) {
        whenDone(
                helper,
                () -> vexesAround(helper.getLevel(), helper.absolutePos(FEET)).forEach(Vex::discard));
    }

    /**
     * Keeps the first of three summoned vexes and parks it at {@code relative}: the others are discarded, and its
     * movement goals are removed so it hovers there while its target goal still runs. Parking fixes every distance
     * in the test, so targeting does not depend on where a wandering vex happens to be.
     */
    static Vex park(GameTestHelper helper, List<Vex> vexes, BlockPos relative) {
        helper.assertValueEqual(vexes.size(), VexRing.SUMMON_COUNT, "three vexes");
        vexes.subList(1, vexes.size()).forEach(Vex::discard);
        Vex vex = vexes.get(0);
        BlockPos at = helper.absolutePos(relative);
        vex.snapTo(at, 0.0F, 0.0F);
        vex.setBoundOrigin(at);
        vex.goalSelector.removeAllGoals(goal -> true);
        // A fresh vex has gravity until its first tick sets noGravity, and the drift would sink it into the floor.
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        return vex;
    }

    /**
     * When a player presses V, should summon three marked vexes in the ring one block up once the warmup has passed,
     * and none before.
     *
     * <p>Method under test: {@link EvokingCaster#request}, then {@link EvokingCaster#tick} and {@link
     * VexSummons#summon}
     */
    static void e8ThreeBesideTheCaster(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        discardLeftoverVexes(helper);

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(request, Request.STARTED, "request");
        MobEffectInstance casting = requireEffect(helper, player, ModEffects.CASTING_VEXES);
        helper.assertValueEqual(casting.getDuration(), EvokerSpell.VEXES.castingTicks(), "casting ticks");
        FangGameTests.step(player);
        helper.assertTrue(
                vexesAround(helper.getLevel(), helper.absolutePos(FEET)).isEmpty(), "none before the warmup");
        helper.runAfterDelay(WARMUP, () -> {
            FangGameTests.step(player);
            BlockPos feet = helper.absolutePos(FEET);
            List<Vex> vexes = vexesAround(helper.getLevel(), feet);
            helper.assertValueEqual(vexes.size(), VexRing.SUMMON_COUNT, "three vexes");
            for (Vex vex : vexes) {
                BlockPos origin = vex.getBoundOrigin();
                if (origin == null) {
                    throw helper.assertionException("a summoned vex has a bound origin");
                }
                helper.assertValueEqual(origin.getY(), feet.getY() + 1, "spawned one block up");
                int distance = chebyshev(origin, feet);
                helper.assertTrue(
                        distance >= VexRing.MIN_DISTANCE && distance <= VexRing.MAX_DISTANCE,
                        "spawned 2-3 blocks away, got " + distance);
                helper.assertTrue(isSummonedBy(vex, player), "marked with the summoner");
                helper.assertValueEqual(
                        vex.getAttributeValue(Attributes.FOLLOW_RANGE), VexSummons.AGGRO_RANGE, "range");
                helper.assertValueEqual(vex.targetSelector.getAvailableGoals().size(), 1, "one target goal");
                // Created in this tick, so no life has run down yet: the roll bounds are exact.
                int life = FangGameTests.savedInt(vex, "life_ticks");
                helper.assertTrue(
                        life >= VexRing.lifeTicks(0) && life <= VexRing.lifeTicks(VexRing.LIFE_ROLL_BOUND - 1),
                        "limited life, got " + life);
            }
            helper.succeed();
        });
    }

    /**
     * When the summoner is on a scoreboard team, should put every summoned vex on that team.
     *
     * <p>Method under test: {@link VexSummons#summon}
     *
     * <p>Puts the summoner on a team, so it runs in the {@code team_mutation} batch. Synchronous on purpose: mock
     * players share one scoreboard name, so the team briefly holds every mock player of the batch and must be gone
     * before the tick ends. Do not add a {@code runAfterDelay}. The body ends in its first tick, so its
     * {@code finally} discards the vexes along with the team.
     */
    static void e9JoinTheTeam(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        withTeam(scoreboard, "spellcraft_vexes_e9", team -> {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);

            // Act
            List<Vex> vexes = VexSummons.summon(helper.getLevel(), player);
            try {
                // Assert
                helper.assertValueEqual(vexes.size(), VexRing.SUMMON_COUNT, "three vexes");
                for (Vex vex : vexes) {
                    helper.assertTrue(team.equals(vex.getTeam()), "on the summoner's team");
                }
            } finally {
                vexes.forEach(Vex::discard);
            }
        });
        helper.succeed();
    }

    /**
     * When a summoned vex hovers nearer to its summoner than to a pig, should take the pig as its target. An
     * unfiltered goal would take the nearer summoner first, so this proves the ally exclusion.
     *
     * <p>Method under test: {@code VexSummons.install} (package-private), through the goal it builds
     */
    static void e10Targeting(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        discardLeftoverVexes(helper);
        Vex vex = park(helper, VexSummons.summon(helper.getLevel(), player), VEX_SPOT);

        // Act
        Pig pig = FangGameTests.pig(helper, PREY_SPOT);

        // Assert
        helper.succeedWhen(() ->
                helper.assertTrue(pig.equals(vex.getTarget()), "the pig, not the nearer summoner, becomes the target"));
    }

    /**
     * When the only other living thing is a pig eight blocks from a summoned vex, should never take it as a target
     * over a 40-tick window (one sampled instant would prove little), and should then take
     * a pig three blocks away. The second half is the control: it shows the vex and its line of sight work out
     * here, so the empty window is down to the range. It proves the range is below 8; the boundary itself is pinned by
     * the effective-value assertions of E8 and E11.
     *
     * <p>Method under test: {@code VexSummons.install} (package-private), through the goal it builds
     */
    static void e10RangeLimit(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        discardLeftoverVexes(helper);
        Vex vex = park(helper, VexSummons.summon(helper.getLevel(), player), OUTSIDE_VEX_SPOT);
        FangGameTests.pig(helper, OUTSIDE_VEX_SPOT.north(8));
        Pig[] near = new Pig[1];

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        RANGE_WINDOW,
                        () -> helper.assertTrue(vex.getTarget() == null, "a pig eight blocks away is never a target"))
                .thenExecute(() -> near[0] = FangGameTests.pig(helper, OUTSIDE_VEX_SPOT.north(3)))
                .thenWaitUntil(() ->
                        helper.assertTrue(near[0].equals(vex.getTarget()), "a pig three blocks away is the target"))
                .thenSucceed();
    }

    /**
     * When a marked vex joins the level, added by hand or reloaded from its save as a chunk load does, should get
     * the summoned goals; an unmarked vex should keep vanilla's three.
     *
     * <p>Method under test: {@link VexSummons#onJoin}
     */
    static void e11GoalsOnJoin(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        discardLeftoverVexes(helper);
        Vex marked = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (marked == null) {
            throw helper.assertionException("could not create a vex");
        }
        marked.snapTo(helper.absolutePos(FEET.north(2)), 0.0F, 0.0F);
        marked.setData(AllyAttachments.SUMMONER, player.getUUID());

        // Act
        level.addFreshEntity(marked);
        Vex vanilla = helper.spawn(EntityTypes.VEX, FEET.north(4));

        // Assert
        helper.assertValueEqual(marked.targetSelector.getAvailableGoals().size(), 1, "one target goal");
        helper.assertValueEqual(marked.getAttributeValue(Attributes.FOLLOW_RANGE), VexSummons.AGGRO_RANGE, "range");
        helper.assertValueEqual(vanilla.targetSelector.getAvailableGoals().size(), 3, "vanilla's three target goals");

        // Act: the save and load round trip (the copy keeps the UUID, so the original goes first)
        if (!(GameTestSupport.reloaded(level, marked) instanceof Vex copy)) {
            throw helper.assertionException("the reloaded copy is not a vex");
        }
        marked.discard();
        helper.assertValueEqual(
                copy.targetSelector.getAvailableGoals().size(),
                3,
                "setup: the copy has vanilla's goals until it joins");
        helper.assertTrue(isSummonedBy(copy, player), "setup: the mark survives the save");
        level.addFreshEntity(copy);

        // Assert
        helper.assertValueEqual(copy.targetSelector.getAvailableGoals().size(), 1, "one target goal after the load");
        helper.assertValueEqual(copy.getAttributeValue(Attributes.FOLLOW_RANGE), VexSummons.AGGRO_RANGE, "range");
        helper.succeed();
    }

    /**
     * When the owner's trail flame reaches the owner's summon and a stranger's, should spare the first and not the
     * second. Vexes are fire immune ({@code EntityTypes.VEX} is {@code fireImmune()}), so they could not show a burn;
     * the decision is tested through the exact call {@code TrailFireBlock} makes.
     *
     * <p>Method under test: {@link TrailFires#isSpared}, through {@link AllyAttachments#SUMMONER}
     */
    static void e12TrailSparesSummons(GameTestHelper helper) {
        // Arrange
        TrailGameTests.floors(helper);
        ServerPlayer owner = TrailGameTests.trailingPlayer(helper);
        TrailGameTests.teleport(helper, owner, TrailGameTests.D);
        helper.assertTrue(TrailGameTests.light(helper, owner, TrailGameTests.A), "lit A");
        Vex mine = summonedVex(helper, TrailGameTests.B, owner.getUUID());
        Vex stranger = summonedVex(helper, TrailGameTests.C, UUID.randomUUID());
        whenDone(helper, () -> {
            TrailGameTests.forget(helper, owner);
            mine.discard();
            stranger.discard();
        });

        // Act
        boolean spareMine =
                TrailGameTests.ledger(helper).isSpared(helper.getLevel(), helper.absolutePos(TrailGameTests.A), mine);
        boolean spareStranger = TrailGameTests.ledger(helper)
                .isSpared(helper.getLevel(), helper.absolutePos(TrailGameTests.A), stranger);

        // Assert
        helper.assertTrue(spareMine, "the flame spares the owner's summon");
        helper.assertFalse(spareStranger, "the flame does not spare a stranger's summon");
        helper.succeed();
    }

    /**
     * When the player presses V in the Overworld and moves to the Nether before the perform, should summon the three
     * vexes beside the player in the Nether and none in the Overworld. {@code toNether} carves a safe pocket above the
     * player's own coordinates, so it cannot overlap {@code fangs_e16_dimension_change}'s, and cleans up the pocket
     * and the player.
     *
     * <p>Method under test: {@link EvokingCaster#tick}, then {@link VexSummons#summon}
     */
    static void e16SummonedInTheNether(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "request");

        // Act
        BlockPos netherFeet = FangGameTests.toNether(helper, player);
        ServerLevel nether = player.level();
        helper.assertValueEqual(nether.dimension(), Level.NETHER, "setup: the player is in the Nether");

        // Assert: poll, stepping each time; the step performs once, when due, and is a no-op after that
        helper.succeedWhen(() -> {
            FangGameTests.step(player);
            helper.assertTrue(
                    vexesAround(helper.getLevel(), helper.absolutePos(FEET)).isEmpty(), "none in the Overworld");
            List<Vex> vexes = vexesAround(nether, netherFeet);
            helper.assertValueEqual(vexes.size(), VexRing.SUMMON_COUNT, "three vexes in the Nether");
        });
    }

    /**
     * When a summoned vex hovers over an armor stand with a pig farther off, should take the pig and never the
     * stand. The goal picks the nearest valid candidate, so reaching the farther pig proves the stand was in range,
     * in sight and refused ({@code attackable()} is the only filter that refuses it).
     *
     * <p>Method under test: {@link EvokingCaster#tick}, then {@link VexSummons#isPrey} through the goal
     */
    static void e18ArmorStand(GameTestHelper helper) {
        // Arrange
        FangGameTests.floor(helper, NORTH);
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        discardLeftoverVexes(helper);
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, STAND_SPOT);

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "request");

        // Assert
        helper.runAfterDelay(WARMUP, () -> {
            FangGameTests.step(player);
            Vex vex = park(helper, vexesAround(helper.getLevel(), helper.absolutePos(FEET)), VEX_SPOT);
            Pig pig = FangGameTests.pig(helper, PREY_SPOT);
            helper.succeedWhen(() -> {
                helper.assertFalse(stand.equals(vex.getTarget()), "the armor stand is never the target");
                helper.assertTrue(pig.equals(vex.getTarget()), "the pig behind it is");
            });
        });
    }

    /**
     * When a summoned vex has chosen an untamed wolf and the summoner then tames it, should drop the target within a
     * few ticks. Vanilla's {@code TargetGoal.canContinueToUse} checks only {@code canAttack}, never the selector, so
     * a plain {@code NearestAttackableTargetGoal} would keep the new ally as its target.
     *
     * <p>Method under test: {@code VexSummons.install} (package-private), through the goal it builds
     */
    static void targetRecheck(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        discardLeftoverVexes(helper);
        Vex vex = park(helper, VexSummons.summon(helper.getLevel(), player), VEX_SPOT);
        Wolf wolf = helper.spawn(EntityTypes.WOLF, PREY_SPOT);
        whenDone(helper, wolf::discard);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(wolf.equals(vex.getTarget()), "setup: the untamed wolf becomes the target"))
                .thenExecute(() -> wolf.tame(player))
                .thenIdle(3)
                .thenExecute(
                        () -> helper.assertTrue(vex.getTarget() == null, "the tamed wolf is dropped as the target"))
                .thenSucceed();
    }

    /**
     * When a player presses V on Peaceful, should send the notice at once, start no cast (no casting effect, no vex
     * after the warmup) and not use up the cooldown: on Normal the same player's next press starts.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     *
     * <p>Sets the server difficulty, which is global, so it runs alone in the {@code peaceful} batch; the difficulty
     * is restored through {@code whenDone}.
     */
    static void peacefulNotice(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        discardLeftoverVexes(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty saved = helper.getLevel().getDifficulty();
        whenDone(helper, () -> server.setDifficulty(saved, true));
        server.setDifficulty(Difficulty.PEACEFUL, true);
        GameTestSupport.drainActionBar(player);

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(request, Request.PEACEFUL, "request");
        GameTestSupport.assertActionBarKey(helper, player, EvokingCaster.PEACEFUL_KEY);
        helper.assertFalse(player.hasEffect(ModEffects.CASTING_VEXES), "no casting effect");
        helper.runAfterDelay(WARMUP, () -> {
            FangGameTests.step(player);
            helper.assertTrue(
                    vexesAround(helper.getLevel(), helper.absolutePos(FEET)).isEmpty(), "no vex was summoned");
            server.setDifficulty(Difficulty.NORMAL, true);
            helper.assertValueEqual(
                    EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "no cooldown was spent");
            helper.succeed();
        });
    }

    /**
     * When a summoned vex weighs a candidate, should call a stranger player prey and spare the summoner's teammate,
     * tamed pet and other summon and an armor stand; while the summoner is offline, nothing is prey.
     *
     * <p>Method under test: {@link VexSummons#isPrey}
     *
     * <p>Puts the summoner on a team, so it runs in the {@code team_mutation} batch. Synchronous on purpose: mock
     * players share one scoreboard name, so the stranger is checked before the team exists.
     */
    static void preyRule(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer summoner = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        UUID id = summoner.getUUID();
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Wolf pet = helper.spawn(EntityTypes.WOLF, FEET);
        pet.tame(summoner);
        Vex otherSummon = GameTestSupport.vexOwnedBy(level, id);
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, FEET);
        Pig pig = FangGameTests.pig(helper, FEET);
        Zombie teammate = helper.spawn(EntityTypes.ZOMBIE, FEET);
        Vex summonedVex = summonedVex(helper, FEET, id);
        Scoreboard scoreboard = level.getScoreboard();

        // Act & Assert
        helper.assertTrue(VexSummons.isPrey(level, id, stranger), "a stranger player is prey");
        helper.assertFalse(VexSummons.isPrey(level, id, pet), "the summoner's tamed pet is not");
        helper.assertFalse(VexSummons.isPrey(level, id, otherSummon), "a vex the summoner owns (vanilla owner) is not");
        helper.assertFalse(
                VexSummons.isPrey(level, id, summonedVex), "a vex carrying the summoner's mark (attachment) is not");
        helper.assertFalse(VexSummons.isPrey(level, id, stand), "an armor stand is not");
        helper.assertTrue(VexSummons.isPrey(level, id, pig), "setup: a pig is prey while the summoner is online");
        helper.assertFalse(
                VexSummons.isPrey(level, UUID.randomUUID(), pig), "nothing is prey while the summoner is offline");
        helper.assertTrue(VexSummons.isPrey(level, id, teammate), "setup: the zombie is prey before the team exists");
        withTeam(scoreboard, "spellcraft_vexes_prey", team -> {
            scoreboard.addPlayerToTeam(summoner.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
            helper.assertFalse(VexSummons.isPrey(level, id, teammate), "the summoner's teammate is not");
        });
        pet.discard();
        stand.discard();
        pig.discard();
        teammate.discard();
        summonedVex.discard();
        helper.succeed();
    }
}
