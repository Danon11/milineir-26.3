package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.PerVillagerThrottle;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class GetResourcesForShopsGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "get_resources_for_shops");
   private static final int STANDARD_DELAY = 2000;
   private static final int IMMEDIATE_THRESHOLD = 16;
   private final PerVillagerThrottle throttle = new PerVillagerThrottle(2000);

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return this.computeRawPriorityCount(context) * 5;
   }

   private int computeRawPriorityCount(GoalContext context) {
      ResourceLocation cultureId = context.village().getCultureId();
      BuildingInstance home = context.resolveHomeBuilding().orElse(null);
      BuildingInstance townhall = context.village().getTownhall();
      int total = 0;

      for (BuildingInstance shop : context.village().getBuildings()) {
         if (shop.isOperational()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(shop.getPlanId());
            if (plan != null && plan.shopId() != null) {
               ShopProfile profile = ShopProfileLoader.getProfile(cultureId, plan.shopId());
               if (profile != null && !profile.deliverTo().isEmpty()) {
                  for (String itemName : profile.deliverTo()) {
                     Item item = ItemHelper.resolve(itemName);
                     if (item != null) {
                        if (home != null && home.getInventory() != null && !shop.getId().equals(home.getId())) {
                           total += home.getInventory().getCount(context.level(), item);
                        }

                        if (townhall != null
                           && townhall.getInventory() != null
                           && !shop.getId().equals(townhall.getId())
                           && (home == null || !townhall.getId().equals(home.getId()))) {
                           total += townhall.getInventory().getCount(context.level(), item);
                        }
                     }
                  }
               }
            }
         }
      }

      return total;
   }

   public boolean canStart(GoalContext context) {
      if (context.resolveHomeBuilding().isEmpty()) {
         return false;
      }

      if (this.isAlreadyCarryingShopGoods(context)) {
         return false;
      }

      List<GetResourcesForShopsGoal.PickupRequest> requests = this.computePickupRequests(context);
      if (requests.isEmpty()) {
         return false;
      }

      int totalAvailable = 0;

      for (GetResourcesForShopsGoal.PickupRequest req : requests) {
         totalAvailable += req.availableCount;
      }

      if (totalAvailable <= 0) {
         return false;
      } else {
         long currentTick = context.gameTime();
         if (totalAvailable > 16) {
            this.throttle.record(context.villager().getUUID(), currentTick);
            return true;
         } else {
            return this.throttle.shouldEvaluate(context.villager().getUUID(), currentTick);
         }
      }
   }

   public VillagerTask start(GoalContext context) {
      List<GetResourcesForShopsGoal.PickupRequest> requests = this.computePickupRequests(context);
      if (requests.isEmpty()) {
         return new GetResourcesForShopsGoal.PickupTask(null, List.of());
      }

      GetResourcesForShopsGoal.PickupRequest first = requests.get(0);
      return new GetResourcesForShopsGoal.PickupTask(first.sourceBuilding, first.items);
   }

   private boolean isAlreadyCarryingShopGoods(GoalContext context) {
      VillagerInventory inv = context.villager().getInventory();
      ResourceLocation cultureId = context.village().getCultureId();

      for (BuildingInstance shop : context.village().getBuildings()) {
         if (shop.isOperational()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(shop.getPlanId());
            if (plan != null && plan.shopId() != null) {
               ShopProfile profile = ShopProfileLoader.getProfile(cultureId, plan.shopId());
               if (profile != null && !profile.deliverTo().isEmpty()) {
                  for (String itemName : profile.deliverTo()) {
                     Item item = ItemHelper.resolve(itemName);
                     if (item != null && inv.getCount(item) > 0) {
                        return true;
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   List<GetResourcesForShopsGoal.PickupRequest> computePickupRequests(GoalContext context) {
      List<GetResourcesForShopsGoal.PickupRequest> requests = new ArrayList<>();
      ResourceLocation cultureId = context.village().getCultureId();
      BuildingInstance home = context.resolveHomeBuilding().orElse(null);
      BuildingInstance townhall = context.village().getTownhall();
      List<GetResourcesForShopsGoal.ShopItemNeed> allNeeds = new ArrayList<>();

      for (BuildingInstance shop : context.village().getBuildings()) {
         if (shop.isOperational()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(shop.getPlanId());
            if (plan != null && plan.shopId() != null) {
               ShopProfile profile = ShopProfileLoader.getProfile(cultureId, plan.shopId());
               if (profile != null && !profile.deliverTo().isEmpty()) {
                  for (String itemName : profile.deliverTo()) {
                     Item item = ItemHelper.resolve(itemName);
                     if (item != null) {
                        allNeeds.add(new GetResourcesForShopsGoal.ShopItemNeed(item, shop.getId()));
                     }
                  }
               }
            }
         }
      }

      if (allNeeds.isEmpty()) {
         return requests;
      }

      this.checkSource(home, allNeeds, context, cultureId, townhall, requests);
      this.checkSource(townhall, allNeeds, context, cultureId, townhall, requests);
      return requests;
   }

   private void checkSource(
      @Nullable BuildingInstance source,
      List<GetResourcesForShopsGoal.ShopItemNeed> allNeeds,
      GoalContext context,
      ResourceLocation cultureId,
      @Nullable BuildingInstance townhall,
      List<GetResourcesForShopsGoal.PickupRequest> requests
   ) {
      if (source != null && source.getInventory() != null) {
         boolean sourceIsTownhall = townhall != null && source.getId().equals(townhall.getId());
         BuildingPlan sourcePlan = ModCultures.getBuildingPlan(source.getPlanId());
         boolean sourceIsShop = sourcePlan != null && sourcePlan.shopId() != null;
         List<Item> sourceDeliverTo = List.of();
         if (sourceIsShop && !sourceIsTownhall) {
            ShopProfile sourceProfile = ShopProfileLoader.getProfile(cultureId, sourcePlan.shopId());
            if (sourceProfile != null && !sourceProfile.deliverTo().isEmpty()) {
               sourceDeliverTo = new ArrayList<>();

               for (String name : sourceProfile.deliverTo()) {
                  Item item = ItemHelper.resolve(name);
                  if (item != null) {
                     sourceDeliverTo.add(item);
                  }
               }
            }
         }

         List<GetResourcesForShopsGoal.ItemAmount> available = new ArrayList<>();
         int totalAvailable = 0;

         for (GetResourcesForShopsGoal.ShopItemNeed need : allNeeds) {
            if (!source.getId().equals(need.shopId) && (!sourceIsShop || !sourceDeliverTo.contains(need.item))) {
               int count = GoodAvailabilityHelper.nbGoodAvailable(source, need.item, context.level(), context.village(), cultureId, true);
               if (count > 0) {
                  available.add(new GetResourcesForShopsGoal.ItemAmount(need.item, count));
                  totalAvailable += count;
               }
            }
         }

         if (totalAvailable > 0) {
            requests.add(new GetResourcesForShopsGoal.PickupRequest(source.getId(), available, totalAvailable));
         }
      }
   }

   record ItemAmount(Item item, int count) {
   }

   record PickupRequest(BuildingId sourceBuilding, List<GetResourcesForShopsGoal.ItemAmount> items, int availableCount) {
   }

   static class PickupTask extends ProgressAwareTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final int ACTION_DURATION = 40;
      private GetResourcesForShopsGoal.PickupTask.State state = GetResourcesForShopsGoal.PickupTask.State.WALKING_TO_SOURCE;
      private int actionTicks;
      @Nullable
      private final BuildingId sourceId;
      private final List<GetResourcesForShopsGoal.ItemAmount> itemsToPickup;

      PickupTask(@Nullable BuildingId sourceId, List<GetResourcesForShopsGoal.ItemAmount> itemsToPickup) {
         this.sourceId = sourceId;
         this.itemsToPickup = itemsToPickup;
         if (sourceId == null || itemsToPickup.isEmpty()) {
            this.state = GetResourcesForShopsGoal.PickupTask.State.DONE;
         }
      }

      public ResourceLocation goalId() {
         return GetResourcesForShopsGoal.ID;
      }

      public void tick(GoalContext ctx) {
         switch (this.state) {
            case WALKING_TO_SOURCE:
               this.tickWalking(ctx);
               break;
            case PICKING_UP:
               this.tickPickingUp(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         BuildingInstance source = this.sourceId != null ? ctx.village().getBuilding(this.sourceId) : null;
         BlockPos target = source != null ? source.getSellingPos() : null;
         if (target == null) {
            this.state = GetResourcesForShopsGoal.PickupTask.State.DONE;
         } else {
            VillagerNavigationManager nav = ctx.villager().getNavManager();
            if (nav.getDestination() == null) {
               nav.navigateTo(ctx.villager(), target, 0.5);
            }

            if (nav.isArrived(ctx.villager(), 3.0)) {
               nav.stop(ctx.villager());
               this.state = GetResourcesForShopsGoal.PickupTask.State.PICKING_UP;
               this.actionTicks = 0;
               this.reportProgress();
            } else if (nav.isAbandoned()) {
               nav.stop(ctx.villager());
               this.state = GetResourcesForShopsGoal.PickupTask.State.DONE;
            }
         }
      }

      private void tickPickingUp(GoalContext ctx) {
         this.actionTicks++;
         if (this.actionTicks >= 40) {
            BuildingInstance source = this.sourceId != null ? ctx.village().getBuilding(this.sourceId) : null;
            if (source != null && source.getInventory() != null) {
               VillagerInventory villagerInv = ctx.villager().getInventory();
               BuildingInventory sourceInv = source.getInventory();
               int picked = 0;

               for (GetResourcesForShopsGoal.ItemAmount ia : this.itemsToPickup) {
                  int available = GoodAvailabilityHelper.nbGoodAvailable(source, ia.item, ctx.level(), ctx.village(), ctx.village().getCultureId(), true);
                  int toTake = Math.min(ia.count, available);
                  if (toTake > 0) {
                     int removed = sourceInv.remove(ctx.level(), ia.item, toTake);
                     if (removed > 0) {
                        villagerInv.add(ia.item, removed);
                        picked += removed;
                     }
                  }
               }

               if (picked > 0) {
                  GetResourcesForShopsGoal.LOGGER.debug("[Millenaire] {} picked up {} items for shops", ctx.villager().getVillagerTypeId(), picked);
                  this.reportProgress();
               }

               this.state = GetResourcesForShopsGoal.PickupTask.State.DONE;
            } else {
               this.state = GetResourcesForShopsGoal.PickupTask.State.DONE;
            }
         }
      }

      public boolean isFinished() {
         return this.state == GetResourcesForShopsGoal.PickupTask.State.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public TravelPhase getTravelPhase() {
         return this.state == GetResourcesForShopsGoal.PickupTask.State.WALKING_TO_SOURCE ? TravelPhase.TRAVELLING : TravelPhase.AT_DESTINATION;
      }

      @Nullable
      public Component getGoalLabel() {
         return switch (this.state) {
            case WALKING_TO_SOURCE -> Component.translatable("goal.millenaire.transport.to_source");
            case PICKING_UP -> Component.translatable("goal.millenaire.transport.picking_up");
            case DONE -> null;
         };
      }

      private enum State {
         WALKING_TO_SOURCE,
         PICKING_UP,
         DONE;
      }
   }

   private record ShopItemNeed(Item item, BuildingId shopId) {
   }
}
