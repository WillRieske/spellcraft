package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.infinity.InfinityAbilitySync;
import io.github.willrieske.spellcraft.infinity.InfinityAttachments;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Ability-sync GameTests for Strength of Infinity. Registered in {@link SpellcraftGameTests}. */
@SuppressWarnings("deprecation") // Every test asserts on Abilities#mayfly; see InfinityAbilitySync#sync for why.
public final class InfinityAbilityGameTests {
    /** {@code inventoryMenu} slot numbers: 36 = hotbar 0, 37 = hotbar 1 (docs/api-notes.md §11). */
    private static final int MENU_HOTBAR_0 = 36;

    private static final int MENU_HOTBAR_1 = 37;

    private InfinityAbilityGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "infinity_grants_in_survival", h -> grants(h, GameType.SURVIVAL));
        SpellcraftGameTests.add(SHARED, "infinity_grants_in_adventure", h -> grants(h, GameType.ADVENTURE));
        SpellcraftGameTests.add(SHARED, "infinity_effect_clear_revokes", InfinityAbilityGameTests::effectClearRevokes);
        SpellcraftGameTests.add(SHARED, "infinity_mode_switch_resumes", InfinityAbilityGameTests::modeSwitchResumes);
        SpellcraftGameTests.add(SHARED, "infinity_leaves_other_flight", InfinityAbilityGameTests::leavesOtherFlight);
        SpellcraftGameTests.add(
                SHARED, "infinity_creative_inventory_packet", InfinityAbilityGameTests::creativeInventoryPacket);
    }

    private static ServerPlayer granted(GameTestHelper helper, GameType mode) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        player.addEffect(InfinityGameTests.infinite());
        InfinityAbilitySync.sync(player);
        return player;
    }

    private static void grants(GameTestHelper helper, GameType mode) {
        ServerPlayer player = granted(helper, mode);
        Abilities abilities = player.getAbilities();
        helper.assertTrue(abilities.mayfly, "may fly");
        helper.assertTrue(abilities.instabuild, "creative inventory and building");
        helper.assertFalse(abilities.invulnerable, "still mortal");
        helper.assertTrue(player.getData(InfinityAttachments.GRANTED), "marked as granted by Infinity");
        helper.succeed();
    }

    static void effectClearRevokes(GameTestHelper helper) {
        ServerPlayer player = granted(helper, GameType.SURVIVAL);
        player.getAbilities().flying = true;
        player.removeAllEffects();
        InfinityAbilitySync.sync(player);
        Abilities abilities = player.getAbilities();
        helper.assertFalse(abilities.mayfly, "flight revoked");
        helper.assertFalse(abilities.instabuild, "creative building revoked");
        helper.assertFalse(abilities.flying, "stops flying: you fall");
        helper.assertFalse(player.getData(InfinityAttachments.GRANTED), "mark cleared");
        helper.succeed();
    }

    static void modeSwitchResumes(GameTestHelper helper) {
        ServerPlayer player = granted(helper, GameType.SURVIVAL);
        player.setGameMode(GameType.CREATIVE);
        InfinityAbilitySync.sync(player);
        helper.assertFalse(player.getData(InfinityAttachments.GRANTED), "creative owns the flags");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertFalse(player.getAbilities().mayfly, "precondition: vanilla reset the flags");
        InfinityAbilitySync.sync(player);
        helper.assertTrue(player.getAbilities().mayfly, "flight regained in survival");
        helper.assertTrue(player.getAbilities().instabuild, "creative building regained in survival");
        helper.succeed();
    }

    static void leavesOtherFlight(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.getAbilities().mayfly = true; // as another mod would
        InfinityAbilitySync.sync(player);
        helper.assertTrue(player.getAbilities().mayfly, "unmarked flags are never touched");
        helper.succeed();
    }

    static void creativeInventoryPacket(GameTestHelper helper) {
        ServerPlayer player = granted(helper, GameType.SURVIVAL);
        player.connection.handleSetCreativeModeSlot(
                new ServerboundSetCreativeModeSlotPacket(MENU_HOTBAR_0, new ItemStack(Items.DIAMOND)));
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND), "accepted with Infinity");
        player.removeAllEffects();
        InfinityAbilitySync.sync(player);
        player.connection.handleSetCreativeModeSlot(
                new ServerboundSetCreativeModeSlotPacket(MENU_HOTBAR_1, new ItemStack(Items.EMERALD)));
        helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "rejected after Infinity ends");
        helper.succeed();
    }
}
