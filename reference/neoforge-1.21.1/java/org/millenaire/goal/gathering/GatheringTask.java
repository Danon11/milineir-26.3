package org.millenaire.goal.gathering;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.building.BuildingId;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.millenaire.diagnostics.NavigationEventLog;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.NavigationHelperUtils;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.slf4j.Logger;

public class GatheringTask implements VillagerTask {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int DEFAULT_ARRIVAL_RANGE = 3;
   private static final double REMOTE_ACTION_HORIZONTAL_DIST_SQ = 64.0;
   private static final double STUCK_THRESHOLD_SQ = 0.25;
   private static final int MAX_STUCK_TELEPORTS = 3;
   private static final int INVALID_WINDOW_TICKS = 400;
   private static final int MAX_INVALIDS_IN_WINDOW = 3;
   private final GatheringType type;
   private final GatheringHandler handler;
   @Nullable
   private final BuildingId targetBuildingId;
   private GatheringTask.State state = GatheringTask.State.WALKING_TO_TARGET;
   @Nullable
   private GatheringTarget currentTarget;
   private int actionsPerformed;
   private int actionCooldown;
   private int cachedActionCooldownValue = -1;
   private int actionCooldownModCount = -1;
   private int stuckTicks;
   @Nullable
   private BlockPos lastPos;
   private boolean progressFlag;
   private int stuckTeleports;
   private final InvalidationWindow invalidationWindow = new InvalidationWindow(400, 3);
   private int actingTicksSinceTarget;
   private boolean remoteActing;
   @Nullable
   private VillagerInventory villagerInventory;
   @Nullable
   private final List<ItemStack> cachedTravelHeldItems;
   @Nullable
   private final List<ItemStack> cachedDestHeldItems;
   private static final Map<String, SoundEvent> SOUND_MAP = Map.of(
      "wood",
      SoundEvents.WOOD_PLACE,
      "stone",
      SoundEvents.ANVIL_USE,
      "metal",
      SoundEvents.ANVIL_USE,
      "glass",
      SoundEvents.GLASS_PLACE,
      "cloth",
      SoundEvents.WOOL_PLACE
   );

   GatheringTask(GatheringType type, GatheringHandler handler, @Nullable BuildingId targetBuildingId) {
      this.type = type;
      this.handler = handler;
      this.targetBuildingId = targetBuildingId;
      this.cachedTravelHeldItems = resolveItemIds(type.heldItems());
      List<String> destIds = type.heldItemsDestination();
      this.cachedDestHeldItems = destIds != null ? resolveItemIds(destIds) : this.cachedTravelHeldItems;
   }

   @Nullable
   private static List<ItemStack> resolveItemIds(@Nullable List<String> itemIds) {
      if (itemIds != null && !itemIds.isEmpty()) {
         List<ItemStack> stacks = new ArrayList<>();

         for (String itemId : itemIds) {
            Item item = ItemHelper.resolve(itemId);
            if (item != null) {
               stacks.add(new ItemStack(item));
            }
         }

         return stacks.isEmpty() ? null : List.copyOf(stacks);
      } else {
         return null;
      }
   }

   public ResourceLocation goalId() {
      return this.type.id();
   }

   @Nullable
   public BuildingId getTargetBuildingId() {
      return this.targetBuildingId;
   }

   public void reportProgress() {
      this.progressFlag = true;
   }

   public boolean consumeProgress() {
      if (this.progressFlag) {
         this.progressFlag = false;
         return true;
      } else {
         return false;
      }
   }

   public void tick(GoalContext ctx) {
      if (this.villagerInventory == null) {
         this.villagerInventory = ctx.villager().getInventory();
      }

      switch (this.state) {
         case WALKING_TO_TARGET:
            this.tickWalking(ctx);
            break;
         case ACTING:
            this.tickActing(ctx);
         case DONE:
      }
   }

   public boolean isFinished() {
      return this.state == GatheringTask.State.DONE;
   }

   public void stop(GoalContext ctx, StopReason reason) {
      if (ctx != null) {
         ctx.villager().getNavManager().stop(ctx.villager());
         if (reason == StopReason.COMPLETED) {
            LOGGER.debug("Gathering {} completed — {} actions performed", this.type.id(), this.actionsPerformed);
         }
      }
   }

