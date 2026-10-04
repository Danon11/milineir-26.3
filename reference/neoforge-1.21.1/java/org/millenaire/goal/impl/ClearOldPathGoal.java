package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalUtils;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.path.VillagePathManager;

public class ClearOldPathGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "clear_old_path");

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return GoalUtils.countSimultaneous(context, ID) > 0 ? 0 : 40;
   }

   public boolean canStart(GoalContext context) {
      if (!(Boolean)MillenaireServerConfig.SERVER.buildPaths.get()) {
         return false;
      } else {
         return GoalUtils.countSimultaneous(context, ID) > 0 ? false : context.village().getPathManager().hasPathsToClear();
      }
   }

   public VillagerTask start(GoalContext context) {
      return new ClearOldPathGoal.Task();
   }

   private class Task extends ProgressAwareTask {
      private ClearOldPathGoal.Task.State state = ClearOldPathGoal.Task.State.WALKING;
      @Nullable
      private BlockPos target;
      private int clearTimer;
      private float cachedShovelEfficiency = 2.0F;
      private boolean efficiencyComputed = false;

      public ResourceLocation goalId() {
         return ClearOldPathGoal.ID;
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

         VillagePathManager pm = ctx.village().getPathManager();
         VillagerNavigationManager nav = ctx.villager().getNavManager();
         switch (this.state) {
            case WALKING:
               this.target = pm.getNextClearPos();
               if (this.target == null) {
                  this.state = ClearOldPathGoal.Task.State.DONE;
                  return;
               }

               nav.navigateTo(ctx.villager(), this.target, 0.5);
               if (nav.isArrivedHorizontal(ctx.villager(), 2.0)) {
                  nav.stop(ctx.villager());
                  this.state = ClearOldPathGoal.Task.State.CLEARING;
                  this.clearTimer = 10 - (int)this.cachedShovelEfficiency;
                  return;
               }
               break;
            case CLEARING:
               this.clearTimer--;
               if (this.clearTimer <= 0) {
                  ServerLevel level = ctx.level();
                  BlockState below = level.getBlockState(this.target.below());
                  BlockState replacement = below.isSolid() ? below : Blocks.DIRT.defaultBlockState();
                  level.setBlock(this.target, replacement, 3);
                  pm.advanceClear();
                  ctx.village().markDirty();
                  this.reportProgress();
                  if (!pm.hasPathsToClear()) {
                     this.state = ClearOldPathGoal.Task.State.DONE;
                  } else {
                     this.state = ClearOldPathGoal.Task.State.WALKING;
                  }
               }
            case DONE:
         }
      }

      public boolean isFinished() {
         return this.state == ClearOldPathGoal.Task.State.DONE;
      }

      public void stop(GoalContext context, StopReason reason) {
         if (context != null) {
            context.villager().getNavManager().stop(context.villager());
         }
      }

      private enum State {
         WALKING,
         CLEARING,
         DONE;
      }
   }
}
