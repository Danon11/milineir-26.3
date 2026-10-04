package org.millenaire.block;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class HorizontalRotatedPillarBlock extends RotatedPillarBlock {
   public HorizontalRotatedPillarBlock(Properties properties) {
      super(properties);
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
   }
}