   public TravelPhase getTravelPhase() {
      return this.state == GatheringTask.State.ACTING ? TravelPhase.AT_DESTINATION : TravelPhase.TRAVELLING;
   }

   @Nullable
   public Component getGoalLabel() {
      return TaskLabels.labelForPhase(this.state == GatheringTask.State.ACTING, this.type.id().getPath());
   }

   public List<ItemStack> getHeldItems(TravelPhase phase) {
      List<ItemStack> cached = phase == TravelPhase.AT_DESTINATION ? this.cachedDestHeldItems : this.cachedTravelHeldItems;
      if (cached != null) {
         return cached;
      } else {
         String toolCat = this.handler.getHeldToolCategoryId(this.type);
         if (toolCat != null) {
            Item fallback = toolFallback(toolCat);
            return List.of(this.getBestToolStack(toolCat, fallback));
         } else {
            Item item = this.handler.getDefaultHeldItem(this.type);
            return item != null ? List.of(new ItemStack(item)) : List.of();
         }
      }
   }

   private static Item toolFallback(String categoryId) {
      if (categoryId.contains("pickaxe")) {
         return Items.WOODEN_PICKAXE;
      } else if (categoryId.contains("axe")) {
         return Items.WOODEN_AXE;
      } else if (categoryId.contains("shovel")) {
         return Items.WOODEN_SHOVEL;
      } else {
         return categoryId.contains("hoe") ? Items.WOODEN_HOE : Items.WOODEN_PICKAXE;
      }
   }

   private ItemStack getBestToolStack(String categoryId, Item fallback) {
      if (this.villagerInventory == null) {
         return new ItemStack(fallback);
      }

      ToolCategory category = ToolCategoryRegistry.get(categoryId);
      if (category == null) {
         return new ItemStack(fallback);
      }

      ToolCategory.ToolEntry best = category.getBestOwned(item -> this.villagerInventory.getCount(item) > 0);
      return best != null && best.item() != null ? new ItemStack(best.item()) : new ItemStack(fallback);
   }

