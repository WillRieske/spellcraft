package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.FEET;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.PREY_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.VEX_SPOT;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.boundVex;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.followOnly;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.husk;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.parked;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.placeBeside;
import static io.github.willrieske.spellcraft.gametest.BoundVexTestSupport.summoner;
import static io.github.willrieske.spellcraft.gametest.FangGameTests.NORTH;
import static io.github.willrieske.spellcraft.gametest.GameTestSupport.whenDone;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.evoking.BoundVex;
import io.github.willrieske.spellcraft.evoking.FollowSummonerGoal;
import io.github.willrieske.spellcraft.evoking.RangedGearGoal;
import io.github.willrieske.spellcraft.evoking.VexGear;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
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
                "bound_vex_gear_load_dismisses_a_conjured_slot_and_gives_the_item_back",
                BoundVexGearGameTests::loadDismissesAConjuredSlotAndGivesTheItemBack);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_load_of_a_conjured_empty_hand_stays_empty",
                BoundVexGearGameTests::loadOfAConjuredEmptyHandStaysEmpty);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_stash_gift_refuses_a_slot_that_is_not_conjured",
                BoundVexGearGameTests::stashGiftRefusesASlotThatIsNotConjured);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_stash_keeps_copies_of_the_stacks",
                BoundVexGearGameTests::stashKeepsCopiesOfTheStacks);
        // Sets the server difficulty, which is global, so it runs alone in its own batch, apart from the one of
        // VexGameTests.peacefulNotice (tests of one batch run side by side).
        FangGameTests.addPadded(
                "peaceful_gear",
                "bound_vex_gear_peaceful_removal_drops_gear",
                BoundVexGearGameTests::peacefulRemovalDropsGear);
        registerGiving();
        registerFighting();
    }

    /** Registers Task 5's tests (kinds, giving, taking back); called from {@link #registerAll}. */
    private static void registerGiving() {
        SpellcraftGameTests.add(SHARED, "bound_vex_gear_kinds_and_slots", BoundVexGearGameTests::kindsAndSlots);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_gear_giving_armor_swaps_the_piece", BoundVexGearGameTests::givingArmorSwapsThePiece);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_giving_a_weapon_returns_the_sword",
                BoundVexGearGameTests::givingAWeaponReturnsTheSword);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_giving_a_shield_uses_the_offhand",
                BoundVexGearGameTests::givingAShieldUsesTheOffhand);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_giving_takes_one_from_a_stack",
                BoundVexGearGameTests::givingTakesOneFromAStack);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_gear_creative_keeps_its_stack", BoundVexGearGameTests::creativeKeepsItsStack);
        SpellcraftGameTests.add(SHARED, "bound_vex_gear_non_gear_passes", BoundVexGearGameTests::nonGearPasses);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_plain_empty_click_takes_the_weapon_only",
                BoundVexGearGameTests::plainEmptyClickTakesTheWeaponOnly);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_gear_conjured_item_is_not_taken", BoundVexGearGameTests::conjuredItemIsNotTaken);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_gear_stranger_cannot_give_or_take", BoundVexGearGameTests::strangerCannotGiveOrTake);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_gear_sneak_with_gear_still_gives", BoundVexGearGameTests::sneakWithGearStillGives);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_a_full_inventory_drops_the_returned_item",
                BoundVexGearGameTests::aFullInventoryDropsTheReturnedItem);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_an_identical_helmet_swap_is_audible",
                BoundVexGearGameTests::identicalHelmetSwapIsAudible);
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
     * When a vex with an ingot conjured over a damaged sword is saved and loaded, should come back holding the same
     * damaged sword (the given item survives the save intact), with nothing conjured and an empty stash: the slot
     * that was conjured is dismissed on load because the goal that conjured the ingot is not running.
     *
     * <p>Method under test: {@code BoundVex#addAdditionalSaveData} and {@code BoundVex#readAdditionalSaveData}
     */
    static void loadDismissesAConjuredSlotAndGivesTheItemBack(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.getMainHandItem().setDamageValue(17);
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));

        // Act
        BoundVex copy = (BoundVex) GameTestSupport.reloaded(helper.getLevel(), vex);

        // Assert
        helper.assertTrue(copy.getMainHandItem().is(Items.IRON_SWORD), "the copy holds the sword");
        helper.assertValueEqual(copy.getMainHandItem().getDamageValue(), 17, "the sword keeps its damage");
        helper.assertFalse(copy.isConjured(EquipmentSlot.MAINHAND), "nothing is conjured in the copy");
        helper.assertTrue(copy.stashed(EquipmentSlot.MAINHAND).isEmpty(), "the copy's stash is empty");
        helper.succeed();
    }

    /**
     * When a vex with an empty main hand has an ingot conjured and is saved and loaded, should load without an
     * exception and come back with an empty main hand and nothing conjured.
     *
     * <p>Method under test: {@code BoundVex#addAdditionalSaveData} and {@code BoundVex#readAdditionalSaveData}
     */
    static void loadOfAConjuredEmptyHandStaysEmpty(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));

        // Act
        BoundVex copy = (BoundVex) GameTestSupport.reloaded(helper.getLevel(), vex);

        // Assert
        helper.assertTrue(copy.getMainHandItem().isEmpty(), "the copy's main hand is empty");
        helper.assertFalse(copy.isConjured(EquipmentSlot.MAINHAND), "nothing is conjured in the copy");
        helper.succeed();
    }

    /**
     * When a gift is stashed for a slot that is not conjured, should throw an {@code IllegalStateException} and leave
     * the stash empty (the gift would never be restored, saved or dropped).
     *
     * <p>Method under test: {@link BoundVex#stashGift}
     */
    static void stashGiftRefusesASlotThatIsNotConjured(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        boolean thrown = false;

        // Act
        try {
            vex.stashGift(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        } catch (IllegalStateException expected) {
            thrown = true;
        }

        // Assert
        helper.assertTrue(thrown, "stashing for a slot that is not conjured throws");
        helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).isEmpty(), "nothing was stashed");
        helper.succeed();
    }

    /**
     * When a stack is changed after it was stashed as a gift, or the stack read from {@code stashed} is changed, should
     * leave the stash as it was.
     *
     * <p>Method under test: {@link BoundVex#stashGift} and {@link BoundVex#stashed}
     */
    static void stashKeepsCopiesOfTheStacks(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
        ItemStack gift = new ItemStack(Items.DIAMOND_SWORD);

        // Act
        vex.stashGift(EquipmentSlot.MAINHAND, gift);
        gift.setDamageValue(30);
        vex.stashed(EquipmentSlot.MAINHAND).setDamageValue(40);

        // Assert
        helper.assertValueEqual(vex.stashed(EquipmentSlot.MAINHAND).getDamageValue(), 0, "the stash is unchanged");
        vex.dismiss(EquipmentSlot.MAINHAND);
        helper.assertValueEqual(vex.getMainHandItem().getDamageValue(), 0, "the restored sword is undamaged");
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

    /** A click of the main hand on {@code vex}, holding {@code stack}. */
    private static InteractionResult click(BoundVex vex, ServerPlayer player, ItemStack stack) {
        GameTestSupport.hold(player, stack);
        return vex.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    /** How many of {@code item} the player's inventory holds. */
    private static int inventoryCount(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Asserts the kind and the slot {@code VexGear} gives {@code stack}. */
    private static void assertGear(
            GameTestHelper helper, ItemStack stack, VexGear.Kind kind, Optional<EquipmentSlot> slot) {
        String name = stack.getItem().toString();
        helper.assertValueEqual(VexGear.kindOf(stack), kind, "kind of " + name);
        helper.assertValueEqual(VexGear.slotFor(stack), slot, "slot of " + name);
    }

    private static ItemStack potion(Item item, Holder<Potion> potion) {
        return PotionContents.createItemStack(item, potion);
    }

    /**
     * When {@code VexGear} classifies armor, a shield, launchers, throwables, arrows, melee weapons and junk, should give
     * the kind and slot of the spec's table, and tell support from ranged by the potion's effects.
     *
     * <p>Method under test: {@link VexGear#kindOf}, {@link VexGear#slotFor}, {@link VexGear#isSupport} and
     * {@link VexGear#isRanged}
     */
    static void kindsAndSlots(GameTestHelper helper) {
        // Arrange
        Optional<EquipmentSlot> main = Optional.of(EquipmentSlot.MAINHAND);

        // Act & Assert: kinds and slots
        assertGear(helper, new ItemStack(Items.IRON_HELMET), VexGear.Kind.ARMOR, Optional.of(EquipmentSlot.HEAD));
        assertGear(
                helper, new ItemStack(Items.DIAMOND_CHESTPLATE), VexGear.Kind.ARMOR, Optional.of(EquipmentSlot.CHEST));
        assertGear(helper, new ItemStack(Items.DIAMOND_LEGGINGS), VexGear.Kind.ARMOR, Optional.of(EquipmentSlot.LEGS));
        assertGear(helper, new ItemStack(Items.DIAMOND_BOOTS), VexGear.Kind.NONE, Optional.empty());
        assertGear(helper, new ItemStack(Items.SHIELD), VexGear.Kind.SHIELD, Optional.of(EquipmentSlot.OFFHAND));
        assertGear(helper, new ItemStack(Items.BOW), VexGear.Kind.LAUNCHER, main);
        assertGear(helper, new ItemStack(Items.CROSSBOW), VexGear.Kind.LAUNCHER, main);
        assertGear(helper, potion(Items.SPLASH_POTION, Potions.HEALING), VexGear.Kind.THROWABLE, main);
        assertGear(helper, new ItemStack(Items.ARROW), VexGear.Kind.ARROW, main);
        assertGear(helper, potion(Items.TIPPED_ARROW, Potions.REGENERATION), VexGear.Kind.ARROW, main);
        assertGear(helper, new ItemStack(Items.DIAMOND_SWORD), VexGear.Kind.MELEE, main);
        assertGear(helper, new ItemStack(Items.IRON_AXE), VexGear.Kind.MELEE, main);
        assertGear(helper, new ItemStack(Items.TRIDENT), VexGear.Kind.MELEE, main);
        assertGear(helper, new ItemStack(Items.MACE), VexGear.Kind.MELEE, main);
        assertGear(helper, new ItemStack(Items.STICK), VexGear.Kind.NONE, Optional.empty());
        assertGear(helper, ItemStack.EMPTY, VexGear.Kind.NONE, Optional.empty());

        // Act & Assert: support and ranged
        helper.assertTrue(VexGear.isSupport(potion(Items.SPLASH_POTION, Potions.HEALING)), "healing is support");
        helper.assertFalse(VexGear.isSupport(potion(Items.SPLASH_POTION, Potions.HARMING)), "harming is not support");
        helper.assertFalse(VexGear.isSupport(potion(Items.SPLASH_POTION, Potions.WATER)), "water is not support");
        helper.assertTrue(
                VexGear.isSupport(potion(Items.TIPPED_ARROW, Potions.REGENERATION)), "regeneration is support");
        helper.assertFalse(VexGear.isSupport(new ItemStack(Items.ARROW)), "a plain arrow is not support");
        helper.assertTrue(VexGear.isRanged(new ItemStack(Items.BOW)), "a bow is ranged");
        helper.assertTrue(VexGear.isRanged(potion(Items.SPLASH_POTION, Potions.HARMING)), "harming is ranged");
        helper.assertTrue(VexGear.isRanged(potion(Items.SPLASH_POTION, Potions.WATER)), "water is ranged");
        helper.assertTrue(VexGear.isRanged(new ItemStack(Items.ARROW)), "a plain arrow is ranged");
        helper.assertFalse(VexGear.isRanged(potion(Items.SPLASH_POTION, Potions.HEALING)), "healing is not ranged");
        helper.assertFalse(VexGear.isRanged(new ItemStack(Items.IRON_SWORD)), "a sword is not ranged");
        helper.succeed();
    }

    /**
     * When the summoner gives an iron helmet, then a diamond helmet, should have the vex wear each in turn, consume the
     * clicks, empty the hand for the first and hand the iron helmet back for the second.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void givingArmorSwapsThePiece(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);

        // Act & Assert: the first piece
        InteractionResult first = click(vex, player, new ItemStack(Items.IRON_HELMET));
        helper.assertTrue(first.consumesAction(), "the first give consumes the action");
        helper.assertTrue(vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the vex wears the iron helmet");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the hand is empty");

        // Act & Assert: the swap
        InteractionResult second = click(vex, player, new ItemStack(Items.DIAMOND_HELMET));
        helper.assertTrue(second.consumesAction(), "the swap consumes the action");
        helper.assertTrue(
                vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "the vex wears the diamond helmet");
        helper.assertValueEqual(inventoryCount(player, Items.IRON_HELMET), 1, "iron helmets handed back");
        helper.succeed();
    }

    /**
     * When the summoner gives a diamond sword to a vex holding its iron sword, should leave the diamond sword in the
     * vex's main hand and hand the iron sword to the player's inventory.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void givingAWeaponReturnsTheSword(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);

        // Act
        click(vex, player, new ItemStack(Items.DIAMOND_SWORD));

        // Assert
        helper.assertTrue(vex.getMainHandItem().is(Items.DIAMOND_SWORD), "the vex holds the diamond sword");
        helper.assertValueEqual(inventoryCount(player, Items.IRON_SWORD), 1, "iron swords handed back");
        helper.succeed();
    }

    /**
     * When the summoner gives a shield, should put it in the vex's off hand and leave the main hand's iron sword.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void givingAShieldUsesTheOffhand(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);

        // Act
        click(vex, player, new ItemStack(Items.SHIELD));

        // Assert
        helper.assertTrue(vex.getOffhandItem().is(Items.SHIELD), "the shield is in the off hand");
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "the main hand still holds the iron sword");
        helper.succeed();
    }

    /**
     * When the summoner gives from a stack of five splash potions of harming, should take exactly one: four stay in the
     * hand, the vex holds one, and the iron sword it held is in the inventory.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void givingTakesOneFromAStack(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        ItemStack stack = potion(Items.SPLASH_POTION, Potions.HARMING);
        stack.setCount(5);

        // Act
        click(vex, player, stack);

        // Assert
        helper.assertValueEqual(player.getMainHandItem().getCount(), 4, "potions left in the hand");
        helper.assertTrue(vex.getMainHandItem().is(Items.SPLASH_POTION), "the vex holds a potion");
        helper.assertValueEqual(vex.getMainHandItem().getCount(), 1, "potions the vex holds");
        helper.assertValueEqual(inventoryCount(player, Items.IRON_SWORD), 1, "iron swords handed back");
        helper.succeed();
    }

    /**
     * When a creative summoner gives a diamond sword, should keep the sword in the hand and still give the vex one.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void creativeKeepsItsStack(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.CREATIVE);
        MockPlayers.moveIntoTest(helper, player);
        BoundVex vex = parked(helper, VEX_SPOT, player);

        // Act
        click(vex, player, new ItemStack(Items.DIAMOND_SWORD));

        // Assert
        helper.assertTrue(player.getMainHandItem().is(Items.DIAMOND_SWORD), "the creative hand keeps its sword");
        helper.assertTrue(vex.getMainHandItem().is(Items.DIAMOND_SWORD), "the vex holds a diamond sword too");
        // The iron sword the vex held is returned like any other: it fits the inventory, so nothing is dropped.
        helper.assertValueEqual(inventoryCount(player, Items.IRON_SWORD), 1, "iron swords handed back");
        helper.assertValueEqual(
                countOf(itemsNear(helper, player.position()), Items.IRON_SWORD), 0L, "iron swords dropped");
        helper.succeed();
    }

    /**
     * When the summoner's inventory is full and the summoner gives a diamond sword to a vex holding its iron sword,
     * should leave the diamond sword in the vex's main hand and drop exactly one iron sword near the player.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void aFullInventoryDropsTheReturnedItem(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Vec3 where = player.position();
        whenDone(helper, () -> itemsNear(helper, where).forEach(ItemEntity::discard));
        for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        // A sword stacks to one; a count of two leaves one in the hand, so the hand's slot does not free up.
        ItemStack swords = new ItemStack(Items.DIAMOND_SWORD, 2);

        // Act
        click(vex, player, swords);

        // Assert
        helper.assertTrue(vex.getMainHandItem().is(Items.DIAMOND_SWORD), "the vex holds the diamond sword");
        helper.assertValueEqual(inventoryCount(player, Items.IRON_SWORD), 0, "iron swords in the inventory");
        helper.assertValueEqual(countOf(itemsNear(helper, where), Items.IRON_SWORD), 1L, "iron swords dropped");
        helper.succeed();
    }

    /**
     * When the summoner clicks holding a stick, then boots, or the off hand clicks holding a helmet, should not consume
     * the click and change nothing.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void nonGearPasses(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);

        // Act & Assert: a stick, then boots
        for (ItemStack stack : List.of(new ItemStack(Items.STICK), new ItemStack(Items.DIAMOND_BOOTS))) {
            helper.assertFalse(click(vex, player, stack).consumesAction(), stack.getItem() + " passes");
            helper.assertFalse(player.getMainHandItem().isEmpty(), stack.getItem() + " stays in the hand");
            helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "the vex keeps its sword");
            helper.assertTrue(vex.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "the vex wears no boots");
        }

        // Act & Assert: the off hand
        GameTestSupport.hold(player, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_HELMET));
        helper.assertFalse(
                vex.interact(player, InteractionHand.OFF_HAND, Vec3.ZERO).consumesAction(), "the off hand passes");
        helper.assertTrue(vex.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the vex wears nothing");
        helper.succeed();
    }

    /**
     * When the summoner clicks empty-handed without sneaking on a vex with a sword, a helmet and a shield, should take
     * only the sword (the vex's main hand is then empty) and leave the helmet and the shield; a second empty click
     * consumes the action but changes nothing, and does not toggle stay.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void plainEmptyClickTakesTheWeaponOnly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        vex.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

        // Act
        InteractionResult result = click(vex, player, ItemStack.EMPTY);

        // Assert
        helper.assertTrue(result.consumesAction(), "the take-back consumes the action");
        helper.assertTrue(player.getMainHandItem().is(Items.IRON_SWORD), "the sword is in the hand");
        helper.assertTrue(vex.getMainHandItem().isEmpty(), "the vex's hand is empty");
        helper.assertTrue(vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet is still worn");
        helper.assertTrue(vex.getOffhandItem().is(Items.SHIELD), "the shield is still held");

        // Act & Assert: the second click
        InteractionResult again = click(vex, player, ItemStack.EMPTY);
        helper.assertTrue(again.consumesAction(), "the second click consumes the action");
        helper.assertTrue(vex.getMainHandItem().isEmpty(), "still nothing in the vex's hand");
        helper.assertTrue(vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet is still worn");
        helper.assertTrue(vex.getOffhandItem().is(Items.SHIELD), "the shield is still held");
        helper.assertFalse(vex.isStaying(), "a plain click does not toggle stay");
        helper.succeed();
    }

    /**
     * When the main hand holds a conjured ingot, should not hand it over on an empty click; and when the summoner then
     * gives a diamond sword, should leave the ingot in hand, stash the sword, and hand the stashed iron sword to the
     * inventory.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void conjuredItemIsNotTaken(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.conjure(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));

        // Act & Assert: the empty click
        click(vex, player, ItemStack.EMPTY);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the hand stays empty");
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_INGOT), "the ingot is still held");

        // Act & Assert: the gift
        click(vex, player, new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_INGOT), "the ingot is still held after the gift");
        helper.assertTrue(vex.stashed(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD), "the gift is stashed");
        helper.assertValueEqual(inventoryCount(player, Items.IRON_SWORD), 1, "the stashed sword is handed back");
        helper.succeed();
    }

    /**
     * When another player clicks, first holding a helmet and then empty-handed, should not consume either click, keep
     * the helmet in that player's hand and leave the vex's sword on the vex.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void strangerCannotGiveOrTake(GameTestHelper helper) {
        // Arrange
        ServerPlayer owner = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, owner);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveIntoTest(helper, stranger);

        // Act & Assert: a gift
        helper.assertFalse(
                click(vex, stranger, new ItemStack(Items.IRON_HELMET)).consumesAction(), "the gift passes");
        helper.assertTrue(stranger.getMainHandItem().is(Items.IRON_HELMET), "the helmet stays in the hand");
        helper.assertTrue(vex.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the vex wears nothing");

        // Act & Assert: a take-back
        helper.assertFalse(click(vex, stranger, ItemStack.EMPTY).consumesAction(), "the take-back passes");
        helper.assertTrue(vex.getMainHandItem().is(Items.IRON_SWORD), "the vex keeps its sword");
        helper.succeed();
    }

    /**
     * When a sneaking summoner clicks holding a helmet, should give the helmet and not toggle stay.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void sneakWithGearStillGives(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        player.setShiftKeyDown(true);

        // Act
        click(vex, player, new ItemStack(Items.IRON_HELMET));

        // Assert
        helper.assertTrue(vex.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet is worn");
        helper.assertFalse(vex.isStaying(), "the vex does not stay");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the helmet left the hand");
        helper.succeed();
    }

    /**
     * When a vex wears an iron helmet and the summoner gives it a second, identical iron helmet, should play the vex's
     * own ambient sound (vanilla's equip sound stays silent for an identical piece) and hand the first helmet back.
     *
     * <p>Method under test: {@code BoundVex#mobInteract} (protected), through {@link BoundVex#interact}
     */
    static void identicalHelmetSwapIsAudible(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        GameTestSupport.drainSounds(player);

        // Act
        click(vex, player, new ItemStack(Items.IRON_HELMET));

        // Assert
        helper.assertTrue(
                GameTestSupport.drainSounds(player).contains(SoundEvents.VEX_AMBIENT),
                "the vex's ambient sound played");
        helper.assertValueEqual(inventoryCount(player, Items.IRON_HELMET), 1, "helmets handed back");
        helper.succeed();
    }

    /** Registers Task 6's tests (fighting with gear); called from {@link #registerAll}. */
    private static void registerFighting() {
        SpellcraftGameTests.add(
                SHARED, "bound_vex_gear_a_better_sword_hits_harder", BoundVexGearGameTests::aBetterSwordHitsHarder);
        SpellcraftGameTests.add(
                SHARED, "bound_vex_gear_the_sword_is_not_worn_down", BoundVexGearGameTests::theSwordIsNotWornDown);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_gear_a_bow_vex_shoots_and_keeps_its_distance",
                BoundVexGearGameTests::aBowVexShootsAndKeepsItsDistance);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_gear_a_following_bow_vex_keeps_its_target_at_range",
                BoundVexGearGameTests::aFollowingBowVexKeepsItsTargetAtRange);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_gear_a_harming_potion_vex_throws_at_its_target",
                BoundVexGearGameTests::aHarmingPotionVexThrowsAtItsTarget);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_gear_a_healing_potion_vex_throws_at_a_hurt_ally",
                BoundVexGearGameTests::aHealingPotionVexThrowsAtAHurtAlly);
        FangGameTests.addPadded(
                SHARED,
                "bound_vex_gear_a_healing_potion_vex_ignores_a_healthy_ally",
                BoundVexGearGameTests::aHealingPotionVexIgnoresAHealthyAlly);
        FangGameTests.addPadded(
                SHARED, "bound_vex_gear_a_support_vex_still_follows", BoundVexGearGameTests::aSupportVexStillFollows);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_a_shield_blocks_from_the_front",
                BoundVexGearGameTests::aShieldBlocksFromTheFront);
        SpellcraftGameTests.add(
                SHARED,
                "bound_vex_gear_without_a_shield_nothing_is_blocked",
                BoundVexGearGameTests::withoutAShieldNothingIsBlocked);
    }

    /** Where the fighting tests hang a vex: four blocks over the structure's middle, above the barrier shell. */
    private static final BlockPos AIR_SPOT = FEET.above(4);

    /**
     * A husk with no AI and no gravity, {@code dx} blocks east and {@code dz} blocks south of {@code at}. It is spawned
     * in the structure and then moved by absolute position: a rotated structure puts relative coordinates a block off.
     */
    private static Husk huskAt(GameTestHelper helper, Vec3 at, double dx, double dz) {
        Husk husk = husk(helper, FEET);
        husk.setNoGravity(true);
        husk.setPos(at.add(dx, 0.0, dz));
        return husk;
    }

    /** Gives {@code mob} a thousand health, so a few hits cannot kill it. */
    private static void toughen(LivingEntity mob) {
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        mob.setHealth(1000.0F);
    }

    /** The arrows {@code vex} shot that are alive near it. */
    private static List<AbstractArrow> arrowsOf(GameTestHelper helper, BoundVex vex) {
        return helper.getLevel()
                .getEntitiesOfClass(
                        AbstractArrow.class, vex.getBoundingBox().inflate(16.0), arrow -> vex.equals(arrow.getOwner()));
    }

    /** The splash potions {@code vex} threw that are alive near it. */
    private static List<ThrownSplashPotion> potionsOf(GameTestHelper helper, BoundVex vex) {
        return helper.getLevel()
                .getEntitiesOfClass(
                        ThrownSplashPotion.class,
                        vex.getBoundingBox().inflate(16.0),
                        potion -> vex.equals(potion.getOwner()));
    }

    /** Discards the arrows and potions of {@code vex} when the test ends. */
    private static void discardShotsWhenDone(GameTestHelper helper, BoundVex vex) {
        whenDone(helper, () -> {
            arrowsOf(helper, vex).forEach(Entity::discard);
            potionsOf(helper, vex).forEach(Entity::discard);
        });
    }

    /**
     * When a vex hits a husk with its iron sword and then, after the attribute update, with a diamond sword, should
     * hurt the second husk more than the first.
     *
     * <p>Method under test: {@code BoundVex#doHurtTarget}
     */
    static void aBetterSwordHitsHarder(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        Husk huskA = husk(helper, PREY_SPOT);
        Husk huskB = husk(helper, PREY_SPOT);
        toughen(huskA);
        toughen(huskB);

        // Act
        vex.doHurtTarget(helper.getLevel(), huskA);
        float lostA = 1000.0F - huskA.getHealth();
        vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));

        // Assert (a stack's attribute modifiers reach the vex on its next tick)
        helper.runAfterDelay(3, () -> {
            vex.doHurtTarget(helper.getLevel(), huskB);
            float lostB = 1000.0F - huskB.getHealth();
            helper.assertTrue(lostA > 0.0F, "the iron sword hurt");
            helper.assertTrue(lostB > lostA, "the diamond sword hurt more: " + lostB + " against " + lostA);
            helper.succeed();
        });
    }

    /**
     * When a vex holding an undamaged diamond sword hits a husk five times, should still hold one sword with a damage
     * value of 0.
     *
     * <p>Method under test: {@code BoundVex#doHurtTarget}
     */
    static void theSwordIsNotWornDown(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        Husk husk = husk(helper, PREY_SPOT);
        toughen(husk);

        // Act
        for (int hit = 0; hit < 5; hit++) {
            husk.damageCooldownTime = 0;
            vex.doHurtTarget(helper.getLevel(), husk);
        }

        // Assert
        helper.assertTrue(husk.getHealth() < 1000.0F, "setup: the hits landed");
        helper.assertTrue(vex.getMainHandItem().is(Items.DIAMOND_SWORD), "the vex still holds the sword");
        helper.assertValueEqual(vex.getMainHandItem().getCount(), 1, "swords held");
        helper.assertValueEqual(vex.getMainHandItem().getDamageValue(), 0, "the sword's damage value");
        helper.succeed();
    }

    /**
     * When a staying vex holding a bow has a husk three blocks away, should shoot an arrow it owns within a hundred and
     * thirty ticks, keep at least {@code MIN_DISTANCE - 1.5} blocks from the husk once it has settled (from tick 60),
     * never charge, and keep an undamaged bow.
     *
     * <p>Method under test: {@code RangedGearGoal} and {@code VexShots#launch}, through the goal selector
     */
    static void aBowVexShootsAndKeepsItsDistance(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, AIR_SPOT, player);
        vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        vex.setStaying(true);
        huskAt(helper, vex.position(), 3.0, 0.0);
        discardShotsWhenDone(helper, vex);
        AtomicInteger ticks = new AtomicInteger();
        AtomicBoolean shot = new AtomicBoolean();

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(130, () -> {
                    if (!arrowsOf(helper, vex).isEmpty()) {
                        shot.set(true);
                    }
                    helper.assertFalse(vex.isCharging(), "a bow vex never charges");
                    LivingEntity target = vex.getTarget();
                    if (ticks.incrementAndGet() > 60 && target != null) {
                        helper.assertTrue(
                                vex.distanceTo(target) >= RangedGearGoal.MIN_DISTANCE - 1.5,
                                "keeps its distance, got " + vex.distanceTo(target));
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(shot.get(), "an arrow of the vex flew");
                    helper.assertTrue(vex.getMainHandItem().is(Items.BOW), "the vex still holds the bow");
                    helper.assertValueEqual(vex.getMainHandItem().getDamageValue(), 0, "the bow's damage value");
                })
                .thenSucceed();
    }

    /**
     * When a staying vex holding one splash potion of harming has a husk six blocks away, should throw a splash potion
     * it owns within a hundred ticks and still hold exactly one potion. (Harming heals undead: the test asserts the
     * throw, not the damage.)
     *
     * <p>Method under test: {@code RangedGearGoal} and {@code VexShots#launch}, through the goal selector
     */
    static void aHarmingPotionVexThrowsAtItsTarget(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, AIR_SPOT, player);
        vex.setItemSlot(EquipmentSlot.MAINHAND, potion(Items.SPLASH_POTION, Potions.HARMING));
        vex.setStaying(true);
        huskAt(helper, vex.position(), 6.0, 0.0);
        discardShotsWhenDone(helper, vex);
        AtomicBoolean thrown = new AtomicBoolean();

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(100, () -> {
                    if (!potionsOf(helper, vex).isEmpty()) {
                        thrown.set(true);
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(thrown.get(), "a splash potion of the vex flew");
                    helper.assertTrue(vex.getMainHandItem().is(Items.SPLASH_POTION), "the vex still holds a potion");
                    helper.assertValueEqual(vex.getMainHandItem().getCount(), 1, "potions held");
                })
                .thenSucceed();
    }

    /**
     * When a vex that follows (it does not stay) holds a bow and has a husk three blocks away, should back off to at
     * least {@code MIN_DISTANCE - 1.5} blocks and shoot an arrow it owns within two hundred ticks, with the husk still
     * its target at that moment and forty ticks later: the band (6 to 10 blocks) lies beyond the following reach, and
     * the vex must not lose a target it stands off from.
     *
     * <p>Method under test: {@code BoundVex#keepsTarget} (package-private), through {@code RangedGearGoal} and the goal
     * selector
     */
    static void aFollowingBowVexKeepsItsTargetAtRange(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = boundVex(helper, AIR_SPOT, player);
        vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Husk husk = huskAt(helper, vex.position(), 3.0, 0.0);
        discardShotsWhenDone(helper, vex);
        helper.assertFalse(vex.isStaying(), "setup: the vex follows");

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            vex.distanceTo(husk) >= RangedGearGoal.MIN_DISTANCE - 1.5,
                            "backed off, got " + vex.distanceTo(husk));
                    helper.assertFalse(arrowsOf(helper, vex).isEmpty(), "an arrow of the vex flew");
                })
                .thenExecute(() -> helper.assertTrue(husk.equals(vex.getTarget()), "the husk is still the target"))
                .thenIdle(40)
                .thenExecute(() ->
                        helper.assertTrue(husk.equals(vex.getTarget()), "the husk is still the target 40 ticks on"))
                .thenSucceed();
    }

    /** An iron golem with no AI and no gravity, {@code dx} east of {@code at}, with {@code health} health. */
    private static IronGolem golemAt(GameTestHelper helper, Vec3 at, double dx, float health) {
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, FEET);
        golem.setNoAi(true);
        golem.setNoGravity(true);
        golem.setPos(at.add(dx, 0.0, 0.0));
        golem.setHealth(health);
        whenDone(helper, golem::discard);
        return golem;
    }

    /**
     * A staying Bound Vex of {@code player} holding a splash potion of healing at {@link #AIR_SPOT}, without the repair
     * goal (a hurt golem would send it off to conjure an ingot over the potion; repair is Part 2's subject).
     */
    private static BoundVex healer(GameTestHelper helper, ServerPlayer player) {
        BoundVex vex = boundVex(helper, AIR_SPOT, player);
        vex.goalSelector.removeAllGoals(goal -> goal.getClass().getSimpleName().equals("RepairGolemGoal"));
        vex.setItemSlot(EquipmentSlot.MAINHAND, potion(Items.SPLASH_POTION, Potions.HEALING));
        vex.setStaying(true);
        discardShotsWhenDone(helper, vex);
        return vex;
    }

    /**
     * When a vex holding a splash potion of healing has a hurt ally golem five blocks away and a husk five blocks away
     * on the other side, should throw a potion within a hundred ticks, heal the golem, never charge, and still hold
     * exactly one potion.
     *
     * <p>Method under test: {@code SupportGearGoal} and {@code VexShots#launch}, through the goal selector
     */
    static void aHealingPotionVexThrowsAtAHurtAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = healer(helper, player);
        IronGolem golem = golemAt(helper, vex.position(), 5.0, 20.0F);
        huskAt(helper, vex.position(), -5.0, 0.0);
        AtomicBoolean thrown = new AtomicBoolean();

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(100, () -> {
                    if (!potionsOf(helper, vex).isEmpty()) {
                        thrown.set(true);
                    }
                    helper.assertFalse(vex.isCharging(), "a support vex never charges");
                })
                .thenExecute(() -> {
                    helper.assertTrue(thrown.get(), "a splash potion of the vex flew");
                    helper.assertTrue(golem.getHealth() > 20.0F, "the golem was healed, has " + golem.getHealth());
                    helper.assertTrue(vex.getMainHandItem().is(Items.SPLASH_POTION), "the vex still holds a potion");
                    helper.assertValueEqual(vex.getMainHandItem().getCount(), 1, "potions held");
                })
                .thenSucceed();
    }

    /**
     * When a vex holding a splash potion of healing has an ally golem at full health five blocks away, should throw no
     * potion in a hundred ticks.
     *
     * <p>Method under test: {@code SupportGearGoal} through the goal selector
     */
    static void aHealingPotionVexIgnoresAHealthyAlly(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = healer(helper, player);
        golemAt(helper, vex.position(), 5.0, 100.0F);

        // Act & Assert
        helper.startSequence()
                .thenExecuteFor(
                        100, () -> helper.assertTrue(potionsOf(helper, vex).isEmpty(), "no potion is thrown"))
                .thenSucceed();
    }

    /**
     * When a vex that only follows holds a splash potion of healing and stands eight blocks from its summoner with
     * nobody hurt, should still fly into the orbit band around the summoner.
     *
     * <p>Method under test: {@code FollowSummonerGoal} beside {@code SupportGearGoal}, through the goal selector
     */
    static void aSupportVexStillFollows(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = FangGameTests.caster(helper, NORTH);
        BoundVex vex = followOnly(helper, AIR_SPOT, player);
        vex.setItemSlot(EquipmentSlot.MAINHAND, potion(Items.SPLASH_POTION, Potions.HEALING));
        vex.setPos(player.position().add(8.0, 1.0, 0.0));
        helper.assertTrue(vex.distanceTo(player) > FollowSummonerGoal.ORBIT_MAX + 2.0, "setup: eight blocks out");

        // Act & Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        vex.distanceTo(player) <= FollowSummonerGoal.ORBIT_MAX + 1.0,
                        "follows its summoner, got " + vex.distanceTo(player)))
                .thenSucceed();
    }

    /** Whether {@code source} hurt {@code vex} just now: what {@code hurtServer} returned. */
    private static boolean hit(GameTestHelper helper, BoundVex vex, DamageSource source) {
        vex.damageCooldownTime = 0;
        return vex.hurtServer(helper.getLevel(), source, 1.0F);
    }

    /** An arrow of {@code shooter}, not in the level, lying {@code dz} blocks south of {@code vex}. */
    private static AbstractArrow arrowSouthOf(BoundVex vex, Husk shooter, double dz) {
        AbstractArrow arrow = ProjectileUtil.getMobArrow(shooter, new ItemStack(Items.ARROW), 1.0F, null);
        arrow.setPos(vex.position().add(0.0, 0.0, dz));
        return arrow;
    }

    /**
     * When a vex with a shield in its off hand faces south and is hit by a husk to the south, then by one to the north,
     * by a damage source with no position, by a plain arrow from the south and by a piercing arrow from the south,
     * should block the husk in front (false, health unchanged, shield intact) and the plain arrow, and take the other
     * three.
     *
     * <p>Method under test: {@code BoundVex#hurtServer}
     */
    static void aShieldBlocksFromTheFront(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        vex.setYHeadRot(0.0F);
        Husk husk = husk(helper, PREY_SPOT);
        ServerLevel level = helper.getLevel();

        // Act & Assert: a husk in front
        placeBeside(vex, husk, 0.0, 2.0);
        float full = vex.getHealth();
        helper.assertFalse(hit(helper, vex, level.damageSources().mobAttack(husk)), "a hit from the front is blocked");
        helper.assertValueEqual(vex.getHealth(), full, "health after the blocked hit");
        helper.assertValueEqual(vex.getOffhandItem().getDamageValue(), 0, "the shield is not worn");

        // Act & Assert: a husk behind
        placeBeside(vex, husk, 0.0, -2.0);
        helper.assertTrue(hit(helper, vex, level.damageSources().mobAttack(husk)), "a hit from behind lands");
        helper.assertTrue(vex.getHealth() < full, "health dropped after the hit from behind");

        // Act & Assert: no source position (the vex is fire immune, so a generic source stands for fire)
        helper.assertTrue(hit(helper, vex, level.damageSources().generic()), "damage with no position lands");

        // Act & Assert: a plain arrow from the south is blocked
        helper.assertFalse(
                hit(helper, vex, level.damageSources().arrow(arrowSouthOf(vex, husk, 2.0), husk)),
                "a plain arrow from the front is blocked");

        // Act & Assert: a piercing arrow from the south lands (the pierce level is private: set it through a save)
        AbstractArrow piercing = arrowSouthOf(vex, husk, 2.0);
        CompoundTag tag = GameTestSupport.saved(piercing);
        tag.putByte("PierceLevel", (byte) 1);
        piercing.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        piercing.setPos(vex.position().add(0.0, 0.0, 2.0));
        helper.assertValueEqual((int) piercing.getPierceLevel(), 1, "setup: the arrow pierces");
        helper.assertTrue(
                hit(helper, vex, level.damageSources().arrow(piercing, husk)), "a piercing arrow from the front lands");
        helper.succeed();
    }

    /**
     * When a vex with an empty off hand is hit by a husk to the south while facing south, should take the hit.
     *
     * <p>Method under test: {@code BoundVex#hurtServer}
     */
    static void withoutAShieldNothingIsBlocked(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = summoner(helper);
        BoundVex vex = parked(helper, VEX_SPOT, player);
        vex.setYHeadRot(0.0F);
        Husk husk = husk(helper, PREY_SPOT);
        placeBeside(vex, husk, 0.0, 2.0);
        float full = vex.getHealth();

        // Act
        boolean hurt = hit(helper, vex, helper.getLevel().damageSources().mobAttack(husk));

        // Assert
        helper.assertTrue(hurt, "the hit lands without a shield");
        helper.assertTrue(vex.getHealth() < full, "health dropped");
        helper.succeed();
    }
}
