package org.millenaire.goal;

import com.mojang.logging.LogUtils;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.entity.BlockHazards;
import org.millenaire.entity.MillVillager;
import org.slf4j.Logger;

public final class NavigationHelperUtils {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int TELEPORT_FACTOR = 4;
   private static final int RANDOM_SAFE_ATTEMPTS = 20;
   private static final int RANDOM_SAFE_RADIUS = 2;

   private NavigationHelperUtils() {
   }

   public static double horizontalDistSq(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dz = a.getZ() - b.getZ();
      return dx * dx + dz * dz;
   }

   public static boolean isSafeLanding(Level level, BlockPos pos) {
      if (level.getBlockState(pos).isSuffocating(level, pos)) {
         return false;
      } else {
         return level.getBlockState(pos.above()).isSuffocating(level, pos.above()) ? false : !BlockHazards.isHazardousAt(level, pos);
      }
   }

   public static void teleportToSafe(MillVillager villager, BlockPos target) {
      Level level = villager.level();
      if (isSafeLanding(level, target)) {
         villager.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
         villager.getNavigation().stop();
      } else if (!tryTeleportTowards(villager, target)) {
         BlockPos outdoorPos = villager.getLastOutdoorPos();
         if (outdoorPos != null && isSafeLanding(level, outdoorPos)) {
            LOGGER.debug("[Millenaire] NavHelperUtils — TP to lastOutdoorPos {}", outdoorPos.toShortString());
            villager.teleportTo(outdoorPos.getX() + 0.5, outdoorPos.getY(), outdoorPos.getZ() + 0.5);
            villager.getNavigation().stop();
         } else {
            BlockPos safePos = findRandomSafePos(level, villager.blockPosition());
            if (safePos != null) {
               LOGGER.debug("[Millenaire] NavHelperUtils — TP to random safe pos {}", safePos.toShortString());
               villager.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
               villager.getNavigation().stop();
            } else {
               int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
               BlockPos surfacePos = new BlockPos(target.getX(), surfaceY, target.getZ());

               for (int nudge = 0; nudge < 5; nudge++) {
                  BlockPos candidate = surfacePos.above(nudge);
                  if (isSafeLanding(level, candidate)) {
                     villager.teleportTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                     villager.getNavigation().stop();
                     return;
                  }
               }

               LOGGER.warn("[Millenaire] NavHelperUtils — Step 5 fallback may suffocate at {}", surfacePos.toShortString());
               villager.teleportTo(surfacePos.getX() + 0.5, surfacePos.getY(), surfacePos.getZ() + 0.5);
               villager.getNavigation().stop();
            }
         }
      }
   }

   public static void teleportToSafeNearTarget(MillVillager villager, BlockPos target) {
      Level level = villager.level();
      if (isSafeLanding(level, target)) {
         villager.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
         villager.getNavigation().stop();
      } else if (!tryTeleportTowards(villager, target)) {
         BlockPos safePos = findRandomSafePos(level, target);
         if (safePos != null) {
            LOGGER.debug("[Millenaire] NavHelperUtils — TP safe near target: {}", safePos.toShortString());
            villager.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
            villager.getNavigation().stop();
         } else {
            int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
            BlockPos surfacePos = new BlockPos(target.getX(), surfaceY, target.getZ());

            for (int nudge = 0; nudge < 5; nudge++) {
               BlockPos candidate = surfacePos.above(nudge);
               if (isSafeLanding(level, candidate)) {
                  villager.teleportTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                  villager.getNavigation().stop();
                  return;
               }
            }

            LOGGER.warn("[Millenaire] NavHelperUtils — target fallback unsafe at {}", surfacePos.toShortString());
            villager.teleportTo(surfacePos.getX() + 0.5, surfacePos.getY(), surfacePos.getZ() + 0.5);
            villager.getNavigation().stop();
         }
      }
   }

   public static boolean tryTeleportTowards(MillVillager villager, BlockPos dest) {
      BlockPos current = villager.blockPosition();
      int dx = dest.getX() - current.getX();
      int dz = dest.getZ() - current.getZ();
      if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4) {
         return false;
      } else {
         int xDir = Integer.signum(dx);
         int zDir = Integer.signum(dz);
         int targetX = current.getX() + xDir * 4;
         int targetZ = current.getZ() + zDir * 4;
         int targetY = villager.level().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
         Level level = villager.level();
         BlockPos candidate = new BlockPos(targetX, targetY, targetZ);
         if (isSafeLanding(level, candidate)) {
            LOGGER.debug("[Millenaire] NavHelperUtils — partial TP towards dest: {}", candidate.toShortString());
            villager.teleportTo(targetX + 0.5, targetY, targetZ + 0.5);
            villager.getNavigation().stop();
            return true;
         } else {
            return false;
         }
      }
   }

   @Nullable
   public static BlockPos findRandomSafePos(Level level, BlockPos origin) {
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for (int i = 0; i < 20; i++) {
         int rdx = random.nextInt(-2, 3);
         int dy = random.nextInt(-1, 2);
         int rdz = random.nextInt(-2, 3);
         if (rdx != 0 || dy != 0 || rdz != 0) {
            BlockPos test = origin.offset(rdx, dy, rdz);
            BlockPos below = test.below();
            if (level.getBlockState(below).isSolidRender(level, below) && isSafeLanding(level, test)) {
               return test;
            }
         }
      }

      return null;
   }
}
