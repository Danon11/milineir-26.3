package org.millenaire.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.WallType;

public final class VillageWallGenerator {
   private final ServerLevel level;

   public VillageWallGenerator(ServerLevel level) {
      this.level = level;
   }

   public VillageWallGenerator.WallLocationResult computeWallBuildingLocations(
      VillageType villageType,
      WallType wallType,
      int maxWallRadius,
      @Nullable VillageTerrainMap terrainMap,
      @Nullable TerrainReachability reachability,
      BlockPos centre
   ) {
      BuildingPlanSet wallPlanSet = resolveSet(wallType.wallPlanSet());
      BuildingPlanSet towerPlanSet = resolveSet(wallType.towerPlanSet());
      BuildingPlanSet gatewayPlanSet = resolveSet(wallType.gatewayPlanSet());
      BuildingPlanSet cornerPlanSet = resolveSet(wallType.cornerPlanSet());
      BuildingPlanSet capRightSet = resolveSet(wallType.capRightPlanSet());
      BuildingPlanSet capLeftSet = resolveSet(wallType.capLeftPlanSet());
      BuildingPlanSet capBothSet = resolveSet(wallType.capBothPlanSet());
      if (gatewayPlanSet == null) {
         return new VillageWallGenerator.WallLocationResult(List.of());
      }

      if (cornerPlanSet == null && towerPlanSet != null) {
         cornerPlanSet = towerPlanSet;
      }

      if (wallPlanSet != null) {
         if (capRightSet == null) {
            capRightSet = wallPlanSet;
         }

         if (capLeftSet == null) {
            capLeftSet = wallPlanSet;
         }

         if (capBothSet == null) {
            capBothSet = wallPlanSet;
         }
      }

      BuildingPlan wallPlan = firstStartingPlan(wallPlanSet);
      BuildingPlan towerPlan = firstStartingPlan(towerPlanSet);
      BuildingPlan gatewayPlan = firstStartingPlan(gatewayPlanSet);
      BuildingPlan cornerPlan = firstStartingPlan(cornerPlanSet);
      BuildingPlan capRightPlan = firstStartingPlan(capRightSet);
      BuildingPlan capLeftPlan = firstStartingPlan(capLeftSet);
      BuildingPlan capBothPlan = firstStartingPlan(capBothSet);
      if (gatewayPlan == null) {
         return new VillageWallGenerator.WallLocationResult(List.of());
      }

      int wallLength = wallPlan != null ? wallPlan.width() : 1;
      int towerLength = towerPlan != null ? towerPlan.width() : 0;
      int cornerLength = cornerPlan != null ? cornerPlan.width() : 0;
      int wallRadius = gatewayPlan.width() / 2;
      int wallRadiusLimit = maxWallRadius > 0 ? maxWallRadius : villageType.radius() - wallLength - cornerLength;

      for (int buildNb = 0; wallRadius < wallRadiusLimit; buildNb++) {
         if (buildNb % (wallType.wallsBetweenTowers() + 1) == wallType.wallsBetweenTowers()) {
            wallRadius += towerLength;
         } else {
            wallRadius += wallLength;
         }
      }

      wallRadius += wallLength;
      wallRadius += cornerLength / 2;
      List<VillageWallGenerator.WallSide> sides = List.of(
         new VillageWallGenerator.WallSide(1, 0, 1, 0),
         new VillageWallGenerator.WallSide(0, 1, -1, 3),
         new VillageWallGenerator.WallSide(-1, 0, -1, 2),
         new VillageWallGenerator.WallSide(0, -1, 1, 1)
      );
      List<VillageWallGenerator.WallSegment> wallSegments = new ArrayList<>();

      for (VillageWallGenerator.WallSide side : sides) {
         BlockPos gatewayCentre = centre.offset(wallRadius * side.xMultiplier, 0, wallRadius * side.zMultiplier);
         int y = this.computeAverageYLevel(gatewayPlan, side.buildingOrientation, gatewayCentre);
         BlockPos gatewayPos = new BlockPos(gatewayCentre.getX(), y, gatewayCentre.getZ());
         List<VillageWallGenerator.WallSegment> segmentsForward = new ArrayList<>();
         List<VillageWallGenerator.WallSegment> segmentsBackward = new ArrayList<>();
         int pos = gatewayPlan.width() / 2;

         for (int i = 0; pos < wallRadiusLimit; i++) {
            BuildingPlanSet currentPlanSet;
            boolean spawn;
            int segmentLength;
            if (i % (wallType.wallsBetweenTowers() + 1) == wallType.wallsBetweenTowers()) {
               currentPlanSet = towerPlanSet;
               spawn = wallType.towerSpawn();
               segmentLength = towerLength;
            } else {
               currentPlanSet = wallPlanSet;
               spawn = wallType.wallSpawn();
               segmentLength = wallLength;
            }

            if (currentPlanSet != null) {
               this.buildNextElements(
                  terrainMap, reachability, wallType, segmentsBackward, segmentsForward, wallRadius, side, pos, currentPlanSet, spawn, true, centre
               );
            }

            pos += segmentLength;
         }

         if (wallPlanSet != null) {
            this.buildNextElements(
               terrainMap, reachability, wallType, segmentsBackward, segmentsForward, wallRadius, side, pos, wallPlanSet, wallType.wallSpawn(), true, centre
            );
         }

         pos += wallLength;
         if (cornerPlanSet != null) {
            this.buildNextElements(
               terrainMap,
               reachability,
               wallType,
               segmentsBackward,
               segmentsForward,
               wallRadius,
               side,
               pos,
               cornerPlanSet,
               wallType.cornerSpawn(),
               false,
               centre
            );
         }

         Collections.reverse(segmentsBackward);
         wallSegments.addAll(segmentsBackward);
         VillageWallGenerator.WallSegment gatewaySegment = this.computeWallElementLocation(
            terrainMap, reachability, wallType, gatewayPlanSet, gatewayPos, side.buildingOrientation, wallType.gatewaySpawn(), centre
         );
         if (gatewaySegment != null) {
            wallSegments.add(gatewaySegment);
         }

         wallSegments.addAll(segmentsForward);
      }

      this.computeWallConnections(wallSegments);
      this.smoothWalls(wallSegments, wallType);
      if (wallPlan != null) {
         this.capWalls(wallSegments, wallPlan, capRightPlan, capLeftPlan, capBothPlan);
         if (wallType.slope1LeftPlanSet() != null && wallType.slope1RightPlanSet() != null) {
            this.addSlopes(wallSegments, wallType);
         }
      }

      List<VillageWallGenerator.PlannedWallSegment> planned = new ArrayList<>(wallSegments.size());

      for (VillageWallGenerator.WallSegment s : wallSegments) {
         planned.add(new VillageWallGenerator.PlannedWallSegment(s.planId, s.pos, s.orientation, s.level));
      }

      return new VillageWallGenerator.WallLocationResult(planned);
   }

