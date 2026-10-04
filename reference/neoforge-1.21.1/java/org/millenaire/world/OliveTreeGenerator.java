package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.OliveTreeLeavesBlock;

public final class OliveTreeGenerator extends AbstractTreeGenerator {
   private static final OliveTreeGenerator INSTANCE = new OliveTreeGenerator();

   private OliveTreeGenerator() {
   }

   protected Block getLogBlock() {
      return Blocks.ACACIA_LOG;
   }

   protected BlockState getLeavesState() {
      return (BlockState)((BlockState)((OliveTreeLeavesBlock)ModBlocks.OLIVE_TREE_LEAVES.get()).defaultBlockState().setValue(OliveTreeLeavesBlock.AGE, 0))
         .setValue(OliveTreeLeavesBlock.PERSISTENT, true);
   }

   public static boolean generate(ServerLevel level, BlockPos position, RandomSource rand) {
      return INSTANCE.doGenerate(level, position, rand);
   }
}
