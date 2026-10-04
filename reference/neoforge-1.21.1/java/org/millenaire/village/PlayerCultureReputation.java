package org.millenaire.village;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.advancement.MillAdvancements;

public class PlayerCultureReputation extends SavedData {
   private static final String DATA_NAME = "millenaire_culture_reputation";
   public static final int CULTURE_MAX_REPUTATION = 4096;
   public static final int CULTURE_MIN_REPUTATION = -640;
   private final Map<UUID, Map<ResourceLocation, Integer>> data = new HashMap<>();
   private final Map<UUID, Boolean> donationMode = new HashMap<>();
   private final Map<UUID, Map<ResourceLocation, Integer>> languageData = new HashMap<>();
   private final Map<UUID, Set<ResourceLocation>> visitedCultures = new HashMap<>();
   private final Map<UUID, Set<String>> learnedCrops = new HashMap<>();
   private final Map<UUID, Set<String>> learnedHuntingDrops = new HashMap<>();
   private final Map<UUID, Set<ResourceLocation>> cultureControlTags = new HashMap<>();
   private final Map<UUID, Set<VillageId>> discoveredVillages = new HashMap<>();
   private final Map<UUID, Map<VillageId, Integer>> diplomacyPoints = new HashMap<>();
   public static final int MAX_DIPLOMACY_POINTS = 5;
   public static final int LANGUAGE_BEGINNER = 100;
   public static final int LANGUAGE_MODERATE = 200;
   public static final int LANGUAGE_FLUENT = 500;
   private final Set<UUID> pendingAttila = new HashSet<>();

   private PlayerCultureReputation() {
   }

   public static PlayerCultureReputation createForTest() {
      return new PlayerCultureReputation();
   }

   public static Factory<PlayerCultureReputation> factory() {
      return new Factory<>(PlayerCultureReputation::new, (tag, registries) -> load(tag), null);
   }

   public static PlayerCultureReputation get(ServerLevel level) {
      return (PlayerCultureReputation)level.getDataStorage().computeIfAbsent(factory(), "millenaire_culture_reputation");
   }

   public int get(UUID player, ResourceLocation cultureId) {
      Map<ResourceLocation, Integer> playerMap = this.data.get(player);
      return playerMap == null ? 0 : playerMap.getOrDefault(cultureId, 0);
   }

   public int countCulturesWithReputation(UUID player) {
      Map<ResourceLocation, Integer> playerMap = this.data.get(player);
      if (playerMap == null) {
         return 0;
      }

      int count = 0;

      for (int rep : playerMap.values()) {
         if (rep != 0) {
            count++;
         }
      }

      return count;
   }

