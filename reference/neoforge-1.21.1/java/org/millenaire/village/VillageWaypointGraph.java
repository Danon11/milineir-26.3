package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.slf4j.Logger;

public class VillageWaypointGraph {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final double MAX_EDGE_DISTANCE = 64.0;
   public static final double SHORT_EDGE_THRESHOLD = 30.0;
   public static final double MACRO_THRESHOLD = 48.0;
   private static final long WARN_REBUILD_MS = 200L;
   private volatile VillageWaypointGraph.GraphSnapshot snapshot = VillageWaypointGraph.GraphSnapshot.EMPTY;

   public void rebuild(List<BuildingInstance> buildings, BlockPos villageCenter, ServerLevel level, Village village) {
      Objects.requireNonNull(level, "ServerLevel is required for pathfind validation");
      Objects.requireNonNull(village, "Village is required so the tester binds villageId");
      this.rebuildInternal(buildings, villageCenter, level, village);
   }

   void rebuild(List<BuildingInstance> buildings, BlockPos villageCenter) {
      this.rebuildInternal(buildings, villageCenter, null, null);
   }

   public void rebuildForTesting(List<BuildingInstance> buildings, BlockPos villageCenter, ServerLevel level) {
      Objects.requireNonNull(level, "ServerLevel is required for pathfind validation");
      this.rebuildInternal(buildings, villageCenter, level, null);
   }

