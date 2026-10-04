package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.slf4j.Logger;

public class GetToolGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "get_tool");
   private static final int PRIORITY = 550;
   private static final int MAX_SIMULTANEOUS_PER_SHOP = 2;

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return 550;
   }

   public boolean canStart(GoalContext context) {
      VillagerType vType = ModCultures.getVillagerType(context.villager().getVillagerTypeId());
      if (vType != null && (!vType.toolNeededClasses().isEmpty() || !vType.itemsNeeded().isEmpty())) {
         List<GetToolGoal.ToolPickup> upgrades = this.findUpgrades(context, vType);
         if (upgrades.isEmpty()) {
            return false;
         }

         BuildingId targetShop = upgrades.getFirst().shopId;
         return this.countGetToolTasksForShop(context, targetShop) < 2;
      } else {
         return false;
      }
   }

   private int countGetToolTasksForShop(GoalContext context, BuildingId shopId) {
      int count = 0;

      for (UUID uuid : context.village().getVillagerUuids()) {
         if (context.level().getEntity(uuid) instanceof MillVillager other && other != context.villager()) {
            GoalScheduler scheduler = other.getGoalScheduler();
            if (scheduler != null
               && ID.equals(scheduler.getCurrentGoalId())
               && scheduler.getCurrentTask() instanceof GetToolGoal.GetToolTask gtt
               && shopId.equals(gtt.getShopBuildingId())) {
               count++;
            }
         }
      }

      return count;
   }

   public VillagerTask start(GoalContext context) {
      VillagerType vType = ModCultures.getVillagerType(context.villager().getVillagerTypeId());
      List<GetToolGoal.ToolPickup> upgrades = vType != null ? this.findUpgrades(context, vType) : List.of();
      if (upgrades.isEmpty()) {
         return new GetToolGoal.GetToolTask(List.of(), null);
      }

      BuildingId firstShop = upgrades.getFirst().shopId;
      List<GetToolGoal.ToolPickup> fromFirstShop = upgrades.stream().filter(u -> u.shopId.equals(firstShop)).toList();
      return new GetToolGoal.GetToolTask(fromFirstShop, firstShop);
   }

   private List<GetToolGoal.ToolPickup> findUpgrades(GoalContext context, VillagerType vType) {
      VillagerInventory villagerInv = context.villager().getInventory();
      List<GetToolGoal.ToolPickup> result = new ArrayList<>();

      for (BuildingInstance building : context.village().getBuildings()) {
         BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
         if (plan != null && plan.shopId() != null && building.getInventory() != null && building.isOperational()) {
            BuildingInventory shopInv = building.getInventory();

            for (ResourceLocation neededId : vType.itemsNeeded()) {
               Item neededItem = ItemHelper.resolve(neededId);
               if (neededItem != null && villagerInv.getCount(neededItem) <= 0 && shopInv.getCount(context.level(), neededItem) > 0) {
                  result.add(new GetToolGoal.ToolPickup(building.getId(), neededItem, "itemneeded"));
               }
            }

            for (String categoryId : vType.toolNeededClasses()) {
               ToolCategory category = ToolCategoryRegistry.get(categoryId);
               if (category != null) {
                  ToolCategory.ToolEntry bestOwned = category.getBestOwned(item -> villagerInv.getCount(item) > 0);
                  ToolCategory.ToolEntry upgrade = category.findUpgrade(bestOwned, item -> shopInv.getCount(context.level(), item) > 0);
                  if (upgrade != null && upgrade.item() != null) {
                     result.add(new GetToolGoal.ToolPickup(building.getId(), upgrade.item(), categoryId));
                  }
               }
            }

            if (!result.isEmpty()) {
               return result;
            }
         }
      }

      return result;
   }

   static class GetToolTask extends ProgressAwareTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final int ACTION_DURATION = 10;
      private GetToolGoal.GetToolTask.State state;
      private final List<GetToolGoal.ToolPickup> pickups;
      @Nullable
      private final BuildingId shopBuildingId;
      private int actionTicks;

      @Nullable
      BuildingId getShopBuildingId() {
         return this.shopBuildingId;
      }

      GetToolTask(List<GetToolGoal.ToolPickup> pickups, @Nullable BuildingId shopBuildingId) {
         this.pickups = pickups;
         this.shopBuildingId = shopBuildingId;
         this.state = pickups.isEmpty() ? GetToolGoal.GetToolTask.State.DONE : GetToolGoal.GetToolTask.State.WALKING_TO_SHOP;
      }

      public ResourceLocation goalId() {
         return GetToolGoal.ID;
      }

      public void tick(GoalContext ctx) {
         switch (this.state) {
            case WALKING_TO_SHOP:
               this.tickWalking(ctx);
               break;
            case PICKING_UP:
               this.tickPickingUp(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         BuildingInstance shop = this.shopBuildingId != null ? ctx.village().getBuilding(this.shopBuildingId) : null;
         BlockPos target = shop != null ? shop.getSellingPos() : null;
         if (target == null) {
            this.state = GetToolGoal.GetToolTask.State.DONE;
         } else {
            VillagerNavigationManager nav = ctx.villager().getNavManager();
            if (nav.getDestination() == null) {
               nav.navigateTo(ctx.villager(), target, 0.5);
            }

            if (nav.isArrived(ctx.villager(), 3.0)) {
               nav.stop(ctx.villager());
               this.state = GetToolGoal.GetToolTask.State.PICKING_UP;
               this.actionTicks = 0;
               this.reportProgress();
            } else if (nav.isAbandoned()) {
               nav.stop(ctx.villager());
               this.state = GetToolGoal.GetToolTask.State.DONE;
            }
         }
      }

      private void tickPickingUp(GoalContext ctx) {
         this.actionTicks++;
         if (this.actionTicks >= 10) {
            BuildingInstance shop = this.shopBuildingId != null ? ctx.village().getBuilding(this.shopBuildingId) : null;
            if (shop != null && shop.getInventory() != null) {
               VillagerInventory villagerInv = ctx.villager().getInventory();
               BuildingInventory shopInv = shop.getInventory();

               for (GetToolGoal.ToolPickup pickup : this.pickups) {
                  int removed = shopInv.remove(ctx.level(), pickup.item, 1);
                  if (removed > 0) {
                     villagerInv.add(pickup.item, 1);
                     GetToolGoal.LOGGER.debug("[Millénaire] {} a récupéré un outil ({}) au shop", ctx.villager().getVillagerTypeId(), pickup.categoryId);
                     this.reportProgress();
                  }
               }

               this.state = GetToolGoal.GetToolTask.State.DONE;
            } else {
               this.state = GetToolGoal.GetToolTask.State.DONE;
            }
         }
      }

      public boolean isFinished() {
         return this.state == GetToolGoal.GetToolTask.State.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public TravelPhase getTravelPhase() {
         return this.state == GetToolGoal.GetToolTask.State.WALKING_TO_SHOP ? TravelPhase.TRAVELLING : TravelPhase.AT_DESTINATION;
      }

      @Nullable
      public Component getGoalLabel() {
         return switch (this.state) {
            case WALKING_TO_SHOP -> Component.translatable("goal.millenaire.get_tool.walking");
            case PICKING_UP -> Component.translatable("goal.millenaire.get_tool.picking_up");
            case DONE -> null;
         };
      }

      private enum State {
         WALKING_TO_SHOP,
         PICKING_UP,
         DONE;
      }
   }

   record ToolPickup(BuildingId shopId, Item item, String categoryId) {
   }
}
