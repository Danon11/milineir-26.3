package org.millenaire.goal.impl;

import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalUtils;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class SpotGatheringGoal implements VillagerGoal {
   private final ResourceLocation id;
   private final String buildingTag;
   private final int maxConcurrent;
   private final int priorityBase;
   private final int priorityMultiplier;
   private final SpotGatheringGoal.StockSource stockSource;
   @Nullable
   private final Supplier<Item> stockItem;
   private final int townhallLimit;
   private final SpotGatheringGoal.SpotFinder spotFinder;
   private final SpotGatheringGoal.SpotCondition spotCondition;
   private final SpotGatheringGoal.SpotAction action;
   private final boolean swingHand;
   private static final long STANDARD_DELAY = 2000L;

   public SpotGatheringGoal(
      ResourceLocation id,
      String buildingTag,
      int maxConcurrent,
      int priorityBase,
      int priorityMultiplier,
      SpotGatheringGoal.StockSource stockSource,
      @Nullable Supplier<Item> stockItem,
      int townhallLimit,
      SpotGatheringGoal.SpotFinder spotFinder,
      SpotGatheringGoal.SpotCondition spotCondition,
      SpotGatheringGoal.SpotAction action,
      boolean swingHand
   ) {
      this.id = id;
      this.buildingTag = buildingTag;
      this.maxConcurrent = maxConcurrent;
      this.priorityBase = priorityBase;
      this.priorityMultiplier = priorityMultiplier;
      this.stockSource = stockSource;
      this.stockItem = stockItem;
      this.townhallLimit = townhallLimit;
      this.spotFinder = spotFinder;
      this.spotCondition = spotCondition;
      this.action = action;
      this.swingHand = swingHand;
   }

   public ResourceLocation id() {
      return this.id;
   }

   public long reoccurDelayTicks() {
      return 2000L;
   }

   public int computePriority(GoalContext ctx) {
      int p = this.priorityBase;
      if (this.priorityMultiplier != 0 && this.stockItem != null) {
         int stock = this.countStock(ctx);
         p = this.priorityBase - stock * this.priorityMultiplier;
      }

      int simultaneous = GoalUtils.countSimultaneous(ctx, this.id);

      for (int i = 0; i < simultaneous; i++) {
         p /= 2;
      }

      return p;
   }

   public boolean canStart(GoalContext ctx) {
      if (GoalUtils.countSimultaneous(ctx, this.id) >= this.maxConcurrent) {
         return false;
      }

      if (this.townhallLimit > 0 && this.stockItem != null && this.countStock(ctx) >= this.townhallLimit) {
         return false;
      }

      for (BuildingInstance building : this.findBuildings(ctx)) {
         for (BlockPos pos : this.spotFinder.getPositions(building)) {
            if (this.spotCondition.test(ctx.level(), pos)) {
               return true;
            }
         }
      }

      return false;
   }

   public VillagerTask start(GoalContext ctx) {
      return new SpotGatheringGoal.SpotGatheringTask();
   }

   private int countStock(GoalContext ctx) {
      return switch (this.stockSource) {
         case NONE -> 0;
         case TOWNHALL -> this.countTownhallStock(ctx);
         case FARM -> this.countFarmStock(ctx);
      };
   }

   private int countTownhallStock(GoalContext ctx) {
      BuildingInstance townhall = ctx.village().getTownhall();
      if (townhall == null) {
         return 0;
      }

      BuildingInventory inv = townhall.getInventory();
      return inv == null ? 0 : inv.getCount(ctx.level(), this.stockItem.get());
   }

   private int countFarmStock(GoalContext ctx) {
      int count = 0;

      for (BuildingInstance b : this.findBuildings(ctx)) {
         if (b.getInventory() != null) {
            count += b.getInventory().getCount(ctx.level(), this.stockItem.get());
         }
      }

      return count;
   }

   private List<BuildingInstance> findBuildings(GoalContext ctx) {
      return ctx.village().getOperationalBuildingsWithTag(this.buildingTag);
   }

   @FunctionalInterface
   public interface SpotAction {
      void perform(GoalContext var1, BlockPos var2);
   }

   @FunctionalInterface
   public interface SpotCondition {
      boolean test(ServerLevel var1, BlockPos var2);
   }

   @FunctionalInterface
   public interface SpotFinder {
      List<BlockPos> getPositions(BuildingInstance var1);
   }

   private class SpotGatheringTask extends ProgressAwareTask {
      @Nullable
      private BlockPos targetSpot;
      private boolean navigating;
      private boolean finished;
      private int actionTicks;
      private static final int ACTION_DURATION = 20;

      public ResourceLocation goalId() {
         return SpotGatheringGoal.this.id;
      }

      public void tick(GoalContext ctx) {
         if (!this.finished) {
            VillagerNavigationManager nav = ctx.villager().getNavManager();
            if (this.targetSpot == null) {
               this.targetSpot = this.findClosestSpot(ctx);
               if (this.targetSpot == null) {
                  this.finished = true;
               } else {
                  nav.navigateTo(ctx.villager(), this.targetSpot, 0.5);
                  this.navigating = true;
               }
            } else {
               if (this.navigating) {
                  if (nav.isAbandoned()) {
                     this.finished = true;
                     return;
                  }

                  if (!nav.isArrived(ctx.villager(), 2.0)) {
                     return;
                  }

                  nav.stop(ctx.villager());
                  this.navigating = false;
                  this.actionTicks = 0;
               }

               this.actionTicks++;
               if (this.actionTicks >= 20) {
                  SpotGatheringGoal.this.action.perform(ctx, this.targetSpot);
                  if (SpotGatheringGoal.this.swingHand) {
                     ctx.villager().swing(InteractionHand.MAIN_HAND);
                  }

                  this.reportProgress();
                  this.targetSpot = this.findClosestSpot(ctx);
                  if (this.targetSpot == null) {
                     this.finished = true;
                  } else {
                     nav.navigateTo(ctx.villager(), this.targetSpot, 0.5);
                     this.navigating = true;
                  }
               }
            }
         }
      }

      public boolean isFinished() {
         return this.finished;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      @Nullable
      private BlockPos findClosestSpot(GoalContext ctx) {
         BlockPos villagerPos = ctx.villager().blockPosition();
         BlockPos best = null;
         double bestDistSq = Double.MAX_VALUE;

         for (BuildingInstance building : SpotGatheringGoal.this.findBuildings(ctx)) {
            for (BlockPos pos : SpotGatheringGoal.this.spotFinder.getPositions(building)) {
               if (SpotGatheringGoal.this.spotCondition.test(ctx.level(), pos)) {
                  double distSq = pos.distSqr(villagerPos);
                  if (distSq < bestDistSq) {
                     bestDistSq = distSq;
                     best = pos;
                  }
               }
            }
         }

         return best;
      }
   }

   public enum StockSource {
      NONE,
      TOWNHALL,
      FARM;
   }
}
