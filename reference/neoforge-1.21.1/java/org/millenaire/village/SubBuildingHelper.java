package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.PlacementStep;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;
import org.slf4j.Logger;

public final class SubBuildingHelper {
   private static final Logger LOGGER = LogUtils.getLogger();

   private SubBuildingHelper() {
   }

   public static void spawnStartingSubBuildings(ServerLevel level, Village village, BuildingPlanSet planSet, BuildingInstance parent, boolean rush) {
      if (!planSet.startingSubBuildings().isEmpty()) {
         String culturePath = planSet.culture().getPath();

         for (String subKey : planSet.startingSubBuildings()) {
            spawnSubBuilding(level, village, culturePath, subKey, parent, rush);
         }
      }
   }

   public static void spawnUpgradeSubBuildings(
      ServerLevel level, Village village, BuildingPlanSet planSet, BuildingPlanSet.LevelDef levelDef, BuildingInstance parent, boolean rush
   ) {
      if (!levelDef.subBuildings().isEmpty()) {
         String culturePath = planSet.culture().getPath();

         for (String subKey : levelDef.subBuildings()) {
            spawnSubBuilding(level, village, culturePath, subKey, parent, rush);
         }
      }
   }

   private static void spawnSubBuilding(ServerLevel level, Village village, String culturePath, String subBuildingKey, BuildingInstance parent, boolean rush) {
      ResourceLocation subSetId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePath + "/" + subBuildingKey.toLowerCase());
      BuildingPlanSet subPlanSet = ModCultures.getBuildingPlanSet(subSetId);
      if (subPlanSet == null) {
         LOGGER.warn("[Millenaire] Sub-building not found: {}", subSetId);
      } else {
         if (!subPlanSet.startingSubBuildings().isEmpty()) {
            LOGGER.warn("[Millenaire] Sub-building {} itself has startingSubBuildings — ignored (no recursivity)", subSetId);
         }

         for (String subVariantKey : subPlanSet.variants().keySet()) {
            BuildingPlanSet.LevelDef subLevel0 = subPlanSet.getLevel(subVariantKey, 0);
            if (subLevel0 != null && !subLevel0.subBuildings().isEmpty()) {
               LOGGER.warn("[Millenaire] Sub-building {} variant {} level 0 has sub_buildings — ignored (no recursivity)", subSetId, subVariantKey);
            }
         }

         if (subPlanSet.variants().isEmpty()) {
            LOGGER.warn("[Millenaire] Sub-building {} has no variants — skipping", subSetId);
         } else {
            String variant = subPlanSet.pickRandomVariant(ThreadLocalRandom.current());
            BuildingPlanSet.LevelDef levelDef = subPlanSet.getLevel(variant, 0);
            if (levelDef == null) {
               LOGGER.warn("[Millenaire] Sub-building {}: no level 0 for variant {}", subSetId, variant);
            } else {
               BuildingPlan subPlan = ModCultures.getBuildingPlan(levelDef.planId());
               if (subPlan == null) {
                  LOGGER.warn("[Millenaire] Sub-building {}: plan not found {}", subSetId, levelDef.planId());
               } else {
                  BlockPos origin = parent.getOrigin();
                  Rotation rotation = parent.getRotation();
                  BuildingId subId = BuildingId.random();
                  if (rush) {
                     BuildingInstance subInstance = new BuildingInstance(
                        subId, subPlan.id(), origin, rotation, BuildingInstance.Status.COMPLETE, subPlanSet.id(), variant, 0
                     );
                     subInstance.setSubBuilding(true);
                     subInstance.setParentBuildingId(parent.getId());
                     BuildingPlacer.placeInstantly(level, subPlan, origin, rotation, subInstance);
                     BuildingFinalizer.applyPostPlacement(level, village, subInstance, subPlan);
                     village.addBuilding(subInstance);
                     BuildingFinalizer.applyCompletionEffects(level, village, subInstance);
                     VillageGrowthManager.spawnBuildingOccupants(level, village, subPlanSet, subInstance);
                  } else {
                     BuildingInstance subInstance = new BuildingInstance(
                        subId, subPlan.id(), origin, rotation, BuildingInstance.Status.PLANNED, subPlanSet.id(), variant, 0
                     );
                     subInstance.setSubBuilding(true);
                     subInstance.setParentBuildingId(parent.getId());
                     List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(level, subPlan, origin, rotation);
                     if (!steps.isEmpty()) {
                        subInstance.setConstructionTask(new ConstructionTask(steps, 0));
                     }

                     village.addBuilding(subInstance);
                  }

                  LOGGER.info("[Millenaire] Sub-building {} created for {} (rush={})", new Object[]{subSetId.getPath(), parent.getPlanId().getPath(), rush});
               }
            }
         }
      }
   }
}
