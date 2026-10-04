package org.millenaire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SlidingDoorBlock extends DoorBlock {
   private static final VoxelShape CLOSED_NS = Block.box(0.0, 0.0, 7.0, 16.0, 16.0, 9.0);
   private static final VoxelShape CLOSED_EW = Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 16.0);
   private static final VoxelShape OPEN_SOUTH = Block.box(7.0, 0.0, 14.0, 9.0, 16.0, 30.0);
   private static final VoxelShape OPEN_NORTH = Block.box(7.0, 0.0, -14.0, 9.0, 16.0, 2.0);
   private static final VoxelShape OPEN_EAST = Block.box(14.0, 0.0, 7.0, 30.0, 16.0, 9.0);
   private static final VoxelShape OPEN_WEST = Block.box(-14.0, 0.0, 7.0, 2.0, 16.0, 9.0);

   public SlidingDoorBlock(BlockSetType type, Properties props) {
      super(type, props);
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      if ((Boolean)state.getValue(OPEN)) {
         Direction facing = (Direction)state.getValue(FACING);
         boolean hingeRight = state.getValue(HINGE) == DoorHingeSide.RIGHT;

         return switch (facing) {
            case EAST -> hingeRight ? OPEN_SOUTH : OPEN_NORTH;
            case SOUTH -> hingeRight ? OPEN_EAST : OPEN_WEST;
            case WEST -> hingeRight ? OPEN_NORTH : OPEN_SOUTH;
            case NORTH -> hingeRight ? OPEN_WEST : OPEN_EAST;
            default -> CLOSED_EW;
         };
      } else {
         Direction facing = (Direction)state.getValue(FACING);
         return facing != Direction.NORTH && facing != Direction.SOUTH ? CLOSED_EW : CLOSED_NS;
      }
   }

   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return super.getShape(state, level, pos, ctx);
   }
}
