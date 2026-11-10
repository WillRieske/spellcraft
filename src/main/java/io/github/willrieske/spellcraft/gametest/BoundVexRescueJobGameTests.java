package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.SIDE_TOLERANCE;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.endVoid;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.platform;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.set;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.soak;
import static io.github.willrieske.spellcraft.gametest.BoundVexWorldTestSupport.vexIn;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.AttackCrystalGoal;
import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.BribeGoal;
import io.github.willrieske.spellcraft.evoking.CureZombieVillagerGoal;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.VexRescues;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of a rescue that preempts a real job: a Bound Vex with its full AI is in the middle of a cure, a bribe, a
 * repair or a flight to a crystal when its summoner needs it (a lava pull in a Nether room, a void catch in the End).
 * In every test the vex is held at the summoner's side within a few ticks, the job's state is given back cleanly, the
 * tail shows its item in the vex's hand (even when the job held the hand at the tail's start), the sword comes back
 * when the rescue ends and the job starts over.
 */
public final class BoundVexRescueJobGameTests {
    /** How many ticks a body may take: a job restarts from nothing after a rescue and a tail of 20 ticks. */
    private static final int BODY_TICKS = 800;

    /** How far the Nether room reaches sideways: room for every job and a second vex. */
    private static final int ROOM_HALF_WIDTH = 8;

    /** How tall the Nether room is. */
    private static final int ROOM_HEIGHT = 8;

    /** How far the End test places reach: the platform stands 10 blocks east and a crystal 20 farther. */
    private static final int VOID_REACH = 48;

    /** How far east of the End slot's column the player's base stands. */
    private static final int BASE_EAST = 10;

    /** How far under the End's lowest Y the fallen player is. */
    private static final int BELOW_FLOOR = 5;

    /** How many ticks after the rescue starts the job must be given back and the vex held at the side. */
    private static final int WITHIN = 3;

    /** The summoner's yaw in these tests is 0: the side point is 0.6 west and 1.2 above their feet. */
    private static final Vec3 SIDE_OFFSET = new Vec3(-0.6, 1.2, 0.0);

    /** The health of a lava test's player: lava deals 4 a tick twice a step. */
    private static final float HEALTH = 100000.0F;

    /** The health a player has otherwise. */
    private static final float NORMAL_HEALTH = 20.0F;

    private BoundVexRescueJobGameTests() {}

    /**
     * One job arranged for a rescue to preempt.
     *
     * @param vex the vex that does the job (and is the rescuer)
     * @param running whether the job is in the step the test wants to preempt
     * @param givenBack asserts that the job's state was given back cleanly (the subject free, the claim free)
     * @param atTrigger runs when the rescue is triggered
     * @param whileRescued whether the evidence that needs the rescue to go on for a while is in (the claim freed for
     *     another vex); true at once for a job without such evidence
     * @param beforeRestart runs when the rescue is over, before the job is expected to start again
     * @param restarted whether the job started over and got on
     */
    private record Job(
            BoundVex vex,
            BooleanSupplier running,
            Runnable givenBack,
            Runnable atTrigger,
            BooleanSupplier whileRescued,
            Runnable beforeRestart,
            BooleanSupplier restarted) {}

    /** Builds a job around {@code base} (the player's feet block) in {@code level}. */
    @FunctionalInterface
    private interface JobMaker {
        /**
         * Builds the job.
         *
         * @param helper the test helper
         * @param level the level the job is in
         * @param player the summoner, already standing at {@code base}
         * @param base the summoner's feet block
         * @return the job
         */
        Job make(GameTestHelper helper, ServerLevel level, ServerPlayer player, BlockPos base);
    }

    static void registerAll() {
        add("bound_vex_world_rescue_lava_preempts_a_cure_in_the_apple_step", cure(false), true);
        add("bound_vex_world_rescue_lava_preempts_a_cure_in_the_potion_step", cure(true), true);
        add("bound_vex_world_rescue_lava_preempts_a_bribe_offer", bribe(), true);
        add("bound_vex_world_rescue_lava_preempts_a_repair", repair(), true);
        add("bound_vex_world_rescue_void_preempts_a_cure_in_the_potion_step", cure(true), false);
        add("bound_vex_world_rescue_void_preempts_a_bribe_offer", bribe(), false);
        add("bound_vex_world_rescue_void_preempts_a_flight_to_a_crystal", crystal(), false);
    }

