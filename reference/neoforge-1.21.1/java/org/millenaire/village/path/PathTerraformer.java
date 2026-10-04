package org.millenaire.village.path;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.millenaire.block.MillPathBlock;
import org.millenaire.block.MillPathSlabBlock;

public final class PathTerraformer {
   private PathTerraformer() {
   }

   public static List<PathEntry> blocksForColumn(
      PathTerraformer.ColumnDecision decision,
      Block fullBlock,
      @Nullable SlabBlock slabBlock,
      BlockState foundationBlock,
      BlockState airBlock,
      IntUnaryOperator fillPermitted,
      IntUnaryOperator cutPermitted
   ) {
      List<PathEntry> out = new ArrayList<>(4);
      boolean slab = (decision.surfaceHalfY & 1) == 1;
      int placeY = slab ? (decision.surfaceHalfY - 1) / 2 : decision.surfaceHalfY / 2 - 1;
      BlockState pathState;
      if (slab && slabBlock != null) {
         pathState = (BlockState)((BlockState)slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM))
            .setValue(MillPathSlabBlock.STABLE, false);
      } else {
         pathState = (BlockState)fullBlock.defaultBlockState().setValue(MillPathBlock.STABLE, false);
         if (slab) {
            placeY--;
         }
      }

      for (int fy = decision.groundSurfaceY; fy < placeY; fy++) {
         if (fillPermitted.applyAsInt(fy) == 1) {
            out.add(new PathEntry(new BlockPos(decision.x, fy, decision.z), foundationBlock));
         }
      }

      out.add(new PathEntry(new BlockPos(decision.x, placeY, decision.z), pathState));

      for (int cy = placeY + 1; cy <= placeY + 2; cy++) {
         if (cutPermitted.applyAsInt(cy) == 1) {
            out.add(new PathEntry(new BlockPos(decision.x, cy, decision.z), airBlock));
         }
      }

      return out;
   }

   public record ColumnDecision(int x, int z, int surfaceHalfY, int groundSurfaceY) {
   }
}
