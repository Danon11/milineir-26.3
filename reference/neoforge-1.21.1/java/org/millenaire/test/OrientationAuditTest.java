package org.millenaire.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModEntities;
import org.millenaire.world.BuildingPlacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class OrientationAuditTest {
   private static final Logger LOGGER = LoggerFactory.getLogger("OrientationAudit");
   private static final int PLATFORM_SIZE = 80;
   private static final int PLATFORM_DEPTH = 12;
   private static final int PROBES_PER_SIDE = 5;
   private static final int PROBE_OFFSET = 2;
   private static final double AMBIGUITY_TOLERANCE = 0.1;
   private static final int PATH_ACCURACY = 1;
   private static final String[] SIDE_NAMES = new String[]{"North", "East", "South", "West"};
   private static final String[] TARGET_PRIORITIES = new String[]{"pathStartPos", "sellingPos", "sleepingPos"};
   @Nullable
   private static MillVillager auditVillager = null;

   @GameTest(template = "empty_platform", required = false, timeoutTicks = 400000, setupTicks = 1L)
   public static void orientationAudit(GameTestHelper helper) {
      if (!"true".equals(System.getProperty("millenaire.audit"))) {
         LOGGER.info("[OrientationAudit] Skipped (set -Dmillenaire.audit=true to run)");
         helper.succeed();
      } else {
         ServerLevel level = helper.getLevel();
         auditVillager = null;
         BlockPos platformOrigin = helper.absolutePos(BlockPos.ZERO);
         int surfaceY = platformOrigin.getY();
         int platformMinX = platformOrigin.getX();
         int platformMinZ = platformOrigin.getZ();
         LOGGER.info("[OrientationAudit] Platform origin: {},{},{}", new Object[]{platformMinX, surfaceY, platformMinZ});
         forceLoadChunks(level, platformMinX, platformMinZ, 80);
         LOGGER.info("[OrientationAudit] Building {}x{} platform at surface Y={} ...", new Object[]{80, 80, surfaceY});
         buildPlatform(level, platformMinX, platformMinZ, surfaceY);
         BlockState surfaceBlock = level.getBlockState(new BlockPos(platformMinX + 40, surfaceY, platformMinZ + 40));
         BlockState aboveBlock = level.getBlockState(new BlockPos(platformMinX + 40, surfaceY + 1, platformMinZ + 40));
         LOGGER.info("[OrientationAudit] Platform check: surface={}, above={}", surfaceBlock.getBlock(), aboveBlock.getBlock());
         Map<ResourceLocation, BuildingPlan> allPlans = ModCultures.getAllBuildingPlans();
         List<BuildingPlan> plans = allPlans.values()
            .stream()
            .filter(p -> p.id().getPath().matches(".*_0$"))
            .sorted(Comparator.comparing(p -> p.id().toString()))
            .toList();
         LOGGER.info("[OrientationAudit] ===== ORIENTATION AUDIT START =====");
         LOGGER.info("[OrientationAudit] {} level-0 plans to audit (out of {} total)", plans.size(), allPlans.size());
         int okCount = 0;
         int mismatchCount = 0;
         int ambiguousCount = 0;
         int skippedCount = 0;
         int unreachableCount = 0;

         for (BuildingPlan plan : plans) {
            try {
               int orient = plan.buildingOrientation();
               if (orient >= 0 && orient <= 3) {
                  int maxDim = Math.max(plan.width(), plan.depth());
                  if (maxDim + 14 > 80) {
                     LOGGER.warn("[OrientationAudit] {}: footprint {}x{} too large for platform → SKIPPED", new Object[]{plan.id(), plan.width(), plan.depth()});
                     skippedCount++;
                  } else {
                     clearPlatform(level, platformMinX, platformMinZ, surfaceY);
                     int centerX = platformMinX + (80 - plan.width()) / 2;
                     int centerZ = platformMinZ + (80 - plan.depth()) / 2;
                     int originY = surfaceY + plan.groundLevel();
                     BlockPos origin = new BlockPos(centerX, originY, centerZ);
                     boolean placed = BuildingPlacer.placeInstantly(level, plan, origin, Rotation.NONE);
                     if (!placed) {
                        LOGGER.warn("[OrientationAudit] {}: placeInstantly failed → SKIPPED", plan.id());
                        skippedCount++;
                     } else {
                        OrientationAuditTest.TargetResult target = findTarget(level, plan, origin);
                        if (target == null) {
                           LOGGER.info("[OrientationAudit] {}: no target point found → SKIPPED", plan.id());
                           skippedCount++;
                        } else {
                           int minX = origin.getX();
                           int minZ = origin.getZ();
                           int maxX = minX + plan.width() - 1;
                           int maxZ = minZ + plan.depth() - 1;
                           int probeY = surfaceY + 1;
                           Integer[] sideLengths = new Integer[4];

                           for (int side = 0; side < 4; side++) {
                              sideLengths[side] = probeSide(level, side, minX, minZ, maxX, maxZ, probeY, target.pos);
                           }

                           OrientationAuditTest.AuditResult result = computeOrientation(sideLengths);
                           String current = orient + "(" + SIDE_NAMES[orient] + ")";
                           String sidesStr = String.format(
                              "N:%s, E:%s, S:%s, W:%s", fmtLen(sideLengths[0]), fmtLen(sideLengths[1]), fmtLen(sideLengths[2]), fmtLen(sideLengths[3])
                           );
                           switch (result.status) {
                              case OK:
                                 String computed = result.orientation + "(" + SIDE_NAMES[result.orientation] + ")";
                                 if (result.orientation == orient) {
                                    LOGGER.info(
                                       "[OrientationAudit] {}: current={} computed={} target={} → OK", new Object[]{plan.id(), current, computed, target.type}
                                    );
                                    okCount++;
                                 } else {
                                    LOGGER.info(
                                       "[OrientationAudit] {}: current={} computed={} target={} sides=[{}] → MISMATCH",
                                       new Object[]{plan.id(), current, computed, target.type, sidesStr}
                                    );
                                    mismatchCount++;
                                 }
                                 break;
                              case AMBIGUOUS:
                                 LOGGER.info(
                                    "[OrientationAudit] {}: current={} sides=[{}] target={} → AMBIGUOUS",
                                    new Object[]{plan.id(), current, sidesStr, target.type}
                                 );
                                 ambiguousCount++;
                                 break;
                              case UNREACHABLE:
                                 LOGGER.info(
                                    "[OrientationAudit] {}: current={} sides=[{}] target={} → UNREACHABLE",
                                    new Object[]{plan.id(), current, sidesStr, target.type}
                                 );
                                 unreachableCount++;
                           }
                        }
                     }
                  }
               } else {
                  LOGGER.warn("[OrientationAudit] {}: invalid building_orientation={} → SKIPPED", plan.id(), orient);
                  skippedCount++;
               }
            } catch (Exception e) {
               LOGGER.error("[OrientationAudit] {}: unexpected error → SKIPPED", plan.id(), e);
               skippedCount++;
            }
         }

         LOGGER.info(
            "[OrientationAudit] ===== RESULTS: {} OK, {} MISMATCH, {} AMBIGUOUS, {} UNREACHABLE, {} SKIPPED =====",
            new Object[]{okCount, mismatchCount, ambiguousCount, unreachableCount, skippedCount}
         );
         helper.succeed();
      }
   }

   private static void forceLoadChunks(ServerLevel level, int minX, int minZ, int size) {
      int minChunkX = minX >> 4;
      int minChunkZ = minZ >> 4;
      int maxChunkX = minX + size - 1 >> 4;
      int maxChunkZ = minZ + size - 1 >> 4;
      Set<ChunkPos> chunks = new HashSet<>();

      for (int cx = minChunkX; cx <= maxChunkX; cx++) {
         for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            chunks.add(new ChunkPos(cx, cz));
            level.getChunk(cx, cz, ChunkStatus.FULL, true);
         }
      }

      LOGGER.info("[OrientationAudit] Force-loaded {} chunks ({},{} to {},{})", new Object[]{chunks.size(), minChunkX, minChunkZ, maxChunkX, maxChunkZ});
   }

   private static void buildPlatform(ServerLevel level, int minX, int minZ, int surfaceY) {
      for (int x = minX; x < minX + 80; x++) {
         for (int z = minZ; z < minZ + 80; z++) {
            for (int y = surfaceY - 12; y <= surfaceY; y++) {
               level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
            }

            for (int y = surfaceY + 1; y <= surfaceY + 40; y++) {
               level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
         }
      }
   }

   private static void clearPlatform(ServerLevel level, int minX, int minZ, int surfaceY) {
      for (int x = minX; x < minX + 80; x++) {
         for (int z = minZ; z < minZ + 80; z++) {
            for (int y = surfaceY - 12; y <= surfaceY; y++) {
               BlockState current = level.getBlockState(new BlockPos(x, y, z));
               if (!current.is(Blocks.STONE)) {
                  level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
               }
            }

            for (int y = surfaceY + 1; y <= surfaceY + 40; y++) {
               BlockState current = level.getBlockState(new BlockPos(x, y, z));
               if (!current.isAir()) {
                  level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
               }
            }
         }
      }
   }

   @Nullable
   private static OrientationAuditTest.TargetResult findTarget(ServerLevel level, BuildingPlan plan, BlockPos origin) {
      StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);

      for (String targetType : TARGET_PRIORITIES) {
         for (SpecialPoint sp : plan.specialPoints()) {
            if (sp.isType(targetType)) {
               BlockPos rotated = StructureTemplate.calculateRelativePosition(settings, sp.pos());
               BlockPos absolute = rotated.offset(origin);
               BlockPos adjusted = adjustToStandable(level, absolute);
               return new OrientationAuditTest.TargetResult(adjusted, targetType);
            }
         }
      }

      return null;
   }

   private static BlockPos adjustToStandable(ServerLevel level, BlockPos pos) {
      for (int dy = 0; dy <= 5; dy++) {
         BlockPos candidate = pos.above(dy);
         if (!level.getBlockState(candidate).isSolid() && level.getBlockState(candidate.below()).isSolid()) {
            return candidate;
         }
      }

      return pos;
   }

   @Nullable
   private static Integer probeSide(ServerLevel level, int side, int minX, int minZ, int maxX, int maxZ, int probeY, BlockPos target) {
      List<BlockPos> probePoints = generateProbePoints(side, minX, minZ, maxX, maxZ, probeY);
      Integer bestLength = null;

      for (BlockPos probe : probePoints) {
         BlockPos below = probe.below();
         if (level.getBlockState(below).isSolid() && level.getBlockState(probe).isAir() && level.getBlockState(probe.above()).isAir()) {
            Integer pathLength = pathfind(level, probe, target);
            if (pathLength != null && (bestLength == null || pathLength < bestLength)) {
               bestLength = pathLength;
            }
         }
      }

      return bestLength;
   }

   static List<BlockPos> generateProbePoints(int side, int minX, int minZ, int maxX, int maxZ, int probeY) {
      List<BlockPos> probes = new ArrayList<>(5);

      for (int i = 1; i <= 5; i++) {
         double fraction = i / 6.0;
         switch (side) {
            case 0: {
               int x = minX + (int)Math.round(fraction * (maxX - minX));
               probes.add(new BlockPos(x, probeY, minZ - 2));
               break;
            }
            case 1: {
               int z = minZ + (int)Math.round(fraction * (maxZ - minZ));
               probes.add(new BlockPos(maxX + 2, probeY, z));
               break;
            }
            case 2: {
               int x = minX + (int)Math.round(fraction * (maxX - minX));
               probes.add(new BlockPos(x, probeY, maxZ + 2));
               break;
            }
            case 3: {
               int z = minZ + (int)Math.round(fraction * (maxZ - minZ));
               probes.add(new BlockPos(minX - 2, probeY, z));
            }
         }
      }

      return probes;
   }

   private static MillVillager getOrCreateVillager(ServerLevel level, BlockPos pos) {
      if (auditVillager == null || auditVillager.isRemoved()) {
         auditVillager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
         if (auditVillager != null) {
            auditVillager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            auditVillager.setPersistenceRequired();
            level.addFreshEntity(auditVillager);
            LOGGER.info("[OrientationAudit] Audit villager spawned (followRange={})", auditVillager.getAttributeValue(Attributes.FOLLOW_RANGE));
         }
      }

      return auditVillager;
   }

   @Nullable
   private static Integer pathfind(ServerLevel level, BlockPos from, BlockPos target) {
      MillVillager villager = getOrCreateVillager(level, from);
      if (villager == null) {
         return null;
      }

      villager.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0.0F, 0.0F);
      villager.setOnGround(true);
      Path path = villager.getNavigation().createPath(target, 1);
      return path == null ? null : path.getNodeCount();
   }

   private static OrientationAuditTest.AuditResult computeOrientation(Integer[] sideLengths) {
      Integer bestLength = null;
      int bestSide = -1;

      for (int i = 0; i < 4; i++) {
         if (sideLengths[i] != null && (bestLength == null || sideLengths[i] < bestLength)) {
            bestLength = sideLengths[i];
            bestSide = i;
         }
      }

      if (bestLength == null) {
         return new OrientationAuditTest.AuditResult(OrientationAuditTest.AuditStatus.UNREACHABLE, -1);
      }

      for (int i = 0; i < 4; i++) {
         if (i != bestSide && sideLengths[i] != null) {
            double ratio = (double)(sideLengths[i] - bestLength) / bestLength.intValue();
            if (ratio <= 0.1) {
               return new OrientationAuditTest.AuditResult(OrientationAuditTest.AuditStatus.AMBIGUOUS, bestSide);
            }
         }
      }

      return new OrientationAuditTest.AuditResult(OrientationAuditTest.AuditStatus.OK, bestSide);
   }

   private static String fmtLen(@Nullable Integer len) {
      return len == null ? "null" : String.valueOf(len);
   }

   private record AuditResult(OrientationAuditTest.AuditStatus status, int orientation) {
   }

   private enum AuditStatus {
      OK,
      AMBIGUOUS,
      UNREACHABLE;
   }

   private record TargetResult(BlockPos pos, String type) {
   }
}
