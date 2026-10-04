package org.millenaire.culture;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingPlanSet;
import org.slf4j.Logger;

final class TravelBookCategoryAssigner {
   private static final Logger LOGGER = LogUtils.getLogger();

   private TravelBookCategoryAssigner() {
   }

   static void autoAssign(ResourceLocation cultureId) {
      for (Entry<ResourceLocation, BuildingPlanSet> entry : ModCultures.getAllBuildingPlanSets().entrySet()) {
         BuildingPlanSet set = entry.getValue();
         if (set.culture().equals(cultureId) && set.travelBookCategory() == null) {
            String autoCategory = autoAssignBuildingCategory(set);
            ModCultures.registerBuildingPlanSet(
               new BuildingPlanSet(
                  set.id(),
                  set.culture(),
                  set.buildingId(),
                  set.category(),
                  set.nativeName(),
                  set.maxCount(),
                  set.minDistance(),
                  set.maxDistance(),
                  set.maleResidents(),
                  set.femaleResidents(),
                  set.priorityMoveIn(),
                  set.tags(),
                  set.terrainPolicy(),
                  set.constructionOrder(),
                  set.variants(),
                  set.startingSubBuildings(),
                  set.icon(),
                  set.clearMargins(),
                  set.price(),
                  set.reputation(),
                  set.randomBrickColours(),
                  set.startingGoods(),
                  autoCategory,
                  set.travelBookDisplay(),
                  set.isSubBuilding(),
                  set.isTownHall(),
                  set.farFromTags(),
                  set.closeToTags(),
                  set.fixedOrientation(),
                  set.isWallSegment(),
                  set.isBorderBuilding(),
                  set.extraWallConstructionSlots()
               )
            );
         }
      }

      for (Entry<ResourceLocation, VillagerType> entry : ModCultures.getAllVillagerTypes().entrySet()) {
         VillagerType vt = entry.getValue();
         if (vt.culture().equals(cultureId) && vt.travelBookCategory() == null) {
            String autoCategory = autoAssignVillagerCategory(vt, cultureId);
            ModCultures.registerVillagerType(
               new VillagerType(
                  vt.id(),
                  vt.culture(),
                  vt.model(),
                  vt.textures(),
                  vt.clothes(),
                  vt.baseScale(),
                  vt.isChild(),
                  vt.goals(),
                  vt.tags(),
                  vt.spawnWeight(),
                  vt.initialInventory(),
                  vt.gender(),
                  vt.firstNameList(),
                  vt.familyNameList(),
                  vt.maleChild(),
                  vt.femaleChild(),
                  vt.bringBackHomeGoods(),
                  vt.collectGoods(),
                  vt.requiredGoods(),
                  vt.icon(),
                  vt.toolNeededClasses(),
                  vt.itemsNeeded(),
                  vt.maxHealth(),
                  vt.villagerConfigKey(),
                  autoCategory,
                  vt.travelBookDisplay(),
                  vt.nativeName(),
                  vt.foreignMerchantStock(),
                  vt.hiringCost(),
                  vt.travelBookHeldItem(),
                  vt.travelBookHeldItemOffHand(),
                  vt.altNativeName(),
                  vt.altKey(),
                  vt.travelBookMainCultureVillager(),
                  vt.defaultWeapon(),
                  vt.resolvedBringBackHomeGoods(),
                  vt.resolvedCollectGoods(),
                  vt.resolvedRequiredGoods()
               )
            );
         }
      }
   }

   private static String autoAssignBuildingCategory(BuildingPlanSet set) {
      if (set.price() > 0) {
         return "playerbuilding";
      }

      boolean foundInLone = false;
      boolean foundInNonLone = false;

      label59:
      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if (vt.culture().equals(set.culture())) {
            Iterator var5 = vt.layout().iterator();

            while (true) {
               if (var5.hasNext()) {
                  VillageType.LayoutSlot slot = (VillageType.LayoutSlot)var5.next();
                  if (!slot.plan().equals(set.id())) {
                     continue;
                  }

                  if (vt.loneBuilding()) {
                     foundInLone = true;
                  } else {
                     foundInNonLone = true;
                  }
               }

               if (foundInNonLone) {
                  break label59;
               }
               break;
            }
         }
      }

      if (foundInLone && !foundInNonLone) {
         return "lonebuilding";
      }

      return switch (set.category()) {
         case "townhalls" -> "townhall";
         case "houses" -> "house";
         default -> "othervillage";
      };
   }

   private static String autoAssignVillagerCategory(VillagerType vt, ResourceLocation cultureId) {
      if (vt.hasTag("chief")) {
         return "leader";
      }

      if (vt.hasTag("visitor")) {
         return "visitor";
      }

      for (BuildingPlanSet set : ModCultures.getAllBuildingPlanSets().values()) {
         if (set.culture().equals(cultureId)) {
            String culturePrefix = set.culture().getPath() + "_";
            boolean residesHere = set.maleResidents().stream().anyMatch(r -> vt.id().getPath().equals(culturePrefix + r))
               || set.femaleResidents().stream().anyMatch(r -> vt.id().getPath().equals(culturePrefix + r));
            if (residesHere) {
               for (VillageType villageType : ModCultures.getAllVillageTypes().values()) {
                  if (villageType.culture().equals(cultureId) && villageType.loneBuilding()) {
                     for (VillageType.LayoutSlot slot : villageType.layout()) {
                        if (slot.plan().equals(set.id())) {
                           return "lonevillager";
                        }
                     }
                  }
               }
            }
         }
      }

      return "villager";
   }
}
