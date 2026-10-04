package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalUtils;
import org.millenaire.goal.NavigationHelperUtils;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.path.PathEntry;
import org.millenaire.village.path.VillagePathManager;
import org.slf4j.Logger;

public class BuildPathGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "build_path");
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int NAVIGATE_STUCK_THRESHOLD = 100;
   private static final int WALKING_STUCK_TIMEOUT = 400;
   private static final int REMOTE_PLACE_WARN_THRESHOLD = 20;

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return GoalUtils.countSimultaneous(context, ID) > 0 ? 0 : 50;
   }

   public boolean canStart(GoalContext context) {
      if (!(Boolean)MillenaireServerConfig.SERVER.buildPaths.get()) {
         return false;
      }

      if (GoalUtils.countSimultaneous(context, ID) > 0) {
         return false;
      }

      VillagePathManager pm = context.village().getPathManager();
      return pm.hasPathsToClear() ? false : pm.hasPathsToBuild();
   }

   public VillagerTask start(GoalContext context) {
      return new BuildPathGoal.Task();
   }

   private class Task extends ProgressAwareTask {
      private BuildPathGoal.Task.State state = BuildPathGoal.Task.State.WALKING;
      @Nullable
      private BlockPos lastEntryPos;
      private int placeTimer;
      private int navigateStuckTicks;
      private int walkingStuckTicks;
      private int remotePlaceCount;
      private boolean warnedRemotePlace;
      private float cachedShovelEfficiency = 2.0F;
      private boolean efficiencyComputed = false;

      public ResourceLocation goalId() {
         return BuildPathGoal.ID;
      }

      public void tick(GoalContext ctx) {
         if (!this.efficiencyComputed) {
            this.efficiencyComputed = true;
            ToolCategory category = ToolCategoryRegistry.get("toolsshovel");
            if (category != null) {
               this.cachedShovelEfficiency = category.getBestDestroySpeed(
                  item -> ctx.villager().getInventory().getCount(item) > 0, Blocks.DIRT.defaultBlockState(), Items.WOODEN_SHOVEL
               );
            }
         }

         switch (this.state) {
            case WALKING:
               this.tickWalking(ctx);
               break;
            case PLACING:
               this.tickPlacing(ctx);
            case DONE:
         }
      }

      private void tickWalking(GoalContext ctx) {
         VillagePathManager pm = ctx.village().getPathManager();
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         PathEntry entry = pm.getNextBuildEntry();
         if (entry == null) {
            this.state = BuildPathGoal.Task.State.DONE;
         } else {
            BlockPos target = entry.pos();
            if (!target.equals(this.lastEntryPos)) {
               this.navigateStuckTicks = 0;
               this.walkingStuckTicks = 0;
               this.lastEntryPos = target;
            }

            nav.navigateTo(ctx.villager(), target, 0.5);
            this.walkingStuckTicks++;
            if (this.walkingStuckTicks > 400) {
               BuildPathGoal.LOGGER.debug("Path builder TP rescue near {} (stuck {} ticks)", target.toShortString(), this.walkingStuckTicks);
               NavigationHelperUtils.teleportToSafeNearTarget(ctx.villager(), target);
               this.navigateStuckTicks = 0;
               this.walkingStuckTicks = 0;
               this.reportProgress();
            } else if (nav.isArrivedHorizontal(ctx.villager(), 2.0)) {
               nav.stop(ctx.villager());
               this.state = BuildPathGoal.Task.State.PLACING;
               this.placeTimer = 10 - (int)this.cachedShovelEfficiency;
            } else {
               this.navigateStuckTicks++;
               if (this.navigateStuckTicks > 100) {
                  this.remotePlaceCount++;
                  BuildPathGoal.LOGGER
                     .debug(
                        "Path builder remote place #{} (stuck {} ticks) at {}",
                        new Object[]{this.remotePlaceCount, this.navigateStuckTicks, target.toShortString()}
                     );
                  if (this.remotePlaceCount == 20 && !this.warnedRemotePlace) {
                     this.warnedRemotePlace = true;
                     BuildPathGoal.LOGGER.warn("Path builder reached {} remote placements — path queue may be broken", 20);
                  }

                  this.placeBlock(ctx, entry);
               }
            }
         }
      }

      private void tickPlacing(GoalContext ctx) {
         this.placeTimer--;
         if (this.placeTimer <= 0) {
            VillagePathManager pm = ctx.village().getPathManager();
            PathEntry entry = pm.getNextBuildEntry();
            if (entry == null) {
               this.state = BuildPathGoal.Task.State.DONE;
               return;
            }

            this.placeBlock(ctx, entry);
         }
      }

      private void placeBlock(GoalContext ctx, PathEntry entry) {
         ServerLevel level = ctx.level();
         VillagePathManager.PlacementCheck check = VillagePathManager.canPlacePathAt(level, ctx.village(), entry.pos());
         if (check == VillagePathManager.PlacementCheck.ALLOWED) {
            level.setBlock(entry.pos(), entry.state(), 3);
         } else {
            BuildPathGoal.LOGGER.debug("Path entry at {} refused: {} — advancing", entry.pos().toShortString(), check);
         }

         VillagePathManager pm = ctx.village().getPathManager();
         pm.advanceBuild();
         ctx.village().markDirty();
         this.reportProgress();
         this.lastEntryPos = null;
         this.state = BuildPathGoal.Task.State.WALKING;
         if (!pm.hasPathsToBuild()) {
            this.state = BuildPathGoal.Task.State.DONE;
         }
      }

      public boolean isFinished() {
         return this.state == BuildPathGoal.Task.State.DONE;
      }

      public void stop(GoalContext context, StopReason reason) {
         if (context != null) {
            context.villager().getNavManager().stop(context.villager());
         }
      }

      private enum State {
         WALKING,
         PLACING,
         DONE;
      }
   }
}
