package org.millenaire.block;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class PaintedBrickStairBlock extends StairBlock implements IPaintedBlock {
   private final DyeColor color;

   public PaintedBrickStairBlock(DyeColor color, BlockState baseState, Properties properties) {
      super(baseState, properties);
      this.color = color;
   }

   public DyeColor getColor() {
      return this.color;
   }

   public PaintedBrickBlock.BrickType getBrickType() {
      return PaintedBrickBlock.BrickType.PLAIN;
   }
}
