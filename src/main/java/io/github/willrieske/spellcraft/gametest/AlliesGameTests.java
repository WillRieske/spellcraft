package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.withTeam;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.Scoreboard;

/** The shared ally rule (Evoking spec §3, table A1–A5). Registered in {@link SpellcraftGameTests}. */
public final class AlliesGameTests {
    private static final BlockPos SPOT = new BlockPos(1, 1, 1);

    private AlliesGameTests() {}

    static void registerAll() {
        // Puts the player on a scoreboard team, so it runs in the team_mutation batch like the Trail team tests.
        SpellcraftGameTests.add("team_mutation", "allies_a1_team", AlliesGameTests::a1Team);
        SpellcraftGameTests.add(SHARED, "allies_a2_tamed_pet", AlliesGameTests::a2TamedPet);
        SpellcraftGameTests.add(SHARED, "allies_a3_summoner_attachment", AlliesGameTests::a3SummonerAttachment);
        SpellcraftGameTests.add(SHARED, "allies_a4_ownable_owner", AlliesGameTests::a4OwnableOwner);
        SpellcraftGameTests.add(SHARED, "allies_a5_strangers", AlliesGameTests::a5Strangers);
        SpellcraftGameTests.add(SHARED, "allies_a7_villagers_and_traders", AlliesGameTests::a7VillagersAndTraders);
        // Mutates the global pvp game rule, so it runs alone in its own batch.
        SpellcraftGameTests.add("pvp_off", "allies_a6_pvp_off", AlliesGameTests::a6PvpOff);
    }

    /**
     * When a mob is on the player's scoreboard team, should be an ally and may have allies; a zombie on no team
     * should not be an ally.
     *
     * <p>Method under test: {@link Allies#isAllyOf}, and {@link Allies#mayHaveAllies}
     *
     * <p>Puts the player on a team, so it runs in the {@code team_mutation} batch; synchronous on purpose.
     */
    static void a1Team(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        withTeam(scoreboard, "spellcraft_allies_a1", team -> {
            Zombie teammate = helper.spawn(EntityTypes.ZOMBIE, SPOT);
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
            Zombie stranger = helper.spawn(EntityTypes.ZOMBIE, SPOT);

            // Act & Assert
            helper.assertTrue(Allies.isAllyOf(player, teammate), "a teammate is an ally");
            helper.assertFalse(Allies.isAllyOf(player, stranger), "a zombie on no team is not");
            helper.assertTrue(Allies.mayHaveAllies(teammate), "a teamed mob may have allies");
        });
        helper.succeed();
    }

    /**
     * When a wolf is tamed by the player, should be an ally and may have allies. The player has no team here, so the
     * pet is allied through ownership, not the scoreboard.
     *
     * <p>Method under test: {@link Allies#isAllyOf}, and {@link Allies#mayHaveAllies}
     */
    static void a2TamedPet(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        helper.assertTrue(player.getTeam() == null, "setup: the player is on no team");
        Wolf pet = helper.spawn(EntityTypes.WOLF, SPOT);

        // Act
        pet.tame(player);

        // Assert
        helper.assertTrue(Allies.isAllyOf(player, pet), "a tamed pet is an ally");
        helper.assertTrue(Allies.mayHaveAllies(pet), "an ownable mob may have allies");
        helper.succeed();
    }

    /**
     * When a mob carries the player's UUID as its summoner mark, should be an ally and may have allies; a mob marked
     * by another player should not be an ally. The zombie pins the attachment clause: a vex is already an {@code
     * OwnableEntity}, so a vex alone would pass without it.
     *
     * <p>Method under test: {@link Allies#isAllyOf}, and {@link Allies#mayHaveAllies}
     */
    static void a3SummonerAttachment(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vex mine = helper.spawn(EntityTypes.VEX, SPOT);
        mine.setData(AllyAttachments.SUMMONER, player.getUUID());
        Vex someoneElses = helper.spawn(EntityTypes.VEX, SPOT);
        someoneElses.setData(AllyAttachments.SUMMONER, UUID.randomUUID());
        Zombie markedZombie = helper.spawn(EntityTypes.ZOMBIE, SPOT);
        helper.assertFalse(Allies.mayHaveAllies(markedZombie), "setup: an unmarked zombie cannot have allies");

        // Act
        markedZombie.setData(AllyAttachments.SUMMONER, player.getUUID());

        // Assert
        helper.assertTrue(Allies.isAllyOf(player, mine), "a mob the player summoned is an ally");
        helper.assertFalse(Allies.isAllyOf(player, someoneElses), "another player's summon is not");
        helper.assertTrue(Allies.mayHaveAllies(mine), "a summoned mob may have allies");
        helper.assertTrue(Allies.isAllyOf(player, markedZombie), "a marked zombie, no vex, is an ally");
        helper.assertTrue(Allies.mayHaveAllies(markedZombie), "a marked zombie may have allies");
        helper.succeed();
    }

    /**
     * When a vex's saved {@code owner} is the player, should be an ally: vanilla stores the owner but never calls the
     * vex an ally of it, so only the {@code OwnableEntity} path can. A second vex owned by a stranger is the negative
     * control: it fails if the owner reference is not compared to the player. Both are built by
     * {@link GameTestSupport#vexOwnedBy}.
     *
     * <p>Method under test: {@link Allies#isAllyOf}, and {@link Allies#mayHaveAllies}
     */
    static void a4OwnableOwner(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        Vex vex = GameTestSupport.vexOwnedBy(level, player.getUUID());
        Vex strangersVex = GameTestSupport.vexOwnedBy(level, UUID.randomUUID());
        helper.assertTrue(vex.getOwnerReference() != null, "setup: the owner was read");
        helper.assertFalse(player.isAlliedTo(vex), "setup: vanilla does not ally the vex to its owner");

        // Act & Assert
        helper.assertTrue(Allies.isAllyOf(player, vex), "an ownable mob the player owns is an ally");
        helper.assertFalse(Allies.isAllyOf(player, strangersVex), "a vex another player owns is not an ally");
        helper.assertTrue(Allies.mayHaveAllies(strangersVex), "an ownable mob may have allies");
        helper.succeed();
    }

