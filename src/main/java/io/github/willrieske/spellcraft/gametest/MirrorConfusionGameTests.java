package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.at;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.floor;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.foeAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.hostileAt;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.images;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;

import io.github.willrieske.spellcraft.evoking.Dice;
import io.github.willrieske.spellcraft.evoking.MirrorImage;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.zombie.Husk;

/** GameTests of the Mirror confusion (Parts 6-7 spec §7.2: 13 of 14, any mob, never a golem onto a player's images). */
public final class MirrorConfusionGameTests {
    private MirrorConfusionGameTests() {}

    /** The dice environment of the Mirror tests: one batch, since the confusion die is global. */
    private static final String DICE = "mirror_dice";

    static void registerAll() {
        FangGameTests.addPadded(
                DICE, "mirror_confusion_redirects_when_the_dice_say_so", MirrorConfusionGameTests::redirects);
        FangGameTests.addPadded(
                DICE, "mirror_confusion_keeps_the_target_when_the_dice_say_no", MirrorConfusionGameTests::keeps);
        FangGameTests.addPadded(
                DICE,
                "mirror_confusion_does_not_roll_for_a_mob_already_on_the_carrier",
                MirrorConfusionGameTests::noRerollOnTheCarrier);
        FangGameTests.addPadded(
                DICE,
                "mirror_confusion_does_not_roll_for_a_mob_already_on_an_image",
                MirrorConfusionGameTests::noRerollOnAnImage);
        FangGameTests.addPadded(
                DICE,
                "mirror_confusion_ignores_images_beyond_16_blocks",
                MirrorConfusionGameTests::farImagesDoNotCount);
        FangGameTests.addPadded(
                DICE,
                "mirror_confusion_never_sends_a_golem_onto_a_players_images",
                MirrorConfusionGameTests::golemAndPlayer);
        FangGameTests.addPadded(
                DICE,
                "mirror_confusion_confuses_a_golem_going_for_a_mob_carrier",
                MirrorConfusionGameTests::golemAndEvoker);
    }

    /** A frozen husk (no AI, no gravity) 4 blocks from the standing spot. */
    private static Husk zombie(GameTestHelper helper) {
        return hostileAt(helper, at(helper, 4.0, 0.0, 0.0));
    }

    /**
     * When the dice roll 0 and a zombie picks a Mirror carrier as its target, should the zombie end up targeting one of
     * that carrier's images.
     *
     * <p>Method under test: {@code MirrorImages.onChangeTarget}.
     */
    static void redirects(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        Husk zombie = zombie(helper);
        // Act
        try {
            Dice.MIRROR_CONFUSION.script(0);
            zombie.setTarget(player);
        } finally {
            Dice.unscriptAll();
        }
        // Assert
        helper.assertTrue(
                zombie.getTarget() instanceof MirrorImage image
                        && image.casterId().filter(player.getUUID()::equals).isPresent(),
                "the target is an image of the player");
        helper.succeed();
    }

    /**
     * When the dice roll 13 and a zombie picks a Mirror carrier as its target, should the zombie keep the carrier.
     *
     * <p>Method under test: {@code MirrorImages.onChangeTarget}.
     */
    static void keeps(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        Husk zombie = zombie(helper);
        // Act
        try {
            Dice.MIRROR_CONFUSION.script(MirrorRule.CONFUSION_BOUND - 1);
            zombie.setTarget(player);
        } finally {
            Dice.unscriptAll();
        }
        // Assert
        helper.assertTrue(player.equals(zombie.getTarget()), "the target is still the player");
        helper.succeed();
    }

    /**
     * When a zombie that already targets a Mirror carrier is set on it again, should no new roll be taken, so the next
     * zombie gets the scripted 0.
     *
     * <p>Method under test: {@code MirrorImages.onChangeTarget}.
     */
    static void noRerollOnTheCarrier(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        Husk zombieA = zombie(helper);
        Husk zombieB = hostileAt(helper, at(helper, -4.0, 0.0, 0.0));
        // Act
        try {
            Dice.MIRROR_CONFUSION.script(MirrorRule.CONFUSION_BOUND - 1, 0);
            zombieA.setTarget(player);
            zombieA.setTarget(player);
            zombieB.setTarget(player);
        } finally {
            Dice.unscriptAll();
        }
        // Assert
        helper.assertTrue(player.equals(zombieA.getTarget()), "A keeps the player");
        helper.assertTrue(zombieB.getTarget() instanceof MirrorImage, "B was redirected, so the 0 was not spent");
        helper.succeed();
    }

