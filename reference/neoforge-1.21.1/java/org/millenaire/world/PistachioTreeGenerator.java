package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.PistachioTreeLeavesBlock;

public final class PistachioTreeGenerator extends AbstractTreeGenerator {
   private static final PistachioTreeGenerator INSTANCE = new PistachioTreeGenerator();

   private PistachioTreeGenerator() {
   }

   protected Block getLogBlock() {
      return Blocks.ACACIA_LOG;
   }

   protected BlockState getLeavesState() {
      return (BlockState)((BlockState)((PistachioTreeLeavesBlock)ModBlocks.PISTACHIO_TREE_LEAVES.get())
            .defaultBlockState()
            .setValue(PistachioTreeLeavesBlock.AGE, 0))
         .setValue(PistachioTreeLeavesBlock.PERSISTENT, true);
   }

   public static boolean generate(ServerLevel level, BlockPos position, RandomSource rand) {
      return INSTANCE.doGenerate(level, position, rand);
   }
}
