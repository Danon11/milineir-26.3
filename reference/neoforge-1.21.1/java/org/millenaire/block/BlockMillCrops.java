package org.millenaire.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class BlockMillCrops extends CropBlock {
   private final boolean requireIrrigation;
   private final boolean slowGrowth;

   public BlockMillCrops(boolean requireIrrigation, boolean slowGrowth, Properties properties) {
      super(properties);
      this.requireIrrigation = requireIrrigation;
      this.slowGrowth = slowGrowth;
   }

   public boolean requiresIrrigation() {
      return this.requireIrrigation;
   }

   public boolean isSlowGrowth() {
      return this.slowGrowth;
   }

   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (this.requireIrrigation) {
         BlockState below = level.getBlockState(pos.below());
         if (below.getBlock() instanceof FarmBlock && (Integer)below.getValue(FarmBlock.MOISTURE) == 0) {
            return;
         }
      }

      if (!this.slowGrowth || !random.nextBoolean()) {
         super.randomTick(state, level, pos, random);
      }
   }

   protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
      return state.getBlock() instanceof FarmBlock;
   }
}
