package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.CRIT_HEALTH;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.critHusk;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.padded;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.plainVictim;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.FEET;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of the Bound Vex's critical hits in the End: the tripled melee hit, what it does not apply to
 * (arrows, spared allies, the Nether) and how it keeps golem mercy and the player-versus-player rule.
 */
public final class BoundVexCritGameTests {
    /** How close a measured tripled loss must be to three times the plain one. */
    private static final float CRIT_TOLERANCE = 0.01F;

    private BoundVexCritGameTests() {}

    static void registerAll() {
        padded("bound_vex_world_crit_in_the_end", BoundVexCritGameTests::critInTheEnd);
        padded("bound_vex_world_crit_not_for_arrows", BoundVexCritGameTests::critNotForArrows);
        padded("bound_vex_world_crit_not_when_spared", BoundVexCritGameTests::critNotWhenSpared);
        padded("bound_vex_world_crit_keeps_golem_mercy", BoundVexCritGameTests::critKeepsGolemMercy);
        padded(
                "bound_vex_world_crit_on_a_player_under_the_pvp_rule",
                BoundVexCritGameTests::critOnAPlayerUnderThePvpRule);
        padded("bound_vex_world_no_crit_in_the_nether", BoundVexCritGameTests::noCritInTheNether);
    }

    /** The health {@code victim} loses to one melee hit by {@code vex}. */
    private static float meleeLoss(BoundVex vex, LivingEntity victim) {
        float before = victim.getHealth();
        vex.doHurtTarget((ServerLevel) vex.level(), victim);
        return before - victim.getHealth();
    }

