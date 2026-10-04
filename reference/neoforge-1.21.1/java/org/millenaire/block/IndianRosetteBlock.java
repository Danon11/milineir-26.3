package org.millenaire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;

public class IndianRosetteBlock extends IronBarsBlock {
   public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

   public IndianRosetteBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any())
                              .setValue(NORTH, false))
                           .setValue(EAST, false))
                        .setValue(SOUTH, false))
                     .setValue(WEST, false))
                  .setValue(WATERLOGGED, false))
               .setValue(FACING, Direction.SOUTH))
            .setValue(HALF, Half.TOP)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{FACING, HALF});
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockState baseState = super.getStateForPlacement(context);
      return baseState == null ? null : this.computeRosettePattern(baseState, context.getLevel(), context.getClickedPos());
   }

   private BlockState computeRosettePattern(BlockState state, BlockGetter level, BlockPos pos) {
      BlockState above = level.getBlockState(pos.above());
      BlockState below = level.getBlockState(pos.below());
      BlockState west = level.getBlockState(pos.west());
      BlockState east = level.getBlockState(pos.east());
      BlockState south = level.getBlockState(pos.south());
      BlockState north = level.getBlockState(pos.north());
      if (above.getBlock() == this && above.getValue(HALF) == Half.TOP) {
         return (BlockState)((BlockState)state.setValue(HALF, Half.BOTTOM)).setValue(FACING, (Direction)above.getValue(FACING));
      }

      if (west.getBlock() == this && west.getValue(FACING) == Direction.WEST) {
         return (BlockState)((BlockState)state.setValue(FACING, Direction.EAST)).setValue(HALF, (Half)west.getValue(HALF));
      }

      if (south.getBlock() == this && south.getValue(FACING) == Direction.SOUTH) {
         return (BlockState)((BlockState)state.setValue(FACING, Direction.NORTH)).setValue(HALF, (Half)south.getValue(HALF));
      }

      if (below.getBlock() == this && below.getValue(HALF) == Half.BOTTOM) {
         return (BlockState)((BlockState)state.setValue(HALF, Half.TOP)).setValue(FACING, (Direction)below.getValue(FACING));
      }

      if (east.getBlock() == this && east.getValue(FACING) == Direction.EAST) {
         return (BlockState)((BlockState)state.setValue(FACING, Direction.WEST)).setValue(HALF, (Half)east.getValue(HALF));
      }

      if (north.getBlock() == this && north.getValue(FACING) == Direction.NORTH) {
         return (BlockState)((BlockState)state.setValue(FACING, Direction.SOUTH)).setValue(HALF, (Half)north.getValue(HALF));
      }

      BlockState result = state;
      if (!above.isSolidRender(level, pos.above()) && below.isSolidRender(level, pos.below())) {
         result = (BlockState)result.setValue(HALF, Half.BOTTOM);
      }

      if (!west.isSolidRender(level, pos.west()) && east.isSolidRender(level, pos.east())) {
         result = (BlockState)result.setValue(FACING, Direction.EAST);
      } else if (!south.isSolidRender(level, pos.south()) && north.isSolidRender(level, pos.north())) {
         result = (BlockState)result.setValue(FACING, Direction.NORTH);
      } else if (south.isSolidRender(level, pos.south()) && !north.isSolidRender(level, pos.north())) {
         result = (BlockState)result.setValue(FACING, Direction.SOUTH);
      } else if (west.isSolidRender(level, pos.west()) && !east.isSolidRender(level, pos.east())) {
         result = (BlockState)result.setValue(FACING, Direction.WEST);
      }

      return result;
   }
}
