package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record NegationWandPayload(String villageId, String villageTypeId, String villageName) implements CustomPacketPayload {
   public static final Type<NegationWandPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "negation_wand"));
   public static final StreamCodec<ByteBuf, NegationWandPayload> STREAM_CODEC = StreamCodec.of(NegationWandPayload::encode, NegationWandPayload::decode);

   private static void encode(ByteBuf buf, NegationWandPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageTypeId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
   }

   private static NegationWandPayload decode(ByteBuf buf) {
      String villageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageTypeId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      return new NegationWandPayload(villageId, villageTypeId, villageName);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
