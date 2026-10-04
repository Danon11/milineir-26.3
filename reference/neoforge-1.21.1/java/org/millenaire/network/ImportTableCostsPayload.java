package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record ImportTableCostsPayload(BlockPos blockPos, String buildingId, String variant, int level, List<ImportTableCostsPayload.Entry> costs)
   implements CustomPacketPayload {
   public static final Type<ImportTableCostsPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "import_table_costs"));
   public static final StreamCodec<ByteBuf, ImportTableCostsPayload> STREAM_CODEC = StreamCodec.of(
      ImportTableCostsPayload::encode, ImportTableCostsPayload::decode
   );
   private static final int MAX_COST_ENTRIES = 4096;

   private static void encode(ByteBuf buf, ImportTableCostsPayload p) {
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, p.buildingId);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.variant);
      ByteBufCodecs.VAR_INT.encode(buf, p.level);
      ByteBufCodecs.VAR_INT.encode(buf, p.costs.size());

      for (ImportTableCostsPayload.Entry e : p.costs) {
         ByteBufCodecs.STRING_UTF8.encode(buf, e.itemId());
         ByteBufCodecs.VAR_INT.encode(buf, e.quantity());
      }
   }

   private static ImportTableCostsPayload decode(ByteBuf buf) {
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String buildingId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String variant = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int level = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int n = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      if (n >= 0 && n <= 4096) {
         List<ImportTableCostsPayload.Entry> costs = new ArrayList<>(n);

         for (int i = 0; i < n; i++) {
            String itemId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
            int qty = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
            costs.add(new ImportTableCostsPayload.Entry(itemId, qty));
         }

         return new ImportTableCostsPayload(new BlockPos(x, y, z), buildingId, variant, level, costs);
      } else {
         throw new DecoderException("ImportTableCostsPayload: cost-list size " + n + " out of bounds (max 4096)");
      }
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record Entry(String itemId, int quantity) {
   }
}
