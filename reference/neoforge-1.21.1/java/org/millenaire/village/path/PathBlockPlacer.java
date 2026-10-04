package org.millenaire.village.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class PathBlockPlacer {
   private static final BlockState DEFAULT_FOUNDATION = Blocks.DIRT.defaultBlockState();

   private PathBlockPlacer() {
   }

   public static boolean[] computeBuildMask(List<BlockPos> trace, Predicate<BlockPos> isProtectedFromPath) {
      return computeBuildMask(trace, isProtectedFromPath, pos -> false);
   }

   public static boolean[] computeBuildMask(List<BlockPos> trace, Predicate<BlockPos> isProtectedFromPath, Predicate<BlockPos> isHardProtected) {
      boolean[] shouldBuild = new boolean[trace.size()];
      Arrays.fill(shouldBuild, true);
      if (trace.size() <= 2) {
         for (int k = 0; k < trace.size(); k++) {
            if (isHardProtected.test(trace.get(k))) {
               shouldBuild[k] = false;
            }
         }

         return shouldBuild;
      } else {
         int i = 0;

         while (i < trace.size()) {
            if (!isProtectedFromPath.test(trace.get(i))) {
               i++;
            } else {
               int runStart = i;

               while (i < trace.size() && isProtectedFromPath.test(trace.get(i))) {
                  i++;
               }

               int runEnd = i;
               boolean hasEntryBefore = runStart == 0 || !isProtectedFromPath.test(trace.get(runStart - 1));
               boolean hasExitAfter = runEnd == trace.size() || !isProtectedFromPath.test(trace.get(runEnd));
               boolean isTraversal = hasEntryBefore && hasExitAfter && runStart > 0 && runEnd < trace.size();
               if (!isTraversal) {
                  for (int j = runStart; j < runEnd; j++) {
                     if (j > 0 && j < trace.size() - 1) {
                        shouldBuild[j] = false;
                     }
                  }
               }
            }
         }

         for (int k = 0; k < trace.size(); k++) {
            if (isHardProtected.test(trace.get(k))) {
               shouldBuild[k] = false;
            }
         }

         return shouldBuild;
      }
   }

   public static List<PathBlockPlacer.WidenedColumn> computeWidenedColumns(
      List<BlockPos> centreTrace, int[] surfaceHalfY, int pathWidth, Predicate<BlockPos> canPlaceAt
   ) {
      Map<Long, PathBlockPlacer.WidenedColumn> byXZ = new LinkedHashMap<>();

      for (int i = 0; i < centreTrace.size(); i++) {
         BlockPos node = centreTrace.get(i);
         int halfY = surfaceHalfY[i];
         addIfFree(byXZ, node.getX(), node.getZ(), halfY);
         boolean corner = isCorner(centreTrace, i);
         if (corner) {
            BlockPos prev = i > 0 ? centreTrace.get(i - 1) : node;
            BlockPos next = i < centreTrace.size() - 1 ? centreTrace.get(i + 1) : node;
            int dx1 = Integer.signum(node.getX() - prev.getX());
            int dz1 = Integer.signum(node.getZ() - prev.getZ());
            int dx2 = Integer.signum(next.getX() - node.getX());
            int dz2 = Integer.signum(next.getZ() - node.getZ());
            addPerpendicular(byXZ, node, halfY, dx1, dz1, canPlaceAt);
            addPerpendicular(byXZ, node, halfY, dx2, dz2, canPlaceAt);
         } else if (pathWidth > 1) {
            BlockPos prev = i > 0 ? centreTrace.get(i - 1) : node;
            BlockPos next = i < centreTrace.size() - 1 ? centreTrace.get(i + 1) : node;
            int dx = Integer.signum(next.getX() - prev.getX());
            int dz = Integer.signum(next.getZ() - prev.getZ());
            if (dx != 0) {
               trySide(byXZ, node, halfY, 0, 1, canPlaceAt);
            } else if (dz != 0) {
               trySide(byXZ, node, halfY, 1, 0, canPlaceAt);
            }
         }
      }

      return new ArrayList<>(byXZ.values());
   }

   private static void trySide(Map<Long, PathBlockPlacer.WidenedColumn> out, BlockPos node, int halfY, int ax, int az, Predicate<BlockPos> canPlaceAt) {
      BlockPos preferred = node.offset(ax, 0, az);
      BlockPos alternate = node.offset(-ax, 0, -az);
      if (canPlaceAt.test(preferred)) {
         addIfFree(out, preferred.getX(), preferred.getZ(), halfY);
      } else if (canPlaceAt.test(alternate)) {
         addIfFree(out, alternate.getX(), alternate.getZ(), halfY);
      }
   }

   private static void addPerpendicular(Map<Long, PathBlockPlacer.WidenedColumn> out, BlockPos node, int halfY, int dx, int dz, Predicate<BlockPos> canPlaceAt) {
      if (dx != 0) {
         BlockPos p1 = node.offset(0, 0, 1);
         BlockPos p2 = node.offset(0, 0, -1);
         if (canPlaceAt.test(p1)) {
            addIfFree(out, p1.getX(), p1.getZ(), halfY);
         }

         if (canPlaceAt.test(p2)) {
            addIfFree(out, p2.getX(), p2.getZ(), halfY);
         }
      }

      if (dz != 0) {
         BlockPos p1 = node.offset(1, 0, 0);
         BlockPos p2 = node.offset(-1, 0, 0);
         if (canPlaceAt.test(p1)) {
            addIfFree(out, p1.getX(), p1.getZ(), halfY);
         }

         if (canPlaceAt.test(p2)) {
            addIfFree(out, p2.getX(), p2.getZ(), halfY);
         }
      }
   }

   private static void addIfFree(Map<Long, PathBlockPlacer.WidenedColumn> out, int x, int z, int halfY) {
      long key = (long)x << 32 | z & 4294967295L;
      out.putIfAbsent(key, new PathBlockPlacer.WidenedColumn(x, z, halfY));
   }

   private static boolean isCorner(List<BlockPos> trace, int i) {
      if (i > 0 && i < trace.size() - 1) {
         BlockPos prev = trace.get(i - 1);
         BlockPos node = trace.get(i);
         BlockPos next = trace.get(i + 1);
         int dx1 = Integer.signum(node.getX() - prev.getX());
         int dz1 = Integer.signum(node.getZ() - prev.getZ());
         int dx2 = Integer.signum(next.getX() - node.getX());
         int dz2 = Integer.signum(next.getZ() - node.getZ());
         return dx1 != dx2 || dz1 != dz2;
      } else {
         return false;
      }
   }

   public static boolean canPlaceSurfaceAt(int x, int z, int effectiveHalfY, boolean hasSlabVariant, Predicate<BlockPos> isReplaceable) {
      return canPlaceSurfaceAt(x, z, effectiveHalfY, hasSlabVariant, isReplaceable, new MutableBlockPos());
   }

   private static boolean canPlaceSurfaceAt(int x, int z, int effectiveHalfY, boolean hasSlabVariant, Predicate<BlockPos> isReplaceable, MutableBlockPos probe) {
      boolean slab = (effectiveHalfY & 1) == 1;
      int placeY = slab ? (effectiveHalfY - 1) / 2 : effectiveHalfY / 2 - 1;
      int minPlaceY = slab && !hasSlabVariant ? placeY - 1 : placeY;

      for (int py = minPlaceY; py <= placeY; py++) {
         probe.set(x, py, z);
         if (!isReplaceable.test(probe)) {
            return false;
         }
      }

      return true;
   }

   public static List<PathEntry> buildPath(
      List<BlockPos> trace,
      Block fullBlock,
      @Nullable SlabBlock slabBlock,
      int pathWidth,
      int pathLevel,
      Predicate<BlockPos> isProtectedFromPath,
      Predicate<BlockPos> isHardProtected,
      Predicate<BlockPos> isReplaceable,
      ToIntFunction<BlockPos> existingPathLevel,
      @Nullable Predicate<BlockPos> hasHeadroom,
      ToIntBiFunction<Integer, Integer> groundAtColumn,
      Predicate<BlockPos> canFillBelowPath,
      Predicate<BlockPos> canCutForHeadroom,
      ToIntBiFunction<Integer, Integer> lockedSurfaceHalfYAt
   ) {
      if (trace.size() < 2) {
         return List.of();
      }

      boolean[] shouldBuild = computeBuildMask(trace, isProtectedFromPath, isHardProtected);
      List<BlockPos> centreTrace = new ArrayList<>();

      for (int i = 0; i < trace.size(); i++) {
         if (shouldBuild[i]) {
            centreTrace.add(trace.get(i));
         }
      }

      if (centreTrace.size() < 2) {
         return List.of();
      }

      int[] groundHalfY = new int[centreTrace.size()];

      for (int i = 0; i < centreTrace.size(); i++) {
         groundHalfY[i] = 2 * centreTrace.get(i).getY();
      }

      int[] surfaceHalfY = PathProfileOptimizer.optimize(groundHalfY, PathProfileOptimizer.Weights.defaults());

      for (int i = 0; i < centreTrace.size(); i++) {
         BlockPos p = centreTrace.get(i);
         int locked = lockedSurfaceHalfYAt.applyAsInt(p.getX(), p.getZ());
         if (locked != Integer.MIN_VALUE) {
            surfaceHalfY[i] = locked;
         }
      }

      Predicate<BlockPos> canPlaceAt = pos -> {
         if (isProtectedFromPath.test(pos)) {
            return false;
         } else if (isHardProtected.test(pos)) {
            return false;
         } else if (!isReplaceable.test(pos)) {
            return false;
         } else {
            return hasHeadroom != null && !hasHeadroom.test(pos) ? false : existingPathLevel.applyAsInt(pos) < pathLevel;
         }
      };
      List<PathBlockPlacer.WidenedColumn> columns = computeWidenedColumns(centreTrace, surfaceHalfY, pathWidth, canPlaceAt);
      List<PathEntry> out = new ArrayList<>();
      BlockState air = Blocks.AIR.defaultBlockState();
      Set<Long> columnsBuilt = new HashSet<>();
      MutableBlockPos probe = new MutableBlockPos();

      for (PathBlockPlacer.WidenedColumn c : columns) {
         long k = (long)c.x << 32 | c.z & 4294967295L;
         if (columnsBuilt.add(k)) {
            int lockedWidened = lockedSurfaceHalfYAt.applyAsInt(c.x, c.z);
            int effectiveHalfY = lockedWidened != Integer.MIN_VALUE ? lockedWidened : c.surfaceHalfY;
            if (canPlaceSurfaceAt(c.x, c.z, effectiveHalfY, slabBlock != null, isReplaceable, probe)) {
               int groundY = groundAtColumn.applyAsInt(c.x, c.z);
               PathTerraformer.ColumnDecision decision = new PathTerraformer.ColumnDecision(c.x, c.z, effectiveHalfY, groundY);
               int cx = c.x;
               int cz = c.z;
               IntUnaryOperator fill = y -> {
                  probe.set(cx, y, cz);
                  return canFillBelowPath.test(probe) ? 1 : 0;
               };
               IntUnaryOperator cut = y -> {
                  probe.set(cx, y, cz);
                  return canCutForHeadroom.test(probe) ? 1 : 0;
               };
               out.addAll(PathTerraformer.blocksForColumn(decision, fullBlock, slabBlock, DEFAULT_FOUNDATION, air, fill, cut));
            }
         }
      }

      return out;
   }

   public record WidenedColumn(int x, int z, int surfaceHalfY) {
   }
}
