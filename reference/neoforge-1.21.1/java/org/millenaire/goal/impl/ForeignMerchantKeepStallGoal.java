package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.SpecialPoint;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ModItems;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class ForeignMerchantKeepStallGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "keep_stall");

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return ThreadLocalRandom.current().nextInt(50);
   }

   public boolean canStart(GoalContext context) {
      MillVillager villager = context.villager();
      if (villager.getForeignMerchantStallId() < 0) {
         return false;
      }

      Village village = context.village();
      if (village == null) {
         return false;
      }

      BuildingInstance home = village.getBuilding(villager.getHomeBuilding());
      if (home == null) {
         return false;
      }

      List<SpecialPoint> stalls = home.getPointsByType("stall");
      if (stalls.isEmpty()) {
         stalls = home.getPointsByType("sellingPos");
      }

      return villager.getForeignMerchantStallId() < stalls.size();
   }

   public VillagerTask start(GoalContext context) {
      MillVillager villager = context.villager();
      Village village = context.village();
      BuildingInstance home = village.getBuilding(villager.getHomeBuilding());
      List<SpecialPoint> stalls = home.getPointsByType("stall");
      if (stalls.isEmpty()) {
         stalls = home.getPointsByType("sellingPos");
      }

      BlockPos stallPos = stalls.get(villager.getForeignMerchantStallId()).pos();
      return new ForeignMerchantKeepStallGoal.KeepStallTask(stallPos);
   }

   public static class KeepStallTask implements VillagerTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final int ACTION_DURATION = 1200;
      private static final int END_CHANCE = 600;
      private final BlockPos stallPos;
      private ForeignMerchantKeepStallGoal.KeepStallTask.State state = ForeignMerchantKeepStallGoal.KeepStallTask.State.WALKING_TO_STALL;
      private int waitTicks;

      public KeepStallTask(BlockPos stallPos) {
         this.stallPos = stallPos;
      }

      public ResourceLocation goalId() {
         return ForeignMerchantKeepStallGoal.ID;
      }

      public void tick(GoalContext ctx) {
         switch (this.state) {
            case WALKING_TO_STALL:
               this.tickWalking(ctx);
               break;
            case WAITING:
               this.tickWaiting(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), this.stallPos, 0.5);
         }

         if (nav.isArrived(ctx.villager(), 3.0)) {
            nav.stop(ctx.villager());
            this.state = ForeignMerchantKeepStallGoal.KeepStallTask.State.WAITING;
            this.waitTicks = 0;
            ForeignMerchantKeepStallGoal.LOGGER
               .debug("[Millenaire] Foreign merchant {} arrived at stall {}", ctx.villager().getVillagerTypeId(), this.stallPos.toShortString());
         } else if (nav.isAbandoned()) {
            nav.stop(ctx.villager());
            this.state = ForeignMerchantKeepStallGoal.KeepStallTask.State.DONE;
         }
      }

      private void tickWaiting(GoalContext ctx) {
         this.waitTicks++;
         Player nearest = ctx.level().getNearestPlayer(this.stallPos.getX() + 0.5, this.stallPos.getY() + 0.5, this.stallPos.getZ() + 0.5, 8.0, false);
         if (nearest != null) {
            ctx.villager().getLookControl().setLookAt(nearest, 30.0F, 30.0F);
         }

         if (this.waitTicks >= 1200) {
            if (ThreadLocalRandom.current().nextInt(600) == 0) {
               this.state = ForeignMerchantKeepStallGoal.KeepStallTask.State.DONE;
            }

            this.waitTicks = 0;
         }
      }

      public boolean isFinished() {
         return this.state == ForeignMerchantKeepStallGoal.KeepStallTask.State.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return phase == TravelPhase.AT_DESTINATION ? List.of(new ItemStack((ItemLike)ModItems.DENIER_ARGENT.get())) : List.of();
      }

      public List<ItemStack> getOffHandItems(TravelPhase phase) {
         return phase == TravelPhase.AT_DESTINATION ? List.of(new ItemStack((ItemLike)ModItems.PURSE.get())) : List.of();
      }

      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.state == ForeignMerchantKeepStallGoal.KeepStallTask.State.WAITING);
      }

      @Nullable
      public Component getGoalLabel() {
         return this.state == ForeignMerchantKeepStallGoal.KeepStallTask.State.DONE
            ? null
            : TaskLabels.labelForPhase(this.state == ForeignMerchantKeepStallGoal.KeepStallTask.State.WAITING, "keep_stall");
      }

      public BlockPos getStallPos() {
         return this.stallPos;
      }

      public boolean isAtStall() {
         return this.state == ForeignMerchantKeepStallGoal.KeepStallTask.State.WAITING;
      }

      private enum State {
         WALKING_TO_STALL,
         WAITING,
         DONE;
      }
   }
}
