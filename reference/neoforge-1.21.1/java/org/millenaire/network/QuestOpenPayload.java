package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record QuestOpenPayload(
   int villagerEntityId,
   long questUniqueId,
   String villagerDisplayName,
   String villagerNativeOccupation,
   String villagerGameOccupation,
   String descriptionText,
   String conditionText,
   boolean conditionsMet,
   boolean isFirstStep,
   int currentStepIndex,
   String cultureKey,
   String villagerTypeKey
) implements CustomPacketPayload {
   public static final Type<QuestOpenPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "quest_open"));
   public static final StreamCodec<ByteBuf, QuestOpenPayload> STREAM_CODEC = StreamCodec.of(QuestOpenPayload::encode, QuestOpenPayload::decode);

   private static void encode(ByteBuf buf, QuestOpenPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.villagerEntityId);
      ByteBufCodecs.VAR_LONG.encode(buf, payload.questUniqueId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villagerDisplayName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villagerNativeOccupation);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villagerGameOccupation);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.descriptionText);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.conditionText);
      ByteBufCodecs.BOOL.encode(buf, payload.conditionsMet);
      ByteBufCodecs.BOOL.encode(buf, payload.isFirstStep);
      ByteBufCodecs.VAR_INT.encode(buf, payload.currentStepIndex);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villagerTypeKey);
   }

   private static QuestOpenPayload decode(ByteBuf buf) {
      return new QuestOpenPayload(
         (Integer)ByteBufCodecs.VAR_INT.decode(buf),
         (Long)ByteBufCodecs.VAR_LONG.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (Boolean)ByteBufCodecs.BOOL.decode(buf),
         (Boolean)ByteBufCodecs.BOOL.decode(buf),
         (Integer)ByteBufCodecs.VAR_INT.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf),
         (String)ByteBufCodecs.STRING_UTF8.decode(buf)
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
