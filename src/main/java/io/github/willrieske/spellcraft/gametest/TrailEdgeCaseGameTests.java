package io.github.willrieske.spellcraft.gametest;

import static io.github.willrieske.spellcraft.gametest.SpellcraftGameTests.SHARED;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.A;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.B;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.C;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.D;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.assertBurned;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.assertSpared;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.fade;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.floors;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.forget;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.ledger;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.light;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.standAt;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.standAtExactly;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.stepInto;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.teleport;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.trailFire;
import static io.github.willrieske.spellcraft.gametest.TrailGameTests.trailingPlayer;

import io.github.willrieske.spellcraft.effect.ModEffects;
import io.github.willrieske.spellcraft.trailing.TrailFires;
import io.github.willrieske.spellcraft.trailing.TrailingAttachments;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * GameTests for the rows of the spec's "Edge cases" table and the save wiring (spec §6, table G1–G28). Uses the
 * helpers in {@link TrailGameTests}. Registered in {@link SpellcraftGameTests}.
 */
public final class TrailEdgeCaseGameTests {
    /** Neighbors of A for G25: a flammable block and TNT. */
    private static final BlockPos PLANKS_SPOT = new BlockPos(1, 1, 0);

    private static final BlockPos TNT_SPOT = new BlockPos(0, 1, 1);
    /** The structure's size along each axis (relative coordinates 0 … 2). */
    private static final int STRUCTURE_SIZE = 3;

    private TrailEdgeCaseGameTests() {}

    static void registerAll() {
        SpellcraftGameTests.add(
                SHARED, "trail_g17_stand_in_cell_above_soul_sand", TrailEdgeCaseGameTests::g17StandInCellAboveSoulSand);
        SpellcraftGameTests.add(
                "team_mutation", "trail_g23_offline_owner_spares_no_ally", TrailEdgeCaseGameTests::g23OfflineOwner);
        SpellcraftGameTests.add(
                SHARED, "trail_g24_owner_spared_after_the_effect", TrailEdgeCaseGameTests::g24OwnerAfterTheEffect);
        SpellcraftGameTests.add(SHARED, "trail_g25_no_spread_no_tnt", TrailEdgeCaseGameTests::g25NoSpreadNoTnt);
        SpellcraftGameTests.add(SHARED, "trail_g26_carpet_and_top_slab", TrailEdgeCaseGameTests::g26CarpetAndTopSlab);
        SpellcraftGameTests.add(
                SHARED, "trail_g27_ledger_saved_with_the_level", TrailEdgeCaseGameTests::g27LedgerSavedWithTheLevel);
    }

    /** A player on soul sand stands 2/16 inside it, so the cell above the sand (the stand-in cell) takes the flame. */
    static void g17StandInCellAboveSoulSand(GameTestHelper helper) {
        floors(helper);
        helper.setBlock(A.below(), Blocks.SOUL_SAND);
        ServerPlayer player = trailingPlayer(helper);
        Vec3 sunk = new Vec3(A.getX() + 0.5, A.below().getY() + 0.875, A.getZ() + 0.5);
        MockPlayers.moveTo(helper, player, sunk);
        helper.assertValueEqual(player.blockPosition(), helper.absolutePos(A.below()), "feet block is the sand");
        standAtExactly(helper, player, sunk, true);
        standAt(helper, player, B, true);
        helper.assertBlockPresent(trailFire(), A);
        helper.assertBlockPresent(Blocks.SOUL_SAND, A.below());
        fade(helper, 39);
        helper.assertBlockPresent(trailFire(), A);
        fade(helper, 1);
        helper.assertBlockNotPresent(trailFire(), A);
        forget(helper, player);
        helper.succeed();
    }

