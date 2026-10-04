package org.millenaire.village;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.network.ControlledProjectsPayload;

public final class ControlledProjectsService {
   private ControlledProjectsService() {
   }

   public static ControlledProjectsPayload buildPayload(Village village) {
      BlockPos center = village.getCenter();
      List<ControlledProjectsPayload.ProjectEntry> entries = new ArrayList<>();

      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isSubBuilding()) {
            BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
            if (planSet != null) {
               String variant = building.getVariant() != null ? building.getVariant() : "0";
               int maxLevel = planSet.getLevelCount(variant);
               if (maxLevel <= 0) {
                  maxLevel = 1;
               }

               String displayName = planSet.nativeName() != null ? planSet.nativeName() : planSet.id().getPath();
               String distance = formatDistanceDirection(center, building.getOrigin());
               entries.add(
                  new ControlledProjectsPayload.ProjectEntry(
                     building.getId().uuid().toString(),
                     displayName,
                     planSet.id().toString(),
                     building.getLevel(),
                     maxLevel,
                     building.isUpgradesAllowed(),
                     distance
                  )
               );
            }
         }
      }

      String pendingPlanName = "";
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null) {
         BuildingPlanSet pendingSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         if (pendingSet != null) {
            pendingPlanName = pendingSet.nativeName() != null ? pendingSet.nativeName() : pendingSet.id().getPath();
         }
      }

      String villageName = village.getVillageName() != null ? village.getVillageName() : "";
      return new ControlledProjectsPayload(village.getId().uuid().toString(), villageName, pendingPlanName, entries);
   }

   private static String formatDistanceDirection(BlockPos origin, BlockPos target) {
      int dx = target.getX() - origin.getX();
      int dz = target.getZ() - origin.getZ();
      int dist = (int)Math.sqrt(dx * dx + dz * dz);
      String dir;
      if (dx == 0 && dz == 0) {
         dir = "";
      } else {
         double angle = Math.atan2(-dx, -dz);
         int sector = (int)Math.round(angle / (Math.PI / 4));

         dir = switch (Math.floorMod(sector, 8)) {
            case 0 -> "N";
            case 1 -> "NE";
            case 2 -> "E";
            case 3 -> "SE";
            case 4 -> "S";
            case 5 -> "SW";
            case 6 -> "W";
            case 7 -> "NW";
            default -> "";
         };
      }

      return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
   }
}
