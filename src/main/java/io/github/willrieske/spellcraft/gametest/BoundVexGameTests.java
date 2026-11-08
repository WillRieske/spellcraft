package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.EvokingCaster;
import io.github.willrieske.spellcraft.evoking.EvokingCaster.Request;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.evoking.VexSummons;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.VexCap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Bound Vex GameTests (Evoking II spec §3, §11 "Part 2"). Tests whose vex flies or teleports, that need headroom, or
 * that park many vexes register through {@code FangGameTests.addPadded} (padding 13); the rest run inside the shared
 * 3×3×3 box, where the barrier shell blocks line of sight, so a targeting test keeps the vex and its prey both inside
 * the box. Hostiles are husks (they do not burn in daylight).
 */
public final class BoundVexGameTests {
    /** The yaw a caster faces; {@code FangGameTests.caster} takes it. */
    static final float NORTH = 180.0F;

    private static final int WARMUP = EvokerSpell.WARMUP_TICKS;

    /** The box's top north-west corner: where a test parks the vex. */
    static final BlockPos VEX_SPOT = new BlockPos(0, 2, 0);

    /** The box's opposite bottom corner: farther from {@link #VEX_SPOT} than the summoner standing at the box's middle. */
    static final BlockPos PREY_SPOT = new BlockPos(2, 1, 2);

