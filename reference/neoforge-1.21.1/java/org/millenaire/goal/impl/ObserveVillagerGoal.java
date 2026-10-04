package org.millenaire.goal.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.PerVillagerThrottle;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.goal.visit.VisitGoalSchema;
import org.millenaire.item.ItemHelper;

public class ObserveVillagerGoal implements VillagerGoal {
   private final ResourceLocation id;
   @Nullable
   private final String targetGoalTag;
   @Nullable
   private final Set<ResourceLocation> targetGoalIds;
   private final int basePriority;
   private final int priorityRandom;
   private final int durationTicks;
   private final int reoccurDelayTicks;
   private final int minimumHour;
   private final int maximumHour;
   @Nullable
   private final String buildingTag;
   @Nullable
   private final String requiredTag;
   @Nullable
   private final List<String> heldItems;
   @Nullable
   private final PerVillagerThrottle reoccurThrottle;

   public ObserveVillagerGoal(ResourceLocation id, String targetGoalTag, int basePriority, int priorityRandom, int durationTicks, int reoccurDelayTicks) {
      this(id, targetGoalTag, null, basePriority, priorityRandom, durationTicks, reoccurDelayTicks, -1, -1, null, null, null);
   }

   public ObserveVillagerGoal(
      ResourceLocation id,
      String targetGoalTag,
      int basePriority,
      int priorityRandom,
      int durationTicks,
      int reoccurDelayTicks,
      @Nullable List<String> heldItems
   ) {
      this(id, targetGoalTag, null, basePriority, priorityRandom, durationTicks, reoccurDelayTicks, -1, -1, null, null, heldItems);
   }

   public ObserveVillagerGoal(
      ResourceLocation id,
      @Nullable String targetGoalTag,
      @Nullable Set<ResourceLocation> targetGoalIds,
      int basePriority,
      int priorityRandom,
      int durationTicks,
      int reoccurDelayTicks,
      int minimumHour,
      int maximumHour,
      @Nullable String buildingTag,
      @Nullable String requiredTag,
      @Nullable List<String> heldItems
   ) {
      this.id = id;
      this.targetGoalTag = targetGoalTag;
      this.targetGoalIds = targetGoalIds;
      this.basePriority = basePriority;
      this.priorityRandom = priorityRandom;
      this.durationTicks = durationTicks;
      this.reoccurDelayTicks = reoccurDelayTicks;
      this.minimumHour = minimumHour;
      this.maximumHour = maximumHour;
      this.buildingTag = buildingTag;
      this.requiredTag = requiredTag;
      this.heldItems = heldItems;
      this.reoccurThrottle = reoccurDelayTicks > 0 ? new PerVillagerThrottle(reoccurDelayTicks, 24000L, 6000L) : null;
   }

   public static ObserveVillagerGoal fromSchema(VisitGoalSchema.ObserveVillager s) {
      return new ObserveVillagerGoal(
         s.id(),
         s.targetGoalTag(),
         s.targetGoalIds(),
         s.basePriority(),
         s.priorityRandom(),
         s.durationTicks(),
         s.reoccurDelayTicks(),
         s.minimumHour(),
         s.maximumHour(),
         s.buildingTag(),
         s.requiredTag(),
         s.heldItems()
      );
   }

   public ResourceLocation id() {
      return this.id;
   }

   public boolean isLeisure() {
      return true;
   }

   public String buildingTag() {
      return this.buildingTag;
   }

   public Set<ResourceLocation> targetGoalIds() {
      return this.targetGoalIds;
   }

   @Nullable
   public List<String> heldItems() {
      return this.heldItems;
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

      return this.buildingTag != null && !this.villageHasBuildingWithTags(context) ? false : this.findTargetVillager(context) != null;
   }

   public VillagerTask start(GoalContext context) {
      if (this.reoccurThrottle != null) {
         this.reoccurThrottle.record(context.villager().getUUID(), context.level().getServer().getTickCount());
      }

      MillVillager target = this.findTargetVillager(context);
      return new ObserveVillagerGoal.ObserveTask(target, this.durationTicks, this.id.getPath(), this.heldItems);
   }

