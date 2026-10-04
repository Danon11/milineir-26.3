package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.building.BedManager;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.ConstructionTask;
import org.millenaire.quest.MarvelManager;
import org.millenaire.world.PlacedLocation;
import org.slf4j.Logger;

public class VillageSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 4;
   private static final String DATA_NAME = "millenaire_villages";
   private final VillageManager villageManager;
   private final List<VillageSavedData.LoneBuildingEntry> loneBuildingPositions = new ArrayList<>();
   private final Set<String> globalTags = new HashSet<>();

   private VillageSavedData() {
      this.villageManager = new VillageManager();
   }

   public static Factory<VillageSavedData> factory() {
      return new Factory<>(VillageSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static VillageSavedData get(ServerLevel level) {
      return (VillageSavedData)level.getDataStorage().computeIfAbsent(factory(), "millenaire_villages");
   }

   public VillageManager getVillageManager() {
      return this.villageManager;
   }

   public List<VillageSavedData.LoneBuildingEntry> getLoneBuildingPositions() {
      return this.loneBuildingPositions;
   }

   public void registerLoneBuilding(BlockPos pos, ResourceLocation type, String culture, @Nullable String generatedFor) {
      for (VillageSavedData.LoneBuildingEntry entry : this.loneBuildingPositions) {
         if (entry.pos().equals(pos)) {
            return;
         }
      }

      this.loneBuildingPositions.add(new VillageSavedData.LoneBuildingEntry(pos, type, culture, generatedFor));
      this.setDirty();
   }

   public void removeLoneBuilding(BlockPos pos) {
      this.loneBuildingPositions.removeIf(entry -> entry.pos().equals(pos));
      this.setDirty();
   }

   public void setGlobalTag(String tag) {
      if (this.globalTags.add(tag)) {
         this.setDirty();
      }
   }

   public void clearGlobalTag(String tag) {
      if (this.globalTags.remove(tag)) {
         this.setDirty();
      }
   }

   public boolean isGlobalTagSet(String tag) {
      return this.globalTags.contains(tag);
   }

   public Set<String> getGlobalTags() {
      return Collections.unmodifiableSet(this.globalTags);
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      ListTag villagesList = new ListTag();

      for (Village village : this.villageManager.getAllVillages()) {
         CompoundTag villageTag = new CompoundTag();
         villageTag.putUUID("id", village.getId().uuid());
         villageTag.putString("culture", village.getCultureId().toString());
         villageTag.putString("village_type", village.getVillageTypeId().toString());
         villageTag.put("center", encodeBlockPos(village.getCenter()));
         if (village.getVillageName() != null) {
            villageTag.putString("name", village.getVillageName());
         }

         ListTag buildingsList = new ListTag();

         for (BuildingInstance b : village.getBuildings()) {
            CompoundTag buildingTag = new CompoundTag();
            buildingTag.putUUID("id", b.getId().uuid());
            buildingTag.putString("plan", b.getPlanId().toString());
            buildingTag.put("origin", encodeBlockPos(b.getOrigin()));
            buildingTag.putString("rotation", b.getRotation().name());
            buildingTag.putString("status", b.getStatus().name());
            if (b.getPlanSetId() != null) {
               buildingTag.putString("plan_set", b.getPlanSetId().toString());
            }

            if (b.getVariant() != null) {
               buildingTag.putString("variant", b.getVariant());
            }

            buildingTag.putInt("level", b.getLevel());
            if (b.isSubBuilding()) {
               buildingTag.putBoolean("sub_building", true);
            }

            if (!b.isUpgradesAllowed()) {
               buildingTag.putBoolean("upgrades_allowed", false);
            }

            if (b.getBrickColourMapping() != null) {
               CompoundTag brickTag = new CompoundTag();

               for (Entry<DyeColor, DyeColor> entry : b.getBrickColourMapping().entrySet()) {
                  brickTag.putString(entry.getKey().getSerializedName(), entry.getValue().getSerializedName());
               }

               buildingTag.put("brick_colours", brickTag);
            }

            if (b.hasBedManager()) {
               buildingTag.put("beds", b.getBedManager().save());
            }

            if (b.getConstructionTask() != null) {
               buildingTag.put("construction_task", b.getConstructionTask().save());
            }

            if (b.getNbNightsMerchant() > 0) {
               buildingTag.putInt("nb_nights_merchant", b.getNbNightsMerchant());
            }

            if (!b.getImported().isEmpty()) {
               CompoundTag importTag = new CompoundTag();

               for (Entry<Item, Integer> entry : b.getImported().entrySet()) {
                  String itemId = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                  importTag.putInt(itemId, entry.getValue());
               }

               buildingTag.put("imported", importTag);
            }

            if (!b.getExported().isEmpty()) {
               CompoundTag exportTag = new CompoundTag();

               for (Entry<Item, Integer> entry : b.getExported().entrySet()) {
                  String itemId = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                  exportTag.putInt(itemId, entry.getValue());
               }

               buildingTag.put("exported", exportTag);
            }

            if (!b.getVisitorLog().isEmpty()) {
               ListTag logList = new ListTag();

               for (String entry : b.getVisitorLog()) {
                  CompoundTag entryTag = new CompoundTag();
                  entryTag.putString("msg", entry);
                  logList.add(entryTag);
               }

               buildingTag.put("visitor_log", logList);
            }

            if (b.getLastMarketNightDay() >= 0L) {
               buildingTag.putLong("last_market_night_day", b.getLastMarketNightDay());
            }

            if (!b.getRuntimeTags().isEmpty()) {
               ListTag runtimeTagsList = new ListTag();

               for (String tag : b.getRuntimeTags()) {
                  runtimeTagsList.add(StringTag.valueOf(tag));
               }

               buildingTag.put("runtime_tags", runtimeTagsList);
            }

            if (b.getParentBuildingId() != null) {
               buildingTag.putUUID("parent_building_id", b.getParentBuildingId().uuid());
            }

            buildingsList.add(buildingTag);
         }

         villageTag.put("buildings", buildingsList);
         ListTag villagersList = new ListTag();

         for (Entry<UUID, VillagerRecord> entry : village.getVillagerRecords().entrySet()) {
            CompoundTag villagerTag = new CompoundTag();
            entry.getValue().save(villagerTag);
            villagersList.add(villagerTag);
         }

         villageTag.put("villagers", villagersList);
         CompoundTag reputationTag = new CompoundTag();
         village.getReputation().save(reputationTag);
         villageTag.put("reputation", reputationTag);
         villageTag.putLong("lastGoodsRefresh", village.getLastGoodsRefresh());
         villageTag.putBoolean("chestLocked", village.areChestsLocked());
         Village.PendingProject pending = village.getPendingProject();
         if (pending != null) {
            CompoundTag pendingTag = new CompoundTag();
            pendingTag.putString("plan_set", pending.planSetId().toString());
            pendingTag.putString("variant", pending.variant());
            pendingTag.putInt("level", pending.level());
            pendingTag.putBoolean("is_upgrade", pending.isUpgrade());
            if (pending.buildingId() != null) {
               pendingTag.putUUID("building_id", pending.buildingId().uuid());
            }

            if (pending.plannedLocation() != null) {
               PlacedLocation planned = pending.plannedLocation();
               pendingTag.putInt("planned_x", planned.position().getX());
               pendingTag.putInt("planned_y", planned.position().getY());
               pendingTag.putInt("planned_z", planned.position().getZ());
               pendingTag.putInt("planned_rotation", planned.rotation().ordinal());
            }

            villageTag.put("pending_project", pendingTag);
         }

         if (village.getBrickThemeName() != null) {
            villageTag.putString("brick_theme", village.getBrickThemeName());
         }

         if (!village.getBuildingsBought().isEmpty()) {
            ListTag boughtList = new ListTag();

            for (ResourceLocation id : village.getBuildingsBought()) {
               CompoundTag entry = new CompoundTag();
               entry.putString("id", id.toString());
               boughtList.add(entry);
            }

            villageTag.put("bought_buildings", boughtList);
         }

         CompoundTag pathsTag = new CompoundTag();
         village.getPathManager().save(pathsTag);
         villageTag.put("paths", pathsTag);
         villageTag.putLong("last_night_action_day", village.getLastNightActionDay());
         if (village.getParentVillageId() != null) {
            villageTag.putUUID("parent_village_id", village.getParentVillageId().uuid());
         }

         if (village.getOwnerUUID() != null) {
            villageTag.putUUID("owner_uuid", village.getOwnerUUID());
            if (village.getOwnerName() != null) {
               villageTag.putString("owner_name", village.getOwnerName());
            }
         }

         if (village.getBannerNbt() != null) {
            villageTag.putString("banner_nbt", village.getBannerNbt());
         }

         if (!village.getRelations().isEmpty()) {
            ListTag relationsList = new ListTag();

            for (Entry<VillageId, Integer> entry : village.getRelations().entrySet()) {
               CompoundTag relTag = new CompoundTag();
               relTag.putUUID("village_id", entry.getKey().uuid());
               relTag.putInt("value", entry.getValue());
               relationsList.add(relTag);
            }

            villageTag.put("relations", relationsList);
         }

         if (village.getMarvelManager() != null) {
            villageTag.put("marvel", village.getMarvelManager().save());
         }

         if (!village.getChronicle().isEmpty()) {
            ListTag chronicleList = new ListTag();

            for (VillageEvent event : village.getChronicle()) {
               CompoundTag eventTag = new CompoundTag();
               eventTag.putLong("time", event.gameTime());
               eventTag.putString("type", event.type().name());
               eventTag.putString("p1", event.param1());
               if (event.param2() != null) {
                  eventTag.putString("p2", event.param2());
               }

               chronicleList.add(eventTag);
            }

            villageTag.put("chronicle", chronicleList);
         }

         villagesList.add(villageTag);
      }

      root.putInt("version", 4);
      root.put("villages", villagesList);
      ListTag lbList = new ListTag();

      for (VillageSavedData.LoneBuildingEntry entry : this.loneBuildingPositions) {
         CompoundTag lbTag = new CompoundTag();
         lbTag.put("pos", encodeBlockPos(entry.pos()));
         lbTag.putString("type", entry.type().toString());
         lbTag.putString("culture", entry.culture());
         if (entry.generatedFor() != null) {
            lbTag.putString("generated_for", entry.generatedFor());
         }

         lbList.add(lbTag);
      }

      root.put("lone_buildings", lbList);
      if (!this.globalTags.isEmpty()) {
         ListTag globalTagsList = new ListTag();

         for (String tag : this.globalTags) {
            globalTagsList.add(StringTag.valueOf(tag));
         }

         root.put("globalTags", globalTagsList);
      }

      return root;
   }

   static VillageSavedData load(CompoundTag root) {
      VillageSavedData data = new VillageSavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      LOGGER.info("Loading VillageSavedData version {}", version);
      ListTag villagesList = root.getList("villages", 10);

      for (int i = 0; i < villagesList.size(); i++) {
         try {
            CompoundTag villageTag = villagesList.getCompound(i);
            VillageId villageId = new VillageId(villageTag.getUUID("id"));
            ResourceLocation cultureId = ResourceLocation.parse(villageTag.getString("culture"));
            ResourceLocation villageTypeId = migrateVillageTypeId(ResourceLocation.parse(villageTag.getString("village_type")));
            BlockPos center = decodeBlockPos(villageTag.getIntArray("center"));
            Village village = new Village(villageId, cultureId, villageTypeId, center);
            if (villageTag.contains("name")) {
               village.setVillageName(villageTag.getString("name"));
            }

            ListTag buildingsList = villageTag.getList("buildings", 10);

            for (int j = 0; j < buildingsList.size(); j++) {
               try {
                  CompoundTag buildingTag = buildingsList.getCompound(j);
                  BuildingId buildingId = new BuildingId(buildingTag.getUUID("id"));
                  ResourceLocation planId = ResourceLocation.parse(buildingTag.getString("plan"));
                  BlockPos origin = decodeBlockPos(buildingTag.getIntArray("origin"));
                  Rotation rotation = Rotation.valueOf(buildingTag.getString("rotation"));
                  BuildingInstance.Status status = BuildingInstance.Status.valueOf(buildingTag.getString("status"));
                  ResourceLocation planSetId = buildingTag.contains("plan_set") ? ResourceLocation.parse(buildingTag.getString("plan_set")) : null;
                  String variant = buildingTag.contains("variant") ? buildingTag.getString("variant") : null;
                  int level = buildingTag.getInt("level");
                  BuildingInstance building = new BuildingInstance(buildingId, planId, origin, rotation, status, planSetId, variant, level);
                  if (buildingTag.getBoolean("sub_building")) {
                     building.setSubBuilding(true);
                  }

                  if (buildingTag.contains("upgrades_allowed")) {
                     building.setUpgradesAllowed(buildingTag.getBoolean("upgrades_allowed"));
                  }

                  if (buildingTag.contains("brick_colours")) {
                     CompoundTag brickTag = buildingTag.getCompound("brick_colours");
                     Map<DyeColor, DyeColor> mapping = new EnumMap<>(DyeColor.class);

                     for (String key : brickTag.getAllKeys()) {
                        DyeColor from = dyeColorFromName(key);
                        DyeColor to = dyeColorFromName(brickTag.getString(key));
                        if (from != null && to != null) {
                           mapping.put(from, to);
                        }
                     }

                     building.setBrickColourMapping(mapping);
                  }

                  if (buildingTag.contains("beds", 9)) {
                     BedManager loaded = BedManager.load(buildingTag.getList("beds", 10));
                     building.setBedManager(loaded);
                     building.setBedManagerInitialized(true);
                  }

                  if (buildingTag.contains("construction_task")) {
                     building.setConstructionTask(ConstructionTask.load(buildingTag.getCompound("construction_task"), BuiltInRegistries.BLOCK.asLookup()));
                  }

                  if (buildingTag.contains("nb_nights_merchant")) {
                     building.setNbNightsMerchant(buildingTag.getInt("nb_nights_merchant"));
                  }

                  if (buildingTag.contains("imported")) {
                     CompoundTag importTag = buildingTag.getCompound("imported");
                     Map<Item, Integer> importMap = new LinkedHashMap<>();

                     for (String itemId : importTag.getAllKeys()) {
                        Item item = (Item)BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
                        if (item != null) {
                           importMap.put(item, importTag.getInt(itemId));
                        }
                     }

                     building.setImported(importMap);
                  }

                  if (buildingTag.contains("exported")) {
                     CompoundTag exportTag = buildingTag.getCompound("exported");
                     Map<Item, Integer> exportMap = new LinkedHashMap<>();

                     for (String itemId : exportTag.getAllKeys()) {
                        Item item = (Item)BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
                        if (item != null) {
                           exportMap.put(item, exportTag.getInt(itemId));
                        }
                     }

                     building.setExported(exportMap);
                  }

                  if (buildingTag.contains("visitor_log", 9)) {
                     ListTag logList = buildingTag.getList("visitor_log", 10);
                     List<String> entries = new ArrayList<>();

                     for (int k = 0; k < logList.size(); k++) {
                        entries.add(logList.getCompound(k).getString("msg"));
                     }

                     building.setVisitorLog(entries);
                  }

                  if (buildingTag.contains("last_market_night_day")) {
                     building.setLastMarketNightDay(buildingTag.getLong("last_market_night_day"));
                  }

                  if (buildingTag.contains("runtime_tags", 9)) {
                     ListTag runtimeTagsList = buildingTag.getList("runtime_tags", 8);
                     List<String> tags = new ArrayList<>();

                     for (int k = 0; k < runtimeTagsList.size(); k++) {
                        tags.add(runtimeTagsList.getString(k));
                     }

                     building.addRuntimeTags(tags);
                  }

                  if (buildingTag.contains("parent_building_id")) {
                     building.setParentBuildingId(new BuildingId(buildingTag.getUUID("parent_building_id")));
                  }

                  village.addBuilding(building);
               } catch (Exception e) {
                  LOGGER.error("Error loading building index {} of village {} — building ignored", new Object[]{j, i, e});
               }
            }

            ListTag villagersList = villageTag.getList("villagers", 10);

            for (int j = 0; j < villagersList.size(); j++) {
               CompoundTag villagerTag = villagersList.getCompound(j);
               if (version >= 2) {
                  VillagerRecord record = VillagerRecord.load(villagerTag);
                  if (record != null) {
                     village.addVillager(record);
                  }
               } else {
                  UUID uuid = villagerTag.getUUID("uuid");
                  ResourceLocation typeId = villagerTag.contains("type")
                     ? ResourceLocation.parse(villagerTag.getString("type"))
                     : ResourceLocation.withDefaultNamespace("unknown");
                  BuildingId home = villagerTag.hasUUID("home") ? new BuildingId(villagerTag.getUUID("home")) : null;
                  village.addVillager(uuid, typeId, home);
               }
            }

            if (villageTag.contains("reputation")) {
               village.getReputation().load(villageTag.getCompound("reputation"));
            }

            if (villageTag.contains("lastGoodsRefresh")) {
               village.setLastGoodsRefresh(villageTag.getLong("lastGoodsRefresh"));
            }

            if (villageTag.contains("chestLocked")) {
               village.setChestLocked(villageTag.getBoolean("chestLocked"));
            }

            if (villageTag.contains("pending_project")) {
               try {
                  CompoundTag pendingTag = villageTag.getCompound("pending_project");
                  ResourceLocation pendingPlanSetId = ResourceLocation.parse(pendingTag.getString("plan_set"));
                  String pendingVariant = pendingTag.getString("variant");
                  int pendingLevel = pendingTag.getInt("level");
                  boolean pendingIsUpgrade = pendingTag.getBoolean("is_upgrade");
                  BuildingId pendingBuildingId = pendingTag.hasUUID("building_id") ? new BuildingId(pendingTag.getUUID("building_id")) : null;
                  PlacedLocation pendingPlanned = null;
                  if (pendingTag.contains("planned_x")) {
                     BlockPos plannedPos = new BlockPos(pendingTag.getInt("planned_x"), pendingTag.getInt("planned_y"), pendingTag.getInt("planned_z"));
                     int rotOrd = Math.floorMod(pendingTag.getInt("planned_rotation"), Rotation.values().length);
                     pendingPlanned = new PlacedLocation(plannedPos, Rotation.values()[rotOrd]);
                  }

                  village.setPendingProject(
                     new Village.PendingProject(pendingPlanSetId, pendingVariant, pendingLevel, pendingIsUpgrade, pendingBuildingId, pendingPlanned)
                  );
               } catch (Exception e) {
                  LOGGER.error("Error loading pending_project of village {} — project ignored", i, e);
               }
            }

            if (villageTag.contains("brick_theme")) {
               village.setRawBrickThemeName(villageTag.getString("brick_theme"));
            }

            if (villageTag.contains("bought_buildings")) {
               ListTag boughtList = villageTag.getList("bought_buildings", 10);
               Set<ResourceLocation> bought = new HashSet<>();

               for (int j = 0; j < boughtList.size(); j++) {
                  CompoundTag entry = boughtList.getCompound(j);
                  bought.add(ResourceLocation.parse(entry.getString("id")));
               }

               village.loadBuildingsBought(bought);
            }

            if (villageTag.contains("paths")) {
               village.getPathManager().load(villageTag.getCompound("paths"));
            }

            if (villageTag.contains("last_night_action_day")) {
               village.setLastNightActionDay(villageTag.getLong("last_night_action_day"));
            }

            if (villageTag.contains("parent_village_id")) {
               village.setParentVillageId(new VillageId(villageTag.getUUID("parent_village_id")));
            }

            if (villageTag.contains("owner_uuid")) {
               UUID ownerUUID = villageTag.getUUID("owner_uuid");
               String ownerName = villageTag.contains("owner_name") ? villageTag.getString("owner_name") : null;
               village.setOwner(ownerUUID, ownerName);
            }

            if (villageTag.contains("banner_nbt")) {
               village.loadBannerNbt(villageTag.getString("banner_nbt"));
            }

            if (villageTag.contains("relations")) {
               ListTag relationsList = villageTag.getList("relations", 10);
               Map<VillageId, Integer> loaded = new HashMap<>();

               for (int r = 0; r < relationsList.size(); r++) {
                  CompoundTag relTag = relationsList.getCompound(r);
                  VillageId otherId = new VillageId(relTag.getUUID("village_id"));
                  int value = relTag.getInt("value");
                  loaded.put(otherId, value);
               }

               village.setRelationsFromNbt(loaded);
            }

            if (villageTag.contains("chronicle")) {
               ListTag chronicleList = villageTag.getList("chronicle", 10);

               for (int c = 0; c < chronicleList.size(); c++) {
                  CompoundTag eventTag = chronicleList.getCompound(c);
                  long gameTime = eventTag.getLong("time");

                  VillageEventType type;
                  try {
                     type = VillageEventType.valueOf(eventTag.getString("type"));
                  } catch (IllegalArgumentException e) {
                     LOGGER.warn("Unknown chronicle event type: {}", eventTag.getString("type"));
                     continue;
                  }

                  String p1 = eventTag.getString("p1");
                  String p2 = eventTag.contains("p2") ? eventTag.getString("p2") : null;
                  village.addChronicleEventDirect(new VillageEvent(gameTime, type, p1, p2));
               }
            }

            if (villageTag.contains("marvel")) {
               MarvelManager mm = new MarvelManager();
               mm.load(villageTag.getCompound("marvel"));
               village.setMarvelManager(mm);
            }

            data.villageManager.addVillage(village);
         } catch (Exception e) {
            LOGGER.error("Error loading village index {} — village ignored", i, e);
         }
      }

      if (root.contains("lone_buildings")) {
         ListTag lbList = root.getList("lone_buildings", 10);

         for (int i = 0; i < lbList.size(); i++) {
            try {
               CompoundTag lbTag = lbList.getCompound(i);
               BlockPos pos = decodeBlockPos(lbTag.getIntArray("pos"));
               ResourceLocation type = ResourceLocation.parse(lbTag.getString("type"));
               String culture = lbTag.getString("culture");
               String generatedFor = lbTag.contains("generated_for") ? lbTag.getString("generated_for") : null;
               data.loneBuildingPositions.add(new VillageSavedData.LoneBuildingEntry(pos, type, culture, generatedFor));
            } catch (Exception e) {
               LOGGER.error("Error loading lone building index {} — ignored", i, e);
            }
         }
      }

      if (root.contains("globalTags")) {
         ListTag globalTagsList = root.getList("globalTags", 8);

         for (int i = 0; i < globalTagsList.size(); i++) {
            data.globalTags.add(globalTagsList.getString(i));
         }
      }

      return data;
   }

   private static IntArrayTag encodeBlockPos(BlockPos pos) {
      return new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()});
   }

   private static BlockPos decodeBlockPos(int[] arr) {
      if (arr.length != 3) {
         throw new IllegalArgumentException("Expected BlockPos as int[3], received int[" + arr.length + "]");
      } else {
         return new BlockPos(arr[0], arr[1], arr[2]);
      }
   }

   private static ResourceLocation migrateVillageTypeId(ResourceLocation id) {
      String path = id.getPath();
      if (!path.contains("/")) {
         int idx = path.indexOf(95);
         if (idx > 0) {
            String migrated = path.substring(0, idx) + "/" + path.substring(idx + 1);
            LOGGER.info("Migrating village type ID: {} -> {}", id, migrated);
            return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), migrated);
         }
      }

      return id;
   }

   private static DyeColor dyeColorFromName(String name) {
      for (DyeColor c : DyeColor.values()) {
         if (c.getSerializedName().equals(name)) {
            return c;
         }
      }

      return null;
   }

   public record LoneBuildingEntry(BlockPos pos, ResourceLocation type, String culture, @Nullable String generatedFor) {
   }
}
