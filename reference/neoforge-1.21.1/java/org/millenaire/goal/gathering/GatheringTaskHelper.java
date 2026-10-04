package org.millenaire.goal.gathering;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class GatheringTaskHelper {
   private static final int VERTICAL_SCAN_RANGE = 20;

   private GatheringTaskHelper() {
   }

   @Nullable
   public static BlockPos findNearestBlock(ServerLevel level, BlockPos center, int radius, Predicate<BlockState> predicate) {
      return findNearestBlock(level, center, radius, predicate, null);
   }

   @Nullable
   public static BlockPos findNearestBlock(
      ServerLevel level, BlockPos center, int radius, Predicate<BlockState> statePredicate, @Nullable Predicate<BlockPos> posPredicate
   ) {
      BlockPos best = null;
      double bestDistSq = Double.MAX_VALUE;
      int foundAtDist = -1;
      MutableBlockPos mutable = new MutableBlockPos();
      int cx = center.getX();
      int cy = center.getY();
      int cz = center.getZ();

      for (int d = 0; d <= radius && (foundAtDist < 0 || d <= foundAtDist); d++) {
         if (d == 0) {
            for (int y = -20; y <= 20; y++) {
               mutable.set(cx, cy + y, cz);
               if (level.isLoaded(mutable)) {
                  BlockState state = level.getBlockState(mutable);
                  if (statePredicate.test(state) && (posPredicate == null || posPredicate.test(mutable))) {
                     double distSq = center.distSqr(mutable);
                     if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = mutable.immutable();
                        foundAtDist = d;
                     }
                  }
               }
            }
         } else {
            for (int x = -d; x <= d; x++) {
               for (int side = -1; side <= 1; side += 2) {
                  int z = side * d;

                  for (int y = -20; y <= 20; y++) {
                     mutable.set(cx + x, cy + y, cz + z);
                     if (level.isLoaded(mutable)) {
                        BlockState state = level.getBlockState(mutable);
                        if (statePredicate.test(state) && (posPredicate == null || posPredicate.test(mutable))) {
                           double distSq = center.distSqr(mutable);
                           if (distSq < bestDistSq) {
                              bestDistSq = distSq;
                              best = mutable.immutable();
                              foundAtDist = d;
                           }
                        }
                     }
                  }
               }
            }

            for (int z = -d + 1; z <= d - 1; z++) {
               for (int side = -1; side <= 1; side += 2) {
                  int x = side * d;

                  for (int y = -20; y <= 20; y++) {
                     mutable.set(cx + x, cy + y, cz + z);
                     if (level.isLoaded(mutable)) {
                        BlockState state = level.getBlockState(mutable);
                        if (statePredicate.test(state) && (posPredicate == null || posPredicate.test(mutable))) {
                           double distSq = center.distSqr(mutable);
                           if (distSq < bestDistSq) {
                              bestDistSq = distSq;
                              best = mutable.immutable();
                              foundAtDist = d;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   public static boolean isStuck(BlockPos currentPos, @Nullable BlockPos lastPos, double thresholdSq) {
      return lastPos == null ? false : currentPos.distSqr(lastPos) < thresholdSq;
   }
}
