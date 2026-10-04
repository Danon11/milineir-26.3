package org.millenaire.village.path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

public final class FallbackAnchorFinder {
   public static final int MAX_PROBES = 3;

   private FallbackAnchorFinder() {
   }

   public static List<BlockPos> enumeratePerimeter(BlockPos min, BlockPos max, int radius, int spacing) {
      if (spacing <= 0) {
         spacing = 1;
      }

      List<BlockPos> out = new ArrayList<>();
      int y = min.getY();
      int x0 = min.getX() - radius;
      int x1 = max.getX() + radius;
      int z0 = min.getZ() - radius;
      int z1 = max.getZ() + radius;

      for (int x = x0; x <= x1; x += spacing) {
         out.add(new BlockPos(x, y, z0));
         out.add(new BlockPos(x, y, z1));
      }

      for (int z = z0 + spacing; z <= z1 - spacing; z += spacing) {
         out.add(new BlockPos(x0, y, z));
         out.add(new BlockPos(x1, y, z));
      }

      return out;
   }

   public static List<BlockPos> findCandidates(BlockPos min, BlockPos max, Predicate<BlockPos> traversable) {
      List<BlockPos> pass1 = enumeratePerimeter(min, max, 1, 2);
      List<BlockPos> ok = filterAndCap(pass1, traversable);
      if (!ok.isEmpty()) {
         return ok;
      }

      List<BlockPos> pass2 = enumeratePerimeter(min, max, 3, 1);
      return filterAndCap(pass2, traversable);
   }

   private static List<BlockPos> filterAndCap(List<BlockPos> cands, Predicate<BlockPos> traversable) {
      List<BlockPos> ok = new ArrayList<>();

      for (BlockPos p : cands) {
         if (traversable.test(p)) {
            ok.add(p);
         }

         if (ok.size() >= 3) {
            break;
         }
      }

      return ok;
   }

   public static List<BlockPos> sortedByDistance(List<BlockPos> cands, BlockPos target) {
      List<BlockPos> copy = new ArrayList<>(cands);
      copy.sort(Comparator.comparingInt(p -> Math.abs(p.getX() - target.getX()) + Math.abs(p.getZ() - target.getZ())));
      return copy;
   }
}