   private void buildNextElements(
      @Nullable VillageTerrainMap terrainMap,
      @Nullable TerrainReachability reachability,
      WallType wallType,
      List<VillageWallGenerator.WallSegment> locationsBackward,
      List<VillageWallGenerator.WallSegment> locationsForward,
      int wallRadius,
      VillageWallGenerator.WallSide side,
      int pos,
      BuildingPlanSet planSet,
      boolean spawn,
      boolean buildNegative,
      BlockPos centre
   ) {
      BuildingPlan plan = firstStartingPlan(planSet);
      if (plan != null) {
         int segmentLength = plan.width();
         if (side.xMultiplier != 0) {
            int deltaZ = (pos + segmentLength / 2) * side.direction;
            BlockPos p = centre.offset(wallRadius * side.xMultiplier, 0, deltaZ);
            VillageWallGenerator.WallSegment seg = this.computeWallElementLocation(
               terrainMap, reachability, wallType, planSet, p, side.buildingOrientation, spawn, centre
            );
            if (seg != null) {
               locationsForward.add(seg);
            }

            if (buildNegative) {
               if (segmentLength % 2 == 1) {
                  deltaZ += side.direction;
               }

               p = centre.offset(wallRadius * side.xMultiplier, 0, -deltaZ);
               seg = this.computeWallElementLocation(terrainMap, reachability, wallType, planSet, p, side.buildingOrientation, spawn, centre);
               if (seg != null) {
                  locationsBackward.add(seg);
               }
            }
         } else {
            int deltaX = (pos + segmentLength / 2) * side.direction;
            BlockPos p = centre.offset(deltaX, 0, wallRadius * side.zMultiplier);
            VillageWallGenerator.WallSegment seg = this.computeWallElementLocation(
               terrainMap, reachability, wallType, planSet, p, side.buildingOrientation, spawn, centre
            );
            if (seg != null) {
               locationsForward.add(seg);
            }

            if (buildNegative) {
               if (segmentLength % 2 == 1) {
                  deltaX += side.direction;
               }

               p = centre.offset(-deltaX, 0, wallRadius * side.zMultiplier);
               seg = this.computeWallElementLocation(terrainMap, reachability, wallType, planSet, p, side.buildingOrientation, spawn, centre);
               if (seg != null) {
                  locationsBackward.add(seg);
               }
            }
         }
      }
   }

