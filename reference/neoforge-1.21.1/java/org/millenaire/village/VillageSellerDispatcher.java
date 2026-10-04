package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.millenaire.TickConstants;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.impl.BuildGoal;
import org.millenaire.goal.impl.SellerGoal;
import org.slf4j.Logger;

public final class VillageSellerDispatcher {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillageSellerDispatcher() {
   }

   public static void checkSeller(ServerLevel level, Village village) {
      if (level.getGameTime() % 10L == 0L) {
         cleanupActiveSellers(village);
         if (!TickConstants.isNight(level)) {
            if (!village.isUnderAttack()) {
               if (village.areChestsLocked()) {
                  BlockPos center = village.getCenter();
                  Player nearestPlayer = level.getNearestPlayer(center.getX(), center.getY(), center.getZ(), 100.0, false);
                  if (nearestPlayer != null) {
                     if (nearestPlayer instanceof ServerPlayer) {
                        if (!village.isControlledBy(nearestPlayer.getUUID())) {
                           int rep = village.getCombinedReputation(level, nearestPlayer.getUUID());
                           if (rep >= -1024) {
                              Map<BlockPos, MillVillager> activeSellers = village.getActiveSellers();

                              for (BuildingInstance b : village.getBuildings()) {
                                 if (b.isOperational()) {
                                    BuildingPlan plan = ModCultures.getBuildingPlan(b.getPlanId());
                                    if (plan != null && plan.shopId() != null) {
                                       List<SpecialPoint> sellingPoints = b.getPointsByType("sellingPos");
                                       if (sellingPoints.isEmpty()) {
                                          BlockPos fallback = b.getFirstPointPos("sleepingPos");
                                          if (fallback != null) {
                                             dispatchIfNeeded(level, village, b, fallback, nearestPlayer, activeSellers);
                                          }
                                       } else {
                                          for (SpecialPoint sp : sellingPoints) {
                                             dispatchIfNeeded(level, village, b, sp.pos(), nearestPlayer, activeSellers);
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void dispatchIfNeeded(
      ServerLevel level, Village village, BuildingInstance shop, BlockPos sellingPos, Player player, Map<BlockPos, MillVillager> activeSellers
   ) {
      if (!activeSellers.containsKey(sellingPos)) {
         double distSq = player.blockPosition().distSqr(sellingPos);
         if (!(distSq >= 9.0)) {
            MillVillager bestSeller = findBestSeller(level, village, sellingPos, activeSellers);
            if (bestSeller != null) {
               GoalScheduler scheduler = bestSeller.getGoalScheduler();
               if (scheduler != null) {
                  GoalContext ctx = bestSeller.buildGoalContext();
                  if (ctx != null) {
                     SellerGoal.SellerTask task = new SellerGoal.SellerTask(shop.getId(), sellingPos);
                     scheduler.forceTask(task, ctx);
                     activeSellers.put(sellingPos, bestSeller);
                     VillagerAnnouncementHelper.sendAnnouncement(bestSeller, (ServerPlayer)player, "sellercoming", "message.millenaire.seller_coming");
                     LOGGER.debug(
                        "[Millenaire] Seller {} sent to {} counter at {}",
                        new Object[]{bestSeller.getVillagerTypeId(), shop.getPlanId(), sellingPos.toShortString()}
                     );
                  }
               }
            }
         }
      }
   }

   private static MillVillager findBestSeller(ServerLevel level, Village village, BlockPos sellingPos, Map<BlockPos, MillVillager> activeSellers) {
      MillVillager bestSeller = null;
      double bestDist = Double.MAX_VALUE;

      for (UUID uuid : village.getVillagerRecords().keySet()) {
         if (level.getEntity(uuid) instanceof MillVillager mv && mv.isAlive()) {
            VillagerType vtype = ModCultures.getVillagerType(mv.getVillagerTypeId());
            if (vtype != null && vtype.hasTag("seller")) {
               GoalScheduler sched = mv.getGoalScheduler();
               if (sched != null) {
                  ResourceLocation currentGoal = sched.getCurrentGoalId();
                  if (BuildGoal.ID.equals(currentGoal)) {
                     continue;
                  }
               }

               if (!activeSellers.containsValue(mv)) {
                  double dist = mv.blockPosition().distSqr(sellingPos);
                  if (dist < bestDist) {
                     bestDist = dist;
                     bestSeller = mv;
                  }
               }
            }
         }
      }

      return bestSeller;
   }

   private static void cleanupActiveSellers(Village village) {
      Map<BlockPos, MillVillager> activeSellers = village.getActiveSellers();
      if (!activeSellers.isEmpty()) {
         Iterator<Entry<BlockPos, MillVillager>> it = activeSellers.entrySet().iterator();

         while (it.hasNext()) {
            Entry<BlockPos, MillVillager> entry = it.next();
            MillVillager seller = entry.getValue();
            if (seller.isAlive() && !seller.isRemoved()) {
               GoalScheduler sched = seller.getGoalScheduler();
               if (sched == null || !SellerGoal.ID.equals(sched.getCurrentGoalId())) {
                  it.remove();
               }
            } else {
               it.remove();
            }
         }
      }
   }
}
