package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record TradePayload(int entityId, String displayName, String roleName, String villageName, int reputation, String reputationLabel)
   implements CustomPacketPayload {
   public static final Type<TradePayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "trade"));
   public static final StreamCodec<ByteBuf, TradePayload> STREAM_CODEC = StreamCodec.of(TradePayload::encode, TradePayload::decode);

   private static void encode(ByteBuf buf, TradePayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.displayName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.roleName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.VAR_INT.encode(buf, payload.reputation);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.reputationLabel);
   }

   private static TradePayload decode(ByteBuf buf) {
      return new TradePayload(
         (Integer)ByteBufCodecs.VAR_INT.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (Integer)ByteBufCodecs.VAR_INT.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf)
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