   private void rebuildInternal(List<BuildingInstance> buildings, BlockPos villageCenter, @Nullable ServerLevel level, @Nullable Village village) {
      long rebuildStartNanos = System.nanoTime();
      List<Waypoint> newWaypoints = new ArrayList<>();
      Map<Waypoint, List<WaypointEdge>> newAdjacency = new HashMap<>();
      newWaypoints.add(new Waypoint(villageCenter, null));
      Set<BlockPos> seen = new HashSet<>();
      seen.add(villageCenter);

      for (BuildingInstance building : buildings) {
         BlockPos anchor = building.resolvePathAnchor();
         if (anchor != null && seen.add(anchor)) {
            newWaypoints.add(new Waypoint(anchor, building.getId()));
         }
      }

      for (Waypoint wp : newWaypoints) {
         newAdjacency.put(wp, new ArrayList<>());
      }

      int n = newWaypoints.size();
      if (n < 2) {
         this.snapshot = new VillageWaypointGraph.GraphSnapshot(List.copyOf(newWaypoints), Map.of());
         long elapsedMs = (System.nanoTime() - rebuildStartNanos) / 1000000L;
         LOGGER.info("[Millenaire] Waypoint graph rebuilt: {} nodes, 0 edges (too few) in {} ms", n, elapsedMs);
      } else {
         int[] parent = new int[n];
         int i = 0;

         while (i < n) {
            parent[i] = i++;
         }

         i = 0;
         int shortRejected = 0;
         int longValidated = 0;
         int longRejected = 0;
         int longSkippedTransitive = 0;

         try (WaypointTraversalTester tester = level != null ? new WaypointTraversalTester(level, village) : null) {
            double shortSq = 900.0;
            double maxSq = 4096.0;

            for (int ix = 0; ix < n; ix++) {
               Waypoint a = newWaypoints.get(ix);

               for (int j = ix + 1; j < n; j++) {
                  Waypoint b = newWaypoints.get(j);
                  double distSq = a.pos().distSqr(b.pos());
                  if (!(distSq > shortSq)) {
                     List<BlockPos> nodes = List.of();
                     if (tester != null) {
                        WaypointTraversalTester.Result r = tester.findPath(a.pos(), b.pos());
                        if (!r.reachable()) {
                           shortRejected++;
                           continue;
                        }

                        nodes = r.nodes();
                     }

                     addEdge(newAdjacency, a, b, Math.sqrt(distSq), nodes);
                     union(parent, ix, j);
                     i++;
                  }
               }
            }

            for (int ix = 0; ix < n; ix++) {
               Waypoint a = newWaypoints.get(ix);

               for (int j = ix + 1; j < n; j++) {
                  Waypoint b = newWaypoints.get(j);
                  double distSq = a.pos().distSqr(b.pos());
                  if (!(distSq <= shortSq) && !(distSq > maxSq)) {
                     if (find(parent, ix) == find(parent, j)) {
                        longSkippedTransitive++;
                     } else {
                        List<BlockPos> nodes = List.of();
                        if (tester != null) {
                           WaypointTraversalTester.Result r = tester.findPath(a.pos(), b.pos());
                           if (!r.reachable()) {
                              longRejected++;
                              continue;
                           }

                           nodes = r.nodes();
                        }

                        addEdge(newAdjacency, a, b, Math.sqrt(distSq), nodes);
                        longValidated++;
                     }
                  }
               }
            }
         }

         int totalEdges = i + longValidated;
         int shortInspected = i + shortRejected;
         if (level != null && shortInspected >= 4 && shortRejected * 5 >= shortInspected * 4) {
            LOGGER.warn(
               "[Millenaire] Validating tester rejected {}/{} short edges (>= 80%) — falling back to euclidean validation for this rebuild",
               shortRejected,
               shortInspected
            );
            double maxSqLocal = 4096.0;
            int permissiveAdded = 0;

            for (int ix = 0; ix < n; ix++) {
               Waypoint a = newWaypoints.get(ix);

               for (int j = ix + 1; j < n; j++) {
                  Waypoint b = newWaypoints.get(j);
                  double distSq = a.pos().distSqr(b.pos());
                  if (!(distSq > maxSqLocal)) {
                     boolean alreadyConnected = false;

                     for (WaypointEdge edge : newAdjacency.get(a)) {
                        if (edge.target().equals(b)) {
                           alreadyConnected = true;
                           break;
                        }
                     }

                     if (!alreadyConnected) {
                        addEdge(newAdjacency, a, b, Math.sqrt(distSq), List.of());
                        permissiveAdded++;
                     }
                  }
               }
            }

            totalEdges += permissiveAdded;
            LOGGER.warn("[Millenaire] Permissive fallback added {} edges (graph now {} edges total)", permissiveAdded, totalEdges);
         }

         Map<Waypoint, List<WaypointEdge>> publishAdj = new HashMap<>(newAdjacency.size() * 2);

         for (Entry<Waypoint, List<WaypointEdge>> e : newAdjacency.entrySet()) {
            publishAdj.put(e.getKey(), List.copyOf(e.getValue()));
         }

         this.snapshot = new VillageWaypointGraph.GraphSnapshot(List.copyOf(newWaypoints), Map.copyOf(publishAdj));
         long elapsedMs = (System.nanoTime() - rebuildStartNanos) / 1000000L;
         LOGGER.info(
            "[Millenaire] Waypoint graph rebuilt: {} nodes, {} edges (short: {} valid / {} rejected, long: {} valid / {} rejected / {} skipped-transitive) in {} ms",
            new Object[]{n, totalEdges, i, shortRejected, longValidated, longRejected, longSkippedTransitive, elapsedMs}
         );
         if (elapsedMs >= 200L) {
            LOGGER.warn(
               "[Millenaire] Waypoint graph rebuild SLOW: {} ms (>= {} ms threshold) for {} nodes — see PERF roadmap", new Object[]{elapsedMs, 200L, n}
            );
         }
      }
   }

   private static void addEdge(Map<Waypoint, List<WaypointEdge>> adj, Waypoint a, Waypoint b, double cost, List<BlockPos> nodes) {
      List<BlockPos> reversed;
      if (nodes.isEmpty()) {
         reversed = List.of();
      } else {
         ArrayList<BlockPos> rev = new ArrayList<>(nodes);
         Collections.reverse(rev);
         reversed = Collections.unmodifiableList(rev);
      }

      adj.get(a).add(new WaypointEdge(b, cost, nodes));
      adj.get(b).add(new WaypointEdge(a, cost, reversed));
   }

   private static int find(int[] parent, int x) {
      while (parent[x] != x) {
         parent[x] = parent[parent[x]];
         x = parent[x];
      }

      return x;
   }

   private static void union(int[] parent, int x, int y) {
      int rx = find(parent, x);
      int ry = find(parent, y);
      if (rx != ry) {
         parent[rx] = ry;
      }
   }

   @Nullable
   public Waypoint findNearestWaypoint(BlockPos pos) {
      return findNearestWaypoint(this.snapshot.waypoints(), pos);
   }

