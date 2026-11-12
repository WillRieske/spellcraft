package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.at;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.floor;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.foeAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.hostileAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.images;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.Dice;
import io.github.willrieske.spellcraft.evoking.EvokingAttachments;
import io.github.willrieske.spellcraft.evoking.GhostrailChaseGoal;
import io.github.willrieske.spellcraft.evoking.ScrollMobs;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.Spell;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** GameTests of the mobs that carry scrolls (Parts 6-7 spec §8) and the Ghostrail chase goal (§7.3). */
final class ScrollMobGameTests {
    /** The environment of the tests that script {@link Dice#SCROLL_MOB}. */
    private static final String DICE = "scroll_mob_dice";

    /**
     * Blocks of floor around the standing spot. Mirror's images wander up to 6 blocks from the evoker, which stands 4
     * blocks east, so 12 keeps them on the floor.
     */
    private static final int FLOOR_RADIUS = 12;

    private ScrollMobGameTests() {}

    /** Registers every scroll-mob GameTest. */
    static void registerAll() {
        FangGameTests.addPadded(DICE, "scroll_mob_evoker_spawn", ScrollMobGameTests::evokerSpawn);
        FangGameTests.addPadded(DICE, "scroll_mob_drop_chance_is_half", ScrollMobGameTests::dropChanceIsHalf);
        FangGameTests.addPadded(DICE, "scroll_mob_pillager_ghostrail", ScrollMobGameTests::pillagerGhostrail);
        FangGameTests.addPadded(
                DICE, "scroll_mob_evoker_roll_is_one_in_four", ScrollMobGameTests::evokerRollIsOneInFour);
        FangGameTests.addPadded(
                DICE, "scroll_mob_pillager_roll_is_one_in_ten", ScrollMobGameTests::pillagerRollIsOneInTen);
        FangGameTests.addPadded(SHARED, "scroll_mob_casts_mirror", ScrollMobGameTests::castsMirror);
        FangGameTests.addPadded(SHARED, "scroll_mob_cooldown_holds", ScrollMobGameTests::cooldownHolds);
        FangGameTests.addPadded(SHARED, "scroll_mob_casts_ghostrail", ScrollMobGameTests::castsGhostrail);
        FangGameTests.addPadded(SHARED, "scroll_mob_no_scroll_no_cast", ScrollMobGameTests::noScrollNoCast);
        FangGameTests.addPadded(SHARED, "scroll_mob_no_recast_on_a_switch", ScrollMobGameTests::noRecastOnASwitch);
        FangGameTests.addPadded(SHARED, "scroll_mob_sound_burst_and_scroll", ScrollMobGameTests::soundBurstAndScroll);
        FangGameTests.addPadded(SHARED, "scroll_mob_survives_a_save", ScrollMobGameTests::survivesASave);
        FangGameTests.addPadded(SHARED, "scroll_mob_chase_goal_installed", ScrollMobGameTests::chaseGoalInstalled);
        FangGameTests.addPadded(
                SHARED, "scroll_mob_chases_through_a_wall", 100, ScrollMobGameTests::chasesThroughAWall);
        FangGameTests.addPadded(SHARED, "scroll_mob_only_raiders_cast", ScrollMobGameTests::onlyRaidersCast);
        FangGameTests.addPadded(
                SHARED, "scroll_mob_ghostrail_cooldown_is_600", ScrollMobGameTests::ghostrailCooldownIs600);
        FangGameTests.addPadded(
                SHARED, "scroll_mob_casts_on_the_next_target", ScrollMobGameTests::castsOnTheNextTarget);
        FangGameTests.addPadded(SHARED, "scroll_mob_drops_on_a_player_kill", ScrollMobGameTests::dropsOnAPlayerKill);
        FangGameTests.addPadded(
                SHARED, "scroll_mob_keeps_it_on_another_kill", ScrollMobGameTests::keepsItOnAnotherKill);
    }

    /**
     * A mob of {@code type} finalized the way a spawn does (the event fires), 4 blocks east of the standing spot, with
     * no AI and no gravity, discarded when the test ends. The caller scripts {@link Dice#SCROLL_MOB} just before.
     */
    private static <T extends Mob> T spawned(GameTestHelper helper, EntityType<T> type) {
        ServerLevel level = helper.getLevel();
        T mob = Objects.requireNonNull(type.create(level, EntitySpawnReason.SPAWNER));
        mob.setPos(at(helper, 4.0, 0.0, 0.0));
        EventHooks.finalizeMobSpawn(
                mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.SPAWNER, null);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        level.addFreshEntity(mob);
        whenDone(helper, mob::discard);
        return mob;
    }

