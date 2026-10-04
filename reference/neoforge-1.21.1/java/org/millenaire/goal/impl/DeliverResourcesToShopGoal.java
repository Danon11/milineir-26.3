package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
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

public class DeliverResourcesToShopGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "deliver_resources_shop");
   private static final int STANDARD_DELAY = 2000;
   private static final int IMMEDIATE_THRESHOLD = 16;
   private final PerVillagerThrottle throttle = new PerVillagerThrottle(2000);

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      VillagerInventory inv = context.villager().getInventory();
      int priority = 0;
      ResourceLocation cultureId = context.village().getCultureId();

      for (BuildingInstance shop : context.village().getBuildings()) {
         if (shop.isOperational()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(shop.getPlanId());
            if (plan != null && plan.shopId() != null) {
               ShopProfile profile = ShopProfileLoader.getProfile(cultureId, plan.shopId());
               if (profile != null && !profile.deliverTo().isEmpty()) {
                  for (String itemName : profile.deliverTo()) {
                     Item item = ItemHelper.resolve(itemName);
                     if (item != null) {
                        priority += inv.getCount(item) * 10;
                     }
                  }
               }
            }
         }
      }

      return priority;
   }

   public boolean canStart(GoalContext context) {
      if (context.resolveHomeBuilding().isEmpty()) {
         return false;
      } else {
         DeliverResourcesToShopGoal.DeliveryTarget target = this.findDeliveryTarget(context);
         if (target == null) {
            return false;
         } else {
            long currentTick = context.gameTime();
            if (target.totalCount > 16) {
               this.throttle.record(context.villager().getUUID(), currentTick);
               return true;
            } else {
               return this.throttle.shouldEvaluate(context.villager().getUUID(), currentTick);
            }
         }
      }
   }

   public VillagerTask start(GoalContext context) {
      DeliverResourcesToShopGoal.DeliveryTarget target = this.findDeliveryTarget(context);
      return target == null
         ? new DeliverResourcesToShopGoal.DeliverTask(null, List.of())
         : new DeliverResourcesToShopGoal.DeliverTask(target.shopId, target.items);
   }

   @Nullable
   private DeliverResourcesToShopGoal.DeliveryTarget findDeliveryTarget(GoalContext context) {
      VillagerInventory inv = context.villager().getInventory();
      ResourceLocation cultureId = context.village().getCultureId();

      for (BuildingInstance shop : context.village().getBuildings()) {
         if (shop.isOperational()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(shop.getPlanId());
            if (plan != null && plan.shopId() != null) {
               ShopProfile profile = ShopProfileLoader.getProfile(cultureId, plan.shopId());
               if (profile != null && !profile.deliverTo().isEmpty()) {
                  List<DeliverResourcesToShopGoal.ItemCount> carriedItems = new ArrayList<>();
                  int totalCount = 0;

                  for (String itemName : profile.deliverTo()) {
                     Item item = ItemHelper.resolve(itemName);
                     if (item != null) {
                        int count = inv.getCount(item);
                        if (count > 0) {
                           carriedItems.add(new DeliverResourcesToShopGoal.ItemCount(item, count));
                           totalCount += count;
                        }
                     }
                  }

                  if (!carriedItems.isEmpty()) {
                     return new DeliverResourcesToShopGoal.DeliveryTarget(shop.getId(), carriedItems, totalCount);
                  }
               }
            }
         }
      }

      return null;
   }

   static class DeliverTask extends ProgressAwareTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final int ACTION_DURATION = 40;
      private DeliverResourcesToShopGoal.DeliverTask.State state = DeliverResourcesToShopGoal.DeliverTask.State.WALKING_TO_SHOP;
      private int actionTicks;
      @Nullable
      private final BuildingId shopId;
      private final List<DeliverResourcesToShopGoal.ItemCount> itemsToDeliver;

      DeliverTask(@Nullable BuildingId shopId, List<DeliverResourcesToShopGoal.ItemCount> itemsToDeliver) {
         this.shopId = shopId;
         this.itemsToDeliver = itemsToDeliver;
         if (shopId == null || itemsToDeliver.isEmpty()) {
            this.state = DeliverResourcesToShopGoal.DeliverTask.State.DONE;
         }
      }

      public ResourceLocation goalId() {
         return DeliverResourcesToShopGoal.ID;
      }

      public void tick(GoalContext ctx) {
         switch (this.state) {
            case WALKING_TO_SHOP:
               this.tickWalking(ctx);
               break;
            case DELIVERING:
               this.tickDelivering(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         BuildingInstance shop = this.shopId != null ? ctx.village().getBuilding(this.shopId) : null;
         BlockPos target = shop != null ? shop.getSellingPos() : null;
         if (target == null) {
            this.state = DeliverResourcesToShopGoal.DeliverTask.State.DONE;
         } else {
            VillagerNavigationManager nav = ctx.villager().getNavManager();
            if (nav.getDestination() == null) {
               nav.navigateTo(ctx.villager(), target, 0.5);
            }

            if (nav.isArrived(ctx.villager(), 3.0)) {
               nav.stop(ctx.villager());
               this.state = DeliverResourcesToShopGoal.DeliverTask.State.DELIVERING;
               this.actionTicks = 0;
               this.reportProgress();
            } else if (nav.isAbandoned()) {
               this.state = DeliverResourcesToShopGoal.DeliverTask.State.DONE;
            }
         }
      }

      private void tickDelivering(GoalContext ctx) {
         this.actionTicks++;
         if (this.actionTicks >= 40) {
            BuildingInstance shop = this.shopId != null ? ctx.village().getBuilding(this.shopId) : null;
            if (shop != null && shop.getInventory() != null) {
               VillagerInventory villagerInv = ctx.villager().getInventory();
               BuildingInventory shopInv = shop.getInventory();
               int delivered = 0;

               for (DeliverResourcesToShopGoal.ItemCount ic : this.itemsToDeliver) {
                  int has = villagerInv.getCount(ic.item);
                  if (has > 0) {
                     int toDeliver = Math.min(has, 256);
                     int added = shopInv.add(ctx.level(), ic.item, toDeliver);
                     if (added > 0) {
                        villagerInv.remove(ic.item, added);
                        delivered += added;
                     }
                  }
               }

               if (delivered > 0) {
                  DeliverResourcesToShopGoal.LOGGER.debug("[Millenaire] {} delivered {} items to shop", ctx.villager().getVillagerTypeId(), delivered);
                  this.reportProgress();
               }

               this.state = DeliverResourcesToShopGoal.DeliverTask.State.DONE;
            } else {
               this.state = DeliverResourcesToShopGoal.DeliverTask.State.DONE;
            }
         }
      }

      public boolean isFinished() {
         return this.state == DeliverResourcesToShopGoal.DeliverTask.State.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public TravelPhase getTravelPhase() {
         return this.state == DeliverResourcesToShopGoal.DeliverTask.State.WALKING_TO_SHOP ? TravelPhase.TRAVELLING : TravelPhase.AT_DESTINATION;
      }

      public List<ItemStack> getHeldItems(TravelPhase phase) {
         if (phase != TravelPhase.TRAVELLING) {
            return List.of();
         }

         List<ItemStack> items = new ArrayList<>();

         for (DeliverResourcesToShopGoal.ItemCount ic : this.itemsToDeliver) {
            items.add(new ItemStack(ic.item, 1));
         }

         return items;
      }

      @Nullable
      public Component getGoalLabel() {
         return switch (this.state) {
            case WALKING_TO_SHOP -> Component.translatable("goal.millenaire.transport.to_dest");
            case DELIVERING -> Component.translatable("goal.millenaire.transport.delivering");
            case DONE -> null;
         };
      }

      private enum State {
         WALKING_TO_SHOP,
         DELIVERING,
         DONE;
      }
   }

   private record DeliveryTarget(BuildingId shopId, List<DeliverResourcesToShopGoal.ItemCount> items, int totalCount) {
   }

   private record ItemCount(Item item, int count) {
   }
}
