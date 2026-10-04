package org.millenaire.goal;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.entity.MillVillager;

public final class GoalUtils {
   private GoalUtils() {
   }

   public static int countSimultaneous(GoalContext ctx, ResourceLocation goalId) {
      int count = 0;
      ServerLevel level = ctx.level();
      UUID selfUuid = ctx.villager().getUUID();

      for (UUID uuid : ctx.village().getVillagerUuids()) {
         if (!uuid.equals(selfUuid) && level.getEntity(uuid) instanceof MillVillager other) {
            GoalScheduler scheduler = other.getGoalScheduler();
            if (scheduler != null && goalId.equals(scheduler.getCurrentGoalId())) {
               count++;
            }
         }
      }

      return count;
   }
}
