package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.padded;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.set;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * GameTests of the fireball return in the Nether and the End: a ghast fireball aimed at the summoner is sent
 * back, what it is left alone for, and how it passes through the summoner's own vexes.
 */
public final class BoundVexFireballGameTests {
    /** How far a fireball test's room reaches sideways (a ghast is 4 wide and stands 9 blocks off). */
    private static final int FIREBALL_ROOM_HALF_WIDTH = 12;

    /** How tall a fireball test's room is. */
    private static final int FIREBALL_ROOM_HEIGHT = 8;

    /** How far north of the player the ghast stands, in blocks. */
    private static final double GHAST_DISTANCE = 9.0;

    /** How far north of the player the fireball starts, in blocks. */
    private static final double FIREBALL_DISTANCE = 1.5;

    /** The speed of a fireball in these tests, in blocks per tick. */
    private static final double FIREBALL_SPEED = 1.5;

    /** How far east of the fireball the default vex hangs: well inside the vex reach of 7.25. */
    private static final double VEX_NEAR = 3.0;

    /** How far east of the fireball a vex hangs that is out of the vex reach. */
    private static final double VEX_FAR = 9.0;

    private BoundVexFireballGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_fireball_returned_to_the_ghast", BoundVexFireballGameTests::fireballReturnedToTheGhast);
        padded(
                "bound_vex_world_fireball_reversed_without_a_ghast",
                BoundVexFireballGameTests::fireballReversedWithoutAGhast);
        padded(
                "bound_vex_world_fireball_reversed_when_the_ghast_is_hidden",
                BoundVexFireballGameTests::fireballReversedWhenTheGhastIsHidden);
        padded(
                "bound_vex_world_fireball_block_hit_near_you",
                helper -> fireballHitNearPlayer(helper, floorHit(3.0), Outcome.RETURNED));
        padded(
                "bound_vex_world_fireball_block_hit_far_from_you",
                helper -> fireballHitNearPlayer(helper, floorHit(6.0), Outcome.LEFT_ALONE));
        padded(
                "bound_vex_world_fireball_block_hit_at_eye_height_near_your_body",
                helper -> fireballHitNearPlayer(helper, eyeHeightHit(3.9), Outcome.RETURNED));
        padded(
                "bound_vex_world_fireball_entity_hit_near_you",
                helper -> fireballHitNearPlayer(helper, huskHit(3.0), Outcome.RETURNED));
        padded(
                "bound_vex_world_fireball_entity_hit_far_from_you",
                helper -> fireballHitNearPlayer(helper, huskHit(6.0), Outcome.LEFT_ALONE));
        padded(
                "bound_vex_world_fireball_left_alone_when_you_own_it",
                helper -> fireballLeftAlone(helper, Alone.OWNED_BY_THE_PLAYER));
        padded(
                "bound_vex_world_fireball_left_alone_with_no_vex_near",
                helper -> fireballLeftAlone(helper, Alone.VEX_OUT_OF_REACH));
        padded(
                "bound_vex_world_fireball_left_alone_for_a_spectator",
                helper -> fireballLeftAlone(helper, Alone.SPECTATOR));
        padded(
                "bound_vex_world_fireball_left_alone_in_the_overworld",
                helper -> fireballLeftAlone(helper, Alone.OVERWORLD));
        padded(
                "bound_vex_world_fireball_left_alone_with_a_strangers_vex",
                helper -> fireballLeftAloneWithAStrangersVex(helper, true));
        padded(
                "bound_vex_world_fireball_left_alone_with_an_unmarked_vex",
                helper -> fireballLeftAloneWithAStrangersVex(helper, false));
        padded(
                "bound_vex_world_fireball_passes_through_your_vexes",
                BoundVexFireballGameTests::fireballPassesThroughYourVexes);
        padded(
                "bound_vex_world_fireball_hits_another_players_vex",
                BoundVexFireballGameTests::fireballHitsAnotherPlayersVex);
        padded("bound_vex_world_fireball_of_a_ghast_hits_a_vex", BoundVexFireballGameTests::fireballOfAGhastHitsAVex);
        padded("bound_vex_world_fireball_nearest_vex_acts", BoundVexFireballGameTests::fireballNearestVexActs);
        padded("bound_vex_world_fireball_staying_vex_acts", BoundVexFireballGameTests::fireballStayingVexActs);
        padded("bound_vex_world_fireball_kills_the_ghast", BoundVexFireballGameTests::fireballKillsTheGhast);
    }

    /** A survival player, at {@link FangGameTests#STAND} until a test moves them into a room. */
    private static ServerPlayer fireballPlayer(GameTestHelper helper) {
        return FangGameTests.caster(helper, 0.0F);
    }

    /** The Nether room of a fireball test, with {@code player} standing in it; undone when the test ends. */
    private static ServerLevel fireballRoom(GameTestHelper helper, ServerPlayer player) {
        return BoundVexWorldTestSupport.room(
                helper, player, Level.NETHER, FIREBALL_ROOM_HALF_WIDTH, FIREBALL_ROOM_HEIGHT);
    }

    /**
     * A ghast with no AI and no gravity {@link #GHAST_DISTANCE} blocks north of {@code player}, its body spanning
     * their eye height; discarded when the test ends.
     *
     * @param invulnerable whether nothing can hurt it (only the kill test wants it vulnerable)
     */
    private static Ghast ghastNorthOf(
            GameTestHelper helper, ServerLevel level, ServerPlayer player, boolean invulnerable) {
        Ghast ghast = EntityTypes.GHAST.create(level, EntitySpawnReason.COMMAND);
        if (ghast == null) {
            throw helper.assertionException("could not create a ghast");
        }
        Vec3 at = player.position().add(0.0, 0.5, -GHAST_DISTANCE);
        ghast.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        ghast.setNoAi(true);
        ghast.setNoGravity(true);
        ghast.setPersistenceRequired();
        ghast.setPermanentlyInvulnerable(invulnerable);
        level.addFreshEntity(ghast);
        whenDone(helper, ghast::discard);
        return ghast;
    }

    /** Where a fireball starts: at {@code player}'s eye height, {@link #FIREBALL_DISTANCE} blocks north of them. */
    private static Vec3 fireballSpot(ServerPlayer player) {
        return player.getEyePosition().add(0.0, 0.0, -FIREBALL_DISTANCE);
    }

    /**
     * A fireball of {@code ghast} (with no owner when {@code ghast} is null) at {@code at}, flying {@code motion}. It
     * is not in the level: the tests post its impact by hand.
     */
    private static LargeFireball fireballFlying(
            GameTestHelper helper, ServerLevel level, Ghast ghast, Vec3 at, Vec3 motion) {
        LargeFireball fireball = ghast != null
                ? new LargeFireball(level, ghast, new Vec3(0.0, 0.0, 1.0), 1)
                : EntityTypes.FIREBALL.create(level, EntitySpawnReason.COMMAND);
        if (fireball == null) {
            throw helper.assertionException("could not create a fireball");
        }
        fireball.setPos(at);
        fireball.setDeltaMovement(motion);
        return fireball;
    }

    /** A vex of {@code player} in {@code level} at {@code at}, with no AI and no targets: it stays where it is put. */
    private static BoundVex fireballVex(GameTestHelper helper, ServerLevel level, Vec3 at, ServerPlayer player) {
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, level, at, player));
        vex.setNoAi(true);
        return vex;
    }

    /** Waits until every one of {@code vexes} has ticked (entity queries see it only then), then runs {@code body}. */
    private static void afterVexesTick(GameTestHelper helper, Runnable body, BoundVex... vexes) {
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (BoundVex vex : vexes) {
                        helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks");
                    }
                })
                .thenExecute(body)
                .thenSucceed();
    }

    /** Posts the impact of {@code fireball} on {@code hit} as vanilla would; true means a handler cancelled it. */
    private static boolean impact(LargeFireball fireball, HitResult hit) {
        return EventHooks.onProjectileImpact(fireball, hit);
    }

    /** Fails the test unless {@code fireball} flies {@code (x, y, z)}, within 1e-6. */
    private static void assertFireballVelocity(
            GameTestHelper helper, LargeFireball fireball, double x, double y, double z) {
        Vec3 v = fireball.getDeltaMovement();
        helper.assertTrue(
                Math.abs(v.x - x) < 1.0e-6 && Math.abs(v.y - y) < 1.0e-6 && Math.abs(v.z - z) < 1.0e-6,
                "the fireball flies (" + x + ", " + y + ", " + z + "), not " + v);
    }

    /**
     * When a ghast's fireball is about to hit a player who has a Bound Vex 3 blocks from it and the ghast is in
     * sight, should the impact be cancelled, the summoner own the fireball, its speed stay 1.5, its heading point at
     * the ghast's eyes, and the vex hang at the fireball.
     *
     * <p>Method under test: {@code VexFireballs.onImpact}
     */
    static void fireballReturnedToTheGhast(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Ghast ghast = ghastNorthOf(helper, nether, player, true);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, ghast, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        fireball.accelerationPower = 0.5;
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    boolean cancelled = impact(fireball, new EntityHitResult(player));
                    helper.assertTrue(cancelled, "the impact is cancelled");
                    helper.assertValueEqual(fireball.accelerationPower, 0.1, "returned by an attack: acceleration 0.1");
                    helper.assertTrue(player.equals(fireball.getOwner()), "the summoner owns the fireball");
                    Vec3 velocity = fireball.getDeltaMovement();
                    helper.assertTrue(
                            Math.abs(velocity.length() - FIREBALL_SPEED) < 1.0e-6, "the speed is kept: " + velocity);
                    Vec3 toGhast = ghast.getEyePosition().subtract(fireball.position());
                    helper.assertTrue(
                            velocity.normalize().dot(toGhast.normalize()) > 0.999,
                            "the fireball flies at the ghast's eyes: " + velocity + " vs " + toGhast);
                    helper.assertTrue(
                            vex.position().distanceTo(fireball.position()) < 0.1,
                            "the vex hangs at the fireball, not " + vex.position());
                },
                vex);
    }

    /**
     * When a fireball with no ghast, flying {@code (0.3, 0, 1.2)}, is about to hit a player with a Bound Vex near,
     * should it fly straight back {@code (-0.3, 0, -1.2)} and belong to the summoner.
     *
     * <p>Method under test: {@code VexFireballs.sendBack}: no heading, straight back
     */
    static void fireballReversedWithoutAGhast(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.3, 0.0, 1.2));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(player)), "the impact is cancelled");
                    assertFireballVelocity(helper, fireball, -0.3, 0.0, -1.2);
                    helper.assertTrue(player.equals(fireball.getOwner()), "the summoner owns the fireball");
                },
                vex);
    }

    /**
     * When a stone wall stands between the vex and the ghast, should the fireball fly straight back, not at the ghast.
     *
     * <p>Method under test: {@code VexFireballs.sendBack}: the ghast must be in the vex's sight
     */
    static void fireballReversedWhenTheGhastIsHidden(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Ghast ghast = ghastNorthOf(helper, nether, player, true);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, ghast, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);
        BlockPos wall = BlockPos.containing(player.position().add(0.0, 0.0, -5.0));
        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y < FIREBALL_ROOM_HEIGHT; y++) {
                set(helper, nether, wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
            }
        }

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(player)), "the impact is cancelled");
                    assertFireballVelocity(helper, fireball, 0.0, 0.0, -FIREBALL_SPEED);
                },
                vex);
    }

    /** What a fireball impact should come to. */
    private enum Outcome {
        /** The impact is cancelled and the summoner owns the fireball. */
        RETURNED,
        /** The impact stands: no owner, same velocity. */
        LEFT_ALONE
    }

    /** Makes the hit a fireball test posts; run once the vexes have ticked. */
    @FunctionalInterface
    private interface HitMaker {
        /** The hit, for a player standing in {@code level}. */
        HitResult hit(GameTestHelper helper, ServerLevel level, ServerPlayer player);
    }

    /** A hit on the floor {@code east} blocks east of {@code player}'s feet. */
    private static HitMaker floorHit(double east) {
        return (helper, level, player) -> {
            Vec3 at = player.position().add(east, 0.0, 0.0);
            return new BlockHitResult(at, Direction.UP, BlockPos.containing(at).below(), false);
        };
    }

    /** A hit on a wall {@code east} blocks east of {@code player}, at their eye height. */
    private static HitMaker eyeHeightHit(double east) {
        return (helper, level, player) -> {
            Vec3 at = player.getEyePosition().add(east, 0.0, 0.0);
            return new BlockHitResult(at, Direction.WEST, BlockPos.containing(at), false);
        };
    }

    /** A hit on a husk with no AI standing {@code east} blocks east of {@code player}; discarded at the end. */
    private static HitMaker huskHit(double east) {
        return (helper, level, player) -> {
            Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            if (husk == null) {
                throw helper.assertionException("could not create a husk");
            }
            Vec3 at = player.position().add(east, 0.0, 0.0);
            husk.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            husk.setNoAi(true);
            husk.setNoGravity(true);
            husk.setPersistenceRequired();
            level.addFreshEntity(husk);
            whenDone(helper, husk::discard);
            return new EntityHitResult(husk);
        };
    }

    /**
     * When an ownerless fireball is about to land with the hit {@code maker} makes, near a player with a Bound Vex in
     * reach, should the impact come to {@code outcome}.
     *
     * <p>Method under test: {@code VexFireballs.onImpact}: {@link VexFireballs#NEAR_PLAYER}, measured to the body
     */
    private static void fireballHitNearPlayer(GameTestHelper helper, HitMaker maker, Outcome outcome) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);
        HitResult hit = maker.hit(helper, nether, player);

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    boolean cancelled = impact(fireball, hit);
                    if (outcome == Outcome.RETURNED) {
                        helper.assertTrue(cancelled, "the impact is cancelled");
                        helper.assertTrue(player.equals(fireball.getOwner()), "the summoner owns the fireball");
                    } else {
                        helper.assertTrue(!cancelled, "the impact is not cancelled");
                        helper.assertTrue(fireball.getOwner() == null, "the owner is untouched");
                        assertFireballVelocity(helper, fireball, 0.0, 0.0, FIREBALL_SPEED);
                    }
                },
                vex);
    }

    /**
     * The cases in which a fireball about to hit a player is left alone, whatever else is arranged for a return:
     * the player already owns it, the only vex is out of reach, the player is a spectator, or the level is the
     * Overworld test level.
     */
    private enum Alone {
        /** The player owns the fireball. */
        OWNED_BY_THE_PLAYER,
        /** The only vex is 9 blocks from the fireball. */
        VEX_OUT_OF_REACH,
        /** The player is a spectator. */
        SPECTATOR,
        /** The whole arrangement is in the Overworld test level, with no room. */
        OVERWORLD
    }

    /**
     * When one of the {@link Alone} cases holds, should the impact on the player not be cancelled and the fireball,
     * its owner and the vex stay as they were.
     *
     * <p>Method under test: {@code VexFireballs.onImpact}: the cases it leaves alone
     */
    private static void fireballLeftAlone(GameTestHelper helper, Alone why) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel level = why == Alone.OVERWORLD ? helper.getLevel() : fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, level, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        if (why == Alone.OWNED_BY_THE_PLAYER) {
            fireball.setOwner(player);
        }
        if (why == Alone.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        double away = why == Alone.VEX_OUT_OF_REACH ? VEX_FAR : VEX_NEAR;
        BoundVex vex = fireballVex(helper, level, spot.add(away, 0.0, 0.0), player);
        Vec3 vexBefore = vex.position();

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(!impact(fireball, new EntityHitResult(player)), "the impact is not cancelled");
                    helper.assertTrue(
                            why == Alone.OWNED_BY_THE_PLAYER
                                    ? player.equals(fireball.getOwner())
                                    : fireball.getOwner() == null,
                            "the owner is untouched");
                    assertFireballVelocity(helper, fireball, 0.0, 0.0, FIREBALL_SPEED);
                    helper.assertTrue(vex.position().distanceTo(vexBefore) < 1.0e-6, "the vex did not move");
                },
                vex);
    }

    /**
     * When the only vex in reach belongs to a second player, or carries no summoner mark, should the impact on the
     * first player not be cancelled and the vex stay where it is.
     *
     * <p>Method under test: {@code VexFireballs.nearestVexOf}: the vex must be summoned by that player
     *
     * @param marked true for a vex of a second mock player, false for an unmarked vex
     */
    private static void fireballLeftAloneWithAStrangersVex(GameTestHelper helper, boolean marked) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), stranger);
        if (!marked) {
            vex.removeData(AllyAttachments.SUMMONER);
        }
        Vec3 vexBefore = vex.position();

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(!impact(fireball, new EntityHitResult(player)), "the impact is not cancelled");
                    helper.assertTrue(fireball.getOwner() == null, "the owner is untouched");
                    helper.assertTrue(vex.position().distanceTo(vexBefore) < 1.0e-6, "the vex did not move");
                },
                vex);
    }

    /**
     * When a fireball its summoner owns is about to hit one of that summoner's Bound Vexes, should the impact be
     * cancelled and the fireball keep its owner and velocity (vanilla then flies it on past the vex).
     *
     * <p>Method under test: {@code VexFireballs.strikesOwnersVex}
     */
    static void fireballPassesThroughYourVexes(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        fireball.setOwner(player);
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);
        float health = vex.getHealth();

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(vex)), "the impact is cancelled");
                    helper.assertTrue(player.equals(fireball.getOwner()), "the owner is untouched");
                    assertFireballVelocity(helper, fireball, 0.0, 0.0, FIREBALL_SPEED);
                    helper.assertValueEqual(vex.getHealth(), health, "the vex is unhurt");
                },
                vex);
    }

    /**
     * When a fireball a player owns is about to hit another player's Bound Vex, should the impact stand.
     *
     * <p>Method under test: {@code VexFireballs.strikesOwnersVex}: only the owner's own vexes
     */
    static void fireballHitsAnotherPlayersVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        fireball.setOwner(player);
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), stranger);

        // Act & Assert
        afterVexesTick(
                helper, () -> helper.assertTrue(!impact(fireball, new EntityHitResult(vex)), "the impact stands"), vex);
    }

    /**
     * When a ghast's fireball is about to hit a Bound Vex with no player near it, should the impact stand.
     *
     * <p>Method under test: {@code VexFireballs.strikesOwnersVex}: only a player's fireball passes through
     */
    static void fireballOfAGhastHitsAVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Ghast ghast = ghastNorthOf(helper, nether, player, true);
        Vec3 far = player.position().add(10.0, 1.0, 0.0);
        LargeFireball fireball = fireballFlying(helper, nether, ghast, far, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, far.add(0.0, 0.0, 1.0), player);

        // Act & Assert
        afterVexesTick(
                helper, () -> helper.assertTrue(!impact(fireball, new EntityHitResult(vex)), "the impact stands"), vex);
    }

    /**
     * When two Bound Vexes of a player are 2 and 5 blocks from a fireball about to hit them, should only the nearer
     * one go to the fireball.
     *
     * <p>Method under test: {@code VexFireballs.nearestVexOf}
     */
    static void fireballNearestVexActs(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex nearer = fireballVex(helper, nether, spot.add(-2.0, 0.0, 0.0), player);
        BoundVex farther = fireballVex(helper, nether, spot.add(5.0, 0.0, 0.0), player);
        Vec3 fartherBefore = farther.position();

        // Act & Assert
        afterVexesTick(
                helper,
                () -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(player)), "the impact is cancelled");
                    helper.assertTrue(
                            nearer.position().distanceTo(spot) < 0.1,
                            "the nearer vex hangs at the fireball, not " + nearer.position());
                    helper.assertTrue(
                            farther.position().distanceTo(fartherBefore) < 1.0e-6, "the farther vex did not move");
                },
                nearer,
                farther);
    }

    /**
     * When the only vex in reach is staying at its post, should it still return the fireball and then fly back to
     * within a block of its post point.
     *
     * <p>Method under test: {@code VexFireballs.onImpact}: a staying vex acts too
     */
    static void fireballStayingVexActs(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, null, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = BoundVexTestSupport.noTargets(vexIn(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player));
        vex.setStaying(true);
        Vec3 post = Vec3.atBottomCenterOf(vex.blockPosition());

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    helper.assertTrue(impact(fireball, new EntityHitResult(player)), "the impact is cancelled");
                    helper.assertTrue(
                            vex.position().distanceTo(spot) < 0.1, "the vex hangs at the fireball for the moment");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.position().distanceTo(post) < 1.0, "the vex flew back to its post, not " + vex.position()))
                .thenSucceed();
    }

    /**
     * When a ghast's fireball is returned by a vex and then flies on in the level, should it kill the (vulnerable)
     * ghast with the one hit vanilla gives a fireball whose owner is a player.
     *
     * <p>Method under test: {@code VexFireballs.sendBack}: the summoner becomes the owner
     */
    static void fireballKillsTheGhast(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = fireballPlayer(helper);
        ServerLevel nether = fireballRoom(helper, player);
        Ghast ghast = ghastNorthOf(helper, nether, player, false);
        Vec3 spot = fireballSpot(player);
        LargeFireball fireball = fireballFlying(helper, nether, ghast, spot, new Vec3(0.0, 0.0, FIREBALL_SPEED));
        BoundVex vex = fireballVex(helper, nether, spot.add(VEX_NEAR, 0.0, 0.0), player);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenExecute(() -> {
                    helper.assertTrue(nether.addFreshEntity(fireball), "setup: the fireball joins the level");
                    whenDone(helper, fireball::discard);
                })
                .thenWaitUntil(() -> helper.assertTrue(!ghast.isAlive(), "the returned fireball killed the ghast"))
                .thenSucceed();
    }
}
