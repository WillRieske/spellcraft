package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.VEX_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.parked;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bound Vex GameTests (Evoking II Part 3 spec §3-§5, §8): the stash, drops, saving, giving, taking back, fighting with
 * gear. Shared constants and helpers live in {@link BoundVexTestSupport}.
 */
public final class BoundVexGearGameTests {
    private BoundVexGearGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_conjure_and_dismiss_round_trip",
                BoundVexGearGameTests::conjureAndDismissRoundTrip);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_gift_during_a_conjure_waits_in_the_stash",
                BoundVexGearGameTests::giftDuringAConjureWaitsInTheStash);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_gear_death_drops_given_gear_and_no_conjured_item",
                BoundVexGearGameTests::deathDropsGivenGearAndNoConjuredItem);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_save_keeps_the_stash_and_dismisses_conjured",
                BoundVexGearGameTests::saveKeepsTheStashAndDismissesConjured);
        // Sets the server difficulty, which is global, so it runs alone in its own batch, apart from the one of
        // VexGameTests.peacefulNotice (tests of one batch run side by side).
        FangGameTests.addPadded(
                "peaceful_gear",
                "bound_vex_gear_peaceful_removal_drops_gear",
                BoundVexGearGameTests::peacefulRemovalDropsGear);
    }

    /** The item entities within three blocks of {@code center}. */
    private static List<ItemEntity> itemsNear(GameTestHelper helper, Vec3 center) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(3.0));
    }

    /** How many of the item entities in {@code items} hold {@code item}. */
    private static long countOf(List<ItemEntity> items, net.minecraft.world.item.Item item) {
        return items.stream().filter(entity -> entity.getItem().is(item)).count();
    }

    /**
     * When an item is conjured into the main hand of a vex holding its sword, then another over it, then dismissed,
     * should keep the sword in the stash throughout, give it back on the dismiss, and ignore a second dismiss.
     *
     * <p>Method under test: {@link BoundVex#conjure}, {@link BoundVex#dismiss}, {@link BoundVex#isConjured} and
     * {@link BoundVex#stashed}
     */
    static void conjureAndDismissRoundTrip(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "setup: the vex holds its iron sword");

        // Act & Assert
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_INGOT), "the ingot is in hand");
        helper.assertTrue(vex.isConjured(EquipmentSlot.MAINHAND), "the main hand is conjured");
        helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "the sword is stashed");
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD));
        helper.assertTrue(vex.getMainHandItem().is(Items.EMERALD), "the emerald replaced the ingot");
        helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "the stash still holds the sword");
        vex.dismiss(EquipmentSlot.MAINHAND);
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "the sword is back");
        helper.assertFalse(vex.isConjured(EquipmentSlot.MAINHAND), "no longer conjured");
        helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).isEmpty(), "the stash is empty");
        vex.dismiss(EquipmentSlot.MAINHAND);
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "a second dismiss changes nothing");
        helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).isEmpty(), "the stash is still empty");
        helper.succeed();
    }

    /**
     * When a gift is put in the stash of a conjured slot, should hand back the sword that was stashed, leave the
     * conjured item in hand, and give the gift back to the hand when the conjure ends.
     *
     * <p>Method under test: {@link BoundVex#stashGift}
     */
    static void giftDuringAConjureWaitsInTheStash(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));

        // Act
        ItemStack before = vex.stashGift(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));

        // Assert
        helper.assertTrue(before.is(Items.IRON_SWORD), "the old stash was the iron sword");
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_INGOT), "the hand still holds the ingot");
        vex.dismiss(EquipmentSlot.MAINHAND);
        helper.assertTrue(vex.getMainHandItem().is(Items.DIAMOND_SWORD), "the gift is in hand after the dismiss");
        helper.succeed();
    }

    /**
     * When a vex wearing a helmet, holding a shield and its sword, and with an ingot conjured over the sword, is
     * killed, should drop exactly the sword (undamaged), the helmet and the shield, and no ingot.
     *
     * <p>Method under test: {@code BoundVex#dropCustomDeathLoot}
     */
    static void deathDropsGivenGearAndNoConjuredItem(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        vex.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
        Vec3 where = vex.position();
        whenDone(helper, () -> itemsNear(helper, where).forEach(ItemEntity::discard));

        // Act
        vex.kill(helper.getLevel());

        // Assert
        helper.runAfterDelay(5, () -> {
            List<ItemEntity> items = itemsNear(helper, where);
            helper.assertValueEqual(countOf(items, Items.IRON_SWORD), 1L, "iron swords dropped");
            helper.assertValueEqual(countOf(items, Items.IRON_HELMET), 1L, "helmets dropped");
            helper.assertValueEqual(countOf(items, Items.SHIELD), 1L, "shields dropped");
            helper.assertValueEqual(countOf(items, Items.IRON_INGOT), 0L, "ingots dropped");
            items.stream()
                    .filter(entity -> entity.getItem().is(Items.IRON_SWORD))
                    .forEach(entity ->
                            helper.assertValueEqual(entity.getItem().getDamageValue(), 0, "the sword drops undamaged"));
            helper.succeed();
        });
    }

    /**
     * When a vex with an ingot conjured over its sword is saved and loaded, should come back holding the sword, with
     * nothing conjured and an empty stash (the goal that conjured the ingot is not running).
     *
     * <p>Method under test: {@code BoundVex#addAdditionalSaveData} and {@code BoundVex#readAdditionalSaveData}
     */
    static void saveKeepsTheStashAndDismissesConjured(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));

        // Act
        BoundVex copy = (BoundVex) GameTestSupport.reloaded(helper.getLevel(), vex);

        // Assert
        helper.assertTrue(copy.getMainHandItem().is(Items.IRON_SWORD), "the copy holds the sword");
        helper.assertFalse(copy.isConjured(EquipmentSlot.MAINHAND), "nothing is conjured in the copy");
        helper.assertTrue(copy.stashed(EquipmentSlot.MAINHAND).isEmpty(), "the copy's stash is empty");
        helper.succeed();
    }

    /**
     * When the difficulty becomes Peaceful under a vex wearing a helmet and holding its sword, should remove the vex
     * and leave a sword and a helmet where it hung.
     *
     * <p>Method under test: {@code BoundVex#checkDespawn}
     *
     * <p>Sets the server difficulty, which is global, so it runs alone in the {@code peaceful_gear} batch; the difficulty
     * is restored through {@code whenDone}.
     */
    static void peacefulRemovalDropsGear(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        Vec3 where = vex.position();
        whenDone(helper, () -> itemsNear(helper, where).forEach(ItemEntity::discard));
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty saved = helper.getLevel().getDifficulty();
        whenDone(helper, () -> server.setDifficulty(saved, true));

        // Act
        server.setDifficulty(Difficulty.PEACEFUL, true);

        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vex.isRemoved(), "the vex is removed on Peaceful"))
                .thenExecute(() -> {
                    List<ItemEntity> items = itemsNear(helper, where);
                    helper.assertTrue(countOf(items, Items.IRON_SWORD) >= 1, "the sword dropped");
                    helper.assertTrue(countOf(items, Items.IRON_HELMET) >= 1, "the helmet dropped");
                })
                .thenSucceed();
    }
}
