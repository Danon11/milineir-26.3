package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record TradeStockUpdatePayload(int containerId, List<Integer> stocks, boolean donationMode) implements CustomPacketPayload {
   public static final Type<TradeStockUpdatePayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "trade_stock_update"));
   public static final StreamCodec<ByteBuf, TradeStockUpdatePayload> STREAM_CODEC = StreamCodec.of(
      TradeStockUpdatePayload::encode, TradeStockUpdatePayload::decode
   );

   private static void encode(ByteBuf buf, TradeStockUpdatePayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.containerId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.stocks.size());

      for (int stock : payload.stocks) {
         ByteBufCodecs.VAR_INT.encode(buf, stock);
      }

      ByteBufCodecs.BOOL.encode(buf, payload.donationMode);
   }

   private static TradeStockUpdatePayload decode(ByteBuf buf) {
      int containerId = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int maxCount = Math.min(count, 512);
      List<Integer> stocks = new ArrayList<>(maxCount);

      for (int i = 0; i < count; i++) {
         int stock = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         if (i < maxCount) {
            stocks.add(stock);
         }
      }

      boolean donationMode = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      return new TradeStockUpdatePayload(containerId, Collections.unmodifiableList(stocks), donationMode);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