    /** Puts the owner on a team, so it runs in the {@code team_mutation} batch and removes the team after. */
    static void g23OfflineOwner(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        UUID ownerId = owner.getUUID();
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("spellcraft_trail_offline_test");
        try {
            Zombie ally = helper.spawn(EntityTypes.ZOMBIE, B);
            scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
            helper.assertTrue(owner.isAlliedTo(ally), "setup: the zombie is the owner's ally");
            owner.discard();
            helper.assertTrue(helper.getLevel().getPlayerByUUID(ownerId) == null, "setup: the owner is offline");
            stepInto(helper, A, ally);
            assertBurned(helper, ally, "an offline owner's ally");
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        ledger(helper).forget(ownerId);
        helper.succeed();
    }

    static void g24OwnerAfterTheEffect(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        teleport(helper, owner, D);
        helper.assertTrue(light(helper, owner, A), "lit A");
        owner.removeEffect(ModEffects.TRAILING);
        helper.assertFalse(owner.hasEffect(ModEffects.TRAILING), "setup: the effect is gone");
        stepInto(helper, A, owner);
        assertSpared(helper, owner, "the owner after the effect ended");
        forget(helper, owner);
        helper.succeed();
    }

    /** The flame's scheduled and random ticks spread to nothing and light no TNT, even a few ticks later. */
    static void g25NoSpreadNoTnt(GameTestHelper helper) {
        floors(helper);
        ServerPlayer owner = trailingPlayer(helper);
        helper.assertTrue(light(helper, owner, A), "lit A");
        helper.setBlock(PLANKS_SPOT, Blocks.OAK_PLANKS);
        helper.setBlock(TNT_SPOT, Blocks.TNT);
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        level.getBlockState(a).tick(level, a, level.getRandom());
        level.getBlockState(a).randomTick(level, a, level.getRandom());
        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(Blocks.OAK_PLANKS, PLANKS_SPOT);
            helper.assertBlockPresent(Blocks.TNT, TNT_SPOT);
            helper.assertTrue(
                    level.getEntities(EntityTypes.TNT, helper.getBounds(), e -> true)
                            .isEmpty(),
                    "no primed TNT");
            for (BlockPos spot : BlockPos.betweenClosed(
                    BlockPos.ZERO, new BlockPos(STRUCTURE_SIZE - 1, STRUCTURE_SIZE - 1, STRUCTURE_SIZE - 1))) {
                if (!spot.equals(A)) {
                    helper.assertFalse(
                            helper.getBlockState(spot).is(BlockTags.FIRE), "no fire at " + spot.toShortString());
                }
            }
            forget(helper, owner);
            helper.succeed();
        });
    }

    /** Carpet is the feet block and has no sturdy top: no flame. A top slab's surface is a full face: it lights. */
    static void g26CarpetAndTopSlab(GameTestHelper helper) {
        floors(helper);
        ServerPlayer player = trailingPlayer(helper);
        helper.setBlock(A, Blocks.MOSS_CARPET);
        Vec3 onCarpet = new Vec3(A.getX() + 0.5, A.getY() + 1.0 / 16, A.getZ() + 0.5);
        MockPlayers.moveTo(helper, player, onCarpet);
        helper.assertValueEqual(player.blockPosition(), helper.absolutePos(A), "feet block is the carpet");
        standAtExactly(helper, player, onCarpet, true);
        standAt(helper, player, B, false);
        helper.assertBlockPresent(Blocks.MOSS_CARPET, A);
        helper.assertBlockNotPresent(trailFire(), A.above());
        helper.setBlock(C, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        standAt(helper, player, C.above(), true);
        standAt(helper, player, D, true);
        helper.assertBlockPresent(trailFire(), C.above());
        forget(helper, player);
        helper.succeed();
    }

    /**
     * Proves the attachment's {@code serialize(CODEC, predicate)} wiring through the level's own save call. Other tests
     * leave trails in the level's ledger, so this swaps in a fresh ledger for its (synchronous) body and restores the
     * shared one after.
     */
    static void g27LedgerSavedWithTheLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String key = TrailingAttachments.FIRES.getId().toString();
        TrailFires shared = level.getData(TrailingAttachments.FIRES);
        level.setData(TrailingAttachments.FIRES, new TrailFires());
        try {
            helper.assertFalse(save(level).contains(key), "an empty ledger is not written");
            floors(helper);
            ServerPlayer owner = trailingPlayer(helper);
            helper.assertTrue(light(helper, owner, A), "lit A");
            CompoundTag saved = save(level);
            helper.assertTrue(saved.contains(key), "a ledger with a flame is written");
            TrailFires loaded = TrailFires.CODEC
                    .codec()
                    .parse(NbtOps.INSTANCE, saved.getCompoundOrEmpty(key))
                    .getOrThrow();
            helper.assertValueEqual(loaded.entries(), ledger(helper).entries(), "trails after the save");
            helper.assertValueEqual(loaded.staleFlames(), ledger(helper).staleFlames(), "stale flames after the save");
            BlockPos a = helper.absolutePos(A);
            helper.assertValueEqual(loaded.ownerOf(a), Optional.of(owner.getUUID()), "owner after the save");
            helper.setBlock(A, Blocks.AIR); // the shared ledger does not know this flame
        } finally {
            level.setData(TrailingAttachments.FIRES, shared);
        }
        helper.succeed();
    }

    /** Writes the level's attachments the way {@code LevelAttachmentsSavedData} does on a world save. */
    private static CompoundTag save(ServerLevel level) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        level.serializeAttachments(output);
        return output.buildResult();
    }
}
