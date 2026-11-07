package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
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
    }

    static void a1Team(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("spellcraft_allies_a1");
        try {
            Zombie teammate = helper.spawn(EntityTypes.ZOMBIE, SPOT);
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
            Zombie stranger = helper.spawn(EntityTypes.ZOMBIE, SPOT);
            helper.assertTrue(Allies.isAllyOf(player, teammate), "a teammate is an ally");
            helper.assertFalse(Allies.isAllyOf(player, stranger), "a zombie on no team is not");
            helper.assertTrue(Allies.mayHaveAllies(teammate), "a teamed mob may have allies");
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }

    /**
     * The player has no team here, so the pet is allied through ownership, not the scoreboard.
     *
     * <p>Method under test: {@link Allies#isAllyOf}
     */
    static void a2TamedPet(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        helper.assertTrue(player.getTeam() == null, "setup: the player is on no team");
        Wolf pet = helper.spawn(EntityTypes.WOLF, SPOT);
        pet.tame(player);
        helper.assertTrue(Allies.isAllyOf(player, pet), "a tamed pet is an ally");
        helper.assertTrue(Allies.mayHaveAllies(pet), "an ownable mob may have allies");
        helper.succeed();
    }

    static void a3SummonerAttachment(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vex mine = helper.spawn(EntityTypes.VEX, SPOT);
        mine.setData(AllyAttachments.SUMMONER, player.getUUID());
        Vex someoneElses = helper.spawn(EntityTypes.VEX, SPOT);
        someoneElses.setData(AllyAttachments.SUMMONER, UUID.randomUUID());
        helper.assertTrue(Allies.isAllyOf(player, mine), "a mob the player summoned is an ally");
        helper.assertFalse(Allies.isAllyOf(player, someoneElses), "another player's summon is not");
        helper.assertTrue(Allies.mayHaveAllies(mine), "a summoned mob may have allies");
        helper.succeed();
    }

    /**
     * A vex whose saved {@code owner} is the player: vanilla stores the owner but never calls the vex an ally of
     * it, so only the {@code OwnableEntity} path can. A second vex owned by a stranger is the negative control: it
     * fails if the owner reference is not compared to the player. Both are built by
     * {@link GameTestSupport#vexOwnedBy}.
     */
    static void a4OwnableOwner(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        Vex vex = GameTestSupport.vexOwnedBy(level, player.getUUID());
        Vex strangersVex = GameTestSupport.vexOwnedBy(level, UUID.randomUUID());
        helper.assertTrue(vex.getOwnerReference() != null, "setup: the owner was read");
        helper.assertFalse(player.isAlliedTo(vex), "setup: vanilla does not ally the vex to its owner");
        helper.assertTrue(Allies.isAllyOf(player, vex), "an ownable mob the player owns is an ally");
        helper.assertFalse(Allies.isAllyOf(player, strangersVex), "a vex another player owns is not an ally");
        helper.assertTrue(Allies.mayHaveAllies(strangersVex), "an ownable mob may have allies");
        helper.succeed();
    }

    static void a5Strangers(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, SPOT);
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, SPOT);
        helper.assertFalse(Allies.mayHaveAllies(zombie), "a plain zombie cannot have allies");
        helper.assertFalse(Allies.mayHaveAllies(creeper), "a plain creeper cannot have allies");
        helper.assertFalse(Allies.isAllyOf(player, zombie), "a zombie is not an ally");
        helper.assertFalse(Allies.isAllyOf(player, creeper), "a creeper is not an ally");
        helper.assertTrue(Allies.isAllyOf(player, player), "the player is their own ally");
        helper.succeed();
    }
}
