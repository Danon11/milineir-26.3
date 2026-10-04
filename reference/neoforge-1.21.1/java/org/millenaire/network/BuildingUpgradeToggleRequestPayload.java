package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.village.ControlledProjectsService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record BuildingUpgradeToggleRequestPayload(String villageUuid, String buildingId, boolean allow) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 256;
   public static final Type<BuildingUpgradeToggleRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "building_upgrade_toggle_request")
   );
   public static final StreamCodec<ByteBuf, BuildingUpgradeToggleRequestPayload> STREAM_CODEC = StreamCodec.of(
      BuildingUpgradeToggleRequestPayload::encode, BuildingUpgradeToggleRequestPayload::decode
   );

   private static void encode(ByteBuf buf, BuildingUpgradeToggleRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.buildingId);
      buf.writeBoolean(payload.allow);
   }

   private static BuildingUpgradeToggleRequestPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      String buildingId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (buildingId.length() > 256) {
         buildingId = buildingId.substring(0, 256);
      }

      return new BuildingUpgradeToggleRequestPayload(villageUuid, buildingId, buf.readBoolean());
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(BuildingUpgradeToggleRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel) {
               ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
               if (overworld != null) {
                  UUID villageUuid;
                  UUID buildingUuid;
                  try {
                     villageUuid = UUID.fromString(payload.villageUuid);
                     buildingUuid = UUID.fromString(payload.buildingId);
                  } catch (IllegalArgumentException e) {
                     return;
                  }

                  VillageSavedData savedData = VillageSavedData.get(overworld);
                  Village village = savedData.getVillageManager().getVillage(new VillageId(villageUuid));
                  if (village == null) {
                     LOGGER.warn("[Millenaire] BuildingUpgradeToggle: village {} not found", payload.villageUuid);
                  } else if (!village.isControlledBy(player.getUUID())) {
                     LOGGER.warn("[Millenaire] BuildingUpgradeToggle: player {} is not owner of village {}", player.getName().getString(), payload.villageUuid);
                  } else {
                     BuildingInstance building = village.findBuildingById(new BuildingId(buildingUuid));
                     if (building == null) {
                        LOGGER.warn("[Millenaire] BuildingUpgradeToggle: building {} not found in village {}", payload.buildingId, payload.villageUuid);
                     } else {
                        building.setUpgradesAllowed(payload.allow);
                        savedData.setDirty();
                        PacketDistributor.sendToPlayer(player, ControlledProjectsService.buildPayload(village), new CustomPacketPayload[0]);
                     }
                  }
               }
            }
         }
      });
   }
}
