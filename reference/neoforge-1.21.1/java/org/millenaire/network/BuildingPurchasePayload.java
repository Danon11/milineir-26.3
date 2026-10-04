package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record BuildingPurchasePayload(String villageId, String planSetId) implements CustomPacketPayload {
   public static final Type<BuildingPurchasePayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "building_purchase"));
   public static final StreamCodec<ByteBuf, BuildingPurchasePayload> STREAM_CODEC = StreamCodec.of(
      BuildingPurchasePayload::encode, BuildingPurchasePayload::decode
   );
   private static final int MAX_ID_LENGTH = 128;

   private static void encode(ByteBuf buf, BuildingPurchasePayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.planSetId);
   }

   private static BuildingPurchasePayload decode(ByteBuf buf) {
      String villageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageId.length() > 128) {
         villageId = villageId.substring(0, 128);
      }

      String planSetId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (planSetId.length() > 128) {
         planSetId = planSetId.substring(0, 128);
      }

      return new BuildingPurchasePayload(villageId, planSetId);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
