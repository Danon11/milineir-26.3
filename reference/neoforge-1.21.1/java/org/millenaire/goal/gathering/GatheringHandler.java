package org.millenaire.goal.gathering;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.goal.GoalContext;

public interface GatheringHandler {
   String id();

   boolean canStart(GoalContext var1, GatheringType var2);

   @Nullable
   GatheringTarget findTarget(GoalContext var1, GatheringType var2, @Nullable GatheringTarget var3);

   boolean performAction(GoalContext var1, GatheringType var2, GatheringTarget var3);

   default boolean isTargetStillValid(GoalContext ctx, GatheringType type, GatheringTarget target) {
      return true;
   }

   default int actingWatchdogTicks(GatheringType type) {
      return Math.max(600, type.actionCooldown() * 2);
   }

   default void onStuckTeleport(GoalContext ctx, GatheringType type, GatheringTarget target) {
   }

   default List<String> validate(GatheringType type) {
      return List.of();
   }

   default boolean supportsRemoteAction() {
      return false;
   }

   @Nullable
   default BuildingInstance resolveBuildingLimitTarget(GoalContext ctx, GatheringType type) {
      return null;
   }

   default int getActionCooldown(GoalContext ctx, GatheringType type) {
      return type.actionCooldown();
   }

   @Nullable
   default String getHeldToolCategoryId(GatheringType type) {
      return null;
   }

   @Nullable
   default Item getDefaultHeldItem(GatheringType type) {
      return null;
   }

   default void onClear() {
   }
}