    /** A mob of {@code type} with {@code spell}'s scroll in its offhand, no AI, 4 blocks east of the standing spot. */
    private static <T extends Mob> T armed(GameTestHelper helper, EntityType<T> type, Spell spell) {
        T mob = foeAt(helper, type, at(helper, 4.0, 0.0, 0.0));
        mob.setItemSlot(EquipmentSlot.OFFHAND, ModItems.scrollFor(spell));
        return mob;
    }

    /** An evoker armed with {@code spell}. */
    private static Evoker armed(GameTestHelper helper, Spell spell) {
        return armed(helper, EntityTypes.EVOKER, spell);
    }

    /** Spawns a mob with the carry roll and the spell roll scripted, unscripting in a {@code finally}. */
    private static <T extends Mob> T spawnedWithRolls(GameTestHelper helper, EntityType<T> type, int... rolls) {
        whenDone(helper, Dice::resetAll);
        try {
            Dice.SCROLL_MOB.script(rolls);
            return spawned(helper, type);
        } finally {
            Dice.unscriptAll();
        }
    }

    /**
     * When the dice roll 0 (carries) then 0 (Mirror) for an evoker, should it spawn with a Mirror scroll in its
     * offhand.
     *
     * <p>Method under test: {@code ScrollMobs.onFinalizeSpawn}
     */
    static void evokerSpawn(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        // Act
        Evoker evoker = spawnedWithRolls(helper, EntityTypes.EVOKER, 0, 0);
        // Assert
        helper.assertTrue(evoker.getOffhandItem().is(ModItems.MIRROR_SCROLL.get()), "a Mirror scroll in the offhand");
        helper.assertTrue(evoker.getMainHandItem().isEmpty(), "the main hand is vanilla's");
        helper.succeed();
    }

    /**
     * When an evoker spawns with a scroll, should the offhand drop chance be 0.5.
     *
     * <p>Method under test: {@code ScrollMobs.onFinalizeSpawn}
     */
    static void dropChanceIsHalf(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        // Act
        Evoker evoker = spawnedWithRolls(helper, EntityTypes.EVOKER, 0, 0);
        // Assert
        helper.assertValueEqual(evoker.getDropChances().byEquipment(EquipmentSlot.OFFHAND), 0.5F, "the drop chance");
        helper.succeed();
    }

    /**
     * When the dice roll 0 then 1 for a pillager, should it spawn with a Ghostrail scroll in its offhand.
     *
     * <p>Method under test: {@code ScrollMobs.onFinalizeSpawn}
     */
    static void pillagerGhostrail(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        // Act
        Pillager pillager = spawnedWithRolls(helper, EntityTypes.PILLAGER, 0, 1);
        // Assert
        helper.assertTrue(
                pillager.getOffhandItem().is(ModItems.GHOSTRAIL_SCROLL.get()), "a Ghostrail scroll in the offhand");
        helper.succeed();
    }

    /**
     * When the evoker's carry die rolls its last side (3), should the evoker carry nothing, and the die be 4-sided.
     *
     * <p>Method under test: {@code ScrollMobs.onFinalizeSpawn}
     */
    static void evokerRollIsOneInFour(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        // Act
        Evoker evoker = spawnedWithRolls(helper, EntityTypes.EVOKER, 3);
        // Assert
        helper.assertTrue(evoker.getOffhandItem().isEmpty(), "no scroll");
        helper.assertValueEqual(ScrollMobs.EVOKER_ONE_IN, 4, "an evoker rolls a 4-sided die");
        helper.succeed();
    }

    /**
     * When the pillager's carry die rolls its last side (9), should the pillager carry nothing, and the die be
     * 10-sided.
     *
     * <p>Method under test: {@code ScrollMobs.onFinalizeSpawn}
     */
    static void pillagerRollIsOneInTen(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        // Act
        Pillager pillager = spawnedWithRolls(helper, EntityTypes.PILLAGER, 9);
        // Assert
        helper.assertTrue(pillager.getOffhandItem().isEmpty(), "no scroll");
        helper.assertValueEqual(ScrollMobs.RAIDER_ONE_IN, 10, "a pillager rolls a 10-sided die");
        helper.succeed();
    }

