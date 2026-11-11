package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.at;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.floor;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.foeAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.images;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.evoking.Dice;
import io.github.willrieske.spellcraft.evoking.MirrorImage;
import io.github.willrieske.spellcraft.evoking.ModEntities;
import io.github.willrieske.spellcraft.spell.Allies;
import io.github.willrieske.spellcraft.spell.AllyAttachments;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import io.github.willrieske.spellcraft.spell.rules.VexRing;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** GameTests of the Mirror effect and its images (Parts 6-7 spec §7.2, §10). */
public final class MirrorGameTests {
    private MirrorGameTests() {}

    static void registerAll() {
        FangGameTests.addPadded(SHARED, "mirror_effect_spawns_thirteen_images", MirrorGameTests::spawnsThirteen);
        FangGameTests.addPadded(SHARED, "mirror_images_stand_on_the_ring", MirrorGameTests::standOnTheRing);
        FangGameTests.addPadded(
                SHARED, "mirror_images_hold_copies_of_the_carriers_items", MirrorGameTests::holdCopiesOfItems);
        FangGameTests.addPadded(
                SHARED, "mirror_player_images_carry_the_players_mark", MirrorGameTests::playerImagesAreAllies);
        FangGameTests.addPadded(SHARED, "mirror_mob_images_carry_no_mark", MirrorGameTests::mobImagesHaveNoMark);
        FangGameTests.addPadded(
                SHARED, "mirror_image_dies_without_drops_or_experience", MirrorGameTests::diesWithNoDrops);
        FangGameTests.addPadded(SHARED, "mirror_images_poof_when_the_effect_is_removed", MirrorGameTests::poofOnRemove);
        FangGameTests.addPadded(
                SHARED, "mirror_images_poof_when_all_effects_are_cleared", MirrorGameTests::poofOnClearAll);
        FangGameTests.addPadded(
                SHARED, "mirror_images_poof_on_their_own_life_when_the_caster_is_gone", MirrorGameTests::poofOnOwnLife);
        FangGameTests.addPadded(
                SHARED,
                "mirror_images_poof_when_a_mob_carrier_is_killed",
                MirrorGameTests::poofWhenAMobCarrierIsKilled);
        FangGameTests.addPadded(
                SHARED, "mirror_images_poof_when_a_player_carrier_dies", MirrorGameTests::poofWhenAPlayerCarrierDies);
        FangGameTests.addPadded(
                SHARED, "mirror_the_last_image_dying_removes_the_effect", MirrorGameTests::lastImageRemovesTheEffect);
        FangGameTests.addPadded(SHARED, "mirror_image_saves_and_loads", MirrorGameTests::savesAndLoads);
        FangGameTests.addPadded(
                SHARED, "mirror_image_without_a_caster_discards_itself", MirrorGameTests::discardsWithNoCaster);
        FangGameTests.addPadded(
                SHARED, "mirror_images_wander_near_the_caster", 200, MirrorGameTests::wanderNearTheCaster);
        FangGameTests.addPadded(
                SHARED, "mirror_an_idle_image_faces_where_the_caster_faces", MirrorGameTests::idleImageFacesTheCaster);
        FangGameTests.addPadded(
                SHARED, "mirror_a_second_cast_makes_no_extra_images", MirrorGameTests::secondCastMakesNoExtras);
        FangGameTests.addPadded(
                SHARED, "mirror_images_never_spawn_inside_blocks", MirrorGameTests::tunnelKeepsImagesOutOfWalls);
        FangGameTests.addPadded(
                SHARED,
                "mirror_tall_images_never_spawn_inside_a_ceiling",
                MirrorGameTests::tallCarrierKeepsImagesOutOfCeiling);
        // Scripts every die, so it runs in an environment of its own (Dice is global).
        SpellcraftGameTests.add(
                "dice_unscript", "dice_unscript_all_clears_every_die", MirrorGameTests::unscriptAllClearsEveryDie);
    }

