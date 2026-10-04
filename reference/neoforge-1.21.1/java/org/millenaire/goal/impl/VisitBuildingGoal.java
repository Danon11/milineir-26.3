package org.millenaire.goal.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.PerVillagerThrottle;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.goal.visit.VisitGoalSchema;
import org.millenaire.item.ItemHelper;

public class VisitBuildingGoal implements VillagerGoal {
   private final ResourceLocation id;
   private final String buildingTag;
   private final int basePriority;
   private final int priorityRandom;
   private final int durationTicks;
   private final int reoccurDelayTicks;
   private final boolean allowRandomMoves;
   @Nullable
   private final String targetPosType;
   @Nullable
   private final String goalKey;
   private final int minimumHour;
   private final int maximumHour;
   private final int maxSimultaneousInBuilding;
   @Nullable
   private final String requiredTag;
   private final boolean leisure;
   @Nullable
   private final List<String> heldItems;
   @Nullable
   private final List<String> heldItemsDestination;
   @Nullable
   private final PerVillagerThrottle reoccurThrottle;

   public VisitBuildingGoal(
      ResourceLocation id,
      String buildingTag,
      int basePriority,
      int priorityRandom,
      int durationTicks,
      int reoccurDelayTicks,
      boolean allowRandomMoves,
      @Nullable String targetPosType,
      @Nullable String goalKey
   ) {
      this(
         id,
         buildingTag,
         basePriority,
         priorityRandom,
         durationTicks,
         reoccurDelayTicks,
         allowRandomMoves,
         targetPosType,
         goalKey,
         -1,
         -1,
         0,
         null,
         null,
         null,
         true
      );
   }

   public VisitBuildingGoal(
      ResourceLocation id,
      String buildingTag,
      int basePriority,
      int priorityRandom,
      int durationTicks,
      int reoccurDelayTicks,
      boolean allowRandomMoves,
      @Nullable String targetPosType,
      @Nullable String goalKey,
      int minimumHour,
      int maximumHour,
      int maxSimultaneousInBuilding,
      @Nullable String requiredTag,
      @Nullable List<String> heldItems,
      @Nullable List<String> heldItemsDestination,
      boolean leisure
   ) {
      this.id = id;
      this.buildingTag = buildingTag;
      this.basePriority = basePriority;
      this.priorityRandom = priorityRandom;
      this.durationTicks = durationTicks;
      this.reoccurDelayTicks = reoccurDelayTicks;
      this.allowRandomMoves = allowRandomMoves;
      this.targetPosType = targetPosType;
      this.goalKey = goalKey != null ? goalKey : id.getPath();
      this.minimumHour = minimumHour;
      this.maximumHour = maximumHour;
      this.maxSimultaneousInBuilding = maxSimultaneousInBuilding;
      this.requiredTag = requiredTag;
      this.leisure = leisure;
      this.heldItems = heldItems;
      this.heldItemsDestination = heldItemsDestination;
      this.reoccurThrottle = reoccurDelayTicks > 0 ? new PerVillagerThrottle(reoccurDelayTicks, 24000L, 6000L) : null;
   }

   public static VisitBuildingGoal fromSchema(VisitGoalSchema.VisitBuilding s) {
      return new VisitBuildingGoal(
         s.id(),
         s.buildingTag(),
         s.basePriority(),
         s.priorityRandom(),
         s.durationTicks(),
         s.reoccurDelayTicks(),
         s.allowRandomMoves(),
         s.targetPosType(),
         s.goalKey(),
         s.minimumHour(),
         s.maximumHour(),
         s.maxSimultaneousInBuilding(),
         s.requiredTag(),
         s.heldItems(),
         s.heldItemsDestination(),
         s.leisure()
      );
   }

   public ResourceLocation id() {
      return this.id;
   }

   public boolean isLeisure() {
      return this.leisure;
   }

   public String buildingTag() {
      return this.buildingTag;
   }

   @Nullable
   public List<String> heldItems() {
      return this.heldItems;
   }

   @Nullable
   public List<String> heldItemsDestination() {
      return this.heldItemsDestination;
   }

   public int computePriority(GoalContext context) {
      int random = this.priorityRandom > 0 ? ThreadLocalRandom.current().nextInt(this.priorityRandom) : 0;
      return this.basePriority + random;
   }

   public long reoccurDelayTicks() {
      return this.reoccurDelayTicks;
   }

   public boolean canStart(GoalContext context) {
      if (this.reoccurThrottle != null) {
         long currentTick = context.level().getServer().getTickCount();
         if (this.reoccurThrottle.isThrottled(context.villager().getUUID(), currentTick)) {
            return false;
         }
      }

      if (this.minimumHour >= 0 || this.maximumHour >= 0) {
         long dayTime = context.level().getDayTime() % 24000L;
         if (this.minimumHour >= 0 && dayTime < this.minimumHour) {
            return false;
         }

         if (this.maximumHour >= 0 && dayTime > this.maximumHour) {
            return false;
         }
      }

      return this.findTarget(context) != null;
   }

