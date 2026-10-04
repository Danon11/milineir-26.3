package org.millenaire.block;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class PaintedBrickBlock extends Block implements IPaintedBlock {
   private final DyeColor color;
   private final PaintedBrickBlock.BrickType brickType;

   public PaintedBrickBlock(DyeColor color, PaintedBrickBlock.BrickType brickType, Properties properties) {
      super(properties);
      this.color = color;
      this.brickType = brickType;
   }

   public DyeColor getColor() {
      return this.color;
   }

   public PaintedBrickBlock.BrickType getBrickType() {
      return this.brickType;
   }

   public enum BrickType {
      PLAIN,
      DECORATED;
   }
}
