package org.millenaire.test.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class TerrainAsciiRenderer {
   private TerrainAsciiRenderer() {
   }

   public static char classify(BlockState state) {
      if (state.isAir()) {
         return '.';
      } else if (state.is(Blocks.GRASS_BLOCK)) {
         return 'G';
      } else if (state.is(Blocks.DIRT)) {
         return 'D';
      } else if (state.is(Blocks.STONE)
         || state.is(Blocks.GRANITE)
         || state.is(Blocks.DIORITE)
         || state.is(Blocks.ANDESITE)
         || state.is(Blocks.DEEPSLATE)
         || state.is(Blocks.SANDSTONE)) {
         return 'S';
      } else if (state.is(Blocks.WATER)) {
         return 'W';
      } else if (state.is(BlockTags.LOGS)) {
         return 'L';
      } else if (state.is(BlockTags.LEAVES)) {
         return 'F';
      } else if (state.is(Blocks.SAND)) {
         return '~';
      } else if (state.is(Blocks.PODZOL)) {
         return 'P';
      } else {
         return (char)(state.is(Blocks.GRAVEL) ? 'v' : '?');
      }
   }

   public static String renderCrossSection(
      ServerLevel level, BlockPos origin, int width, int depth, int baseY, int margin, Direction sliceAxis, int sliceOffset
   ) {
      boolean alongZ = sliceAxis == Direction.NORTH;
      int mainSize = alongZ ? depth + 2 * margin : width + 2 * margin;
      int mainStart = alongZ ? origin.getZ() - margin : origin.getX() - margin;
      int footprintStart = alongZ ? 0 : 0;
      int footprintEnd = alongZ ? depth : width;
      int fixedCoord;
      if (alongZ) {
         fixedCoord = origin.getX() + sliceOffset;
         int fixedFootprintStart = origin.getX();
         int fixedFootprintEnd = origin.getX() + width;
      } else {
         fixedCoord = origin.getZ() + sliceOffset;
         int fixedFootprintStart = origin.getZ();
         int fixedFootprintEnd = origin.getZ() + depth;
      }

      int yTop = baseY + 8;
      int yBot = baseY - 12;
      StringBuilder sb = new StringBuilder();
      String axisName = alongZ ? "Z" : "X";
      String perpName = alongZ ? "X" : "Z";
      sb.append("=== Cross-section ")
         .append(axisName)
         .append(" (at ")
         .append(perpName)
         .append("=")
         .append(fixedCoord)
         .append(", offset=")
         .append(sliceOffset)
         .append(") ===\n");
      sb.append("      ");

      for (int i = 0; i < mainSize; i++) {
         int absCoord = mainStart + i;
         int relCoord = alongZ ? absCoord - origin.getZ() : absCoord - origin.getX();
         sb.append(String.format("%4d", relCoord));
      }

      sb.append("\n");

      for (int y = yTop; y >= yBot; y--) {
         int relY = y - baseY;
         String yLabel;
         if (relY > 0) {
            yLabel = String.format("Y+%-2d", relY);
         } else if (relY == 0) {
            yLabel = "Y=0 ";
         } else {
            yLabel = String.format("Y%-3d", relY);
         }

         sb.append(yLabel).append(" |");

         for (int i = 0; i < mainSize; i++) {
            int absMain = mainStart + i;
            int relMain = alongZ ? absMain - origin.getZ() : absMain - origin.getX();
            if (relMain >= 0 && relMain < footprintEnd) {
               boolean inFootprint = true;
            } else {
               boolean inFootprint = false;
            }

            BlockPos pos;
            if (alongZ) {
               pos = new BlockPos(fixedCoord, y, absMain);
            } else {
               pos = new BlockPos(absMain, y, fixedCoord);
            }

            BlockState state = level.getBlockState(pos);
            char c = classify(state);
            boolean isStartBoundary = relMain == 0;
            boolean isEndBoundary = relMain == footprintEnd - 1;
            if (isStartBoundary) {
               sb.append(" [").append(c);
            } else if (isEndBoundary) {
               sb.append("   ").append(c).append("]");
            } else {
               sb.append("   ").append(c);
            }
         }

         if (y == baseY) {
            sb.append("   ← baseY");
         }

         sb.append("\n");
      }

      return sb.toString();
   }

   public static String renderSurfaceHeightmap(ServerLevel level, BlockPos origin, int width, int depth, int baseY, int margin) {
      int startX = origin.getX() - margin;
      int endX = origin.getX() + width + margin;
      int startZ = origin.getZ() - margin;
      int endZ = origin.getZ() + depth + margin;
      int colCount = endX - startX;
      StringBuilder sb = new StringBuilder();
      sb.append("=== Surface heightmap (relative to baseY=").append(baseY).append(") ===\n");
      sb.append("       ");

      for (int x = startX; x < endX; x++) {
         int relX = x - origin.getX();
         sb.append(String.format("%4d", relX));
      }

      sb.append("\n");

      for (int z = endZ - 1; z >= startZ; z--) {
         int relZ = z - origin.getZ();
         sb.append(String.format("%4d |", relZ));

         for (int x = startX; x < endX; x++) {
            String cell = "_   ";

            for (int y = baseY + 20; y >= baseY - 15; y--) {
               BlockState state = level.getBlockState(new BlockPos(x, y, z));
               if (state.is(Blocks.WATER)) {
                  cell = " W  ";
                  break;
               }

               if (!state.isAir()) {
                  int relY = y - baseY;
                  if (relY >= 0) {
                     cell = String.format("+%-3d", relY);
                  } else {
                     cell = String.format("%-4d", relY);
                  }
                  break;
               }
            }

            sb.append(cell);
         }

         sb.append("\n");
      }

      return sb.toString();
   }
}