    /**
     * When an evoker with a Mirror scroll takes a target, should it get Mirror at once with 13 images of itself.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}
     */
    static void castsMirror(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, FLOOR_RADIUS);
        Evoker evoker = armed(helper, Spells.MIRROR);
        // Act
        evoker.setTarget(player);
        // Assert
        helper.assertTrue(evoker.hasEffect(ModEffects.MIRROR), "Mirror on the evoker");
        helper.assertValueEqual(images(evoker).size(), MirrorRule.IMAGES, "13 images of the evoker");
        helper.succeed();
    }

    /**
     * When an evoker that cast Mirror loses its effect and takes a target again inside the cooldown, should it not cast
     * a second time.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}
     */
    static void cooldownHolds(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, FLOOR_RADIUS);
        Evoker evoker = armed(helper, Spells.MIRROR);
        evoker.setTarget(player);
        helper.assertTrue(evoker.hasEffect(ModEffects.MIRROR), "setup: the first cast happened");
        evoker.setTarget(null);
        evoker.removeEffect(ModEffects.MIRROR);
        // Act
        evoker.setTarget(player);
        // Assert
        helper.assertFalse(evoker.hasEffect(ModEffects.MIRROR), "no second Mirror inside the cooldown");
        helper.succeed();
    }

    /**
     * When a pillager with a Ghostrail scroll takes a target, should it get Ghostrail for the hostile duration and its
     * cooldown end 600 ticks from now.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}
     */
    static void castsGhostrail(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Pillager pillager = armed(helper, EntityTypes.PILLAGER, Spells.GHOSTRAIL);
        // Act
        pillager.setTarget(player);
        // Assert
        helper.assertTrue(pillager.hasEffect(ModEffects.GHOSTRAIL), "Ghostrail on the pillager");
        helper.assertValueEqual(
                pillager.getEffect(ModEffects.GHOSTRAIL).getDuration(),
                GhostrailDuration.HOSTILE_TICKS,
                "the hostile duration");
        helper.assertValueEqual(
                pillager.getExistingData(EvokingAttachments.MOB_GHOSTRAIL_READY).orElse(0L),
                helper.getLevel().getGameTime() + ScrollMobs.MOB_GHOSTRAIL_COOLDOWN_TICKS,
                "the cooldown ends 600 ticks from now");
        helper.succeed();
    }

    /**
     * When a mob that is no evoker, vindicator or pillager has a scroll in its offhand and takes a target, should it
     * cast nothing.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}: only the three raiders cast
     */
    static void onlyRaidersCast(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Husk husk = hostileAt(helper, at(helper, 4.0, 0.0, 0.0));
        husk.setItemSlot(EquipmentSlot.OFFHAND, ModItems.scrollFor(Spells.MIRROR));
        // Act
        husk.setTarget(player);
        // Assert
        helper.assertFalse(husk.hasEffect(ModEffects.MIRROR), "no Mirror on a husk");
        helper.assertValueEqual(
                husk.getExistingData(EvokingAttachments.MOB_MIRROR_READY).orElse(0L), 0L, "no cooldown");
        helper.succeed();
    }

    /**
     * When a pillager casts Ghostrail and takes a target again inside the cooldown, should the stored ready time be
     * the cast's game time plus 600 and the second target change cast nothing.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}: the 600-tick cooldown
     */
    static void ghostrailCooldownIs600(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Pillager pillager = armed(helper, EntityTypes.PILLAGER, Spells.GHOSTRAIL);
        long castAt = helper.getLevel().getGameTime();
        pillager.setTarget(player);
        helper.assertTrue(pillager.hasEffect(ModEffects.GHOSTRAIL), "setup: the first cast happened");
        long readyAt =
                pillager.getExistingData(EvokingAttachments.MOB_GHOSTRAIL_READY).orElse(0L);
        pillager.removeEffect(ModEffects.GHOSTRAIL);
        pillager.setTarget(null);
        // Act
        pillager.setTarget(player);
        // Assert
        helper.assertValueEqual(readyAt, castAt + 600L, "the stored ready time is the cast time plus 600");
        helper.assertFalse(pillager.hasEffect(ModEffects.GHOSTRAIL), "no second cast inside the cooldown");
        helper.assertValueEqual(
                pillager.getExistingData(EvokingAttachments.MOB_GHOSTRAIL_READY).orElse(0L),
                readyAt,
                "the cooldown is not restarted");
        helper.succeed();
    }

    /**
     * When a pillager loses its target after a cast and the cooldown is over, should it cast on the next target.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}: a lost target is a new fight
     */
    static void castsOnTheNextTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Pillager pillager = armed(helper, EntityTypes.PILLAGER, Spells.GHOSTRAIL);
        pillager.setTarget(player);
        helper.assertTrue(pillager.hasEffect(ModEffects.GHOSTRAIL), "setup: the first cast happened");
        pillager.removeEffect(ModEffects.GHOSTRAIL);
        pillager.setTarget(null);
        pillager.setData(EvokingAttachments.MOB_GHOSTRAIL_READY, 0L);
        // Act
        pillager.setTarget(player);
        // Assert
        helper.assertTrue(pillager.hasEffect(ModEffects.GHOSTRAIL), "it casts on the next target");
        helper.succeed();
    }

    /** The Mirror scroll items on the ground within 3 blocks of {@code where}. */
    private static List<ItemEntity> scrollsNear(GameTestHelper helper, Vec3 where) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(where, where).inflate(3.0)).stream()
                .filter(item -> item.getItem().is(ModItems.MIRROR_SCROLL.get()))
                .toList();
    }

    /**
     * Kills an evoker armed with a Mirror scroll and returns the scrolls it dropped. The offhand drop chance is raised
     * to 1.0 first: the 0.5 roll is vanilla's own random, which a test cannot script ({@code dropChanceIsHalf} pins
     * the 0.5), and what this checks is who may collect the drop.
     */
    private static List<ItemEntity> killedWith(GameTestHelper helper, Function<ServerLevel, DamageSource> source) {
        Evoker evoker = armed(helper, Spells.MIRROR);
        evoker.setDropChance(EquipmentSlot.OFFHAND, 1.0F);
        Vec3 where = evoker.position();
        whenDone(helper, () -> scrollsNear(helper, where).forEach(ItemEntity::discard));
        ServerLevel level = helper.getLevel();
        evoker.hurtServer(level, source.apply(level), 1000.0F);
        helper.assertFalse(evoker.isAlive(), "setup: the evoker died");
        return scrollsNear(helper, where);
    }

    /**
     * When a player kills an evoker that carries a scroll, should the scroll drop.
     *
     * <p>Method under test: {@code ScrollMobs}: the offhand scroll is the player-kill loot
     */
    static void dropsOnAPlayerKill(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        // Act
        List<ItemEntity> dropped =
                killedWith(helper, level -> level.damageSources().playerAttack(player));
        // Assert
        helper.assertValueEqual(dropped.size(), 1, "the scroll drops");
        helper.succeed();
    }

    /**
     * When something other than a player kills an evoker that carries a scroll, should the scroll not drop.
     *
     * <p>Method under test: {@code ScrollMobs}: the offhand scroll is the player-kill loot
     */
    static void keepsItOnAnotherKill(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        // Act
        List<ItemEntity> dropped =
                killedWith(helper, level -> level.damageSources().generic());
        // Assert
        helper.assertTrue(dropped.isEmpty(), "no scroll drops");
        helper.succeed();
    }

    /**
     * When an evoker with an empty offhand takes a target, should it cast neither spell.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}
     */
    static void noScrollNoCast(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        Evoker evoker = foeAt(helper, EntityTypes.EVOKER, at(helper, 4.0, 0.0, 0.0));
        // Act
        evoker.setTarget(player);
        // Assert
        helper.assertFalse(evoker.hasEffect(ModEffects.MIRROR), "no Mirror");
        helper.assertFalse(evoker.hasEffect(ModEffects.GHOSTRAIL), "no Ghostrail");
        helper.succeed();
    }

    /**
     * When an evoker that already has a target switches to another, should it not cast again even with the effect gone
     * and the cooldown cleared.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}
     */
    static void noRecastOnASwitch(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, FLOOR_RADIUS);
        Husk husk = hostileAt(helper, at(helper, -4.0, 0.0, 0.0));
        Evoker evoker = armed(helper, Spells.MIRROR);
        evoker.setTarget(husk);
        helper.assertTrue(evoker.hasEffect(ModEffects.MIRROR), "setup: the first cast happened");
        // Clear everything that could block a second cast except the switch rule
        evoker.removeEffect(ModEffects.MIRROR);
        evoker.setData(EvokingAttachments.MOB_MIRROR_READY, 0L);
        // Act
        evoker.setTarget(player);
        // Assert
        helper.assertFalse(evoker.hasEffect(ModEffects.MIRROR), "a switch of target is not a new fight");
        helper.succeed();
    }

    /**
     * When an evoker casts its scroll with a player near, should that player be sent the cast sound and the purple
     * burst, and the evoker keep the scroll.
     *
     * <p>Method under test: {@code ScrollMobs.onChangeTarget}
     */
    static void soundBurstAndScroll(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, FLOOR_RADIUS);
        Evoker evoker = armed(helper, Spells.MIRROR);
        GameTestSupport.drainShown(player);
        // Act
        evoker.setTarget(player);
        // Assert
        GameTestSupport.Shown shown = GameTestSupport.drainShown(player);
        helper.assertTrue(shown.sounds().contains(SoundEvents.EVOKER_CAST_SPELL), "the cast sound");
        helper.assertTrue(shown.particles().contains(ParticleTypes.ENTITY_EFFECT), "the burst");
        helper.assertTrue(evoker.getOffhandItem().is(ModItems.MIRROR_SCROLL.get()), "the scroll is not used up");
        helper.succeed();
    }

    /**
     * When an evoker that cast Mirror is saved and loaded, should the copy keep the cooldown and the scroll.
     *
     * <p>Method under test: {@code EvokingAttachments.MOB_MIRROR_READY}
     */
    static void survivesASave(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, FLOOR_RADIUS);
        Evoker evoker = armed(helper, Spells.MIRROR);
        evoker.setTarget(player);
        // Act
        Evoker copy = (Evoker) GameTestSupport.reloaded(helper.getLevel(), evoker);
        // Assert
        helper.assertValueEqual(
                copy.getExistingData(EvokingAttachments.MOB_MIRROR_READY),
                evoker.getExistingData(EvokingAttachments.MOB_MIRROR_READY),
                "the cooldown");
        helper.assertTrue(copy.getOffhandItem().is(ModItems.MIRROR_SCROLL.get()), "the scroll");
        helper.succeed();
    }

    /** True when {@code mob} has a chase goal. */
    private static boolean hasChaseGoal(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof GhostrailChaseGoal);
    }

    /**
     * When a pillager, an evoker or a vindicator joins a level, should it have the chase goal; a husk should not.
     *
     * <p>Method under test: {@code ScrollMobs.onJoin}
     */
    static void chaseGoalInstalled(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        // Act
        Pillager pillager = helper.spawn(EntityTypes.PILLAGER, FangGameTests.FEET.offset(3, 0, 0));
        Evoker evoker = helper.spawn(EntityTypes.EVOKER, FangGameTests.FEET.offset(4, 0, 0));
        Vindicator vindicator = helper.spawn(EntityTypes.VINDICATOR, FangGameTests.FEET.offset(5, 0, 0));
        Husk husk = helper.spawn(EntityTypes.HUSK, FangGameTests.FEET.offset(6, 0, 0));
        whenDone(helper, () -> {
            pillager.discard();
            evoker.discard();
            vindicator.discard();
            husk.discard();
        });
        // Assert
        helper.assertTrue(hasChaseGoal(pillager), "a pillager has the goal");
        helper.assertTrue(hasChaseGoal(evoker), "an evoker has the goal");
        helper.assertTrue(hasChaseGoal(vindicator), "a vindicator has the goal");
        helper.assertFalse(hasChaseGoal(husk), "a husk has none");
        helper.succeed();
    }

    /**
     * When a pillager with Ghostrail and a target has a stone wall between it and the target, should it fly straight at
     * the target through the wall and end up within 2 blocks of it.
     *
     * <p>Method under test: {@code GhostrailChaseGoal}
     */
    static void chasesThroughAWall(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, FLOOR_RADIUS);
        // The player stays vulnerable: an invulnerable player is not a valid target (Mob.asValidTarget), and the
        // pillager has no weapon, so it cannot hurt the player
        // A wall across the whole floor (z -12 to 12, 4 high): there is no ground beyond its ends and it is too high to
        // jump, so a pillager that reaches the player has not walked around it
        for (int y = 0; y <= 3; y++) {
            for (int z = -12; z <= 12; z++) {
                helper.setBlock(FangGameTests.FEET.offset(5, y, z), Blocks.STONE);
            }
        }
        Pillager pillager = helper.spawn(EntityTypes.PILLAGER, FangGameTests.FEET.offset(9, 0, 0));
        whenDone(helper, pillager::discard);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        // No target goals: with the test's own target set, a mock player of another test running at the same time
        // must not be able to draw the pillager away (the test failed intermittently on a busy run)
        pillager.targetSelector.removeAllGoals(goal -> true);
        // Act
        Spells.cast(pillager, Spells.GHOSTRAIL, 400);
        pillager.setTarget(player);
        // Assert: 100 ticks is the test's limit; the chase covers the 8 blocks in about 30
        helper.startSequence()
                .thenWaitUntil(
                        () -> helper.assertTrue(pillager.distanceTo(player) < 2.0, "the pillager reached the target"))
                .thenSucceed();
    }
}
