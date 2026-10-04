package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public final class MarketManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private MarketManager() {
   }

   public static void updateMarket(ServerLevel level, Village village, BuildingInstance market, boolean isDaytime) {
      if (!isDaytime) {
         long currentDay = level.getDayTime() / 24000L;
         if (market.getLastMarketNightDay() > currentDay) {
            market.setLastMarketNightDay(-1L);
         }

         if (market.getLastMarketNightDay() < currentDay) {
            List<SpecialPoint> stalls = market.getPointsByType("stall");
            if (stalls.isEmpty()) {
               stalls = market.getPointsByType("sellingPos");
            }

            if (stalls.isEmpty()) {
               market.setLastMarketNightDay(currentDay);
            } else {
               int maxMerchants = stalls.size();
               int currentMerchants = countForeignMerchants(village, market.getId());
               if (currentMerchants < maxMerchants) {
                  LOGGER.debug(
                     "[Millenaire] Market {} attempting to spawn foreign merchant ({}/{})", new Object[]{market.getPlanSetId(), currentMerchants, maxMerchants}
                  );
                  VillagerType type = selectMerchantType(level, village);
                  if (type != null) {
                     spawnForeignMerchant(level, village, market, type, currentMerchants);
                  }
               }

               market.setLastMarketNightDay(currentDay);
            }
         }
      }
   }

   @Nullable
   private static VillagerType selectMerchantType(ServerLevel level, Village village) {
      List<VillagerType> foreignCandidates = new ArrayList<>();
      VillageManager villageManager = VillageSavedData.get(level).getVillageManager();
      ResourceLocation ownCulture = village.getCultureId();

      for (Village otherVillage : villageManager.getAllVillages()) {
         if (!otherVillage.getId().equals(village.getId())) {
            int relation = village.getRelation(otherVillage.getId());
            if (relation > 70) {
               ResourceLocation otherCulture = otherVillage.getCultureId();
               if (!ownCulture.equals(otherCulture) && hasMarketBuilding(otherVillage)) {
                  VillagerType foreignType = getRandomForeignMerchant(otherCulture);
                  if (foreignType != null) {
                     foreignCandidates.add(foreignType);
                  }
               }
            }
         }
      }

      int foreignChance = Math.min(1 + foreignCandidates.size(), 5);
      return !foreignCandidates.isEmpty() && ThreadLocalRandom.current().nextInt(11) < foreignChance
         ? foreignCandidates.get(ThreadLocalRandom.current().nextInt(foreignCandidates.size()))
         : getRandomForeignMerchant(ownCulture);
   }

   private static void spawnForeignMerchant(ServerLevel level, Village village, BuildingInstance market, VillagerType type, int stallIndex) {
      BlockPos spawnPos = market.resolveNavigationTarget("pathStartPos", "sellingPos");
      BuildingId marketId = market.getId();
      MillVillager merchant = VillagerSpawnFactory.spawnInVillage(level, village, type.id(), spawnPos, marketId);
      if (merchant == null) {
         LOGGER.warn("[Millenaire] Failed to spawn foreign merchant {} at {}", type.id(), spawnPos.toShortString());
      } else {
         merchant.setForeignMerchantStallId(stallIndex);
         BuildingInventory inventory = market.getInventory();
         if (inventory != null && type.foreignMerchantStock() != null) {
            for (Entry<ResourceLocation, Integer> entry : type.foreignMerchantStock().entrySet()) {
               Item item = ItemHelper.resolve(entry.getKey());
               if (item != null) {
                  inventory.add(level, item, entry.getValue());
               }
            }
         }

         Culture merchantCulture = ModCultures.getCulture(type.culture());
         String cultureName = merchantCulture != null ? merchantCulture.displayName() : type.culture().getPath();
         village.recordChronicleEvent(level, VillageEventType.MERCHANT_ARRIVED, type.id().getPath(), cultureName);
         LOGGER.debug("[Millenaire] Spawned foreign merchant {} (stall {}) at market {}", new Object[]{type.id(), stallIndex, market.getPlanSetId()});
      }
   }

   private static int countForeignMerchants(Village village, BuildingId buildingId) {
      int count = 0;

      for (Entry<UUID, VillagerRecord> entry : village.getVillagerRecords().entrySet()) {
         VillagerRecord record = entry.getValue();
         if (record.getHomeBuilding() != null && record.getHomeBuilding().equals(buildingId)) {
            VillagerType vType = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (vType != null && vType.hasTag("foreignmerchant")) {
               count++;
            }
         }
      }

      return count;
   }

   private static boolean hasMarketBuilding(Village village) {
      for (BuildingInstance b : village.getBuildings()) {
         if (b.isOperational()) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
            if (planSet != null && planSet.isMarket()) {
               return true;
            }
         }
      }

      return false;
   }

   @Nullable
   private static VillagerType getRandomForeignMerchant(ResourceLocation cultureId) {
      List<VillagerType> candidates = new ArrayList<>();
      int totalWeight = 0;

      for (VillagerType vt : ModCultures.getAllVillagerTypes().values()) {
         if (vt.culture().equals(cultureId) && vt.hasTag("foreignmerchant")) {
            candidates.add(vt);
            totalWeight += vt.spawnWeight();
         }
      }

      if (!candidates.isEmpty() && totalWeight > 0) {
         int roll = ThreadLocalRandom.current().nextInt(totalWeight);
         int cumulative = 0;

         for (VillagerType vt : candidates) {
            cumulative += vt.spawnWeight();
            if (roll < cumulative) {
               return vt;
            }
         }

         return candidates.get(candidates.size() - 1);
      } else {
         return null;
      }
   }
}
