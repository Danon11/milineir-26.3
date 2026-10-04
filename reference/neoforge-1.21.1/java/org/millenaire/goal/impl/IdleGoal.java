package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class IdleGoal implements VillagerGoal {
   private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "idle");

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return 0;
   }

   public boolean isLeisure() {
      return true;
   }

   public boolean canStart(GoalContext context) {
      return true;
   }

   public VillagerTask start(GoalContext context) {
      return new IdleGoal.IdleTask();
   }

   static class IdleTask implements VillagerTask {
      private static final int MAX_TICKS = 200;
      private static final double WALK_SPEED = 0.5;
      private static final double HOME_DISTANCE_THRESHOLD_SQ = 25.0;
      private boolean arrived;
      private int tickCount;
      @Nullable
      private BlockPos homeTarget;
      private boolean resolved;

      public ResourceLocation goalId() {
         return IdleGoal.ID;
      }

      public void tick(GoalContext ctx) {
         this.tickCount++;
         if (!this.resolved) {
            this.resolved = true;
            this.homeTarget = this.resolveHomePos(ctx);
            if (this.homeTarget == null || ctx.villager().blockPosition().distSqr(this.homeTarget) <= 25.0) {
               this.arrived = true;
            }
         }

         if (!this.arrived && this.homeTarget != null) {
            VillagerNavigationManager nav = ctx.villager().getNavManager();
            if (nav.getDestination() == null) {
               nav.navigateTo(ctx.villager(), this.homeTarget, 0.5);
            }

            if (nav.isArrived(ctx.villager(), 3.0) || nav.isAbandoned()) {
               this.arrived = true;
               nav.stop(ctx.villager());
            }
         }
      }

      @Nullable
      private BlockPos resolveHomePos(GoalContext ctx) {
         BuildingId homeId = ctx.villager().getHomeBuilding();
         if (homeId != null) {
            BuildingInstance home = ctx.village().getBuilding(homeId);
            if (home != null) {
               return home.getSleepingPos();
            }
         }

         BuildingInstance th = ctx.village().getTownhall();
         return th != null ? th.getSleepingPos() : null;
      }

      public boolean isFinished() {
         return this.tickCount >= 200;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public Component getGoalLabel() {
         return null;
      }
   }
}