   @Nullable
   private MillVillager findTargetVillager(GoalContext ctx) {
      List<MillVillager> candidates = new ArrayList<>();

      for (UUID uuid : ctx.village().getVillagerUuids()) {
         if (ctx.level().getEntity(uuid) instanceof MillVillager other && other != ctx.villager()) {
            GoalScheduler scheduler = other.getGoalScheduler();
            if (scheduler != null) {
               ResourceLocation currentGoalId = scheduler.getCurrentGoalId();
               if (currentGoalId != null && this.goalMatches(currentGoalId, scheduler)) {
                  candidates.add(other);
               }
            }
         }
      }

      return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   private boolean goalMatches(ResourceLocation goalId, GoalScheduler scheduler) {
      if (this.targetGoalIds != null && !this.targetGoalIds.isEmpty()) {
         return this.targetGoalIds.contains(goalId);
      } else {
         return this.targetGoalTag != null ? this.goalMatchesTag(goalId, scheduler) : false;
      }
   }

   private boolean villageHasBuildingWithTags(GoalContext ctx) {
      List<BuildingInstance> tagged = ctx.village().getOperationalBuildingsWithTag(this.buildingTag);
      if (tagged.isEmpty()) {
         return false;
      }

      if (this.requiredTag == null) {
         return true;
      }

      for (BuildingInstance building : tagged) {
         BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
         if (plan != null && plan.hasTag(this.requiredTag)) {
            return true;
         }
      }

      return false;
   }

   private boolean goalMatchesTag(ResourceLocation goalId, GoalScheduler scheduler) {
      String path = goalId.getPath();

      return switch (this.targetGoalTag) {
         case "tag_agriculture" -> path.contains("harvest")
            || path.contains("plant")
            || path.contains("breed")
            || path.contains("shear")
            || path.contains("slaughter");
         case "tag_construction" -> path.equals("build");
         case "tag_smithing" -> path.contains("craft_norman") || path.contains("craft_steel") || path.contains("craft_stone");
         case "tag_producefoodalcohol", "tag_producealcohol" -> path.contains("craft_cider")
            || path.contains("craft_calva")
            || path.contains("craft_boudin")
            || path.contains("craft_tripes");
         case "tag_producefood" -> path.contains("craft_bread") || path.contains("craft_cake") || path.contains("cook_");
         default -> path.contains(this.targetGoalTag);
      };
   }

   static class ObserveTask implements VillagerTask {
      private static final double WALK_SPEED = 0.5;
      @Nullable
      private final MillVillager targetVillager;
      private final int maxWaitTicks;
      private final String goalKey;
      private boolean arrived;
      private int waitTicks;
      private final List<ItemStack> resolvedHeldItems;

      ObserveTask(@Nullable MillVillager targetVillager, int maxWaitTicks, String goalKey, @Nullable List<String> heldItems) {
         this.targetVillager = targetVillager;
         this.maxWaitTicks = maxWaitTicks;
         this.goalKey = goalKey;
         if (targetVillager == null) {
            this.arrived = true;
         }

         this.resolvedHeldItems = resolveItems(heldItems);
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

      public ResourceLocation goalId() {
         return ResourceLocation.fromNamespaceAndPath("millenaire", this.goalKey);
      }

      public void tick(GoalContext ctx) {
         if (this.targetVillager != null && !this.targetVillager.isAlive()) {
            this.arrived = true;
            this.waitTicks = this.maxWaitTicks;
         } else {
            if (!this.arrived && this.targetVillager != null) {
               VillagerNavigationManager nav = ctx.villager().getNavManager();
               nav.navigateTo(ctx.villager(), this.targetVillager.blockPosition(), 0.5);
               if (nav.isArrived(ctx.villager(), 8.0)) {
                  this.arrived = true;
                  nav.stop(ctx.villager());
                  ctx.villager().getLookControl().setLookAt(this.targetVillager);
               } else if (nav.isAbandoned()) {
                  this.arrived = true;
                  nav.stop(ctx.villager());
               }
            }

            if (this.arrived) {
               this.waitTicks++;
               if (this.targetVillager != null && this.targetVillager.isAlive()) {
                  ctx.villager().getLookControl().setLookAt(this.targetVillager);
               }
            }
         }
      }

      public boolean isFinished() {
         return this.arrived && this.waitTicks >= this.maxWaitTicks;
      }

      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return this.resolvedHeldItems;
      }

      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.arrived);
      }

      @Nullable
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, this.goalKey);
      }
   }
}
