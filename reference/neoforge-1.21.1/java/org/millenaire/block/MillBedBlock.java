package org.millenaire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MillBedBlock extends BedBlock {
   private final int heightPixels;
   private final VoxelShape shape;

   public MillBedBlock(DyeColor color, int heightPixels, Properties props) {
      super(color, props);
      this.heightPixels = heightPixels;
      this.shape = box(0.0, 0.0, 0.0, 16.0, heightPixels, 16.0);
   }

   public int getHeightPixels() {
      return this.heightPixels;
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.shape;
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return null;
   }
}