   @Nullable
   private VillageWallGenerator.WallSegment computeWallElementLocation(
      @Nullable VillageTerrainMap terrainMap,
      @Nullable TerrainReachability reachability,
      WallType wallType,
      BuildingPlanSet planSet,
      BlockPos pos,
      int orientation,
      boolean spawn,
      BlockPos centre
   ) {
      BuildingPlan plan = firstStartingPlan(planSet);
      if (plan == null) {
         return null;
      }

      int finalOrientation = (orientation + plan.buildingOrientation()) % 4;
      int orientatedLength = plan.width();
      int orientatedWidth = plan.depth();
      if (finalOrientation % 2 == 1) {
         orientatedLength = plan.depth();
         orientatedWidth = plan.width();
      }

      int y = this.computeAverageYLevel(plan, orientation, pos);
      if (y > centre.getY() + wallType.maxYDelta()) {
         return null;
      }

      BlockPos buildingPos = new BlockPos(pos.getX(), y, pos.getZ());
      if (terrainMap != null) {
         BlockPos[] testPoints = new BlockPos[]{
            buildingPos,
            buildingPos.offset(orientatedLength / 2, 0, orientatedWidth / 2),
            buildingPos.offset(orientatedLength / 2, 0, -orientatedWidth / 2),
            buildingPos.offset(-orientatedLength / 2, 0, orientatedWidth / 2),
            buildingPos.offset(-orientatedLength / 2, 0, -orientatedWidth / 2)
         };
         boolean reachable = false;

         for (BlockPos tp : testPoints) {
            if (isUsableWallAnchor(terrainMap, reachability, tp)) {
               reachable = true;
               break;
            }
         }

         if (!reachable) {
            return null;
         }
      }

      int segmentLevel = -1;
      return new VillageWallGenerator.WallSegment(plan.id(), buildingPos, finalOrientation, segmentLevel);
   }

   static boolean isUsableWallAnchor(VillageTerrainMap terrainMap, @Nullable TerrainReachability reachability, BlockPos pos) {
      int lx = terrainMap.toLocalX(pos.getX());
      int lz = terrainMap.toLocalZ(pos.getZ());
      if (!terrainMap.inBounds(lx, lz)) {
         return false;
      } else if (terrainMap.isOccupied(lx, lz)) {
         return false;
      } else {
         return reachability != null
            ? reachability.isReachable(pos.getX(), pos.getZ())
            : terrainMap.canBuildAt(lx, lz) && !terrainMap.isWaterAt(lx, lz) && !terrainMap.isDangerAt(lx, lz) && terrainMap.getSpaceAbove(lx, lz) > 1;
      }
   }

   private int computeAverageYLevel(BuildingPlan plan, int orientation, BlockPos pos) {
      int orient = (orientation + plan.buildingOrientation()) % 4;
      int orientatedLength = plan.width();
      int orientatedWidth = plan.depth();
      if (orient % 2 == 1) {
         orientatedLength = plan.depth();
         orientatedWidth = plan.width();
      }

      BlockPos[] corners = new BlockPos[]{
         pos.offset(orientatedLength / 2, 0, orientatedWidth / 2),
         pos.offset(orientatedLength / 2, 0, -orientatedWidth / 2),
         pos.offset(-orientatedLength / 2, 0, orientatedWidth / 2),
         pos.offset(-orientatedLength / 2, 0, -orientatedWidth / 2)
      };
      int sum = 2;

      for (BlockPos c : corners) {
         sum += TerrainPreparer.getSurfaceOrWaterHeight(this.level, c.getX(), c.getZ());
      }

      return sum / corners.length;
   }

