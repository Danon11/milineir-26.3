package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record QuestResultTextPayload(long questUniqueId, String resultText, boolean isSuccess) implements CustomPacketPayload {
   public static final Type<QuestResultTextPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "quest_result"));
   public static final StreamCodec<ByteBuf, QuestResultTextPayload> STREAM_CODEC = StreamCodec.of(
      QuestResultTextPayload::encode, QuestResultTextPayload::decode
   );

   private static void encode(ByteBuf buf, QuestResultTextPayload payload) {
      ByteBufCodecs.VAR_LONG.encode(buf, payload.questUniqueId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.resultText);
      ByteBufCodecs.BOOL.encode(buf, payload.isSuccess);
   }

   private static QuestResultTextPayload decode(ByteBuf buf) {
      return new QuestResultTextPayload(
         (Long)ByteBufCodecs.VAR_LONG.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf), (Boolean)ByteBufCodecs.BOOL.decode(buf)
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
