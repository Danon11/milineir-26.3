package org.millenaire.world;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.ClearMargins;

public class TerrainReachability {
   private static final int[][] DIRS = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
   private final VillageTerrainMap map;
   private final int[][] regions;
   private final int thRegion;
   private static final float MIN_REACHABLE_RATIO = 0.7F;

   private TerrainReachability(VillageTerrainMap map, int[][] regions, int thRegion) {
      this.map = map;
      this.regions = regions;
      this.thRegion = thRegion;
   }

   public static TerrainReachability compute(VillageTerrainMap map, BlockPos townHallWorldPos) {
      int size = map.getSize();
      int[][] regions = new int[size][size];
      int nextRegion = 0;

      for (int lx = 0; lx < size; lx++) {
         for (int lz = 0; lz < size; lz++) {
            if (regions[lx][lz] == 0 && isCellPassable(map, lx, lz)) {
               floodFill(map, regions, lx, lz, ++nextRegion, size);
            }
         }
      }

      int thLx = map.toLocalX(townHallWorldPos.getX());
      int thLz = map.toLocalZ(townHallWorldPos.getZ());
      int thReg = map.inBounds(thLx, thLz) ? regions[thLx][thLz] : 0;
      return new TerrainReachability(map, regions, thReg);
   }

   private static boolean isCellPassable(VillageTerrainMap map, int lx, int lz) {
      return !map.isDangerAt(lx, lz) && !map.isWaterAt(lx, lz) && map.getSpaceAbove(lx, lz) > 1;
   }

   private static boolean isConnected(VillageTerrainMap map, int cx, int cz, int nx, int nz) {
      if (!map.inBounds(nx, nz)) {
         return false;
      } else if (!isCellPassable(map, nx, nz)) {
         return false;
      } else {
         int curY = map.getTopGround(cx, cz);
         int nbrY = map.getTopGround(nx, nz);
         int dy = nbrY - curY;
         int curSpace = map.getSpaceAbove(cx, cz);
         int nbrSpace = map.getSpaceAbove(nx, nz);
         if (dy == 0) {
            return true;
         } else if (dy == -1) {
            return nbrSpace > 2;
         } else {
            return dy == 1 ? curSpace > 2 : false;
         }
      }
   }

   private static void floodFill(VillageTerrainMap map, int[][] regions, int startLx, int startLz, int regionId, int size) {
      Queue<int[]> queue = new ArrayDeque<>();
      queue.add(new int[]{startLx, startLz});
      regions[startLx][startLz] = regionId;

      while (!queue.isEmpty()) {
         int[] cell = queue.poll();
         int cx = cell[0];
         int cz = cell[1];

         for (int[] d : DIRS) {
            int nx = cx + d[0];
            int nz = cz + d[1];
            if (nx >= 0 && nx < size && nz >= 0 && nz < size && regions[nx][nz] == 0 && isConnected(map, cx, cz, nx, nz)) {
               regions[nx][nz] = regionId;
               queue.add(new int[]{nx, nz});
            }
         }
      }
   }

   public boolean isReachable(int worldX, int worldZ) {
      int lx = this.map.toLocalX(worldX);
      int lz = this.map.toLocalZ(worldZ);
      if (!this.map.inBounds(lx, lz)) {
         return false;
      } else {
         return this.thRegion == 0 ? false : this.regions[lx][lz] == this.thRegion;
      }
   }

   public boolean isFootprintReachable(int worldX, int worldZ, int width, int depth, Rotation rotation) {
      VillageTerrainMap.FootprintRect rect = VillageTerrainMap.computeFootprintRect(worldX, worldZ, width, depth, ClearMargins.symmetric(0), rotation);
      int total = 0;
      int reachable = 0;

      for (int dx = 0; dx < rect.width(); dx++) {
         for (int dz = 0; dz < rect.depth(); dz++) {
            total++;
            if (this.isReachable(rect.startX() + dx, rect.startZ() + dz)) {
               reachable++;
            }
         }
      }

      return total == 0 ? false : (float)reachable / total >= 0.7F;
   }
}
