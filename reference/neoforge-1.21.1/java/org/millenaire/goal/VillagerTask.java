package org.millenaire.goal;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public interface VillagerTask {
   ResourceLocation goalId();

   void tick(GoalContext var1);

   boolean isFinished();

   void stop(GoalContext var1, StopReason var2);

   default List<ItemStack> getHeldItems(TravelPhase phase) {
      return List.of();
   }

   default List<ItemStack> getOffHandItems(TravelPhase phase) {
      return List.of();
   }

   default TravelPhase getTravelPhase() {
      return TravelPhase.AT_DESTINATION;
   }

   @Nullable
   default Component getGoalLabel() {
      ResourceLocation id = this.goalId();
      return id == null ? null : Component.translatable("goal.millenaire." + id.getPath());
   }

   default void reportProgress() {
   }

   default boolean consumeProgress() {
      return false;
   }

   default Map<String, String> getNavDebugInfo() {
      return Map.of();
   }
}