   private void computeWallConnections(List<VillageWallGenerator.WallSegment> segments) {
      if (!segments.isEmpty()) {
         for (int i = 0; i < segments.size(); i++) {
            VillageWallGenerator.WallSegment prev = i == 0 ? segments.get(segments.size() - 1) : segments.get(i - 1);
            VillageWallGenerator.WallSegment cur = segments.get(i);
            BuildingPlan prevPlan = ModCultures.getBuildingPlan(prev.planId);
            BuildingPlan curPlan = ModCultures.getBuildingPlan(cur.planId);
            if (prevPlan != null && curPlan != null) {
               double dx = prev.pos.getX() - cur.pos.getX();
               double dz = prev.pos.getZ() - cur.pos.getZ();
               double horizDist = Math.sqrt(dx * dx + dz * dz);
               double threshold = (prevPlan.width() + curPlan.width()) / 2.0 + 4.0;
               if (horizDist < threshold) {
                  prev.nextSegment = cur;
                  cur.previousSegment = prev;
               }
            }
         }
      }
   }

   private void smoothWalls(List<VillageWallGenerator.WallSegment> segments, WallType wallType) {
      if (!segments.isEmpty()) {
         float[] refY = new float[segments.size()];

         for (int i = 0; i < segments.size(); i++) {
            refY[i] = segments.get(i).pos.getY();
         }

         for (int run = 0; run < wallType.nbSmoothRuns(); run++) {
            float[] adjusted = new float[segments.size()];

            for (int i = 0; i < segments.size(); i++) {
               int prevId = i == 0 ? segments.size() - 1 : i - 1;
               int nextId = i == segments.size() - 1 ? 0 : i + 1;
               VillageWallGenerator.WallSegment s = segments.get(i);
               int nbPoints = 1;
               float avg = refY[i];
               if (s.previousSegment != null) {
                  nbPoints++;
                  avg += refY[prevId];
               }

               if (s.nextSegment != null) {
                  nbPoints++;
                  avg += refY[nextId];
               }

               adjusted[i] = avg / nbPoints;
            }

            System.arraycopy(adjusted, 0, refY, 0, refY.length);
         }

         for (int i = 0; i < segments.size(); i++) {
            int finalY = Math.round(refY[i]);
            if (segments.get(i).pos.getY() != finalY) {
               segments.get(i).setYLevel(finalY);
            }
         }
      }
   }

   private void capWalls(
      List<VillageWallGenerator.WallSegment> segments,
      BuildingPlan wallPlan,
      @Nullable BuildingPlan capRight,
      @Nullable BuildingPlan capLeft,
      @Nullable BuildingPlan capBoth
   ) {
      for (VillageWallGenerator.WallSegment s : segments) {
         BuildingPlan sPlan = ModCultures.getBuildingPlan(s.planId);
         if (sPlan == wallPlan) {
            boolean noPrev = s.previousSegment == null;
            boolean noNext = s.nextSegment == null;
            BuildingPlan target = null;
            if (noPrev && noNext) {
               target = capBoth;
            } else if (noPrev) {
               target = capRight;
            } else if (noNext) {
               target = capLeft;
            }

            if (target != null && target != wallPlan) {
               s.planId = target.id();
            }
         }
      }
   }