   public boolean markCultureVisited(UUID player, ResourceLocation cultureId) {
      Set<ResourceLocation> visited = this.visitedCultures.computeIfAbsent(player, k -> new HashSet<>());
      if (visited.add(cultureId)) {
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public int countCulturesVisited(UUID player) {
      Set<ResourceLocation> visited = this.visitedCultures.get(player);
      return visited != null ? visited.size() : 0;
   }

   public void add(UUID player, ResourceLocation cultureId, int amount) {
      Map<ResourceLocation, Integer> playerMap = this.data.computeIfAbsent(player, k -> new HashMap<>());
      int current = playerMap.getOrDefault(cultureId, 0);
      int newValue = Math.max(-640, Math.min(4096, current + amount));
      playerMap.put(cultureId, newValue);
      this.setDirty();
      if (newValue <= -640) {
         int nbAwfulRep = 0;

         for (int rep : playerMap.values()) {
            if (rep <= -640) {
               nbAwfulRep++;
            }
         }

         if (nbAwfulRep >= 3) {
            this.pendingAttila.add(player);
         }
      }
   }

   public void checkPendingAttila(ServerPlayer player) {
      if (this.pendingAttila.remove(player.getUUID())) {
         MillAdvancements.grant(player, MillAdvancements.ATTILA);
      }
   }

   public boolean isDonationMode(UUID player) {
      return this.donationMode.getOrDefault(player, false);
   }

   public void setDonationMode(UUID player, boolean mode) {
      this.donationMode.put(player, mode);
      this.setDirty();
   }

   public int getLanguageKnowledge(UUID player, ResourceLocation cultureId) {
      Map<ResourceLocation, Integer> map = this.languageData.get(player);
      return map != null ? map.getOrDefault(cultureId, 0) : 0;
   }

   public void addLanguageKnowledge(UUID player, ResourceLocation cultureId, int items) {
      Map<ResourceLocation, Integer> map = this.languageData.computeIfAbsent(player, k -> new HashMap<>());
      int current = map.getOrDefault(cultureId, 0);
      map.put(cultureId, Math.min(current + items, 500));
      this.setDirty();
   }

   public PlayerCultureReputation.LanguageLevel getLanguageLevel(UUID player, ResourceLocation cultureId) {
      int score = this.getLanguageKnowledge(player, cultureId);
      if (score >= 500) {
         return PlayerCultureReputation.LanguageLevel.FLUENT;
      } else if (score >= 200) {
         return PlayerCultureReputation.LanguageLevel.MODERATE;
      } else {
         return score >= 100 ? PlayerCultureReputation.LanguageLevel.BEGINNER : PlayerCultureReputation.LanguageLevel.MINIMAL;
      }
   }

   public boolean hasLearnedCrop(UUID player, String cropKey) {
      Set<String> set = this.learnedCrops.get(player);
      return set != null && set.contains(cropKey);
   }

   public void learnCrop(UUID player, String cropKey) {
      this.learnedCrops.computeIfAbsent(player, k -> new HashSet<>()).add(cropKey);
      this.setDirty();
   }

   public boolean hasLearnedHuntingDrop(UUID player, String dropKey) {
      Set<String> set = this.learnedHuntingDrops.get(player);
      return set != null && set.contains(dropKey);
   }

   public void learnHuntingDrop(UUID player, String dropKey) {
      this.learnedHuntingDrops.computeIfAbsent(player, k -> new HashSet<>()).add(dropKey);
      this.setDirty();
   }

   public boolean hasCultureControl(UUID player, ResourceLocation cultureId) {
      Set<ResourceLocation> set = this.cultureControlTags.get(player);
      return set != null && set.contains(cultureId);
   }

   public void grantCultureControl(UUID player, ResourceLocation cultureId) {
      this.cultureControlTags.computeIfAbsent(player, k -> new HashSet<>()).add(cultureId);
      this.setDirty();
   }

   public void revokeCultureControl(UUID player, ResourceLocation cultureId) {
      Set<ResourceLocation> set = this.cultureControlTags.get(player);
      if (set != null) {
         set.remove(cultureId);
         this.setDirty();
      }
   }

   public boolean hasDiscoveredVillage(UUID player, VillageId villageId) {
      Set<VillageId> set = this.discoveredVillages.get(player);
      return set != null && set.contains(villageId);
   }

   public boolean markVillageDiscovered(UUID player, VillageId villageId) {
      boolean added = this.discoveredVillages.computeIfAbsent(player, k -> new HashSet<>()).add(villageId);
      if (added) {
         this.setDirty();
      }

      return added;
   }

   public Set<VillageId> getDiscoveredVillages(UUID player) {
      Set<VillageId> set = this.discoveredVillages.get(player);
      return set != null ? Collections.unmodifiableSet(set) : Set.of();
   }

   public int getDiplomacyPoints(UUID player, VillageId villageId) {
      Map<VillageId, Integer> map = this.diplomacyPoints.get(player);
      return map != null ? map.getOrDefault(villageId, 0) : 0;
   }

   public boolean consumeDiplomacyPoint(UUID player, VillageId villageId) {
      Map<VillageId, Integer> map = this.diplomacyPoints.get(player);
      if (map == null) {
         return false;
      }

      int current = map.getOrDefault(villageId, 0);
      if (current <= 0) {
         return false;
      }

      map.put(villageId, current - 1);
      this.setDirty();
      return true;
   }

   public void regenerateDiplomacyPoints(UUID player, VillageId villageId) {
      Map<VillageId, Integer> map = this.diplomacyPoints.computeIfAbsent(player, k -> new HashMap<>());
      map.put(villageId, 5);
      this.setDirty();
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      Set<UUID> allPlayers = new HashSet<>();
      allPlayers.addAll(this.data.keySet());
      allPlayers.addAll(this.donationMode.keySet());
      allPlayers.addAll(this.languageData.keySet());
      allPlayers.addAll(this.visitedCultures.keySet());
      allPlayers.addAll(this.diplomacyPoints.keySet());
      allPlayers.addAll(this.learnedCrops.keySet());
      allPlayers.addAll(this.learnedHuntingDrops.keySet());
      allPlayers.addAll(this.cultureControlTags.keySet());
      allPlayers.addAll(this.discoveredVillages.keySet());
      ListTag playersList = new ListTag();

      for (UUID playerId : allPlayers) {
         CompoundTag playerTag = new CompoundTag();
         playerTag.putUUID("player", playerId);
         Map<ResourceLocation, Integer> cultureMap = this.data.get(playerId);
         if (cultureMap != null) {
            ListTag culturesList = new ListTag();

            for (Entry<ResourceLocation, Integer> cultureEntry : cultureMap.entrySet()) {
               CompoundTag cultureTag = new CompoundTag();
               cultureTag.putString("culture", cultureEntry.getKey().toString());
               cultureTag.putInt("value", cultureEntry.getValue());
               culturesList.add(cultureTag);
            }

            playerTag.put("cultures", culturesList);
         }

         if (this.donationMode.getOrDefault(playerId, false)) {
            playerTag.putBoolean("donation_mode", true);
         }

         Map<ResourceLocation, Integer> langMap = this.languageData.get(playerId);
         if (langMap != null && !langMap.isEmpty()) {
            ListTag langList = new ListTag();

            for (Entry<ResourceLocation, Integer> langEntry : langMap.entrySet()) {
               CompoundTag langTag = new CompoundTag();
               langTag.putString("culture", langEntry.getKey().toString());
               langTag.putInt("score", langEntry.getValue());
               langList.add(langTag);
            }

            playerTag.put("languages", langList);
         }

         Set<ResourceLocation> visited = this.visitedCultures.get(playerId);
         if (visited != null && !visited.isEmpty()) {
            ListTag visitedList = new ListTag();

            for (ResourceLocation culture : visited) {
               CompoundTag vTag = new CompoundTag();
               vTag.putString("culture", culture.toString());
               visitedList.add(vTag);
            }

            playerTag.put("visited_cultures", visitedList);
         }

         Map<VillageId, Integer> dpMap = this.diplomacyPoints.get(playerId);
         if (dpMap != null && !dpMap.isEmpty()) {
            ListTag dpList = new ListTag();

            for (Entry<VillageId, Integer> dpEntry : dpMap.entrySet()) {
               CompoundTag dpTag = new CompoundTag();
               dpTag.putUUID("village_id", dpEntry.getKey().uuid());
               dpTag.putInt("points", dpEntry.getValue());
               dpList.add(dpTag);
            }

            playerTag.put("diplomacy_points", dpList);
         }

         Set<String> crops = this.learnedCrops.get(playerId);
         if (crops != null && !crops.isEmpty()) {
            ListTag cropList = new ListTag();

            for (String crop : crops) {
               CompoundTag cTag = new CompoundTag();
               cTag.putString("key", crop);
               cropList.add(cTag);
            }

            playerTag.put("learned_crops", cropList);
         }

         Set<String> drops = this.learnedHuntingDrops.get(playerId);
         if (drops != null && !drops.isEmpty()) {
            ListTag dropList = new ListTag();

            for (String drop : drops) {
               CompoundTag dTag = new CompoundTag();
               dTag.putString("key", drop);
               dropList.add(dTag);
            }

            playerTag.put("learned_hunting_drops", dropList);
         }

         Set<ResourceLocation> controls = this.cultureControlTags.get(playerId);
         if (controls != null && !controls.isEmpty()) {
            ListTag controlList = new ListTag();

            for (ResourceLocation ctrl : controls) {
               CompoundTag ctTag = new CompoundTag();
               ctTag.putString("culture", ctrl.toString());
               controlList.add(ctTag);
            }

            playerTag.put("culture_control", controlList);
         }

         Set<VillageId> discovered = this.discoveredVillages.get(playerId);
         if (discovered != null && !discovered.isEmpty()) {
            ListTag discList = new ListTag();

            for (VillageId vid : discovered) {
               CompoundTag dTag = new CompoundTag();
               dTag.putUUID("village_id", vid.uuid());
               discList.add(dTag);
            }

            playerTag.put("discovered_villages", discList);
         }

         playersList.add(playerTag);
      }

      root.put("players", playersList);
      return root;
   }

   private static PlayerCultureReputation load(CompoundTag root) {
      PlayerCultureReputation result = new PlayerCultureReputation();
      ListTag playersList = root.getList("players", 10);

      for (int i = 0; i < playersList.size(); i++) {
         CompoundTag playerTag = playersList.getCompound(i);
         UUID playerId = playerTag.getUUID("player");
         if (playerTag.contains("cultures")) {
            Map<ResourceLocation, Integer> cultureMap = new HashMap<>();
            ListTag culturesList = playerTag.getList("cultures", 10);

            for (int j = 0; j < culturesList.size(); j++) {
               CompoundTag cultureTag = culturesList.getCompound(j);
               ResourceLocation cultureId = ResourceLocation.parse(cultureTag.getString("culture"));
               int value = cultureTag.getInt("value");
               cultureMap.put(cultureId, value);
            }

            result.data.put(playerId, cultureMap);
         }

         if (playerTag.getBoolean("donation_mode")) {
            result.donationMode.put(playerId, true);
         }

         if (playerTag.contains("visited_cultures")) {
            Set<ResourceLocation> visited = new HashSet<>();
            ListTag visitedList = playerTag.getList("visited_cultures", 10);

            for (int j = 0; j < visitedList.size(); j++) {
               CompoundTag vTag = visitedList.getCompound(j);
               visited.add(ResourceLocation.parse(vTag.getString("culture")));
            }

            result.visitedCultures.put(playerId, visited);
         }

         if (playerTag.contains("languages")) {
            Map<ResourceLocation, Integer> langMap = new HashMap<>();
            ListTag langList = playerTag.getList("languages", 10);

            for (int j = 0; j < langList.size(); j++) {
               CompoundTag langTag = langList.getCompound(j);
               ResourceLocation cultureId = ResourceLocation.parse(langTag.getString("culture"));
               int score = langTag.getInt("score");
               langMap.put(cultureId, score);
            }

            result.languageData.put(playerId, langMap);
         }

         if (playerTag.contains("diplomacy_points")) {
            Map<VillageId, Integer> dpMap = new HashMap<>();
            ListTag dpList = playerTag.getList("diplomacy_points", 10);

            for (int j = 0; j < dpList.size(); j++) {
               CompoundTag dpTag = dpList.getCompound(j);
               VillageId villageId = new VillageId(dpTag.getUUID("village_id"));
               int points = dpTag.getInt("points");
               dpMap.put(villageId, points);
            }

            result.diplomacyPoints.put(playerId, dpMap);
         }

         if (playerTag.contains("learned_crops")) {
            Set<String> crops = new HashSet<>();
            ListTag cropList = playerTag.getList("learned_crops", 10);

            for (int j = 0; j < cropList.size(); j++) {
               crops.add(cropList.getCompound(j).getString("key"));
            }

            result.learnedCrops.put(playerId, crops);
         }

         if (playerTag.contains("learned_hunting_drops")) {
            Set<String> drops = new HashSet<>();
            ListTag dropList = playerTag.getList("learned_hunting_drops", 10);

            for (int j = 0; j < dropList.size(); j++) {
               drops.add(dropList.getCompound(j).getString("key"));
            }

            result.learnedHuntingDrops.put(playerId, drops);
         }

         if (playerTag.contains("culture_control")) {
            Set<ResourceLocation> controls = new HashSet<>();
            ListTag controlList = playerTag.getList("culture_control", 10);

            for (int j = 0; j < controlList.size(); j++) {
               controls.add(ResourceLocation.parse(controlList.getCompound(j).getString("culture")));
            }

            result.cultureControlTags.put(playerId, controls);
         }

         if (playerTag.contains("discovered_villages")) {
            Set<VillageId> discovered = new HashSet<>();
            ListTag discList = playerTag.getList("discovered_villages", 10);

            for (int j = 0; j < discList.size(); j++) {
               discovered.add(new VillageId(discList.getCompound(j).getUUID("village_id")));
            }

            result.discoveredVillages.put(playerId, discovered);
         }
      }

      return result;
   }

   public enum LanguageLevel {
      MINIMAL,
      BEGINNER,
      MODERATE,
      FLUENT;
   }
}