    /**
     * When a mob has no team, owner or mark, should be no ally and cannot have allies; the player is their own ally.
     *
     * <p>Method under test: {@link Allies#isAllyOf}, and {@link Allies#mayHaveAllies}
     */
    static void a5Strangers(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, SPOT);
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, SPOT);

        // Act & Assert
        helper.assertFalse(Allies.mayHaveAllies(zombie), "a plain zombie cannot have allies");
        helper.assertFalse(Allies.mayHaveAllies(creeper), "a plain creeper cannot have allies");
        helper.assertFalse(Allies.isAllyOf(player, zombie), "a zombie is not an ally");
        helper.assertFalse(Allies.isAllyOf(player, creeper), "a creeper is not an ally");
        helper.assertTrue(Allies.isAllyOf(player, player), "the player is their own ally");
        helper.succeed();
    }

    /**
     * When a villager and a wandering trader stand near a player with no team, should both be allies and may have
     * allies, though neither has a team, an owner or a summoner mark; a zombie villager (a {@code Zombie}, not an
     * {@code AbstractVillager}) should not be an ally (Evoking II §2.3).
     *
     * <p>Method under test: {@link Allies#isAllyOf}, and {@link Allies#mayHaveAllies}
     */
    static void a7VillagersAndTraders(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        helper.assertTrue(player.getTeam() == null, "setup: the player is on no team");
        Villager villager = helper.spawn(EntityTypes.VILLAGER, SPOT);
        villager.setNoAi(true);
        WanderingTrader trader = helper.spawn(EntityTypes.WANDERING_TRADER, SPOT);
        trader.setNoAi(true);
        ZombieVillager zombieVillager = helper.spawn(EntityTypes.ZOMBIE_VILLAGER, SPOT);
        zombieVillager.setNoAi(true);
        helper.assertFalse(villager.hasData(AllyAttachments.SUMMONER), "setup: the villager carries no mark");
        helper.assertFalse(trader.hasData(AllyAttachments.SUMMONER), "setup: the trader carries no mark");

        // Act & Assert
        helper.assertTrue(Allies.isAllyOf(player, villager), "a villager is an ally");
        helper.assertTrue(Allies.isAllyOf(player, trader), "a wandering trader is an ally");
        helper.assertTrue(Allies.mayHaveAllies(villager), "a villager may have allies");
        helper.assertTrue(Allies.mayHaveAllies(trader), "a trader may have allies");
        helper.assertFalse(Allies.isAllyOf(player, zombieVillager), "a zombie villager is not an ally");
        zombieVillager.discard();
        helper.succeed();
    }

    /**
     * When the {@code pvp} game rule is off, should not call a stranger survival player prey of a summoned vex, nor a
     * Bound Vex that stranger summoned (spec §3.4, the PvP rule), nor a mob summoned by an offline player, and should still call
     * a pig prey. With the rule on, the same stranger is prey (the positive control, also in the prey-rule test).
     *
     * <p>Method under test: {@link BoundVex#isPrey(ServerLevel, UUID, LivingEntity)}
     *
     * <p>Mutates a global game rule, so it runs alone in the {@code pvp_off} batch; the rule is restored through
     * {@code whenDone}.
     */
    static void a6PvpOff(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ServerPlayer summoner = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Pig pig = helper.spawn(EntityTypes.PIG, SPOT);
        BoundVex strangersVex = BoundVexGameTests.parked(helper, SPOT, stranger);
        Pig offlinesPig = helper.spawn(EntityTypes.PIG, SPOT);
        offlinesPig.setData(AllyAttachments.SUMMONER, UUID.randomUUID());
        GameRules rules = level.getGameRules();
        boolean saved = rules.get(GameRules.PVP);
        whenDone(helper, () -> {
            rules.set(GameRules.PVP, saved, level.getServer());
            pig.discard();
            offlinesPig.discard();
        });
        rules.set(GameRules.PVP, true, level.getServer());
        helper.assertTrue(
                BoundVex.isPrey(level, summoner.getUUID(), stranger), "setup: with pvp on, a stranger is prey");
        helper.assertTrue(
                BoundVex.isPrey(level, summoner.getUUID(), strangersVex),
                "setup: with pvp on, a stranger's Bound Vex is prey");
        helper.assertTrue(
                BoundVex.isPrey(level, summoner.getUUID(), offlinesPig),
                "setup: with pvp on, an offline player's summon is prey");

        // Act
        rules.set(GameRules.PVP, false, level.getServer());

        // Assert
        helper.assertFalse(
                BoundVex.isPrey(level, summoner.getUUID(), stranger), "with pvp off, a stranger player is not prey");
        helper.assertFalse(
                BoundVex.isPrey(level, summoner.getUUID(), strangersVex),
                "with pvp off, a stranger's Bound Vex is not prey");
        helper.assertFalse(
                BoundVex.isPrey(level, summoner.getUUID(), offlinesPig),
                "with pvp off, an offline player's summon is not prey");
        helper.assertTrue(BoundVex.isPrey(level, summoner.getUUID(), pig), "a pig is still prey with pvp off");
        helper.succeed();
    }
}
