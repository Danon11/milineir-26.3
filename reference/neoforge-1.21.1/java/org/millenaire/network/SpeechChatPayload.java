package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record SpeechChatPayload(String villagerName, String speechRef, String cultureKey, int languageScore, String vanillaFallbackKey)
   implements CustomPacketPayload {
   public static final Type<SpeechChatPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "speech_chat"));
   public static final StreamCodec<ByteBuf, SpeechChatPayload> STREAM_CODEC = StreamCodec.of(SpeechChatPayload::encode, SpeechChatPayload::decode);

   private static void encode(ByteBuf buf, SpeechChatPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villagerName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.speechRef);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureKey);
      ByteBufCodecs.VAR_INT.encode(buf, payload.languageScore);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.vanillaFallbackKey);
   }

   private static SpeechChatPayload decode(ByteBuf buf) {
      String villagerName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String speechRef = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String cultureKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int languageScore = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String vanillaFallbackKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      return new SpeechChatPayload(villagerName, speechRef, cultureKey, languageScore, vanillaFallbackKey);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
