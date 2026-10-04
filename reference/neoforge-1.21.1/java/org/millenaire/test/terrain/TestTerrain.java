package org.millenaire.test.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public enum TestTerrain {
   FLAT(TestTerrain::buildFlat),
   HILL_NORTH(TestTerrain::buildHillNorth),
   VALLEY(TestTerrain::buildValley),
   RIVER_EAST(TestTerrain::buildRiverEast),
   CLIFF_WEST(TestTerrain::buildCliffWest),
   FOREST(TestTerrain::buildForest),
   LAKE_ADJACENT(TestTerrain::buildLakeAdjacent),
   STEP(TestTerrain::buildStep);

   public static final int BASE_FLAT_Y = 4;
   static final int STEP_HEIGHT = 5;
   private final TestTerrain.TerrainBuilder builder;

   TestTerrain(TestTerrain.TerrainBuilder builder) {
      this.builder = builder;
   }

   public void build(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      this.builder.build(level, origin, width, depth, margin);
   }

   public boolean hasWater() {
      return this == RIVER_EAST || this == LAKE_ADJACENT;
   }

   private static void buildBase(ServerLevel level, BlockPos origin, int width, int depth, int margin, int extraMargin) {
      int startX = origin.getX() - margin - extraMargin;
      int endX = origin.getX() + width + margin + extraMargin;
      int startZ = origin.getZ() - margin - extraMargin;
      int endZ = origin.getZ() + depth + margin + extraMargin;

      for (int x = startX; x < endX; x++) {
         for (int z = startZ; z < endZ; z++) {
            for (int y = 5; y <= 24; y++) {
               level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }

            for (int y = -8; y <= 2; y++) {
               level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            }

            level.setBlock(new BlockPos(x, 3, z), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, 4, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
         }
      }
   }

   private static void buildFlat(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      buildBase(level, origin, width, depth, margin, 3);
   }

   private static void buildHillNorth(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      buildBase(level, origin, width, depth, margin, 3);
      int startX = origin.getX() - margin - 3;
      int endX = origin.getX() + width + margin + 3;
      int midZ = origin.getZ() + depth / 2;
      int maxZ = origin.getZ() + depth + margin + 3;

      for (int x = startX; x < endX; x++) {
         for (int z = midZ; z < maxZ; z++) {
            int extraHeight = (z - midZ) * 6 / (maxZ - midZ);
            if (extraHeight > 0) {
               for (int dy = 1; dy <= extraHeight; dy++) {
                  level.setBlock(new BlockPos(x, 4 + dy, z), dy == extraHeight ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 3);
               }
            }
         }
      }
   }

   private static void buildValley(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      buildBase(level, origin, width, depth, margin, 3);
      int centerX = origin.getX() + width / 2;
      int centerZ = origin.getZ() + depth / 2;
      int maxRadius = Math.min(width, depth) / 2;

      for (int x = origin.getX(); x < origin.getX() + width; x++) {
         for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
            int dist = Math.max(Math.abs(x - centerX), Math.abs(z - centerZ));
            int valleyDepth = maxRadius > 0 ? 4 * (maxRadius - dist) / maxRadius : 4;
            if (valleyDepth > 0) {
               for (int dy = 0; dy >= -valleyDepth + 1; dy--) {
                  level.setBlock(new BlockPos(x, 4 + dy, z), Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
      }
   }

   private static void buildRiverEast(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      buildBase(level, origin, width, depth, margin, 3);
      int riverStartX = origin.getX() + width + 2;
      int startZ = origin.getZ() - margin - 3;
      int endZ = origin.getZ() + depth + margin + 3;

      for (int x = riverStartX; x < riverStartX + 3; x++) {
         for (int z = startZ; z < endZ; z++) {
            level.setBlock(new BlockPos(x, 4, z), Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, 3, z), Blocks.WATER.defaultBlockState(), 3);
         }
      }
   }

   private static void buildCliffWest(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      buildBase(level, origin, width, depth, margin, 3);
      int startZ = origin.getZ() - margin - 3;
      int endZ = origin.getZ() + depth + margin + 3;
      int cliffEndX = origin.getX() - 1;
      int cliffStartX = origin.getX() - margin - 3;

      for (int x = cliffStartX; x <= cliffEndX; x++) {
         for (int z = startZ; z < endZ; z++) {
            for (int dy = 1; dy <= 10; dy++) {
               level.setBlock(new BlockPos(x, 4 + dy, z), dy == 10 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 3);
            }
         }
      }
   }

   private static void buildForest(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      buildBase(level, origin, width, depth, margin, 3);
      int[][] treePositions = new int[][]{
         {origin.getX() + width / 4, origin.getZ() + depth / 4},
         {origin.getX() + 3 * width / 4, origin.getZ() + depth / 4},
         {origin.getX() + width / 4, origin.getZ() + 3 * depth / 4},
         {origin.getX() + 3 * width / 4, origin.getZ() + 3 * depth / 4}
      };

      for (int[] pos : treePositions) {
         int tx = pos[0];
         int tz = pos[1];
         int trunkHeight = 5;

         for (int dy = 1; dy <= trunkHeight; dy++) {
            level.setBlock(new BlockPos(tx, 4 + dy, tz), Blocks.OAK_LOG.defaultBlockState(), 3);
         }

         for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
               for (int ly = 0; ly <= 1; ly++) {
                  BlockPos leafPos = new BlockPos(tx + lx, 4 + trunkHeight + ly, tz + lz);
                  if (level.getBlockState(leafPos).isAir()) {
                     level.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState(), 3);
                  }
               }
            }
         }
      }
   }

   private static void buildStep(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      buildBase(level, origin, width, depth, margin, 3);
      int midZ = origin.getZ() + depth / 2;
      int startX = origin.getX() - margin - 3;
      int endX = origin.getX() + width + margin + 3;
      int endZ = origin.getZ() + depth + margin + 3;

      for (int x = startX; x < endX; x++) {
         for (int z = midZ; z < endZ; z++) {
            for (int dy = 1; dy <= 5; dy++) {
               level.setBlock(new BlockPos(x, 4 + dy, z), dy == 5 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 3);
            }
         }
      }
   }

   private static void buildLakeAdjacent(ServerLevel level, BlockPos origin, int width, int depth, int margin) {
      buildBase(level, origin, width, depth, margin, 3);
      int lakeStartZ = origin.getZ() + depth + 1;
      int lakeStartX = origin.getX() - 1;

      for (int x = lakeStartX; x < lakeStartX + 8; x++) {
         for (int z = lakeStartZ; z < lakeStartZ + 8; z++) {
            for (int dy = 0; dy >= -2; dy--) {
               level.setBlock(new BlockPos(x, 4 + dy, z), Blocks.WATER.defaultBlockState(), 3);
            }
         }
      }
   }

   @FunctionalInterface
   interface TerrainBuilder {
      void build(ServerLevel var1, BlockPos var2, int var3, int var4, int var5);
   }
}
