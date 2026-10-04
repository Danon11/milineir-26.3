package org.millenaire.culture;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;

public final class ModCultures {
   private static final Map<ResourceLocation, Culture> cultures = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, BuildingPlan> buildingPlans = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, BuildingPlanSet> buildingPlanSets = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, VillagerType> villagerTypes = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, VillageType> villageTypes = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, List<ReputationLabel>> reputationLabels = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, List<ReputationLabel>> cultureReputationLabels = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, NameLists> nameLists = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, WallType> wallTypes = new ConcurrentHashMap<>();

   private ModCultures() {
   }

   @Nullable
   public static Culture getCulture(ResourceLocation id) {
      return cultures.get(id);
   }

   @Nullable
   public static BuildingPlan getBuildingPlan(ResourceLocation id) {
      return id == null ? null : buildingPlans.get(id);
   }

   @Nullable
   public static BuildingPlanSet getBuildingPlanSet(ResourceLocation id) {
      return id == null ? null : buildingPlanSets.get(id);
   }

   public static Map<ResourceLocation, BuildingPlanSet> getAllBuildingPlanSets() {
      return Collections.unmodifiableMap(buildingPlanSets);
   }

   @Nullable
   public static VillagerType getVillagerType(ResourceLocation id) {
      return villagerTypes.get(id);
   }

   @Nullable
   public static VillageType getVillageType(ResourceLocation id) {
      return villageTypes.get(id);
   }

   public static Map<ResourceLocation, Culture> getAllCultures() {
      return Collections.unmodifiableMap(cultures);
   }

   public static Map<ResourceLocation, BuildingPlan> getAllBuildingPlans() {
      return Collections.unmodifiableMap(buildingPlans);
   }

   public static Map<ResourceLocation, VillagerType> getAllVillagerTypes() {
      return Collections.unmodifiableMap(villagerTypes);
   }

   public static Map<ResourceLocation, VillageType> getAllVillageTypes() {
      return Collections.unmodifiableMap(villageTypes);
   }

   @Nullable
   public static WallType getWallType(ResourceLocation id) {
      return id == null ? null : wallTypes.get(id);
   }

   public static Map<ResourceLocation, WallType> getAllWallTypes() {
      return Collections.unmodifiableMap(wallTypes);
   }

   public static void registerWallType(WallType wallType) {
      wallTypes.put(wallType.id(), wallType);
   }

   @Nullable
   public static List<ReputationLabel> getReputationLabels(ResourceLocation cultureId) {
      return reputationLabels.get(cultureId);
   }

   @Nullable
   public static List<ReputationLabel> getCultureReputationLabels(ResourceLocation cultureId) {
      return cultureReputationLabels.get(cultureId);
   }

   @Nullable
   public static NameLists getNameLists(ResourceLocation cultureId) {
      return nameLists.get(cultureId);
   }

   public static ResourceLocation extractCultureId(ResourceLocation villagerTypeId) {
      String path = villagerTypeId.getPath();
      int slashIdx = path.indexOf(47);
      String culturePath = slashIdx > 0 ? path.substring(0, slashIdx) : path;
      return ResourceLocation.fromNamespaceAndPath(villagerTypeId.getNamespace(), culturePath);
   }

   public static void registerCulture(Culture culture) {
      cultures.put(culture.id(), culture);
   }

   public static void registerBuildingPlan(BuildingPlan plan) {
      buildingPlans.put(plan.id(), plan);
   }

   public static void registerBuildingPlanSet(BuildingPlanSet set) {
      buildingPlanSets.put(set.id(), set);
   }

   public static void registerVillagerType(VillagerType type) {
      villagerTypes.put(type.id(), type);
   }

   public static void registerVillageType(VillageType type) {
      villageTypes.put(type.id(), type);
   }

   public static void registerReputationLabels(ResourceLocation cultureId, List<ReputationLabel> labels) {
      reputationLabels.put(cultureId, labels);
   }

   public static void registerCultureReputationLabels(ResourceLocation cultureId, List<ReputationLabel> labels) {
      cultureReputationLabels.put(cultureId, labels);
   }

   public static void registerNameLists(ResourceLocation cultureId, NameLists lists) {
      nameLists.put(cultureId, lists);
   }

   public static void unregisterBuildingPlan(ResourceLocation planId) {
      if (planId != null) {
         buildingPlans.remove(planId);
      }
   }

   public static void unregisterCultureContent(ResourceLocation cultureId) {
      if (cultureId != null) {
         buildingPlans.values().removeIf(p -> cultureId.equals(p.culture()));
         buildingPlanSets.values().removeIf(s -> cultureId.equals(s.culture()));
         villagerTypes.values().removeIf(v -> cultureId.equals(v.culture()));
         villageTypes.values().removeIf(v -> cultureId.equals(v.culture()));
      }
   }

   public static void unregisterCulture(ResourceLocation cultureId) {
      if (cultureId != null) {
         unregisterCultureContent(cultureId);
         cultures.remove(cultureId);
         reputationLabels.remove(cultureId);
         cultureReputationLabels.remove(cultureId);
         nameLists.remove(cultureId);
      }
   }

   public static void clear() {
      cultures.clear();
      buildingPlans.clear();
      buildingPlanSets.clear();
      villagerTypes.clear();
      villageTypes.clear();
      wallTypes.clear();
      reputationLabels.clear();
      cultureReputationLabels.clear();
      nameLists.clear();
   }
}