    /** Registers a job test: lava in a Nether room when {@code lava}, a void catch in the End otherwise. */
    private static void add(String name, JobMaker maker, boolean lava) {
        FangGameTests.addPadded(SHARED, name, BODY_TICKS, helper -> {
            if (lava) {
                lavaTest(helper, maker);
            } else {
                voidTest(helper, maker);
            }
        });
    }

    // ---- The arenas ----

    /**
     * A survival player at the padded box's stand with so much health that the lava of a test cannot kill them. Not
     * {@code abilities.invulnerable}: a player with it is no valid target ({@code canBeSeenAsEnemy}), and the bribe's
     * enemy vex must target the summoner.
     */
    static ServerPlayer rescuePlayer(GameTestHelper helper) {
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            throw helper.assertionException("setup: the player has no max health attribute");
        }
        maxHealth.setBaseValue(HEALTH);
        player.setHealth(HEALTH);
        whenDone(helper, () -> {
            maxHealth.setBaseValue(NORMAL_HEALTH);
            player.setHealth(NORMAL_HEALTH);
        });
        return player;
    }

    /**
     * A Nether room with {@code player} standing in it, and around the two blocks of their column (lava later) a
     * stone ring and a cap, so lava placed there spreads nowhere.
     *
     * @return the Nether's level; the player's feet block is {@code player.blockPosition()}
     */
    static ServerLevel netherRoom(GameTestHelper helper, ServerPlayer player) {
        ServerLevel nether = BoundVexWorldTestSupport.room(helper, player, Level.NETHER, ROOM_HALF_WIDTH, ROOM_HEIGHT);
        BlockPos slot = player.blockPosition();
        for (BlockPos column : new BlockPos[] {slot, slot.above()}) {
            for (BlockPos side : new BlockPos[] {column.east(), column.west(), column.north(), column.south()}) {
                set(helper, nether, side, Blocks.STONE.defaultBlockState());
            }
        }
        set(helper, nether, slot.above(2), Blocks.STONE.defaultBlockState());
        return nether;
    }

    /** Fills the player's two-block column in {@code level} with lava. */
    static void lavaOn(GameTestHelper helper, ServerLevel level, BlockPos slot) {
        set(helper, level, slot, Blocks.LAVA.defaultBlockState());
        set(helper, level, slot.above(), Blocks.LAVA.defaultBlockState());
    }

    /** Drains the column and stands the player on the floor three blocks east of it. */
    static void walkOut(GameTestHelper helper, ServerPlayer player, BlockPos slot) {
        ServerLevel level = player.level();
        set(helper, level, slot, Blocks.AIR.defaultBlockState());
        set(helper, level, slot.above(), Blocks.AIR.defaultBlockState());
        player.setPos(slot.getX() + 3.5, slot.getY(), slot.getZ() + 0.5);
        player.setOnGround(true);
    }

    /** The player's tick as the server would run it for a real player: the fluid state, then the rescue tick. */
    static void lavaStep(ServerPlayer player) {
        soak(player);
        VexRescues.tick(player);
    }

    /**
     * Steps {@code player} with {@code stepper} on every tick while {@code live} is true. A mock player gets no
     * player tick, so this plays the server's part.
     */
    static void stepWhileLive(
            GameTestHelper helper, ServerPlayer player, AtomicBoolean live, Consumer<ServerPlayer> stepper) {
        BoundVexTestSupport.everyTick(helper, () -> {
            if (live.get()) {
                stepper.accept(player);
            }
        });
    }

    /** Where a job's vex starts: 1.5 blocks east of the player's column and 3 up. */
    private static Vec3 vexSpot(BlockPos base) {
        return new Vec3(base.getX() + 2.0, base.getY() + 3.0, base.getZ() + 0.5);
    }

    /** Fails the test unless {@code vex} hangs at the side point of {@code player}. */
    private static void assertAtSide(GameTestHelper helper, ServerPlayer player, BoundVex vex, String what) {
        Vec3 side = player.position().add(SIDE_OFFSET);
        helper.assertTrue(
                vex.position().distanceTo(side) <= SIDE_TOLERANCE,
                what + ": the vex hangs at " + side + ", not " + vex.position());
    }

    // ---- The two harnesses ----

    /**
     * A lava pull preempts {@code maker}'s job in a Nether room: the vex is held within {@link #WITHIN} ticks, the job
     * is given back, the vex's hand holds its sword; the player walks out, the tail shows the bucket; and when it ends
     * the sword is back and the job starts over.
     */
    private static void lavaTest(GameTestHelper helper, JobMaker maker) {
        // Arrange
        ServerPlayer player = rescuePlayer(helper);
        ServerLevel nether = netherRoom(helper, player);
        BlockPos slot = player.blockPosition();
        Job job = maker.make(helper, nether, player, slot);
        BoundVex vex = job.vex();
        AtomicBoolean live = new AtomicBoolean();
        stepWhileLive(helper, player, live, BoundVexRescueJobGameTests::lavaStep);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenWaitUntil(() -> helper.assertTrue(job.running().getAsBoolean(), "setup: the job is running"))
                .thenExecute(() -> {
                    job.atTrigger().run();
                    lavaOn(helper, nether, slot);
                    live.set(true);
                })
                .thenExecuteAfter(WITHIN, () -> {
                    helper.assertValueEqual(
                            VexRescues.running(player), Optional.of(VexRescues.Kind.LAVA), "the lava rescue runs");
                    helper.assertValueEqual(
                            VexRescues.rescuer(player), Optional.of(vex), "the job's vex is the rescuer");
                    assertAtSide(helper, player, vex, "held within " + WITHIN + " ticks");
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the hand holds the sword, nothing conjured");
                    job.givenBack().run();
                })
                .thenWaitUntil(() ->
                        helper.assertTrue(job.whileRescued().getAsBoolean(), "the evidence of the freed claim is in"))
                .thenExecute(() -> {
                    assertAtSide(helper, player, vex, "still held while the pull goes on");
                    walkOut(helper, player, slot);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.getMainHandItem().is(Items.WATER_BUCKET) && vex.isConjured(EquipmentSlot.MAINHAND),
                        "the tail shows the bucket"))
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            VexRescues.running(player), Optional.of(VexRescues.Kind.LAVA), "the tail is a rescue");
                    assertAtSide(helper, player, vex, "held through the tail");
                    job.givenBack().run();
                })
                .thenWaitUntil(
                        () -> helper.assertTrue(VexRescues.running(player).isEmpty(), "the tail ends"))
                .thenExecute(() -> {
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back, nothing conjured");
                    live.set(false);
                    job.beforeRestart().run();
                })
                .thenWaitUntil(
                        () -> helper.assertTrue(job.restarted().getAsBoolean(), "the job starts over and gets on"))
                .thenSucceed();
    }

    /**
     * A void catch preempts {@code maker}'s job in the End: the player falls from the job's place (a platform under
     * it), is put back on the platform, the vex is held within {@link #WITHIN} ticks with the apple in its hand even
     * though the job held the hand at the catch, the job is given back; and when the 20-tick tail ends the sword is
     * back and the job starts over.
     */
    private static void voidTest(GameTestHelper helper, JobMaker maker) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, 0.0F);
        ServerLevel end = endVoid(helper, player, VOID_REACH);
        BlockPos slot = player.blockPosition();
        BlockPos base = new BlockPos(slot.getX() + BASE_EAST, end.getMinY() + 4, slot.getZ());
        platform(helper, end, base.below(), 1);
        Vec3 stand = Vec3.atBottomCenterOf(base);
        player.setPos(stand);
        player.setOnGround(true);
        Job job = maker.make(helper, end, player, base);
        BoundVex vex = job.vex();
        AtomicBoolean live = new AtomicBoolean();
        stepWhileLive(helper, player, live, VexRescues::tick);

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.tickCount > 0, "setup: the vex ticks"))
                .thenWaitUntil(() -> helper.assertTrue(job.running().getAsBoolean(), "setup: the job is running"))
                .thenExecute(() -> {
                    job.atTrigger().run();
                    player.setPos(stand.x, end.getMinY() - BELOW_FLOOR, stand.z);
                    player.setOnGround(false);
                    player.fallDistance = 40.0;
                    player.setDeltaMovement(0.0, -1.5, 0.0);
                    live.set(true);
                })
                .thenExecuteAfter(WITHIN, () -> {
                    helper.assertValueEqual(
                            VexRescues.running(player), Optional.of(VexRescues.Kind.VOID), "the void catch runs");
                    helper.assertValueEqual(
                            VexRescues.rescuer(player), Optional.of(vex), "the job's vex is the rescuer");
                    helper.assertTrue(player.position().distanceTo(stand) < 0.01, "the player is back on the platform");
                    assertAtSide(helper, player, vex, "held within " + WITHIN + " ticks");
                    helper.assertTrue(
                            vex.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE)
                                    && vex.isConjured(EquipmentSlot.MAINHAND),
                            "the tail shows the apple although the job held the hand");
                    job.givenBack().run();
                })
                .thenWaitUntil(
                        () -> helper.assertTrue(VexRescues.running(player).isEmpty(), "the tail ends"))
                .thenExecute(() -> {
                    helper.assertTrue(BoundVexTestSupport.swordBack(vex), "the sword is back, nothing conjured");
                    live.set(false);
                    job.beforeRestart().run();
                })
                .thenWaitUntil(
                        () -> helper.assertTrue(job.restarted().getAsBoolean(), "the job starts over and gets on"))
                .thenSucceed();
    }

    // ---- The jobs ----

    /** A job with nothing to do at the trigger, no extra evidence and nothing to prepare for the restart. */
    private static Job plainJob(BoundVex vex, BooleanSupplier running, Runnable givenBack, BooleanSupplier restarted) {
        return new Job(vex, running, givenBack, () -> {}, () -> true, () -> {}, restarted);
    }

    /** A vex of {@code player} with its full AI and no target goals, at {@link #vexSpot}. */
    private static BoundVex jobVex(GameTestHelper helper, ServerLevel level, ServerPlayer player, BlockPos base) {
        return BoundVexTestSupport.noTargets(vexIn(helper, level, vexSpot(base), player));
    }

    /** The cure: a zombie villager three blocks east and two south of {@code base}, one up; in the apple or potion step. */
    private static JobMaker cure(boolean potionStep) {
        return (helper, level, player, base) -> {
            BoundVex vex = jobVex(helper, level, player, base);
            ZombieVillager zombieVillager = EntityTypes.ZOMBIE_VILLAGER.create(level, EntitySpawnReason.COMMAND);
            if (zombieVillager == null) {
                throw helper.assertionException("could not create a zombie villager");
            }
            zombieVillager.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            zombieVillager.setNoGravity(true);
            zombieVillager.setPersistenceRequired();
            zombieVillager.goalSelector.removeAllGoals(goal -> true);
            zombieVillager.targetSelector.removeAllGoals(goal -> true);
            zombieVillager.setPos(base.getX() + 3.5, base.getY() + 1.0, base.getZ() + 2.5);
            level.addFreshEntity(zombieVillager);
            whenDone(helper, zombieVillager::discard);
            BooleanSupplier inStep = potionStep
                    ? () -> ItemStack.matches(vex.getMainHandItem(), CureZombieVillagerGoal.curePotion())
                    : () -> vex.getMainHandItem().is(Items.GOLDEN_APPLE);
            return plainJob(
                    vex,
                    () -> inStep.getAsBoolean()
                            && vex.isConjured(EquipmentSlot.MAINHAND)
                            && zombieVillager.isNoAi()
                            && CureZombieVillagerGoal.isClaimed(zombieVillager),
                    () -> {
                        helper.assertFalse(zombieVillager.isNoAi(), "the zombie villager is unfrozen");
                        helper.assertFalse(AllyAttachments.isBeingCured(zombieVillager), "and unmarked");
                        helper.assertFalse(CureZombieVillagerGoal.isClaimed(zombieVillager), "and the claim is free");
                    },
                    zombieVillager::isConverting);
        };
    }

    /** The bribe: an enemy vanilla vex that targets the summoner, in the offer, the dice scripted to accept. */
    private static JobMaker bribe() {
        return (helper, level, player, base) -> {
            BoundVex vex = jobVex(helper, level, player, base);
            BoundVexTestSupport.rolling(helper, vex, 0);
            Vex other = EntityTypes.VEX.create(level, EntitySpawnReason.COMMAND);
            if (other == null) {
                throw helper.assertionException("could not create a vex");
            }
            other.setNoAi(true);
            other.setNoGravity(true);
            other.setPersistenceRequired();
            other.setPos(base.getX() + 3.5, base.getY() + 1.0, base.getZ() - 1.5);
            other.setTarget(player);
            level.addFreshEntity(other);
            whenDone(helper, other::discard);
            return plainJob(
                    vex,
                    () -> vex.getMainHandItem().is(Items.EMERALD)
                            && vex.isConjured(EquipmentSlot.MAINHAND)
                            && BribeGoal.isCourted(other),
                    () -> {
                        helper.assertFalse(BribeGoal.isCourted(other), "the other vex is not courted");
                        helper.assertFalse(AllyAttachments.isBribed(other), "nor bribed");
                        helper.assertFalse(AllyAttachments.wasOfferedBribe(other), "and carries no offer mark");
                    },
                    () -> AllyAttachments.isBribed(other));
        };
    }

    /**
     * The repair: an ally golem at half health, tapped once by the vex (the ingot in its hand). The claim is shown
     * free by a second vex, spawned at the trigger and kept off the summoner: it must finish the golem while the first
     * is held. The restart hurts the golem again with the second vex gone.
     */
    private static JobMaker repair() {
        return (helper, level, player, base) -> {
            BoundVex vex = jobVex(helper, level, player, base);
            IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
            if (golem == null) {
                throw helper.assertionException("could not create a golem");
            }
            golem.setNoAi(true);
            golem.setNoGravity(true);
            golem.setPersistenceRequired();
            golem.setHealth(50.0F);
            golem.setPos(base.getX() + 3.0, base.getY(), base.getZ() + 3.0);
            level.addFreshEntity(golem);
            whenDone(helper, golem::discard);
            float[] atTrigger = {0.0F};
            BoundVex[] second = {null};
            return new Job(
                    vex,
                    () -> vex.getMainHandItem().is(Items.IRON_INGOT)
                            && vex.isConjured(EquipmentSlot.MAINHAND)
                            && golem.getHealth() > 50.0F,
                    () -> helper.assertFalse(
                            vex.getMainHandItem().is(Items.IRON_INGOT), "the first vex holds no ingot"),
                    () -> {
                        atTrigger[0] = golem.getHealth();
                        BoundVex other = jobVex(helper, level, player, base.offset(4, 0, 3));
                        other.goalSelector.removeAllGoals(goal -> goal instanceof FollowSummonerGoal);
                        other.setPos(golem.getX() + 3.5, golem.getY() + 1.4, golem.getZ());
                        second[0] = other;
                    },
                    () -> golem.getHealth() > atTrigger[0],
                    () -> {
                        second[0].discard();
                        golem.setHealth(50.0F);
                    },
                    () -> vex.getMainHandItem().is(Items.IRON_INGOT) && golem.getHealth() > 50.0F);
        };
    }

    /**
     * The flight to a crystal (End only): a crystal twenty blocks east of {@code base}, the dragon counted alive for
     * the vex; the vex is on its way when claimed.
     */
    private static JobMaker crystal() {
        return (helper, level, player, base) -> {
            BoundVex vex = jobVex(helper, level, player, base);
            AttackCrystalGoal.scriptDragon(vex, true);
            whenDone(helper, () -> AttackCrystalGoal.unscriptDragon(vex));
            EndCrystal crystal = EntityTypes.END_CRYSTAL.create(level, EntitySpawnReason.COMMAND);
            if (crystal == null) {
                throw helper.assertionException("could not create an end crystal");
            }
            crystal.setPos(base.getX() + 20.5, base.getY() + 1.0, base.getZ() + 0.5);
            level.addFreshEntity(crystal);
            whenDone(helper, crystal::discard);
            return plainJob(
                    vex,
                    () -> AttackCrystalGoal.isClaimed(crystal) && !crystal.isRemoved(),
                    () -> helper.assertFalse(AttackCrystalGoal.isClaimed(crystal), "the crystal's claim is free"),
                    crystal::isRemoved);
        };
    }
}
