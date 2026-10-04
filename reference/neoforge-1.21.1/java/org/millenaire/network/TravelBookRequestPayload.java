package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.village.TravelBookContentBuilder;
import org.millenaire.village.TravelBookScreenState;
import org.slf4j.Logger;

public record TravelBookRequestPayload(TravelBookScreenState targetState, String cultureKey, String categoryKey, String itemKey, int navAction)
   implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_KEY_LENGTH = 128;
   public static final Type<TravelBookRequestPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "travel_book_request"));
   public static final StreamCodec<ByteBuf, TravelBookRequestPayload> STREAM_CODEC = StreamCodec.of(
      TravelBookRequestPayload::encode, TravelBookRequestPayload::decode
   );

   private static void encode(ByteBuf buf, TravelBookRequestPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetState.ordinal());
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.categoryKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.itemKey);
      ByteBufCodecs.VAR_INT.encode(buf, payload.navAction);
   }

   private static TravelBookRequestPayload decode(ByteBuf buf) {
      int stateOrdinal = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      TravelBookScreenState[] states = TravelBookScreenState.values();
      TravelBookScreenState state = stateOrdinal >= 0 && stateOrdinal < states.length ? states[stateOrdinal] : TravelBookScreenState.HOME;
      String cultureKey = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String categoryKey = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String itemKey = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      int navAction = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      if (navAction < 0 || navAction > 3) {
         navAction = 0;
      }

      return new TravelBookRequestPayload(state, cultureKey, categoryKey, itemKey, navAction);
   }

   private static String truncate(String raw) {
      return raw.length() > 128 ? raw.substring(0, 128) : raw;
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(TravelBookRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer serverPlayer) {
            TravelBookContentBuilder.handleRequest(serverPlayer, payload);
         }
      });
   }
}
