package org.millenaire.goal.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.millenaire.building.BuildingInstance;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.goal.visit.VisitGoalSchema;

public class PlayGoal implements VillagerGoal {
   private final ResourceLocation id;
   private final boolean withFriends;
   private final long reoccurDelay;

   public PlayGoal(boolean withFriends) {
      this.withFriends = withFriends;
      this.id = ResourceLocation.fromNamespaceAndPath("millenaire", withFriends ? "play_with_friends" : "play");
      this.reoccurDelay = withFriends ? 1200L : 600L;
   }

   public static PlayGoal fromSchema(VisitGoalSchema.Play s) {
      return new PlayGoal(s.withFriends());
   }

   public ResourceLocation id() {
      return this.id;
   }

   public boolean isLeisure() {
      return true;
   }

   public long reoccurDelayTicks() {
      return this.reoccurDelay;
   }

   public int computePriority(GoalContext context) {
      return 5 + ThreadLocalRandom.current().nextInt(10);
   }

   public boolean canStart(GoalContext context) {
      return this.withFriends ? this.findPlayingFriend(context) != null : this.findLeisurePos(context) != null;
   }

   public VillagerTask start(GoalContext context) {
      if (this.withFriends) {
         MillVillager friend = this.findPlayingFriend(context);
         return new PlayGoal.PlayWithFriendTask(friend);
      } else {
         BlockPos target = this.findLeisurePos(context);
         return new PlayGoal.PlayTask(target);
      }
   }

   @Nullable
   private MillVillager findPlayingFriend(GoalContext ctx) {
      List<MillVillager> candidates = new ArrayList<>();

      for (UUID uuid : ctx.village().getVillagerUuids()) {
         if (ctx.level().getEntity(uuid) instanceof MillVillager other && other != ctx.villager()) {
            GoalScheduler scheduler = other.getGoalScheduler();
            if (scheduler != null) {
               ResourceLocation otherGoalId = scheduler.getCurrentGoalId();
               if (otherGoalId != null) {
                  String path = otherGoalId.getPath();
                  if ("play".equals(path) || "play_with_friends".equals(path)) {
                     candidates.add(other);
                  }
               }
            }
         }
      }

      return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   @Nullable
   private BlockPos findLeisurePos(GoalContext ctx) {
      List<BuildingInstance> leisureBuildings = ctx.village().getOperationalBuildingsWithTag("leisure");
      if (leisureBuildings.isEmpty()) {
         return null;
      }

      BuildingInstance chosen = leisureBuildings.get(ThreadLocalRandom.current().nextInt(leisureBuildings.size()));
      BlockPos basePos = chosen.resolveNavigationTarget("leisurePos", "sellingPos", "sleepingPos");
      return SocialiseGoal.findRandomSafePosition(ctx.level(), basePos);
   }

   static class PlayTask extends WalkAndWaitTask {
      private static final int MAX_WAIT_TICKS = 200;

      PlayTask(@Nullable BlockPos target) {
         super(target, 200);
      }

      protected boolean allowRandomMoves() {
         return true;
      }

      public ResourceLocation goalId() {
         return ResourceLocation.fromNamespaceAndPath("millenaire", "play");
      }

      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return List.of(new ItemStack(Items.STICK));
      }

      @Nullable
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "play");
      }
   }

   static class PlayWithFriendTask implements VillagerTask {
      private static final double WALK_SPEED = 0.5;
      private static final int MAX_WAIT_TICKS = 200;
      @Nullable
      private final MillVillager friend;
      private boolean arrived;
      private int waitTicks;

      PlayWithFriendTask(@Nullable MillVillager friend) {
         this.friend = friend;
         if (friend == null) {
            this.arrived = true;
         }
      }

      public ResourceLocation goalId() {
         return ResourceLocation.fromNamespaceAndPath("millenaire", "play_with_friends");
      }

      public void tick(GoalContext ctx) {
         if (this.friend != null && !this.friend.isAlive()) {
            this.arrived = true;
            this.waitTicks = 200;
         } else {
            if (!this.arrived && this.friend != null) {
               VillagerNavigationManager nav = ctx.villager().getNavManager();
               nav.navigateTo(ctx.villager(), this.friend.blockPosition(), 0.5);
               if (nav.isArrived(ctx.villager(), 4.0)) {
                  this.arrived = true;
                  nav.stop(ctx.villager());
                  ctx.villager().getLookControl().setLookAt(this.friend);
               } else if (nav.isAbandoned()) {
                  this.arrived = true;
                  nav.stop(ctx.villager());
               }
            }

            if (this.arrived) {
               this.waitTicks++;
               if (this.friend != null && this.friend.isAlive()) {
                  ctx.villager().getLookControl().setLookAt(this.friend);
               }
            }
         }
      }

      public boolean isFinished() {
         return this.arrived && this.waitTicks >= 200;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return List.of(new ItemStack(Items.STICK));
      }

      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.arrived);
      }

      @Nullable
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "play");
      }
   }
}
