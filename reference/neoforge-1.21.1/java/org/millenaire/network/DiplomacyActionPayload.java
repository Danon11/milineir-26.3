package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.village.DiplomacyHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public record DiplomacyActionPayload(String villageId, String targetVillageId, boolean isPraise) implements CustomPacketPayload {
   public static final Type<DiplomacyActionPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "diplomacy_action"));
   public static final StreamCodec<ByteBuf, DiplomacyActionPayload> STREAM_CODEC = StreamCodec.of(
      DiplomacyActionPayload::encode, DiplomacyActionPayload::decode
   );
   private static final int MAX_ID_LENGTH = 128;

   private static void encode(ByteBuf buf, DiplomacyActionPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.targetVillageId);
      ByteBufCodecs.BOOL.encode(buf, payload.isPraise);
   }

   private static DiplomacyActionPayload decode(ByteBuf buf) {
      String villageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageId.length() > 128) {
         villageId = villageId.substring(0, 128);
      }

      String targetVillageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (targetVillageId.length() > 128) {
         targetVillageId = targetVillageId.substring(0, 128);
      }

      boolean isPraise = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      return new DiplomacyActionPayload(villageId, targetVillageId, isPraise);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(DiplomacyActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel) {
               UUID targetUuid;
               UUID actingUuid;
               try {
                  actingUuid = UUID.fromString(payload.villageId);
                  targetUuid = UUID.fromString(payload.targetVillageId);
               } catch (IllegalArgumentException e) {
                  return;
               }

               VillageManager vm = VillageSavedData.get(serverLevel).getVillageManager();
               VillageId actingId = new VillageId(actingUuid);
               VillageId targetId = new VillageId(targetUuid);
               Village village = vm.getVillage(actingId);
               if (village != null) {
                  if (!actingId.equals(targetId)) {
                     if (village.getRelations().containsKey(targetId)) {
                        DiplomacyHelper.performDiplomacy(serverLevel, player, village, targetId, payload.isPraise);
                     }
                  }
               }
            }
         }
      });
   }
}
