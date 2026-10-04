package org.millenaire.world;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.ClearMargins;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class BuildingLocationFinder {
   private static final Logger LOGGER = LogUtils.getLogger();

   private BuildingLocationFinder() {
   }

   @Nullable
   public static PlacedLocation findLocation(
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings
   ) {
      return findLocation(map, plan, villageCenter, constraints, clearMargins, existingBuildings, null);
   }

   @Nullable
   public static PlacedLocation findLocation(
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings,
      @Nullable TerrainReachability reachability
   ) {
      int centerX = villageCenter.getX();
      int centerZ = villageCenter.getZ();
      int minDist = constraints.minDistance();
      int maxDist = constraints.maxDistance();
      int sideOffset = ThreadLocalRandom.current().nextInt(4);

      for (int r = minDist; r <= maxDist; r++) {
         for (int sideIdx = 0; sideIdx < 4; sideIdx++) {
            int side = (sideIdx + sideOffset) % 4;
            PlacedLocation result = scanSide(side, r, centerX, centerZ, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability);
            if (result != null) {
               return result;
            }
         }
      }

      return null;
   }

   @Nullable
   private static PlacedLocation scanSide(
      int side,
      int r,
      int centerX,
      int centerZ,
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings,
      @Nullable TerrainReachability reachability
   ) {
      switch (side) {
         case 0:
            for (int x = -r; x <= r; x++) {
               PlacedLocation result = tryPosition(
                  centerX + x, centerZ - r, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability
               );
               if (result != null) {
                  return result;
               }
            }
            break;
         case 1:
            for (int z = -r + 1; z <= r; z++) {
               PlacedLocation result = tryPosition(
                  centerX + r, centerZ + z, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability
               );
               if (result != null) {
                  return result;
               }
            }
            break;
         case 2:
            for (int x = r - 1; x >= -r; x--) {
               PlacedLocation result = tryPosition(
                  centerX + x, centerZ + r, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability
               );
               if (result != null) {
                  return result;
               }
            }
            break;
         case 3:
            for (int z = r - 1; z >= -r + 1; z--) {
               PlacedLocation result = tryPosition(
                  centerX - r, centerZ + z, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability
               );
               if (result != null) {
                  return result;
               }
            }
      }

      return null;
   }

   @Nullable
   private static PlacedLocation tryPosition(
      int worldX,
      int worldZ,
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings,
      @Nullable TerrainReachability reachability
   ) {
      int halfFootprint = (plan.width() + plan.depth()) / 4;
      int candidateCenterX = worldX + halfFootprint;
      int candidateCenterZ = worldZ + halfFootprint;
      int computedDir = directionToVillageCenter(candidateCenterX, candidateCenterZ, villageCenter);
      int targetDirection = constraints.fixedOrientation() != null ? constraints.fixedOrientation() : computedDir;
      int rotationOrdinal = Math.floorMod(targetDirection - plan.buildingOrientation(), 4);
      Rotation rotation = Rotation.values()[rotationOrdinal];
      int pivotX;
      int pivotZ;
      switch (rotation) {
         case CLOCKWISE_90:
            pivotX = worldX + plan.depth() - 1;
            pivotZ = worldZ;
            break;
         case CLOCKWISE_180:
            pivotX = worldX + plan.width() - 1;
            pivotZ = worldZ + plan.depth() - 1;
            break;
         case COUNTERCLOCKWISE_90:
            pivotX = worldX;
            pivotZ = worldZ + plan.width() - 1;
            break;
         default:
            pivotX = worldX;
            pivotZ = worldZ;
      }

      if (!checkTagConstraints(pivotX, pivotZ, constraints, existingBuildings)) {
         return null;
      }

      int result = map.testFootprint(pivotX, pivotZ, plan.width(), plan.depth(), clearMargins, rotation);
      if (result < 0) {
         return null;
      }

      if (reachability != null && !reachability.isFootprintReachable(pivotX, pivotZ, plan.width(), plan.depth(), rotation)) {
         return null;
      }

      BlockPos pos = new BlockPos(pivotX, villageCenter.getY(), pivotZ);
      if (LOGGER.isDebugEnabled()) {
         LOGGER.debug(
            "[Orient] plan={} bo={} fixed={} nwCorner=({},{}) wh={}x{} center=({},{}) thCenter=({},{}) dx={} dz={} target={} rot={} pivot=({},{})",
            new Object[]{
               plan.id(),
               plan.buildingOrientation(),
               constraints.fixedOrientation(),
               worldX,
               worldZ,
               plan.width(),
               plan.depth(),
               candidateCenterX,
               candidateCenterZ,
               villageCenter.getX(),
               villageCenter.getZ(),
               candidateCenterX - villageCenter.getX(),
               candidateCenterZ - villageCenter.getZ(),
               targetDirection,
               rotation,
               pivotX,
               pivotZ
            }
         );
      }

      return new PlacedLocation(pos, rotation);
   }

   static int directionToVillageCenter(int buildingX, int buildingZ, BlockPos villageCenter) {
      int relx = villageCenter.getX() - buildingX;
      int relz = villageCenter.getZ() - buildingZ;
      if (relx * relx > relz * relz) {
         return relx > 0 ? 1 : 3;
      } else {
         return relz > 0 ? 2 : 0;
      }
   }

   private static boolean checkTagConstraints(int worldX, int worldZ, PlacementConstraints constraints, List<BuildingInstance> existingBuildings) {
      for (Entry<String, Integer> entry : constraints.farFromTags().entrySet()) {
         String tag = entry.getKey();
         int distMin = entry.getValue();

         for (BuildingInstance building : existingBuildings) {
            BuildingPlan existingPlan = ModCultures.getBuildingPlan(building.getPlanId());
            if (existingPlan != null && existingPlan.hasTag(tag)) {
               double dist = horizontalDistance(worldX, worldZ, building.getOrigin());
               if (dist < distMin) {
                  return false;
               }
            }
         }
      }

      for (Entry<String, Integer> entry : constraints.closeToTags().entrySet()) {
         String tag = entry.getKey();
         int distMax = entry.getValue();
         boolean found = false;

         for (BuildingInstance building : existingBuildings) {
            BuildingPlan existingPlan = ModCultures.getBuildingPlan(building.getPlanId());
            if (existingPlan != null && existingPlan.hasTag(tag)) {
               double dist = horizontalDistance(worldX, worldZ, building.getOrigin());
               if (dist <= distMax) {
                  found = true;
                  break;
               }
            }
         }

         if (!found) {
            return false;
         }
      }

      return true;
   }

   private static double horizontalDistance(int x, int z, BlockPos pos) {
      int dx = x - pos.getX();
      int dz = z - pos.getZ();
      return Math.sqrt(dx * dx + dz * dz);
   }
}
