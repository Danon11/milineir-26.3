package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.AppleTreeLeavesBlock;
import org.millenaire.block.ModBlocks;

public final class AppleTreeGenerator extends AbstractTreeGenerator {
   private static final AppleTreeGenerator INSTANCE = new AppleTreeGenerator();

   private AppleTreeGenerator() {
   }

   protected Block getLogBlock() {
      return Blocks.OAK_LOG;
   }

   protected BlockState getLeavesState() {
      return (BlockState)((BlockState)((AppleTreeLeavesBlock)ModBlocks.APPLE_TREE_LEAVES.get()).defaultBlockState().setValue(AppleTreeLeavesBlock.AGE, 0))
         .setValue(AppleTreeLeavesBlock.PERSISTENT, true);
   }

   public static boolean generate(ServerLevel level, BlockPos position, RandomSource rand) {
      return INSTANCE.doGenerate(level, position, rand);
   }
}
