package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.AIR_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.huskAt;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.stayingVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Bound Vex GameTests (Evoking II Parts 4-5 spec §2-§3, §9): the cure and the bribe. Tests whose vex flies or that
 * need headroom register through {@code FangGameTests.addPadded} (padding 13); the rest run inside the shared 3x3x3
 * box. Shared constants and helpers live in {@link BoundVexTestSupport}.
 */
public final class BoundVexCureGameTests {
    private BoundVexCureGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_a_zombie_villager_is_never_prey",
                BoundVexCureGameTests::aZombieVillagerIsNeverPrey);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_bribe_a_bribed_vex_is_not_prey", BoundVexCureGameTests::aBribedVexIsNotPrey);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_a_zombie_villager_being_cured_is_an_ally",
                BoundVexCureGameTests::aZombieVillagerBeingCuredIsAnAlly);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_a_golem_leaves_a_zombie_villager_being_cured",
                BoundVexCureGameTests::aGolemLeavesAZombieVillagerBeingCured);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_cure_marks_survive_a_save", BoundVexCureGameTests::marksSurviveASave);
    }

    /**
     * A zombie villager with no AI and no gravity and a leather helmet (against sunburn), {@code dx} blocks east and
     * {@code dz} blocks south of {@code at} (absolute, same height), discarded when the test ends. It is spawned in the
     * structure and moved by absolute position: a rotated structure puts relative coordinates a block off.
     */
    private static ZombieVillager frozenZombieVillager(GameTestHelper helper, Vec3 at, double dx, double dz) {
        ZombieVillager zombieVillager = helper.spawn(EntityTypes.ZOMBIE_VILLAGER, FEET);
        zombieVillager.setNoAi(true);
        zombieVillager.setNoGravity(true);
        zombieVillager.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombieVillager.setPos(at.add(dx, 0.0, dz));
        whenDone(helper, zombieVillager::discard);
        return zombieVillager;
    }

    /**
     * When a staying Bound Vex has a zombie villager and a husk three blocks away on either side, should take the husk
     * and never the zombie villager: not as prey, not as a patrol target, and not as a grudge after it hurt the
     * summoner.
     *
     * <p>Method under test: {@link BoundVex#isPrey(net.minecraft.world.entity.LivingEntity)}, through the vex's target
     * goals
     */
    static void aZombieVillagerIsNeverPrey(GameTestHelper helper) {
        // Arrange: no movement goals (the target goals are the subject), a staying vex so the guard box is fixed
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        vex.goalSelector.removeAllGoals(goal -> true);
        ZombieVillager zombieVillager = frozenZombieVillager(helper, vex.position(), 3.0, 0.0);
        Husk husk = huskAt(helper, vex.position(), -3.0, 0.0);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(husk.equals(vex.getTarget()), "setup: the vex patrols and takes the husk"))
                .thenExecute(() -> {
                    helper.assertFalse(vex.isPrey(zombieVillager), "a zombie villager is not prey");
                    helper.assertFalse(
                            BoundVex.isPrey(helper.getLevel(), player.getUUID(), zombieVillager),
                            "the static rule says the same");
                    husk.discard();
                    vex.setTarget(null);
                    player.setLastHurtByMob(zombieVillager);
                })
                .thenExecuteFor(
                        30,
                        () -> helper.assertTrue(
                                !zombieVillager.equals(vex.getTarget()),
                                "not even a zombie villager that hurt the summoner is taken"))
                .thenSucceed();
    }

    /**
     * When a vanilla vex carries the bribed mark, should {@link BoundVex#isPrey} answer false for it; without the
     * mark, or with only the bribe-tried mark, it is prey.
     *
     * <p>Method under test: {@link BoundVex#isPrey(ServerLevel, java.util.UUID, net.minecraft.world.entity.LivingEntity)}
     */
    static void aBribedVexIsNotPrey(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        ServerLevel level = helper.getLevel();
        Vex vex = helper.spawn(EntityTypes.VEX, BoundVexTestSupport.VEX_SPOT);
        vex.setNoAi(true);
        whenDone(helper, vex::discard);

        // Act & Assert
        helper.assertTrue(BoundVex.isPrey(level, player.getUUID(), vex), "setup: a plain vex is prey");
        vex.setData(AllyAttachments.BRIBE_TRIED, true);
        helper.assertTrue(BoundVex.isPrey(level, player.getUUID(), vex), "an offered but not bribed vex is still prey");
        vex.setData(AllyAttachments.BRIBED, true);
        helper.assertFalse(BoundVex.isPrey(level, player.getUUID(), vex), "a bribed vex is not prey");
        helper.succeed();
    }

    /**
     * When a zombie villager carries the cure mark, should {@link Allies#isAllyOf} be true for the summoner and the
     * mark count in {@link Allies#mayHaveAllies}, and the summoner's fangs spare it while they bite an unmarked one.
     *
     * <p>Method under test: {@link Allies#isAllyOf} and {@link Allies#mayHaveAllies}, through fangs
     */
    static void aZombieVillagerBeingCuredIsAnAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(AIR_SPOT));
        ZombieVillager marked = frozenZombieVillager(helper, at, 3.0, 0.0);
        ZombieVillager plain = frozenZombieVillager(helper, at, -3.0, 0.0);
        marked.setData(AllyAttachments.CURE, level.getGameTime());
        float markedHealth = marked.getHealth();
        float plainHealth = plain.getHealth();

        // Act
        helper.assertTrue(Allies.isAllyOf(player, marked), "a marked zombie villager is an ally");
        helper.assertFalse(Allies.isAllyOf(player, plain), "an unmarked one is not");
        helper.assertTrue(Allies.mayHaveAllies(marked), "the fast path does not skip a marked one");
        helper.assertFalse(Allies.mayHaveAllies(plain), "the fast path still skips an unmarked one");
        for (ZombieVillager victim : new ZombieVillager[] {marked, plain}) {
            level.addFreshEntity(new EvokerFangs(level, victim.getX(), victim.getY(), victim.getZ(), 0.0F, 0, player));
        }

        // Assert: the fangs bite on their 8th tick
        helper.runAfterDelay(12, () -> {
            helper.assertValueEqual(marked.getHealth(), markedHealth, "the fangs spare the marked zombie villager");
            helper.assertTrue(plain.getHealth() < plainHealth, "the fangs bite the unmarked one");
            helper.succeed();
        });
    }

    /**
     * When an iron golem with its full AI stands three blocks from a zombie villager that carries the cure mark, should
     * never target it in sixty ticks; once the mark is removed, should take it as a target (a golem attacks every
     * enemy).
     *
     * <p>Method under test: {@code GolemMercy.onChangeTarget} (the cure mark)
     */
    static void aGolemLeavesAZombieVillagerBeingCured(GameTestHelper helper) {
        // Arrange: a golem on stone with its AI, the zombie villager on stone beside it
        FangGameTests.caster(helper, NORTH);
        BlockPos golemSpot = FEET.east(4);
        helper.setBlock(golemSpot.below(), Blocks.STONE);
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, golemSpot);
        whenDone(helper, golem::discard);
        ZombieVillager zombieVillager = frozenZombieVillager(helper, golem.position(), 3.0, 0.0);
        zombieVillager.setData(AllyAttachments.CURE, helper.getLevel().getGameTime());

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        60,
                        () -> helper.assertTrue(
                                !zombieVillager.equals(golem.getTarget()),
                                "the golem leaves a zombie villager being cured alone"))
                .thenExecute(() -> zombieVillager.removeData(AllyAttachments.CURE))
                .thenWaitUntil(() -> helper.assertTrue(
                        zombieVillager.equals(golem.getTarget()), "without the mark the golem takes it"))
                .thenSucceed();
    }

    /**
     * When a zombie villager with the cure mark and a vex with the bribed and bribe-tried marks are saved and loaded
     * into fresh entities, should the copies carry the marks (the zombie villager's with its game time).
     *
     * <p>Method under test: {@link AllyAttachments} (the three marks' serializers), through {@link
     * GameTestSupport#reloaded}
     */
    static void marksSurviveASave(GameTestHelper helper) {
        // Arrange
        ServerLevel level = helper.getLevel();
        ZombieVillager zombieVillager = helper.spawn(EntityTypes.ZOMBIE_VILLAGER, FEET);
        zombieVillager.setNoAi(true);
        whenDone(helper, zombieVillager::discard);
        zombieVillager.setData(AllyAttachments.CURE, 1234L);
        Vex vex = helper.spawn(EntityTypes.VEX, FEET);
        vex.setNoAi(true);
        whenDone(helper, vex::discard);
        vex.setData(AllyAttachments.BRIBED, true);
        vex.setData(AllyAttachments.BRIBE_TRIED, true);

        // Act
        ZombieVillager loadedZombieVillager = (ZombieVillager) GameTestSupport.reloaded(level, zombieVillager);
        Vex loadedVex = (Vex) GameTestSupport.reloaded(level, vex);

        // Assert
        helper.assertTrue(AllyAttachments.isBeingCured(loadedZombieVillager), "the cure mark is kept");
        helper.assertValueEqual(
                loadedZombieVillager.getExistingData(AllyAttachments.CURE).orElse(-1L),
                1234L,
                "the cure mark keeps its game time");
        helper.assertTrue(AllyAttachments.isBribed(loadedVex), "the bribed mark is kept");
        helper.assertTrue(AllyAttachments.wasOfferedBribe(loadedVex), "the bribe-tried mark is kept");
        helper.assertFalse(AllyAttachments.isBeingCured(loadedVex), "no cure mark appears from nowhere");
        helper.succeed();
    }
}
