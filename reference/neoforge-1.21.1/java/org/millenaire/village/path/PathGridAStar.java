package org.millenaire.village.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.world.TerrainPreparer;

public final class PathGridAStar {
   private static final int[][] DIRECTIONS = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
   private static final int DIR_NONE = -1;

   private PathGridAStar() {
   }

   @Nullable
   public static List<BlockPos> findPath(
      BlockPos from,
      BlockPos to,
      PathGridAStar.GroundHeightProvider ground,
      PathGridAStar.TraversabilityCheck traversable,
      PathGridAStar.Weights w,
      int maxNodes
   ) {
      return search(from, to, ground, traversable, w, maxNodes).path;
   }

   public static PathGridAStar.Result search(
      BlockPos from,
      BlockPos to,
      PathGridAStar.GroundHeightProvider ground,
      PathGridAStar.TraversabilityCheck traversable,
      PathGridAStar.Weights w,
      int maxNodes
   ) {
      int fx = from.getX();
      int fz = from.getZ();
      int tx = to.getX();
      int tz = to.getZ();
      Comparator<PathGridAStar.Node> comparator = Comparator.comparingDouble(n -> n.f);
      PriorityQueue<PathGridAStar.Node> open = new PriorityQueue<>(comparator);
      Map<PathGridAStar.DirKey, Double> bestG = new HashMap<>();
      int startY = ground.heightAt(fx, fz);
      double h0 = w.heuristicScale * manhattan(fx, fz, tx, tz);
      open.add(new PathGridAStar.Node(fx, fz, startY, -1, 0.0, h0, null));
      bestG.put(new PathGridAStar.DirKey(fx, fz, -1), 0.0);
      int explored = 0;
      int rejectedStep = 0;
      int rejectedTraversable = 0;

      while (!open.isEmpty() && explored < maxNodes) {
         PathGridAStar.Node cur = open.poll();
         explored++;
         if (cur.x == tx && cur.z == tz) {
            return new PathGridAStar.Result(reconstruct(cur), explored, "found");
         }

         for (int di = 0; di < DIRECTIONS.length; di++) {
            int[] d = DIRECTIONS[di];
            int nx = cur.x + d[0];
            int nz = cur.z + d[1];
            if (!traversable.allows(nx, nz)) {
               rejectedTraversable++;
            } else {
               int ny = ground.heightAt(nx, nz);
               int dh = ny - cur.y;
               if (Math.abs(dh) > w.maxStepUp) {
                  rejectedStep++;
               } else {
                  double stepCost = 1.0 + w.gradientAlpha * dh * dh;
                  if (cur.dirIdx != -1 && cur.dirIdx != di) {
                     stepCost += w.turnPenalty;
                  }

                  double newG = cur.g + stepCost;
                  PathGridAStar.DirKey key = new PathGridAStar.DirKey(nx, nz, di);
                  Double existing = bestG.get(key);
                  if (existing == null || !(existing <= newG)) {
                     bestG.put(key, newG);
                     double hEst = w.heuristicScale * manhattan(nx, nz, tx, tz);
                     open.add(new PathGridAStar.Node(nx, nz, ny, di, newG, newG + hEst, cur));
                  }
               }
            }
         }
      }

      String reason;
      if (explored >= maxNodes) {
         reason = "budget_exhausted";
      } else {
         reason = String.format("frontier_empty (rejectedStep=%d, rejectedTraversable=%d)", rejectedStep, rejectedTraversable);
      }

      return new PathGridAStar.Result(null, explored, reason);
   }

   @Nullable
   public static List<BlockPos> findPath(ServerLevel level, BlockPos from, BlockPos to, PathGridAStar.Weights w, int maxNodes) {
      return searchOnLevel(level, from, to, w, maxNodes).path;
   }

   public static PathGridAStar.Result searchOnLevel(ServerLevel level, BlockPos from, BlockPos to, PathGridAStar.Weights w, int maxNodes) {
      PathGridAStar.GroundHeightProvider ground = (x, z) -> TerrainPreparer.getGroundHeight(level, x, z);
      PathGridAStar.TraversabilityCheck traversable = (x, z) -> isWalkableColumn(level, x, z, ground.heightAt(x, z));
      return search(from, to, ground, traversable, w, maxNodes);
   }

   private static boolean isWalkableColumn(ServerLevel level, int x, int z, int y) {
      BlockPos surface = new BlockPos(x, y - 1, z);
      BlockState surfaceState = level.getBlockState(surface);
      return surfaceState.liquid() ? false : surfaceState.getFluidState().isEmpty();
   }

   @Nullable
   public static List<BlockPos> findPath(
      BlockPos from, BlockPos to, BiFunction<Integer, Integer, Integer> heightProvider, BiPredicate<Integer, Integer> traversable, int maxNodes
   ) {
      PathGridAStar.GroundHeightProvider g = (x, z) -> heightProvider.apply(x, z);
      PathGridAStar.TraversabilityCheck t = (x, z) -> traversable.test(x, z);
      return findPath(from, to, g, t, PathGridAStar.Weights.defaults(), maxNodes);
   }

   private static double manhattan(int x1, int z1, int x2, int z2) {
      return Math.abs(x1 - x2) + Math.abs(z1 - z2);
   }

   private static List<BlockPos> reconstruct(PathGridAStar.Node end) {
      List<BlockPos> path = new ArrayList<>();

      for (PathGridAStar.Node cur = end; cur != null; cur = cur.parent) {
         path.add(new BlockPos(cur.x, cur.y, cur.z));
      }

      Collections.reverse(path);
      return path;
   }

   private record DirKey(int x, int z, int dirIdx) {
   }

   @FunctionalInterface
   public interface GroundHeightProvider {
      int heightAt(int var1, int var2);
   }

   private record Node(int x, int z, int y, int dirIdx, double g, double f, @Nullable PathGridAStar.Node parent) {
   }

   public record Result(@Nullable List<BlockPos> path, int nodesExplored, String reason) {
      public boolean success() {
         return this.path != null;
      }
   }

   @FunctionalInterface
   public interface TraversabilityCheck {
      boolean allows(int var1, int var2);
   }

   public record Weights(double gradientAlpha, int maxStepUp, double heuristicScale, double turnPenalty) {
      public static PathGridAStar.Weights defaults() {
         return new PathGridAStar.Weights(2.0, 3, 1.0, 0.3);
      }

      public static PathGridAStar.Weights relaxed() {
         return new PathGridAStar.Weights(0.5, 6, 1.0, 0.3);
      }
   }
}
