package org.millenaire.village;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.discovery.DiscoveryTracker;
import org.millenaire.item.ModItems;

public final class PlayerDiscoveryHelper {
   private PlayerDiscoveryHelper() {
   }

   public static void checkExplorationAdvancements(ServerLevel level, Village village) {
      VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
      if (vType != null) {
         int hRadius = vType.radius();
         BlockPos center = village.getCenter();
         AABB area = new AABB(
            center.getX() - hRadius, center.getY() - 20, center.getZ() - hRadius, center.getX() + hRadius, center.getY() + 20, center.getZ() + hRadius
         );

         for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (vType.loneBuilding()) {
               MillAdvancements.grant(player, MillAdvancements.EXPLORER);
            }

            BuildingInstance townhall = village.getTownhall();
            if (townhall != null) {
               BuildingPlan plan = ModCultures.getBuildingPlan(townhall.getPlanId());
               if (plan != null && plan.hasTag("hof")) {
                  MillAdvancements.grant(player, MillAdvancements.PANTHEON);
               }
            }

            PlayerCultureReputation cultureRep = PlayerCultureReputation.get(level);
            cultureRep.markCultureVisited(player.getUUID(), village.getCultureId());
            int culturesVisited = cultureRep.countCulturesVisited(player.getUUID());
            if (culturesVisited >= 3) {
               MillAdvancements.grant(player, MillAdvancements.MARCO_POLO);
            }

            int totalCultures = MillAdvancements.ADVANCEMENT_CULTURES.size();
            if (culturesVisited >= totalCultures) {
               MillAdvancements.grant(player, MillAdvancements.MAGELLAN);
            }
         }
      }
   }

   public static void checkTravelBookDiscoveries(ServerLevel level, Village village, AABB villageArea) {
      if ((Boolean)MillenaireServerConfig.SERVER.travelBookLearning.get()) {
         List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, villageArea);
         if (!nearbyPlayers.isEmpty()) {
            String cultureKey = village.getCultureId().getPath();
            DiscoveryTracker tracker = DiscoveryTracker.get(level);

            for (BuildingInstance building : village.getBuildings()) {
               if (building.isOperational()) {
                  BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
                  if (plan != null) {
                     BlockPos o = building.getOrigin();
                     int minX = o.getX() + building.getCachedMinX() - 2;
                     int maxX = o.getX() + building.getCachedMaxX() + 2;
                     int minZ = o.getZ() + building.getCachedMinZ() - 2;
                     int maxZ = o.getZ() + building.getCachedMaxZ() + 2;
                     double minY = o.getY() - 5;
                     double maxY = o.getY() + plan.height() + 5;

                     for (ServerPlayer player : nearbyPlayers) {
                        double px = player.getX();
                        double py = player.getY();
                        double pz = player.getZ();
                        if (!(px < minX) && !(px > maxX) && !(py < minY) && !(py > maxY) && !(pz < minZ) && !(pz > maxZ)) {
                           ResourceLocation planSetId = building.getPlanSetId();
                           if (planSetId != null && tracker.unlockBuilding(player.getUUID(), cultureKey, planSetId.getPath())) {
                              BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
                              String name = planSet != null ? planSet.nativeName() : planSetId.getPath();
                              player.sendSystemMessage(Component.translatable("travelbook.discovered.building", new Object[]{name}));
                           }

                           if (tracker.unlockVillage(player.getUUID(), cultureKey, village.getVillageTypeId().getPath())) {
                              player.sendSystemMessage(Component.translatable("travelbook.discovered.village", new Object[]{village.getVillageName()}));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void regenerateScrollIfNeeded(ServerLevel level, Village village) {
      if (village.isPlayerControlled()) {
         BuildingInstance townhall = village.getTownhall();
         if (townhall != null) {
            BuildingInventory inv = townhall.getInventory();
            if (inv != null) {
               Map<Item, Integer> contents = inv.scanChests(level);
               if (contents.getOrDefault(ModItems.VILLAGE_SCROLL.get(), 0) <= 0) {
                  ItemStack scroll = VillageBookService.createScrollForVillage(village);
                  inv.addStack(level, scroll);
               }
            }
         }
      }
   }
}
