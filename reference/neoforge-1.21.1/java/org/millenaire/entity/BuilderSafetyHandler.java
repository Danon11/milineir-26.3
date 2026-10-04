package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.NavigationHelperUtils;
import org.millenaire.goal.VillagerTask;
import org.millenaire.goal.impl.BuildGoal;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public final class BuilderSafetyHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int COOLDOWN_TICKS = 200;
   private static final int ON_SITE_MARGIN = 4;
   private static final Map<UUID, Long> LAST_RESCUE_TICK = new ConcurrentHashMap<>();

   private static boolean isStuckRelatedDamage(DamageSource source) {
      return source.is(DamageTypes.IN_WALL)
         || source.is(DamageTypes.FALL)
         || source.is(DamageTypes.FALLING_BLOCK)
         || source.is(DamageTypes.LAVA)
         || source.is(DamageTypes.IN_FIRE)
         || source.is(DamageTypes.ON_FIRE)
         || source.is(DamageTypes.HOT_FLOOR)
         || source.is(DamageTypes.CRAMMING)
         || source.is(DamageTypes.DROWN);
   }

   private BuilderSafetyHandler() {
   }

   private static void pruneStaleEntries(long now) {
      LAST_RESCUE_TICK.entrySet().removeIf(e -> now - e.getValue() >= 200L);
   }

   private static boolean isInsideSiteFootprint(BlockPos pos, BuildingInstance site) {
      int w = site.getEffectiveWidth();
      int d = site.getEffectiveDepth();
      if (w > 0 && d > 0) {
         BlockPos origin = site.getOrigin();
         int minX = origin.getX() + site.getCachedMinX() - 4;
         int maxX = origin.getX() + site.getCachedMaxX() + 4;
         int minZ = origin.getZ() + site.getCachedMinZ() - 4;
         int maxZ = origin.getZ() + site.getCachedMaxZ() + 4;
         return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
      } else {
         return true;
      }
   }

   public static void onLivingDamage(Pre event) {
      if (event.getEntity() instanceof MillVillager villager) {
         if (villager.level() instanceof ServerLevel level) {
            if (isStuckRelatedDamage(event.getSource())) {
               BuildingId siteId = villager.getConstructionBuildingId();
               if (siteId != null) {
                  if (villager.getVillageId() != null) {
                     GoalScheduler scheduler = villager.getGoalScheduler();
                     if (scheduler != null) {
                        VillagerTask currentTask = scheduler.getCurrentTask();
                        if (currentTask != null && BuildGoal.ID.equals(currentTask.goalId())) {
                           long now = level.getGameTime();
                           Long last = LAST_RESCUE_TICK.get(villager.getUUID());
                           if (last == null || now - last >= 200L) {
                              Village village = Village.resolve(level, villager.getVillageId());
                              if (village != null) {
                                 BuildingInstance site = village.getBuilding(siteId);
                                 if (site != null) {
                                    BlockPos before = villager.blockPosition();
                                    if (isInsideSiteFootprint(before, site)) {
                                       BlockPos rescue = site.getFirstPointPos("pathStartPos");
                                       if (rescue == null) {
                                          rescue = site.getOrigin();
                                       }

                                       NavigationHelperUtils.teleportToSafeNearTarget(villager, rescue);
                                       villager.fallDistance = 0.0F;
                                       villager.clearFire();
                                       pruneStaleEntries(now);
                                       LAST_RESCUE_TICK.put(villager.getUUID(), now);
                                       event.setNewDamage(0.0F);
                                       LOGGER.info(
                                          "[Millenaire] Builder {} rescued from damage at {} -> TP target {} (site {})",
                                          new Object[]{villager.getVillagerDisplayName(), before.toShortString(), rescue.toShortString(), siteId}
                                       );
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
