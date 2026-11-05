package io.github.willrieske.spellcraft.trailing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flame a trailing player leaves behind (spec §3). Looks like soul fire (its models), sits on any sturdy block,
 * spreads to nothing, has no scheduled tick (so never ignites TNT; its random tick only cleans up orphans), lights no
 * portal, and spares the owner and their allies.
 */
public final class TrailFireBlock extends BaseFireBlock {
    /** Soul fire's damage. */
    public static final float FIRE_DAMAGE = 2.0F;

    public TrailFireBlock(BlockBehaviour.Properties properties) {
        super(properties, FIRE_DAMAGE);
    }

    /** Required by {@code BaseFireBlock}; trail fire burns nothing. */
    @Override
    protected boolean canBurn(BlockState state) {
        return false;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbour,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /** Only the survive check: {@code BaseFireBlock.onPlace} would also light a nether portal. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !state.canSurvive(level, pos)) {
            level.removeBlock(pos, false);
        }
    }

    /** Server only; spared entities (the owner and their allies) feel nothing, everyone else gets soul fire's. */
    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effectApplier,
            boolean isPrecise) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (TrailFires.of(serverLevel).isSpared(serverLevel, pos, entity)) {
            return;
        }
        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
    }

    /** An orphan (no owner in the level's ledger) puts itself out; an owned flame waits for its fade. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (TrailFires.of(level).ownerOf(pos).isEmpty()) {
            level.removeBlock(pos, false);
        }
    }
}
