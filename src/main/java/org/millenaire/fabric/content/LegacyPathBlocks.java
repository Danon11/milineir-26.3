package org.millenaire.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Legacy stable flag is consumed by village path maintenance; full paths are 15/16 high. */
public final class LegacyPathBlocks {
    public static final BooleanProperty STABLE = BooleanProperty.create("stable");
    private LegacyPathBlocks() {}
    public static Block create(boolean slab, BlockBehaviour.Properties properties) {
        return slab ? new PathSlab(properties) : new Path(properties.noOcclusion());
    }
    private static final class Path extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);
        Path(BlockBehaviour.Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(STABLE, false)); }
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(STABLE); }
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
        public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(STABLE, true); }
    }
    private static final class PathSlab extends SlabBlock {
        PathSlab(BlockBehaviour.Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(STABLE, false)); }
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(STABLE); }
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState state = super.getStateForPlacement(context);
            return state == null ? null : state.setValue(STABLE, true);
        }
    }
}
