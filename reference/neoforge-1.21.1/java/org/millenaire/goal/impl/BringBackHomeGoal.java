package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.PerVillagerThrottle;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.slf4j.Logger;

public class BringBackHomeGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "bring_back_home");
   private static final int STANDARD_DELAY = 2000;
   private static final int IMMEDIATE_THRESHOLD = 16;
   private final PerVillagerThrottle throttle = new PerVillagerThrottle(2000);

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      int nbGoods = countBringBackGoods(context);
      return 10 + nbGoods * 3;
   }

   public boolean canStart(GoalContext context) {
      VillagerType vtype = ModCultures.getVillagerType(context.villager().getVillagerTypeId());
      if (vtype == null || vtype.bringBackHomeGoods().isEmpty()) {
         return false;
      }

      if (context.villager().getHomeBuilding() == null) {
         return false;
      }

      BuildingInstance home = context.village().getBuilding(context.villager().getHomeBuilding());
      if (home != null && home.getInventory() != null) {
         int nbGoods = countBringBackGoods(context);
         if (nbGoods <= 0) {
            return false;
         }

         if (nbGoods > 16) {
            return true;
         }

         long now = context.level().getGameTime();
         return this.throttle.shouldEvaluate(context.villager().getUUID(), now);
      } else {
         return false;
      }
   }

   public VillagerTask start(GoalContext context) {
      return new BringBackHomeGoal.BringBackHomeTask();
   }

   private static int countBringBackGoods(GoalContext context) {
      VillagerType vtype = ModCultures.getVillagerType(context.villager().getVillagerTypeId());
      if (vtype == null) {
         return 0;
      }

      VillagerInventory inv = context.villager().getInventory();
      int total = 0;

      for (Entry<Item, Integer> entry : inv.getAll().entrySet()) {
         if (isBringBackGood(entry.getKey(), vtype)) {
            total += entry.getValue();
         }
      }

      return total;
   }

   static boolean isBringBackGood(Item item, VillagerType vtype) {
      return vtype.resolvedBringBackHomeGoods().contains(item);
   }

   static class BringBackHomeTask extends ProgressAwareTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final int ACTION_DURATION = 40;
      private BringBackHomeGoal.BringBackHomeTask.State state = BringBackHomeGoal.BringBackHomeTask.State.WALKING_TO_HOME;
      private int actionTicks;

      public ResourceLocation goalId() {
         return BringBackHomeGoal.ID;
      }

      public void tick(GoalContext ctx) {
         switch (this.state) {
            case WALKING_TO_HOME:
               this.tickWalking(ctx);
               break;
            case TRANSFERRING:
               this.tickTransferring(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         BuildingInstance home = this.resolveHomeBuilding(ctx);
         BlockPos target = home != null ? home.getPathStartPos() : null;
         if (target == null) {
            this.state = BringBackHomeGoal.BringBackHomeTask.State.DONE;
         } else {
            VillagerNavigationManager nav = ctx.villager().getNavManager();
            if (nav.getDestination() == null) {
               nav.navigateTo(ctx.villager(), target, 0.5);
            }

            if (nav.isArrived(ctx.villager(), 3.0)) {
               nav.stop(ctx.villager());
               this.state = BringBackHomeGoal.BringBackHomeTask.State.TRANSFERRING;
               this.reportProgress();
            } else if (nav.isAbandoned()) {
               nav.stop(ctx.villager());
               BringBackHomeGoal.LOGGER.debug("[Millenaire] BringBackHome — navigation abandoned, goal finished");
               this.state = BringBackHomeGoal.BringBackHomeTask.State.DONE;
            }
         }
      }

      private void tickTransferring(GoalContext ctx) {
         this.actionTicks++;
         if (this.actionTicks >= 40) {
            BuildingInstance home = this.resolveHomeBuilding(ctx);
            if (home != null && home.getInventory() != null) {
               VillagerType vtype = ModCultures.getVillagerType(ctx.villager().getVillagerTypeId());
               if (vtype == null) {
                  this.state = BringBackHomeGoal.BringBackHomeTask.State.DONE;
               } else {
                  VillagerInventory villagerInv = ctx.villager().getInventory();
                  BuildingInventory buildingInv = home.getInventory();
                  int transferred = 0;

                  for (Entry<Item, Integer> entry : new ArrayList<>(villagerInv.getAll().entrySet())) {
                     if (BringBackHomeGoal.isBringBackGood(entry.getKey(), vtype)) {
                        int count = entry.getValue();
                        int added = buildingInv.add(ctx.level(), entry.getKey(), count);
                        if (added > 0) {
                           villagerInv.remove(entry.getKey(), added);
                           transferred += added;
                        }
                     }
                  }

                  if (transferred > 0) {
                     this.reportProgress();
                     BringBackHomeGoal.LOGGER.debug("[Millenaire] {} delivered {} items to home", ctx.villager().getVillagerTypeId(), transferred);
                  }

                  this.state = BringBackHomeGoal.BringBackHomeTask.State.DONE;
               }
            } else {
               BringBackHomeGoal.LOGGER.debug("[Millenaire] BringBackHome — no inventory in building, abandoning");
               this.state = BringBackHomeGoal.BringBackHomeTask.State.DONE;
            }
         }
      }

      @Nullable
      private BuildingInstance resolveHomeBuilding(GoalContext ctx) {
         BuildingId homeId = ctx.villager().getHomeBuilding();
         return homeId == null ? null : ctx.village().getBuilding(homeId);
      }

      public boolean isFinished() {
         return this.state == BringBackHomeGoal.BringBackHomeTask.State.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.state != BringBackHomeGoal.BringBackHomeTask.State.WALKING_TO_HOME);
      }

      @Nullable
      public Component getGoalLabel() {
         return this.state == BringBackHomeGoal.BringBackHomeTask.State.DONE
            ? null
            : TaskLabels.labelForPhase(this.state != BringBackHomeGoal.BringBackHomeTask.State.WALKING_TO_HOME, "bring_back_home");
      }

      private enum State {
         WALKING_TO_HOME,
         TRANSFERRING,
         DONE;
      }
   }
}
