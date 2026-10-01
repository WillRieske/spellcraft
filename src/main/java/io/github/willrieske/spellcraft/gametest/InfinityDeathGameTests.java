package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.infinity.DeathStash;
import io.github.willrieske.spellcraft.infinity.InfinityAbilitySync;
import io.github.willrieske.spellcraft.infinity.InfinityAttachments;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/** Death-rule GameTests for Strength of Infinity (spec §8 G1–G11). Registered in {@link SpellcraftGameTests}. */
public final class InfinityDeathGameTests {
    private static final int MAIN_SLOT = 20;
    private static final double NEAR = 4.0;

    private InfinityDeathGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(SHARED, "infinity_death_g1_no_drops", InfinityDeathGameTests::g1NoDrops);
        SpellcraftGameTests.add(SHARED, "infinity_death_g2_restores", InfinityDeathGameTests::g2Restores);
        SpellcraftGameTests.add(SHARED, "infinity_death_g3_xp_vanilla", InfinityDeathGameTests::g3XpVanilla);
        SpellcraftGameTests.add(SHARED, "infinity_death_g4_ends_infinity", InfinityDeathGameTests::g4EndsInfinity);
        SpellcraftGameTests.add(SHARED, "infinity_death_g5_vanishing", InfinityDeathGameTests::g5Vanishing);
        SpellcraftGameTests.add(
                "keep_inventory", "infinity_death_g6_keep_inventory", InfinityDeathGameTests::g6KeepInventory);
        SpellcraftGameTests.add(
                SHARED, "infinity_death_g7_without_infinity", InfinityDeathGameTests::g7WithoutInfinity);
        SpellcraftGameTests.add(SHARED, "infinity_death_g8_totem", InfinityDeathGameTests::g8Totem);
        SpellcraftGameTests.add(SHARED, "infinity_death_g9_stash_codec", InfinityDeathGameTests::g9StashCodec);
        SpellcraftGameTests.add(SHARED, "infinity_death_g10_nether", InfinityDeathGameTests::g10Nether);
        SpellcraftGameTests.add(SHARED, "infinity_death_g11_flying", InfinityDeathGameTests::g11Flying);
    }

    /** Infinity; diamonds in hotbar 0, cobblestone in main slot 20, a shield in the offhand, helmet and boots worn. */
    private static ServerPlayer kitted(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveIntoTest(helper, player);
        player.addEffect(InfinityGameTests.infinite());
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
        inventory.setItem(MAIN_SLOT, new ItemStack(Items.COBBLESTONE, 64));
        inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        return player;
    }

    private static ServerPlayer respawn(GameTestHelper helper, ServerPlayer dead) {
        return helper.getLevel().getServer().getPlayerList().respawn(dead, false, Entity.RemovalReason.KILLED);
    }

    private static int itemsNear(ServerPlayer player) {
        return player.level()
                .getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(NEAR))
                .size();
    }

    private static void assertKitRestored(GameTestHelper helper, ServerPlayer respawned) {
        Inventory inventory = respawned.getInventory();
        helper.assertTrue(inventory.getItem(0).is(Items.DIAMOND), "hotbar restored in place");
        helper.assertValueEqual(inventory.getItem(0).getCount(), 3, "hotbar count");
        helper.assertTrue(inventory.getItem(MAIN_SLOT).is(Items.COBBLESTONE), "main inventory restored in place");
        helper.assertTrue(inventory.getItem(Inventory.SLOT_OFFHAND).is(Items.SHIELD), "offhand restored");
        helper.assertTrue(respawned.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "helmet deleted");
        helper.assertTrue(respawned.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "boots deleted");
    }

    static void g1NoDrops(GameTestHelper helper) {
        ServerPlayer player = kitted(helper);
        player.kill(helper.getLevel());
        helper.assertValueEqual(itemsNear(player), 0, "no item drops");
        List<Integer> slots = player.getData(InfinityAttachments.STASH).entries().stream()
                .map(DeathStash.Entry::slot)
                .toList();
        helper.assertValueEqual(slots, List.of(0, MAIN_SLOT, Inventory.SLOT_OFFHAND), "stashed slots");
        helper.succeed();
    }

    static void g2Restores(GameTestHelper helper) {
        ServerPlayer player = kitted(helper);
        player.kill(helper.getLevel());
        ServerPlayer respawned = respawn(helper, player);
        assertKitRestored(helper, respawned);
        helper.assertTrue(player.getData(InfinityAttachments.STASH).entries().isEmpty(), "stash cleared");
        helper.succeed();
    }

    static void g3XpVanilla(GameTestHelper helper) {
        ServerPlayer player = kitted(helper);
        player.setExperienceLevels(10);
        player.kill(helper.getLevel());
        int orbs = player.level()
                .getEntitiesOfClass(ExperienceOrb.class, player.getBoundingBox().inflate(NEAR))
                .size();
        helper.assertTrue(orbs > 0, "XP orbs drop as vanilla");
        helper.assertValueEqual(respawn(helper, player).experienceLevel, 0, "respawn at 0 levels");
        helper.succeed();
    }

    @SuppressWarnings("deprecation") // Abilities#mayfly is @Deprecated; see InfinityAbilitySync#sync.
    static void g4EndsInfinity(GameTestHelper helper) {
        ServerPlayer player = kitted(helper);
        InfinityAbilitySync.sync(player);
        player.kill(helper.getLevel());
        ServerPlayer respawned = respawn(helper, player);
        InfinityAbilitySync.sync(respawned);
        helper.assertFalse(respawned.hasEffect(ModEffects.INFINITY), "Infinity gone after respawn");
        helper.assertFalse(respawned.getAbilities().mayfly, "no flight");
        helper.assertFalse(respawned.getAbilities().instabuild, "no creative inventory");
        helper.assertFalse(respawned.getData(InfinityAttachments.GRANTED), "unmarked");
        helper.succeed();
    }

    static void g5Vanishing(GameTestHelper helper) {
        ServerPlayer player = kitted(helper);
        ItemStack cursed = new ItemStack(Items.GOLDEN_SWORD);
        cursed.enchant(
                helper.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.VANISHING_CURSE),
                1);
        player.getInventory().setItem(1, cursed);
        player.kill(helper.getLevel());
        helper.assertValueEqual(itemsNear(player), 0, "vanishing item not dropped");
        ServerPlayer respawned = respawn(helper, player);
        helper.assertTrue(respawned.getInventory().getItem(1).isEmpty(), "vanishing item not restored");
        helper.succeed();
    }

    /** Mutates a global game rule, so it runs alone in the {@code keep_inventory} batch. */
    static void g6KeepInventory(GameTestHelper helper) {
        GameRules rules = helper.getLevel().getGameRules();
        boolean saved = rules.get(GameRules.KEEP_INVENTORY);
        rules.set(GameRules.KEEP_INVENTORY, true, helper.getLevel().getServer());
        try {
            ServerPlayer player = kitted(helper);
            player.kill(helper.getLevel());
            helper.assertTrue(
                    player.getData(InfinityAttachments.STASH).entries().isEmpty(), "rule doesn't run");
            ServerPlayer respawned = respawn(helper, player);
            helper.assertTrue(respawned.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "armor kept");
            helper.assertTrue(respawned.getInventory().getItem(0).is(Items.DIAMOND), "hotbar kept");
        } finally {
            rules.set(GameRules.KEEP_INVENTORY, saved, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    static void g7WithoutInfinity(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MockPlayers.moveIntoTest(helper, player);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        player.kill(helper.getLevel());
        helper.assertTrue(itemsNear(player) > 0, "vanilla drops without Infinity");
        helper.assertTrue(player.getData(InfinityAttachments.STASH).entries().isEmpty(), "nothing stashed");
        helper.succeed();
    }

    static void g8Totem(GameTestHelper helper) {
        ServerPlayer player = kitted(helper);
        player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);
        helper.assertTrue(player.isAlive(), "the totem saves you");
        helper.assertTrue(player.hasEffect(ModEffects.INFINITY), "Infinity survives the totem");
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND), "inventory untouched");
        helper.assertTrue(player.getInventory().getItem(Inventory.SLOT_OFFHAND).isEmpty(), "totem used up");
        helper.assertTrue(player.getData(InfinityAttachments.STASH).entries().isEmpty(), "nothing stashed");
        helper.succeed();
    }

    /** The stash is saved with the player (logout on the death screen); round-trip it through the save codec. */
    static void g9StashCodec(GameTestHelper helper) {
        DeathStash stash = new DeathStash(List.of(
                new DeathStash.Entry(0, new ItemStack(Items.DIAMOND, 3)),
                new DeathStash.Entry(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD))));
        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = DeathStash.CODEC.codec().encodeStart(ops, stash).getOrThrow();
        DeathStash loaded = DeathStash.CODEC.codec().parse(ops, saved).getOrThrow();
        helper.assertValueEqual(loaded.entries().size(), 2, "entries survive");
        for (int i = 0; i < 2; i++) {
            DeathStash.Entry before = stash.entries().get(i);
            DeathStash.Entry after = loaded.entries().get(i);
            helper.assertValueEqual(after.slot(), before.slot(), "slot survives");
            helper.assertTrue(ItemStack.matches(after.stack(), before.stack()), "stack survives");
        }
        helper.succeed();
    }

    static void g10Nether(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw helper.assertionException("no Nether on the GameTest server: move G10 to the playtest");
        }
        ServerPlayer player = kitted(helper);
        player.teleportTo(nether, 0.5, 64, 0.5, Set.of(), 0.0F, 0.0F, false);
        // A dimension change leaves the player invulnerable until the next tick; end that so the kill lands.
        player.hasChangedDimension();
        player.connection.markClientLoaded();
        player.kill(nether);
        ServerPlayer respawned = respawn(helper, player);
        helper.assertValueEqual(respawned.level().dimension(), Level.OVERWORLD, "respawned in the Overworld");
        assertKitRestored(helper, respawned);
        helper.succeed();
    }

    static void g11Flying(GameTestHelper helper) {
        ServerPlayer player = kitted(helper);
        InfinityAbilitySync.sync(player);
        player.getAbilities().flying = true;
        player.kill(helper.getLevel());
        helper.assertValueEqual(itemsNear(player), 0, "no item drops");
        ServerPlayer respawned = respawn(helper, player);
        helper.assertFalse(respawned.getAbilities().flying, "respawn grounded");
        helper.succeed();
    }
}
