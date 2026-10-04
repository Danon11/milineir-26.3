package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import org.millenaire.FormatUtils;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.slf4j.Logger;

public final class VillageIntegrityChecker {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MISSING_THRESHOLD = 3;

   private VillageIntegrityChecker() {
   }

   public static void checkIntegrity(ServerLevel level, Village village) {
      List<UUID> toRespawn = new ArrayList<>();

      for (Entry<UUID, VillagerRecord> entry : village.getVillagerRecords().entrySet()) {
         UUID uuid = entry.getKey();
         VillagerRecord record = entry.getValue();
         if (record.isKilled()) {
            if (level.getGameTime() - record.getLastRespawnTick() >= 6000L) {
               toRespawn.add(uuid);
            }
         } else {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof MillVillager) {
               village.removeMissingCount(uuid);
            } else {
               BlockPos checkPos = village.getCenter();
               BuildingId homeId = record.getHomeBuilding();
               if (homeId != null) {
                  BuildingInstance homeBuilding = village.getBuilding(homeId);
                  if (homeBuilding != null) {
                     checkPos = homeBuilding.getOrigin();
                  }
               } else {
                  BuildingInstance townhall = village.getTownhall();
                  if (townhall != null) {
                     checkPos = townhall.getOrigin();
                  }
               }

               ChunkPos chunkPos = new ChunkPos(checkPos);
               if (level.hasChunk(chunkPos.x, chunkPos.z)) {
                  int count = village.getMissingCount(uuid) + 1;
                  village.putMissingCount(uuid, count);
                  if (count >= 3) {
                     toRespawn.add(uuid);
                  } else {
                     LOGGER.debug("[Millénaire] Villageois {} manquant ({}/{})", new Object[]{FormatUtils.shortUuid(uuid), count, 3});
                  }
               }
            }
         }
      }

      for (UUID uuid : toRespawn) {
         respawnVillager(level, village, uuid);
      }
   }

   private static void respawnVillager(ServerLevel level, Village village, UUID oldUuid) {
      VillagerRecord record = village.getVillagerRecords().get(oldUuid);
      if (record != null) {
         ResourceLocation typeId = record.getVillagerTypeId();
         BuildingId savedHome = record.getHomeBuilding();
         BlockPos spawnPos = null;
         if (savedHome != null) {
            BuildingInstance home = village.getBuilding(savedHome);
            if (home != null) {
               spawnPos = home.getPathStartPos();
            }
         }

         if (spawnPos == null) {
            BuildingInstance th = village.getTownhall();
            if (th != null) {
               spawnPos = th.getPathStartPos();
            }
         }

         if (spawnPos == null) {
            BlockPos center = village.getCenter();
            int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
            spawnPos = new BlockPos(center.getX(), surfaceY, center.getZ());
         }

         BuildingId homeBuildingId;
         if (savedHome != null && village.getBuilding(savedHome) != null) {
            homeBuildingId = savedHome;
         } else {
            BuildingInstance townhall = village.getTownhall();
            homeBuildingId = townhall != null ? townhall.getId() : null;
         }

         BuildingId oldHome = record.getHomeBuilding();
         if (oldHome != null) {
            BuildingInstance home = village.getBuilding(oldHome);
            if (home != null && home.hasBedManager()) {
               home.getBedManager().releaseBedByVillager(oldUuid);
            }
         }

         int savedMissingCount = village.getMissingCount(oldUuid);
         village.removeVillagerRecord(oldUuid);
         village.removeMissingCount(oldUuid);
         MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, typeId, spawnPos, homeBuildingId, record);
         if (villager == null) {
            record.setUuid(oldUuid);
            village.addVillager(record);
            village.putMissingCount(oldUuid, savedMissingCount);
         } else {
            UUID newUuid = villager.getUUID();
            record.setLastRespawnTick(level.getGameTime());
            record.setHomeBuilding(homeBuildingId);
            village.markDirty();
            village.recordEvent(
               level, "Villager respawned: " + typeId.getPath() + " [" + oldUuid.toString().substring(0, 8) + " → " + newUuid.toString().substring(0, 8) + "]"
            );
            LOGGER.info(
               "[Millenaire] Villager respawned: type={}, old UUID={}, new UUID={}, pos={}, {}, {}",
               new Object[]{
                  typeId, oldUuid.toString().substring(0, 8), newUuid.toString().substring(0, 8), spawnPos.getX(), spawnPos.getY() + 1, spawnPos.getZ()
               }
            );
         }
      }
   }

   public static void cleanupOrphanedEntities(ServerLevel level, Village village) {
      Set<ChunkPos> loadedChunks = village.getLoadedChunks();
      if (!loadedChunks.isEmpty()) {
         int minX = Integer.MAX_VALUE;
         int minZ = Integer.MAX_VALUE;
         int maxX = Integer.MIN_VALUE;
         int maxZ = Integer.MIN_VALUE;

         for (ChunkPos cp : loadedChunks) {
            if (cp.getMinBlockX() < minX) {
               minX = cp.getMinBlockX();
            }

            if (cp.getMinBlockZ() < minZ) {
               minZ = cp.getMinBlockZ();
            }

            if (cp.getMaxBlockX() > maxX) {
               maxX = cp.getMaxBlockX();
            }

            if (cp.getMaxBlockZ() > maxZ) {
               maxZ = cp.getMaxBlockZ();
            }
         }

         AABB scanArea = new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
         int discarded = 0;

         for (MillVillager entity : level.getEntitiesOfClass(MillVillager.class, scanArea)) {
            if (entity.getVillageId() != null && entity.getVillageId().equals(village.getId()) && village.getVillagerRecord(entity.getUUID()) == null) {
               LOGGER.warn(
                  "[Millenaire] Discarding orphaned villager {} ({}) in village {}",
                  new Object[]{FormatUtils.shortUuid(entity.getUUID()), entity.getVillagerTypeId(), village.getVillageName()}
               );
               entity.discard();
               discarded++;
            }
         }

         if (discarded > 0) {
            LOGGER.info("[Millenaire] Cleaned up {} orphaned entities in village {}", discarded, village.getVillageName());
         }
      }
   }
}
