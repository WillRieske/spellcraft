package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.A;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.C;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.D;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.assertBurned;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.assertSpared;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.floors;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.forget;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.light;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.stepInto;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.teleport;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.trailingPlayer;

import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.trailing.TrailFires;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;

/**
 * Iron golems as allies, the trail's golem clause, golem mercy and forgiveness, and the rule that a golem never targets
 * a summon (Evoking II spec §2.3). Registered in {@link SpellcraftGameTests}.
 */
public final class GolemGameTests {
    static final BlockPos SPOT = new BlockPos(1, 1, 1);

    private GolemGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "golems_ii_allies_unless_mad", GolemGameTests::alliesUnlessMad);
        SpellcraftGameTests.add(SHARED, "golems_ii_trail_spares_every_golem", GolemGameTests::trailSparesEveryGolem);
    }

    /**
     * A golem with no AI: no goals, so no target selection or movement. Its {@code aiStep} still runs, and with it the
     * anger upkeep ({@code updatePersistentAnger}, docs/api-notes.md §20), which is why {@link #anger} also starts the
     * anger timer.
     */
    static IronGolem golem(GameTestHelper helper, BlockPos relative) {
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, relative);
        golem.setNoAi(true);
        return golem;
    }

    /**
     * Makes {@code golem} mad at {@code player} the way vanilla does: target, persistent anger target and the anger
     * timer (without the timer the upkeep in {@code aiStep} would clear the anger on the next tick; Task 7's 35-tick
     * mercy test depends on this).
     */
    static void anger(IronGolem golem, ServerPlayer player) {
        golem.setTarget(player);
        golem.setPersistentAngerTarget(EntityReference.of(player));
        golem.startPersistentAngerTimer();
    }

    /**
     * When a calm golem stands near the player, should be an ally and may have allies; with the player as its target,
     * or with its persistent anger naming the player, should not be an ally; after it stops being angry, should be one
     * again.
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
        helper.succeed();
    }

    /**
     * When a calm golem, then a golem mad at the owner, then the same golem with the owner offline step into the
     * owner's flame, should none of them burn, while a zombie stepping in does (the flame works). The offline case
     * pins that the golem clause does not go through the ally path, which spares only while the owner is online.
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
        helper.succeed();
    }
}