   @Nullable
   private static Waypoint findNearestWaypoint(List<Waypoint> wps, BlockPos pos) {
      Waypoint nearest = null;
      double nearestDistSq = Double.MAX_VALUE;

      for (Waypoint wp : wps) {
         double distSq = wp.pos().distSqr(pos);
         if (distSq < nearestDistSq) {
            nearestDistSq = distSq;
            nearest = wp;
         }
      }

      return nearest;
   }

   public List<BlockPos> findPath(BlockPos from, BlockPos to) {
      VillageWaypointGraph.GraphSnapshot s = this.snapshot;
      List<Waypoint> wps = s.waypoints();
      Map<Waypoint, List<WaypointEdge>> adj = s.adjacency();
      if (wps.size() < 2) {
         return Collections.emptyList();
      }

      Waypoint startWp = findNearestWaypoint(wps, from);
      Waypoint endWp = findNearestWaypoint(wps, to);
      if (startWp != null && endWp != null) {
         if (startWp.equals(endWp)) {
            return Collections.emptyList();
         }

         Map<Waypoint, Double> gScore = new HashMap<>();
         Map<Waypoint, Waypoint> cameFrom = new HashMap<>();
         Set<Waypoint> closed = new HashSet<>();
         gScore.put(startWp, 0.0);
         PriorityQueue<Waypoint> open = new PriorityQueue<>(
            Comparator.comparingDouble(wp -> gScore.getOrDefault(wp, Double.MAX_VALUE) + this.heuristic(wp, endWp))
         );
         open.add(startWp);

         while (!open.isEmpty()) {
            Waypoint current = open.poll();
            if (current.equals(endWp)) {
               return this.reconstructPath(cameFrom, current);
            }

            if (!closed.contains(current)) {
               closed.add(current);

               for (WaypointEdge edge : adj.getOrDefault(current, Collections.emptyList())) {
                  Waypoint neighbor = edge.target();
                  if (!closed.contains(neighbor)) {
                     double tentativeG = gScore.getOrDefault(current, Double.MAX_VALUE) + edge.cost();
                     if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                        gScore.put(neighbor, tentativeG);
                        cameFrom.put(neighbor, current);
                        open.add(neighbor);
                     }
                  }
               }
            }
         }

         return Collections.emptyList();
      } else {
         return Collections.emptyList();
      }
   }

   public boolean isAvailable() {
      return this.snapshot.waypoints().size() >= 2;
   }

   public List<Waypoint> getWaypoints() {
      return Collections.unmodifiableList(this.snapshot.waypoints());
   }

   public List<VillageWaypointGraph.DirectedEdge> getEdges() {
      VillageWaypointGraph.GraphSnapshot s = this.snapshot;
      List<Waypoint> wps = s.waypoints();
      Map<Waypoint, List<WaypointEdge>> adj = s.adjacency();
      List<VillageWaypointGraph.DirectedEdge> out = new ArrayList<>();
      Set<Waypoint> seen = new HashSet<>();

      for (Waypoint a : wps) {
         seen.add(a);

         for (WaypointEdge edge : adj.getOrDefault(a, Collections.emptyList())) {
            if (!seen.contains(edge.target())) {
               out.add(new VillageWaypointGraph.DirectedEdge(a, edge.target(), edge.cost(), edge.pathNodes()));
            }
         }
      }

      return out;
   }

   public int waypointCount() {
      return this.snapshot.waypoints().size();
   }

   private double heuristic(Waypoint a, Waypoint b) {
      return Math.sqrt(a.pos().distSqr(b.pos()));
   }

   private List<BlockPos> reconstructPath(Map<Waypoint, Waypoint> cameFrom, Waypoint current) {
      List<BlockPos> path = new ArrayList<>();

      while (cameFrom.containsKey(current)) {
         path.add(current.pos());
         current = cameFrom.get(current);
      }

      Collections.reverse(path);
      return path;
   }

   public record DirectedEdge(Waypoint from, Waypoint to, double cost, List<BlockPos> pathNodes) {
   }

   private record GraphSnapshot(List<Waypoint> waypoints, Map<Waypoint, List<WaypointEdge>> adjacency) {
      static final VillageWaypointGraph.GraphSnapshot EMPTY = new VillageWaypointGraph.GraphSnapshot(List.of(), Map.of());
   }
}
