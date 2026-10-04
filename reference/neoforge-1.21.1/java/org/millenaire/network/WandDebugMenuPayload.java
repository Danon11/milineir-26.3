package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record WandDebugMenuPayload(int targetEntityId, BlockPos targetPos, String headerTitle, List<WandDebugMenuPayload.ActionEntry> actions)
   implements CustomPacketPayload {
   private static final int MAX_ACTIONS = 32;
   private static final int MAX_STRING_LENGTH = 256;
   public static final Type<WandDebugMenuPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "wand_debug_menu"));
   public static final StreamCodec<ByteBuf, WandDebugMenuPayload> STREAM_CODEC = StreamCodec.of(WandDebugMenuPayload::encode, WandDebugMenuPayload::decode);

   private static void encode(ByteBuf buf, WandDebugMenuPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetEntityId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.headerTitle);
      ByteBufCodecs.VAR_INT.encode(buf, payload.actions.size());

      for (WandDebugMenuPayload.ActionEntry entry : payload.actions) {
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.id);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.translationKey);
      }
   }

   private static WandDebugMenuPayload decode(ByteBuf buf) {
      int targetEntityId = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      BlockPos targetPos = new BlockPos(x, y, z);
      String headerTitle = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (headerTitle.length() > 256) {
         headerTitle = headerTitle.substring(0, 256);
      }

      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, 32);
      List<WandDebugMenuPayload.ActionEntry> actions = new ArrayList<>(count);

      for (int i = 0; i < rawCount; i++) {
         String id = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String translationKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         if (i < count) {
            if (id.length() > 256) {
               id = id.substring(0, 256);
            }

            if (translationKey.length() > 256) {
               translationKey = translationKey.substring(0, 256);
            }

            actions.add(new WandDebugMenuPayload.ActionEntry(id, translationKey));
         }
      }

      return new WandDebugMenuPayload(targetEntityId, targetPos, headerTitle, Collections.unmodifiableList(actions));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record ActionEntry(String id, String translationKey) {
   }
}
