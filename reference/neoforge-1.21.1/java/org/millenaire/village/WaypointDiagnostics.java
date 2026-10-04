package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.village.path.PathGridAStar;
import org.slf4j.Logger;

public final class WaypointDiagnostics {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int DEFAULT_PROBE_NODES = 200;
   public static final int CENTRAL_ANCHOR_COUNT = 2;

   private WaypointDiagnostics() {
   }

   public static WaypointDiagnostics.Report analyze(ServerLevel level, Village village) {
      return analyze(level, village, 200);
   }

   public static WaypointDiagnostics.Report analyze(ServerLevel level, Village village, int nodeBudget) {
      List<BlockPos> anchors = pickAnchors(village);
      List<WaypointDiagnostics.Finding> findings = new ArrayList<>();
      int probed = 0;

      for (Waypoint wp : village.getWaypointGraph().getWaypoints()) {
         if (wp.buildingId() != null && !isAnchor(wp.pos(), anchors)) {
            probed++;
            BlockPos reached = tryReachFromAnyAnchor(level, anchors, wp.pos(), nodeBudget);
            if (reached == null) {
               findings.add(
                  new WaypointDiagnostics.Finding(wp.buildingId(), wp.pos(), resolvePlanSetId(village, wp.buildingId()), nearestAnchor(anchors, wp.pos()))
               );
            }
         }
      }

      LOGGER.info(
         "[Millenaire] WaypointDiagnostics — village {} : probed {} waypoints from {} anchors, {} unreachable (budget={})",
         new Object[]{village.getVillageTypeId(), probed, anchors.size(), findings.size(), nodeBudget}
      );
      return new WaypointDiagnostics.Report(village, anchors, probed, findings);
   }

   private static List<BlockPos> pickAnchors(Village village) {
      BlockPos center = village.getCenter();
      Set<BlockPos> anchors = new LinkedHashSet<>();
      BuildingInstance townhall = village.getTownhall();
      if (townhall != null) {
         BlockPos anchor = resolveAnchorPos(townhall);
         if (anchor != null) {
            anchors.add(anchor);
         }
      }

      List<BuildingInstance> sorted = new ArrayList<>(village.getBuildings());
      sorted.sort(Comparator.comparingDouble(bx -> bx.getOrigin().distSqr(center)));

      for (BuildingInstance b : sorted) {
         if (anchors.size() >= 3) {
            break;
         }

         BlockPos anchor = resolveAnchorPos(b);
         if (anchor != null) {
            anchors.add(anchor);
         }
      }

      return new ArrayList<>(anchors);
   }

   @Nullable
   private static BlockPos resolveAnchorPos(BuildingInstance building) {
      return building.resolvePathAnchor();
   }

   private static boolean isAnchor(BlockPos wp, List<BlockPos> anchors) {
      for (BlockPos a : anchors) {
         if (a.equals(wp)) {
            return true;
         }
      }

      return false;
   }

   private static BlockPos nearestAnchor(List<BlockPos> anchors, BlockPos wp) {
      BlockPos best = anchors.isEmpty() ? BlockPos.ZERO : anchors.get(0);
      double bestSq = best.distSqr(wp);

      for (BlockPos a : anchors) {
         double d = a.distSqr(wp);
         if (d < bestSq) {
            bestSq = d;
            best = a;
         }
      }

      return best;
   }

   @Nullable
   private static BlockPos tryReachFromAnyAnchor(ServerLevel level, List<BlockPos> anchors, BlockPos target, int nodeBudget) {
      for (BlockPos anchor : anchors) {
         List<BlockPos> path = PathGridAStar.findPath(level, anchor, target, PathGridAStar.Weights.defaults(), nodeBudget);
         if (path != null) {
            return anchor;
         }
      }

      return null;
   }

   @Nullable
   private static ResourceLocation resolvePlanSetId(Village village, BuildingId buildingId) {
      BuildingInstance b = village.findBuildingById(buildingId);
      return b != null ? b.getPlanSetId() : null;
   }

   public record Finding(BuildingId buildingId, BlockPos waypointPos, @Nullable ResourceLocation planSetId, BlockPos nearestAnchor) {
      public double distanceToNearestAnchor() {
         return Math.sqrt(this.waypointPos.distSqr(this.nearestAnchor));
      }
   }

   public record Report(Village village, List<BlockPos> anchors, int probedCount, List<WaypointDiagnostics.Finding> findings) {
      public boolean clean() {
         return this.findings.isEmpty();
      }
   }
}
