package org.millenaire.test.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class TerrainSnapshot {
   private final int[][] originalSurfaceY;
   private final int originX;
   private final int originZ;
   private final int scanWidth;
   private final int scanDepth;

   private TerrainSnapshot(int[][] originalSurfaceY, int originX, int originZ, int scanWidth, int scanDepth) {
      this.originalSurfaceY = originalSurfaceY;
      this.originX = originX;
      this.originZ = originZ;
      this.scanWidth = scanWidth;
      this.scanDepth = scanDepth;
   }

   public static TerrainSnapshot capture(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      int startX = origin.getX() - margin;
      int startZ = origin.getZ() - margin;
      int scanW = width + 2 * margin;
      int scanD = depth + 2 * margin;
      int[][] surfaceY = new int[scanW][scanD];

      for (int lx = 0; lx < scanW; lx++) {
         for (int lz = 0; lz < scanD; lz++) {
            int wx = startX + lx;
            int wz = startZ + lz;
            surfaceY[lx][lz] = findSurfaceY(level, wx, wz);
         }
      }

      return new TerrainSnapshot(surfaceY, startX, startZ, scanW, scanD);
   }

   public int getOriginalSurfaceY(int worldX, int worldZ) {
      int lx = worldX - this.originX;
      int lz = worldZ - this.originZ;
      return lx >= 0 && lx < this.scanWidth && lz >= 0 && lz < this.scanDepth ? this.originalSurfaceY[lx][lz] : Integer.MIN_VALUE;
   }

   private static int findSurfaceY(ServerLevel level, int x, int z) {
      for (int y = 34; y >= -11; y--) {
         BlockState state = level.getBlockState(new BlockPos(x, y, z));
         if (!state.isAir()) {
            return y;
         }
      }

      return -11;
   }
}
