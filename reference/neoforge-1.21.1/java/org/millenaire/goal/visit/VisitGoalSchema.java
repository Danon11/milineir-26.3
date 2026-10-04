package org.millenaire.goal.visit;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public sealed interface VisitGoalSchema permits VisitGoalSchema.VisitBuilding, VisitGoalSchema.ObserveVillager, VisitGoalSchema.Play {
   ResourceLocation id();

   String goalKey();

   record ObserveVillager(
      ResourceLocation id,
      String goalKey,
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
   ) implements VisitGoalSchema {
   }

   record Play(ResourceLocation id, String goalKey, boolean withFriends) implements VisitGoalSchema {
   }

   record VisitBuilding(
      ResourceLocation id,
      String goalKey,
      String buildingTag,
      @Nullable String requiredTag,
      int basePriority,
      int priorityRandom,
      int durationTicks,
      int reoccurDelayTicks,
      boolean allowRandomMoves,
      boolean leisure,
      @Nullable String targetPosType,
      int minimumHour,
      int maximumHour,
      int maxSimultaneousInBuilding,
      @Nullable List<String> heldItems,
      @Nullable List<String> heldItemsDestination
   ) implements VisitGoalSchema {
   }
}
