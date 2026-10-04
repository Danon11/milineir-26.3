package org.millenaire.map;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.village.VillageId;

public class MillenaireMapMarkersData extends SavedData {
   private static final String DATA_NAME = "millenaire_map_markers";
   private final Map<Integer, Set<VillageId>> trackedByMap = new HashMap<>();

   MillenaireMapMarkersData() {
   }

   public static Factory<MillenaireMapMarkersData> factory() {
      return new Factory<>(MillenaireMapMarkersData::new, (tag, registries) -> load(tag), null);
   }

   public static MillenaireMapMarkersData get(ServerLevel level) {
      return (MillenaireMapMarkersData)level.getDataStorage().computeIfAbsent(factory(), "millenaire_map_markers");
   }

   public Set<VillageId> tracked(int mapId) {
      Set<VillageId> set = this.trackedByMap.get(mapId);
      return set != null ? Collections.unmodifiableSet(set) : Set.of();
   }

   public void addTracked(int mapId, VillageId villageId) {
      if (this.trackedByMap.computeIfAbsent(mapId, k -> new HashSet<>()).add(villageId)) {
         this.setDirty();
      }
   }

   public void removeTracked(int mapId, VillageId villageId) {
      Set<VillageId> set = this.trackedByMap.get(mapId);
      if (set != null && set.remove(villageId)) {
         if (set.isEmpty()) {
            this.trackedByMap.remove(mapId);
         }

         this.setDirty();
      }
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      ListTag mapsList = new ListTag();

      for (Entry<Integer, Set<VillageId>> entry : this.trackedByMap.entrySet()) {
         CompoundTag mapTag = new CompoundTag();
         mapTag.putInt("map_id", entry.getKey());
         ListTag villageList = new ListTag();

         for (VillageId vid : entry.getValue()) {
            CompoundTag vTag = new CompoundTag();
            vTag.putUUID("village_id", vid.uuid());
            villageList.add(vTag);
         }

         mapTag.put("villages", villageList);
         mapsList.add(mapTag);
      }

      root.put("maps", mapsList);
      return root;
   }

   private static MillenaireMapMarkersData load(CompoundTag root) {
      MillenaireMapMarkersData result = new MillenaireMapMarkersData();
      ListTag mapsList = root.getList("maps", 10);

      for (int i = 0; i < mapsList.size(); i++) {
         CompoundTag mapTag = mapsList.getCompound(i);
         int mapId = mapTag.getInt("map_id");
         Set<VillageId> villages = new HashSet<>();
         ListTag villageList = mapTag.getList("villages", 10);

         for (int j = 0; j < villageList.size(); j++) {
            villages.add(new VillageId(villageList.getCompound(j).getUUID("village_id")));
         }

         if (!villages.isEmpty()) {
            result.trackedByMap.put(mapId, villages);
         }
      }

      return result;
   }

   public static MillenaireMapMarkersData createForTest() {
      return new MillenaireMapMarkersData();
   }

   public static MillenaireMapMarkersData loadForTest(CompoundTag tag) {
      return load(tag);
   }
}