    /**
     * When Mirror is cast on a player, should exactly 13 images of them exist.
     *
     * <p>Method under test: {@code MirrorEffect#onEffectAdded}
     */
    static void spawnsThirteen(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        // Act
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Assert
        helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "thirteen images");
        helper.succeed();
    }

    /**
     * When Mirror is cast over a floor, should the 13 images stand on 13 different ring cells 2 to 3 blocks away.
     *
     * <p>Method under test: {@code MirrorImages#spawn}
     */
    static void standOnTheRing(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        // Act
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Assert
        Set<BlockPos> cells = new HashSet<>();
        for (MirrorImage image : images(player)) {
            BlockPos cell = image.blockPosition();
            int reach = Math.max(
                    Math.abs(cell.getX() - player.blockPosition().getX()),
                    Math.abs(cell.getZ() - player.blockPosition().getZ()));
            helper.assertTrue(reach >= VexRing.MIN_DISTANCE && reach <= VexRing.MAX_DISTANCE, "ring distance " + reach);
            cells.add(cell);
        }
        helper.assertValueEqual(cells.size(), MirrorRule.IMAGES, "13 different cells");
        helper.succeed();
    }

    /**
     * When the carrier holds 3 bricks and a shield, should every image hold copies, not the carrier's stacks.
     *
     * <p>Method under test: {@code MirrorImage#bind}
     */
    static void holdCopiesOfItems(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRICK, 3));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        // Act
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Assert
        for (MirrorImage image : images(player)) {
            helper.assertTrue(image.getMainHandItem().is(Items.BRICK), "main hand");
            helper.assertTrue(image.getOffhandItem().is(Items.SHIELD), "offhand");
        }
        images(player).get(0).getMainHandItem().setCount(1);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "the carrier's stack is untouched");
        helper.succeed();
    }

    /**
     * When a player casts Mirror, should each image carry that player's summoner mark and count as the player's ally.
     *
     * <p>Method under test: {@code MirrorImage#bind} for a player
     */
    static void playerImagesAreAllies(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        // Act
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Assert
        for (MirrorImage image : images(player)) {
            helper.assertValueEqual(
                    image.getExistingData(AllyAttachments.SUMMONER).orElse(null), player.getUUID(), "mark");
            helper.assertTrue(Allies.isAllyOf(player, image), "an ally, so fangs spare it");
        }
        helper.succeed();
    }

    /**
     * When an evoker has Mirror, should its images copy the evoker's type and carry no summoner mark.
     *
     * <p>Method under test: {@code MirrorImage#bind} for a mob
     */
    static void mobImagesHaveNoMark(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        floor(helper, 6);
        Evoker evoker = foeAt(helper, EntityTypes.EVOKER, at(helper, 0.0, 0.0, 0.0));
        // Act
        Spells.cast(evoker, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Assert
        helper.assertValueEqual(images(evoker).size(), MirrorRule.IMAGES, "thirteen");
        for (MirrorImage image : images(evoker)) {
            helper.assertTrue(image.copiedType().equals(EntityTypes.EVOKER), "looks like an evoker");
            helper.assertFalse(image.hasData(AllyAttachments.SUMMONER), "no mark");
        }
        helper.succeed();
    }

    /**
     * When an image takes lethal damage, should it die and leave no item and no experience orb, its hands' items
     * included.
     *
     * <p>Method under test: {@code MirrorImage#die}
     */
    static void diesWithNoDrops(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        MirrorImage image = images(player).get(0);
        ServerLevel level = helper.getLevel();
        // Act
        image.hurtServer(level, level.damageSources().generic(), 100.0F);
        // Assert
        AABB around = image.getBoundingBox().inflate(6.0);
        helper.assertFalse(image.isAlive(), "dead");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "no drops");
        helper.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, around).isEmpty(), "no experience");
        helper.succeed();
    }

    /**
     * When the carrier loses the effect (expiry, dispel), should every image poof within a few ticks.
     *
     * <p>Method under test: {@code MirrorImage#tick} (the caster lost Mirror)
     */
    static void poofOnRemove(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Act
        player.removeEffect(ModEffects.MIRROR);
        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(images(player).isEmpty(), "the images are gone"))
                .thenSucceed();
    }

    /**
     * When every effect is cleared (milk, a totem), should the images poof too.
     *
     * <p>Method under test: {@code MirrorImage#tick} (the caster lost Mirror)
     */
    static void poofOnClearAll(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Act
        player.removeAllEffects();
        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(images(player).isEmpty(), "the images are gone"))
                .thenSucceed();
    }

    /**
     * When the caster is removed without dying (unloaded with its chunk), should each image still end when its own life
     * runs out. (A killed or discarded mob takes its images with it: {@code poofWhenAMobCarrierIsKilled}.)
     *
     * <p>Method under test: {@code MirrorImage#tick} (the image's own life)
     */
    static void poofOnOwnLife(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        floor(helper, 6);
        Evoker evoker = foeAt(helper, EntityTypes.EVOKER, at(helper, 0.0, 0.0, 0.0));
        Spells.cast(evoker, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        List<MirrorImage> images = images(evoker);
        // Act: UNLOADED_TO_CHUNK skips LivingEntity.triggerOnDeathMobEffects, so MirrorEffect.onMobRemoved does not run
        evoker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(images.stream().allMatch(MirrorImage::isAlive), "the images outlive an unloaded carrier");
        images.forEach(image -> image.setLifeTicks(3));
        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(images.stream().noneMatch(MirrorImage::isAlive), "all ended"))
                .thenSucceed();
    }

    /**
     * When a mob carrier is killed (removed with the KILLED reason), should all its images poof at once.
     *
     * <p>Method under test: {@code MirrorEffect#onMobRemoved}
     */
    static void poofWhenAMobCarrierIsKilled(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        floor(helper, 6);
        Evoker evoker = foeAt(helper, EntityTypes.EVOKER, at(helper, 0.0, 0.0, 0.0));
        Spells.cast(evoker, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        helper.assertValueEqual(images(evoker).size(), MirrorRule.IMAGES, "thirteen to start with");
        // Act
        evoker.remove(Entity.RemovalReason.KILLED);
        // Assert
        helper.assertTrue(images(evoker).isEmpty(), "the images went with their carrier");
        helper.succeed();
    }

    /**
     * When a player carrier dies (its effects stay until the respawn), should all its images poof at once.
     *
     * <p>Method under test: {@code MirrorImages#onDeath}
     */
    static void poofWhenAPlayerCarrierDies(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "thirteen to start with");
        ServerLevel level = helper.getLevel();
        // Act
        player.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
        // Assert
        helper.assertFalse(player.isAlive(), "the player died");
        helper.assertTrue(player.hasEffect(ModEffects.MIRROR), "vanilla keeps a dead player's effects");
        helper.assertTrue(images(player).isEmpty(), "the images did not wait for the effect to go");
        helper.succeed();
    }

    /**
     * When the images die one by one, should the effect stay until the last one dies and then go.
     *
     * <p>Method under test: {@code MirrorImages#afterEnd}
     */
    static void lastImageRemovesTheEffect(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        ServerLevel level = helper.getLevel();
        List<MirrorImage> images = images(player);
        // Act & Assert
        for (int i = 0; i < images.size() - 1; i++) {
            images.get(i).hurtServer(level, level.damageSources().generic(), 100.0F);
        }
        helper.assertTrue(player.hasEffect(ModEffects.MIRROR), "twelve dead, one left: still Mirror");
        images.get(images.size() - 1).hurtServer(level, level.damageSources().generic(), 100.0F);
        helper.assertFalse(player.hasEffect(ModEffects.MIRROR), "the last image's death ends the effect");
        helper.succeed();
    }

    /**
     * When an image is saved and loaded, should it keep its caster, life, look and held item.
     *
     * <p>Method under test: {@code MirrorImage#addAdditionalSaveData} and {@code readAdditionalSaveData}
     */
    static void savesAndLoads(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRICK, 2));
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        MirrorImage image = images(player).get(0);
        image.setLifeTicks(1234);
        // Act
        Entity copy = GameTestSupport.reloaded(helper.getLevel(), image);
        // Assert
        helper.assertTrue(copy instanceof MirrorImage, "an image");
        MirrorImage loaded = (MirrorImage) copy;
        helper.assertValueEqual(loaded.casterId().orElse(null), player.getUUID(), "caster");
        helper.assertValueEqual(loaded.lifeTicks(), 1234, "life");
        helper.assertTrue(loaded.copiedType().equals(EntityTypes.PLAYER), "look");
        helper.assertTrue(loaded.getMainHandItem().is(Items.BRICK), "held item");
        helper.succeed();
    }

    /**
     * When an image is summoned with no caster (as /summon does), should it discard itself on its first tick.
     *
     * <p>Method under test: {@code MirrorImage#tick} with no caster
     */
    static void discardsWithNoCaster(GameTestHelper helper) {
        // Arrange & Act
        MirrorImage image = helper.spawn(ModEntities.MIRROR_IMAGE.get(), new BlockPos(1, 2, 1));
        // Assert
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(image.isRemoved(), "discarded"))
                .thenSucceed();
    }

    /**
     * When 140 ticks pass over a floor, should every image still be within 8 blocks of the carrier, with less life, and
     * at least 3 of the 13 should have walked at least a block from where they started (an image that never moves,
     * whatever the cause, fails this).
     *
     * <p>Method under test: {@code MirrorImage#serverAiStep}
     */
    static void wanderNearTheCaster(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 12);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        Map<MirrorImage, Vec3> starts = new HashMap<>();
        images(player).forEach(image -> starts.put(image, image.position()));
        // Act & Assert
        helper.startSequence()
                .thenExecuteAfter(140, () -> {
                    int moved = 0;
                    for (MirrorImage image : images(player)) {
                        helper.assertTrue(image.distanceTo(player) <= MirrorRule.WANDER_RADIUS + 2.0, "within reach");
                        helper.assertTrue(image.lifeTicks() < MirrorRule.LIFE_TICKS, "its life counts down");
                        Vec3 start = starts.get(image);
                        double dx = image.getX() - start.x;
                        double dz = image.getZ() - start.z;
                        if (dx * dx + dz * dz >= 1.0) {
                            moved++;
                        }
                    }
                    helper.assertTrue(moved >= 3, "at least 3 of 13 walked a block, got " + moved);
                })
                .thenSucceed();
    }

    /**
     * When an image has arrived at its wander target, should it face the way the caster faces.
     *
     * <p>Method under test: {@code MirrorImage#serverAiStep} (idle)
     */
    static void idleImageFacesTheCaster(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        MirrorImage image = images(player).get(0);
        player.setYRot(90.0F);
        // Act: the image's target is where it already stands, so it has "arrived"
        image.setWanderTo(image.position());
        // Assert
        helper.startSequence()
                .thenExecuteAfter(
                        3, () -> helper.assertValueEqual(image.getYRot(), 90.0F, "faces where the caster faces"))
                .thenSucceed();
    }

    /**
     * When Mirror is cast in a one-wide stone tunnel, should all 13 images exist, none inside a block, and still all 13
     * after 20 ticks (an image in a block takes wall damage and dies).
     *
     * <p>Method under test: {@code MirrorImages#spawn}
     */
    static void tunnelKeepsImagesOutOfWalls(GameTestHelper helper) {
        // Arrange: solid stone around the standing spot, with only a one-wide corridor (two high) along z
        ServerPlayer player = plainPlayer(helper);
        for (int dz = -6; dz <= 6; dz++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    boolean corridor = dx == 0 && (dy == 0 || dy == 1);
                    helper.setBlock(FangGameTests.FEET.offset(dx, dy, dz), corridor ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        // Act
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Assert
        helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "thirteen images");
        helper.assertTrue(images(player).stream().noneMatch(MirrorImage::isInWall), "none starts inside a block");
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertValueEqual(
                            images(player).size(), MirrorRule.IMAGES, "still thirteen: none took wall damage");
                    helper.assertTrue(
                            images(player).stream().noneMatch(MirrorImage::isInWall), "none is inside a block");
                })
                .thenSucceed();
    }

    /**
     * When a 2.9-high enderman has Mirror under a two-high ceiling that covers every ring cell but its own column,
     * should none of the 13 images start inside a block. (The enderman stands away from the test box, whose barrier
     * shell would already cover its column.)
     *
     * <p>Method under test: {@code MirrorImages#spawn}
     */
    static void tallCarrierKeepsImagesOutOfCeiling(GameTestHelper helper) {
        // Arrange: the carrier stands 5 blocks east of the standing spot, clear of the test box's barrier shell
        plainPlayer(helper);
        floor(helper, 12);
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = 1; dx <= 9; dx++) {
                if (dx != 5 || dz != 0) {
                    helper.setBlock(FangGameTests.FEET.offset(dx, 2, dz), Blocks.STONE);
                }
            }
        }
        Enderman enderman = foeAt(helper, EntityTypes.ENDERMAN, at(helper, 5.0, 0.0, 0.0));
        // Act
        Spells.cast(enderman, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Assert
        helper.assertValueEqual(images(enderman).size(), MirrorRule.IMAGES, "thirteen images");
        images(enderman).forEach(image -> helper.assertFalse(image.isInWall(), "none starts inside a block"));
        helper.succeed();
    }

    /**
     * When every die is scripted with a roll that is out of range for its bound and {@code Dice.unscriptAll()} runs,
     * should each die answer from the real random source (a leaked script would throw).
     *
     * <p>Method under test: {@code Dice#unscriptAll}
     */
    static void unscriptAllClearsEveryDie(GameTestHelper helper) {
        // Arrange
        RandomSource random = helper.getLevel().getRandom();
        List<Dice> dice = List.of(Dice.RAID_KIT, Dice.CASTER_ROLL, Dice.SCROLL_MOB, Dice.MIRROR_CONFUSION);
        dice.forEach(die -> die.script(5));
        GameTestSupport.whenDone(helper, Dice::unscriptAll);
        // Act
        Dice.unscriptAll();
        // Assert
        for (Dice die : dice) {
            helper.assertValueEqual(die.below(random, 1), 0, "no script is left: the only roll below 1 is 0");
        }
        helper.succeed();
    }

    /**
     * When Mirror is removed and cast again in the same tick, should exactly 13 images exist (the old set poofs at
     * once).
     *
     * <p>Method under test: {@code MirrorImages#spawn}
     */
    static void secondCastMakesNoExtras(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        player.removeEffect(ModEffects.MIRROR);
        // Act
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        // Assert
        helper.assertValueEqual(images(player).size(), MirrorRule.IMAGES, "thirteen, not twenty-six");
        helper.succeed();
    }
}
