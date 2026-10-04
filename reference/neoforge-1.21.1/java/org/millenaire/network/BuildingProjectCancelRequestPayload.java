package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.chat.Component;
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
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.ControlledProjectsService;
import org.millenaire.village.PlacementSignHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record BuildingProjectCancelRequestPayload(String villageUuid) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 256;
   public static final Type<BuildingProjectCancelRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "building_project_cancel_request")
   );
   public static final StreamCodec<ByteBuf, BuildingProjectCancelRequestPayload> STREAM_CODEC = StreamCodec.of(
      BuildingProjectCancelRequestPayload::encode, BuildingProjectCancelRequestPayload::decode
   );

   private static void encode(ByteBuf buf, BuildingProjectCancelRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
   }

   private static BuildingProjectCancelRequestPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      return new BuildingProjectCancelRequestPayload(villageUuid);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(BuildingProjectCancelRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(
         () -> {
            if (context.player() instanceof ServerPlayer player) {
               if (player.level() instanceof ServerLevel serverLevel) {
                  ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
                  if (overworld != null) {
                     UUID uuid;
                     try {
                        uuid = UUID.fromString(payload.villageUuid);
                     } catch (IllegalArgumentException e) {
                        return;
                     }

                     VillageSavedData savedData = VillageSavedData.get(overworld);
                     Village village = savedData.getVillageManager().getVillage(new VillageId(uuid));
                     if (village != null) {
                        if (!village.isControlledBy(player.getUUID())) {
                           LOGGER.warn(
                              "[Millenaire] BuildingProjectCancel: player {} is not owner of village {}", player.getName().getString(), payload.villageUuid
                           );
                        } else {
                           Village.PendingProject pending = village.getPendingProject();
                           if (pending != null) {
                              if (pending.plannedLocation() != null) {
                                 BuildingPlanSet set = ModCultures.getBuildingPlanSet(pending.planSetId());
                                 if (set != null) {
                                    BuildingPlanSet.LevelDef levelDef = set.getLevel(pending.variant(), pending.level());
                                    BuildingPlan plan = levelDef != null ? ModCultures.getBuildingPlan(levelDef.planId()) : null;
                                    if (plan != null) {
                                       PlacementSignHelper.removeCornerSigns(overworld, plan, pending.plannedLocation());
                                    }
                                 }
                              }

                              village.setPendingProject(null);
                              savedData.setDirty();
                              BuildingPlanSet set = ModCultures.getBuildingPlanSet(pending.planSetId());
                              String name = set != null ? set.nativeName() : pending.planSetId().getPath();
                              player.sendSystemMessage(Component.translatable("gui.millenaire.building_project.cancelled", new Object[]{name}));
                              PacketDistributor.sendToPlayer(player, ControlledProjectsService.buildPayload(village), new CustomPacketPayload[0]);
                           }
                        }
                     }
                  }
               }
            }
         }
      );
   }
}
