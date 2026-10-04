package org.millenaire.item;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.MapData;
import org.millenaire.network.PanelContentPayload;
import org.millenaire.network.VillageBookPayload;
import org.millenaire.village.Village;
import org.millenaire.village.VillageBookService;
import org.millenaire.village.VillageId;
import org.millenaire.village.panel.PanelContent;
import org.millenaire.village.panel.PanelContentGenerator;
import org.slf4j.Logger;

public class VillageBookItem extends Item {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String TAG_VILLAGE_ID = "village_id";
   private static final String TAG_VILLAGE_NAME = "village_name";

   public VillageBookItem(Properties properties) {
      super(properties);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide()) {
         return InteractionResultHolder.success(stack);
      }

      if (player instanceof ServerPlayer serverPlayer) {
         UUID villageUuid = getVillageId(stack);
         if (villageUuid == null) {
            serverPlayer.sendSystemMessage(Component.translatable("millenaire.scroll.error.village_not_found"));
            return InteractionResultHolder.fail(stack);
         }

         ServerLevel serverLevel = (ServerLevel)level;
         Village village = Village.resolve(serverLevel, new VillageId(villageUuid));
         if (village == null) {
            serverPlayer.sendSystemMessage(Component.translatable("millenaire.scroll.error.village_not_found"));
            return InteractionResultHolder.fail(stack);
         }

         try {
            boolean degraded = VillageBookService.isDegraded(village, serverLevel);
            ServerLevel effectiveLevel = degraded ? null : serverLevel;
            List<PanelContent> sections = VillageBookService.generateBookContent(village, effectiveLevel, serverPlayer);
            List<MapData.MapBuilding> mapBuildings = List.of();
            List<MapData.MapVillager> mapVillagers = List.of();
            int mapPlayerX = 0;
            int mapPlayerZ = 0;
            int mapCenterX = 0;
            int mapCenterZ = 0;
            MapData.MapTerrain mapTerrain = MapData.MapTerrain.EMPTY;
            List<MapData.MapPath> mapPaths = List.of();
            boolean hasMapData = false;
            if (!degraded) {
               PanelContent dummyContent = sections.get(0);
               PanelContentPayload mapPayload = PanelContentGenerator.createMapPayload(dummyContent, village, serverLevel, serverPlayer);
               mapBuildings = mapPayload.mapBuildings();
               mapVillagers = mapPayload.mapVillagers();
               mapPlayerX = mapPayload.mapPlayerX();
               mapPlayerZ = mapPayload.mapPlayerZ();
               mapCenterX = mapPayload.mapCenterX();
               mapCenterZ = mapPayload.mapCenterZ();
               mapTerrain = mapPayload.mapTerrain();
               mapPaths = mapPayload.mapPaths();
               hasMapData = mapPayload.hasMapData();
            }

            VillageBookPayload payload = new VillageBookPayload(
               village.getVillageName(),
               village.getCultureId().toString(),
               sections,
               mapBuildings,
               mapVillagers,
               mapPlayerX,
               mapPlayerZ,
               mapCenterX,
               mapCenterZ,
               mapTerrain,
               mapPaths,
               hasMapData,
               degraded
            );
            PacketDistributor.sendToPlayer(serverPlayer, payload, new CustomPacketPayload[0]);
            LOGGER.debug("Scroll used by {} for village {}", player.getName().getString(), village.getVillageName());
         } catch (Exception e) {
            LOGGER.error("Error generating scroll for village {}", village.getVillageName(), e);
            serverPlayer.sendSystemMessage(Component.translatable("millenaire.scroll.error.generation_failed"));
         }

         return InteractionResultHolder.success(stack);
      } else {
         return InteractionResultHolder.fail(stack);
      }
   }

   public Component getName(ItemStack stack) {
      String villageName = getVillageName(stack);
      return (Component)(villageName != null ? Component.translatable("item.millenaire.village_scroll.named").append(villageName) : super.getName(stack));
   }

   @Nullable
   public static UUID getVillageId(ItemStack stack) {
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return null;
      }

      CompoundTag tag = customData.copyTag();
      if (!tag.contains("village_id")) {
         return null;
      }

      try {
         return UUID.fromString(tag.getString("village_id"));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   @Nullable
   public static String getVillageName(ItemStack stack) {
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return null;
      }

      CompoundTag tag = customData.copyTag();
      if (!tag.contains("village_name")) {
         return null;
      }

      String name = tag.getString("village_name");
      return name.isEmpty() ? null : name;
   }
}
