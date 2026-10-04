package org.millenaire.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public class DiscoveryTracker extends SavedData {
   private static final String DATA_NAME = "millenaire_discovery";
   private final Map<UUID, DiscoveryTracker.DiscoveryData> data = new HashMap<>();

   DiscoveryTracker() {
   }

   public static Factory<DiscoveryTracker> factory() {
      return new Factory<>(DiscoveryTracker::new, (tag, registries) -> load(tag), null);
   }

   public static DiscoveryTracker get(ServerLevel level) {
      return (DiscoveryTracker)level.getDataStorage().computeIfAbsent(factory(), "millenaire_discovery");
   }

   public boolean unlockVillager(UUID player, String cultureKey, String villagerKey) {
      return this.getOrCreate(player).unlockedVillagers.add(composeKey(cultureKey, villagerKey)) && this.markDirty();
   }

   public boolean unlockBuilding(UUID player, String cultureKey, String buildingKey) {
      return this.getOrCreate(player).unlockedBuildings.add(composeKey(cultureKey, buildingKey)) && this.markDirty();
   }

   public boolean unlockVillage(UUID player, String cultureKey, String villageKey) {
      return this.getOrCreate(player).unlockedVillages.add(composeKey(cultureKey, villageKey)) && this.markDirty();
   }

   public boolean unlockTradeGood(UUID player, String cultureKey, String goodKey) {
      return this.getOrCreate(player).unlockedTradeGoods.add(composeKey(cultureKey, goodKey)) && this.markDirty();
   }

   public List<Boolean> unlockTradeGoods(UUID player, String cultureKey, List<String> goodKeys) {
      DiscoveryTracker.DiscoveryData dd = this.getOrCreate(player);
      boolean anyNew = false;
      List<Boolean> results = new ArrayList<>(goodKeys.size());

      for (String key : goodKeys) {
         boolean added = dd.unlockedTradeGoods.add(composeKey(cultureKey, key));
         results.add(added);
         anyNew = anyNew || added;
      }

      if (anyNew) {
         this.setDirty();
      }

      return results;
   }

   public boolean isVillagerUnlocked(UUID player, String cultureKey, String key) {
      DiscoveryTracker.DiscoveryData dd = this.data.get(player);
      return dd != null && dd.unlockedVillagers.contains(composeKey(cultureKey, key));
   }

   public boolean isBuildingUnlocked(UUID player, String cultureKey, String key) {
      DiscoveryTracker.DiscoveryData dd = this.data.get(player);
      return dd != null && dd.unlockedBuildings.contains(composeKey(cultureKey, key));
   }

   public boolean isVillageUnlocked(UUID player, String cultureKey, String key) {
      DiscoveryTracker.DiscoveryData dd = this.data.get(player);
      return dd != null && dd.unlockedVillages.contains(composeKey(cultureKey, key));
   }

   public boolean isTradeGoodUnlocked(UUID player, String cultureKey, String key) {
      DiscoveryTracker.DiscoveryData dd = this.data.get(player);
      return dd != null && dd.unlockedTradeGoods.contains(composeKey(cultureKey, key));
   }

   public Set<String> getUnlockedVillagers(UUID player) {
      DiscoveryTracker.DiscoveryData dd = this.data.get(player);
      return dd != null ? Collections.unmodifiableSet(dd.unlockedVillagers) : Set.of();
   }

   public Set<String> getUnlockedBuildings(UUID player) {
      DiscoveryTracker.DiscoveryData dd = this.data.get(player);
      return dd != null ? Collections.unmodifiableSet(dd.unlockedBuildings) : Set.of();
   }

   public Set<String> getUnlockedVillages(UUID player) {
      DiscoveryTracker.DiscoveryData dd = this.data.get(player);
      return dd != null ? Collections.unmodifiableSet(dd.unlockedVillages) : Set.of();
   }

   public Set<String> getUnlockedTradeGoods(UUID player) {
      DiscoveryTracker.DiscoveryData dd = this.data.get(player);
      return dd != null ? Collections.unmodifiableSet(dd.unlockedTradeGoods) : Set.of();
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      ListTag playersList = new ListTag();

      for (Entry<UUID, DiscoveryTracker.DiscoveryData> entry : this.data.entrySet()) {
         CompoundTag playerTag = new CompoundTag();
         playerTag.putUUID("player", entry.getKey());
         DiscoveryTracker.DiscoveryData dd = entry.getValue();
         saveStringSet(playerTag, "villagers", dd.unlockedVillagers);
         saveStringSet(playerTag, "buildings", dd.unlockedBuildings);
         saveStringSet(playerTag, "villages", dd.unlockedVillages);
         saveStringSet(playerTag, "trade_goods", dd.unlockedTradeGoods);
         playersList.add(playerTag);
      }

      root.put("players", playersList);
      return root;
   }

   private static DiscoveryTracker load(CompoundTag root) {
      DiscoveryTracker result = new DiscoveryTracker();
      ListTag playersList = root.getList("players", 10);

      for (int i = 0; i < playersList.size(); i++) {
         CompoundTag playerTag = playersList.getCompound(i);
         UUID playerId = playerTag.getUUID("player");
         DiscoveryTracker.DiscoveryData dd = new DiscoveryTracker.DiscoveryData();
         loadStringSet(playerTag, "villagers", dd.unlockedVillagers);
         loadStringSet(playerTag, "buildings", dd.unlockedBuildings);
         loadStringSet(playerTag, "villages", dd.unlockedVillages);
         loadStringSet(playerTag, "trade_goods", dd.unlockedTradeGoods);
         result.data.put(playerId, dd);
      }

      return result;
   }

   private DiscoveryTracker.DiscoveryData getOrCreate(UUID player) {
      return this.data.computeIfAbsent(player, k -> new DiscoveryTracker.DiscoveryData());
   }

   static String composeKey(String cultureKey, String itemKey) {
      return cultureKey + "_" + itemKey;
   }

   private boolean markDirty() {
      this.setDirty();
      return true;
   }

   private static void saveStringSet(CompoundTag parent, String name, Set<String> set) {
      if (!set.isEmpty()) {
         ListTag list = new ListTag();

         for (String s : set) {
            list.add(StringTag.valueOf(s));
         }

         parent.put(name, list);
      }
   }

   private static void loadStringSet(CompoundTag parent, String name, Set<String> target) {
      if (parent.contains(name)) {
         ListTag list = parent.getList(name, 8);

         for (int i = 0; i < list.size(); i++) {
            target.add(list.getString(i));
         }
      }
   }

   private static class DiscoveryData {
      final Set<String> unlockedVillagers = new HashSet<>();
      final Set<String> unlockedBuildings = new HashSet<>();
      final Set<String> unlockedVillages = new HashSet<>();
      final Set<String> unlockedTradeGoods = new HashSet<>();
   }
}
