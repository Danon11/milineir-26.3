package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.ClearMargins;

public final class TerrainTraversal {
   public static final int CLEAR_ABOVE_MARGIN = 50;
   public static final int FOUNDATION_DEPTH = 10;

   private TerrainTraversal() {
   }

   public static int computeOffsetComponent(int worldCoord, int originCoord, int size) {
      if (worldCoord < originCoord) {
         return originCoord - worldCoord;
      } else {
         return worldCoord >= originCoord + size ? worldCoord - (originCoord + size) + 1 : 0;
      }
   }

   public static boolean isInsideFootprint(int wx, int wz, BlockPos origin, int width, int depth) {
      return wx >= origin.getX() && wx < origin.getX() + width && wz >= origin.getZ() && wz < origin.getZ() + depth;
   }

   public static void traverse(
      ServerLevel level,
      BlockPos effOrigin,
      int effWidth,
      int effDepth,
      int height,
      int baseY,
      int groundLevel,
      ClearMargins effectiveMargins,
      TerrainTraversal.BlockVisitor visitor
   ) {
      int startX = effOrigin.getX() - effectiveMargins.lengthBefore();
      int endX = effOrigin.getX() + effWidth + effectiveMargins.lengthAfter();
      int startZ = effOrigin.getZ() - effectiveMargins.widthBefore();
      int endZ = effOrigin.getZ() + effDepth + effectiveMargins.widthAfter();
      int gridW = endX - startX;
      int gridD = endZ - startZ;
      BlockState[][] surfaceBlocks = new BlockState[gridW][gridD];
      BlockState[][] subSurfaceBlocks = new BlockState[gridW][gridD];

      for (int ix = 0; ix < gridW; ix++) {
         for (int iz = 0; iz < gridD; iz++) {
            surfaceBlocks[ix][iz] = TerrainPreparer.getSurfaceBlock(level, startX + ix, startZ + iz);
            subSurfaceBlocks[ix][iz] = TerrainPreparer.getSubSurfaceBlock(level, startX + ix, startZ + iz);
         }
      }

      for (int wx = startX; wx < endX; wx++) {
         for (int wz = startZ; wz < endZ; wz++) {
            int offsetX = computeOffsetComponent(wx, effOrigin.getX(), effWidth);
            int offsetZ = computeOffsetComponent(wz, effOrigin.getZ(), effDepth);
            int offset = Math.max(offsetX, offsetZ);
            if (Math.abs(offsetX - offsetZ) < 3) {
               offset++;
            }

            offset--;
            boolean isInside = isInsideFootprint(wx, wz, effOrigin, effWidth, effDepth);
            boolean isSideBorder = wx == startX || wx == endX - 1 || wz == startZ || wz == endZ - 1;
            BlockState surfaceBlock = surfaceBlocks[wx - startX][wz - startZ];
            BlockState subSurfaceBlock = subSurfaceBlocks[wx - startX][wz - startZ];
            int topDeltaY = height + 50;

            for (int deltaY = topDeltaY; deltaY >= 0; deltaY--) {
               int y = baseY + deltaY;
               BlockPos pos = new BlockPos(wx, y, wz);
               BlockState existing = level.getBlockState(pos);
               if (!existing.isAir()) {
                  TerrainTraversal.BlockContext ctx = new TerrainTraversal.BlockContext(wx, wz, y, existing, isInside, surfaceBlock, subSurfaceBlock);
                  if (deltaY >= offset - 2) {
                     if (!isInside) {
                        boolean isBorder = deltaY == offset - 2 || deltaY == 0 || isSideBorder;
                        if (isBorder) {
                           if (!TerrainPreparer.isDecorativePlant(existing) && !TerrainPreparer.isLeaves(existing)) {
                              if (TerrainPreparer.isAdjacentToWater(level, pos)) {
                                 visitor.visit(ctx, TerrainTraversal.Action.BORDER_ANTIFLOOD);
                              } else {
                                 visitor.visit(ctx, TerrainTraversal.Action.CLEAR_AIR);
                              }
                           }
                        } else if (!TerrainPreparer.isDecorativePlant(existing) && !TerrainPreparer.isLeaves(existing)) {
                           visitor.visit(ctx, TerrainTraversal.Action.CLEAR_AIR);
                        }
                     } else if (!TerrainPreparer.isDecorativePlant(existing)) {
                        visitor.visit(ctx, TerrainTraversal.Action.CLEAR_AIR);
                     }
                  } else if (TerrainPreparer.isLog(existing) || TerrainPreparer.isHugeMushroomBlock(existing)) {
                     visitor.visit(ctx, TerrainTraversal.Action.CLEAR_TREE);
                  }
               }
            }

            if (groundLevel < 0 && isInside) {
               int clearBottom = baseY + groundLevel + 1;

               for (int y = baseY - 1; y >= clearBottom; y--) {
                  BlockPos pos = new BlockPos(wx, y, wz);
                  BlockState existing = level.getBlockState(pos);
                  if (!existing.isAir()) {
                     TerrainTraversal.BlockContext ctx = new TerrainTraversal.BlockContext(wx, wz, y, existing, true, surfaceBlock, subSurfaceBlock);
                     visitor.visit(ctx, TerrainTraversal.Action.CLEAR_SUBGROUND);
                  }
               }
            }
         }
      }

      int foundationBottom = baseY - 10;
      int insideFoundationTop = baseY + Math.min(groundLevel, 0);

      for (int wx = startX; wx < endX; wx++) {
         for (int wz = startZ; wz < endZ; wz++) {
            int offsetX = computeOffsetComponent(wx, effOrigin.getX(), effWidth);
            int offsetZ = computeOffsetComponent(wz, effOrigin.getZ(), effDepth);
            int offset = Math.max(offsetX, offsetZ);
            if (Math.abs(offsetX - offsetZ) < 3) {
               offset++;
            }

            offset--;
            boolean isInside = isInsideFootprint(wx, wz, effOrigin, effWidth, effDepth);
            int columnTop = isInside ? insideFoundationTop : baseY;
            BlockState surfaceBlock = surfaceBlocks[wx - startX][wz - startZ];
            BlockState subSurfaceBlock = subSurfaceBlocks[wx - startX][wz - startZ];

            for (int y = foundationBottom; y < columnTop; y++) {
               int depthBelowBase = baseY - y;
               BlockPos pos = new BlockPos(wx, y, wz);
               BlockState existing = level.getBlockState(pos);
               TerrainTraversal.BlockContext ctx = new TerrainTraversal.BlockContext(wx, wz, y, existing, isInside, surfaceBlock, subSurfaceBlock);
               if (depthBelowBase > offset) {
                  if (TerrainPreparer.isPreservedFoundationBlock(existing)) {
                     if (groundLevel < 0 && isInside && TerrainPreparer.isFallingBlock(existing)) {
                        visitor.visit(ctx, TerrainTraversal.Action.STABILIZE_FALLING);
                     }
                  } else if (existing.isAir()
                     || !existing.getFluidState().isEmpty()
                     || TerrainPreparer.isLeaves(existing)
                     || TerrainPreparer.isLog(existing)
                     || TerrainPreparer.isHugeMushroomBlock(existing)
                     || !existing.canOcclude()) {
                     visitor.visit(ctx, TerrainTraversal.Action.FILL_SUBSURFACE);
                  }
               } else if (depthBelowBase >= offset - 1) {
                  if (TerrainPreparer.isPreservedFoundationBlock(existing)) {
                     if (groundLevel < 0 && isInside && TerrainPreparer.isFallingBlock(existing)) {
                        visitor.visit(ctx, TerrainTraversal.Action.STABILIZE_FALLING);
                     }
                  } else if (existing.isAir()
                     || !existing.getFluidState().isEmpty()
                     || TerrainPreparer.isLeaves(existing)
                     || TerrainPreparer.isLog(existing)
                     || TerrainPreparer.isHugeMushroomBlock(existing)
                     || !existing.canOcclude()) {
                     visitor.visit(ctx, TerrainTraversal.Action.FILL_SURFACE);
                  }
               } else if (TerrainPreparer.isLog(existing) || TerrainPreparer.isHugeMushroomBlock(existing)) {
                  visitor.visit(ctx, TerrainTraversal.Action.CLEAR_TREE);
               }
            }
         }
      }
   }

   public enum Action {
      CLEAR_AIR,
      CLEAR_TREE,
      FILL_SUBSURFACE,
      FILL_SURFACE,
      BORDER_ANTIFLOOD,
      CLEAR_SUBGROUND,
      STABILIZE_FALLING;
   }

   public record BlockContext(int wx, int wz, int y, BlockState existing, boolean isInsideBuilding, BlockState surfaceBlock, BlockState subSurfaceBlock) {
   }

   @FunctionalInterface
   public interface BlockVisitor {
      void visit(TerrainTraversal.BlockContext var1, TerrainTraversal.Action var2);
   }
}
