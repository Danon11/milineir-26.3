package org.millenaire.goal;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.pathfinder.Path;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillageWaypointGraph;
import org.slf4j.Logger;

public class WaypointNavigator {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final double WAYPOINT_ARRIVE_DISTANCE_SQ = 9.0;
   private static final int STUCK_REPATH_TIMEOUT = 100;
   private static final int STUCK_TELEPORT_TIMEOUT = 600;
   private static final double STUCK_THRESHOLD_SQ = 0.25;
   private static final double WALK_SPEED = 0.5;
   private static final int MAX_TELEPORTS = 3;
   private static final long REBUILD_THROTTLE = 60L;
   private WaypointNavigator.State state = WaypointNavigator.State.IDLE;
   @Nullable
   private List<BlockPos> macroPath;
   private int currentWaypointIndex;
   @Nullable
   private BlockPos destination;
   private int stuckTicks;
   @Nullable
   private BlockPos lastPos;
   private int teleportCount;
   @Nullable
   private Village village;
   @Nullable
   private VillageWaypointGraph graph;
   private boolean firstLegRebuildAttempted;
   private boolean lastLegRebuildAttempted;

   public int getDebugStuckTicks() {
      return this.stuckTicks;
   }

   public int getDebugWaypointIndex() {
      return this.currentWaypointIndex;
   }

   public int getDebugPathSize() {
      return this.macroPath != null ? this.macroPath.size() : 0;
   }

   public int getDebugTeleportCount() {
      return this.teleportCount;
   }

