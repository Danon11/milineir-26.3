package org.millenaire.goal;

import net.minecraft.network.chat.Component;

public final class TaskLabels {
   private TaskLabels() {
   }

   public static TravelPhase phaseFor(boolean atDestination) {
      return atDestination ? TravelPhase.AT_DESTINATION : TravelPhase.TRAVELLING;
   }

   public static Component labelForPhase(boolean atDestination, String goalKey) {
      return Component.translatable("goal.millenaire." + goalKey);
   }
}
