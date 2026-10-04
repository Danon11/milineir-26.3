package org.millenaire.goal.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.millenaire.building.BuildingInstance;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class SocialiseGoal implements VillagerGoal {
   private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "socialise");
   private static final int MIN_WAIT_TICKS = 200;
   private static final int MAX_WAIT_TICKS = 400;

   public ResourceLocation id() {
      return ID;
   }

   public int computePriority(GoalContext context) {
      return 5;
   }

   public boolean isLeisure() {
      return true;
   }

   public boolean canStart(GoalContext context) {
      return true;
   }

   public VillagerTask start(GoalContext context) {
      BlockPos target = findLeisurePos(context);
      return new SocialiseGoal.SocialiseTask(target);
   }

   @Nullable
   private static BlockPos findLeisurePos(GoalContext ctx) {
      List<BuildingInstance> leisureBuildings = new ArrayList<>(ctx.village().getOperationalBuildingsWithTag("leisure"));
      if (leisureBuildings.isEmpty()) {
         BuildingInstance townhall = ctx.village().getTownhall();
         if (townhall != null) {
            leisureBuildings.add(townhall);
         }
      }

      if (leisureBuildings.isEmpty()) {
         return null;
      }

      BuildingInstance chosen = leisureBuildings.get(ThreadLocalRandom.current().nextInt(leisureBuildings.size()));
      BlockPos basePos = chosen.resolveNavigationTarget("leisurePos", "sellingPos", "sleepingPos");
      return findRandomSafePosition(ctx.level(), basePos);
   }

   static BlockPos findRandomSafePosition(Level level, BlockPos center) {
      List<BlockPos> safe = new ArrayList<>();

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = center.offset(dx, 0, dz);
            BlockPos below = pos.below();
            if (level.getBlockState(below).isSolidRender(level, below)
               && !level.getBlockState(pos).isSuffocating(level, pos)
               && !level.getBlockState(pos.above()).isSuffocating(level, pos.above())) {
               safe.add(pos);
            }
         }
      }

      return !safe.isEmpty() ? safe.get(ThreadLocalRandom.current().nextInt(safe.size())) : center;
   }

   static class SocialiseTask extends WalkAndWaitTask {
      SocialiseTask(@Nullable BlockPos target) {
         super(target, ThreadLocalRandom.current().nextInt(200, 401));
      }

      protected boolean allowRandomMoves() {
         return true;
      }

      public ResourceLocation goalId() {
         return SocialiseGoal.ID;
      }

      @Nullable
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "socialise");
      }

      boolean isSocialising() {
         return this.arrived && this.waitTicks < this.maxWaitTicks;
      }
   }
}
