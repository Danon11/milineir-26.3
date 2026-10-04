package org.millenaire.village.path;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import org.millenaire.building.BuildingId;
import org.slf4j.Logger;

public final class PathRouter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final double NODE_SWITCH_FACTOR = 1.3;
   private static final double INTERMEDIATE_FACTOR = 1.5;
   private static final double INTERMEDIATE_THRESHOLD = 20.0;
   private static final double LATERAL_MAX_DIST = 35.0;
   private static final double LATERAL_DETOUR_THRESHOLD = 2.5;
   private static final int LATERAL_MAX_PER_BUILDING = 1;

   private PathRouter() {
   }

   public static List<PathRoute> computeRoutes(
      List<PathRouter.BuildingInfo> buildings, List<PathRouter.NodeInfo> nodes, BlockPos thPos, List<String> pathMaterials
   ) {
      return computeRoutes(buildings, nodes, thPos, pathMaterials, Set.of());
   }

   public static List<PathRoute> computeRoutes(
      List<PathRouter.BuildingInfo> buildings, List<PathRouter.NodeInfo> nodes, BlockPos thPos, List<String> pathMaterials, Set<BuildingId> unreachableFromTH
   ) {
      List<PathRoute> routes = new ArrayList<>();
      List<PathRouter.BuildingInfo> routable = new ArrayList<>(buildings.size());

      for (PathRouter.BuildingInfo info : buildings) {
         if (!info.isSubBuilding && !info.noPaths() && info.pathStart != null) {
            routable.add(info);
         }
      }

      for (PathRouter.BuildingInfo b : routable) {
         BlockPos source = b.pathStart;
         BlockPos dest = thPos;
         double destDist = distance(source, dest);
         boolean isNode = b.isPathNode();
         if (!isNode) {
            for (PathRouter.NodeInfo node : nodes) {
               if (dest == thPos) {
                  double nodeDist = distance(source, node.pos);
                  if (nodeDist * 1.3 < destDist) {
                     dest = node.pos;
                     destDist = nodeDist;
                  }
               } else {
                  double nodeDist = distance(source, node.pos);
                  if (nodeDist < destDist) {
                     dest = node.pos;
                     destDist = nodeDist;
                  }
               }
            }

            if (destDist > 20.0) {
               double thDistSqFromB = distanceSq(thPos, source);
               BlockPos bestIntermediate = null;
               PathRouter.BuildingInfo bestIntermediateInfo = null;
               double bestIntermediateDist = Double.MAX_VALUE;

               for (PathRouter.BuildingInfo other : routable) {
                  if (other != b && !other.isPathNode() && (other.id == null || !unreachableFromTH.contains(other.id))) {
                     double thDistSqFromOther = distanceSq(thPos, other.pathStart);
                     if (!(thDistSqFromOther >= thDistSqFromB)) {
                        double otherDist = distance(source, other.pathStart);
                        if (otherDist * 1.5 < destDist && otherDist < bestIntermediateDist) {
                           bestIntermediate = other.pathStart;
                           bestIntermediateInfo = other;
                           bestIntermediateDist = otherDist;
                        }
                     }
                  }
               }

               if (bestIntermediate != null) {
                  dest = bestIntermediate;
               }

               if (bestIntermediateInfo != null) {
                  routes.add(buildRoute(b, source, bestIntermediateInfo.pathStart, bestIntermediateInfo.id, b.pathWidth, b.pathLevel, pathMaterials));
                  continue;
               }
            }
         }

         routes.add(buildRoute(b, source, dest, null, b.pathWidth, b.pathLevel, pathMaterials));
      }

      List<PathRoute> uplifted = propagateTierUplift(routes, routable, pathMaterials);
      List<PathRoute> laterals = computeLateralRoutes(uplifted, routable, thPos, pathMaterials);
      if (!laterals.isEmpty()) {
         List<PathRoute> all = new ArrayList<>(uplifted);
         all.addAll(laterals);
         return all;
      } else {
         return uplifted;
      }
   }

   private static List<PathRoute> propagateTierUplift(List<PathRoute> routes, List<PathRouter.BuildingInfo> routable, List<String> pathMaterials) {
      if (!routes.isEmpty() && !pathMaterials.isEmpty()) {
         Map<BuildingId, Integer> rawTier = new HashMap<>();

         for (PathRouter.BuildingInfo info : routable) {
            if (info.id != null) {
               rawTier.put(info.id, info.pathLevel);
            }
         }

         Map<BuildingId, List<BuildingId>> upstream = new HashMap<>();

         for (PathRoute r : routes) {
            if (r.destinationId() != null && r.sourceId() != null) {
               upstream.computeIfAbsent(r.destinationId(), k -> new ArrayList<>()).add(r.sourceId());
            }
         }

         Map<BuildingId, Integer> effective = new HashMap<>();
         Set<BuildingId> visiting = new HashSet<>();

         for (BuildingId id : rawTier.keySet()) {
            effectiveTier(id, rawTier, upstream, effective, visiting);
         }

         List<PathRoute> rewritten = new ArrayList<>(routes.size());

         for (PathRoute r : routes) {
            int srcTier = r.sourceId() != null ? effective.getOrDefault(r.sourceId(), r.pathLevel()) : r.pathLevel();
            int dstTier = r.destinationId() != null ? effective.getOrDefault(r.destinationId(), r.pathLevel()) : r.pathLevel();
            int t = Math.max(srcTier, dstTier);
            int clamped = Math.min(t, pathMaterials.size() - 1);
            rewritten.add(r.withTierAndMaterial(clamped, pathMaterials.get(clamped)));
         }

         return rewritten;
      } else {
         return routes;
      }
   }

   private static int effectiveTier(
      BuildingId id, Map<BuildingId, Integer> rawTier, Map<BuildingId, List<BuildingId>> upstream, Map<BuildingId, Integer> memo, Set<BuildingId> visiting
   ) {
      if (id == null) {
         return 0;
      }

      Integer cached = memo.get(id);
      if (cached != null) {
         return cached;
      }

      if (visiting.contains(id)) {
         LOGGER.warn("path graph cycle at {} — breaking with rawTier", id);
         return rawTier.getOrDefault(id, 0);
      }

      visiting.add(id);
      int max = rawTier.getOrDefault(id, 0);

      for (BuildingId up : upstream.getOrDefault(id, List.of())) {
         max = Math.max(max, effectiveTier(up, rawTier, upstream, memo, visiting));
      }

      visiting.remove(id);
      memo.put(id, max);
      return max;
   }

   private static List<PathRoute> computeLateralRoutes(
      List<PathRoute> mainRoutes, List<PathRouter.BuildingInfo> routable, BlockPos thPos, List<String> pathMaterials
   ) {
      if (routable.size() < 2) {
         return List.of();
      }

      Map<BuildingId, PathRouter.BuildingInfo> infoById = new HashMap<>();

      for (PathRouter.BuildingInfo info : routable) {
         if (info.id() != null) {
            infoById.put(info.id(), info);
         }
      }

      Map<BuildingId, BuildingId> parentOf = new HashMap<>();
      Map<BuildingId, Double> distToParent = new HashMap<>();
      Map<BlockPos, BuildingId> posToBuildingId = new HashMap<>();

      for (PathRouter.BuildingInfo info : routable) {
         if (info.id() != null) {
            posToBuildingId.put(info.pathStart(), info.id());
         }
      }

      for (PathRoute r : mainRoutes) {
         if (!r.lateral() && r.sourceId() != null) {
            BuildingId destBuildingId = r.destinationId();
            if (destBuildingId == null) {
               destBuildingId = posToBuildingId.get(r.to());
            }

            parentOf.put(r.sourceId(), destBuildingId);
            distToParent.put(r.sourceId(), distance(r.from(), r.to()));
         }
      }

      Map<BuildingId, PathRouter.TreeNode> tree = new HashMap<>();
      Set<BuildingId> inProgress = new HashSet<>();

      for (BuildingId id : infoById.keySet()) {
         buildTreeNode(id, parentOf, distToParent, tree, inProgress);
      }

      List<PathRouter.LateralCandidate> candidates = new ArrayList<>();
      List<BuildingId> ids = new ArrayList<>(infoById.keySet());

      for (int i = 0; i < ids.size(); i++) {
         for (int j = i + 1; j < ids.size(); j++) {
            BuildingId aId = ids.get(i);
            BuildingId bId = ids.get(j);
            PathRouter.TreeNode nodeA = tree.get(aId);
            PathRouter.TreeNode nodeB = tree.get(bId);
            if (nodeA != null && nodeB != null && !nodeA.ancestors.contains(bId) && !nodeB.ancestors.contains(aId)) {
               BlockPos aPos = infoById.get(aId).pathStart();
               BlockPos bPos = infoById.get(bId).pathStart();
               double directDist = distance(aPos, bPos);
               if (!(directDist > 35.0) && !(directDist < 0.001)) {
                  double treeDist = computeTreeDistViaLCA(nodeA, nodeB, tree);
                  if (!(treeDist < 0.0)) {
                     double detour = treeDist / directDist;
                     if (!(detour <= 2.5)) {
                        candidates.add(new PathRouter.LateralCandidate(aId, bId, aPos, bPos, detour, directDist));
                     }
                  }
               }
            }
         }
      }

      if (candidates.isEmpty()) {
         return List.of();
      }

      candidates.sort(
         Comparator.<PathRouter.LateralCandidate>comparingDouble(cx -> cx.detourFactor)
            .reversed()
            .thenComparingDouble(cx -> cx.directDist)
            .thenComparing(cx -> cx.aId.toString())
            .thenComparing(cx -> cx.bId.toString())
      );
      Map<BuildingId, Integer> lateralCount = new HashMap<>();
      List<PathRoute> laterals = new ArrayList<>();
      String lateralMaterial = pathMaterials.isEmpty() ? "pathgravel" : pathMaterials.getFirst();

      for (PathRouter.LateralCandidate c : candidates) {
         int countA = lateralCount.getOrDefault(c.aId, 0);
         int countB = lateralCount.getOrDefault(c.bId, 0);
         if (countA < 1 && countB < 1) {
            laterals.add(new PathRoute(c.aPos, c.bPos, lateralMaterial, 1, 0, c.aId, c.bId, true));
            lateralCount.merge(c.aId, 1, Integer::sum);
            lateralCount.merge(c.bId, 1, Integer::sum);
         }
      }

      if (!laterals.isEmpty()) {
         LOGGER.debug("Pass 3: emitted {} lateral route(s)", laterals.size());
      }

      return laterals;
   }

   private static PathRouter.TreeNode buildTreeNode(
      BuildingId id,
      Map<BuildingId, BuildingId> parentOf,
      Map<BuildingId, Double> distToParentMap,
      Map<BuildingId, PathRouter.TreeNode> tree,
      Set<BuildingId> inProgress
   ) {
      PathRouter.TreeNode existing = tree.get(id);
      if (existing != null) {
         return existing;
      }

      if (!inProgress.add(id)) {
         PathRouter.TreeNode node = new PathRouter.TreeNode(id, null, 0.0, Set.of(), 0.0);
         tree.put(id, node);
         return node;
      }

      try {
         BuildingId parentId = parentOf.get(id);
         double dtp = distToParentMap.getOrDefault(id, 0.0);
         if (parentId == null) {
            PathRouter.TreeNode node = new PathRouter.TreeNode(id, null, dtp, Set.of(), dtp);
            tree.put(id, node);
            return node;
         }

         Set<BuildingId> ancestors;
         double distToRoot;
         if (!tree.containsKey(parentId) && !parentOf.containsKey(parentId)) {
            ancestors = new HashSet<>();
            ancestors.add(parentId);
            distToRoot = dtp;
         } else {
            PathRouter.TreeNode parentNode = buildTreeNode(parentId, parentOf, distToParentMap, tree, inProgress);
            ancestors = new HashSet<>(parentNode.ancestors);
            ancestors.add(parentId);
            distToRoot = parentNode.distToRoot + dtp;
         }

         PathRouter.TreeNode node = new PathRouter.TreeNode(id, parentId, dtp, Set.copyOf(ancestors), distToRoot);
         tree.put(id, node);
         return node;
      } finally {
         inProgress.remove(id);
      }
   }

   private static double computeTreeDistViaLCA(PathRouter.TreeNode a, PathRouter.TreeNode b, Map<BuildingId, PathRouter.TreeNode> tree) {
      Map<BuildingId, Double> distFromA = new HashMap<>();
      distFromA.put(a.id, 0.0);
      double cumA = 0.0;
      PathRouter.TreeNode cur = a;

      while (cur.parentId != null) {
         cumA += cur.distToParent;
         distFromA.put(cur.parentId, cumA);
         PathRouter.TreeNode parent = tree.get(cur.parentId);
         if (parent == null) {
            break;
         }

         cur = parent;
      }

      double cumB = 0.0;
      cur = b;
      if (distFromA.containsKey(b.id)) {
         return distFromA.get(b.id);
      }

      while (cur.parentId != null) {
         cumB += cur.distToParent;
         if (distFromA.containsKey(cur.parentId)) {
            return distFromA.get(cur.parentId) + cumB;
         }

         PathRouter.TreeNode parent = tree.get(cur.parentId);
         if (parent == null) {
            break;
         }

         cur = parent;
      }

      return a.distToRoot + b.distToRoot;
   }

   private static PathRoute buildRoute(
      PathRouter.BuildingInfo source, BlockPos from, BlockPos to, @Nullable BuildingId destinationId, int width, int pathLevel, List<String> pathMaterials
   ) {
      String material;
      if (pathMaterials.isEmpty()) {
         material = "pathgravel";
      } else {
         int index = Math.min(pathLevel, pathMaterials.size() - 1);
         material = pathMaterials.get(index);
      }

      return new PathRoute(from, to, material, width, pathLevel, source.id, destinationId, false);
   }

   private static double distance(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dz = a.getZ() - b.getZ();
      return Math.sqrt(dx * dx + dz * dz);
   }

   private static double distanceSq(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dz = a.getZ() - b.getZ();
      return dx * dx + dz * dz;
   }

   public record BuildingInfo(@Nullable BuildingId id, BlockPos pathStart, boolean isSubBuilding, List<String> tags, int pathLevel, int pathWidth) {
      public BuildingInfo(BlockPos pathStart, boolean isSubBuilding, List<String> tags, int pathLevel, int pathWidth) {
         this(null, pathStart, isSubBuilding, tags, pathLevel, pathWidth);
      }

      public boolean noPaths() {
         return this.tags != null && this.tags.contains("nopaths");
      }

      public boolean isPathNode() {
         return this.tags != null && this.tags.contains("pathnode");
      }
   }

   private record LateralCandidate(BuildingId aId, BuildingId bId, BlockPos aPos, BlockPos bPos, double detourFactor, double directDist) {
   }

   public record NodeInfo(BlockPos pos) {
   }

   private record TreeNode(BuildingId id, @Nullable BuildingId parentId, double distToParent, Set<BuildingId> ancestors, double distToRoot) {
   }
}
