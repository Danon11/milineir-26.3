package org.millenaire.quest;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ConstructionTask;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.PlayerQuestData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public class MarvelManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String NORMAN_MARVEL_COMPLETION_TAG = "normanmarvel_helper";
   private static final long DAWN_TIME = 23500L;
   private static final int COMPLETION_CHECK_INTERVAL = 200;
   private static final int LUCK_DURATION_TICKS = 12000;
   private static final int LUCK_AMPLIFIER = 1;
   private static final int BELL_RANGE = 128;
   private static final float DONATION_RATIO = 0.5F;
   private boolean marvelComplete;
   private final CopyOnWriteArrayList<String> donationList = new CopyOnWriteArrayList<>();
   private long lastDonationDay = -1L;
   private boolean nightActionDone = false;
   private boolean dawnActionDone = false;

   public static boolean isMarvelVillageType(Village village) {
      VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
      return vType != null && vType.isMarvel();
   }

   public void tick(Village village, ServerLevel level) {
      if ((level.getGameTime() + village.hashCode()) % 200L == 120L) {
         this.testForCompletion(village, level);
      }

      this.updateNightAction(village, level);
      this.updateDawnAction(village, level);
   }

   private void testForCompletion(Village village, ServerLevel level) {
      if (!this.marvelComplete) {
         BuildingInstance marvelBuilding = findBuildingWithTag(village, "marvel");
         if (marvelBuilding != null) {
            BuildingPlanSet planSet = marvelBuilding.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(marvelBuilding.getPlanSetId()) : null;
            if (planSet != null) {
               String variant = marvelBuilding.getVariant() != null ? marvelBuilding.getVariant() : "default";
               int totalLevels = planSet.getLevelCount(variant);
               if (marvelBuilding.getLevel() + 1 >= totalLevels) {
                  this.marvelComplete = true;
                  village.markDirty();
                  Component message = Component.translatable("marvel.norman.marvelbuilt").withStyle(ChatFormatting.BLUE);

                  for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                     player.sendSystemMessage(message);
                  }

                  LOGGER.info("Marvel village '{}' construction complete!", village.getVillageName());
               }
            }
         }
      }

      if (this.marvelComplete) {
         PlayerQuestData questData = PlayerQuestData.get(level, QuestRegistry::get);

         for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (questData.hasPlayerTag(player.getUUID(), "normanmarvel_helper")) {
               MillAdvancements.grant(player, MillAdvancements.MARVEL_NORMAN);
            }
         }
      }
   }

   @Nullable
   private static BuildingInstance findBuildingWithTag(Village village, String tag) {
      for (BuildingInstance building : village.getBuildings()) {
         if (building.isOperational()) {
            BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
            if (planSet != null && planSet.hasTag(tag)) {
               return building;
            }
         }
      }

      return null;
   }

   private void updateNightAction(Village village, ServerLevel level) {
      if (level.isDay()) {
         this.nightActionDone = false;
      } else if (!this.nightActionDone) {
         long currentDay = level.getDayTime() / 24000L;
         if (currentDay <= this.lastDonationDay) {
            this.nightActionDone = true;
         } else {
            if (!this.marvelComplete) {
               this.gatherDonationsFromVillages(village, level);
               this.lastDonationDay = currentDay;
            }

            this.nightActionDone = true;
         }
      }
   }

   private void gatherDonationsFromVillages(Village village, ServerLevel level) {
      Map<ResourceLocation, Integer> needs = this.computeRemainingNeeds(village, level);
      if (!needs.isEmpty()) {
         VillageSavedData savedData = VillageSavedData.get(level);

         for (Entry<VillageId, Integer> entry : village.getRelations().entrySet()) {
            VillageId otherId = entry.getKey();
            int relation = entry.getValue();
            if (relation >= 90) {
               Village otherVillage = savedData.getVillageManager().getVillage(otherId);
               if (otherVillage != null && otherVillage.getCultureId().equals(village.getCultureId())) {
                  VillageType otherType = ModCultures.getVillageType(otherVillage.getVillageTypeId());
                  if (otherType != null && (otherType.isRegularVillage() || otherType.isHamlet())) {
                     this.gatherDonationsFrom(village, otherVillage, needs, level);
                  }
               }
            }
         }
      }
   }

   private void gatherDonationsFrom(Village marvelVillage, Village donorVillage, Map<ResourceLocation, Integer> needs, ServerLevel level) {
      BuildingInstance townHall = marvelVillage.getTownhall();
      if (townHall != null) {
         BuildingInventory thInventory = townHall.getInventory();
         if (thInventory != null) {
            List<TradeGood> tradeGoods = TradeGoodsLoader.getGoods(marvelVillage.getCultureId());
            StringBuilder donations = new StringBuilder();

            for (Entry<ResourceLocation, Integer> needEntry : needs.entrySet()) {
               ResourceLocation neededItem = needEntry.getKey();
               int needed = needEntry.getValue();
               if (needed > 0) {
                  String tradeGoodKey = findTradeGoodKey(tradeGoods, neededItem);
                  if (tradeGoodKey != null) {
                     int gathered = 0;

                     for (BuildingInstance donorBuilding : donorVillage.getBuildings()) {
                        if (donorBuilding.isOperational()) {
                           int capacity = getAbstractedProduction(donorBuilding, tradeGoodKey);
                           int donated = (int)(capacity * 0.5F);
                           if (donated > 0) {
                              donated = Math.min(donated, needed - gathered);
                              gathered += donated;
                           }
                        }
                     }

                     if (gathered > 0) {
                        Item item = (Item)BuiltInRegistries.ITEM.getOptional(neededItem).orElse(null);
                        if (item != null) {
                           thInventory.add(level, item, gathered);
                        }

                        if (donations.length() > 0) {
                           donations.append(";");
                        }

                        donations.append(tradeGoodKey).append("/").append(gathered);
                     }
                  }
               }
            }

            if (donations.length() > 0) {
               String donationEntry = "donation;" + donorVillage.getVillageName() + ";" + donations;
               this.donationList.add(donationEntry);
               marvelVillage.markDirty();
               LOGGER.debug("Marvel donation from '{}': {}", donorVillage.getVillageName(), donationEntry);
            }
         }
      }
   }

   private static int getAbstractedProduction(BuildingInstance building, String tradeGoodKey) {
      BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
      if (planSet == null) {
         return 0;
      }

      String variant = building.getVariant() != null ? building.getVariant() : "default";
      BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, building.getLevel());
      return levelDef == null ? 0 : levelDef.abstractedProduction().getOrDefault(tradeGoodKey, 0);
   }

   @Nullable
   private static String findTradeGoodKey(List<TradeGood> tradeGoods, ResourceLocation itemId) {
      if (tradeGoods == null) {
         return null;
      }

      for (TradeGood good : tradeGoods) {
         if (good.itemLocation().equals(itemId)) {
            return good.id();
         }
      }

      return null;
   }

   private Map<ResourceLocation, Integer> computeRemainingNeeds(Village village, ServerLevel level) {
      Map<ResourceLocation, Integer> needs = new HashMap<>();
      Map<ResourceLocation, Integer> placedPlanSetCounts = new HashMap<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (b.getPlanSetId() != null) {
            placedPlanSetCounts.merge(b.getPlanSetId(), 1, Integer::sum);
         }
      }

      for (BuildingInstance building : village.getBuildings()) {
         BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
         if (planSet != null) {
            String variant = building.getVariant() != null ? building.getVariant() : "default";
            int currentLevel = building.getLevel();
            int totalLevels = planSet.getLevelCount(variant);
            boolean needsCurrentLevel = building.isBeingBuilt() || building.getStatus() == BuildingInstance.Status.PLANNED;
            int startLevel = needsCurrentLevel ? currentLevel : currentLevel + 1;
            if (startLevel < totalLevels) {
               Set<String> existingSubBuildings = new HashSet<>();

               for (int lvl = 0; lvl < startLevel; lvl++) {
                  BuildingPlanSet.LevelDef completedDef = planSet.getLevel(variant, lvl);
                  if (completedDef != null) {
                     existingSubBuildings.addAll(completedDef.subBuildings());
                  }
               }

               List<String> newSubBuildings = new ArrayList<>();

               for (int lvl = startLevel; lvl < totalLevels; lvl++) {
                  BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, lvl);
                  if (levelDef != null) {
                     addPlanCost(levelDef, needs);

                     for (String subKey : levelDef.subBuildings()) {
                        if (!newSubBuildings.contains(subKey) && !existingSubBuildings.contains(subKey)) {
                           newSubBuildings.add(subKey);
                        }
                     }
                  }
               }

               addSubBuildingCosts(village, newSubBuildings, needs, placedPlanSetCounts);
            }
         }
      }

      VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
      if (vType != null) {
         Map<ResourceLocation, Integer> layoutCounts = new HashMap<>();

         for (VillageType.LayoutSlot slot : vType.layout()) {
            layoutCounts.merge(slot.plan(), 1, Integer::sum);
         }

         for (Entry<ResourceLocation, Integer> entry : layoutCounts.entrySet()) {
            ResourceLocation planId = entry.getKey();
            int layoutCount = entry.getValue();
            int placedCount = placedPlanSetCounts.getOrDefault(planId, 0);
            int unplacedCount = layoutCount - placedCount;
            if (unplacedCount > 0) {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planId);
               if (planSet != null) {
                  String variant = planSet.variants().keySet().iterator().next();
                  int totalLevels = planSet.getLevelCount(variant);
                  List<String> allSubBuildings = new ArrayList<>();

                  for (int lvl = 0; lvl < totalLevels; lvl++) {
                     BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, lvl);
                     if (levelDef != null) {
                        addPlanCostMultiplied(levelDef, needs, unplacedCount);

                        for (String subKey : levelDef.subBuildings()) {
                           if (!allSubBuildings.contains(subKey)) {
                              allSubBuildings.add(subKey);
                           }
                        }
                     }
                  }

                  addSubBuildingCosts(village, allSubBuildings, needs, placedPlanSetCounts, unplacedCount);
               }
            }
         }
      }

      BuildingInstance townHall = village.getTownhall();
      if (townHall != null && townHall.getInventory() != null) {
         for (Entry<ResourceLocation, Integer> entry : new HashMap<>(needs).entrySet()) {
            Item item = (Item)BuiltInRegistries.ITEM.getOptional(entry.getKey()).orElse(null);
            if (item != null) {
               int stock = townHall.getInventory().getCount(level, item);
               needs.put(entry.getKey(), needs.get(entry.getKey()) - stock);
            }
         }
      }

      for (BuildingInstance building : village.getBuildings()) {
         if (building.isBeingBuilt()) {
            ConstructionTask task = building.getConstructionTask();
            if (task != null && task.getReservedBuilder() != null && level.getEntity(task.getReservedBuilder()) instanceof MillVillager builder) {
               for (Entry<ResourceLocation, Integer> entry : new HashMap<>(needs).entrySet()) {
                  Item item = (Item)BuiltInRegistries.ITEM.getOptional(entry.getKey()).orElse(null);
                  if (item != null) {
                     int carried = builder.getInventory().getCount(item);
                     needs.put(entry.getKey(), needs.get(entry.getKey()) - carried);
                  }
               }
            }
         }
      }

      needs.entrySet().removeIf(e -> e.getValue() <= 0);
      return needs;
   }

   private static void addPlanCost(BuildingPlanSet.LevelDef levelDef, Map<ResourceLocation, Integer> needs) {
      addPlanCostMultiplied(levelDef, needs, 1);
   }

   private static void addPlanCostMultiplied(BuildingPlanSet.LevelDef levelDef, Map<ResourceLocation, Integer> needs, int multiplier) {
      for (Entry<ResourceLocation, Integer> resEntry : levelDef.requiredResources().entrySet()) {
         needs.merge(resEntry.getKey(), resEntry.getValue() * multiplier, Integer::sum);
      }
   }

   private static void addSubBuildingCosts(
      Village village, List<String> subBuildingKeys, Map<ResourceLocation, Integer> needs, Map<ResourceLocation, Integer> placedPlanSetCounts
   ) {
      addSubBuildingCosts(village, subBuildingKeys, needs, placedPlanSetCounts, 1);
   }

   private static void addSubBuildingCosts(
      Village village, List<String> subBuildingKeys, Map<ResourceLocation, Integer> needs, Map<ResourceLocation, Integer> placedPlanSetCounts, int multiplier
   ) {
      for (String subKey : subBuildingKeys) {
         ResourceLocation subPlanSetId = ResourceLocation.fromNamespaceAndPath("millenaire", village.getCultureId().getPath() + "/" + subKey.toLowerCase());
         if (!placedPlanSetCounts.containsKey(subPlanSetId)) {
            BuildingPlanSet subPlanSet = ModCultures.getBuildingPlanSet(subPlanSetId);
            if (subPlanSet != null) {
               String subVariant = subPlanSet.variants().keySet().iterator().next();
               int subTotalLevels = subPlanSet.getLevelCount(subVariant);

               for (int lvl = 0; lvl < subTotalLevels; lvl++) {
                  BuildingPlanSet.LevelDef subLevelDef = subPlanSet.getLevel(subVariant, lvl);
                  if (subLevelDef != null) {
                     addPlanCostMultiplied(subLevelDef, needs, multiplier);
                  }
               }
            }
         }
      }
   }

   private void updateDawnAction(Village village, ServerLevel level) {
      long timeOfDay = level.getDayTime() % 24000L;
      boolean isDawn = timeOfDay > 23500L;
      if (!isDawn) {
         this.dawnActionDone = false;
      } else if (!this.dawnActionDone) {
         if (this.marvelComplete) {
            this.ringMorningBells(village, level);
         }

         this.dawnActionDone = true;
      }
   }

   private void ringMorningBells(Village village, ServerLevel level) {
      BuildingInstance marvelBuilding = findBuildingWithTag(village, "marvel");
      BlockPos bellPos = marvelBuilding != null ? marvelBuilding.getOrigin() : village.getCenter();
      LOGGER.info("Norman bells ring at marvel village '{}'!", village.getVillageName());
      AABB bellArea = new AABB(bellPos).inflate(128.0);

      for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bellArea)) {
         player.addEffect(new MobEffectInstance(MobEffects.LUCK, 12000, 1, true, true));
         player.sendSystemMessage(Component.translatable("marvel.norman.morningbells", new Object[]{village.getVillageName()}));
      }
   }

   public boolean isMarvelComplete() {
      return this.marvelComplete;
   }

   public List<String> getDonationList() {
      return this.donationList;
   }

   public Map<ResourceLocation, Integer> getRemainingNeeds(Village village, ServerLevel level) {
      return this.computeRemainingNeeds(village, level);
   }

   public Map<ResourceLocation, Integer> getTotalNeeds(Village village) {
      Map<ResourceLocation, Integer> needs = new HashMap<>();
      Map<ResourceLocation, Integer> placedPlanSetCounts = new HashMap<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (b.getPlanSetId() != null) {
            placedPlanSetCounts.merge(b.getPlanSetId(), 1, Integer::sum);
         }
      }

      for (BuildingInstance building : village.getBuildings()) {
         BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
         if (planSet != null) {
            String variant = building.getVariant() != null ? building.getVariant() : "default";
            int totalLevels = planSet.getLevelCount(variant);
            List<String> allSubBuildings = new ArrayList<>();

            for (int lvl = 0; lvl < totalLevels; lvl++) {
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, lvl);
               if (levelDef != null) {
                  addPlanCost(levelDef, needs);

                  for (String subKey : levelDef.subBuildings()) {
                     if (!allSubBuildings.contains(subKey)) {
                        allSubBuildings.add(subKey);
                     }
                  }
               }
            }

            addSubBuildingCosts(village, allSubBuildings, needs, placedPlanSetCounts);
         }
      }

      VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
      if (vType != null) {
         Map<ResourceLocation, Integer> layoutCounts = new HashMap<>();

         for (VillageType.LayoutSlot slot : vType.layout()) {
            layoutCounts.merge(slot.plan(), 1, Integer::sum);
         }

         for (Entry<ResourceLocation, Integer> entry : layoutCounts.entrySet()) {
            ResourceLocation planId = entry.getKey();
            int layoutCount = entry.getValue();
            int placedCount = placedPlanSetCounts.getOrDefault(planId, 0);
            int unplacedCount = layoutCount - placedCount;
            if (unplacedCount > 0) {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planId);
               if (planSet != null) {
                  String variant = planSet.variants().keySet().iterator().next();
                  int totalLevels = planSet.getLevelCount(variant);
                  List<String> allSubBuildings = new ArrayList<>();

                  for (int lvl = 0; lvl < totalLevels; lvl++) {
                     BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, lvl);
                     if (levelDef != null) {
                        addPlanCostMultiplied(levelDef, needs, unplacedCount);

                        for (String subKey : levelDef.subBuildings()) {
                           if (!allSubBuildings.contains(subKey)) {
                              allSubBuildings.add(subKey);
                           }
                        }
                     }
                  }

                  addSubBuildingCosts(village, allSubBuildings, needs, placedPlanSetCounts, unplacedCount);
               }
            }
         }
      }

      return needs;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("marvelComplete", this.marvelComplete);
      tag.putLong("lastDonationDay", this.lastDonationDay);
      ListTag donationListTag = new ListTag();

      for (String s : this.donationList) {
         CompoundTag entry = new CompoundTag();
         entry.putString("donation", s);
         donationListTag.add(entry);
      }

      tag.put("marvelDonationList", donationListTag);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.marvelComplete = tag.getBoolean("marvelComplete");
      this.lastDonationDay = tag.getLong("lastDonationDay");
      if (tag.contains("marvelDonationList")) {
         ListTag donationListTag = tag.getList("marvelDonationList", 10);
         this.donationList.clear();

         for (int i = 0; i < donationListTag.size(); i++) {
            this.donationList.add(donationListTag.getCompound(i).getString("donation"));
         }
      }
   }
}
