package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.summonedVex;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.A;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.C;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.D;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.assertBurned;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.assertSpared;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.floors;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.forget;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.light;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.ownerOf;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.stepInto;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.teleport;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.trailingPlayer;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.GolemMercy;
import io.github.willrieske.spellcraft.trailing.TrailFires;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Iron golems as allies, the trail's golem clause, golem mercy and forgiveness, and the rule that a golem never targets
 * a summon (Evoking II spec §2.3). Registered in {@link SpellcraftGameTests}.
 */
public final class GolemGameTests {
    static final BlockPos SPOT = new BlockPos(1, 1, 1);

    /** The environment of the one test that changes the {@code universal_anger} game rule. */
    private static final String UNIVERSAL_ANGER = "universal_anger";

    private GolemGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "golems_ii_allies_unless_mad", GolemGameTests::alliesUnlessMad);
        SpellcraftGameTests.add(SHARED, "golems_ii_trail_spares_every_golem", GolemGameTests::trailSparesEveryGolem);
        // Builds one EvokerFangs inside the structure (no padding needed); it bites on its 8th tick and the second hit
        // comes after the 20-tick hurt cooldown: 60 ticks.
        SpellcraftGameTests.add(
                SHARED, "golems_ii_mercy_clamps_a_fang_bite", 60, 0, GolemGameTests::mercyClampsAFangBite);
        SpellcraftGameTests.add(
                SHARED, "golems_ii_mercy_sword_wolf_and_summon", GolemGameTests::mercySwordWolfAndSummon);
        SpellcraftGameTests.add(SHARED, "golems_ii_mercy_offline_wolf_owner", GolemGameTests::mercyOfflineWolfOwner);
        SpellcraftGameTests.add(SHARED, "golems_ii_other_damage_untouched", GolemGameTests::otherDamageUntouched);
        SpellcraftGameTests.add(SHARED, "golems_ii_never_targets_a_summon", GolemGameTests::neverTargetsASummon);
        SpellcraftGameTests.add(UNIVERSAL_ANGER, "golems_ii_universal_anger", GolemGameTests::universalAnger);
    }

    /**
     * A golem with no AI: no goals, so no target selection or movement. Its {@code aiStep} still runs, and with it the
     * anger upkeep ({@code updatePersistentAnger}, docs/api-notes.md §20), which is why {@link #anger} also starts the
     * anger timer.
     *
     * @param helper the test helper
     * @param relative where to spawn it, relative to the test's structure
     * @return the golem
     */
    static IronGolem golem(GameTestHelper helper, BlockPos relative) {
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, relative);
        golem.setNoAi(true);
        return golem;
    }

    /**
     * {@link #golem(GameTestHelper, BlockPos)} at {@code health}.
     *
     * @param helper the test helper
     * @param relative where to spawn it, relative to the test's structure
     * @param health the golem's starting health
     * @return the golem
     */
    static IronGolem golem(GameTestHelper helper, BlockPos relative, float health) {
        IronGolem golem = golem(helper, relative);
        golem.setHealth(health);
        return golem;
    }

    /**
     * Asserts that {@code golem} remembers {@code entity} as the last mob that hurt it.
     *
     * @param helper the test helper
     * @param golem the golem
     * @param entity the expected attacker
     * @param message what the assertion says when it fails
     */
    static void assertRemembers(GameTestHelper helper, IronGolem golem, Entity entity, String message) {
        helper.assertTrue(
                golem.getLastHurtByMob() != null
                        && golem.getLastHurtByMob().getUUID().equals(entity.getUUID()),
                message);
    }

    /**
     * Makes {@code golem} mad at {@code player} the way vanilla does: target, persistent anger target and the anger
     * timer (without the timer the upkeep in {@code aiStep} would clear the anger on the next tick; {@link
     * #mercyClampsAFangBite}, which lets the golem live 35 ticks, depends on this).
     *
     * @param golem the golem to anger
     * @param player the player it becomes mad at
     */
    static void anger(IronGolem golem, ServerPlayer player) {
        golem.setTarget(player);
        golem.setPersistentAngerTarget(EntityReference.of(player));
        golem.startPersistentAngerTimer();
    }

    /**
     * When a calm golem stands near the player, should be an ally and may have allies; with the player as its target,
     * or with its persistent anger naming the player, should not be an ally; after it stops being angry, should be one
     * again; a player-created golem (vanilla's {@code canAttack} says no to players, so it is never angry at one)
     * should stay an ally with only its anger naming the player, and still be clamped by mercy; a golem angry at a
     * second player should still be an ally of the first.
     *
     * <p>Method under test: {@link Allies#isAllyOf}, {@link Allies#isMadAt}, and {@link Allies#mayHaveAllies}
     */
    static void alliesUnlessMad(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem golem = golem(helper, SPOT);

        // Act & Assert
        helper.assertTrue(Allies.isAllyOf(player, golem), "a calm golem is an ally");
        helper.assertTrue(Allies.mayHaveAllies(golem), "a golem may have allies");
        helper.assertFalse(Allies.isMadAt(golem, player), "a calm golem is not mad");
        golem.setTarget(player);
        helper.assertTrue(Allies.isMadAt(golem, player), "a golem targeting the player is mad");
        helper.assertFalse(Allies.isAllyOf(player, golem), "and not an ally");
        golem.setTarget(null);
        golem.setPersistentAngerTarget(EntityReference.of(player));
        golem.startPersistentAngerTimer();
        helper.assertTrue(golem.isAngryAt(player, level), "setup: vanilla calls it angry at the player");
        helper.assertFalse(Allies.isAllyOf(player, golem), "a golem whose anger names the player is not an ally");
        golem.stopBeingAngry();
        helper.assertTrue(Allies.isAllyOf(player, golem), "forgiven: an ally again");

        // A player-created golem never counts as angry at a player (canAttack is false), so its anger alone, with no
        // target set, leaves it an ally; the mercy clamp does not depend on anger and still applies.
        IronGolem built = golem(helper, SPOT, 30.0F);
        built.setPlayerCreated(true);
        built.setPersistentAngerTarget(EntityReference.of(player));
        built.startPersistentAngerTimer();
        helper.assertFalse(built.isAngryAt(player, level), "setup: a player-created golem is not angry at a player");
        helper.assertTrue(Allies.isAllyOf(player, built), "a player-created golem stays an ally");
        built.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
        helper.assertValueEqual(built.getHealth(), 25.0F, "and mercy still clamps it");

        // A golem mad at someone else is no enemy of this player.
        ServerPlayer other = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem madAtOther = golem(helper, SPOT);
        anger(madAtOther, other);
        helper.assertTrue(Allies.isMadAt(madAtOther, other), "setup: the golem is mad at the other player");
        helper.assertFalse(Allies.isMadAt(madAtOther, player), "it is not mad at the first player");
        helper.assertTrue(Allies.isAllyOf(player, madAtOther), "a golem mad at another player is still an ally");
        helper.succeed();
    }

    /**
     * When the {@code universal_anger} game rule is on and a golem has anger with no persistent target, should the
     * golem be angry at every player: mad at the player and no ally.
     *
     * <p>Method under test: {@link Allies#isMadAt}, and {@link Allies#isAllyOf}
     *
     * <p>Changes a game rule, so it runs in the {@code universal_anger} environment, restored afterwards.
     */
    static void universalAnger(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        GameRules rules = level.getGameRules();
        boolean saved = rules.get(GameRules.UNIVERSAL_ANGER);
        whenDone(helper, () -> rules.set(GameRules.UNIVERSAL_ANGER, saved, level.getServer()));
        rules.set(GameRules.UNIVERSAL_ANGER, true, level.getServer());
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem golem = golem(helper, SPOT);

        // Act
        golem.startPersistentAngerTimer();

        // Assert
        helper.assertTrue(golem.isAngryAtAllPlayers(level), "setup: universal anger with no persistent target");
        helper.assertTrue(Allies.isMadAt(golem, player), "the golem is mad at the player");
        helper.assertFalse(Allies.isAllyOf(player, golem), "and no ally");
        helper.succeed();
    }

    /**
     * When a calm golem, then a golem mad at the owner, then the same golem with the owner offline step into the
     * owner's flame, should none of them burn, while a zombie stepping in does (the flame works). The offline case
     * pins that the golem clause does not go through the ally path, which spares only while the owner is online. A
     * golem stepping into a flame no ledger entry owns (the negative control) should burn.
     *
     * <p>Method under test: {@link TrailFires#isSpared}
     */
    static void trailSparesEveryGolem(GameTestHelper helper) {
        // Arrange
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        UUID ownerId = owner.getUUID();
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        IronGolem golem = golem(helper, C);

        // Act & Assert
        stepInto(helper, A, golem);
        assertSpared(helper, golem, "a calm golem");
        anger(golem, owner);
        helper.assertTrue(Allies.isMadAt(golem, owner), "setup: the golem is mad at the owner");
        stepInto(helper, A, golem);
        assertSpared(helper, golem, "a golem mad at the owner");
        Zombie stranger = helper.spawn(EntityTypes.ZOMBIE, C);
        stepInto(helper, A, stranger);
        assertBurned(helper, stranger, "a zombie");
        owner.discard();
        helper.assertTrue(helper.getLevel().getPlayerByUUID(ownerId) == null, "setup: the owner is offline");
        stepInto(helper, A, golem);
        assertSpared(helper, golem, "a golem with the owner offline");
        forget(helper, owner);
        helper.assertTrue(ownerOf(helper, A).isEmpty(), "setup: the flame at A has no owner now");
        IronGolem unowned = golem(helper, C);
        stepInto(helper, A, unowned);
        assertBurned(helper, unowned, "a golem in an ownerless flame");
        helper.succeed();
    }

    /**
     * When a golem mad at the player, at 30 health, is bitten by the player's fang (6 damage), should it stop at 25,
     * forget its target, anger and the hit's memory, and be an ally again; a player hit at 25 afterwards should do
     * nothing and leave no grudge.
     *
     * <p>Method under test: {@link GolemMercy#onDamagePre}, and {@link GolemMercy#onDamagePost}
     */
    static void mercyClampsAFangBite(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        // The structure is 3 blocks of air under a barrier ceiling: at SPOT the golem's head (2.7 tall) reaches into
        // the barrier and it suffocates for 1 damage a tick, so this long-lived golem stands one block lower, where
        // its head stays under the ceiling (the stone replaces the barrier shell's floor at relative y -1).
        BlockPos low = SPOT.below();
        helper.setBlock(low.below(), Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem golem = golem(helper, low);
        golem.setHealth(30.0F);
        anger(golem, player);
        helper.assertTrue(golem.isAngryAt(player, level), "setup: angry");
        helper.assertTrue(Allies.isMadAt(golem, player), "setup: mad");

        // Act
        level.addFreshEntity(new EvokerFangs(level, golem.getX(), golem.getY(), golem.getZ(), 0.0F, 0, player));
        helper.runAfterDelay(12, () -> {
            // Assert
            helper.assertValueEqual(golem.getHealth(), 25.0F, "the bite stops at 25");
            helper.assertTrue(golem.getTarget() == null, "the target is forgotten");
            helper.assertFalse(golem.isAngryAt(player, level), "the anger is gone");
            helper.assertTrue(Allies.isAllyOf(player, golem), "an ally again");
            // Forgiveness in Pre would be undone by the attacker record written after the damage lands; a null memory
            // is what keeps HurtByTargetGoal.canUse from firing (HurtByTargetGoal.java:33-36).
            helper.assertTrue(golem.getLastHurtByMob() == null, "it does not remember the hit");
        });
        // 35 ticks is past the bite (about tick 9) plus the 20-tick hurt cooldown, so the second hit is not swallowed.
        helper.runAfterDelay(35, () -> {
            // Act
            golem.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
            // Assert
            helper.assertValueEqual(golem.getHealth(), 25.0F, "a hit at 25 does nothing");
            helper.assertTrue(golem.getLastHurtByMob() == null, "and leaves no grudge");
            helper.succeed();
        });
    }

    /**
     * When the player's sword hit would take a golem from 30 to 20, should it land at 25 with no grudge; when the
     * player's tamed wolf would take one from 40 to 20, should it land at 25 too; when the player's summoned vex would
     * take one from 30 to 20, should it land at 25; when a sword hit takes one from 40 to 30, should it land at 30 and
     * remember the player (vanilla gets mad on its own); when a player hits a golem already at 20, should nothing land.
     *
     * <p>Method under test: {@link GolemMercy#onDamagePre}, and {@link GolemMercy#onDamagePost}
     */
    static void mercySwordWolfAndSummon(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem bySword = golem(helper, SPOT, 30.0F);
        IronGolem byWolf = golem(helper, SPOT, 40.0F);
        Wolf pet = helper.spawn(EntityTypes.WOLF, SPOT);
        pet.setNoAi(true);
        pet.tame(player);
        IronGolem byVex = golem(helper, SPOT, 30.0F);
        Vex summon = summonedVex(helper, SPOT, player.getUUID());
        IronGolem roomLeft = golem(helper, SPOT, 40.0F);
        IronGolem alreadyUnder = golem(helper, SPOT, 20.0F);

        // Act
        bySword.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
        byWolf.hurtServer(level, level.damageSources().mobAttack(pet), 20.0F);
        byVex.hurtServer(level, level.damageSources().mobAttack(summon), 10.0F);
        roomLeft.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
        alreadyUnder.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);

        // Assert
        helper.assertValueEqual(bySword.getHealth(), 25.0F, "the sword stops at 25");
        helper.assertTrue(bySword.getLastHurtByMob() == null, "forgiven: no grudge against the player");
        helper.assertValueEqual(byWolf.getHealth(), 25.0F, "the wolf stops at 25");
        helper.assertTrue(byWolf.getLastHurtByMob() == null, "forgiven: no grudge against the wolf");
        helper.assertValueEqual(byVex.getHealth(), 25.0F, "the summoned vex stops at 25");
        helper.assertTrue(byVex.getLastHurtByMob() == null, "forgiven: no grudge against the vex");
        helper.assertValueEqual(roomLeft.getHealth(), 30.0F, "above 25 the hit lands whole");
        assertRemembers(helper, roomLeft, player, "above 25 the golem remembers the player");
        helper.assertValueEqual(alreadyUnder.getHealth(), 20.0F, "under 25 nothing lands");
        helper.assertTrue(alreadyUnder.getLastHurtByMob() == null, "and no grudge");
        summon.discard();
        helper.succeed();
    }

    /**
     * When a tamed wolf whose owner is not in the level would take a golem from 40 to 20, should the golem still land
     * at 25 with no grudge: a tame wolf is the player's side whether or not its owner is online.
     *
     * <p>Method under test: {@link GolemMercy#onDamagePre}, and {@link GolemMercy#onDamagePost}
     */
    static void mercyOfflineWolfOwner(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        IronGolem golem = golem(helper, SPOT, 40.0F);
        Wolf pet = helper.spawn(EntityTypes.WOLF, SPOT);
        pet.setNoAi(true);
        pet.setTame(true, false);
        pet.setOwnerReference(EntityReference.of(UUID.randomUUID()));
        helper.assertTrue(pet.isTame(), "setup: the wolf is tame");
        helper.assertTrue(pet.getOwner() == null, "setup: its owner is not in the level");

        // Act
        golem.hurtServer(level, level.damageSources().mobAttack(pet), 20.0F);

        // Assert
        helper.assertValueEqual(golem.getHealth(), 25.0F, "the wolf stops at 25 with its owner offline");
        helper.assertTrue(golem.getLastHurtByMob() == null, "forgiven: no grudge against the wolf");
        helper.succeed();
    }

    /**
     * When a golem at 30 is hurt by magic with no owner (a vanilla ownerless fang), another by a zombie, and another by
     * a wolf nobody owns, should all three land at 20: mercy is only for the player's side.
     *
     * <p>Method under test: {@link GolemMercy#onDamagePre}
     */
    static void otherDamageUntouched(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        IronGolem byMagic = golem(helper, SPOT, 30.0F);
        IronGolem byZombie = golem(helper, SPOT, 30.0F);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, SPOT);
        zombie.setNoAi(true);
        // Fighting the zombie already: mercy forgiving it would clear this target and memory.
        byZombie.setTarget(zombie);
        byZombie.setLastHurtByMob(zombie);
        IronGolem byStrayWolf = golem(helper, SPOT, 30.0F);
        Wolf stray = helper.spawn(EntityTypes.WOLF, SPOT);
        stray.setNoAi(true);

        // Act
        byMagic.hurtServer(level, level.damageSources().magic(), 10.0F);
        byZombie.hurtServer(level, level.damageSources().mobAttack(zombie), 10.0F);
        byStrayWolf.hurtServer(level, level.damageSources().mobAttack(stray), 10.0F);

        // Assert
        helper.assertValueEqual(byMagic.getHealth(), 20.0F, "ownerless magic lands whole");
        helper.assertValueEqual(byZombie.getHealth(), 20.0F, "a zombie's hit lands whole");
        assertRemembers(helper, byZombie, zombie, "the golem still remembers the zombie: no forgiveness at 20");
        helper.assertTrue(
                byZombie.getTarget() != null && byZombie.getTarget().getUUID().equals(zombie.getUUID()),
                "and still targets it");
        helper.assertValueEqual(byStrayWolf.getHealth(), 20.0F, "an unowned wolf's hit lands whole");
        zombie.discard();
        helper.succeed();
    }

    /**
     * When a golem is told to target a vex the player summoned, should it keep no target; told to target a zombie,
     * should it take it (the control); told to target the summon while it has the zombie, should it keep the zombie.
     *
     * <p>Method under test: {@link GolemMercy#onChangeTarget}
     */
    static void neverTargetsASummon(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        IronGolem golem = golem(helper, SPOT);
        Vex summon = summonedVex(helper, SPOT, player.getUUID());
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, SPOT);
        zombie.setNoAi(true);

        // Act & Assert
        golem.setTarget(summon);
        helper.assertTrue(golem.getTarget() == null, "a summoned vex is never a golem's target");
        golem.setTarget(zombie);
        helper.assertTrue(
                golem.getTarget() != null && golem.getTarget().getUUID().equals(zombie.getUUID()), "a zombie still is");
        golem.setTarget(summon);
        helper.assertTrue(
                golem.getTarget() != null && golem.getTarget().getUUID().equals(zombie.getUUID()),
                "a cancelled change keeps the old target");
        summon.discard();
        zombie.discard();
        helper.succeed();
    }
}
