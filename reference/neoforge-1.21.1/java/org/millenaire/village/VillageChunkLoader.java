package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.slf4j.Logger;

public final class VillageChunkLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final TicketController CONTROLLER = new TicketController(
      ResourceLocation.fromNamespaceAndPath("millenaire", "village_chunks"), (level, ticketHelper) -> {}
   );

   private VillageChunkLoader() {
   }

   public static TicketController getController() {
      return CONTROLLER;
   }

   public static void forceVillageChunks(ServerLevel level, BlockPos anchor, Set<ChunkPos> chunks) {
      int count = 0;

      for (ChunkPos cp : chunks) {
         CONTROLLER.forceChunk(level, anchor, cp.x, cp.z, true, true);
         count++;
      }

      LOGGER.debug("[Millénaire] Force-loaded {} chunks (anchor {})", count, anchor.toShortString());
   }

   public static void releaseVillageChunks(ServerLevel level, BlockPos anchor, Set<ChunkPos> chunks) {
      for (ChunkPos cp : chunks) {
         CONTROLLER.forceChunk(level, anchor, cp.x, cp.z, false, true);
      }

      LOGGER.debug("[Millénaire] Released {} chunks (anchor {})", chunks.size(), anchor.toShortString());
   }

   public static void updateVillageChunks(ServerLevel level, Village village, Set<ChunkPos> newChunks) {
      Set<ChunkPos> oldChunks = village.getLoadedChunks();
      BlockPos anchor = village.getCenter();

      for (ChunkPos cp : oldChunks) {
         if (!newChunks.contains(cp)) {
            CONTROLLER.forceChunk(level, anchor, cp.x, cp.z, false, true);
         }
      }

      for (ChunkPos cp : newChunks) {
         if (!oldChunks.contains(cp)) {
            CONTROLLER.forceChunk(level, anchor, cp.x, cp.z, true, true);
         }
      }

      village.setLoadedChunks(newChunks);
   }
}
