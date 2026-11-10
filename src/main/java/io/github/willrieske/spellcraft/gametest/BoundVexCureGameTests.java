package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.AIR_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.huskAt;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.noTargets;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.patientCreeper;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.stayingVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.CureEvents;
import io.github.willrieske.spellcraft.evoking.CureZombieVillagerGoal;
import io.github.willrieske.spellcraft.evoking.FleeCreeperGoal;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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
        FangGameTests.addPadded(SHARED, "bound_vex_cure_runs_its_timeline", BoundVexCureGameTests::cureRunsItsTimeline);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_ends_in_a_villager_with_ai_and_the_discount",
                BoundVexCureGameTests::cureEndsInAVillagerWithAiAndTheDiscount);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_a_new_target_does_not_stop_the_cure",
                BoundVexCureGameTests::aNewTargetDoesNotStopTheCure);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_an_attacker_is_claimed_by_a_busy_vex",
                BoundVexCureGameTests::anAttackerIsClaimedByABusyVex);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_a_creeper_interrupts_and_the_cure_restarts",
                BoundVexCureGameTests::aCreeperInterruptsAndTheCureRestarts);
        FangGameTests.addPadded(
                SHARED, "bound_vex_cure_approach_timeout_aborts", BoundVexCureGameTests::approachTimeoutAborts);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_the_summoner_leaving_aborts_the_cure",
                BoundVexCureGameTests::theSummonerLeavingAbortsTheCure);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_the_subject_dying_restores_the_hand",
                BoundVexCureGameTests::theSubjectDyingRestoresTheHand);
        FangGameTests.addPadded(
                SHARED, "bound_vex_cure_two_vexes_one_subject", BoundVexCureGameTests::twoVexesOneSubject);
        FangGameTests.addPadded(
                SHARED, "bound_vex_cure_two_vexes_two_subjects", BoundVexCureGameTests::twoVexesTwoSubjects);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_cure_a_stale_freeze_is_released", BoundVexCureGameTests::aStaleFreezeIsReleased);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_a_hand_fed_zombie_villager_keeps_its_conversion",
                BoundVexCureGameTests::aHandFedZombieVillagerKeepsItsConversion);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_a_no_ai_statue_is_never_claimed",
                BoundVexCureGameTests::aNoAiStatueIsNeverClaimed);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_cure_a_staying_vex_cures_only_in_its_area",
                BoundVexCureGameTests::aStayingVexCuresOnlyInItsArea);
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

    /**
     * A zombie villager with its AI on (the cure skips {@code NoAI} ones) but no goals, so it neither walks nor picks a
     * target on its own, no gravity and a leather helmet (against sunburn), {@code dx} blocks east and {@code dz}
     * blocks south of {@code at} (absolute, same height), discarded when the test ends.
     */
    private static ZombieVillager cureSubject(GameTestHelper helper, Vec3 at, double dx, double dz) {
        ZombieVillager zombieVillager = frozenZombieVillager(helper, at, dx, dz);
        zombieVillager.setNoAi(false);
        zombieVillager.goalSelector.removeAllGoals(goal -> true);
        zombieVillager.targetSelector.removeAllGoals(goal -> true);
        zombieVillager.setPersistenceRequired();
        return zombieVillager;
    }

    /**
     * When the test ends, discards what a cure leaves behind near {@code around}: the villager a conversion creates,
     * thrown potions and dropped items.
     */
    private static void discardCureLeftovers(GameTestHelper helper, Vec3 around) {
        whenDone(helper, () -> {
            AABB box = new AABB(around, around).inflate(12.0);
            ServerLevel level = helper.getLevel();
            level.getEntitiesOfClass(Villager.class, box).forEach(Entity::discard);
            level.getEntitiesOfClass(ThrownSplashPotion.class, box).forEach(Entity::discard);
            level.getEntitiesOfClass(ItemEntity.class, box).forEach(Entity::discard);
        });
    }

    /** A flag that turns true the first tick a splash potion is in the air within ten blocks of {@code around}. */
    private static AtomicBoolean watchForPotion(GameTestHelper helper, Vec3 around) {
        AtomicBoolean seen = new AtomicBoolean();
        BoundVexTestSupport.everyTick(helper, () -> {
            AABB box = new AABB(around, around).inflate(10.0);
            if (!helper.getLevel()
                    .getEntitiesOfClass(ThrownSplashPotion.class, box)
                    .isEmpty()) {
                seen.set(true);
            }
        });
        return seen;
    }

    /** Records in {@code ate} the UUID of every vex of {@code vexes} the tick it holds a golden apple. */
    private static void watchForApples(GameTestHelper helper, Set<UUID> ate, BoundVex... vexes) {
        BoundVexTestSupport.everyTick(helper, () -> {
            for (BoundVex vex : vexes) {
                if (vex.getMainHandItem().is(Items.GOLDEN_APPLE)) {
                    ate.add(vex.getUUID());
                }
            }
        });
    }

    /** Whether {@code vex} holds its own iron sword again, nothing conjured. */
    private static boolean swordBack(BoundVex vex) {
        return vex.getMainHandItem().is(Items.IRON_SWORD) && !vex.isConjured(EquipmentSlot.MAINHAND);
    }

    /** Whether {@code zombieVillager} is frozen and carries the cure mark. */
    private static boolean claimed(ZombieVillager zombieVillager) {
        return zombieVillager.isNoAi() && AllyAttachments.isBeingCured(zombieVillager);
    }

    /** Whether {@code zombieVillager} is neither frozen nor marked: released, or never taken. */
    private static boolean free(ZombieVillager zombieVillager) {
        return !zombieVillager.isNoAi() && !AllyAttachments.isBeingCured(zombieVillager);
    }

    /**
     * When a staying Bound Vex finds a zombie villager three blocks away, should claim it at once (frozen and marked),
     * then in order hold a golden apple, hold the cure potion, throw a splash potion, begin the conversion, and hold
     * its iron sword again with nothing conjured; should then be free to take a husk as a target; and should weaken
     * nobody standing near (the cure potion has no effects).
     *
     * <p>Method under test: {@link CureZombieVillagerGoal}, through the vex's goal selector: the four steps of spec
     * §2.2
     */
    static void cureRunsItsTimeline(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        Vec3 post = vex.position();
        ZombieVillager zombieVillager = cureSubject(helper, post, 3.0, 0.0);
        ServerPlayer bystander = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveTo(
                helper, bystander, helper.relativeVec(zombieVillager.position()).add(2.0, 0.0, 0.0));
        AtomicBoolean thrown = watchForPotion(helper, post);
        discardCureLeftovers(helper, post);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(claimed(zombieVillager), "claimed at once: frozen and marked"))
                .thenWaitUntil(() ->
                        helper.assertTrue(vex.getMainHandItem().is(Items.GOLDEN_APPLE), "the vex holds a golden apple"))
                .thenWaitUntil(() -> helper.assertTrue(
                        ItemStack.matches(vex.getMainHandItem(), CureZombieVillagerGoal.curePotion()),
                        "the vex holds the cure potion"))
                .thenWaitUntil(() -> helper.assertTrue(thrown.get(), "a splash potion was thrown"))
                .thenWaitUntil(() -> helper.assertTrue(zombieVillager.isConverting(), "the conversion began"))
                .thenWaitUntil(() -> helper.assertTrue(swordBack(vex), "the vex holds its sword, nothing conjured"))
                .thenExecute(() -> {
                    helper.assertTrue(claimed(zombieVillager), "the mark and the freeze stay while it converts");
                    Husk husk = huskAt(helper, post, -2.0, 0.0);
                    husk.setAbsorptionAmount(100000.0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(vex.getTarget() != null, "the vex is free to take a target"))
                .thenExecute(() -> {
                    helper.assertFalse(bystander.hasEffect(MobEffects.WEAKNESS), "the bystander is not weakened");
                    helper.assertFalse(player.hasEffect(MobEffects.WEAKNESS), "the summoner is not weakened");
                    helper.assertFalse(
                            zombieVillager.hasEffect(MobEffects.WEAKNESS), "the zombie villager is not weakened");
                })
                .thenSucceed();
    }

    /**
     * When a cure has begun and the conversion has one tick left, should a villager stand there within twenty ticks
     * with its AI on (the freeze is not inherited) and a positive reputation for the summoner (the discount), and no
     * zombie villager remain.
     *
     * <p>Method under test: {@link CureEvents#onConversion} and the starter passed to {@code startConverting}
     */
    static void cureEndsInAVillagerWithAiAndTheDiscount(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        Vec3 post = vex.position();
        ZombieVillager zombieVillager = cureSubject(helper, post, 3.0, 0.0);
        discardCureLeftovers(helper, post);
        List<Villager> cured = new ArrayList<>();

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(zombieVillager.isConverting(), "the conversion began"))
                .thenExecute(() -> zombieVillager.setVillagerConversionTime(1))
                .thenWaitUntil(() -> {
                    cured.clear();
                    cured.addAll(
                            helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(post, post).inflate(10.0)));
                    helper.assertFalse(cured.isEmpty(), "a villager stands where the zombie villager was");
                })
                .thenExecute(() -> {
                    Villager villager = cured.get(0);
                    helper.assertTrue(zombieVillager.isRemoved(), "no zombie villager remains");
                    helper.assertFalse(villager.isNoAi(), "the villager has its AI");
                    helper.assertFalse(AllyAttachments.isBeingCured(villager), "and no cure mark");
                    helper.assertTrue(
                            villager.getPlayerReputation(player) > 0,
                            "the summoner has a discount, got " + villager.getPlayerReputation(player));
                })
                .thenSucceed();
    }

    /**
     * When a husk becomes the vex's target during the apple step, should the cure still reach {@code isConverting()}
     * (a new target does not end the goal) with the vex still holding its target.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal#canContinueToUse}: it does not look at the vex's target
     */
    static void aNewTargetDoesNotStopTheCure(GameTestHelper helper) {
        // Arrange: no target goals, so only the test decides the vex's target
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = noTargets(stayingVex(helper, player, ItemStack.EMPTY));
        Vec3 post = vex.position();
        ZombieVillager zombieVillager = cureSubject(helper, post, 3.0, 0.0);
        discardCureLeftovers(helper, post);
        Husk husk = huskAt(helper, post, -2.0, 0.0);
        husk.setAbsorptionAmount(100000.0F);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(vex.getMainHandItem().is(Items.GOLDEN_APPLE), "setup: in the apple step"))
                .thenExecute(() -> vex.setTarget(husk))
                .thenWaitUntil(() -> helper.assertTrue(zombieVillager.isConverting(), "the cure reaches the end"))
                .thenExecute(() -> helper.assertTrue(husk.equals(vex.getTarget()), "the vex kept its new target"))
                .thenSucceed();
    }

    /**
     * When a staying Bound Vex is fighting a husk (a target), should claim a zombie villager whose own target is the
     * summoner, and not claim a nearer zombie villager that has no target while the vex still has its target.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal#canUse}: the target clause of spec §2.2
     */
    static void anAttackerIsClaimedByABusyVex(GameTestHelper helper) {
        // Arrange: the husk soaks up the vex's blows, so the vex keeps its target
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = noTargets(stayingVex(helper, player, ItemStack.EMPTY));
        Vec3 post = vex.position();
        Husk husk = huskAt(helper, post, -3.0, 0.0);
        husk.setAbsorptionAmount(100000.0F);
        vex.setTarget(husk);
        ZombieVillager attacker = cureSubject(helper, post, 4.0, 0.0);
        attacker.setTarget(player);
        ZombieVillager bystander = cureSubject(helper, post, 2.0, 0.0);
        discardCureLeftovers(helper, post);
        helper.assertTrue(player.equals(attacker.getTarget()), "setup: the attacker targets the summoner");

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(claimed(attacker), "the attacker is claimed"))
                .thenExecuteFor(30, () -> {
                    helper.assertTrue(husk.equals(vex.getTarget()), "the vex still has its target");
                    helper.assertTrue(free(bystander), "a zombie villager with no target is left alone");
                })
                .thenSucceed();
    }

    /**
     * When a hissing creeper comes within flee range of a staying vex in the apple step, should the cure be aborted (the
     * zombie villager unfrozen and unmarked, the vex's sword back) and, when the creeper is gone, start again and reach
     * {@code isConverting()}.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal#stop} as the abort, through {@link FleeCreeperGoal} taking
     * the move flag
     */
    static void aCreeperInterruptsAndTheCureRestarts(GameTestHelper helper) {
        // Arrange: a staying vex, so the zombie villager stays in the guard area while the vex flees
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = noTargets(stayingVex(helper, player, ItemStack.EMPTY));
        Vec3 post = vex.position();
        ZombieVillager zombieVillager = cureSubject(helper, post, 4.0, 0.0);
        discardCureLeftovers(helper, post);
        Creeper creeper = patientCreeper(helper, FEET);
        creeper.setNoGravity(true);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(vex.getMainHandItem().is(Items.GOLDEN_APPLE), "setup: in the apple step"))
                .thenExecute(() -> {
                    creeper.setPos(post.add(0.0, 0.0, 3.0));
                    creeper.setSwellDir(1);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(free(zombieVillager), "the zombie villager is unfrozen and unmarked");
                    helper.assertTrue(swordBack(vex), "the vex's sword is back");
                })
                .thenExecute(creeper::discard)
                .thenWaitUntil(() -> helper.assertTrue(zombieVillager.isConverting(), "the cure starts again"))
                .thenSucceed();
    }

    /**
     * When the vex cannot reach the zombie villager (held in place), should give up after {@code APPROACH_LIMIT} ticks:
     * the zombie villager is released (unfrozen, unmarked).
     *
     * <p>Method under test: {@link CureZombieVillagerGoal#canContinueToUse}: the approach timeout
     */
    static void approachTimeoutAborts(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        Vec3 post = vex.position();
        BoundVexTestSupport.holdAt(helper, vex, post);
        ZombieVillager zombieVillager = cureSubject(helper, post, 5.0, 0.0);
        discardCureLeftovers(helper, post);
        long[] claimedAt = {-1};

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(claimed(zombieVillager), "setup: claimed");
                    claimedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(free(zombieVillager), "released after the approach times out"))
                .thenExecuteFor(
                        30,
                        () -> helper.assertTrue(free(zombieVillager), "and not claimed again at once by the same vex"))
                .thenExecute(() -> {
                    long waited = helper.getTick() - claimedAt[0];
                    helper.assertTrue(
                            waited >= CureZombieVillagerGoal.APPROACH_LIMIT - 10,
                            "it did not give up early, after " + waited);
                    helper.assertTrue(swordBack(vex), "the hand is untouched");
                })
                .thenSucceed();
    }

    /**
     * When the summoner becomes a spectator (absent) in the apple step, should the cure be aborted: the zombie villager
     * is unfrozen and unmarked and the vex's hand restored.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal#canContinueToUse}: the summoner clause
     */
    static void theSummonerLeavingAbortsTheCure(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        Vec3 post = vex.position();
        ZombieVillager zombieVillager = cureSubject(helper, post, 3.0, 0.0);
        discardCureLeftovers(helper, post);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(vex.getMainHandItem().is(Items.GOLDEN_APPLE), "setup: in the apple step"))
                .thenExecute(() -> player.setGameMode(GameType.SPECTATOR))
                .thenWaitUntil(() -> {
                    helper.assertTrue(free(zombieVillager), "the zombie villager is released");
                    helper.assertTrue(swordBack(vex), "the vex's hand is restored");
                })
                .thenExecuteFor(
                        30,
                        () -> helper.assertTrue(
                                free(zombieVillager), "and nothing claims it while the summoner is away"))
                .thenSucceed();
    }

    /**
     * When the zombie villager dies in the apple step, should the vex hold its sword again with nothing conjured.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal#canContinueToUse}: the subject-alive clause, and {@code stop}
     */
    static void theSubjectDyingRestoresTheHand(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        Vec3 post = vex.position();
        ZombieVillager zombieVillager = cureSubject(helper, post, 3.0, 0.0);
        discardCureLeftovers(helper, post);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(vex.getMainHandItem().is(Items.GOLDEN_APPLE), "setup: in the apple step"))
                .thenExecute(() -> zombieVillager.kill(helper.getLevel()))
                .thenWaitUntil(() -> helper.assertTrue(swordBack(vex), "the vex holds its sword, nothing conjured"))
                .thenSucceed();
    }

    /**
     * When two staying vexes hang over one zombie villager, should exactly one of them ever hold the apple, and both
     * hold their swords once the conversion has begun.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal}'s claim (the mark and the claim map)
     */
    static void twoVexesOneSubject(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex first = stayingVex(helper, player, ItemStack.EMPTY);
        BoundVex second = stayingVex(helper, player, ItemStack.EMPTY);
        Vec3 post = first.position();
        ZombieVillager zombieVillager = cureSubject(helper, post, 3.0, 0.0);
        discardCureLeftovers(helper, post);
        Set<UUID> ate = new HashSet<>();
        watchForApples(helper, ate, first, second);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(zombieVillager.isConverting(), "the conversion began"))
                .thenExecuteFor(
                        30, () -> helper.assertTrue(ate.size() == 1, "exactly one vex fed it, got " + ate.size()))
                .thenExecute(
                        () -> helper.assertTrue(swordBack(first) && swordBack(second), "both hold their swords again"))
                .thenSucceed();
    }

    /**
     * When two staying vexes, eight blocks apart, each have a zombie villager three blocks away (outside the other's
     * stay area), should each vex work its own and both zombie villagers end up converting.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal}, through two vexes (neighboring entity ids differ in
     * parity: the goal clock)
     */
    static void twoVexesTwoSubjects(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex first = stayingVex(helper, player, ItemStack.EMPTY);
        BoundVex second = BoundVexTestSupport.boundVex(helper, AIR_SPOT.south(8), player);
        second.setStaying(true);
        ZombieVillager firstSubject = cureSubject(helper, first.position(), 3.0, 0.0);
        ZombieVillager secondSubject = cureSubject(helper, second.position(), 3.0, 0.0);
        discardCureLeftovers(helper, first.position());
        discardCureLeftovers(helper, second.position());
        Set<UUID> ate = new HashSet<>();
        watchForApples(helper, ate, first, second);

        // Act & Assert
        helper.succeedWhen(() -> {
            helper.assertTrue(firstSubject.isConverting(), "the first subject is converting");
            helper.assertTrue(secondSubject.isConverting(), "the second subject is converting");
            helper.assertTrue(ate.size() == 2, "each vex fed one, got " + ate.size());
        });
    }

    /**
     * When a zombie villager has {@code NoAI} and a cure mark whose age is 301 ticks, should have its AI back and no
     * mark on its next tick; with a mark 10 ticks old, should stay frozen and marked; a converting one with an old mark
     * stays frozen and marked.
     *
     * <p>Method under test: {@link CureEvents#onEntityTick}: the release after {@link CureEvents#RELEASE_AFTER}
     */
    static void aStaleFreezeIsReleased(GameTestHelper helper) {
        // Arrange
        long now = helper.getLevel().getGameTime();
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(FEET));
        ZombieVillager stale = frozenZombieVillager(helper, at, 0.0, 0.0);
        stale.setData(AllyAttachments.CURE, now - (CureEvents.RELEASE_AFTER + 1));
        ZombieVillager fresh = frozenZombieVillager(helper, at, 1.0, 0.0);
        fresh.setData(AllyAttachments.CURE, now - 10);
        ZombieVillager converting = frozenZombieVillager(helper, at, -1.0, 0.0);
        converting.setData(AllyAttachments.CURE, now - (CureEvents.RELEASE_AFTER + 100));
        converting.startConverting(null, 3000);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(free(stale), "the stale freeze is released"))
                .thenExecuteFor(5, () -> {
                    helper.assertTrue(claimed(fresh), "a young mark stays frozen and marked");
                    helper.assertTrue(claimed(converting), "a converting one with an old mark stays");
                })
                .thenSucceed();
    }

    /**
     * When a player feeds the zombie villager by hand during the apple step (a conversion with another starter and
     * 3000 ticks), should the vex's step 4 leave vanilla's conversion as it is: still converting with the other
     * starter and about 3000 ticks left.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal}: step 4 skips {@code startConverting} for a converting zombie
     * villager
     */
    static void aHandFedZombieVillagerKeepsItsConversion(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        ServerPlayer other = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        Vec3 post = vex.position();
        ZombieVillager zombieVillager = cureSubject(helper, post, 3.0, 0.0);
        discardCureLeftovers(helper, post);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() ->
                        helper.assertTrue(vex.getMainHandItem().is(Items.GOLDEN_APPLE), "setup: in the apple step"))
                .thenExecute(() -> zombieVillager.startConverting(other.getUUID(), 3000))
                .thenWaitUntil(() -> helper.assertTrue(swordBack(vex), "the goal ran to its end"))
                .thenExecute(() -> {
                    CompoundTag tag = GameTestSupport.saved(zombieVillager);
                    int left = tag.getIntOr("ConversionTime", -1);
                    helper.assertTrue(left > 2500 && left <= 3000, "the conversion time is untouched, got " + left);
                    helper.assertValueEqual(
                            tag.read("ConversionPlayer", UUIDUtil.CODEC).orElse(null),
                            other.getUUID(),
                            "the starter is the player who fed it");
                })
                .thenSucceed();
    }

    /**
     * When a zombie villager with {@code NoAI} and no mark (a map-made statue) stands three blocks from a staying vex
     * for forty ticks, should it stay unmarked and the vex keep its sword.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal#canUse}: the {@code isNoAi} clause
     */
    static void aNoAiStatueIsNeverClaimed(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        ZombieVillager statue = frozenZombieVillager(helper, vex.position(), 3.0, 0.0);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(40, () -> {
                    helper.assertFalse(AllyAttachments.isBeingCured(statue), "the statue is never marked");
                    helper.assertTrue(swordBack(vex), "the vex holds its sword");
                })
                .thenSucceed();
    }

    /**
     * When a staying vex has a zombie villager five blocks from its post and another nine blocks away, should claim the
     * near one and leave the far one (outside the stay area) alone.
     *
     * <p>Method under test: {@link CureZombieVillagerGoal#canUse}: the stay area, through {@code BoundVex.inGuardArea}
     */
    static void aStayingVexCuresOnlyInItsArea(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = stayingVex(helper, player, ItemStack.EMPTY);
        Vec3 post = vex.position();
        ZombieVillager near = cureSubject(helper, post, 5.0, 0.0);
        ZombieVillager far = cureSubject(helper, post, -9.0, 0.0);
        discardCureLeftovers(helper, post);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(claimed(near), "the near one is claimed"))
                .thenExecuteFor(40, () -> helper.assertTrue(free(far), "the far one is left alone"))
                .thenSucceed();
    }
}
