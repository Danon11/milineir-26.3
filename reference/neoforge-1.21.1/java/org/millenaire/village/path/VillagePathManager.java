package org.millenaire.village.path;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.block.MillPathBlock;
import org.millenaire.block.MillPathSlabBlock;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.SpecialPoint;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.millenaire.world.TerrainPreparer;
import org.slf4j.Logger;

public class VillagePathManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_A_STAR_NODES = 5000;
   private static final int REACHABILITY_PROBE_BUDGET = 5000;
   private final List<PathRoute> pendingRoutes = new ArrayList<>();
   private final List<List<PathEntry>> pathsToBuild = new ArrayList<>();
   private int buildPathIndex;
   private int buildEntryIndex;
   private List<BlockPos> pathsToClear = new ArrayList<>();
   private int clearIndex;
   private final Set<BlockPos> allNewPathPositions = new HashSet<>();
   private final Map<BlockPos, Integer> pathLevelByPos = new HashMap<>();
   @Nullable
   private BuildingId thBuildingId;
   private final Map<BuildingId, PathDiagnostic> lastDiagnostics = new HashMap<>();
   private final List<PathDiagnostic> lateralDiagnostics = new ArrayList<>();
   private long lastRecalcTick = Long.MIN_VALUE;
   private final transient Map<BuildingId, VillagePathManager.DiagSourceInfo> pendingDiagSources = new HashMap<>();
   @Nullable
   private transient BlockPos pendingThPosForDiag;
   private int pathRecheckFailNights = 0;
   private static final int MAX_FOOTPRINT_PROBE = 16;

   public void recalculatePaths(ServerLevel level, Village village, boolean autobuild) {
      if ((Boolean)MillenaireServerConfig.SERVER.buildPaths.get()) {
         VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
         if (villageType != null) {
            this.pathRecheckFailNights = 0;
            List<PathRouter.BuildingInfo> buildingInfos = new ArrayList<>();
            List<PathRouter.NodeInfo> nodeInfos = new ArrayList<>();
            BlockPos thPos = village.getCenter();
            boolean thFound = false;
            this.thBuildingId = null;

            for (BuildingInstance b : village.getBuildings()) {
               if (isPathContributor(b)) {
                  BuildingPlanSet planSet = b.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(b.getPlanSetId()) : null;
                  if (planSet != null && planSet.isTownHall()) {
                     thFound = true;
                     this.thBuildingId = b.getId();
                     BlockPos thAnchor = resolvePathDestination(b);
                     if (thAnchor != null) {
                        thPos = thAnchor;
                     }
                     break;
                  }
               }
            }

            this.lastDiagnostics.clear();
            this.lateralDiagnostics.clear();
            this.lastRecalcTick = level.getGameTime();
            this.pendingDiagSources.clear();
            this.pendingThPosForDiag = null;
            Map<BuildingId, VillagePathManager.DiagSourceInfo> diagSources = this.pendingDiagSources;
            Map<BuildingId, PathFailureReason> preRouteFailures = new HashMap<>();
            Map<Long, Integer> buildingFloorsEarly = computeBuildingFloors(village);
            Set<Long> borderCellsEarly = computeBorderCells(village);
            BlockPos thPosFinal = thPos;

            for (BuildingInstance b : village.getBuildings()) {
               if (isPathContributor(b)) {
                  BuildingPlanSet planSet = b.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(b.getPlanSetId()) : null;
                  boolean isTH = planSet != null && planSet.isTownHall();
                  if (!isTH) {
                     if (planSet == null || !planSet.tags().contains("nopaths")) {
                        BlockPos pathStart = resolvePathDestination(b);
                        boolean usedFallback = false;
                        if (pathStart == null) {
                           pathStart = findFallbackAnchor(level, b, thPosFinal, buildingFloorsEarly, borderCellsEarly);
                           if (pathStart != null) {
                              usedFallback = true;
                              String planSetIdStr = b.getPlanSetId() != null ? b.getPlanSetId().toString() : "unknown";
                              LOGGER.info(
                                 "[Path] village={} building={}@{} fallback_anchor={}",
                                 new Object[]{village.getVillageTypeId(), planSetIdStr, b.getOrigin(), pathStart}
                              );
                           }
                        }

                        if (pathStart == null) {
                           if (b.getId() != null) {
                              preRouteFailures.put(b.getId(), PathFailureReason.NO_PATH_START);
                           }
                        } else {
                           if (planSet != null && planSet.tags().contains("pathnode")) {
                              nodeInfos.add(new PathRouter.NodeInfo(pathStart));
                           }

                           int pathLevel = 0;
                           int pathWidth = 2;
                           if (planSet != null && b.getVariant() != null) {
                              BuildingPlanSet.LevelDef levelDef = planSet.getLevel(b.getVariant(), b.getLevel());
                              if (levelDef != null) {
                                 pathLevel = levelDef.pathLevel();
                                 pathWidth = levelDef.pathWidth();
                              }
                           }

                           List<String> tags = planSet != null ? planSet.tags() : List.of();
                           buildingInfos.add(new PathRouter.BuildingInfo(b.getId(), pathStart, false, tags, pathLevel, pathWidth));
                           if (b.getId() != null) {
                              diagSources.put(b.getId(), new VillagePathManager.DiagSourceInfo(b, usedFallback, pathLevel));
                           }
                        }
                     } else if (b.getId() != null) {
                        preRouteFailures.put(b.getId(), PathFailureReason.NO_PATHS_TAG);
                     }
                  }
               }
            }

            for (Entry<BuildingId, PathFailureReason> e : preRouteFailures.entrySet()) {
               BuildingInstance b = findBuildingById(village, e.getKey());
               if (b != null) {
                  String planSetIdStr = b.getPlanSetId() != null ? b.getPlanSetId().toString() : "unknown";
                  this.lastDiagnostics
                     .put(
                        e.getKey(),
                        new PathDiagnostic(
                           e.getKey(), planSetIdStr, b.getOrigin(), 0, 0, b.getOrigin(), false, thPos, false, e.getValue(), AStarFailureDetail.empty(), 0, 0
                        )
                     );
               }
            }

            if (!thFound) {
               LOGGER.warn("Path: NO town hall found in village {}!", village.getVillageTypeId());
            }

            this.pendingThPosForDiag = thPos;
            Set<BlockPos> unreachableNodePos = new HashSet<>();

            for (PathRouter.NodeInfo ni : nodeInfos) {
               if (!isReachableFromTH(level, ni.pos(), thPos, buildingFloorsEarly, borderCellsEarly, 5000)) {
                  unreachableNodePos.add(ni.pos());
                  LOGGER.warn("Path: pathnode at {} unreachable from TH — excluded from routing", ni.pos());
               }
            }

            List<PathRouter.NodeInfo> filteredNodes = nodeInfos.stream().filter(n -> !unreachableNodePos.contains(n.pos())).toList();
            Set<BuildingId> unreachableIds = Set.of();
            List<PathRoute> routes = PathRouter.computeRoutes(buildingInfos, filteredNodes, thPos, villageType.pathMaterials(), unreachableIds);
            if (autobuild) {
               Set<BlockPos> newPositions = new HashSet<>();
               Map<BlockPos, Integer> newLevels = new HashMap<>();
               Map<Long, Integer> buildingFloors = computeBuildingFloors(village);
               Set<Long> borderCells = computeBorderCells(village);
               int routeFail = 0;

               for (PathRoute route : routes) {
                  VillagePathManager.TraceResult tr = findTraceWithFallbackDetailed(level, village, route, buildingFloors, borderCells);
                  List<BlockPos> trace = tr.trace();
                  if (trace == null) {
                     routeFail++;
                     this.recordDiagnostic(route, tr, diagSources, thPos, 0);
                  } else {
                     PathMaterials.MaterialPair mat = PathMaterials.resolve(route.material());
                     if (mat == null) {
                        this.recordDiagnosticExplicit(route, diagSources, thPos, trace.size(), 0, PathFailureReason.UNKNOWN_MATERIAL, tr.detail());
                     } else {
                        int routePathLevel = route.pathLevel();
                        BuildingId routeSourceId = route.sourceId();
                        BuildingId routeDestId = route.destinationId();
                        BuildingId thIdSnap = this.thBuildingId;
                        List<PathEntry> entries = PathBlockPlacer.buildPath(
                           trace,
                           mat.fullBlock(),
                           mat.slabBlock(),
                           route.width(),
                           routePathLevel,
                           pos -> isProtectedFromPathBuilding(level, village, pos)
                              || isInsideForeignFootprint(village, pos, routeSourceId, routeDestId, thIdSnap),
                           pos -> isHardProtectedFromPath(level, village, pos),
                           pos -> isReplaceableForPath(level, pos) && !isSurfaceLiquid(level, pos),
                           pos -> newLevels.getOrDefault(pos, -1),
                           pos -> hasHeadroom(level, pos),
                           (x, z) -> groundForPathing(level, buildingFloors, x, z, true),
                           pos -> canFillBelowPath(level, pos),
                           pos -> canCutForHeadroom(level, pos),
                           (x, z) -> lockedSurfaceHalfYAt(buildingFloors, x, z)
                        );

                        for (PathEntry entry : entries) {
                           level.setBlock(entry.pos(), entry.state(), 3);
                           newPositions.add(entry.pos());
                           newLevels.put(entry.pos(), routePathLevel);
                        }

                        this.recordDiagnostic(route, tr, diagSources, thPos, entries.size());
                     }
                  }
               }

               if (routeFail > 0) {
                  LOGGER.warn("Path autobuild: {} of {} routes failed A*", routeFail, routes.size());
               }

               this.rewriteDiagnosticsConnectivity(newLevels, thPos, this.foreignFootprintProbe(village), village);
               this.pathLevelByPos.clear();
               this.pathLevelByPos.putAll(newLevels);
               newPositions.addAll(this.pathLevelByPos.keySet());
               this.clearOldPaths(level, village, newPositions);
            } else {
               this.pendingRoutes.clear();
               this.pendingRoutes.addAll(routes);
               this.pathsToBuild.clear();
               this.buildPathIndex = 0;
               this.buildEntryIndex = 0;
               this.pathsToClear.clear();
               this.clearIndex = 0;
               this.allNewPathPositions.clear();
               this.pathLevelByPos.clear();
            }
         }
      }
   }

   public void tick(ServerLevel level, Village village) {
      if (!this.pendingRoutes.isEmpty()) {
         PathRoute route = this.pendingRoutes.removeFirst();
         Map<Long, Integer> buildingFloors = computeBuildingFloors(village);
         Set<Long> borderCells = computeBorderCells(village);
         VillagePathManager.TraceResult tr = findTraceWithFallbackDetailed(level, village, route, buildingFloors, borderCells);
         List<BlockPos> trace = tr.trace();
         BlockPos thPosSnap = this.pendingThPosForDiag != null ? this.pendingThPosForDiag : village.getCenter();
         if (trace == null) {
            this.recordDiagnostic(route, tr, this.pendingDiagSources, thPosSnap, 0);
            this.checkRoutesComplete(level, village);
         } else {
            PathMaterials.MaterialPair mat = PathMaterials.resolve(route.material());
            if (mat == null) {
               LOGGER.warn("Unknown path material: {}", route.material());
               this.recordDiagnosticExplicit(route, this.pendingDiagSources, thPosSnap, trace.size(), 0, PathFailureReason.UNKNOWN_MATERIAL, tr.detail());
               this.checkRoutesComplete(level, village);
            } else {
               int routePathLevel = route.pathLevel();
               BuildingId routeSourceIdT = route.sourceId();
               BuildingId routeDestIdT = route.destinationId();
               BuildingId thIdSnapT = this.thBuildingId;
               List<PathEntry> entries = PathBlockPlacer.buildPath(
                  trace,
                  mat.fullBlock(),
                  mat.slabBlock(),
                  route.width(),
                  routePathLevel,
                  pos -> isProtectedFromPathBuilding(level, village, pos) || isInsideForeignFootprint(village, pos, routeSourceIdT, routeDestIdT, thIdSnapT),
                  pos -> isHardProtectedFromPath(level, village, pos),
                  pos -> isReplaceableForPath(level, pos) && !isSurfaceLiquid(level, pos),
                  pos -> this.pathLevelByPos.getOrDefault(pos, -1),
                  pos -> hasHeadroom(level, pos),
                  (x, z) -> groundForPathing(level, buildingFloors, x, z, true),
                  pos -> canFillBelowPath(level, pos),
                  pos -> canCutForHeadroom(level, pos),
                  (x, z) -> lockedSurfaceHalfYAt(buildingFloors, x, z)
               );
               if (!entries.isEmpty()) {
                  this.pathsToBuild.add(entries);

                  for (PathEntry e : entries) {
                     this.allNewPathPositions.add(e.pos());
                     this.pathLevelByPos.put(e.pos(), routePathLevel);
                  }
               }

               this.recordDiagnostic(route, tr, this.pendingDiagSources, thPosSnap, entries.size());
               this.checkRoutesComplete(level, village);
            }
         }
      }
   }

   @Nullable
   private static BuildingInstance findBuildingById(Village village, BuildingId id) {
      for (BuildingInstance b : village.getBuildings()) {
         if (id.equals(b.getId())) {
            return b;
         }
      }

      return null;
   }

   private void recordDiagnostic(
      PathRoute route, VillagePathManager.TraceResult tr, Map<BuildingId, VillagePathManager.DiagSourceInfo> diagSources, BlockPos thPos, int placedBlocks
   ) {
      BuildingId sid = route.sourceId();
      if (sid != null) {
         VillagePathManager.DiagSourceInfo info = diagSources.get(sid);
         if (info != null) {
            String planSetIdStr = info.instance().getPlanSetId() != null ? info.instance().getPlanSetId().toString() : "unknown";
            PathFailureReason failure = tr.failure();
            int traceLen = tr.trace() == null ? 0 : tr.trace().size();
            if (failure == null && tr.trace() != null && placedBlocks == 0) {
               failure = PathFailureReason.PLACEMENT_EMPTY;
            }

            PathDiagnostic diag = new PathDiagnostic(
               sid,
               planSetIdStr,
               info.instance().getOrigin(),
               info.expectedTier(),
               route.pathLevel(),
               route.from(),
               info.fallback(),
               route.to(),
               false,
               failure,
               tr.detail() != null ? tr.detail() : AStarFailureDetail.empty(),
               traceLen,
               placedBlocks,
               route.lateral()
            );
            if (route.lateral()) {
               this.lateralDiagnostics.add(diag);
            } else {
               this.lastDiagnostics.put(sid, diag);
            }
         }
      }
   }

   private void recordDiagnosticExplicit(
      PathRoute route,
      Map<BuildingId, VillagePathManager.DiagSourceInfo> diagSources,
      BlockPos thPos,
      int traceLen,
      int placedBlocks,
      PathFailureReason failure,
      AStarFailureDetail detail
   ) {
      BuildingId sid = route.sourceId();
      if (sid != null) {
         VillagePathManager.DiagSourceInfo info = diagSources.get(sid);
         if (info != null) {
            String planSetIdStr = info.instance().getPlanSetId() != null ? info.instance().getPlanSetId().toString() : "unknown";
            PathDiagnostic diag = new PathDiagnostic(
               sid,
               planSetIdStr,
               info.instance().getOrigin(),
               info.expectedTier(),
               route.pathLevel(),
               route.from(),
               info.fallback(),
               route.to(),
               false,
               failure,
               detail != null ? detail : AStarFailureDetail.empty(),
               traceLen,
               placedBlocks,
               route.lateral()
            );
            if (route.lateral()) {
               this.lateralDiagnostics.add(diag);
            } else {
               this.lastDiagnostics.put(sid, diag);
            }
         }
      }
   }

   private void rewriteDiagnosticsConnectivity(Map<BlockPos, Integer> placed, BlockPos thPos) {
      this.rewriteDiagnosticsConnectivity(placed, thPos, pos -> false, null);
   }

   private void rewriteDiagnosticsConnectivity(Map<BlockPos, Integer> placed, BlockPos thPos, Predicate<BlockPos> isForeignFootprint, @Nullable Village village) {
      if (!this.lastDiagnostics.isEmpty()) {
         if (this.thBuildingId != null) {
            this.lastDiagnostics.remove(this.thBuildingId);
         }

         Map<Long, Integer> placedLong = new HashMap<>();

         for (Entry<BlockPos, Integer> e : placed.entrySet()) {
            BlockPos p = e.getKey();
            placedLong.put(BlockPos.asLong(p.getX(), p.getY(), p.getZ()), e.getValue());
         }

         VillagePathManager.Footprint sinkFootprint = village != null ? footprintOfTownhall(village) : null;

         for (Entry<BuildingId, PathDiagnostic> e : new HashMap<>(this.lastDiagnostics).entrySet()) {
            PathDiagnostic d = e.getValue();
            boolean connected;
            if (d.source() != null && d.source().getX() == thPos.getX() && d.source().getZ() == thPos.getZ() && Math.abs(d.source().getY() - thPos.getY()) <= 2
               )
             {
               connected = true;
            } else {
               VillagePathManager.Footprint sourceFootprint = village != null ? footprintOfBuilding(village, e.getKey()) : null;
               connected = traceFromAnchor(placedLong, d.source(), thPos, isForeignFootprint, sourceFootprint, sinkFootprint);
            }

            if (connected != d.connected()) {
               this.lastDiagnostics
                  .put(
                     e.getKey(),
                     new PathDiagnostic(
                        d.building(),
                        d.planSetId(),
                        d.origin(),
                        d.expectedTier(),
                        d.effectiveTier(),
                        d.source(),
                        d.sourceIsFallback(),
                        d.destination(),
                        connected,
                        d.failure(),
                        d.astarDetail(),
                        d.traceLength(),
                        d.placedBlocks()
                     )
                  );
            }
         }
      }
   }

   @Nullable
   private static VillagePathManager.Footprint footprintOfBuilding(Village village, BuildingId id) {
      BuildingInstance b = village.findBuildingById(id);
      if (b == null) {
         return null;
      }

      int ox = b.getOrigin().getX();
      int oz = b.getOrigin().getZ();
      return new VillagePathManager.Footprint(ox + b.getCachedMinX(), ox + b.getCachedMaxX(), oz + b.getCachedMinZ(), oz + b.getCachedMaxZ());
   }

   @Nullable
   private static VillagePathManager.Footprint footprintOfTownhall(Village village) {
      BuildingInstance th = village.getTownhall();
      if (th == null) {
         return null;
      }

      int ox = th.getOrigin().getX();
      int oz = th.getOrigin().getZ();
      return new VillagePathManager.Footprint(ox + th.getCachedMinX(), ox + th.getCachedMaxX(), oz + th.getCachedMinZ(), oz + th.getCachedMaxZ());
   }

   private Predicate<BlockPos> foreignFootprintProbe(Village village) {
      return pos -> {
         BuildingInstance b = village.getBuildingAt(pos);
         if (b == null) {
            return false;
         } else {
            return this.thBuildingId != null && this.thBuildingId.equals(b.getId()) ? false : b.getId() != null;
         }
      };
   }

   @Nullable
   private static List<BlockPos> findTraceWithFallback(
      ServerLevel level, Village village, PathRoute route, Map<Long, Integer> buildingFloors, Set<Long> borderCells
   ) {
      return findTraceWithFallbackDetailed(level, village, route, buildingFloors, borderCells).trace();
   }

   private static VillagePathManager.TraceResult findTraceWithFallbackDetailed(
      ServerLevel level, Village village, PathRoute route, Map<Long, Integer> buildingFloors, Set<Long> borderCells
   ) {
      BlockPos from = route.from();
      BlockPos to = route.to();
      PathGridAStar.GroundHeightProvider ground = (x, z) -> {
         if (x == from.getX() && z == from.getZ()) {
            return from.getY();
         } else {
            return x == to.getX() && z == to.getZ() ? to.getY() : groundForPathing(level, buildingFloors, x, z, true);
         }
      };
      PathGridAStar.TraversabilityCheck traversable = makeTraversable(level, from, to, ground, borderCells, false);
      PathGridAStar.Result first = PathGridAStar.search(from, to, ground, traversable, PathGridAStar.Weights.defaults(), 5000);
      if (first.success()) {
         AStarFailureDetail d = new AStarFailureDetail(first.nodesExplored(), 0, 0, 0, 0, 0, first.reason(), null, null);
         return new VillagePathManager.TraceResult(first.path(), d, null);
      } else {
         PathGridAStar.Result second = PathGridAStar.search(from, to, ground, traversable, PathGridAStar.Weights.relaxed(), 5000);
         if (second.success()) {
            LOGGER.info(
               "Path A* fallback-relaxed succeeded from {} to {} (nodes={}, firstReason={})", new Object[]{from, to, second.nodesExplored(), first.reason()}
            );
            AStarFailureDetail d = new AStarFailureDetail(first.nodesExplored(), second.nodesExplored(), 0, 0, 0, 0, first.reason(), second.reason(), null);
            return new VillagePathManager.TraceResult(second.path(), d, null);
         } else {
            PathGridAStar.TraversabilityCheck permissive = makeTraversable(level, from, to, ground, borderCells, true);
            PathGridAStar.Result third = PathGridAStar.search(from, to, ground, permissive, PathGridAStar.Weights.relaxed(), 5000);
            if (third.success()) {
               LOGGER.info(
                  "Path A* stage-3 permissive succeeded from {} to {} (nodes={}, defaultReason={}, relaxedReason={})",
                  new Object[]{from, to, third.nodesExplored(), first.reason(), second.reason()}
               );
               AStarFailureDetail d = new AStarFailureDetail(
                  first.nodesExplored(), second.nodesExplored(), third.nodesExplored(), 0, 0, 0, first.reason(), second.reason(), third.reason()
               );
               return new VillagePathManager.TraceResult(third.path(), d, null);
            } else {
               LOGGER.warn(
                  "Path A* failed from {} to {} (distance: {}). default={} nodes, reason={}; relaxed={} nodes, reason={}; permissive={} nodes, reason={}",
                  new Object[]{
                     from,
                     to,
                     String.format("%.0f", Math.sqrt(from.distSqr(to))),
                     first.nodesExplored(),
                     first.reason(),
                     second.nodesExplored(),
                     second.reason(),
                     third.nodesExplored(),
                     third.reason()
                  }
               );
               AStarFailureDetail d = new AStarFailureDetail(
                  first.nodesExplored(), second.nodesExplored(), third.nodesExplored(), 0, 0, 0, first.reason(), second.reason(), third.reason()
               );
               return new VillagePathManager.TraceResult(null, d, PathFailureReason.A_STAR_FAILED);
            }
         }
      }
   }

   private static boolean isReachableFromTH(
      ServerLevel level, BlockPos from, BlockPos thPos, Map<Long, Integer> buildingFloors, Set<Long> borderCells, int budget
   ) {
      if (from.equals(thPos)) {
         return true;
      }

      PathGridAStar.GroundHeightProvider ground = (x, z) -> {
         if (x == from.getX() && z == from.getZ()) {
            return from.getY();
         } else {
            return x == thPos.getX() && z == thPos.getZ() ? thPos.getY() : groundForPathing(level, buildingFloors, x, z, true);
         }
      };
      PathGridAStar.TraversabilityCheck traversable = makeTraversable(level, from, thPos, ground, borderCells, false);
      PathGridAStar.Result first = PathGridAStar.search(from, thPos, ground, traversable, PathGridAStar.Weights.defaults(), budget);
      if (first.success()) {
         return true;
      }

      PathGridAStar.Result second = PathGridAStar.search(from, thPos, ground, traversable, PathGridAStar.Weights.relaxed(), budget);
      return second.success();
   }

   private static PathGridAStar.TraversabilityCheck makeTraversable(
      ServerLevel level, BlockPos from, BlockPos to, PathGridAStar.GroundHeightProvider ground, Set<Long> borderCells, boolean permissive
   ) {
      return (x, z) -> {
         if (x == from.getX() && z == from.getZ()) {
            return true;
         }

         if (x == to.getX() && z == to.getZ()) {
            return true;
         }

         if (borderCells.contains(packXZ(x, z))) {
            return true;
         }

         int y = ground.heightAt(x, z);
         BlockPos surfacePos = new BlockPos(x, y - 1, z);
         BlockState surface = level.getBlockState(surfacePos);
         if (surface.liquid() || !surface.getFluidState().isEmpty()) {
            return false;
         }

         if (surface.isAir()) {
            return false;
         }

         if (permissive) {
            return true;
         }

         BlockPos herePos = new BlockPos(x, y, z);
         BlockState here = level.getBlockState(herePos);
         return !here.isCollisionShapeFullBlock(level, herePos);
      };
   }

   @Nullable
   private static BlockPos findFallbackAnchor(ServerLevel level, BuildingInstance b, BlockPos thPos, Map<Long, Integer> buildingFloors, Set<Long> borderCells) {
      int ox = b.getOrigin().getX();
      int oz = b.getOrigin().getZ();
      int y = b.getOrigin().getY();
      BlockPos min = new BlockPos(ox + b.getCachedMinX(), y, oz + b.getCachedMinZ());
      BlockPos max = new BlockPos(ox + b.getCachedMaxX(), y, oz + b.getCachedMaxZ());
      Predicate<BlockPos> traversable = pos -> isTraversableForFallback(level, pos, buildingFloors, borderCells);
      List<BlockPos> cands = FallbackAnchorFinder.findCandidates(min, max, traversable);
      if (cands.isEmpty()) {
         return null;
      }

      cands = FallbackAnchorFinder.sortedByDistance(cands, thPos);
      return cands.get(0);
   }

   private static boolean isTraversableForFallback(ServerLevel level, BlockPos pos, Map<Long, Integer> buildingFloors, Set<Long> borderCells) {
      if (borderCells.contains(packXZ(pos.getX(), pos.getZ()))) {
         return true;
      }

      int y = groundForPathing(level, buildingFloors, pos.getX(), pos.getZ(), true);
      BlockPos surfacePos = new BlockPos(pos.getX(), y - 1, pos.getZ());
      BlockState surface = level.getBlockState(surfacePos);
      if (surface.liquid() || !surface.getFluidState().isEmpty()) {
         return false;
      }

      if (surface.isAir()) {
         return false;
      }

      BlockPos here = new BlockPos(pos.getX(), y, pos.getZ());
      return !level.getBlockState(here).isCollisionShapeFullBlock(level, here);
   }

   static boolean traceFromAnchor(Map<Long, Integer> placedPositions, BlockPos anchor, BlockPos sink) {
      return traceFromAnchor(placedPositions, anchor, sink, pos -> false);
   }

   static boolean traceFromAnchor(Map<Long, Integer> placedPositions, BlockPos anchor, BlockPos sink, Predicate<BlockPos> isForeignFootprint) {
      return traceFromAnchor(placedPositions, anchor, sink, isForeignFootprint, null, null);
   }

   static boolean traceFromAnchor(
      Map<Long, Integer> placedPositions,
      BlockPos anchor,
      BlockPos sink,
      Predicate<BlockPos> isForeignFootprint,
      @Nullable VillagePathManager.Footprint sourceFootprint,
      @Nullable VillagePathManager.Footprint sinkFootprint
   ) {
      List<long[]> seeds = new ArrayList<>();
      int seedMinX;
      int seedMaxX;
      int seedMinZ;
      int seedMaxZ;
      if (sourceFootprint != null) {
         seedMinX = sourceFootprint.minX() - 1;
         seedMaxX = sourceFootprint.maxX() + 1;
         seedMinZ = sourceFootprint.minZ() - 1;
         seedMaxZ = sourceFootprint.maxZ() + 1;
      } else {
         seedMinX = anchor.getX() - 1;
         seedMaxX = anchor.getX() + 1;
         seedMinZ = anchor.getZ() - 1;
         seedMaxZ = anchor.getZ() + 1;
      }

      for (int x = seedMinX; x <= seedMaxX; x++) {
         for (int z = seedMinZ; z <= seedMaxZ; z++) {
            for (int dy = -2; dy <= 1; dy++) {
               int y = anchor.getY() + dy;
               long k = BlockPos.asLong(x, y, z);
               if (placedPositions.containsKey(k)) {
                  seeds.add(new long[]{k, x, y, z});
               }
            }
         }
      }

      if (seeds.isEmpty()) {
         return false;
      }

      int sinkMinX = sinkFootprint != null ? sinkFootprint.minX() - 1 : sink.getX() - 1;
      int sinkMaxX = sinkFootprint != null ? sinkFootprint.maxX() + 1 : sink.getX() + 1;
      int sinkMinZ = sinkFootprint != null ? sinkFootprint.minZ() - 1 : sink.getZ() - 1;
      int sinkMaxZ = sinkFootprint != null ? sinkFootprint.maxZ() + 1 : sink.getZ() + 1;
      Predicate<long[]> atSink = cx -> {
         int cxx = (int)cx[1];
         int cy = (int)cx[2];
         int cz = (int)cx[3];
         if (cxx < sinkMinX || cxx > sinkMaxX) {
            return false;
         } else if (cz >= sinkMinZ && cz <= sinkMaxZ) {
            int dyx = cy - sink.getY();
            return dyx >= -2 && dyx <= 1;
         } else {
            return false;
         }
      };
      Set<Long> visited = new HashSet<>();
      Deque<int[]> queue = new ArrayDeque<>();

      for (long[] seed : seeds) {
         if (visited.add(seed[0])) {
            if (atSink.test(seed)) {
               return true;
            }

            queue.add(new int[]{(int)seed[1], (int)seed[2], (int)seed[3]});
         }
      }

      int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

      while (!queue.isEmpty()) {
         int[] c = queue.poll();

         for (int[] d : dirs) {
            int nx = c[0] + d[0];
            int nz = c[2] + d[1];
            boolean placedNeighbour = false;

            for (int dy = -1; dy <= 1; dy++) {
               int ny = c[1] + dy;
               long k = BlockPos.asLong(nx, ny, nz);
               if (placedPositions.containsKey(k)) {
                  placedNeighbour = true;
                  if (visited.add(k)) {
                     long[] node = new long[]{k, nx, ny, nz};
                     if (atSink.test(node)) {
                        return true;
                     }

                     queue.add(new int[]{nx, ny, nz});
                  }
               }
            }

            if (!placedNeighbour) {
               BlockPos probe0 = new BlockPos(nx, c[1], nz);
               if (isForeignFootprint.test(probe0)) {
                  boolean foundBridge = false;

                  for (int step = 2; step <= 16 && !foundBridge; step++) {
                     int px = c[0] + d[0] * step;
                     int pz = c[2] + d[1] * step;

                     for (int dy = -step; dy <= step && !foundBridge; dy++) {
                        int py = c[1] + dy;
                        long k = BlockPos.asLong(px, py, pz);
                        if (placedPositions.containsKey(k)) {
                           if (!visited.add(k)) {
                              foundBridge = true;
                              break;
                           }

                           long[] node = new long[]{k, px, py, pz};
                           if (atSink.test(node)) {
                              return true;
                           }

                           queue.add(new int[]{px, py, pz});
                           foundBridge = true;
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private void checkRoutesComplete(ServerLevel level, Village village) {
      if (this.pendingRoutes.isEmpty()) {
         this.pathsToClear = this.scanExistingPaths(level, village, this.allNewPathPositions);
         this.clearIndex = 0;
         LOGGER.info("Path calculation complete: {} path lists, {} blocks to clear", this.pathsToBuild.size(), this.pathsToClear.size());
         if (this.pendingThPosForDiag != null) {
            this.rewriteDiagnosticsConnectivity(this.pathLevelByPos, this.pendingThPosForDiag, this.foreignFootprintProbe(village), village);
         }

         village.markDirty();
      }
   }

   public Map<BuildingId, PathDiagnostic> getLastDiagnostics() {
      return Collections.unmodifiableMap(this.lastDiagnostics);
   }

   public List<PathDiagnostic> getLateralDiagnostics() {
      return Collections.unmodifiableList(this.lateralDiagnostics);
   }

   public boolean isDiagnosticStale(ServerLevel level) {
      return this.lastRecalcTick == Long.MIN_VALUE ? true : level.getGameTime() - this.lastRecalcTick > 600L;
   }

   public boolean hasPathsToClear() {
      return this.clearIndex < this.pathsToClear.size();
   }

   public boolean hasPathsToBuild() {
      return this.buildPathIndex < this.pathsToBuild.size();
   }

   @Nullable
   public BlockPos getNextClearPos() {
      return !this.hasPathsToClear() ? null : this.pathsToClear.get(this.clearIndex);
   }

   public void advanceClear() {
      this.clearIndex++;
   }

   @Nullable
   public PathEntry getNextBuildEntry() {
      if (!this.hasPathsToBuild()) {
         return null;
      }

      List<PathEntry> current = this.pathsToBuild.get(this.buildPathIndex);
      return this.buildEntryIndex >= current.size() ? null : current.get(this.buildEntryIndex);
   }

   public void advanceBuild() {
      if (this.hasPathsToBuild()) {
         this.buildEntryIndex++;
         if (this.buildEntryIndex >= this.pathsToBuild.get(this.buildPathIndex).size()) {
            this.buildPathIndex++;
            this.buildEntryIndex = 0;
         }
      }
   }

   public void clearAllPathsNow(ServerLevel level) {
      while (this.hasPathsToClear()) {
         BlockPos pos = this.getNextClearPos();
         if (pos != null && level.isLoaded(pos)) {
            level.removeBlock(pos, false);
         }

         this.advanceClear();
      }

      for (BlockPos pos : new ArrayList<>(this.allNewPathPositions)) {
         if (level.isLoaded(pos)) {
            level.removeBlock(pos, false);
         }
      }

      int cleared = this.allNewPathPositions.size();
      this.allNewPathPositions.clear();
      this.pathLevelByPos.clear();
      LOGGER.debug("Bulk-cleared {} path blocks", cleared);
   }

   private static boolean isProtectedFromPathBuilding(ServerLevel level, Village village, BlockPos pos) {
      BuildingInstance building = village.getBuildingAt(pos);
      if (building == null) {
         return false;
      }

      if (building.getPlanSetId() != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet != null && planSet.tags().contains("nopaths")) {
            return true;
         }
      }

      return matchesSoilOrSource(building, pos);
   }

   private static boolean isInsideForeignFootprint(
      Village village, BlockPos pos, @Nullable BuildingId sourceId, @Nullable BuildingId destinationId, @Nullable BuildingId thId
   ) {
      BuildingInstance building = village.getBuildingAt(pos);
      if (building == null) {
         return false;
      } else {
         BuildingId bid = building.getId();
         if (bid == null) {
            return false;
         } else if (sourceId != null && bid.equals(sourceId)) {
            return false;
         } else {
            return destinationId != null && bid.equals(destinationId) ? false : thId == null || !bid.equals(thId);
         }
      }
   }

   private static boolean isHardProtectedFromPath(ServerLevel level, Village village, BlockPos pos) {
      BuildingInstance building = village.getBuildingAt(pos);
      return building == null ? false : matchesSoilOrSource(building, pos);
   }

   private static boolean matchesSoilOrSource(BuildingInstance building, BlockPos pos) {
      for (SpecialPoint sp : building.getResolvedPoints()) {
         String type = sp.type();
         if ("soil".equals(type) || "source".equals(type)) {
            BlockPos spPos = sp.pos();
            if (spPos.equals(pos) || spPos.above().equals(pos) || spPos.below().equals(pos)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean isSurfaceLiquid(ServerLevel level, BlockPos pos) {
      BlockState below = level.getBlockState(pos.below());
      return below.liquid() || !below.getFluidState().isEmpty();
   }

   private static boolean hasHeadroom(ServerLevel level, BlockPos pos) {
      BlockState above1 = level.getBlockState(pos.above());
      BlockState above2 = level.getBlockState(pos.above(2));
      return isPassable(above1) && isPassable(above2);
   }

   private static boolean isPassable(BlockState state) {
      if (state.isAir()) {
         return true;
      } else if (state.liquid()) {
         return false;
      } else {
         return state.getBlock() instanceof LeavesBlock ? true : !state.isSolid();
      }
   }

   private void clearOldPaths(ServerLevel level, Village village, Set<BlockPos> newPositions) {
      for (BlockPos pos : this.scanExistingPaths(level, village, newPositions)) {
         BlockState below = level.getBlockState(pos.below());
         BlockState replacement = below.isSolid() ? below : Blocks.DIRT.defaultBlockState();
         level.setBlock(pos, replacement, 3);
      }
   }

   private List<BlockPos> scanExistingPaths(ServerLevel level, Village village, Set<BlockPos> newPositions) {
      List<BlockPos> result = new ArrayList<>();
      int radius = 80;
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      if (vt != null) {
         radius = vt.radius();
      }

      int cx = village.getCenter().getX();
      int cz = village.getCenter().getZ();
      MutableBlockPos mutable = new MutableBlockPos();

      for (int x = cx - radius; x <= cx + radius; x++) {
         for (int z = cz - radius; z <= cz + radius; z++) {
            int terrainY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;

            for (int y = terrainY - 2; y <= terrainY + 3; y++) {
               mutable.set(x, y, z);
               BlockState state = level.getBlockState(mutable);
               if (isSystemPathBlock(state) && !newPositions.contains(mutable)) {
                  result.add(mutable.immutable());
               }
            }
         }
      }

      return result;
   }

   private static boolean isSystemPathBlock(BlockState state) {
      if (state.getBlock() instanceof MillPathBlock) {
         return !(Boolean)state.getValue(MillPathBlock.STABLE);
      } else {
         return state.getBlock() instanceof MillPathSlabBlock ? !(Boolean)state.getValue(MillPathSlabBlock.STABLE) : false;
      }
   }

   static boolean isReplaceableForPath(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.liquid() || !state.getFluidState().isEmpty()) {
         return false;
      } else if (isSystemPathBlock(state)) {
         return true;
      } else {
         Block block = state.getBlock();
         if (block == Blocks.GRASS_BLOCK
            || block == Blocks.SAND
            || block == Blocks.RED_SAND
            || block == Blocks.GRAVEL
            || block == Blocks.CLAY
            || block == Blocks.TERRACOTTA
            || state.is(BlockTags.DIRT)) {
            return true;
         } else {
            return block instanceof CropBlock ? false : state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE);
         }
      }
   }

   @Nullable
   private static BlockPos resolvePathDestination(BuildingInstance b) {
      return b.resolvePathAnchor();
   }

   private static boolean isPathContributor(BuildingInstance b) {
      return !b.isSubBuilding() && b.getStatus() == BuildingInstance.Status.COMPLETE;
   }

   private static Map<Long, Integer> computeBuildingFloors(Village village) {
      Map<Long, Integer> out = new HashMap<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (isPathContributor(b)) {
            BlockPos floor = resolvePathDestination(b);
            if (floor != null) {
               int floorY = floor.getY();
               int ox = b.getOrigin().getX();
               int oz = b.getOrigin().getZ();
               int minX = ox + b.getCachedMinX();
               int maxX = ox + b.getCachedMaxX();
               int minZ = oz + b.getCachedMinZ();
               int maxZ = oz + b.getCachedMaxZ();

               for (int x = minX; x <= maxX; x++) {
                  for (int z = minZ; z <= maxZ; z++) {
                     long key = packXZ(x, z);
                     Integer existing = out.get(key);
                     if (existing == null || floorY < existing) {
                        out.put(key, floorY);
                     }
                  }
               }
            }
         }
      }

      Set<Long> footprintKeys = new HashSet<>(out.keySet());

      for (BuildingInstance b : village.getBuildings()) {
         if (isPathContributor(b)) {
            int wallY = b.getWallFootY();
            int ox = b.getOrigin().getX();
            int oz = b.getOrigin().getZ();
            int minX = ox + b.getCachedMinX() - 1;
            int maxX = ox + b.getCachedMaxX() + 1;
            int minZ = oz + b.getCachedMinZ() - 1;
            int maxZ = oz + b.getCachedMaxZ() + 1;

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  long key = packXZ(x, z);
                  if (!footprintKeys.contains(key)) {
                     Integer existing = out.get(key);
                     if (existing == null || wallY < existing) {
                        out.put(key, wallY);
                     }
                  }
               }
            }
         }
      }

      return out;
   }

   private static Set<Long> computeBorderCells(Village village) {
      Set<Long> footprint = new HashSet<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (isPathContributor(b)) {
            int ox = b.getOrigin().getX();
            int oz = b.getOrigin().getZ();
            int minX = ox + b.getCachedMinX();
            int maxX = ox + b.getCachedMaxX();
            int minZ = oz + b.getCachedMinZ();
            int maxZ = oz + b.getCachedMaxZ();

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  footprint.add(packXZ(x, z));
               }
            }
         }
      }

      Set<Long> border = new HashSet<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (isPathContributor(b)) {
            int ox = b.getOrigin().getX();
            int oz = b.getOrigin().getZ();
            int minX = ox + b.getCachedMinX() - 1;
            int maxX = ox + b.getCachedMaxX() + 1;
            int minZ = oz + b.getCachedMinZ() - 1;
            int maxZ = oz + b.getCachedMaxZ() + 1;

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  long key = packXZ(x, z);
                  if (!footprint.contains(key)) {
                     border.add(key);
                  }
               }
            }
         }
      }

      return border;
   }

   private static long packXZ(int x, int z) {
      return (long)x << 32 | z & 4294967295L;
   }

   private static int groundForPathing(ServerLevel level, Map<Long, Integer> buildingFloors, int x, int z, boolean ignorePaths) {
      Integer floorY = buildingFloors.get(packXZ(x, z));
      if (floorY != null) {
         return floorY;
      } else {
         return ignorePaths ? groundHeightIgnoringPaths(level, x, z) : TerrainPreparer.getGroundHeight(level, x, z);
      }
   }

   private static int lockedSurfaceHalfYAt(Map<Long, Integer> buildingFloors, int x, int z) {
      Integer floorY = buildingFloors.get(packXZ(x, z));
      return floorY != null ? 2 * floorY : Integer.MIN_VALUE;
   }

   private static boolean canFillBelowPath(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.isAir()) {
         return true;
      } else if (isSystemPathBlock(state)) {
         return true;
      } else {
         Block block = state.getBlock();
         if (block == Blocks.GRASS_BLOCK
            || block == Blocks.SAND
            || block == Blocks.RED_SAND
            || block == Blocks.GRAVEL
            || block == Blocks.CLAY
            || block == Blocks.TERRACOTTA) {
            return true;
         } else if (state.is(BlockTags.DIRT)) {
            return true;
         } else if (state.is(BlockTags.FLOWERS)) {
            return true;
         } else {
            return block instanceof CropBlock ? false : state.is(BlockTags.REPLACEABLE);
         }
      }
   }

   private static boolean canCutForHeadroom(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.isAir()) {
         return true;
      } else if (state.is(BlockTags.LEAVES)) {
         return true;
      } else if (state.is(BlockTags.REPLACEABLE)) {
         return true;
      } else if (isSystemPathBlock(state)) {
         return true;
      } else {
         Block block = state.getBlock();
         if (block instanceof CropBlock) {
            return false;
         } else {
            return block != Blocks.GRASS_BLOCK
                  && block != Blocks.SAND
                  && block != Blocks.RED_SAND
                  && block != Blocks.GRAVEL
                  && block != Blocks.CLAY
                  && block != Blocks.TERRACOTTA
               ? state.is(BlockTags.DIRT)
               : true;
         }
      }
   }

   private static int groundHeightIgnoringPaths(ServerLevel level, int x, int z) {
      int top = level.getHeight(Types.WORLD_SURFACE, x, z);
      if (top <= level.getMinBuildHeight()) {
         top = level.getMaxBuildHeight();
      }

      MutableBlockPos mutable = new MutableBlockPos();

      for (int cy = top; cy > level.getMinBuildHeight(); cy--) {
         mutable.set(x, cy, z);
         BlockState state = level.getBlockState(mutable);
         if (!state.isAir()
            && !(state.getBlock() instanceof MillPathBlock)
            && !(state.getBlock() instanceof MillPathSlabBlock)
            && !state.is(BlockTags.LEAVES)
            && !state.is(BlockTags.LOGS)
            && !state.is(BlockTags.REPLACEABLE)
            && state.getFluidState().isEmpty()
            && state.canOcclude()) {
            return cy + 1;
         }
      }

      return level.getMinBuildHeight();
   }

   int getExistingPathLevel(BlockPos pos) {
      return this.pathLevelByPos.getOrDefault(pos, -1);
   }

   public void forEachPath(BiConsumer<BlockPos, Integer> consumer) {
      this.pathLevelByPos.forEach(consumer);
   }

   public static VillagePathManager.PlacementCheck canPlacePathAt(ServerLevel level, Village village, BlockPos pos) {
      BlockState stateAtPos = level.getBlockState(pos);
      if (stateAtPos.liquid() || !stateAtPos.getFluidState().isEmpty()) {
         return VillagePathManager.PlacementCheck.BLOCKED;
      } else if (isProtectedFromPathBuilding(level, village, pos)) {
         return VillagePathManager.PlacementCheck.BLOCKED;
      } else if (!isReplaceableForPath(level, pos) && !isSystemPathBlock(level.getBlockState(pos))) {
         return VillagePathManager.PlacementCheck.BLOCKED;
      } else {
         return !hasHeadroom(level, pos) ? VillagePathManager.PlacementCheck.BLOCKED : VillagePathManager.PlacementCheck.ALLOWED;
      }
   }

   public void addPendingRoute(PathRoute route) {
      this.pendingRoutes.add(route);
   }

   public int getPendingRouteCount() {
      return this.pendingRoutes.size();
   }

   public void addBuiltPath(List<PathEntry> entries) {
      this.pathsToBuild.add(entries);
   }

   public void setPathsToClear(List<BlockPos> positions) {
      this.pathsToClear = new ArrayList<>(positions);
   }

   public int getClearIndex() {
      return this.clearIndex;
   }

   public void setClearIndex(int index) {
      this.clearIndex = index;
   }

   public static boolean shouldRunNightlyRecalc(int failNights) {
      if (failNights < 0) {
         return false;
      } else if (failNights == 0) {
         return true;
      } else {
         return failNights <= 64 ? (failNights & failNights - 1) == 0 : failNights % 64 == 0;
      }
   }

   public void nightlyRecheck(ServerLevel level, Village village) {
      if ((Boolean)MillenaireServerConfig.SERVER.buildPaths.get()) {
         if (this.pendingRoutes.isEmpty() && !this.hasPathsToBuild() && !this.hasPathsToClear()) {
            boolean allOk = this.verifyInvariantAll(level, village);
            if (allOk) {
               if (this.pathRecheckFailNights != 0) {
                  LOGGER.info("[Path] village={} nightly recheck ok, reset counter (was {})", village.getId(), this.pathRecheckFailNights);
                  this.pathRecheckFailNights = 0;
               }
            } else {
               this.pathRecheckFailNights++;
               if (shouldRunNightlyRecalc(this.pathRecheckFailNights)) {
                  LOGGER.info("[Path] village={} nightly recalcul queued (failNights={})", village.getId(), this.pathRecheckFailNights);
                  this.recalculatePaths(level, village, false);
               }
            }
         }
      }
   }

   private boolean verifyInvariantAll(ServerLevel level, Village village) {
      BlockPos thPos = null;

      for (BuildingInstance b : village.getBuildings()) {
         if (isPathContributor(b)) {
            BuildingPlanSet planSet = b.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(b.getPlanSetId()) : null;
            if (planSet != null && planSet.isTownHall()) {
               thPos = resolvePathDestination(b);
               break;
            }
         }
      }

      if (thPos == null) {
         thPos = village.getCenter();
      }

      Map<Long, Integer> placed = new HashMap<>(this.pathLevelByPos.size());

      for (Entry<BlockPos, Integer> e : this.pathLevelByPos.entrySet()) {
         BlockPos p = e.getKey();
         placed.put(BlockPos.asLong(p.getX(), p.getY(), p.getZ()), e.getValue());
      }

      for (BuildingInstance b : village.getBuildings()) {
         if (isPathContributor(b)) {
            BuildingPlanSet planSet = b.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(b.getPlanSetId()) : null;
            if (planSet != null && !planSet.isTownHall() && !planSet.tags().contains("nopaths")) {
               if (b.getId() != null) {
                  PathDiagnostic last = this.lastDiagnostics.get(b.getId());
                  if (last != null && last.failure() == PathFailureReason.UNREACHABLE_TERRAIN) {
                     continue;
                  }
               }

               BlockPos anchor = resolvePathDestination(b);
               if (anchor != null && !traceFromAnchor(placed, anchor, thPos, this.foreignFootprintProbe(village))) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   public void save(CompoundTag tag) {
      ListTag routesList = new ListTag();

      for (PathRoute route : this.pendingRoutes) {
         CompoundTag routeTag = new CompoundTag();
         routeTag.putIntArray("from", new int[]{route.from().getX(), route.from().getY(), route.from().getZ()});
         routeTag.putIntArray("to", new int[]{route.to().getX(), route.to().getY(), route.to().getZ()});
         routeTag.putString("material", route.material());
         routeTag.putInt("width", route.width());
         routeTag.putInt("path_level", route.pathLevel());
         routeTag.putBoolean("lateral", route.lateral());
         routesList.add(routeTag);
      }

      tag.put("pending_routes", routesList);
      ListTag buildList = new ListTag();

      for (List<PathEntry> path : this.pathsToBuild) {
         CompoundTag pathTag = new CompoundTag();
         ListTag entriesList = new ListTag();

         for (PathEntry entry : path) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putIntArray("pos", new int[]{entry.pos().getX(), entry.pos().getY(), entry.pos().getZ()});
            entryTag.put("state", NbtUtils.writeBlockState(entry.state()));
            entriesList.add(entryTag);
         }

         pathTag.put("entries", entriesList);
         buildList.add(pathTag);
      }

      tag.put("paths_to_build", buildList);
      tag.putInt("build_path_index", this.buildPathIndex);
      tag.putInt("build_entry_index", this.buildEntryIndex);
      ListTag clearList = new ListTag();

      for (BlockPos pos : this.pathsToClear) {
         clearList.add(new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()}));
      }

      tag.put("paths_to_clear", clearList);
      tag.putInt("clear_index", this.clearIndex);
      ListTag levelList = new ListTag();

      for (Entry<BlockPos, Integer> entry : this.pathLevelByPos.entrySet()) {
         CompoundTag e = new CompoundTag();
         BlockPos pos = entry.getKey();
         e.putIntArray("pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
         e.putInt("level", entry.getValue());
         levelList.add(e);
      }

      tag.put("path_levels", levelList);
      tag.putInt("recheck_fail_nights", this.pathRecheckFailNights);
   }

   public void load(CompoundTag tag) {
      this.pendingRoutes.clear();
      if (tag.contains("pending_routes")) {
         ListTag routesList = tag.getList("pending_routes", 10);

         for (int i = 0; i < routesList.size(); i++) {
            CompoundTag routeTag = routesList.getCompound(i);
            int[] from = routeTag.getIntArray("from");
            int[] to = routeTag.getIntArray("to");
            String material = routeTag.getString("material");
            int width = routeTag.getInt("width");
            int pathLevel = routeTag.getInt("path_level");
            boolean lateral = routeTag.getBoolean("lateral");
            this.pendingRoutes
               .add(new PathRoute(new BlockPos(from[0], from[1], from[2]), new BlockPos(to[0], to[1], to[2]), material, width, pathLevel, null, null, lateral));
         }
      }

      this.pathsToBuild.clear();
      if (tag.contains("paths_to_build")) {
         ListTag buildList = tag.getList("paths_to_build", 10);

         for (int i = 0; i < buildList.size(); i++) {
            CompoundTag pathTag = buildList.getCompound(i);
            ListTag entriesList = pathTag.getList("entries", 10);
            List<PathEntry> entries = new ArrayList<>();

            for (int j = 0; j < entriesList.size(); j++) {
               CompoundTag entryTag = entriesList.getCompound(j);
               int[] pos = entryTag.getIntArray("pos");
               BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entryTag.getCompound("state"));
               if (!state.isAir()) {
                  entries.add(new PathEntry(new BlockPos(pos[0], pos[1], pos[2]), state));
               }
            }

            if (!entries.isEmpty()) {
               this.pathsToBuild.add(entries);
            }
         }
      }

      this.buildPathIndex = tag.getInt("build_path_index");
      this.buildEntryIndex = tag.getInt("build_entry_index");
      this.pathsToClear.clear();
      if (tag.contains("paths_to_clear")) {
         ListTag clearList = tag.getList("paths_to_clear", 11);

         for (int i = 0; i < clearList.size(); i++) {
            int[] pos = clearList.getIntArray(i);
            this.pathsToClear.add(new BlockPos(pos[0], pos[1], pos[2]));
         }
      }

      this.clearIndex = tag.getInt("clear_index");
      this.pathLevelByPos.clear();
      if (tag.contains("path_levels")) {
         ListTag levelList = tag.getList("path_levels", 10);

         for (int i = 0; i < levelList.size(); i++) {
            CompoundTag e = levelList.getCompound(i);
            int[] pos = e.getIntArray("pos");
            this.pathLevelByPos.put(new BlockPos(pos[0], pos[1], pos[2]), e.getInt("level"));
         }
      }

      this.allNewPathPositions.clear();

      for (List<PathEntry> path : this.pathsToBuild) {
         for (PathEntry entry : path) {
            this.allNewPathPositions.add(entry.pos());
         }
      }

      this.pathRecheckFailNights = tag.getInt("recheck_fail_nights");
   }

   public String toDumpJson(Village village) {
      JsonObject root = new JsonObject();
      BlockPos center = village.getCenter();
      JsonObject centerJson = new JsonObject();
      centerJson.addProperty("x", center.getX());
      centerJson.addProperty("y", center.getY());
      centerJson.addProperty("z", center.getZ());
      root.add("center", centerJson);
      int radius = 80;
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      if (vt != null) {
         radius = vt.radius();
      }

      root.addProperty("radius", radius);
      JsonArray buildings = new JsonArray();

      for (BuildingInstance b : village.getBuildings()) {
         if (isPathContributor(b)) {
            JsonObject bj = new JsonObject();
            bj.addProperty("id", b.getId() == null ? "null" : b.getId().uuid().toString());
            bj.addProperty("planSetId", b.getPlanSetId() == null ? "null" : b.getPlanSetId().toString());
            BlockPos origin = b.getOrigin();
            JsonObject oj = new JsonObject();
            oj.addProperty("x", origin.getX());
            oj.addProperty("y", origin.getY());
            oj.addProperty("z", origin.getZ());
            bj.add("origin", oj);
            int pathLevel = 0;
            boolean noPaths = false;
            BuildingPlanSet planSet = b.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(b.getPlanSetId()) : null;
            if (planSet != null) {
               noPaths = planSet.tags().contains("nopaths");
               if (b.getVariant() != null) {
                  BuildingPlanSet.LevelDef levelDef = planSet.getLevel(b.getVariant(), b.getLevel());
                  if (levelDef != null) {
                     pathLevel = levelDef.pathLevel();
                  }
               }
            }

            bj.addProperty("pathLevel", pathLevel);
            bj.addProperty("noPaths", noPaths);
            int ox = b.getOrigin().getX();
            int oz = b.getOrigin().getZ();
            JsonObject fp = new JsonObject();
            fp.addProperty("minX", ox + b.getCachedMinX());
            fp.addProperty("maxX", ox + b.getCachedMaxX());
            fp.addProperty("minZ", oz + b.getCachedMinZ());
            fp.addProperty("maxZ", oz + b.getCachedMaxZ());
            bj.add("footprint", fp);
            buildings.add(bj);
         }
      }

      root.add("buildings", buildings);
      JsonArray diags = new JsonArray();

      for (Entry<BuildingId, PathDiagnostic> e : this.lastDiagnostics.entrySet()) {
         PathDiagnostic d = e.getValue();
         JsonObject dj = new JsonObject();
         dj.addProperty("buildingId", e.getKey().uuid().toString());
         dj.addProperty("planSetId", d.planSetId() == null ? "null" : d.planSetId().toString());
         dj.add("origin", posJson(d.origin()));
         dj.addProperty("expectedTier", d.expectedTier());
         dj.addProperty("effectiveTier", d.effectiveTier());
         dj.add("source", (JsonElement)(d.source() == null ? JsonNull.INSTANCE : posJson(d.source())));
         dj.addProperty("sourceIsFallback", d.sourceIsFallback());
         dj.add("destination", (JsonElement)(d.destination() == null ? JsonNull.INSTANCE : posJson(d.destination())));
         dj.addProperty("connected", d.connected());
         dj.addProperty("failure", d.failure() == null ? "null" : d.failure().name());
         dj.addProperty("traceLength", d.traceLength());
         dj.addProperty("placedBlocks", d.placedBlocks());
         dj.addProperty("lateral", d.lateral());
         if (d.astarDetail() != null) {
            AStarFailureDetail a = d.astarDetail();
            JsonObject aj = new JsonObject();
            aj.addProperty("nodesExploredDefault", a.nodesExploredDefault());
            aj.addProperty("nodesExploredRelaxed", a.nodesExploredRelaxed());
            aj.addProperty("nodesExploredPermissive", a.nodesExploredPermissive());
            aj.addProperty("rejectedStep", a.rejectedStep());
            aj.addProperty("rejectedTraversable", a.rejectedTraversable());
            aj.addProperty("rejectedByFootprint", a.rejectedByFootprint());
            aj.addProperty("defaultReason", a.defaultReason());
            aj.addProperty("relaxedReason", a.relaxedReason());
            aj.addProperty("permissiveReason", a.permissiveReason());
            dj.add("astarDetail", aj);
         } else {
            dj.add("astarDetail", JsonNull.INSTANCE);
         }

         diags.add(dj);
      }

      root.add("diagnostics", diags);
      JsonArray lateralDiagsJson = new JsonArray();

      for (PathDiagnostic d : this.lateralDiagnostics) {
         JsonObject dj = new JsonObject();
         dj.addProperty("buildingId", d.building() != null ? d.building().uuid().toString() : "null");
         dj.addProperty("planSetId", d.planSetId() == null ? "null" : d.planSetId().toString());
         dj.add("source", (JsonElement)(d.source() == null ? JsonNull.INSTANCE : posJson(d.source())));
         dj.add("destination", (JsonElement)(d.destination() == null ? JsonNull.INSTANCE : posJson(d.destination())));
         dj.addProperty("effectiveTier", d.effectiveTier());
         dj.addProperty("failure", d.failure() == null ? "null" : d.failure().name());
         dj.addProperty("traceLength", d.traceLength());
         dj.addProperty("placedBlocks", d.placedBlocks());
         lateralDiagsJson.add(dj);
      }

      root.add("lateralDiagnostics", lateralDiagsJson);
      JsonArray positions = new JsonArray();

      for (Entry<BlockPos, Integer> e : this.pathLevelByPos.entrySet()) {
         JsonObject pj = new JsonObject();
         pj.add("pos", posJson(e.getKey()));
         pj.addProperty("tier", e.getValue());
         positions.add(pj);
      }

      root.add("path_positions", positions);
      JsonArray pending = new JsonArray();

      for (PathRoute r : this.pendingRoutes) {
         JsonObject rj = new JsonObject();
         rj.add("from", posJson(r.from()));
         rj.add("to", posJson(r.to()));
         rj.addProperty("material", r.material());
         rj.addProperty("width", r.width());
         rj.addProperty("pathLevel", r.pathLevel());
         rj.addProperty("sourceId", r.sourceId() == null ? "null" : r.sourceId().uuid().toString());
         rj.addProperty("destinationId", r.destinationId() == null ? "null" : r.destinationId().uuid().toString());
         pending.add(rj);
      }

      root.add("pending_routes", pending);
      int buildRemaining = 0;

      for (int i = this.buildPathIndex; i < this.pathsToBuild.size(); i++) {
         int start = i == this.buildPathIndex ? this.buildEntryIndex : 0;
         buildRemaining += Math.max(0, this.pathsToBuild.get(i).size() - start);
      }

      root.addProperty("paths_to_build_remaining", buildRemaining);
      root.addProperty("paths_to_clear_remaining", Math.max(0, this.pathsToClear.size() - this.clearIndex));
      root.addProperty("pending_routes_count", this.pendingRoutes.size());
      root.addProperty("lastRecalcTick", this.lastRecalcTick);
      return new GsonBuilder().setPrettyPrinting().create().toJson(root);
   }

   private static JsonObject posJson(BlockPos p) {
      JsonObject o = new JsonObject();
      o.addProperty("x", p.getX());
      o.addProperty("y", p.getY());
      o.addProperty("z", p.getZ());
      return o;
   }

   private record DiagSourceInfo(BuildingInstance instance, boolean fallback, int expectedTier) {
   }

   record Footprint(int minX, int maxX, int minZ, int maxZ) {
   }

   public enum PlacementCheck {
      ALLOWED,
      BLOCKED;
   }

   private record TraceResult(@Nullable List<BlockPos> trace, AStarFailureDetail detail, @Nullable PathFailureReason failure) {
   }
}
