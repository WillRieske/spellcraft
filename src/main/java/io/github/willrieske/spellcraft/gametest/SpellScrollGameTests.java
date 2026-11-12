package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.floor;
import static io.github.willrieske.spellcraft.gametest.ScrollTestSupport.plainPlayer;
import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.spell.ModItems;
import io.github.willrieske.spellcraft.spell.SpellScrollItem;
import io.github.willrieske.spellcraft.spell.Spells;
import io.github.willrieske.spellcraft.spell.rules.GhostrailDuration;
import io.github.willrieske.spellcraft.spell.rules.MirrorRule;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** GameTests of the Mirror and Ghostrail scrolls and of {@code Spells.cast} (Parts 6-7 spec §7.1, §10). */
public final class SpellScrollGameTests {
    private SpellScrollGameTests() {}

    /** Registers every spell-scroll and {@code Spells.cast} GameTest. */
    static void registerAll() {
        FangGameTests.addPadded(
                SHARED, "spell_scroll_mirror_gives_the_effect_for_7200_ticks", SpellScrollGameTests::mirrorScroll);
        FangGameTests.addPadded(
                SHARED,
                "spell_scroll_ghostrail_gives_the_effect_for_4800_ticks",
                SpellScrollGameTests::ghostrailScroll);
        FangGameTests.addPadded(
                SHARED, "spell_scroll_mirror_is_refused_while_active", SpellScrollGameTests::mirrorRefusedWhileActive);
        FangGameTests.addPadded(
                SHARED, "spell_scroll_mirror_sneak_cast_dispels", SpellScrollGameTests::mirrorSneakDispels);
        FangGameTests.addPadded(
                SHARED,
                "spell_scroll_ghostrail_is_refused_in_creative",
                SpellScrollGameTests::ghostrailRefusedInCreative);
        FangGameTests.addPadded(
                SHARED, "spell_scroll_mirror_is_allowed_in_creative", SpellScrollGameTests::mirrorAllowedInCreative);
        SpellcraftGameTests.add(
                SHARED, "spell_cast_helper_applies_any_spell_to_a_mob", SpellScrollGameTests::castHelperOnAMob);
    }

    /**
     * When a survival player reads a Mirror scroll, should they get Mirror for 7200 ticks and the scroll be used up.
     *
     * <p>Method under test: {@code SpellScrollItem#use} with the Mirror scroll
     */
    static void mirrorScroll(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        GameTestSupport.hold(player, new ItemStack(ModItems.MIRROR_SCROLL.get(), 2));
        // Act
        GameTestSupport.useHeld(helper, player);
        // Assert
        MobEffectInstance effect = GameTestSupport.requireEffect(helper, player, ModEffects.MIRROR);
        helper.assertValueEqual(effect.getDuration(), MirrorRule.DURATION_TICKS, "Mirror lasts 7200 ticks");
        helper.assertValueEqual(GameTestSupport.heldCount(player), 1, "one scroll is used up");
        helper.succeed();
    }

    /**
     * When a survival player reads a Ghostrail scroll, should they get Ghostrail for 4800 ticks and the scroll be
     * used up.
     *
     * <p>Method under test: {@code SpellScrollItem#use} with the Ghostrail scroll
     */
    static void ghostrailScroll(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        GameTestSupport.hold(player, new ItemStack(ModItems.GHOSTRAIL_SCROLL.get(), 2));
        // Act
        GameTestSupport.useHeld(helper, player);
        // Assert
        MobEffectInstance effect = GameTestSupport.requireEffect(helper, player, ModEffects.GHOSTRAIL);
        helper.assertValueEqual(effect.getDuration(), GhostrailDuration.PLAYER_TICKS, "Ghostrail lasts 4800 ticks");
        helper.assertValueEqual(GameTestSupport.heldCount(player), 1, "one scroll is used up");
        helper.succeed();
    }

    /**
     * When a player with Mirror reads a Mirror scroll, should the scroll be kept and the refusal shown on the action
     * bar.
     *
     * <p>Method under test: {@code SpellScrollItem#use} with an active Mirror
     */
    static void mirrorRefusedWhileActive(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, 100);
        GameTestSupport.hold(player, new ItemStack(ModItems.MIRROR_SCROLL.get(), 2));
        GameTestSupport.drainActionBar(player);
        // Act
        GameTestSupport.useHeld(helper, player);
        // Assert
        helper.assertValueEqual(GameTestSupport.heldCount(player), 2, "a refused scroll is kept");
        GameTestSupport.assertActionBarKey(helper, player, SpellScrollItem.REFUSED_KEY);
        helper.succeed();
    }

    /**
     * When a sneaking player with Mirror reads a Mirror scroll, should Mirror end and one scroll be used up.
     *
     * <p>Method under test: {@code SpellScrollItem#use} with an active Mirror, sneaking
     */
    static void mirrorSneakDispels(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        Spells.cast(player, Spells.MIRROR, 100);
        GameTestSupport.hold(player, new ItemStack(ModItems.MIRROR_SCROLL.get(), 2));
        player.setShiftKeyDown(true);
        // Act
        GameTestSupport.useHeld(helper, player);
        // Assert
        helper.assertFalse(player.hasEffect(ModEffects.MIRROR), "the effect is gone");
        helper.assertValueEqual(GameTestSupport.heldCount(player), 1, "one scroll is used up");
        helper.succeed();
    }

    /**
     * When a creative player reads a Ghostrail scroll, should the scroll be kept and no effect given (creative
     * already flies).
     *
     * <p>Method under test: {@code SpellScrollItem#use} with the Ghostrail scroll in creative
     */
    static void ghostrailRefusedInCreative(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        player.setGameMode(GameType.CREATIVE);
        GameTestSupport.hold(player, new ItemStack(ModItems.GHOSTRAIL_SCROLL.get(), 2));
        // Act
        GameTestSupport.useHeld(helper, player);
        // Assert
        helper.assertValueEqual(GameTestSupport.heldCount(player), 2, "a refused scroll is kept");
        helper.assertFalse(player.hasEffect(ModEffects.GHOSTRAIL), "no effect in creative");
        helper.succeed();
    }

    /**
     * When a creative player reads a Mirror scroll, should they get Mirror (it is not redundant in creative).
     *
     * <p>Method under test: {@code SpellScrollItem#use} with the Mirror scroll in creative
     */
    static void mirrorAllowedInCreative(GameTestHelper helper) {
        // Arrange
        ServerPlayer player = plainPlayer(helper);
        floor(helper, 6);
        player.setGameMode(GameType.CREATIVE);
        GameTestSupport.hold(player, new ItemStack(ModItems.MIRROR_SCROLL.get(), 2));
        // Act
        GameTestSupport.useHeld(helper, player);
        // Assert
        GameTestSupport.requireEffect(helper, player, ModEffects.MIRROR);
        helper.succeed();
    }

    /**
     * When Spells.cast is called on a zombie with 40 ticks, should the zombie carry that spell's effect for 40 ticks.
     *
     * <p>Method under test: {@link Spells#cast}
     */
    static void castHelperOnAMob(GameTestHelper helper) {
        // Arrange
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        zombie.setNoAi(true);
        // Act
        boolean added = Spells.cast(zombie, Spells.GHOSTRAIL, 40);
        // Assert
        helper.assertTrue(added, "a fresh effect is added");
        helper.assertValueEqual(zombie.getEffect(ModEffects.GHOSTRAIL).getDuration(), 40, "40 ticks");
        helper.assertFalse(zombie.getEffect(ModEffects.GHOSTRAIL).isVisible(), "no particles");
        helper.succeed();
    }
}
