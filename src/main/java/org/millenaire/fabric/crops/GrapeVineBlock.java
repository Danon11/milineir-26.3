package org.millenaire.fabric.crops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two-block vine: only the lower half ticks, both halves share growth. */
public final class GrapeVineBlock extends GenericCropBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public GrapeVineBlock(BlockBehaviour.Properties properties) {
        super(properties, "grapes", false, false);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    public boolean canPlacePair(LevelReader level, BlockPos pos) {
        return pos.above().getY() < level.getMaxY()
                && level.getBlockState(pos.above()).canBeReplaced()
                && super.canSurvive(defaultBlockState(), level, pos);
    }

    public boolean placePair(Level level, BlockPos pos) {
        if (!canPlacePair(level, pos)) return false;
        // Delay neighbour updates until both halves exist.
        if (!level.setBlock(pos, defaultBlockState(), Block.UPDATE_CLIENTS)) return false;
        if (!level.setBlock(pos.above(), defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return false;
        }
        level.updateNeighborsAt(pos, this);
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return canPlacePair(context.getLevel(), context.getClickedPos()) ? defaultBlockState() : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(pos.below());
            return lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                    && super.canSurvive(lower, level, pos.below());
        }
        return super.canSurvive(state, level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighbor, RandomSource random) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (direction == (lower ? Direction.UP : Direction.DOWN)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != state.getValue(HALF)
                    ? state.setValue(AGE, neighbor.getValue(AGE)) : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && !isMaxAge(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        BlockState upper = level.getBlockState(pos.above());
        if (!upper.is(this) || upper.getValue(HALF) != DoubleBlockHalf.UPPER) return;
        super.randomTick(state, level, pos, random);
        setPairAge(level, pos, getAge(level.getBlockState(pos)));
    }

    @Override
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        setPairAge(level, lowerPos, Math.min(getMaxAge(), getAge(state) + getBonemealAgeIncrease(level)));
    }

    private void setPairAge(Level level, BlockPos lowerPos, int age) {
        BlockState lower = level.getBlockState(lowerPos);
        BlockState upper = level.getBlockState(lowerPos.above());
        if (!lower.is(this) || !upper.is(this)) return;
        if (getAge(lower) != age) level.setBlock(lowerPos, lower.setValue(AGE, age), Block.UPDATE_CLIENTS);
        if (getAge(upper) != age) level.setBlock(lowerPos.above(), upper.setValue(AGE, age), Block.UPDATE_CLIENTS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
