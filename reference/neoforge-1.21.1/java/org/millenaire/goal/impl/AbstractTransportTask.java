package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.slf4j.Logger;

abstract class AbstractTransportTask extends ProgressAwareTask {
   private static final Logger LOGGER = LogUtils.getLogger();
   static final double ARRIVE_DISTANCE = 3.0;
   static final double WALK_SPEED = 0.5;
   static final int ACTION_DURATION = 40;
   protected AbstractTransportTask.State state = AbstractTransportTask.State.WALKING_TO_SOURCE;
   protected int actionTicks;
   protected boolean hasPickedUpGoods;

   @Nullable
   protected abstract BuildingInstance resolveSourceBuilding(GoalContext var1);

   @Nullable
   protected abstract BuildingInstance resolveDestBuilding(GoalContext var1);

   protected abstract List<? extends AbstractTransportTask.ItemRef> getItemsToTransfer();

   protected abstract String pickupLogLabel();

   protected abstract String deliveryLogLabel();

   public void tick(GoalContext ctx) {
      switch (this.state) {
         case WALKING_TO_SOURCE:
            this.tickWalkingToSource(ctx);
            break;
         case PICKING_UP:
            this.tickPickingUp(ctx);
            break;
         case WALKING_TO_DEST:
            this.tickWalkingToDest(ctx);
            break;
         case DELIVERING:
            this.tickDelivering(ctx);
         case DONE:
      }
   }

   private void tickWalkingToSource(GoalContext ctx) {
      BuildingInstance source = this.resolveSourceBuilding(ctx);
      BlockPos target = source != null ? source.getSellingPos() : null;
      if (target == null) {
         this.state = AbstractTransportTask.State.DONE;
      } else {
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), target, 0.5);
         }

         if (nav.isArrived(ctx.villager(), 3.0)) {
            nav.stop(ctx.villager());
            this.state = AbstractTransportTask.State.PICKING_UP;
            this.actionTicks = 0;
            this.reportProgress();
         } else if (nav.isAbandoned()) {
            this.state = AbstractTransportTask.State.DONE;
         }
      }
   }

   protected void tickPickingUp(GoalContext ctx) {
      this.actionTicks++;
      if (this.actionTicks >= 40) {
         BuildingInstance source = this.resolveSourceBuilding(ctx);
         if (source != null && source.getInventory() != null) {
            VillagerInventory villagerInv = ctx.villager().getInventory();
            BuildingInventory sourceInv = source.getInventory();
            int picked = 0;

            for (AbstractTransportTask.ItemRef ia : this.getItemsToTransfer()) {
               int removed = sourceInv.remove(ctx.level(), ia.item(), ia.count());
               if (removed > 0) {
                  villagerInv.add(ia.item(), removed);
                  picked += removed;
               }
            }

            if (picked > 0) {
               this.hasPickedUpGoods = true;
               LOGGER.debug("[Millenaire] {} picked up {} items {}", new Object[]{ctx.villager().getVillagerTypeId(), picked, this.pickupLogLabel()});
               this.reportProgress();
            }

            this.state = AbstractTransportTask.State.WALKING_TO_DEST;
            this.actionTicks = 0;
         } else {
            this.state = AbstractTransportTask.State.DONE;
         }
      }
   }

   private void tickWalkingToDest(GoalContext ctx) {
      BuildingInstance dest = this.resolveDestBuilding(ctx);
      BlockPos target = dest != null ? dest.getSellingPos() : null;
      if (target == null) {
         this.state = AbstractTransportTask.State.DONE;
      } else {
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), target, 0.5);
         }

         if (nav.isArrived(ctx.villager(), 3.0)) {
            nav.stop(ctx.villager());
            this.state = AbstractTransportTask.State.DELIVERING;
            this.actionTicks = 0;
            this.reportProgress();
         } else if (nav.isAbandoned()) {
            this.state = AbstractTransportTask.State.DONE;
         }
      }
   }

   protected void tickDelivering(GoalContext ctx) {
      this.actionTicks++;
      if (this.actionTicks >= 40) {
         BuildingInstance dest = this.resolveDestBuilding(ctx);
         if (dest != null && dest.getInventory() != null) {
            VillagerInventory villagerInv = ctx.villager().getInventory();
            BuildingInventory destInv = dest.getInventory();
            int delivered = 0;

            for (AbstractTransportTask.ItemRef ia : this.getItemsToTransfer()) {
               int has = villagerInv.getCount(ia.item());
               if (has > 0) {
                  int added = destInv.add(ctx.level(), ia.item(), has);
                  if (added > 0) {
                     villagerInv.remove(ia.item(), added);
                     delivered += added;
                  }
               }
            }

            if (delivered > 0) {
               LOGGER.debug("[Millénaire] {} a livré {} items {}", new Object[]{ctx.villager().getVillagerTypeId(), delivered, this.deliveryLogLabel()});
               this.reportProgress();
            }

            this.hasPickedUpGoods = false;
            this.state = AbstractTransportTask.State.DONE;
         } else {
            this.state = AbstractTransportTask.State.DONE;
         }
      }
   }

   public boolean isFinished() {
      return this.state == AbstractTransportTask.State.DONE;
   }

   public void stop(GoalContext ctx, StopReason reason) {
      if (ctx != null) {
         ctx.villager().getNavManager().stop(ctx.villager());
         if (this.hasPickedUpGoods) {
            BuildingInstance source = this.resolveSourceBuilding(ctx);
            if (source != null && source.getInventory() != null) {
               VillagerInventory villagerInv = ctx.villager().getInventory();
               BuildingInventory sourceInv = source.getInventory();
               int returned = 0;

               for (AbstractTransportTask.ItemRef ia : this.getItemsToTransfer()) {
                  int has = villagerInv.getCount(ia.item());
                  if (has > 0) {
                     int added = sourceInv.add(ctx.level(), ia.item(), has);
                     if (added > 0) {
                        villagerInv.remove(ia.item(), added);
                        returned += added;
                     }
                  }
               }

               if (returned > 0) {
                  LOGGER.debug("[Millenaire] {} interrupted — {} items returned to source building", ctx.villager().getVillagerTypeId(), returned);
               }
            }
         }
      }
   }

   public TravelPhase getTravelPhase() {
      return switch (this.state) {
         case WALKING_TO_SOURCE, WALKING_TO_DEST -> TravelPhase.TRAVELLING;
         default -> TravelPhase.AT_DESTINATION;
      };
   }

   interface ItemRef {
      Item item();

      int count();
   }

   protected enum State {
      WALKING_TO_SOURCE,
      PICKING_UP,
      WALKING_TO_DEST,
      DELIVERING,
      DONE;
   }
}
