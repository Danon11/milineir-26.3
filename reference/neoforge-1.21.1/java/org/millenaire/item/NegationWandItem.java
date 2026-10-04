package org.millenaire.item;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.block.LockedChestBlockEntity;
import org.millenaire.building.BuildingInstance;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.entity.MillVillager;
import org.millenaire.network.NegationWandPayload;
import org.millenaire.village.Village;
import org.millenaire.village.VillageChunkLoader;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public class NegationWandItem extends Item {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final double SEARCH_RADIUS = 30.0;

   public NegationWandItem(Properties properties) {
      super(properties);
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }

      ServerLevel serverLevel = (ServerLevel)level;
      if (context.getPlayer() instanceof ServerPlayer player) {
         BlockPos var9 = context.getClickedPos();
         VillageSavedData savedData = VillageSavedData.get(serverLevel);
         Village village = savedData.getVillageManager().findNearestVillage(var9, 30.0);
         if (village == null) {
            player.sendSystemMessage(Component.translatable("negationwand.novillage"));
            return InteractionResult.FAIL;
         } else if (village.areChestsLocked()) {
            String name = village.getVillageName() != null ? village.getVillageName() : village.getVillageTypeId().getPath();
            player.sendSystemMessage(Component.translatable("negationwand.villagelocked", new Object[]{name}));
            return InteractionResult.SUCCESS;
         } else {
            String villageName = village.getVillageName() != null ? village.getVillageName() : "";
            PacketDistributor.sendToPlayer(
               player,
               new NegationWandPayload(village.getId().uuid().toString(), village.getVillageTypeId().getPath(), villageName),
               new CustomPacketPayload[0]
            );
            return InteractionResult.SUCCESS;
         }
      } else {
         return InteractionResult.FAIL;
      }
   }

   public static void performDeletion(ServerLevel level, VillageSavedData savedData, Village village, ServerPlayer player) {
      for (BuildingInstance building : village.getBuildings()) {
         for (BlockPos chestPos : building.getChestPositions()) {
            if (level.getBlockEntity(chestPos) instanceof LockedChestBlockEntity chest) {
               chest.setBuildingId(null);
            }
         }
      }

      int killed = 0;

      for (UUID uuid : village.getVillagerUuids()) {
         if (level.getEntity(uuid) instanceof MillVillager villager) {
            villager.discard();
            killed++;
         }
      }

      if (!village.getLoadedChunks().isEmpty()) {
         VillageChunkLoader.releaseVillageChunks(level, village.getCenter(), village.getLoadedChunks());
         village.setLoadedChunks(Set.of());
         village.setChunksForceLoaded(false);
      }

      savedData.getVillageManager().removeVillage(village.getId());

      for (Village other : savedData.getVillageManager().getAllVillages()) {
         other.removeRelation(village.getId());
         if (village.getId().equals(other.getParentVillageId())) {
            other.setParentVillageId(null);
         }
      }

      savedData.removeLoneBuilding(village.getCenter());
      savedData.setDirty();
      LOGGER.info("[Millénaire] Village {} deleted by negation wand ({} villagers removed)", village.getId().uuid().toString().substring(0, 8), killed);
      VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
      if (vType != null && !vType.loneBuilding()) {
         MillAdvancements.grant(player, MillAdvancements.SCIPIO);
      }

      String name = village.getVillageName() != null ? village.getVillageName() : village.getVillageTypeId().getPath();
      player.sendSystemMessage(Component.translatable("negationwand.destroyed", new Object[]{name}));
   }
}