    /**
     * What one melee hit by a parked vex of {@code player} costs a plain husk in the Overworld test level, checked
     * against the vex's attack damage so that a crit leaking into the baseline cannot hide. Call it
     * while {@code player} is still in that level (a vex whose summoner is in another level has no backstop to pass).
     */
    private static float overworldHuskLoss(GameTestHelper helper, ServerPlayer player) {
        BoundVex vex = BoundVexTestSupport.parked(helper, FEET.offset(0, 3, 4), player);
        Husk husk =
                critHusk(helper, helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(FEET.offset(2, 1, 0))));
        float loss = meleeLoss(vex, husk);
        helper.assertTrue(
                Math.abs(loss - vex.getAttributeValue(Attributes.ATTACK_DAMAGE)) < CRIT_TOLERANCE,
                "setup: the Overworld hit is the vex's plain attack damage, not " + loss);
        return loss;
    }

    /** A vex of {@code player} next to them in {@code level}, with no AI. */
    private static BoundVex critVex(GameTestHelper helper, ServerLevel level, ServerPlayer player) {
        BoundVex vex = vexIn(helper, level, player.position().add(1.0, 1.0, 0.0), player);
        vex.setNoAi(true);
        return vex;
    }

    /** A husk 2 blocks west of {@code player} in {@code level}. */
    private static Husk critHuskBeside(GameTestHelper helper, ServerLevel level, ServerPlayer player) {
        return critHusk(helper, level, player.position().add(-2.0, 0.0, 0.0));
    }

    /**
     * When a Bound Vex hits a husk in the End, should the health lost be three times what the same hit costs a husk
     * in the Overworld, within 0.01 (the Overworld cost is measured, not assumed).
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: the End crit
     */
    static void critInTheEnd(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        float plain = overworldHuskLoss(helper, player);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        Husk husk = critHuskBeside(helper, end, player);

        // Act
        float loss = meleeLoss(vex, husk);

        // Assert
        helper.assertTrue(
                Math.abs(loss - 3.0F * plain) < CRIT_TOLERANCE, "the End hit costs 3 x " + plain + ", not " + loss);
        helper.succeed();
    }

    /**
     * When a Bound Vex's arrow (its direct entity is the arrow) deals 4 to a husk in the End, should the husk lose 4.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: only the vex's own melee crits
     */
    static void critNotForArrows(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        Husk husk = critHuskBeside(helper, end, player);
        Arrow arrow = new Arrow(end, vex, new ItemStack(Items.ARROW), null);

        // Act
        husk.hurtServer(end, end.damageSources().arrow(arrow, vex), 4.0F);

        // Assert
        helper.assertValueEqual(CRIT_HEALTH - husk.getHealth(), 4.0F, "the arrow costs its plain 4");
        helper.succeed();
    }

    /**
     * When a Bound Vex in the End hits its summoner's tamed wolf, should the wolf's health not change (the backstop
     * spares it, and a spared hit is not tripled). Whether the crit was skipped is not observable here: a canceled
     * hit does nothing at any size.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: the backstop comes first
     */
    static void critNotWhenSpared(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        Wolf wolf = EntityTypes.WOLF.create(end, EntitySpawnReason.COMMAND);
        if (wolf == null) {
            throw helper.assertionException("could not create a wolf");
        }
        Vec3 at = player.position().add(-2.0, 0.0, 0.0);
        wolf.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        wolf.setNoAi(true);
        wolf.setNoGravity(true);
        wolf.setPersistenceRequired();
        wolf.tame(player);
        end.addFreshEntity(wolf);
        whenDone(helper, wolf::discard);
        float health = wolf.getHealth();

        // Act
        vex.doHurtTarget(end, wolf);

        // Assert
        helper.assertValueEqual(wolf.getHealth(), health, "the wolf is unhurt");
        helper.succeed();
    }

    /**
     * When a Bound Vex in the End hits an iron golem that is mad at its summoner and stands at 30 health, should the
     * golem stop at 25: mercy clamps the tripled hit as it does a plain one.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage} and {@code GolemMercy.onDamagePre}
     */
    static void critKeepsGolemMercy(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        IronGolem golem = EntityTypes.IRON_GOLEM.create(end, EntitySpawnReason.COMMAND);
        if (golem == null) {
            throw helper.assertionException("could not create a golem");
        }
        Vec3 at = player.position().add(-2.0, 0.0, 0.0);
        golem.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        golem.setNoAi(true);
        golem.setNoGravity(true);
        golem.setPersistenceRequired();
        golem.setHealth(30.0F);
        end.addFreshEntity(golem);
        whenDone(helper, golem::discard);
        GolemGameTests.anger(golem, player);
        helper.assertTrue(vex.isPrey(golem), "setup: a golem mad at the summoner is prey");

        // Act
        vex.doHurtTarget(end, golem);

        // Assert
        helper.assertValueEqual(golem.getHealth(), 25.0F, "the golem stops at 25");
        helper.succeed();
    }

    /**
     * When a Bound Vex hits a second player, who is prey under the server's PvP rule, in the End, should the health
     * lost be three times what the same hit costs another such player in the Overworld, within 0.01 (both measured,
     * neither wearing armor).
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: the crit on a player victim
     */
    static void critOnAPlayerUnderThePvpRule(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerPlayer overworldVictim = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        plainVictim(overworldVictim);
        BoundVex overworldVex = BoundVexTestSupport.parked(helper, FEET.offset(0, 3, 4), player);
        helper.assertTrue(helper.getLevel().isPvpAllowed(), "setup: pvp is on");
        helper.assertTrue(overworldVex.isPrey(overworldVictim), "setup: the Overworld victim is prey");
        float plain = meleeLoss(overworldVex, overworldVictim);
        helper.assertTrue(plain > 0.0F, "setup: the Overworld hit hurts (" + plain + ")");
        ServerLevel end = BoundVexWorldTestSupport.room(helper, player, Level.END, 3, 4);
        BoundVex vex = critVex(helper, end, player);
        ServerPlayer victim = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        whenDone(helper, () -> MockPlayers.moveTo(helper, victim, FangGameTests.STAND));
        Vec3 at = player.position().add(-2.0, 0.0, 0.0);
        victim.teleportTo(end, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, false);
        victim.hasChangedDimension(); // a mock player is never ticked, which would end the dimension change's immunity
        plainVictim(victim);
        helper.assertTrue(vex.isPrey(victim), "setup: the End victim is prey");

        // Act
        float loss = meleeLoss(vex, victim);

        // Assert
        helper.assertTrue(
                Math.abs(loss - 3.0F * plain) < CRIT_TOLERANCE, "the End hit costs 3 x " + plain + ", not " + loss);
        helper.succeed();
    }

    /**
     * When a Bound Vex hits a husk in a Nether room, should the health lost equal what the same hit costs a husk in
     * the Overworld.
     *
     * <p>Method under test: {@code BoundVexEvents.onIncomingDamage}: the crit is for the End only
     */
    static void noCritInTheNether(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        float plain = overworldHuskLoss(helper, player);
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, 3, 4);
        BoundVex vex = critVex(helper, nether, player);
        Husk husk = critHuskBeside(helper, nether, player);

        // Act
        float loss = meleeLoss(vex, husk);

        // Assert
        helper.assertTrue(Math.abs(loss - plain) < CRIT_TOLERANCE, "the Nether hit costs " + plain + ", not " + loss);
        helper.succeed();
    }
}