   public void navigateTo(MillVillager villager, BlockPos dest, @Nullable Village village, @Nullable VillageWaypointGraph graph) {
      this.destination = dest;
      this.stuckTicks = 0;
      this.lastPos = null;
      this.currentWaypointIndex = 0;
      this.teleportCount = 0;
      this.village = village;
      this.graph = graph;
      this.firstLegRebuildAttempted = false;
      this.lastLegRebuildAttempted = false;
      BlockPos currentPos = villager.blockPosition();
      double dist = Math.sqrt(currentPos.distSqr(dest));
      if (!(dist <= 48.0) && graph != null && graph.isAvailable()) {
         List<BlockPos> path = graph.findPath(currentPos, dest);
         if (path.isEmpty()) {
            this.macroPath = null;
            this.state = WaypointNavigator.State.NAVIGATING_TO_DESTINATION;
            villager.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 0.5);
         } else {
            this.macroPath = path;
            this.state = WaypointNavigator.State.NAVIGATING_TO_WAYPOINT;
            BlockPos firstWp = this.macroPath.get(0);
            villager.getNavigation().moveTo(firstWp.getX() + 0.5, firstWp.getY(), firstWp.getZ() + 0.5, 0.5);
            LOGGER.debug("[Millenaire] Macro navigation started: {} waypoints to {}", path.size(), dest.toShortString());
         }
      } else {
         this.macroPath = null;
         this.state = WaypointNavigator.State.NAVIGATING_TO_DESTINATION;
         villager.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 0.5);
      }
   }

   public void tick(MillVillager villager) {
      switch (this.state) {
         case NAVIGATING_TO_WAYPOINT:
            this.tickNavigatingToWaypoint(villager);
            break;
         case NAVIGATING_TO_DESTINATION:
            this.tickNavigatingToDestination(villager);
      }
   }

   public boolean isArrived() {
      return this.state == WaypointNavigator.State.ARRIVED;
   }

   public boolean isDone() {
      return this.state == WaypointNavigator.State.ARRIVED || this.state == WaypointNavigator.State.ABANDONED;
   }

   public WaypointNavigator.State getState() {
      return this.state;
   }

   private void tickNavigatingToWaypoint(MillVillager villager) {
      if (this.macroPath != null && this.currentWaypointIndex < this.macroPath.size()) {
         BlockPos currentPos = villager.blockPosition();
         BlockPos targetWp = this.macroPath.get(this.currentWaypointIndex);
         if (currentPos.distSqr(targetWp) <= 9.0) {
            this.currentWaypointIndex++;
            this.stuckTicks = 0;
            this.lastPos = null;
            if (this.currentWaypointIndex >= this.macroPath.size()) {
               this.switchToDestination(villager);
            } else {
               BlockPos nextWp = this.macroPath.get(this.currentWaypointIndex);
               villager.getNavigation().moveTo(nextWp.getX() + 0.5, nextWp.getY(), nextWp.getZ() + 0.5, 0.5);
            }
         } else {
            if (this.isStuck(currentPos)) {
               this.stuckTicks++;
            } else {
               this.stuckTicks = 0;
            }

            if (this.stuckTicks >= 100 && this.stuckTicks % 100 == 0 && this.stuckTicks < 600) {
               if (this.currentWaypointIndex == 0 && !this.firstLegRebuildAttempted) {
                  this.firstLegRebuildAttempted = true;
                  if (this.tryRebuildAndRecomputeMacro(villager, "first-leg")) {
                     return;
                  }
               }

               LOGGER.debug("[Millenaire] Waypoint {} — stuck level 1, recalculating path", targetWp.toShortString());
               villager.getNavigation().stop();
               villager.getNavigation().moveTo(targetWp.getX() + 0.5, targetWp.getY(), targetWp.getZ() + 0.5, 0.5);
            } else if (this.stuckTicks > 600) {
               if (this.teleportCount >= 3) {
                  LOGGER.warn("[Millenaire] Navigation abandoned after {} teleportations — villager stuck", this.teleportCount);
                  this.state = WaypointNavigator.State.ABANDONED;
                  this.stuckTicks = 0;
                  villager.getNavEventLog()
                     .record(villager.level().getGameTime(), NavEvent.Layer.WAYPOINT, NavEvent.Type.GOAL_ABANDONED, "tp-cap wp=" + targetWp.toShortString());
                  NavigationCounters.incGoalAbandoned();
               } else {
                  villager.getNavEventLog()
                     .record(
                        villager.level().getGameTime(),
                        NavEvent.Layer.WAYPOINT,
                        NavEvent.Type.STUCK_DETECTED,
                        "wp=" + targetWp.toShortString() + " ticks=" + this.stuckTicks
                     );
                  this.teleportToSafe(villager, targetWp);
                  this.teleportCount++;
                  NavigationCounters.incTeleport(NavEvent.Layer.WAYPOINT);
                  villager.getNavEventLog()
                     .record(
                        villager.level().getGameTime(),
                        NavEvent.Layer.WAYPOINT,
                        NavEvent.Type.TELEPORT,
                        "wp=" + targetWp.toShortString() + " #" + this.teleportCount
                     );
                  LOGGER.debug("[Millenaire] Waypoint {} — stuck level 2, teleporting ({}/{})", new Object[]{targetWp.toShortString(), this.teleportCount, 3});
                  this.currentWaypointIndex++;
                  this.stuckTicks = 0;
                  this.lastPos = null;
                  if (this.currentWaypointIndex >= this.macroPath.size()) {
                     this.switchToDestination(villager);
                  } else {
                     BlockPos nextWp = this.macroPath.get(this.currentWaypointIndex);
                     villager.getNavigation().moveTo(nextWp.getX() + 0.5, nextWp.getY(), nextWp.getZ() + 0.5, 0.5);
                  }
               }
            } else {
               if (villager.getNavigation().isDone()) {
                  villager.getNavigation().moveTo(targetWp.getX() + 0.5, targetWp.getY(), targetWp.getZ() + 0.5, 0.5);
               }
            }
         }
      } else {
         this.switchToDestination(villager);
      }
   }

   private void tickNavigatingToDestination(MillVillager villager) {
      if (this.destination == null) {
         this.state = WaypointNavigator.State.ARRIVED;
      } else {
         BlockPos currentPos = villager.blockPosition();
         if (this.isStuck(currentPos)) {
            this.stuckTicks++;
         } else {
            this.stuckTicks = 0;
         }

         if (this.stuckTicks >= 100 && this.stuckTicks % 100 == 0 && this.stuckTicks < 600) {
            if (!this.lastLegRebuildAttempted) {
               this.lastLegRebuildAttempted = true;
               if (this.tryRebuildAndRecomputeMacro(villager, "last-leg")) {
                  return;
               }
            }

            LOGGER.debug("[Millenaire] Destination {} — stuck level 1, recalculating path", this.destination.toShortString());
            villager.getNavigation().stop();
            villager.getNavigation().moveTo(this.destination.getX() + 0.5, this.destination.getY(), this.destination.getZ() + 0.5, 0.5);
         } else if (this.stuckTicks > 600) {
            if (this.teleportCount >= 3) {
               LOGGER.warn("[Millenaire] Navigation abandoned after {} teleportations — villager stuck", this.teleportCount);
               this.state = WaypointNavigator.State.ABANDONED;
               this.stuckTicks = 0;
               villager.getNavEventLog()
                  .record(
                     villager.level().getGameTime(), NavEvent.Layer.WAYPOINT, NavEvent.Type.GOAL_ABANDONED, "tp-cap dest=" + this.destination.toShortString()
                  );
               NavigationCounters.incGoalAbandoned();
            } else {
               villager.getNavEventLog()
                  .record(
                     villager.level().getGameTime(),
                     NavEvent.Layer.WAYPOINT,
                     NavEvent.Type.STUCK_DETECTED,
                     "dest=" + this.destination.toShortString() + " ticks=" + this.stuckTicks
                  );
               this.teleportToSafe(villager, this.destination);
               this.teleportCount++;
               NavigationCounters.incTeleport(NavEvent.Layer.WAYPOINT);
               villager.getNavEventLog()
                  .record(
                     villager.level().getGameTime(),
                     NavEvent.Layer.WAYPOINT,
                     NavEvent.Type.TELEPORT,
                     "dest=" + this.destination.toShortString() + " #" + this.teleportCount
                  );
               LOGGER.debug(
                  "[Millenaire] Destination {} — stuck level 2, teleporting ({}/{})", new Object[]{this.destination.toShortString(), this.teleportCount, 3}
               );
               this.stuckTicks = 0;
               this.lastPos = null;
            }
         } else {
            if (villager.getNavigation().isDone()) {
               villager.getNavigation().moveTo(this.destination.getX() + 0.5, this.destination.getY(), this.destination.getZ() + 0.5, 0.5);
            }
         }
      }
   }

   private boolean tryRebuildAndRecomputeMacro(MillVillager villager, String reason) {
      if (this.village != null && this.graph != null && this.destination != null) {
         if (villager.level() instanceof ServerLevel sl) {
            boolean var9 = this.village.rebuildWaypointGraphIfStale(sl, 60L);
            List<BlockPos> newPath = this.graph.findPath(villager.blockPosition(), this.destination);
            if (newPath.isEmpty()) {
               return false;
            } else {
               BlockPos currentFirstWp = this.macroPath != null && this.currentWaypointIndex < this.macroPath.size()
                  ? this.macroPath.get(this.currentWaypointIndex)
                  : null;
               if (currentFirstWp != null && currentFirstWp.equals(newPath.get(0))) {
                  return false;
               } else {
                  BlockPos firstWp = newPath.get(0);
                  if (!isLegReachable(villager, villager.blockPosition(), firstWp)) {
                     LOGGER.debug(
                        "[Millenaire] {} entry-leg {} -> {} unreachable, abandoning recompute",
                        new Object[]{reason, villager.blockPosition().toShortString(), firstWp.toShortString()}
                     );
                     return false;
                  } else {
                     BlockPos lastWp = newPath.get(newPath.size() - 1);
                     if (!isLegReachableFromPos(villager, lastWp, this.destination)) {
                        LOGGER.debug(
                           "[Millenaire] {} exit-leg {} -> {} unreachable, abandoning recompute",
                           new Object[]{reason, lastWp.toShortString(), this.destination.toShortString()}
                        );
                        return false;
                     } else {
                        this.macroPath = newPath;
                        this.currentWaypointIndex = 0;
                        this.stuckTicks = 0;
                        this.lastPos = null;
                        this.firstLegRebuildAttempted = false;
                        this.lastLegRebuildAttempted = false;
                        this.state = WaypointNavigator.State.NAVIGATING_TO_WAYPOINT;
                        villager.getNavigation().stop();
                        villager.getNavigation().moveTo(firstWp.getX() + 0.5, firstWp.getY(), firstWp.getZ() + 0.5, 0.5);
                        villager.getNavEventLog()
                           .record(
                              villager.level().getGameTime(),
                              NavEvent.Layer.WAYPOINT,
                              NavEvent.Type.NAV_START,
                              "MACRO recompute (" + reason + ", rebuilt=" + var9 + ") wps=" + this.macroPath.size()
                           );
                        LOGGER.debug("[Millenaire] WaypointNavigator — {} recompute, {} new waypoints", reason, this.macroPath.size());
                        return true;
                     }
                  }
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isLegReachable(MillVillager villager, BlockPos from, BlockPos target) {
      Path probe = villager.getNavigation().createPath(target, 1);
      return probe != null && probe.canReach();
   }

   private static boolean isLegReachableFromPos(MillVillager villager, BlockPos from, BlockPos target) {
      return !villager.level().isLoaded(target) ? false : from.distSqr(target) <= 4096.0;
   }

   private void switchToDestination(MillVillager villager) {
      this.state = WaypointNavigator.State.NAVIGATING_TO_DESTINATION;
      this.stuckTicks = 0;
      this.lastPos = null;
      if (this.destination != null) {
         villager.getNavigation().moveTo(this.destination.getX() + 0.5, this.destination.getY(), this.destination.getZ() + 0.5, 0.5);
      }
   }

   private boolean isStuck(BlockPos currentPos) {
      if (this.lastPos == null) {
         this.lastPos = currentPos;
         return false;
      } else {
         double distSq = currentPos.distSqr(this.lastPos);
         if (distSq > 0.25) {
            this.lastPos = currentPos;
            return false;
         } else {
            return true;
         }
      }
   }

   private void teleportToSafe(MillVillager villager, BlockPos target) {
      Level level = villager.level();
      int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
      BlockPos surfacePos = new BlockPos(target.getX(), surfaceY, target.getZ());

      for (int nudge = 0; nudge < 5; nudge++) {
         BlockPos candidate = surfacePos.above(nudge);
         if (!level.getBlockState(candidate).isSuffocating(level, candidate) && !level.getBlockState(candidate.above()).isSuffocating(level, candidate.above())
            )
          {
            villager.teleportTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
            villager.getNavigation().stop();
            return;
         }
      }

      villager.teleportTo(surfacePos.getX() + 0.5, surfacePos.getY(), surfacePos.getZ() + 0.5);
      villager.getNavigation().stop();
   }

   public enum State {
      IDLE,
      NAVIGATING_TO_WAYPOINT,
      NAVIGATING_TO_DESTINATION,
      ARRIVED,
      ABANDONED;
   }
}
