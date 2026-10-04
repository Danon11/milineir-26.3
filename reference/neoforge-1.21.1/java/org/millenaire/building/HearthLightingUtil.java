package org.millenaire.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.millenaire.tag.ModTags;

public final class HearthLightingUtil {
   private HearthLightingUtil() {
   }

   public static void lightHearthsInArea(ServerLevel level, BlockPos origin, Vec3i size) {
      if (level != null && origin != null && size != null) {
         int sx = size.getX();
         int sy = size.getY();
         int sz = size.getZ();
         if (sx > 0 && sy > 0 && sz > 0) {
            MutableBlockPos pos = new MutableBlockPos();

            for (int x = 0; x < sx; x++) {
               for (int z = 0; z < sz; z++) {
                  for (int y = 0; y < sy; y++) {
                     pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                     BlockState state = level.getBlockState(pos);
                     if (state.is(ModTags.Blocks.HEARTH_BLOCKS)
                        && state.hasProperty(BlockStateProperties.LIT)
                        && !(Boolean)state.getValue(BlockStateProperties.LIT)) {
                        level.setBlock(pos, (BlockState)state.setValue(BlockStateProperties.LIT, true), 2);
                     }
                  }
               }
            }
         }
      }
   }
}
