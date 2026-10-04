package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record QuestInstanceDestroyPayload(long uniqueId) implements CustomPacketPayload {
   public static final Type<QuestInstanceDestroyPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "quest_destroy"));
   public static final StreamCodec<ByteBuf, QuestInstanceDestroyPayload> STREAM_CODEC = StreamCodec.of(
      QuestInstanceDestroyPayload::encode, QuestInstanceDestroyPayload::decode
   );

   private static void encode(ByteBuf buf, QuestInstanceDestroyPayload payload) {
      ByteBufCodecs.VAR_LONG.encode(buf, payload.uniqueId);
   }

   private static QuestInstanceDestroyPayload decode(ByteBuf buf) {
      return new QuestInstanceDestroyPayload((Long)ByteBufCodecs.VAR_LONG.decode(buf));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
