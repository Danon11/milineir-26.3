package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.PlacementSignHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.PlacedLocation;
import org.slf4j.Logger;

public record BuildingProjectRequestPayload(String villageUuid, String planSetId) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 256;
   public static final Type<BuildingProjectRequestPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "building_project_request"));
   public static final StreamCodec<ByteBuf, BuildingProjectRequestPayload> STREAM_CODEC = StreamCodec.of(
      BuildingProjectRequestPayload::encode, BuildingProjectRequestPayload::decode
   );

   private static void encode(ByteBuf buf, BuildingProjectRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.planSetId);
   }

   private static BuildingProjectRequestPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      String planSetId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (planSetId.length() > 256) {
         planSetId = planSetId.substring(0, 256);
      }

      return new BuildingProjectRequestPayload(villageUuid, planSetId);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(BuildingProjectRequestPayload payload, IPayloadContext context) {
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
                     if (village == null) {
                        LOGGER.warn(
                           "[Millenaire] BuildingProjectRequest: village {} not found for player {}", payload.villageUuid, player.getName().getString()
                        );
                     } else if (!village.isControlledBy(player.getUUID())) {
                        LOGGER.warn(
                           "[Millenaire] BuildingProjectRequest: player {} is not owner of village {}", player.getName().getString(), payload.villageUuid
                        );
                     } else {
                        ResourceLocation planSetLoc = ResourceLocation.tryParse(payload.planSetId);
                        if (planSetLoc != null) {
                           BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetLoc);
                           if (planSet == null) {
                              LOGGER.warn("[Millenaire] BuildingProjectRequest: plan set {} not found", payload.planSetId);
                           } else {
                              String variant = planSet.pickRandomVariant(ThreadLocalRandom.current());
                              BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, 0);
                              BuildingPlan plan = levelDef != null ? ModCultures.getBuildingPlan(levelDef.planId()) : null;
                              if (plan == null) {
                                 LOGGER.warn("[Millenaire] BuildingProjectRequest: plan {} has no level 0 / plan resource", payload.planSetId);
                                 player.sendSystemMessage(
                                    Component.translatable("gui.millenaire.building_project.no_location", new Object[]{planSet.nativeName()})
                                 );
                              } else {
                                 VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
                                 VillageType.LayoutSlot slot = null;
                                 if (vt != null) {
                                    for (VillageType.LayoutSlot s : vt.layout()) {
                                       if (planSetLoc.equals(s.plan())) {
                                          slot = s;
                                          break;
                                       }
                                    }
                                 }

                                 PlacedLocation planned = VillageGrowthManager.findLocationForNewBuilding(overworld, village, planSet, plan, slot);
                                 if (planned == null) {
                                    player.sendSystemMessage(
                                       Component.translatable("gui.millenaire.building_project.no_location", new Object[]{planSet.nativeName()})
                                    );
                                 } else {
                                    Village.PendingProject previous = village.getPendingProject();
                                    if (previous != null && previous.plannedLocation() != null) {
                                       BuildingPlanSet previousSet = ModCultures.getBuildingPlanSet(previous.planSetId());
                                       if (previousSet != null) {
                                          BuildingPlanSet.LevelDef prevLevel = previousSet.getLevel(previous.variant(), previous.level());
                                          BuildingPlan prevPlan = prevLevel != null ? ModCultures.getBuildingPlan(prevLevel.planId()) : null;
                                          if (prevPlan != null) {
                                             PlacementSignHelper.removeCornerSigns(overworld, prevPlan, previous.plannedLocation());
                                          }
                                       }
                                    }

                                    village.setPendingProject(new Village.PendingProject(planSetLoc, variant, 0, false, null, planned));
                                    village.setNoProjectsLeftUntil(0L);
                                    PlacementSignHelper.placeCornerSigns(overworld, plan, planned, planSet.nativeName());
                                    player.sendSystemMessage(
                                       Component.translatable("gui.millenaire.building_project.queued", new Object[]{planSet.nativeName()})
                                    );
                                 }
                              }
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