   public VillagerTask start(GoalContext context) {
      if (this.reoccurThrottle != null) {
         this.reoccurThrottle.record(context.villager().getUUID(), context.level().getServer().getTickCount());
      }

      VisitBuildingGoal.TargetInfo target = this.findTarget(context);
      return new VisitBuildingGoal.VisitTask(
         target != null ? target.pos() : null,
         target != null ? target.building() : null,
         this.durationTicks,
         this.allowRandomMoves,
         this.goalKey,
         this.heldItems,
         this.heldItemsDestination
      );
   }

   @Nullable
   private VisitBuildingGoal.TargetInfo findTarget(GoalContext ctx) {
      if (this.buildingTag.isEmpty()) {
         BuildingId homeId = ctx.villager().getHomeBuilding();
         BuildingInstance home = homeId != null ? ctx.village().getBuilding(homeId) : null;
         if (home != null && home.isOperational()) {
            if (this.requiredTag != null) {
               BuildingPlan plan = ModCultures.getBuildingPlan(home.getPlanId());
               if (plan == null || !plan.hasTag(this.requiredTag)) {
                  return null;
               }
            }

            BlockPos pos = this.resolveTargetPos(home);
            return pos != null ? new VisitBuildingGoal.TargetInfo(home, pos) : null;
         } else {
            return null;
         }
      } else {
         List<VisitBuildingGoal.TargetInfo> candidates = new ArrayList<>();

         for (BuildingInstance building : ctx.village().getOperationalBuildingsWithTag(this.buildingTag)) {
            if (this.requiredTag != null) {
               BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
               if (plan == null || !plan.hasTag(this.requiredTag)) {
                  continue;
               }
            }

            BlockPos craftingPos = building.getFirstPointPos("craftingPos");
            if (craftingPos != null) {
               double dist = ctx.villager().blockPosition().distSqr(craftingPos);
               if (dist <= 25.0) {
                  continue;
               }
            }

            if (this.maxSimultaneousInBuilding > 0) {
               int count = this.countVillagersWithGoalInBuilding(ctx, building);
               if (count >= this.maxSimultaneousInBuilding) {
                  continue;
               }
            }

            BlockPos pos = this.resolveTargetPos(building);
            if (pos != null) {
               candidates.add(new VisitBuildingGoal.TargetInfo(building, pos));
            }
         }

         return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
      }
   }

   private int countVillagersWithGoalInBuilding(GoalContext ctx, BuildingInstance building) {
      int count = 0;

      for (UUID uuid : ctx.village().getVillagerUuids()) {
         if (ctx.level().getEntity(uuid) instanceof MillVillager other && other != ctx.villager()) {
            GoalScheduler scheduler = other.getGoalScheduler();
            if (scheduler != null) {
               ResourceLocation currentGoalId = scheduler.getCurrentGoalId();
               if (this.id.equals(currentGoalId)
                  && scheduler.getCurrentTask() instanceof VisitBuildingGoal.VisitTask visitTask
                  && visitTask.targetBuilding == building) {
                  count++;
               }
            }
         }
      }

      return count;
   }

   @Nullable
   private BlockPos resolveTargetPos(BuildingInstance building) {
      if (this.targetPosType != null) {
         BlockPos pos = building.getFirstPointPos(this.targetPosType);
         if (pos != null) {
            return pos;
         }
      }

      BlockPos pos = building.getFirstPointPos("sleepingPos");
      return pos != null ? pos : building.getOrigin();
   }

   private record TargetInfo(BuildingInstance building, BlockPos pos) {
   }

   static class VisitTask extends WalkAndWaitTask {
      @Nullable
      final BuildingInstance targetBuilding;
      private final boolean randomMovesEnabled;
      private final String goalKey;
      private final List<ItemStack> travelHeldItems;
      private final List<ItemStack> destHeldItems;

      VisitTask(
         @Nullable BlockPos target,
         @Nullable BuildingInstance targetBuilding,
         int maxWaitTicks,
         boolean allowRandomMoves,
         String goalKey,
         @Nullable List<String> heldItems,
         @Nullable List<String> heldItemsDestination
      ) {
         super(target, maxWaitTicks);
         this.targetBuilding = targetBuilding;
         this.randomMovesEnabled = allowRandomMoves;
         this.goalKey = goalKey;
         this.travelHeldItems = resolveItems(heldItems);
         this.destHeldItems = heldItemsDestination != null ? resolveItems(heldItemsDestination) : this.travelHeldItems;
      }

      private static List<ItemStack> resolveItems(@Nullable List<String> itemIds) {
         if (itemIds != null && !itemIds.isEmpty()) {
            List<ItemStack> result = new ArrayList<>();

            for (String itemId : itemIds) {
               Item item = ItemHelper.resolve(itemId);
               if (item != null) {
                  result.add(new ItemStack(item));
               }
            }

            return List.copyOf(result);
         } else {
            return List.of();
         }
      }

      protected boolean allowRandomMoves() {
         return this.randomMovesEnabled;
      }

      public ResourceLocation goalId() {
         return ResourceLocation.fromNamespaceAndPath("millenaire", this.goalKey);
      }

      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return phase == TravelPhase.AT_DESTINATION ? this.destHeldItems : this.travelHeldItems;
      }

      @Nullable
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, this.goalKey);
      }
   }
}