   private void addSlopes(List<VillageWallGenerator.WallSegment> segments, WallType wallType) {
      BuildingPlan wallPlan = firstStartingPlan(resolveSet(wallType.wallPlanSet()));
      if (wallPlan != null) {
         BuildingPlan[] leftSlopes = new BuildingPlan[]{
            firstStartingPlan(resolveSet(wallType.slope1LeftPlanSet())),
            firstStartingPlan(resolveSet(wallType.slope2LeftPlanSet())),
            firstStartingPlan(resolveSet(wallType.slope3LeftPlanSet()))
         };
         BuildingPlan[] rightSlopes = new BuildingPlan[]{
            firstStartingPlan(resolveSet(wallType.slope1RightPlanSet())),
            firstStartingPlan(resolveSet(wallType.slope2RightPlanSet())),
            firstStartingPlan(resolveSet(wallType.slope3RightPlanSet()))
         };

         for (VillageWallGenerator.WallSegment s : segments) {
            BuildingPlan sPlan = ModCultures.getBuildingPlan(s.planId);
            s.sloppable = sPlan != null && sPlan.id().equals(wallPlan.id());
         }

         for (VillageWallGenerator.WallSegment s : segments) {
            int y = s.pos.getY();
            if (s.previousSegment != null && s.nextSegment != null) {
               if (s.previousSegment.pos.getY() < y && s.nextSegment.pos.getY() < y) {
                  s.setYLevel(Math.max(s.previousSegment.pos.getY(), s.nextSegment.pos.getY()));
                  y = s.pos.getY();
               } else if (s.previousSegment.pos.getY() > y && s.nextSegment.pos.getY() > y) {
                  s.setYLevel(Math.min(s.previousSegment.pos.getY(), s.nextSegment.pos.getY()));
                  y = s.pos.getY();
               }
            }

            if (s.sloppable) {
               if (s.previousSegment != null && !s.previousSegment.sloppable && s.previousSegment.pos.getY() < y) {
                  s.setYLevel(s.previousSegment.pos.getY());
                  y = s.pos.getY();
               } else if (s.nextSegment != null && !s.nextSegment.sloppable && s.nextSegment.pos.getY() < y) {
                  s.setYLevel(s.nextSegment.pos.getY());
                  y = s.pos.getY();
               }

               BuildingPlan slopePlan = null;
               if (s.nextSegment != null && s.nextSegment.yTowardsPrevious > y) {
                  int deltaY = s.nextSegment.yTowardsPrevious - y;

                  for (int d = leftSlopes.length; d > 0; d--) {
                     if (deltaY >= d && leftSlopes[d - 1] != null) {
                        slopePlan = leftSlopes[d - 1];
                        s.yTowardsNext += d;
                        break;
                     }
                  }
               } else if (s.previousSegment != null && s.previousSegment.yTowardsNext > y) {
                  int deltaY = s.previousSegment.yTowardsNext - y;

                  for (int d = rightSlopes.length; d > 0; d--) {
                     if (deltaY >= d && rightSlopes[d - 1] != null) {
                        slopePlan = rightSlopes[d - 1];
                        s.yTowardsPrevious += d;
                        break;
                     }
                  }
               }

               if (slopePlan != null) {
                  s.planId = slopePlan.id();
               }
            }
         }
      }
   }

   @Nullable
   private static BuildingPlanSet resolveSet(@Nullable ResourceLocation id) {
      return id == null ? null : ModCultures.getBuildingPlanSet(id);
   }

   @Nullable
   private static BuildingPlan firstStartingPlan(@Nullable BuildingPlanSet set) {
      if (set == null) {
         return null;
      }

      String variant = set.variants().keySet().stream().findFirst().orElse(null);
      if (variant == null) {
         return null;
      }

      BuildingPlanSet.LevelDef level0 = set.getLevel(variant, 0);
      return level0 == null ? null : ModCultures.getBuildingPlan(level0.planId());
   }

   public record PlannedWallSegment(ResourceLocation planId, BlockPos pos, int orientation, int level) {
   }

   public record WallLocationResult(List<VillageWallGenerator.PlannedWallSegment> segments) {
   }

   private static final class WallSegment {
      ResourceLocation planId;
      BlockPos pos;
      final int orientation;
      final int level;
      @Nullable
      VillageWallGenerator.WallSegment previousSegment = null;
      @Nullable
      VillageWallGenerator.WallSegment nextSegment = null;
      boolean sloppable = false;
      int yTowardsPrevious;
      int yTowardsNext;

      WallSegment(ResourceLocation planId, BlockPos pos, int orientation, int level) {
         this.planId = planId;
         this.pos = pos;
         this.orientation = orientation;
         this.level = level;
         this.yTowardsPrevious = pos.getY();
         this.yTowardsNext = pos.getY();
      }

      void setYLevel(int newY) {
         int delta = newY - this.pos.getY();
         this.pos = new BlockPos(this.pos.getX(), newY, this.pos.getZ());
         this.yTowardsPrevious += delta;
         this.yTowardsNext += delta;
      }
   }

   private record WallSide(int xMultiplier, int zMultiplier, int direction, int buildingOrientation) {
   }
}
