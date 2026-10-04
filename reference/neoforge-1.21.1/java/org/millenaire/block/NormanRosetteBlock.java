package org.millenaire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class NormanRosetteBlock extends IronBarsBlock {
   public static final BooleanProperty ROS_NORTH = BooleanProperty.create("ros_n");
   public static final BooleanProperty ROS_EAST = BooleanProperty.create("ros_e");
   public static final BooleanProperty ROS_SOUTH = BooleanProperty.create("ros_s");
   public static final BooleanProperty ROS_WEST = BooleanProperty.create("ros_w");
   public static final BooleanProperty ROS_UP = BooleanProperty.create("ros_u");
   public static final BooleanProperty ROS_DOWN = BooleanProperty.create("ros_d");

   public NormanRosetteBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition
                                             .any())
                                          .setValue(NORTH, false))
                                       .setValue(EAST, false))
                                    .setValue(SOUTH, false))
                                 .setValue(WEST, false))
                              .setValue(WATERLOGGED, false))
                           .setValue(ROS_NORTH, false))
                        .setValue(ROS_EAST, false))
                     .setValue(ROS_SOUTH, false))
                  .setValue(ROS_WEST, false))
               .setValue(ROS_UP, false))
            .setValue(ROS_DOWN, false)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{ROS_NORTH, ROS_EAST, ROS_SOUTH, ROS_WEST, ROS_UP, ROS_DOWN});
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockState state = super.getStateForPlacement(context);
      if (state == null) {
         return null;
      }

      BlockGetter level = context.getLevel();
      BlockPos pos = context.getClickedPos();
      return this.withRosetteProperties(state, level, pos);
   }

   protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
      BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
      return this.withRosetteProperties(updated, level, pos);
   }

   private boolean hasRosette(BlockGetter level, BlockPos pos, Direction direction) {
      return level.getBlockState(pos.relative(direction)).getBlock() == this;
   }

   private BlockState withRosetteProperties(BlockState state, BlockGetter level, BlockPos pos) {
      return (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)state.setValue(
                        ROS_NORTH, this.hasRosette(level, pos, Direction.NORTH)
                     ))
                     .setValue(ROS_EAST, this.hasRosette(level, pos, Direction.EAST)))
                  .setValue(ROS_SOUTH, this.hasRosette(level, pos, Direction.SOUTH)))
               .setValue(ROS_WEST, this.hasRosette(level, pos, Direction.WEST)))
            .setValue(ROS_UP, this.hasRosette(level, pos, Direction.UP)))
         .setValue(ROS_DOWN, this.hasRosette(level, pos, Direction.DOWN));
   }
}