   private void tickWalking(GoalContext ctx) {
      if (this.currentTarget == null) {
         this.currentTarget = this.handler.findTarget(ctx, this.type, null);
         if (this.currentTarget == null) {
            LOGGER.debug("Gathering {} — no target found, stopping", this.type.id());
            this.state = GatheringTask.State.DONE;
            return;
         }
      }

      BlockPos currentPos = ctx.villager().blockPosition();
      BlockPos targetPos = this.currentTarget.navigationPos();
      double hDistSq = horizontalDistSqr(currentPos, targetPos);
      double arrivalDistSq = this.type.arrivalRange() * this.type.arrivalRange();
      if (hDistSq <= arrivalDistSq) {
         ctx.villager().getNavManager().stop(ctx.villager());
         this.state = GatheringTask.State.ACTING;
         this.actionCooldown = 0;
         this.actingTicksSinceTarget = 0;
         this.reportProgress();
      } else {
         int navY = getNavigationY(ctx, targetPos);
         BlockPos navDest = new BlockPos(targetPos.getX(), navY, targetPos.getZ());
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), navDest, this.type.walkSpeed());
         } else if (this.currentTarget instanceof GatheringTarget.EntityTarget) {
            double entityMovedSq = nav.getDestination().distSqr(navDest);
            if (entityMovedSq > 4.0) {
               nav.navigateTo(ctx.villager(), navDest, this.type.walkSpeed());
            }
         }

         if (this.lastPos == null) {
            this.lastPos = currentPos;
         }

         if (GatheringTaskHelper.isStuck(currentPos, this.lastPos, 0.25)) {
            this.stuckTicks++;
         } else {
            this.stuckTicks = 0;
            this.lastPos = currentPos;
         }

         if (this.stuckTicks > this.type.stuckTimeout()) {
            VillagerType vType = ModCultures.getVillagerType(ctx.villager().getVillagerTypeId());
            boolean canTeleport = vType == null || !vType.hasTag("noteleport");
            if (canTeleport && this.stuckTeleports < 3) {
               int safeY = ctx.level().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, targetPos.getX(), targetPos.getZ());
               if (Math.abs(safeY - targetPos.getY()) < 4) {
                  this.stuckTeleports++;
                  LOGGER.debug(
                     "Gathering {} — stuck TP to {},{},{} ({}/{})",
                     new Object[]{this.type.id(), targetPos.getX(), safeY, targetPos.getZ(), this.stuckTeleports, 3}
                  );
                  ctx.villager().teleportTo(targetPos.getX() + 0.5, safeY, targetPos.getZ() + 0.5);
                  ctx.villager().getNavManager().stop(ctx.villager());
                  if (this.currentTarget != null) {
                     this.handler.onStuckTeleport(ctx, this.type, this.currentTarget);
                  }

                  this.stuckTicks = 0;
                  this.lastPos = null;
                  this.reportProgress();
                  return;
               }
            }

            if (this.handler.supportsRemoteAction()) {
               double remoteDistSq = horizontalDistSqr(currentPos, targetPos);
               if (remoteDistSq <= 64.0) {
                  LOGGER.debug(
                     "Gathering {} — remote action (stuck {} ticks, horiz dist {}) at {}",
                     new Object[]{this.type.id(), this.stuckTicks, Math.sqrt(remoteDistSq), targetPos}
                  );
                  this.state = GatheringTask.State.ACTING;
                  this.remoteActing = true;
                  this.actionCooldown = 0;
                  this.actingTicksSinceTarget = 0;
                  this.reportProgress();
                  return;
               }
            }

            LOGGER.debug("Gathering {} — villager stuck for {} ticks, giving up", this.type.id(), this.type.stuckTimeout());
            this.state = GatheringTask.State.DONE;
         }
      }
   }

   private void tickActing(GoalContext ctx) {
      if (this.currentTarget == null) {
         this.state = GatheringTask.State.DONE;
      } else if (!this.handler.isTargetStillValid(ctx, this.type, this.currentTarget)) {
         this.handleTargetInvalidated(ctx);
      } else {
         this.actingTicksSinceTarget++;
         int watchdog = this.handler.actingWatchdogTicks(this.type);
         if (this.actingTicksSinceTarget > watchdog) {
            long now = ctx.gameTime();
            LOGGER.debug(
               "Gathering {} — ACTING watchdog fired after {} ticks (budget {}), abandoning",
               new Object[]{this.type.id(), this.actingTicksSinceTarget, watchdog}
            );
            NavigationEventLog log = ctx.villager().getNavEventLog();
            log.record(
               now,
               NavEvent.Layer.GATHERING,
               NavEvent.Type.ACTING_WATCHDOG_FIRED,
               "handler=" + this.handler.id() + " elapsed=" + this.actingTicksSinceTarget + " budget=" + watchdog
            );
            log.record(now, NavEvent.Layer.GATHERING, NavEvent.Type.GOAL_ABANDONED, "acting_watchdog handler=" + this.handler.id());
            NavigationCounters.incGoalAbandoned();
            this.state = GatheringTask.State.DONE;
         } else {
            BlockPos targetPos = this.currentTarget.navigationPos();
            double hDistSq = horizontalDistSqr(ctx.villager().blockPosition(), targetPos);
            double arrivalDistSq = this.type.arrivalRange() * this.type.arrivalRange();
            double maxDistSq = this.remoteActing ? 64.0 : arrivalDistSq;
            if (hDistSq > maxDistSq) {
               this.state = GatheringTask.State.WALKING_TO_TARGET;
               this.remoteActing = false;
               this.stuckTicks = 0;
               this.lastPos = null;
            } else {
               this.actionCooldown++;
               int currentModCount = ctx.villager().getInventory().modCount();
               if (this.cachedActionCooldownValue < 0 || currentModCount != this.actionCooldownModCount) {
                  this.cachedActionCooldownValue = this.handler.getActionCooldown(ctx, this.type);
                  this.actionCooldownModCount = currentModCount;
               }

               if (this.actionCooldown < this.cachedActionCooldownValue) {
                  if (this.actionCooldown % 20 == 0) {
                     ctx.villager().swing(InteractionHand.MAIN_HAND);
                  }
               } else {
                  this.actionCooldown = 0;
                  ctx.villager().getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                  boolean actionDone = this.handler.performAction(ctx, this.type, this.currentTarget);
                  if (actionDone) {
                     ctx.villager().swing(InteractionHand.MAIN_HAND);
                     this.playGatheringSound(ctx);
                     this.actionsPerformed++;
                     this.reportProgress();
                     if (this.actionsPerformed >= this.type.maxActionsPerTask()) {
                        LOGGER.debug("Gathering {} — action limit reached ({})", this.type.id(), this.type.maxActionsPerTask());
                        this.state = GatheringTask.State.DONE;
                        return;
                     }

                     GatheringTarget nextTarget = this.handler.findTarget(ctx, this.type, this.currentTarget);
                     if (nextTarget == null) {
                        LOGGER.debug("Gathering {} — no more targets, stopping after {} actions", this.type.id(), this.actionsPerformed);
                        this.state = GatheringTask.State.DONE;
                     } else {
                        this.currentTarget = nextTarget;
                        ctx.villager().getNavManager().stop(ctx.villager());
                        this.state = GatheringTask.State.WALKING_TO_TARGET;
                        this.remoteActing = false;
                        this.stuckTicks = 0;
                        this.lastPos = null;
                     }
                  } else {
                     ctx.villager().swing(InteractionHand.MAIN_HAND);
                     this.reportProgress();
                  }
               }
            }
         }
      }
   }

   private void handleTargetInvalidated(GoalContext ctx) {
      long now = ctx.gameTime();
      boolean exhausted = this.invalidationWindow.record(now);
      NavigationCounters.incTargetInvalid();
      ctx.villager()
         .getNavEventLog()
         .record(
            now,
            NavEvent.Layer.GATHERING,
            NavEvent.Type.TARGET_INVALID,
            "handler=" + this.handler.id() + " count=" + this.invalidationWindow.count() + "/" + this.invalidationWindow.maxCount()
         );
      if (exhausted) {
         LOGGER.debug("Gathering {} — {} target invalidations in {} ticks, abandoning goal", new Object[]{this.type.id(), this.invalidationWindow.count(), 400});
         ctx.villager()
            .getNavEventLog()
            .record(now, NavEvent.Layer.GATHERING, NavEvent.Type.GOAL_ABANDONED, "target_invalid livelock handler=" + this.handler.id());
         NavigationCounters.incGoalAbandoned();
         this.state = GatheringTask.State.DONE;
      } else {
         GatheringTarget next = this.handler.findTarget(ctx, this.type, null);
         if (next == null) {
            this.state = GatheringTask.State.DONE;
         } else {
            this.currentTarget = next;
            ctx.villager().getNavManager().stop(ctx.villager());
            this.state = GatheringTask.State.WALKING_TO_TARGET;
            this.remoteActing = false;
            this.actionCooldown = 0;
            this.stuckTicks = 0;
            this.lastPos = null;
         }
      }
   }

   private void playGatheringSound(GoalContext ctx) {
      String sound = this.type.sound();
      if (sound != null) {
         SoundEvent soundEvent = SOUND_MAP.get(sound);
         if (soundEvent != null) {
            ctx.villager().playSound(soundEvent, 0.5F, 0.9F + ctx.level().random.nextFloat() * 0.2F);
         }
      }
   }

   private static double horizontalDistSqr(BlockPos a, BlockPos b) {
      return NavigationHelperUtils.horizontalDistSq(a, b);
   }

   private static int getNavigationY(GoalContext ctx, BlockPos targetPos) {
      if (ctx.level().isLoaded(targetPos)) {
         int groundY = ctx.level().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, targetPos.getX(), targetPos.getZ());
         return targetPos.getY() <= groundY ? targetPos.getY() : groundY;
      } else {
         return targetPos.getY();
      }
   }

   public Map<String, String> getNavDebugInfo() {
      Map<String, String> info = new LinkedHashMap<>();
      info.put("gatherState", this.state.name());
      info.put("type", this.type.id().getPath());
      info.put("target", this.currentTarget != null ? this.currentTarget.navigationPos().toShortString() : "null");
      info.put("actions", this.actionsPerformed + "/" + this.type.maxActionsPerTask());
      info.put("stuckTicks", this.stuckTicks + "/" + this.type.stuckTimeout());
      if (this.remoteActing) {
         info.put("remoteActing", "true");
      }

      return info;
   }

   enum State {
      WALKING_TO_TARGET,
      ACTING,
      DONE;
   }
}
