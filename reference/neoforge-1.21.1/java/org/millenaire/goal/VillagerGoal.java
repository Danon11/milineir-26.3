package org.millenaire.goal;

import net.minecraft.resources.ResourceLocation;

public interface VillagerGoal {
   ResourceLocation id();

   int computePriority(GoalContext var1);

   boolean canStart(GoalContext var1);

   VillagerTask start(GoalContext var1);

   default boolean isLeisure() {
      return false;
   }

   default boolean canBeDoneAtNight() {
      return false;
   }

   default boolean canBeDoneInDayTime() {
      return true;
   }

   default long reoccurDelayTicks() {
      return 0L;
   }
}
