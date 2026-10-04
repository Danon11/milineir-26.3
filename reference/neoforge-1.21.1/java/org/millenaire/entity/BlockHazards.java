package org.millenaire.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockHazards {
   private BlockHazards() {
   }

   public static boolean isHazardous(BlockState state) {
      if (state.is(Blocks.FIRE)) {
         return true;
      } else if (state.is(Blocks.SOUL_FIRE)) {
         return true;
      } else if (state.is(Blocks.LAVA)) {
         return true;
      } else if (state.is(Blocks.MAGMA_BLOCK)) {
         return true;
      } else if (state.is(Blocks.CACTUS)) {
         return true;
      } else if (state.is(Blocks.WITHER_ROSE)) {
         return true;
      } else {
         return state.is(Blocks.POWDER_SNOW)
            ? true
            : state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && (Boolean)state.getValue(CampfireBlock.LIT);
      }
   }

   public static boolean isHazardousAt(BlockGetter level, BlockPos feet) {
      return isHazardous(level.getBlockState(feet)) || isHazardous(level.getBlockState(feet.below()));
   }
}
