package org.millenaire.world;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public class VillageSpawnQueue {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_CHEAP_PER_TICK = 10;
   static final int MAX_LB_CHECKED_PER_TICK = 20;
   static final int LB_COVERAGE_RADIUS_CHUNKS = 8;
   private static final int BASE_EXPENSIVE_PER_TICK = 1;
   private static final int QUEUE_PRESSURE_THRESHOLD = 50;
   private static final int MAX_EXPENSIVE_PER_TICK = 5;
   static final long LB_TTL_TICKS = 72000L;
   static final long LB_RETRY_BACKOFF_TICKS = 200L;
   private final Queue<BlockPos> candidates = new ArrayDeque<>();
   private final Deque<VillageSpawnQueue.LBCandidate> loneBuildingQueue = new ArrayDeque<>();
   private final LongOpenHashSet resolvedChunks = new LongOpenHashSet();
   private Map<TagKey<Biome>, List<VillageType>> villageBiomeIndex;
   private Map<TagKey<Biome>, List<VillageType>> lbBiomeIndex;
   private List<VillageType> villagesNoBiomeTags;
   private List<VillageType> lbsNoBiomeTags;

   public void enqueue(BlockPos chunkCenter) {
      this.candidates.add(chunkCenter);
   }

   public void markResolved(ChunkPos chunkPos) {
      this.resolvedChunks.add(chunkPos.toLong());
   }

   public static boolean couldHostAnyVillage(Collection<VillageType> types, Predicate<TagKey<Biome>> biomeTagMatcher) {
      for (VillageType vt : types) {
         if (vt.weight() > 0) {
            for (TagKey<Biome> tag : vt.biomeTags()) {
               if (biomeTagMatcher.test(tag)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public void trySpawnNext(ServerLevel level) {
      boolean log = (Boolean)MillenaireServerConfig.SERVER.logSpawnAttempts.get();
      boolean genVillages = (Boolean)MillenaireServerConfig.SERVER.generateVillages.get();
      boolean genLone = (Boolean)MillenaireServerConfig.SERVER.generateLoneBuildings.get();
      if (genVillages || genLone) {
         VillageSavedData savedData = VillageSavedData.get(level);
         VillageManager manager = savedData.getVillageManager();
         long currentTick = level.getGameTime();
         this.processMainQueue(level, savedData, manager, genVillages, genLone, log, currentTick);
         if (genVillages && genLone && currentTick % 5L == 0L) {
            this.processLBQueue(level, savedData, manager, log, currentTick);
         }
      }
   }

   private void processMainQueue(
      ServerLevel level, VillageSavedData savedData, VillageManager manager, boolean genVillages, boolean genLone, boolean log, long currentTick
   ) {
      int cheapChecked = 0;
      int expensiveBudget = computeExpensiveBudget(this.candidates.size());
      int expensiveUsed = 0;
      long spawnProtRadius = MillenaireServerConfig.SERVER.spawnProtectionRadius.getAsInt();

      while (cheapChecked < 10) {
         BlockPos candidate = this.candidates.poll();
         if (candidate == null) {
            return;
         }

         cheapChecked++;
         double distToSpawnSq = candidate.distSqr(level.getSharedSpawnPos());
         if (distToSpawnSq < spawnProtRadius * spawnProtRadius) {
            if (log) {
               LOGGER.info(
                  "[Millenaire spawn] {} rejected: within spawn protection ({} < {})",
                  new Object[]{candidate.toShortString(), (int)Math.sqrt(distToSpawnSq), spawnProtRadius}
               );
            }
         } else {
            boolean tooCloseForVillage = genVillages
               && (
                  manager.isWithinMinDistance(candidate, MillenaireServerConfig.SERVER.minVillageDistance.getAsInt())
                     || manager.isWithinMinDistanceOfLoneBuildings(
                        candidate, MillenaireServerConfig.SERVER.minVillageLoneBuildingDistance.getAsInt(), savedData.getLoneBuildingPositions()
                     )
               );
            boolean tooCloseForLone = genLone
               && manager.isWithinMinDistanceLB(
                  candidate,
                  MillenaireServerConfig.SERVER.minLoneBuildingDistance.getAsInt(),
                  MillenaireServerConfig.SERVER.minVillageLoneBuildingDistance.getAsInt(),
                  savedData.getLoneBuildingPositions()
               );
            if (genVillages && !tooCloseForVillage || genLone && !tooCloseForLone) {
               int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX(), candidate.getZ());
               if (surfaceY > level.getMinBuildHeight()) {
                  BlockPos surfacePos = new BlockPos(candidate.getX(), surfaceY, candidate.getZ());
                  if (genVillages && genLone) {
                     if (!tooCloseForVillage) {
                        VillageSpawnQueue.SpawnResult result = this.trySpawnVillage(level, surfacePos, distToSpawnSq, manager, false, log);
                        expensiveUsed++;
                        if (result == VillageSpawnQueue.SpawnResult.SPAWNED || result == VillageSpawnQueue.SpawnResult.DEFERRED) {
                           if (expensiveUsed >= expensiveBudget) {
                              return;
                           }
                           continue;
                        }

                        if (!tooCloseForLone) {
                           this.loneBuildingQueue.addLast(new VillageSpawnQueue.LBCandidate(surfacePos, distToSpawnSq, currentTick, 0L));
                           if (log) {
                              LOGGER.info("[Millenaire spawn] {} deferred to LB queue (village failed)", surfacePos.toShortString());
                           }
                        }
                     } else if (!tooCloseForLone) {
                        this.loneBuildingQueue.addLast(new VillageSpawnQueue.LBCandidate(surfacePos, distToSpawnSq, currentTick, 0L));
                        if (log) {
                           LOGGER.info("[Millenaire spawn] {} deferred to LB queue (too close for village)", surfacePos.toShortString());
                        }
                     }

                     if (expensiveUsed >= expensiveBudget) {
                        return;
                     }
                  } else if (genVillages && !tooCloseForVillage) {
                     this.trySpawnVillage(level, surfacePos, distToSpawnSq, manager, false, log);
                     if (++expensiveUsed >= expensiveBudget) {
                        return;
                     }
                  } else if (genLone && !tooCloseForLone) {
                     this.trySpawnVillage(level, surfacePos, distToSpawnSq, manager, true, log);
                     if (++expensiveUsed >= expensiveBudget) {
                        return;
                     }
                  }
               }
            } else if (log && genVillages && tooCloseForVillage) {
               LOGGER.info("[Millenaire spawn] {} skipped for village: tooClose", candidate.toShortString());
            }
         }
      }
   }

   private void processLBQueue(ServerLevel level, VillageSavedData savedData, VillageManager manager, boolean log, long currentTick) {
      int queueSize = this.loneBuildingQueue.size();
      if (queueSize != 0) {
         int scanBudget = Math.min(queueSize, 20);
         int expensiveBudget = computeExpensiveBudget(queueSize);
         int expensiveUsed = 0;

         for (int i = 0; i < scanBudget; i++) {
            VillageSpawnQueue.LBCandidate candidate = this.loneBuildingQueue.pollFirst();
            if (candidate == null) {
               return;
            }

            if (isExpired(candidate, currentTick, 72000L)) {
               if (log) {
                  LOGGER.info("[Millenaire spawn] LB candidate {} expired (TTL)", candidate.pos().toShortString());
               }
            } else if (shouldBackoff(candidate, currentTick)) {
               this.loneBuildingQueue.addLast(candidate);
            } else if (!this.isCoverageComplete(candidate.pos())) {
               VillageSpawnQueue.LBCandidate deferred = new VillageSpawnQueue.LBCandidate(
                  candidate.pos(), candidate.distToSpawnSq(), candidate.enqueuedTick(), currentTick + 200L
               );
               this.loneBuildingQueue.addLast(deferred);
            } else {
               boolean tooClose = manager.isWithinMinDistanceLB(
                  candidate.pos(),
                  MillenaireServerConfig.SERVER.minLoneBuildingDistance.getAsInt(),
                  MillenaireServerConfig.SERVER.minVillageLoneBuildingDistance.getAsInt(),
                  savedData.getLoneBuildingPositions()
               );
               if (tooClose) {
                  if (log) {
                     LOGGER.info("[Millenaire spawn] LB candidate {} dropped (too close after re-check)", candidate.pos().toShortString());
                  }
               } else {
                  if (log) {
                     LOGGER.info("[Millenaire spawn] LB candidate {} passed coverage check", candidate.pos().toShortString());
                  }

                  VillageSpawnQueue.SpawnResult result = this.trySpawnVillage(level, candidate.pos(), candidate.distToSpawnSq(), manager, true, log);
                  if (result == VillageSpawnQueue.SpawnResult.DEFERRED) {
                     this.loneBuildingQueue
                        .addLast(new VillageSpawnQueue.LBCandidate(candidate.pos(), candidate.distToSpawnSq(), candidate.enqueuedTick(), currentTick + 200L));
                  }

                  if (++expensiveUsed >= expensiveBudget) {
                     return;
                  }
               }
            }
         }
      }
   }

   static boolean isCoverageComplete(BlockPos pos, LongOpenHashSet resolved, int radiusChunks) {
      int centerCX = pos.getX() >> 4;
      int centerCZ = pos.getZ() >> 4;

      for (int cx = centerCX - radiusChunks; cx <= centerCX + radiusChunks; cx++) {
         for (int cz = centerCZ - radiusChunks; cz <= centerCZ + radiusChunks; cz++) {
            if (!resolved.contains(ChunkPos.asLong(cx, cz))) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean isCoverageComplete(BlockPos pos) {
      return isCoverageComplete(pos, this.resolvedChunks, 8);
   }

   static boolean isExpired(VillageSpawnQueue.LBCandidate candidate, long currentTick, long ttlTicks) {
      return currentTick - candidate.enqueuedTick() > ttlTicks;
   }

   static boolean shouldBackoff(VillageSpawnQueue.LBCandidate candidate, long currentTick) {
      return currentTick < candidate.retryAfterTick();
   }

   static int computeExpensiveBudget(int queueSize) {
      if (queueSize <= 50) {
         return 1;
      }

      int extra = (queueSize - 50) / 50;
      return Math.min(1 + extra, 5);
   }

   public void invalidateBiomeIndex() {
      this.villageBiomeIndex = null;
   }

   private void ensureBiomeIndex() {
      if (this.villageBiomeIndex == null) {
         this.villageBiomeIndex = new HashMap<>();
         this.lbBiomeIndex = new HashMap<>();
         this.villagesNoBiomeTags = new ArrayList<>();
         this.lbsNoBiomeTags = new ArrayList<>();

         for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
            if (vt.weight() > 0) {
               if (vt.biomeTags().isEmpty()) {
                  (vt.loneBuilding() ? this.lbsNoBiomeTags : this.villagesNoBiomeTags).add(vt);
               } else {
                  Map<TagKey<Biome>, List<VillageType>> index = vt.loneBuilding() ? this.lbBiomeIndex : this.villageBiomeIndex;

                  for (TagKey<Biome> tag : vt.biomeTags()) {
                     index.computeIfAbsent(tag, k -> new ArrayList<>()).add(vt);
                  }
               }
            }
         }

         LOGGER.debug(
            "[Millenaire spawn] Biome index built: {} village tags, {} LB tags, {} no-tag villages, {} no-tag LBs",
            new Object[]{this.villageBiomeIndex.size(), this.lbBiomeIndex.size(), this.villagesNoBiomeTags.size(), this.lbsNoBiomeTags.size()}
         );
      }
   }

   private VillageSpawnQueue.SpawnResult trySpawnVillage(
      ServerLevel level, BlockPos surfacePos, double distToSpawnSq, VillageManager manager, boolean loneBuilding, boolean log
   ) {
      String label = loneBuilding ? "lone building" : "village";
      Holder<Biome> biome = level.getBiome(surfacePos);
      ResourceLocation biomeId = biome.unwrapKey().map(k -> k.location()).orElse(null);
      if (biomeId == null) {
         return VillageSpawnQueue.SpawnResult.FAILED;
      }

      VillageSavedData savedData = VillageSavedData.get(level);
      Map<ResourceLocation, Integer> lbCounts = null;
      if (loneBuilding) {
         lbCounts = new HashMap<>();

         for (VillageSavedData.LoneBuildingEntry entry : savedData.getLoneBuildingPositions()) {
            lbCounts.merge(entry.type(), 1, Integer::sum);
         }
      }

      this.ensureBiomeIndex();
      Map<TagKey<Biome>, List<VillageType>> biomeIndex = loneBuilding ? this.lbBiomeIndex : this.villageBiomeIndex;
      List<VillageType> noBiomeTags = loneBuilding ? this.lbsNoBiomeTags : this.villagesNoBiomeTags;
      Set<VillageType> biomeCandidates = new LinkedHashSet<>(noBiomeTags);

      for (Entry<TagKey<Biome>, List<VillageType>> entry : biomeIndex.entrySet()) {
         if (biome.is(entry.getKey())) {
            biomeCandidates.addAll(entry.getValue());
         }
      }

      boolean hamletConfigEnabled = (Boolean)MillenaireServerConfig.SERVER.generateHamlets.get();
      int spawnProtRadius = MillenaireServerConfig.SERVER.spawnProtectionRadius.getAsInt();
      List<VillageType> compatible = new ArrayList<>();
      Map<Integer, List<VillageType>> byRadius = new LinkedHashMap<>();
      int filteredByHamlets = 0;
      int filteredBySpawnDist = 0;
      int filteredByMaxCount = 0;
      int filteredByMinDist = 0;

      for (VillageType vt : biomeCandidates) {
         if (!loneBuilding && !hamletConfigEnabled && !vt.hamlets().isEmpty()) {
            filteredByHamlets++;
         } else {
            if (loneBuilding && vt.minDistanceFromSpawn() >= 0) {
               long minDist = vt.minDistanceFromSpawn();
               if (distToSpawnSq <= (double)minDist * minDist) {
                  filteredByMinDist++;
                  continue;
               }
            }

            if (loneBuilding && vt.max() != -1 && lbCounts != null) {
               int count = lbCounts.getOrDefault(vt.id(), 0);
               if (count >= vt.max()) {
                  filteredByMaxCount++;
                  continue;
               }
            }

            long combinedDist = spawnProtRadius + vt.radius();
            if (distToSpawnSq < (double)combinedDist * combinedDist) {
               filteredBySpawnDist++;
            } else if (vt.biomeTags().isEmpty()) {
               compatible.add(vt);
            } else {
               byRadius.computeIfAbsent(vt.radius(), k -> new ArrayList<>()).add(vt);
            }
         }
      }

      int filteredByBiomeValidity = 0;

      for (Entry<Integer, List<VillageType>> entry : byRadius.entrySet()) {
         int radius = entry.getKey();
         List<VillageType> types = entry.getValue();
         int totalCount = 0;
         List<Holder<Biome>> samples = new ArrayList<>();

         for (int gx = -radius; gx <= radius; gx += 16) {
            for (int gz = -radius; gz <= radius; gz += 16) {
               totalCount++;
               samples.add(level.getBiome(surfacePos.offset(gx, 0, gz)));
            }
         }

         for (VillageType vt : types) {
            int validCount = 0;

            for (Holder<Biome> sample : samples) {
               if (matchesAnyTag(sample, vt.biomeTags())) {
                  validCount++;
               }
            }

            float validPerc = (float)validCount / totalCount;
            if (validPerc < vt.minimumBiomeValidity()) {
               filteredByBiomeValidity++;
               if (log && validCount > 0) {
                  LOGGER.info(
                     "[Millenaire spawn]   {} rejected: biome validity {}/{} = {}% < {}%",
                     new Object[]{vt.id(), validCount, totalCount, Math.round(validPerc * 100.0F), Math.round(vt.minimumBiomeValidity() * 100.0F)}
                  );
               }
            } else {
               compatible.add(vt);
            }
         }
      }

      if (compatible.isEmpty()) {
         if (log) {
            int dist = (int)Math.sqrt(distToSpawnSq);
            if (biomeCandidates.isEmpty()) {
               LOGGER.info(
                  "[Millenaire spawn] {} at {} rejected: no {} type registered for biome {}", new Object[]{label, surfacePos.toShortString(), label, biomeId}
               );
            } else {
               LOGGER.info(
                  "[Millenaire spawn] {} at {} rejected: {} biome-compatible {} type(s) found but all filtered out (spawnDist={}/{}: {}, hamlets: {}, maxCount: {}, minDist: {}, biomeValidity: {})",
                  new Object[]{
                     label,
                     surfacePos.toShortString(),
                     biomeCandidates.size(),
                     label,
                     dist,
                     spawnProtRadius,
                     filteredBySpawnDist,
                     filteredByHamlets,
                     filteredByMaxCount,
                     filteredByMinDist,
                     filteredByBiomeValidity
                  }
               );
            }
         }

         return VillageSpawnQueue.SpawnResult.FAILED;
      } else {
         int totalWeight = 0;

         for (VillageType vt : compatible) {
            totalWeight += vt.weight();
         }

         if (totalWeight <= 0) {
            return VillageSpawnQueue.SpawnResult.FAILED;
         }

         int roll = ThreadLocalRandom.current().nextInt(totalWeight);
         VillageType chosen = compatible.getLast();

         for (VillageType vt : compatible) {
            roll -= vt.weight();
            if (roll < 0) {
               chosen = vt;
               break;
            }
         }

         if (log) {
            LOGGER.info(
               "[Millenaire spawn] {} at {} trying type '{}' (radius={}, biome={})",
               new Object[]{label, surfacePos.toShortString(), chosen.id(), chosen.radius(), biomeId}
            );
         }

         int r = chosen.radius();
         if (!level.hasChunksAt(new BlockPos(surfacePos.getX() - r, 0, surfacePos.getZ() - r), new BlockPos(surfacePos.getX() + r, 0, surfacePos.getZ() + r))) {
            if (log) {
               LOGGER.info("[Millenaire spawn] {} at {} deferred: chunks not loaded (radius={})", new Object[]{label, surfacePos.toShortString(), r});
            }

            if (!loneBuilding) {
               this.candidates.add(new BlockPos(surfacePos.getX(), 0, surfacePos.getZ()));
            }

            return VillageSpawnQueue.SpawnResult.DEFERRED;
         } else if (StructureAvoidance.hasConflict(level, surfacePos, chosen.radius())) {
            if (log) {
               LOGGER.info("[Millenaire spawn] {} at {} rejected: vanilla structure conflict", label, surfacePos.toShortString());
            }

            return VillageSpawnQueue.SpawnResult.FAILED;
         } else if (!SiteValidator.validate(level, surfacePos, chosen.radius(), loneBuilding)) {
            if (log) {
               LOGGER.info("[Millenaire spawn] {} at {} rejected: terrain validation failed", label, surfacePos.toShortString());
            }

            return VillageSpawnQueue.SpawnResult.FAILED;
         } else {
            double distToSpawn = Math.sqrt(distToSpawnSq);
            int completion = computeCompletionByDistance(distToSpawn);
            LOGGER.info(
               "[Millenaire] Natural spawn ({}) : {} at {} (completion: {}%, queue: {}, lbQueue: {})",
               new Object[]{label, chosen.id(), surfacePos.toShortString(), completion, this.candidates.size(), this.loneBuildingQueue.size()}
            );
            Component failure = VillageSpawner.spawnVillage(level, surfacePos, chosen, completion);
            if (log && failure != null) {
               LOGGER.info("[Millenaire spawn] {} at {} rejected: {}", new Object[]{label, surfacePos.toShortString(), failure.getString()});
            }

            if (failure == null && loneBuilding) {
               savedData.registerLoneBuilding(surfacePos, chosen.id(), chosen.culture().getPath(), null);
            }

            return failure == null ? VillageSpawnQueue.SpawnResult.SPAWNED : VillageSpawnQueue.SpawnResult.FAILED;
         }
      }
   }

   static int computeMaxCompletion(double distanceToSpawn, int minDist, int maxDist, int maxPct) {
      if (maxPct <= 0 || maxDist <= 0) {
         return 0;
      }

      if (distanceToSpawn <= minDist) {
         return 0;
      }

      float completionRatio;
      if (distanceToSpawn > maxDist) {
         completionRatio = maxPct / 100.0F;
      } else {
         completionRatio = (float)(maxPct * ((distanceToSpawn - minDist) / maxDist) / 100.0);
      }

      return Math.round(completionRatio * 100.0F);
   }

   static int computeCompletionByDistance(double distanceToSpawn) {
      int maxCompletion = computeMaxCompletion(
         distanceToSpawn,
         MillenaireServerConfig.SERVER.completionMinDistance.getAsInt(),
         MillenaireServerConfig.SERVER.completionMaxDistance.getAsInt(),
         MillenaireServerConfig.SERVER.completionMaxPercentage.getAsInt()
      );
      return maxCompletion <= 0 ? 0 : ThreadLocalRandom.current().nextInt(maxCompletion + 1);
   }

   public Map<String, Object> getStats() {
      Map<String, Object> stats = new LinkedHashMap<>();
      stats.put("mainQueueSize", this.candidates.size());
      stats.put("lbQueueSize", this.loneBuildingQueue.size());
      stats.put("resolvedChunks", this.resolvedChunks.size());
      return stats;
   }

   private static boolean matchesAnyTag(Holder<Biome> biome, List<TagKey<Biome>> tags) {
      for (TagKey<Biome> tag : tags) {
         if (biome.is(tag)) {
            return true;
         }
      }

      return false;
   }

   record LBCandidate(BlockPos pos, double distToSpawnSq, long enqueuedTick, long retryAfterTick) {
   }

   enum SpawnResult {
      SPAWNED,
      FAILED,
      DEFERRED;
   }
}
