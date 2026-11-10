package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.PREY_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.VEX_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.boundVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.husk;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.parked;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.unmarkedVex;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.EvokingCaster;
import io.github.willrieske.spellcraft.evoking.EvokingCaster.Request;
import io.github.willrieske.spellcraft.evoking.VexSummons;
import io.github.willrieske.spellcraft.spell.rules.EvokerSpell;
import io.github.willrieske.spellcraft.spell.rules.VexCap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Bound Vex GameTests (Evoking II spec section 3, section 11 "Part 2"), cap and stay area: the twelve-vex cap,
 * the stay toggle, the idle and summoner rules, saving, the guard-area edges and experience. Tests whose vex
 * flies or teleports, that need headroom, or that park many vexes register through {@code
 * FangGameTests.addPadded} (padding 13); the rest run inside the shared 3x3x3 box. Shared constants and helpers
 * live in {@link BoundVexTestSupport}.
 */
public final class BoundVexCapGameTests {
    private static final int WARMUP = EvokerSpell.WARMUP_TICKS;

    private BoundVexCapGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(SHARED, "bound_vex_cap_refuses_at_twelve", BoundVexCapGameTests::capRefusesAtTwelve);
        FangGameTests.addPadded(
                SHARED, "bound_vex_cap_tops_up_from_ten", helper -> capTopsUpToTwelve(helper, VexCap.CAP - 2));
        FangGameTests.addPadded(
                SHARED, "bound_vex_cap_tops_up_from_eleven", helper -> capTopsUpToTwelve(helper, VexCap.CAP - 1));
        FangGameTests.addPadded(SHARED, "bound_vex_cap_is_per_summoner", BoundVexCapGameTests::capIsPerSummoner);
        FangGameTests.addPadded(SHARED, "bound_vex_cap_counts_every_level", BoundVexCapGameTests::capCountsEveryLevel);
        SpellcraftGameTests.add(SHARED, "bound_vex_gives_no_experience", BoundVexCapGameTests::givesNoExperience);
        SpellcraftGameTests.add(SHARED, "bound_vex_stay_toggle", BoundVexCapGameTests::stayToggle);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_stay_toggle_keeps_the_off_hand_item",
                BoundVexCapGameTests::stayToggleKeepsTheOffHandItem);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_idles_without_a_summoner", BoundVexCapGameTests::idlesWithoutASummoner);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_idles_with_a_dead_summoner", BoundVexCapGameTests::idlesWithADeadSummoner);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_idles_with_the_summoner_elsewhere",
                BoundVexCapGameTests::idlesWithTheSummonerElsewhere);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_save_keeps_stay_and_mark", BoundVexCapGameTests::saveKeepsStayAndMark);
        FangGameTests.addPadded(SHARED, "bound_vex_guard_area_edges", BoundVexCapGameTests::guardAreaEdges);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_idles_with_a_spectating_summoner",
                BoundVexCapGameTests::idlesWithASpectatingSummoner);
        SpellcraftGameTests.add(SHARED, "bound_vex_count_skips_a_dying_vex", BoundVexCapGameTests::countSkipsADyingVex);
        FangGameTests.addPadded(SHARED, "bound_vex_cap_refusal_is_silent", BoundVexCapGameTests::capRefusalIsSilent);
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
        cancelCast(player);
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
     * When another player has twelve Bound Vexes alive and an unmarked Bound Vex ({@code /summon}) is near, should
     * count none of them for this summoner, whose press starts.
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
        unmarkedVex(helper, VEX_SPOT).goalSelector.removeAllGoals(goal -> true);
        helper.assertValueEqual(
                BoundVex.countOwnedBy(server, stranger.getUUID()), VexCap.CAP, "setup: stranger capped");

        // Act
        Request request = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 0, "nobody else's vexes counted");
        helper.assertValueEqual(request, Request.STARTED, "this summoner's press starts");
        cancelCast(player);
        helper.succeed();
    }

    /**
     * Drops {@code player}'s pending cast the way a logout does, so a test that ended on a started request leaves no
     * cast on its mock player (a pending cast would summon vexes into a later test).
     */
    private static void cancelCast(ServerPlayer player) {
        EvokingCaster.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
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
        // Staying, so the summoner's trip back brings nothing along: a follower within 16 blocks would be carried to
        // the Overworld and the count would be twelve in one level, which no per-level count could tell apart.
        inNether.forEach(vex -> {
            vex.setStaying(true);
            whenDone(helper, vex::discard);
        });
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
     * When the summoner sneak-clicks a Bound Vex with an empty main hand, should toggle stay on (the post is the block
     * it stands at, whatever origin it had before) and off again, each handled by the server, the post kept when stay
     * goes off; a stranger's click, a click with an item in hand and an off-hand click should pass and change
     * nothing, and a plain (un-sneaked) empty-hand click by the summoner should be consumed and change nothing.
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
        stranger.setShiftKeyDown(true);
        helper.assertFalse(
                vex.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a stranger's sneaking click passes");
        helper.assertFalse(vex.isStaying(), "still following after a stranger's sneaking click");
        helper.assertTrue(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a plain empty-hand click by the summoner is consumed");
        helper.assertFalse(vex.isStaying(), "a plain click leaves it following");
        GameTestSupport.hold(summoner, new ItemStack(Items.STICK));
        summoner.setShiftKeyDown(true);
        helper.assertFalse(
                vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(),
                "a click with an item passes");
        GameTestSupport.hold(summoner, ItemStack.EMPTY);
        helper.assertFalse(
                vex.interact(summoner, InteractionHand.OFF_HAND, Vec3.ZERO).consumesAction(),
                "an off-hand click with an empty main hand passes");
        helper.assertFalse(vex.isStaying(), "still following after an off-hand click");
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
     * When the summoner holds an ender pearl in the off hand and sneak-clicks a Bound Vex with the empty main hand,
     * should get {@code SUCCESS_SERVER} (a {@code Success}, which stops the client's fall-through to the off hand),
     * toggle stay, and still hold the whole pearl. A server-side test cannot run the client's fall-through, so the
     * result type is what it pins; the playtest checklist covers the rest.
     *
     * <p>Method under test: {@code BoundVex.mobInteract} (protected), through {@code Mob.interact}
     */
    static void stayToggleKeepsTheOffHandItem(GameTestHelper helper) {
        // Arrange
        ServerPlayer summoner = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, summoner);
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
        summoner.setItemInHand(InteractionHand.OFF_HAND, pearl);
        summoner.setShiftKeyDown(true);

        // Act
        InteractionResult result = vex.interact(summoner, InteractionHand.MAIN_HAND, Vec3.ZERO);

        // Assert
        helper.assertValueEqual(result, InteractionResult.SUCCESS_SERVER, "handled by the server");
        helper.assertTrue(result instanceof InteractionResult.Success, "a Success, which stops the fall-through");
        helper.assertTrue(vex.isStaying(), "stay toggled");
        helper.assertValueEqual(summoner.getOffhandItem().getCount(), 1, "the pearl is still there");
        helper.assertTrue(summoner.getOffhandItem().is(Items.ENDER_PEARL), "and still a pearl");
        helper.succeed();
    }

    /**
     * When a Bound Vex has no summoner mark at all (as {@code /summon} makes one), should idle: no summoner, no prey, a
     * click passes, and it is never removed for being far away.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(LivingEntity)},
     * {@link BoundVex#removeWhenFarAway}, {@link BoundVex#requiresCustomPersistence}
     */
    static void idlesWithoutASummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = unmarkedVex(helper, VEX_SPOT);
        vex.goalSelector.removeAllGoals(goal -> true);

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
     * When a Bound Vex is asked what killing it is worth, should answer no experience (vanilla's vex gives 3, which
     * three vexes a press would farm), whoever killed it.
     *
     * <p>Method under test: {@code BoundVex}'s constructor ({@code xpReward}), through
     * {@code LivingEntity.getExperienceReward}
     */
    static void givesNoExperience(GameTestHelper helper) {
        // Arrange
        BoundVex vex = boundVex(helper, VEX_SPOT, summoner(helper));

        // Act
        int reward = vex.getExperienceReward(helper.getLevel(), null);

        // Assert
        helper.assertValueEqual(reward, 0, "a Bound Vex gives no experience");
        helper.succeed();
    }

    /**
     * When a marked Bound Vex's summoner dies, should idle as if the summoner were offline (spec §10, "as offline until
     * respawn"): no summoner, and a husk that was prey a moment ago no longer is.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(LivingEntity)}
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
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(LivingEntity)}
     */
    static void idlesWithTheSummonerElsewhere(GameTestHelper helper) {
        // Arrange: staying, so the trip to the Nether does not carry the vex along (then no instance is left here)
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setStaying(true);
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
     * When a marked Bound Vex's summoner switches to spectator mode, should idle as if the summoner were offline (spec
     * §3.1; vanilla pets ignore a spectating owner): no summoner, and a husk that was prey a moment ago no longer is;
     * back in survival, the summoner and the prey are back.
     *
     * <p>Method under test: {@link BoundVex#summoner}, {@link BoundVex#isPrey(LivingEntity)}
     */
    static void idlesWithASpectatingSummoner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk husk = husk(helper, PREY_SPOT);
        helper.assertTrue(vex.summoner().isPresent() && vex.isPrey(husk), "setup: a summoner and a prey");

        // Act
        player.setGameMode(GameType.SPECTATOR);

        // Assert
        helper.assertTrue(vex.summoner().isEmpty(), "no summoner while spectating");
        helper.assertFalse(vex.isPrey(husk), "nothing is prey while the summoner spectates");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(vex.summoner().isPresent(), "the summoner is back in survival");
        helper.assertTrue(vex.isPrey(husk), "and the husk is prey again");
        helper.succeed();
    }

    /**
     * When one of a summoner's three Bound Vexes is at zero health (dying: it stays in the level for its twenty death
     * ticks), should count two: a dying vex must not count toward the cap (a press just after a kill could be refused).
     *
     * <p>Method under test: {@link BoundVex#countOwnedBy}
     */
    static void countSkipsADyingVex(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        parked(helper, VEX_SPOT, player);
        parked(helper, VEX_SPOT, player);
        BoundVex dying = parked(helper, VEX_SPOT, player);
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 3, "setup: three counted");

        // Act
        dying.setHealth(0.0F);

        // Assert
        helper.assertFalse(dying.isAlive(), "setup: the vex is dying");
        helper.assertValueEqual(BoundVex.countOwnedBy(server, player.getUUID()), 2, "the dying vex is not counted");
        helper.succeed();
    }

    /**
     * When the summoner has twelve Bound Vexes and presses V, should play no sound (the refusal shows only the notice;
     * a started cast plays the evoker's prepare-summon sound, the control once the vexes are gone). Padded: twelve
     * vexes in the shared box would sit near the edge of a neighbor's vex search.
     *
     * <p>Method under test: {@link EvokingCaster#request}
     */
    static void capRefusalIsSilent(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        VexGameTests.discardLeftoverVexes(helper);
        List<BoundVex> vexes = new ArrayList<>();
        for (int i = 0; i < VexCap.CAP; i++) {
            vexes.add(parked(helper, VEX_SPOT, player));
        }
        GameTestSupport.drainSounds(player);

        // Act
        Request refused = EvokingCaster.request(player, EvokerSpell.VEXES);

        // Assert
        helper.assertValueEqual(refused, Request.CAPPED, "setup: capped");
        helper.assertFalse(
                GameTestSupport.drainSounds(player).contains(SoundEvents.EVOKER_PREPARE_SUMMON),
                "a refused press plays no prepare sound");
        vexes.forEach(Entity::discard);
        helper.assertValueEqual(EvokingCaster.request(player, EvokerSpell.VEXES), Request.STARTED, "control: starts");
        helper.assertTrue(
                GameTestSupport.drainSounds(player).contains(SoundEvents.EVOKER_PREPARE_SUMMON),
                "control: a started press plays the prepare sound, so the mock player does hear sounds");
        cancelCast(player);
        helper.succeed();
    }

    /**
     * When another entity's feet are exactly on a face of the 8×8×7 guard box, should count it inside; half a block
     * past the face (or past the top), outside. Every goal asks this one method.
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