    /**
     * When a zombie is set on an image, then on the image's carrier, should no roll be taken for either (the carrier
     * is then its plain target), so the next zombie gets the scripted 0.
     *
     * <p>Method under test: {@code MirrorImages.onChangeTarget}.
     */
    static void noRerollOnAnImage(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        Husk zombieA = zombie(helper);
        Husk zombieB = hostileAt(helper, at(helper, -4.0, 0.0, 0.0));
        MirrorImage image = images(player).get(0);
        // Act
        try {
            Dice.MIRROR_CONFUSION.script(0);
            zombieA.setTarget(image);
            zombieA.setTarget(player);
            zombieB.setTarget(player);
        } finally {
            Dice.unscriptAll();
        }
        // Assert
        helper.assertTrue(player.equals(zombieA.getTarget()), "A went to the player without a roll");
        helper.assertTrue(zombieB.getTarget() instanceof MirrorImage, "B was redirected, so the 0 was not spent");
        helper.succeed();
    }

    /**
     * When every image is more than 16 blocks from the carrier and the dice roll 0, should the zombie keep the carrier.
     *
     * <p>Method under test: {@code MirrorImages.onChangeTarget}.
     */
    static void farImagesDoNotCount(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        for (MirrorImage image : images(player)) {
            image.setPos(image.getX() + 20.0, image.getY(), image.getZ());
        }
        Husk zombie = zombie(helper);
        // Act
        try {
            Dice.MIRROR_CONFUSION.script(0);
            zombie.setTarget(player);
        } finally {
            Dice.unscriptAll();
        }
        // Assert
        helper.assertTrue(player.equals(zombie.getTarget()), "the target is still the player");
        helper.succeed();
    }

    /**
     * When the dice roll 0 and an iron golem picks a player with Mirror, should the golem keep the player, since the
     * images carry the player's mark.
     *
     * <p>Method under test: {@code MirrorImages.onChangeTarget}.
     */
    static void golemAndPlayer(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        IronGolem golem = foeAt(helper, EntityTypes.IRON_GOLEM, at(helper, 4.0, 0.0, 0.0));
        // Act
        try {
            Dice.MIRROR_CONFUSION.script(0);
            golem.setTarget(player);
        } finally {
            Dice.unscriptAll();
        }
        // Assert
        helper.assertTrue(player.equals(golem.getTarget()), "the golem is not redirected onto the player's images");
        helper.succeed();
    }

    /**
     * When the dice roll 0 and an iron golem picks an evoker with Mirror, should the golem end up targeting one of the
     * evoker's images.
     *
     * <p>Method under test: {@code MirrorImages.onChangeTarget}.
     */
    static void golemAndEvoker(GameTestHelper helper) {
        // Arrange
        plainPlayer(helper);
        floor(helper, 6);
        Evoker evoker = foeAt(helper, EntityTypes.EVOKER, at(helper, 0.0, 0.0, 0.0));
        Spells.cast(evoker, Spells.MIRROR, MirrorRule.DURATION_TICKS);
        IronGolem golem = foeAt(helper, EntityTypes.IRON_GOLEM, at(helper, 4.0, 0.0, 0.0));
        // Act
        try {
            Dice.MIRROR_CONFUSION.script(0);
            golem.setTarget(evoker);
        } finally {
            Dice.unscriptAll();
        }
        // Assert
        helper.assertTrue(
                golem.getTarget() instanceof MirrorImage image
                        && image.casterId().filter(evoker.getUUID()::equals).isPresent(),
                "the golem targets an image of the evoker");
        helper.succeed();
    }
}
