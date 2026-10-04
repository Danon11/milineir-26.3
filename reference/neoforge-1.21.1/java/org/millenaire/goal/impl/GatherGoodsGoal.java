package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.millenaire.TickConstants;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.PerVillagerThrottle;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.slf4j.Logger;

public class GatherGoodsGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "gather_goods");
   private static final int PRIORITY = 500;
   static final double SCAN_RADIUS_H = 20.0;
   static final double SCAN_RADIUS_V = 10.0;
   private final PerVillagerThrottle throttle = new PerVillagerThrottle(100);

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return 500;
   }

   public boolean canStart(GoalContext context) {
      VillagerType vtype = ModCultures.getVillagerType(context.villager().getVillagerTypeId());
      if (vtype == null || vtype.resolvedCollectGoods().isEmpty()) {
         return false;
      }

      if (TickConstants.isNight(context.level())) {
         return false;
      }

      long currentTick = context.level().getServer().getTickCount();
      return !this.throttle.shouldEvaluate(context.villager().getUUID(), currentTick) ? false : findClosestItem(context, vtype) != null;
   }

   public VillagerTask start(GoalContext context) {
      VillagerType vtype = ModCultures.getVillagerType(context.villager().getVillagerTypeId());
      ItemEntity target = vtype != null ? findClosestItem(context, vtype) : null;
      return new GatherGoodsGoal.GatherGoodsTask(target);
   }

   @Nullable
   static ItemEntity findClosestItem(GoalContext context, VillagerType vtype) {
      AABB scanBox = context.villager().getBoundingBox().inflate(20.0, 10.0, 20.0);
      List<ItemEntity> items = context.level().getEntitiesOfClass(ItemEntity.class, scanBox);
      ItemEntity closest = null;
      double closestDistSq = Double.MAX_VALUE;

      for (ItemEntity itemEntity : items) {
         if (!itemEntity.isRemoved()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
            if (vtype.resolvedCollectGoods().contains(itemId)) {
               double distSq = context.villager().distanceToSqr(itemEntity);
               if (distSq < closestDistSq) {
                  closestDistSq = distSq;
                  closest = itemEntity;
               }
            }
         }
      }

      return closest;
   }

   static class GatherGoodsTask extends ProgressAwareTask {
      private static final double ARRIVE_DISTANCE = 5.0;
      private static final double WALK_SPEED = 0.5;
      private static final int WAIT_DURATION = 40;
      private static final int STUCK_ITEM_TELEPORT_TICKS = 200;
      private GatherGoodsGoal.GatherGoodsTask.State state;
      @Nullable
      private ItemEntity targetItem;
      private int waitTicks;
      private int totalTicks;

      GatherGoodsTask(@Nullable ItemEntity targetItem) {
         this.targetItem = targetItem;
         this.state = targetItem != null ? GatherGoodsGoal.GatherGoodsTask.State.WALKING : GatherGoodsGoal.GatherGoodsTask.State.DONE;
      }

      public ResourceLocation goalId() {
         return GatherGoodsGoal.ID;
      }

      public void tick(GoalContext ctx) {
         this.totalTicks++;
         switch (this.state) {
            case WALKING:
               this.tickWalking(ctx);
               break;
            case WAITING:
               this.tickWaiting(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         if (this.targetItem == null || this.targetItem.isRemoved()) {
            VillagerType vtype = ModCultures.getVillagerType(ctx.villager().getVillagerTypeId());
            if (vtype != null) {
               this.targetItem = GatherGoodsGoal.findClosestItem(ctx, vtype);
            }

            if (this.targetItem == null || this.targetItem.isRemoved()) {
               this.state = GatherGoodsGoal.GatherGoodsTask.State.DONE;
               return;
            }

            ctx.villager().getNavManager().stop(ctx.villager());
         }

         BlockPos targetPos = this.targetItem.blockPosition();
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), targetPos, 0.5);
         }

         if (nav.isArrived(ctx.villager(), 5.0)) {
            nav.stop(ctx.villager());
            this.state = GatherGoodsGoal.GatherGoodsTask.State.WAITING;
            this.waitTicks = 0;
            this.reportProgress();
         } else if (nav.isAbandoned() || this.totalTicks >= 200) {
            nav.stop(ctx.villager());
            this.doStuckItemTeleport(ctx);
         }
      }

      private void tickWaiting(GoalContext ctx) {
         this.waitTicks++;
         if (this.waitTicks >= 40) {
            this.state = GatherGoodsGoal.GatherGoodsTask.State.DONE;
            this.reportProgress();
         }
      }

      private void doStuckItemTeleport(GoalContext ctx) {
         if (this.targetItem != null && !this.targetItem.isRemoved()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(this.targetItem.getItem().getItem());
            ctx.villager().getInventory().add(this.targetItem.getItem().getItem(), 1);
            this.targetItem.discard();
            GatherGoodsGoal.LOGGER.debug("[Millenaire] {} stuck — force-collected 1x {} (item teleport)", ctx.villager().getVillagerTypeId(), itemId);
         }

         this.state = GatherGoodsGoal.GatherGoodsTask.State.DONE;
      }

      public boolean isFinished() {
         return this.state == GatherGoodsGoal.GatherGoodsTask.State.DONE;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public TravelPhase getTravelPhase() {
         return this.state == GatherGoodsGoal.GatherGoodsTask.State.WALKING ? TravelPhase.TRAVELLING : TravelPhase.AT_DESTINATION;
      }

      @Nullable
      public Component getGoalLabel() {
         return Component.translatable("goal.millenaire.gather_goods");
      }

      private enum State {
         WALKING,
         WAITING,
         DONE;
      }
   }
}
