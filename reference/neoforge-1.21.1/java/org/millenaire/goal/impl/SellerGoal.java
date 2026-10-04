package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.millenaire.building.BuildingId;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ModItems;
import org.millenaire.village.VillagerAnnouncementHelper;
import org.slf4j.Logger;

public class SellerGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "be_seller");

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return 0;
   }

   public boolean canStart(GoalContext context) {
      return false;
   }

   public VillagerTask start(GoalContext context) {
      LOGGER.warn("[Millenaire] SellerGoal.start() called via scheduler — this should be forced via forceTask()");
      return new SellerGoal.SellerTask(BuildingId.random(), context.villager().blockPosition());
   }

   public static class SellerTask implements VillagerTask {
      private static final double ARRIVE_DISTANCE = 2.0;
      private static final double WALK_SPEED = 0.5;
      private static final double SELLING_RADIUS = 7.0;
      private final BuildingId shopBuildingId;
      private final BlockPos sellingPos;
      private SellerGoal.SellerTask.State state = SellerGoal.SellerTask.State.WALKING_TO_SELLING_POS;
      private boolean hasTraded = false;

      public SellerTask(BuildingId shopBuildingId, BlockPos sellingPos) {
         this.shopBuildingId = shopBuildingId;
         this.sellingPos = sellingPos;
      }

      public void markTraded() {
         this.hasTraded = true;
      }

      public ResourceLocation goalId() {
         return SellerGoal.ID;
      }

      public void tick(GoalContext ctx) {
         switch (this.state) {
            case WALKING_TO_SELLING_POS:
               this.tickWalking(ctx);
               break;
            case WAITING_FOR_PLAYER:
               this.tickWaiting(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), this.sellingPos, 0.5);
         }

         if (nav.isArrived(ctx.villager(), 2.0)) {
            nav.stop(ctx.villager());
            this.state = SellerGoal.SellerTask.State.WAITING_FOR_PLAYER;
            ctx.villager().setSelling(true);
            SellerGoal.LOGGER.debug("[Millenaire] Seller {} arrived at counter at {}", ctx.villager().getVillagerTypeId(), this.sellingPos.toShortString());
         } else if (nav.isAbandoned()) {
            nav.stop(ctx.villager());
            this.state = SellerGoal.SellerTask.State.DONE;
         }
      }

      private void tickWaiting(GoalContext ctx) {
         Player nearest = ctx.level().getNearestPlayer(this.sellingPos.getX() + 0.5, this.sellingPos.getY() + 0.5, this.sellingPos.getZ() + 0.5, 7.0, false);
         if (nearest != null) {
            ctx.villager().getLookControl().setLookAt(nearest, 30.0F, 30.0F);
         } else {
            this.state = SellerGoal.SellerTask.State.DONE;
         }
      }

      public boolean isFinished() {
         return this.state == SellerGoal.SellerTask.State.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().setSelling(false);
            ctx.villager().getNavManager().stop(ctx.villager());
            if (this.hasTraded
               && ctx.level().getNearestPlayer(this.sellingPos.getX() + 0.5, this.sellingPos.getY() + 0.5, this.sellingPos.getZ() + 0.5, 14.0, false) instanceof ServerPlayer sp
               )
             {
               VillagerAnnouncementHelper.sendAnnouncement(ctx.villager(), sp, "tradecomplete", "message.millenaire.trade_complete");
            }

            SellerGoal.LOGGER.debug("[Millenaire] Seller {} finished trading (hasTraded={})", ctx.villager().getVillagerTypeId(), this.hasTraded);
         }
      }

      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return phase == TravelPhase.AT_DESTINATION ? List.of(new ItemStack((ItemLike)ModItems.DENIER.get())) : List.of();
      }

      public List<ItemStack> getOffHandItems(TravelPhase phase) {
         return phase == TravelPhase.AT_DESTINATION ? List.of(new ItemStack((ItemLike)ModItems.PURSE.get())) : List.of();
      }

      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.state == SellerGoal.SellerTask.State.WAITING_FOR_PLAYER);
      }

      @Nullable
      public Component getGoalLabel() {
         return this.state == SellerGoal.SellerTask.State.DONE
            ? null
            : TaskLabels.labelForPhase(this.state == SellerGoal.SellerTask.State.WAITING_FOR_PLAYER, "be_seller");
      }

      public boolean isWaiting() {
         return this.state == SellerGoal.SellerTask.State.WAITING_FOR_PLAYER;
      }

      public BuildingId getShopBuildingId() {
         return this.shopBuildingId;
      }

      public BlockPos getSellingPos() {
         return this.sellingPos;
      }

      private enum State {
         WALKING_TO_SELLING_POS,
         WAITING_FOR_PLAYER,
         DONE;
      }
   }
}
