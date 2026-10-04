package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record InfoPanelContentPayload(List<InfoPanelContentPayload.CultureEntry> cultures) implements CustomPacketPayload {
   private static final int MAX_CULTURES = 32;
   public static final Type<InfoPanelContentPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "info_panel_content"));
   public static final StreamCodec<ByteBuf, InfoPanelContentPayload> STREAM_CODEC = StreamCodec.of(
      InfoPanelContentPayload::encode, InfoPanelContentPayload::decode
   );

   private static void encode(ByteBuf buf, InfoPanelContentPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.cultures.size());

      for (InfoPanelContentPayload.CultureEntry entry : payload.cultures) {
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.cultureNameKey);
         ByteBufCodecs.VAR_INT.encode(buf, entry.reputation);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.reputationLabel);
         ByteBufCodecs.VAR_INT.encode(buf, entry.languageScore);
      }
   }

   private static InfoPanelContentPayload decode(ByteBuf buf) {
      int count = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      count = Math.min(count, 32);
      List<InfoPanelContentPayload.CultureEntry> entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         String cultureNameKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         int reputation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         String reputationLabel = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         int languageScore = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         entries.add(new InfoPanelContentPayload.CultureEntry(cultureNameKey, reputation, reputationLabel, languageScore));
      }

      return new InfoPanelContentPayload(entries);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record CultureEntry(String cultureNameKey, int reputation, String reputationLabel, int languageScore) {
   }
}
