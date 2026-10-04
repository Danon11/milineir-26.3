package org.millenaire.goal;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.TickConstants;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.slf4j.Logger;

public class GoalScheduler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_TASK_TICKS = 6000;
   private static final int MAX_IDLE_BACKOFF = 20;
   private final List<VillagerGoal> goals;
   private final Map<ResourceLocation, Long> lastGoalTick = new HashMap<>();
   @Nullable
   private VillagerGoal currentGoal;
   @Nullable
   private VillagerTask currentTask;
   private int taskTicks;
   private int idleBackoff = 0;
   private int idleBackoffDelay = 1;

   public int getTaskTicks() {
      return this.taskTicks;
   }

   public int getMaxTaskTicks() {
      return 6000;
   }

   public GoalScheduler(List<VillagerGoal> goals) {
      this.goals = goals;
   }

   public void tick(@Nullable GoalContext ctx) {
      try {
         this.tickInternal(ctx);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Exception in GoalScheduler.tick() — goal={}, task={} — forced reset",
            new Object[]{this.currentGoal != null ? this.currentGoal.id() : "null", this.currentTask != null ? this.currentTask.goalId() : "null", e}
         );
         this.currentGoal = null;
         this.currentTask = null;
         this.taskTicks = 0;
      }
   }

   private void tickInternal(@Nullable GoalContext ctx) {
      if (this.currentTask != null && this.currentTask.isFinished()) {
         this.currentTask.stop(ctx, StopReason.COMPLETED);
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }

         this.currentGoal = null;
         this.currentTask = null;
         this.taskTicks = 0;
         this.resetIdleBackoff();
      }

      if (this.currentTask != null && this.currentGoal != null && !isTimeAllowed(this.currentGoal, ctx)) {
         this.currentTask.stop(ctx, StopReason.INTERRUPTED);
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }

         this.currentGoal = null;
         this.currentTask = null;
         this.taskTicks = 0;
         this.resetIdleBackoff();
      }

      if (this.currentTask != null && this.currentGoal != null && this.currentGoal.isLeisure()) {
         for (VillagerGoal goal : this.goals) {
            if (!goal.isLeisure()
               && isTimeAllowed(goal, ctx)
               && this.isCooldownExpired(goal.id(), goal.reoccurDelayTicks(), ctx != null ? ctx.gameTime() : 0L)
               && goal.canStart(ctx)) {
               this.currentTask.stop(ctx, StopReason.INTERRUPTED);
               if (ctx != null) {
                  ctx.villager().getNavManager().stop(ctx.villager());
               }

               this.currentGoal = goal;
               this.currentTask = goal.start(ctx);
               this.lastGoalTick.put(goal.id(), ctx != null ? ctx.gameTime() : 0L);
               this.taskTicks = 0;
               this.resetIdleBackoff();
               break;
            }
         }
      }

      if (this.currentTask == null) {
         if (this.idleBackoff > 0) {
            this.idleBackoff--;
         } else {
            GoalScheduler.BestGoal best = this.pickBest(ctx);
            if (best != null) {
               this.currentGoal = best.goal();
               this.currentTask = best.goal().start(ctx);
               this.lastGoalTick.put(best.goal().id(), ctx != null ? ctx.gameTime() : 0L);
               this.resetIdleBackoff();
            } else {
               this.idleBackoff = this.idleBackoffDelay;
               this.idleBackoffDelay = Math.min(this.idleBackoffDelay * 2, 20);
            }
         }
      }

      if (this.currentTask != null) {
         if (this.currentTask.consumeProgress()) {
            this.taskTicks = 0;
         }

         this.taskTicks++;
         if (this.taskTicks > 6000) {
            LOGGER.warn(
               "Watchdog: task without progress for {} ticks, goal={} — forced stop", this.taskTicks, this.currentGoal != null ? this.currentGoal.id() : "null"
            );
            this.recordAbandon(ctx, "watchdog ticks=" + this.taskTicks);
            this.currentTask.stop(ctx, StopReason.IMPOSSIBLE);
            if (ctx != null) {
               ctx.villager().getNavManager().stop(ctx.villager());
            }

            this.currentGoal = null;
            this.currentTask = null;
            this.taskTicks = 0;
         } else {
            this.currentTask.tick(ctx);
         }
      }
   }

   private void resetIdleBackoff() {
      this.idleBackoff = 0;
      this.idleBackoffDelay = 1;
   }

   private boolean isCooldownExpired(ResourceLocation goalId, long cooldownTicks, long currentGameTime) {
      if (cooldownTicks <= 0L) {
         return true;
      }

      Long lastTick = this.lastGoalTick.get(goalId);
      return lastTick == null ? true : currentGameTime >= lastTick + cooldownTicks;
   }

   @Nullable
   private GoalScheduler.BestGoal pickBest(@Nullable GoalContext ctx) {
      VillagerGoal best = null;
      int bestPriority = Integer.MIN_VALUE;
      boolean bestIsLeisure = true;

      for (VillagerGoal goal : this.goals) {
         if (isTimeAllowed(goal, ctx) && this.isCooldownExpired(goal.id(), goal.reoccurDelayTicks(), ctx != null ? ctx.gameTime() : 0L) && goal.canStart(ctx)) {
            int priority = goal.computePriority(ctx);
            boolean isLeisure = goal.isLeisure();
            if (!isLeisure && bestIsLeisure || isLeisure == bestIsLeisure && priority > bestPriority) {
               bestPriority = priority;
               best = goal;
               bestIsLeisure = isLeisure;
            }
         }
      }

      return best != null ? new GoalScheduler.BestGoal(best, bestPriority) : null;
   }

   private static boolean isTimeAllowed(VillagerGoal goal, @Nullable GoalContext ctx) {
      if (ctx == null) {
         return true;
      }

      boolean isNight = TickConstants.isNight(ctx.level());
      return isNight && !goal.canBeDoneAtNight() ? false : isNight || goal.canBeDoneInDayTime();
   }

   public void forceTask(VillagerTask task, @Nullable GoalContext ctx) {
      if (this.currentTask != null) {
         try {
            this.currentTask.stop(ctx, StopReason.INTERRUPTED);
            if (ctx != null) {
               ctx.villager().getNavManager().stop(ctx.villager());
            }
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Exception in stop() during forceTask — goal={}", this.currentGoal != null ? this.currentGoal.id() : "null", e);
         }
      }

      this.currentGoal = null;
      this.currentTask = task;
      this.taskTicks = 0;
      this.resetIdleBackoff();
   }

   public void forceStop(@Nullable GoalContext ctx) {
      if (this.currentTask != null) {
         this.recordAbandon(ctx, "force-stop");
         this.currentTask.stop(ctx, StopReason.IMPOSSIBLE);
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }

         LOGGER.info("forceStop: forced stop of goal {}", this.currentGoal != null ? this.currentGoal.id() : "null");
         this.currentGoal = null;
         this.currentTask = null;
         this.taskTicks = 0;
      }
   }

   private void recordAbandon(@Nullable GoalContext ctx, String detail) {
      String goalSuffix = " goal=" + (this.currentGoal != null ? this.currentGoal.id() : "forced");
      if (ctx != null) {
         ctx.villager().getNavEventLog().record(ctx.gameTime(), NavEvent.Layer.SCHEDULER, NavEvent.Type.GOAL_ABANDONED, detail + goalSuffix);
      }

      NavigationCounters.incGoalAbandoned();
   }

   @Nullable
   public VillagerGoal getCurrentGoal() {
      return this.currentGoal;
   }

   @Nullable
   public VillagerTask getCurrentTask() {
      return this.currentTask;
   }

   @Nullable
   public ResourceLocation getCurrentGoalId() {
      if (this.currentGoal != null) {
         return this.currentGoal.id();
      } else {
         return this.currentTask != null ? this.currentTask.goalId() : null;
      }
   }

   private record BestGoal(VillagerGoal goal, int priority) {
   }
}