    private BoundVexGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(SHARED, "bound_vex_cap_refuses_at_twelve", BoundVexGameTests::capRefusesAtTwelve);
        FangGameTests.addPadded(
                SHARED, "bound_vex_cap_tops_up_from_ten", helper -> capTopsUpToTwelve(helper, VexCap.CAP - 2));
        FangGameTests.addPadded(
                SHARED, "bound_vex_cap_tops_up_from_eleven", helper -> capTopsUpToTwelve(helper, VexCap.CAP - 1));
        FangGameTests.addPadded(SHARED, "bound_vex_cap_is_per_summoner", BoundVexGameTests::capIsPerSummoner);
        FangGameTests.addPadded(SHARED, "bound_vex_cap_counts_every_level", BoundVexGameTests::capCountsEveryLevel);
        SpellcraftGameTests.add(SHARED, "bound_vex_stay_toggle", BoundVexGameTests::stayToggle);
        SpellcraftGameTests.add(SHARED, "bound_vex_idles_without_a_summoner", BoundVexGameTests::idlesWithoutASummoner);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_idles_with_a_dead_summoner", BoundVexGameTests::idlesWithADeadSummoner);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_idles_with_the_summoner_elsewhere",
                BoundVexGameTests::idlesWithTheSummonerElsewhere);
        SpellcraftGameTests.add(SHARED, "bound_vex_save_keeps_stay_and_mark", BoundVexGameTests::saveKeepsStayAndMark);
        FangGameTests.addPadded(SHARED, "bound_vex_guard_area_edges", BoundVexGameTests::guardAreaEdges);
    }

    /**
     * A Bound Vex of {@code summoner} at {@code relative} with its full AI and the iron sword ({@code finalizeSpawn}
     * runs because a spawn reason is given), discarded when the test ends. Not through {@code VexSummons.summon}: the
     * test chooses where it hovers.
     *
     * @param helper the test helper
     * @param relative where to spawn it, relative to the structure
     * @param summoner the player it counts as summoned by
     * @return the vex
     */
    static BoundVex boundVex(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), relative, EntitySpawnReason.MOB_SUMMONED);
        vex.setData(AllyAttachments.SUMMONER, summoner.getUUID());
        vex.setBoundOrigin(helper.absolutePos(relative));
        // A fresh vex has gravity until its first tick sets noGravity, and the drift would sink it into the floor.
        vex.setNoGravity(true);
        vex.setDeltaMovement(Vec3.ZERO);
        whenDone(helper, vex::discard);
        return vex;
    }

    /**
     * {@link #boundVex} with its movement goals removed, so it hovers where it was put while its target goals still
     * run. Parking fixes every distance in a test.
     *
     * @param helper the test helper
     * @param relative where to park it
     * @param summoner the player it counts as summoned by
     * @return the parked vex
     */
    static BoundVex parked(GameTestHelper helper, BlockPos relative, ServerPlayer summoner) {
        BoundVex vex = boundVex(helper, relative, summoner);
        vex.goalSelector.removeAllGoals(goal -> true);
        return vex;
    }

    /**
     * A husk (a hostile that does not burn in daylight) with no AI, discarded when the test ends.
     *
     * @param helper the test helper
     * @param relative where to spawn it
     * @return the husk
     */
    static Husk husk(GameTestHelper helper, BlockPos relative) {
        Husk husk = helper.spawn(EntityTypes.HUSK, relative);
        husk.setNoAi(true);
        whenDone(helper, husk::discard);
        return husk;
    }

    /** A survival player with Evoking inside the shared box (no padding wait). */
    private static ServerPlayer summoner(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(EvokingGameTests.evoking());
        MockPlayers.moveIntoTest(helper, player);
        return player;
    }

    /**
     * When the summoner already has twelve Bound Vexes alive, should answer CAPPED with the too-many notice, start no
     * cast and spend no cooldown: with the vexes gone, the same press starts. Padded: twelve vexes in the shared box
     * would sit near the edge of a neighbor's vex search.
     *
     * <p>Method under test: {@link EvokingCaster#request}, through {@link BoundVex#countOwnedBy} and
     * {@link VexCap#toSummon}
     */
    static void capRefusesAtTwelve(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        MinecraftServer server = helper.getLevel().getServer();
        List<BoundVex> vexes = new ArrayList<>();
        for (int i = 0; i < VexCap.CAP; i++) {
            vexes.add(parked(helper, VEX_SPOT, player));
        }
        GameTestSupport.drainActionBar(player);

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(request, Request.CAPPED, "request at the cap");
        GameTestSupport.assertActionBarKey(helper, player, EvokingCaster.TOO_MANY_KEY);
        helper.assertFalse(player.hasEffect(ModEffects.CASTING_VEXES), "no casting effect");
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), VexCap.CAP, "twelve counted");

        // Act: the vexes go, and the same press starts, so no cooldown was spent
        vexes.forEach(Entity::discard);
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 0, "none counted once gone");
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "no cooldown spent");
        helper.succeed();
    }

    /**
     * When the summoner has ten (or eleven) Bound Vexes alive, should start the cast and summon two (or one), for
     * twelve in all: the remainder, not a whole batch.
     *
     * <p>Method under test: {@link EvokingCaster#request} then {@link EvokingCaster#tick}, through
     * {@code VexSummons.summon(level, player, count)}
     */
    static void capTopsUpToTwelve(GameTestHelper helper, int alive) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        for (int i = 0; i < alive; i++) {
            parked(helper, VEX_SPOT, player);
        }

        // Act
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "request with room");

        // Assert
        helper.runAfterDelay(WARMUP, () -> {
            FangGameTests.step(player);
            helper.assertValueEqual(
                    BoundVex.countOwnedBy(helper.getLevel().getServer(), player.getUUID()),
                    VexCap.CAP,
                    "topped up from " + alive + " to twelve");
            helper.succeed();
        });
    }

    /**
     * When another player has twelve Bound Vexes alive and an unmarked Bound Vex ({@code /summon}) is near, should count
     * none of them for this summoner, whose press starts.
     *
     * <p>Method under test: {@link BoundVex#countOwnedBy}, through {@link EvokingCaster#request}
     */
    static void capIsPerSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer stranger = summoner(helper);
        for (int i = 0; i < VexCap.CAP; i++) {
            parked(helper, VEX_SPOT, stranger);
        }
        BoundVex unmarked = helper.spawn(ModEntities.BOUND_VEX.get(), VEX_SPOT);
        unmarked.goalSelector.removeAllGoals(goal -> true);
        whenDone(helper, unmarked::discard);
        helper.assertValueEqual(
                BoundVex.countOwnedBy(server, stranger.getUUID()), VexCap.CAP, "setup: stranger capped");

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 0, "nobody else's vexes counted");
        helper.assertValueEqual(request, Request.STARTED, "this summoner's press starts");
        helper.succeed();
    }

    /**
     * When the summoner has one Bound Vex in the Nether and eleven here, should count twelve and answer CAPPED: the
     * count spans every loaded level (spec §3.2). A count of this level alone would see eleven and start.
     *
     * <p>Method under test: {@link BoundVex#countOwnedBy}, through {@link EvokingCaster#request}
     */
    static void capCountsEveryLevel(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        FangGameTests.toNether(helper, player);
        List<BoundVex> inNether = VexSummons.summon(player.level(), player, 1);
        inNether.forEach(vex -> whenDone(helper, vex::discard));
        helper.assertValueEqual(inNether.size(), 1, "setup: one Bound Vex in the Nether");
        MockPlayers.moveIntoTest(helper, player);
        helper.assertValueEqual(player.level().dimension(), helper.getLevel().dimension(), "setup: summoner back");
        for (int i = 0; i < VexCap.CAP - 1; i++) {
            parked(helper, VEX_SPOT, player);
        }

        // Act & Assert: polled, because a vex in a just-forced Nether chunk is not in the level's entity lookup until
        // the chunk's ticket has taken effect a tick or two later (the count is of loaded levels, spec §13). The
        // count assertion fails first, so the request runs only once the Nether vex is counted.
        helper.succeedWhen(() -> {
            helper.assertValueEqual(
                    BoundVex.countOwnedBy(helper.getLevel().getServer(), player.getUUID()), VexCap.CAP, "both levels");
            helper.assertValueEqual(
                    EvokingCaster.request(player, EvokerSpell.VEXES), Request.CAPPED, "capped by the Nether vex");
        });
    }

    /**
     * When the summoner clicks a Bound Vex with an empty main hand, should toggle stay on (the post is the block it
     * stands at, whatever origin it had before) and off again, each handled by the server, the post kept when stay goes
     * off; a stranger's click, a sneaking click and a click with an item in hand should pass and change nothing.
     *
     * <p>Method under test: {@code BoundVex.mobInteract} (protected), through {@code Mob.interact}
     */
    static void stayToggle(GameTestHelper helper) {
        // Arrange
        ServerPlayer summoner = summoner(helper);
        ServerPlayer stranger = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, summoner);
        BlockPos post = helper.absolutePos(VEX_SPOT);
        vex.setBoundOrigin(post.above(3)); // another origin, so only the click can make it the post

        // Act & Assert
        helper.assertFalse(
                vex.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a stranger's click passes");
        helper.assertFalse(vex.isStaying(), "still following after a stranger's click");
        summoner.setShiftKeyDown(true);
        helper.assertFalse(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a sneaking click passes");
        summoner.setShiftKeyDown(false);
        GameTestSupport.hold(summoner, new ItemStack(Items.STICK));
        helper.assertFalse(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a click with an item passes");
        GameTestSupport.hold(summoner, ItemStack.EMPTY);
        helper.assertFalse(vex.isStaying(), "setup: following before the real click");
        helper.assertValueEqual(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO),
                InteractionResult.SUCCESS_SERVER,
                "the summoner's empty click is handled by the server");
        helper.assertTrue(vex.isStaying(), "staying");
        helper.assertValueEqual(vex.getBoundOrigin(), post, "the post is where it stood");
        helper.assertValueEqual(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO),
                InteractionResult.SUCCESS_SERVER,
                "a second click is handled too");
        helper.assertFalse(vex.isStaying(), "following again");
        helper.assertValueEqual(vex.getBoundOrigin(), post, "switching stay off leaves the post");
        helper.succeed();
    }

    /**
     * When a Bound Vex has no summoner mark at all (as {@code /summon} makes one), should idle: no summoner, no prey, a
     * click passes, and it is never removed for being far away.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)},
     * {@link BoundVex#removeWhenFarAway}, {@link BoundVex#requiresCustomPersistence}
     */
    static void idlesWithoutASummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = helper.spawn(ModEntities.BOUND_VEX.get(), VEX_SPOT);
        vex.goalSelector.removeAllGoals(goal -> true);
        whenDone(helper, vex::discard);

        // Act & Assert
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner");
        helper.assertFalse(vex.isPrey(player), "nothing is prey without a summoner");
        helper.assertFalse(
                vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "a click passes");
        helper.assertFalse(vex.removeWhenFarAway(1.0E6), "never removed for distance");
        helper.assertTrue(vex.requiresCustomPersistence(), "persists on its own");
        helper.succeed();
    }

    /**
     * When a staying Bound Vex is saved and loaded the way a chunk save does, should still be staying and still carry
     * its summoner mark, and should save no life limit.
     *
     * <p>Method under test: {@code BoundVex.addAdditionalSaveData}/{@code readAdditionalSaveData} (protected), through
     * {@link GameTestSupport#reloaded}
     */
    static void saveKeepsStayAndMark(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setStaying(true);

        // Act
        if (!(GameTestSupport.reloaded(helper.getLevel(), vex) instanceof BoundVex copy)) {
            throw helper.assertionException("the reloaded copy is not a Bound Vex");
        }

        // Assert
        helper.assertTrue(copy.isStaying(), "staying survives the save");
        helper.assertTrue(copy.isSummonedBy(player.getUUID()), "the mark survives the save");
        helper.assertValueEqual(FangGameTests.savedInt(vex, "life_ticks"), Integer.MIN_VALUE, "no life limit saved");
        helper.succeed();
    }

    /**
     * When a marked Bound Vex's summoner dies, should idle as if the summoner were offline (spec §10, "as offline until
     * respawn"): no summoner, and a husk that was prey a moment ago no longer is.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}
     */
    static void idlesWithADeadSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        helper.assertTrue(vex.isPrey(husk), "setup: a husk is prey while the summoner lives");

        // Act
        player.kill(helper.getLevel());

        // Assert
        helper.assertFalse(player.isAlive(), "setup: the summoner is dead");
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner while dead");
        helper.assertFalse(vex.isPrey(husk), "nothing is prey while the summoner is dead");
        helper.succeed();
    }

    /**
     * When a marked Bound Vex's summoner goes to another dimension, should idle (spec §3.1: the summoner must be in the
     * vex's level): no summoner, and a husk that was prey a moment ago no longer is.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}
     */
    static void idlesWithTheSummonerElsewhere(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        helper.assertTrue(vex.isPrey(husk), "setup: a husk is prey while the summoner is here");

        // Act
        FangGameTests.toNether(helper, player);

        // Assert
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner while it is in the Nether");
        helper.assertFalse(vex.isPrey(husk), "nothing is prey while the summoner is elsewhere");
        helper.succeed();
    }

    /**
     * When another entity's feet are exactly on a face of the 8×8×7 guard box, should count it inside; half a block
     * past the face (or past the top), outside. The goals of Tasks 5 and 6 all ask this one method.
     *
     * <p>Method under test: {@link BoundVex#inGuardArea}
     */
    static void guardAreaEdges(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        Vec3 at = vex.position();

        // Act & Assert
        husk.setPos(at.add(4.0, 0.0, 0.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the east face: inside");
        husk.setPos(at.add(4.5, 0.0, 0.0));
        helper.assertFalse(vex.inGuardArea(husk), "half a block east of it: outside");
        husk.setPos(at.add(0.0, 0.0, -4.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the north face: inside");
        husk.setPos(at.add(0.0, 0.0, -4.5));
        helper.assertFalse(vex.inGuardArea(husk), "half a block north of it: outside");
        husk.setPos(at.add(0.0, 3.5, 0.0));
        helper.assertTrue(vex.inGuardArea(husk), "on the top face: inside");
        husk.setPos(at.add(0.0, 4.0, 0.0));
        helper.assertFalse(vex.inGuardArea(husk), "half a block above it: outside");
        husk.setPos(at.add(4.0, -3.5, 4.0));
        helper.assertTrue(vex.inGuardArea(husk), "a bottom corner: inside");
        helper.succeed();
    }
}
