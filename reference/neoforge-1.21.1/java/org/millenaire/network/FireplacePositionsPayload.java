package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record FireplacePositionsPayload(UUID villageId, BlockPos villageCenter, List<BlockPos> positions) implements CustomPacketPayload {
   private static final int MAX_POSITIONS = 512;
   public static final Type<FireplacePositionsPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "fireplace_positions"));
   public static final StreamCodec<ByteBuf, FireplacePositionsPayload> STREAM_CODEC = StreamCodec.of(
      FireplacePositionsPayload::encode, FireplacePositionsPayload::decode
   );

   private static void encode(ByteBuf buf, FireplacePositionsPayload payload) {
      buf.writeLong(payload.villageId.getMostSignificantBits());
      buf.writeLong(payload.villageId.getLeastSignificantBits());
      ByteBufCodecs.VAR_INT.encode(buf, payload.villageCenter.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.villageCenter.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.villageCenter.getZ());
      int toWrite = Math.min(payload.positions.size(), 512);
      ByteBufCodecs.VAR_INT.encode(buf, toWrite);

      for (int i = 0; i < toWrite; i++) {
         BlockPos pos = payload.positions.get(i);
         ByteBufCodecs.VAR_INT.encode(buf, pos.getX());
         ByteBufCodecs.VAR_INT.encode(buf, pos.getY());
         ByteBufCodecs.VAR_INT.encode(buf, pos.getZ());
      }
   }

   private static FireplacePositionsPayload decode(ByteBuf buf) {
      UUID villageId = new UUID(buf.readLong(), buf.readLong());
      int cx = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int cy = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int cz = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      BlockPos center = new BlockPos(cx, cy, cz);
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int kept = Math.min(rawCount, 512);
      List<BlockPos> positions = new ArrayList<>(kept);

      for (int i = 0; i < rawCount; i++) {
         int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         if (i < kept) {
            positions.add(new BlockPos(x, y, z));
         }
      }

      return new FireplacePositionsPayload(villageId, center, Collections.unmodifiableList(positions));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
