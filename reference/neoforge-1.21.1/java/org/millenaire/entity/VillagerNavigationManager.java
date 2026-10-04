package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.millenaire.goal.NavigationHelperUtils;
import org.millenaire.goal.WaypointNavigator;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageWaypointGraph;
import org.slf4j.Logger;

public class VillagerNavigationManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int LOCAL_STUCK_INCREMENT = 4;
   private static final int LOCAL_STUCK_REPATH = 30;
   private static final int LOCAL_STUCK_JUMP = 100;
   private static final double PROGRESS_THRESHOLD = 2.0E-4;
   private static final int LONG_STUCK_TELEPORT = 200;
   private static final int MAX_TELEPORTS = 3;
   private static final double DEST_CHANGE_THRESHOLD_SQ = 4.0;
   private static final int REPATH_INTERVAL = 10;
   private static final double WPN_ARRIVAL_DISTANCE_SQ = 25.0;
   @Nullable
   private BlockPos destination;
   private double speed = 0.5;
   private int localStuck;
   private double prevDistToNextNode = -1.0;
   private int longDistanceStuck;
   private double prevDistToDest = -1.0;
   @Nullable
   private WaypointNavigator waypointNavigator;
   private int teleportCount;
   private boolean abandoned;
   private int repathAttempts;
   private int consecutivePathFailures;
   static final int MAX_CONSECUTIVE_PATH_FAILURES = 3;
   private int pathFailLogCooldown;
   private int pathfindCooldown;
   static final int PATHFIND_COOLDOWN_TICKS = 15;
   private static final long WAYPOINT_REBUILD_THROTTLE = 60L;
   private static final int NO_PATH_STUCK_ACCELERATION = 5;

   public void navigateTo(MillVillager villager, BlockPos dest, double walkSpeed) {
      boolean forceReset = this.abandoned;
      if (!forceReset && this.destination != null && dest.distSqr(this.destination) <= 4.0) {
         this.destination = dest;
         this.speed = walkSpeed;
         if (this.waypointNavigator == null && villager.getNavigation().isDone()) {
            villager.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, walkSpeed);
         }
      } else {
         this.destination = dest;
         this.speed = walkSpeed;
         this.localStuck = 0;
         this.longDistanceStuck = 0;
         this.prevDistToNextNode = -1.0;
         this.prevDistToDest = -1.0;
         this.teleportCount = 0;
         this.abandoned = false;
         this.waypointNavigator = null;
         this.pathfindCooldown = 0;
         this.repathAttempts = 0;
         this.consecutivePathFailures = 0;
         Village village = resolveVillage(villager);
         VillageWaypointGraph graph = village != null ? village.getWaypointGraph() : null;
         double dist = Math.sqrt(villager.blockPosition().distSqr(dest));
         boolean macro = dist > 48.0 && graph != null && graph.isAvailable();
         if (macro) {
            this.waypointNavigator = new WaypointNavigator();
            this.waypointNavigator.navigateTo(villager, dest, village, graph);
         } else {
            villager.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, walkSpeed);
         }

         villager.getNavEventLog()
            .record(
               villager.level().getGameTime(),
               NavEvent.Layer.VNM,
               NavEvent.Type.NAV_START,
               (macro ? "MACRO" : "DIRECT") + " dest=" + dest.toShortString() + " dist=" + String.format("%.1f", dist)
            );
      }
   }

   public void tick(MillVillager villager, @Nullable Village village) {
      if (this.destination != null && !this.abandoned) {
         if (this.waypointNavigator != null) {
            this.tickWaypointNavigator(villager);
         } else if (villager.getNavigation().isDone()) {
            if (this.pathfindCooldown > 0) {
               this.pathfindCooldown--;
            } else {
               boolean ok = villager.getNavigation().moveTo(this.destination.getX() + 0.5, this.destination.getY(), this.destination.getZ() + 0.5, this.speed);
               if (ok) {
                  this.pathfindCooldown = 0;
                  this.consecutivePathFailures = 0;
               } else {
                  this.pathfindCooldown = 15;
                  this.consecutivePathFailures++;
                  if (this.pathFailLogCooldown <= 0) {
                     LOGGER.debug(
                        "[Millenaire] NavManager — pathfind FAILED for {} from {} to {} (dist={}, consec={})",
                        new Object[]{
                           villager.getVillagerTypeId(),
                           villager.blockPosition().toShortString(),
                           this.destination.toShortString(),
                           String.format("%.1f", Math.sqrt(villager.blockPosition().distSqr(this.destination))),
                           this.consecutivePathFailures
                        }
                     );
                     this.pathFailLogCooldown = 100;
                  }

                  if (this.consecutivePathFailures >= 3) {
                     this.tryPromoteToMacro(villager, "no-path×" + this.consecutivePathFailures);
                     this.consecutivePathFailures = 0;
                  }
               }
            }

            if (this.pathFailLogCooldown > 0) {
               this.pathFailLogCooldown--;
            }
         }

         this.tickLocalStuck(villager);
         this.tickLongDistanceStuck(villager);
      }
   }

   private void tickLocalStuck(MillVillager villager) {
      Path path = villager.getNavigation().getPath();
      if (path != null && !path.isDone() && path.getNextNodeIndex() < path.getNodeCount()) {
         Node nextNode = path.getNode(path.getNextNodeIndex());
         double distToNext = villager.position().distanceToSqr(nextNode.x + 0.5, nextNode.y + 0.5, nextNode.z + 0.5);
         if (this.prevDistToNextNode >= 0.0) {
            double progress = this.prevDistToNextNode - distToNext;
            if (progress < 2.0E-4) {
               this.localStuck += 4;
            } else {
               this.localStuck = Math.max(0, this.localStuck - 1);
               if (this.localStuck == 0) {
                  this.repathAttempts = 0;
               }
            }
         }

         this.prevDistToNextNode = distToNext;
         if (this.localStuck > 30 && this.localStuck % 10 == 0 && this.localStuck <= 100) {
            villager.getNavigation().stop();
            villager.getNavEventLog().record(villager.level().getGameTime(), NavEvent.Layer.VNM, NavEvent.Type.REPATH, "local stuck=" + this.localStuck);
            this.repathAttempts++;
            if (this.repathAttempts >= 2) {
               this.tryPromoteToMacro(villager, "after " + this.repathAttempts + " repath");
               this.repathAttempts = 0;
            }
         }

         if (this.localStuck > 100) {
            villager.getNavEventLog()
               .record(villager.level().getGameTime(), NavEvent.Layer.VNM, NavEvent.Type.STUCK_DETECTED, "local stuck=" + this.localStuck);
            this.doShortJump(villager, path);
            this.localStuck = 0;
            this.prevDistToNextNode = -1.0;
            this.repathAttempts = 0;
         }
      } else {
         this.localStuck = 0;
         this.prevDistToNextNode = -1.0;
      }
   }

   private void tryPromoteToMacro(MillVillager villager, String reason) {
      if (this.waypointNavigator == null) {
         if (this.destination != null) {
            Village village = resolveVillage(villager);
            if (village != null) {
               VillageWaypointGraph graph = village.getWaypointGraph();
               if (graph != null) {
                  if (villager.level() instanceof ServerLevel sl) {
                     boolean rebuilt = village.rebuildWaypointGraphIfStale(sl, 60L);
                     if (rebuilt) {
                        villager.getNavEventLog()
                           .record(
                              villager.level().getGameTime(),
                              NavEvent.Layer.VNM,
                              NavEvent.Type.NAV_START,
                              "GRAPH rebuilt (on-stuck) edges=" + graph.getEdges().size()
                           );
                     }
                  }

                  if (graph.isAvailable()) {
                     villager.getNavEventLog()
                        .record(
                           villager.level().getGameTime(),
                           NavEvent.Layer.VNM,
                           NavEvent.Type.NAV_START,
                           "MACRO promote (" + reason + ") dest=" + this.destination.toShortString()
                        );
                     this.waypointNavigator = new WaypointNavigator();
                     this.waypointNavigator.navigateTo(villager, this.destination, village, graph);
                     this.localStuck = 0;
                     this.prevDistToNextNode = -1.0;
                  }
               }
            }
         }
      }
   }

   private void doShortJump(MillVillager villager, Path path) {
      BlockPos current = villager.blockPosition();

      for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
         BlockPos next = path.getNode(i).asBlockPos();
         if (!next.equals(current) && !(current.distSqr(next) < 1.0)) {
            Level level = villager.level();
            if (!level.getBlockState(next).isSuffocating(level, next) && !level.getBlockState(next.above()).isSuffocating(level, next.above())) {
               LOGGER.debug("[Millenaire] NavManager — short jump {} → {}", current.toShortString(), next.toShortString());
               villager.teleportTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
               villager.getNavigation().stop();
               villager.getNavEventLog()
                  .record(villager.level().getGameTime(), NavEvent.Layer.VNM, NavEvent.Type.SHORT_JUMP, current.toShortString() + "→" + next.toShortString());
               NavigationCounters.incShortJump();
               return;
            }
         }
      }

      LOGGER.debug("[Millenaire] NavManager — short jump: no safe node in path");
   }

   private void tickLongDistanceStuck(MillVillager villager) {
      if (this.destination != null) {
         double dx = villager.getX() - (this.destination.getX() + 0.5);
         double dz = villager.getZ() - (this.destination.getZ() + 0.5);
         double distToDest = dx * dx + dz * dz;
         boolean noPath = this.waypointNavigator == null && villager.getNavigation().getPath() == null && villager.getNavigation().isDone();
         int increment = noPath ? 5 : 1;
         if (this.prevDistToDest >= 0.0) {
            double progress = this.prevDistToDest - distToDest;
            if (progress < 2.0E-4) {
               this.longDistanceStuck += increment;
            } else {
               this.longDistanceStuck = Math.max(0, this.longDistanceStuck - 1);
            }
         }

         this.prevDistToDest = distToDest;
         if (this.longDistanceStuck > 200) {
            this.doLongDistanceTeleport(villager);
            this.longDistanceStuck = 0;
            this.prevDistToDest = -1.0;
         }
      }
   }

   private void doLongDistanceTeleport(MillVillager villager) {
      if (this.destination != null) {
         if (this.teleportCount >= 3) {
            LOGGER.warn(
               "[Millenaire] NavManager — {} abandon after {} TPs toward {}",
               new Object[]{villager.getVillagerTypeId(), this.teleportCount, this.destination.toShortString()}
            );
            this.abandoned = true;
            villager.getNavEventLog()
               .record(villager.level().getGameTime(), NavEvent.Layer.VNM, NavEvent.Type.GOAL_ABANDONED, "tp-cap dest=" + this.destination.toShortString());
            NavigationCounters.incGoalAbandoned();
         } else if (!villager.level().isLoaded(this.destination)) {
            LOGGER.warn("[Millenaire] NavManager — {} destination not loaded, abandoning", villager.getVillagerTypeId());
            this.abandoned = true;
            villager.getNavEventLog()
               .record(villager.level().getGameTime(), NavEvent.Layer.VNM, NavEvent.Type.GOAL_ABANDONED, "dest-unloaded=" + this.destination.toShortString());
            NavigationCounters.incGoalAbandoned();
         } else {
            BlockPos beforeTP = villager.blockPosition();
            this.teleportCount++;
            villager.getNavEventLog()
               .record(
                  villager.level().getGameTime(),
                  NavEvent.Layer.VNM,
                  NavEvent.Type.STUCK_DETECTED,
                  "long-dist #" + this.teleportCount + " ticks=" + this.longDistanceStuck
               );
            NavigationHelperUtils.teleportToSafe(villager, this.destination);
            BlockPos afterTP = villager.blockPosition();
            NavigationCounters.incTeleport(NavEvent.Layer.VNM);
            villager.getNavEventLog()
               .record(
                  villager.level().getGameTime(),
                  NavEvent.Layer.VNM,
                  NavEvent.Type.TELEPORT,
                  "#" + this.teleportCount + " " + beforeTP.toShortString() + "→" + afterTP.toShortString() + " dest=" + this.destination.toShortString()
               );
            if (beforeTP.distSqr(afterTP) < 4.0) {
               LOGGER.warn(
                  "[Millenaire] NavManager — {} TP #{} ineffective (stayed at {}), dest={}",
                  new Object[]{villager.getVillagerTypeId(), this.teleportCount, afterTP.toShortString(), this.destination.toShortString()}
               );
               this.teleportCount++;
            } else {
               LOGGER.info(
                  "[Millenaire] NavManager — {} TP #{} from {} to {} (dest={})",
                  new Object[]{
                     villager.getVillagerTypeId(), this.teleportCount, beforeTP.toShortString(), afterTP.toShortString(), this.destination.toShortString()
                  }
               );
            }

            this.localStuck = 0;
            this.prevDistToNextNode = -1.0;
         }
      }
   }

   private void tickWaypointNavigator(MillVillager villager) {
      this.waypointNavigator.tick(villager);
      if (this.destination != null && villager.blockPosition().distSqr(this.destination) <= 25.0) {
         this.waypointNavigator = null;
      } else {
         if (this.waypointNavigator.isDone()) {
            this.waypointNavigator = null;
            this.longDistanceStuck = 201;
         }
      }
   }

   public boolean isArrived(MillVillager villager, double arriveDistance) {
      return this.destination == null ? true : villager.blockPosition().distSqr(this.destination) <= arriveDistance * arriveDistance;
   }

   public boolean isArrivedHorizontal(MillVillager villager, double arriveDistance) {
      return this.destination == null
         ? true
         : NavigationHelperUtils.horizontalDistSq(villager.blockPosition(), this.destination) <= arriveDistance * arriveDistance;
   }

   public boolean isAbandoned() {
      return this.abandoned;
   }

   public void stop(MillVillager villager) {
      this.destination = null;
      this.waypointNavigator = null;
      villager.getNavigation().stop();
      this.localStuck = 0;
      this.longDistanceStuck = 0;
      this.prevDistToNextNode = -1.0;
      this.prevDistToDest = -1.0;
      this.teleportCount = 0;
      this.abandoned = false;
      this.pathfindCooldown = 0;
   }

   @Nullable
   public BlockPos getDestination() {
      return this.destination;
   }

   public int getLocalStuck() {
      return this.localStuck;
   }

   public int getLongDistanceStuck() {
      return this.longDistanceStuck;
   }

   public int getTeleportCount() {
      return this.teleportCount;
   }

   @Nullable
   public WaypointNavigator getWaypointNavigator() {
      return this.waypointNavigator;
   }

   int getPathfindCooldown() {
      return this.pathfindCooldown;
   }

   @Nullable
   private static Village resolveVillage(MillVillager villager) {
      VillageId vid = villager.getVillageId();
      if (vid == null) {
         return null;
      } else {
         return villager.level() instanceof ServerLevel serverLevel ? Village.resolve(serverLevel, vid) : null;
      }
   }
}
