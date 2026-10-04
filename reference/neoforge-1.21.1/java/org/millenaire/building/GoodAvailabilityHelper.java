package org.millenaire.building;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public final class GoodAvailabilityHelper {
   private GoodAvailabilityHelper() {
   }

   public static int nbGoodAvailable(
      BuildingInstance building, Item item, ServerLevel level, Village village, ResourceLocation cultureId, boolean forShop, boolean forConstruction
   ) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return 0;
      }

      int nb = inv.getCount(level, item);
      if (nb <= 0) {
         return 0;
      }

      BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
      boolean isTownhall = plan != null && "townhall".equals(plan.shopId());
      String shopId = plan != null ? plan.shopId() : null;
      if (isTownhall) {
         nb -= getConstructionReservedQuantity(item, level, village, null);
      }

      if (!forConstruction) {
         boolean tradedHere = false;
         if (shopId != null) {
            ShopProfile profile = ShopProfileLoader.getProfile(cultureId, shopId);
            if (profile != null) {
               tradedHere = isItemSoldAt(item, profile, cultureId);
            }
         }

         if (isTownhall || tradedHere) {
            int reserved = getReservedQuantity(item, cultureId);
            nb -= reserved;
         }

         nb -= getResidentNeeds(building, item, village);
      }

      return Math.max(nb, 0);
   }

   public static int nbGoodAvailable(
      BuildingInstance building,
      Item item,
      ServerLevel level,
      Village village,
      ResourceLocation cultureId,
      boolean forShop,
      boolean forConstruction,
      @Nullable Village.PendingProject excludeProject
   ) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return 0;
      }

      int nb = inv.getCount(level, item);
      if (nb <= 0) {
         return 0;
      }

      BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
      boolean isTownhall = plan != null && "townhall".equals(plan.shopId());
      String shopId = plan != null ? plan.shopId() : null;
      if (isTownhall) {
         nb -= getConstructionReservedQuantity(item, level, village, excludeProject);
      }

      if (!forConstruction) {
         boolean tradedHere = false;
         if (shopId != null) {
            ShopProfile profile = ShopProfileLoader.getProfile(cultureId, shopId);
            if (profile != null) {
               tradedHere = isItemSoldAt(item, profile, cultureId);
            }
         }

         if (isTownhall || tradedHere) {
            int reserved = getReservedQuantity(item, cultureId);
            nb -= reserved;
         }

         nb -= getResidentNeeds(building, item, village);
      }

      return Math.max(nb, 0);
   }

   public static int nbGoodAvailable(BuildingInstance building, Item item, ServerLevel level, Village village, ResourceLocation cultureId, boolean forShop) {
      return nbGoodAvailable(building, item, level, village, cultureId, forShop, false);
   }

   private static boolean isItemSoldAt(Item item, ShopProfile profile, ResourceLocation cultureId) {
      ItemStack stack = new ItemStack(item);

      for (String sellName : profile.sells()) {
         TradeGood good = TradeGoodsLoader.getGoodById(cultureId, sellName);
         if (good != null && good.matchesItem(stack)) {
            return true;
         }
      }

      return false;
   }

   private static int getReservedQuantity(Item item, ResourceLocation cultureId) {
      List<TradeGood> tradeGoods = TradeGoodsLoader.getGoods(cultureId);
      if (tradeGoods == null) {
         return 0;
      }

      ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

      for (TradeGood good : tradeGoods) {
         if (!good.isTag() && good.itemLocation().equals(itemId)) {
            return good.reservedQuantity();
         }
      }

      return 0;
   }

   public static int getConstructionReservedQuantity(Item item, ServerLevel level, Village village, @Nullable Village.PendingProject excludeProject) {
      ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
      int reserved = 0;
      Village.PendingProject pending = village.getPendingProject();
      boolean pendingHandled = false;

      for (BuildingInstance building : village.getBuildings()) {
         ConstructionTask task = building.getConstructionTask();
         if (task != null && !task.isComplete() && building.isBeingBuilt()) {
            UUID builderUuid = task.getReservedBuilder();
            if (builderUuid == null) {
               if (pending != null
                  && !pendingHandled
                  && building.getPlanSetId() != null
                  && building.getPlanSetId().equals(pending.planSetId())
                  && pending.variant().equals(building.getVariant())
                  && pending.level() == building.getLevel()
                  && (!pending.isUpgrade() || Objects.equals(pending.buildingId(), building.getId()))) {
                  pendingHandled = true;
               }
            } else {
               BuildingPlanSet.LevelDef levelDef = getLevelDefForBuilding(building);
               if (levelDef != null) {
                  Map<ResourceLocation, Integer> resCost = levelDef.requiredResources();
                  if (resCost.containsKey(itemId)) {
                     int needed = resCost.get(itemId);
                     int builderHas = 0;
                     if (level.getEntity(builderUuid) instanceof MillVillager villager) {
                        builderHas = villager.getInventory().getCount(item);
                     }

                     if (builderHas < needed) {
                        reserved += needed - builderHas;
                     }
                  }

                  if (pending != null
                     && !pendingHandled
                     && building.getPlanSetId() != null
                     && building.getPlanSetId().equals(pending.planSetId())
                     && pending.variant().equals(building.getVariant())
                     && pending.level() == building.getLevel()
                     && (!pending.isUpgrade() || Objects.equals(pending.buildingId(), building.getId()))) {
                     pendingHandled = true;
                  }
               }
            }
         }
      }

      if (pending != null && !pendingHandled && !pending.equals(excludeProject)) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         if (planSet != null) {
            String variant = pending.variant();
            int pendingLevel = pending.level();
            BuildingPlanSet.LevelDef pendingDef = planSet.getLevel(variant, pendingLevel);
            if (pendingDef != null) {
               Map<ResourceLocation, Integer> resCost = pendingDef.requiredResources();
               if (resCost.containsKey(itemId)) {
                  reserved += resCost.get(itemId);
               }
            }
         }
      }

      return reserved;
   }

   public static Set<ResourceLocation> collectConstructionNeedItems(Village village) {
      Set<ResourceLocation> items = new HashSet<>();
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         if (planSet != null) {
            BuildingPlanSet.LevelDef levelDef = planSet.getLevel(pending.variant(), pending.level());
            if (levelDef != null) {
               items.addAll(levelDef.requiredResources().keySet());
            }
         }
      }

      for (BuildingInstance building : village.getBuildings()) {
         ConstructionTask task = building.getConstructionTask();
         if (task != null && !task.isComplete() && building.isBeingBuilt()) {
            BuildingPlanSet.LevelDef levelDef = getLevelDefForBuilding(building);
            if (levelDef != null) {
               items.addAll(levelDef.requiredResources().keySet());
            }
         }
      }

      return items;
   }

   public static int getAnywoodReservedQuantity(ServerLevel level, Village village, @Nullable Village.PendingProject excludeProject) {
      int reserved = 0;
      Village.PendingProject pending = village.getPendingProject();
      boolean pendingHandled = false;

      for (BuildingInstance building : village.getBuildings()) {
         ConstructionTask task = building.getConstructionTask();
         if (task != null && !task.isComplete() && building.isBeingBuilt()) {
            UUID builderUuid = task.getReservedBuilder();
            if (builderUuid == null) {
               if (pending != null
                  && !pendingHandled
                  && building.getPlanSetId() != null
                  && building.getPlanSetId().equals(pending.planSetId())
                  && pending.variant().equals(building.getVariant())
                  && pending.level() == building.getLevel()
                  && (!pending.isUpgrade() || Objects.equals(pending.buildingId(), building.getId()))) {
                  pendingHandled = true;
               }
            } else {
               BuildingPlanSet.LevelDef levelDef = getLevelDefForBuilding(building);
               if (levelDef != null) {
                  Map<ResourceLocation, Integer> resCost = levelDef.requiredResources();
                  Integer anywoodNeeded = resCost.get(AnywoodHelper.ANYWOOD_LOG);
                  if (anywoodNeeded != null && anywoodNeeded > 0) {
                     int builderHas = 0;
                     if (level.getEntity(builderUuid) instanceof MillVillager villager) {
                        builderHas = villager.getInventory().getCountByTag(AnywoodHelper.LOGS_TAG);
                     }

                     if (builderHas < anywoodNeeded) {
                        reserved += anywoodNeeded - builderHas;
                     }
                  }

                  if (pending != null
                     && !pendingHandled
                     && building.getPlanSetId() != null
                     && building.getPlanSetId().equals(pending.planSetId())
                     && pending.variant().equals(building.getVariant())
                     && pending.level() == building.getLevel()
                     && (!pending.isUpgrade() || Objects.equals(pending.buildingId(), building.getId()))) {
                     pendingHandled = true;
                  }
               }
            }
         }
      }

      if (pending != null && !pendingHandled && !pending.equals(excludeProject)) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         if (planSet != null) {
            String variant = pending.variant();
            int pendingLevel = pending.level();
            BuildingPlanSet.LevelDef pendingDef = planSet.getLevel(variant, pendingLevel);
            if (pendingDef != null) {
               Integer anywoodNeeded = pendingDef.requiredResources().get(AnywoodHelper.ANYWOOD_LOG);
               if (anywoodNeeded != null) {
                  reserved += anywoodNeeded;
               }
            }
         }
      }

      return reserved;
   }

   @Nullable
   private static BuildingPlanSet.LevelDef getLevelDefForBuilding(BuildingInstance building) {
      ResourceLocation planSetId = building.getPlanSetId();
      if (planSetId == null) {
         return null;
      }

      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
      if (planSet == null) {
         return null;
      }

      String variant = building.getVariant();
      return variant == null ? null : planSet.getLevel(variant, building.getLevel());
   }

   private static int getResidentNeeds(BuildingInstance building, Item item, Village village) {
      int needs = 0;

      for (VillagerRecord record : village.getVillagerRecords().values()) {
         BuildingId homeId = record.getHomeBuilding();
         if (homeId != null && homeId.equals(building.getId())) {
            VillagerType vtype = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (vtype != null) {
               Integer qty = vtype.resolvedRequiredGoods().get(item);
               if (qty != null) {
                  needs += qty;
               }
            }
         }
      }

      return needs;
   }
}
