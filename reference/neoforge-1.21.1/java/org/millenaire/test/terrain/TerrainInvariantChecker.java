package org.millenaire.test.terrain;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class TerrainInvariantChecker {
   private static final int FOUNDATION_DEPTH = 10;

   private TerrainInvariantChecker() {
   }

   public static List<Violation> check(
      ServerLevel level, BlockPos origin, int width, int height, int depth, int baseY, TestTerrain terrain, TerrainSnapshot before, int groundLevel
   ) {
      List<Violation> violations = new ArrayList<>();
      checkInteriorClear(level, origin, width, height, depth, baseY, violations);
      checkFoundationContinuous(level, origin, width, depth, baseY, before, groundLevel, violations);
      checkNoWaterFootprint(level, origin, width, height, depth, baseY, violations);
      if (groundLevel < 0) {
         checkBuildingInteriorClear(level, origin, width, depth, baseY, groundLevel, violations);
      }

      if (terrain == TestTerrain.RIVER_EAST || terrain == TestTerrain.LAKE_ADJACENT) {
         checkDamPresent(level, origin, width, depth, baseY, terrain, violations);
         checkNoWaterNearFootprint(level, origin, width, depth, baseY, terrain, violations);
      }

      if (terrain == TestTerrain.FOREST) {
         checkCanopyIgnored(baseY, violations);
         checkLogsCleared(level, origin, width, depth, baseY, violations);
      }

      if (terrain == TestTerrain.STEP) {
         checkBaseYMidpoint(baseY, violations);
         checkLowSideRaised(level, origin, width, depth, baseY, before, violations);
         checkTransitionMonotonic(level, origin, width, depth, baseY, before, violations);
      }

      return violations;
   }

   private static void checkInteriorClear(ServerLevel level, BlockPos origin, int width, int height, int depth, int baseY, List<Violation> violations) {
      for (int x = origin.getX(); x < origin.getX() + width; x++) {
         for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
            for (int y = baseY; y < baseY + height; y++) {
               BlockPos pos = new BlockPos(x, y, z);
               BlockState state = level.getBlockState(pos);
               if (!state.isAir() && !isDecorativePlant(state)) {
                  violations.add(
                     new Violation(
                        "INTERIOR_CLEAR",
                        pos,
                        "air",
                        state.getBlock().toString(),
                        "footprint (%d,%d), Y=baseY+%d".formatted(x - origin.getX(), z - origin.getZ(), y - baseY)
                     )
                  );
               }
            }
         }
      }
   }

   private static void checkFoundationContinuous(
      ServerLevel level, BlockPos origin, int width, int depth, int baseY, TerrainSnapshot before, int groundLevel, List<Violation> violations
   ) {
      int foundationBottom = baseY - 10;
      int foundationTop = groundLevel < 0 ? baseY + groundLevel : baseY;

      for (int x = origin.getX(); x < origin.getX() + width; x++) {
         for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
            int originalY = before.getOriginalSurfaceY(x, z);

            for (int y = foundationBottom; y < foundationTop; y++) {
               if (originalY >= y) {
                  BlockPos pos = new BlockPos(x, y, z);
                  BlockState state = level.getBlockState(pos);
                  if (state.isAir() || state.is(Blocks.WATER)) {
                     violations.add(
                        new Violation(
                           "FOUNDATION_CONTINUOUS",
                           pos,
                           "solid (foundation)",
                           state.isAir() ? "air" : "water",
                           "footprint (%d,%d), Y=baseY%d".formatted(x - origin.getX(), z - origin.getZ(), y - baseY)
                        )
                     );
                  }
               }
            }
         }
      }
   }

   private static void checkNoWaterFootprint(ServerLevel level, BlockPos origin, int width, int height, int depth, int baseY, List<Violation> violations) {
      int bottom = baseY - 10;
      int top = baseY + height;

      for (int x = origin.getX(); x < origin.getX() + width; x++) {
         for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
            for (int y = bottom; y <= top; y++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (level.getBlockState(pos).is(Blocks.WATER)) {
                  violations.add(
                     new Violation(
                        "NO_WATER_FOOTPRINT",
                        pos,
                        "not water",
                        "water",
                        "footprint (%d,%d), Y=baseY%+d".formatted(x - origin.getX(), z - origin.getZ(), y - baseY)
                     )
                  );
               }
            }
         }
      }
   }

   private static void checkDamPresent(ServerLevel level, BlockPos origin, int width, int depth, int baseY, TestTerrain terrain, List<Violation> violations) {
      boolean found = false;
      if (terrain == TestTerrain.RIVER_EAST) {
         int checkX = origin.getX() + width;

         for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
            for (int y = baseY - 2; y <= baseY; y++) {
               BlockState state = level.getBlockState(new BlockPos(checkX, y, z));
               if (!state.isAir() && !state.is(Blocks.WATER)) {
                  found = true;
                  break;
               }
            }

            if (found) {
               break;
            }
         }
      } else if (terrain == TestTerrain.LAKE_ADJACENT) {
         int checkZ = origin.getZ() + depth;

         for (int x = origin.getX(); x < origin.getX() + width; x++) {
            for (int y = baseY - 2; y <= baseY; y++) {
               BlockState state = level.getBlockState(new BlockPos(x, y, checkZ));
               if (!state.isAir() && !state.is(Blocks.WATER)) {
                  found = true;
                  break;
               }
            }

            if (found) {
               break;
            }
         }
      }

      if (!found) {
         violations.add(new Violation("DAM_PRESENT", origin, "at least one dam block", "none found", "interface footprint/eau"));
      }
   }

   private static void checkNoWaterNearFootprint(
      ServerLevel level, BlockPos origin, int width, int depth, int baseY, TestTerrain terrain, List<Violation> violations
   ) {
      if (terrain == TestTerrain.RIVER_EAST) {
         for (int dx = 0; dx < 2; dx++) {
            int x = origin.getX() + width + dx;

            for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
               for (int y = baseY - 10; y <= baseY; y++) {
                  BlockPos pos = new BlockPos(x, y, z);
                  if (level.getBlockState(pos).is(Blocks.WATER)) {
                     violations.add(
                        new Violation("NO_WATER_NEAR_FOOTPRINT", pos, "not water", "water", "margin east +%d, Z=%d".formatted(dx, z - origin.getZ()))
                     );
                  }
               }
            }
         }
      } else if (terrain == TestTerrain.LAKE_ADJACENT) {
         for (int dz = 0; dz < 2; dz++) {
            int z = origin.getZ() + depth + dz;

            for (int x = origin.getX(); x < origin.getX() + width; x++) {
               for (int y = baseY - 10; y <= baseY; y++) {
                  BlockPos pos = new BlockPos(x, y, z);
                  if (level.getBlockState(pos).is(Blocks.WATER)) {
                     violations.add(
                        new Violation("NO_WATER_NEAR_FOOTPRINT", pos, "not water", "water", "margin south +%d, X=%d".formatted(dz, x - origin.getX()))
                     );
                  }
               }
            }
         }
      }
   }

   private static void checkCanopyIgnored(int baseY, List<Violation> violations) {
      if (baseY > 5) {
         violations.add(new Violation("CANOPY_IGNORED", new BlockPos(0, baseY, 0), "baseY <= 5", "baseY = " + baseY, "canopy inflated baseY calculation"));
      }
   }

   private static void checkLogsCleared(ServerLevel level, BlockPos origin, int width, int depth, int baseY, List<Violation> violations) {
      for (int x = origin.getX(); x < origin.getX() + width; x++) {
         for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
            for (int y = baseY - 10; y < baseY + 20; y++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                  violations.add(
                     new Violation(
                        "LOGS_CLEARED", pos, "not log", "log", "footprint (%d,%d), Y=baseY%+d".formatted(x - origin.getX(), z - origin.getZ(), y - baseY)
                     )
                  );
               }
            }
         }
      }
   }

   private static void checkBuildingInteriorClear(
      ServerLevel level, BlockPos origin, int width, int depth, int baseY, int groundLevel, List<Violation> violations
   ) {
      int floorY = baseY + groundLevel;
      int interiorBottom = floorY + 1;
      int interiorTop = baseY - 1;
      if (interiorBottom <= interiorTop) {
         for (int x = origin.getX(); x < origin.getX() + width; x++) {
            for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
               for (int y = interiorBottom; y <= interiorTop; y++) {
                  BlockPos pos = new BlockPos(x, y, z);
                  BlockState state = level.getBlockState(pos);
                  if (!state.isAir() && !isDecorativePlant(state)) {
                     violations.add(
                        new Violation(
                           "BUILDING_INTERIOR_CLEAR",
                           pos,
                           "air",
                           state.getBlock().toString(),
                           "above floor (gl=%d), Y=baseY%+d, footprint (%d,%d)".formatted(groundLevel, y - baseY, x - origin.getX(), z - origin.getZ())
                        )
                     );
                  }
               }
            }
         }
      }
   }

   private static void checkBaseYMidpoint(int baseY, List<Violation> violations) {
      int lowGroundHeight = 5;
      int highGroundHeight = 10;
      int expectedMid = (lowGroundHeight + highGroundHeight) / 2;
      if (baseY < expectedMid - 1 || baseY > expectedMid + 1) {
         violations.add(
            new Violation(
               "BASE_Y_MIDPOINT",
               new BlockPos(0, baseY, 0),
               "baseY in [%d, %d]".formatted(expectedMid - 1, expectedMid + 1),
               "baseY = " + baseY,
               "baseY should be mid-height between step levels %d and %d".formatted(lowGroundHeight, highGroundHeight)
            )
         );
      }
   }

   private static void checkLowSideRaised(
      ServerLevel level, BlockPos origin, int width, int depth, int baseY, TerrainSnapshot before, List<Violation> violations
   ) {
      for (int x = origin.getX(); x < origin.getX() + width; x++) {
         for (int z = origin.getZ(); z < origin.getZ() + depth; z++) {
            int originalY = before.getOriginalSurfaceY(x, z);
            if (originalY < baseY - 1) {
               for (int y = originalY + 1; y < baseY; y++) {
                  BlockPos pos = new BlockPos(x, y, z);
                  BlockState state = level.getBlockState(pos);
                  if (state.isAir() || state.is(Blocks.WATER)) {
                     violations.add(
                        new Violation(
                           "LOW_SIDE_RAISED",
                           pos,
                           "solid (fill)",
                           state.isAir() ? "air" : "water",
                           "footprint (%d,%d), Y=baseY%+d, original=%d".formatted(x - origin.getX(), z - origin.getZ(), y - baseY, originalY)
                        )
                     );
                  }
               }
            }
         }
      }
   }

   private static void checkTransitionMonotonic(
      ServerLevel level, BlockPos origin, int width, int depth, int baseY, TerrainSnapshot before, List<Violation> violations
   ) {
      int margin = 5;

      for (int x = origin.getX(); x < origin.getX() + width; x++) {
         int prevSurface = Integer.MAX_VALUE;

         for (int dz = 0; dz < margin; dz++) {
            int z = origin.getZ() - 1 - dz;
            int surfY = findCurrentSurfaceY(level, x, z);
            if (dz > 0 && surfY > prevSurface) {
               violations.add(
                  new Violation(
                     "TRANSITION_MONOTONIC",
                     new BlockPos(x, surfY, z),
                     "surface <= %d".formatted(prevSurface),
                     "surface = " + surfY,
                     "north margin dist=%d, non-monotonic (should decrease toward original)".formatted(dz + 1)
                  )
               );
            }

            prevSurface = surfY;
         }

         prevSurface = Integer.MIN_VALUE;

         for (int dz = 0; dz < margin; dz++) {
            int z = origin.getZ() + depth + dz;
            int surfY = findCurrentSurfaceY(level, x, z);
            if (dz > 0 && surfY < prevSurface) {
               violations.add(
                  new Violation(
                     "TRANSITION_MONOTONIC",
                     new BlockPos(x, surfY, z),
                     "surface >= %d".formatted(prevSurface),
                     "surface = " + surfY,
                     "south margin dist=%d, non-monotonic (should increase toward original)".formatted(dz + 1)
                  )
               );
            }

            prevSurface = surfY;
         }
      }
   }

   private static int findCurrentSurfaceY(ServerLevel level, int x, int z) {
      for (int y = 34; y >= -11; y--) {
         BlockState state = level.getBlockState(new BlockPos(x, y, z));
         if (!state.isAir()) {
            return y;
         }
      }

      return -11;
   }

   private static boolean isDecorativePlant(BlockState state) {
      return state.is(Blocks.SHORT_GRASS)
         || state.is(Blocks.TALL_GRASS)
         || state.is(Blocks.FERN)
         || state.is(Blocks.LARGE_FERN)
         || state.is(Blocks.POPPY)
         || state.is(Blocks.DANDELION)
         || state.is(Blocks.BLUE_ORCHID)
         || state.is(Blocks.ALLIUM)
         || state.is(Blocks.AZURE_BLUET)
         || state.is(Blocks.RED_TULIP)
         || state.is(Blocks.ORANGE_TULIP)
         || state.is(Blocks.WHITE_TULIP)
         || state.is(Blocks.PINK_TULIP)
         || state.is(Blocks.OXEYE_DAISY)
         || state.is(Blocks.CORNFLOWER)
         || state.is(Blocks.LILY_OF_THE_VALLEY)
         || state.is(Blocks.BROWN_MUSHROOM)
         || state.is(Blocks.RED_MUSHROOM)
         || state.is(Blocks.OAK_SAPLING)
         || state.is(Blocks.SPRUCE_SAPLING)
         || state.is(Blocks.BIRCH_SAPLING)
         || state.is(Blocks.JUNGLE_SAPLING)
         || state.is(Blocks.ACACIA_SAPLING)
         || state.is(Blocks.DARK_OAK_SAPLING)
         || state.is(Blocks.DEAD_BUSH);
   }
}
