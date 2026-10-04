package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record ImportTableActionPayload(BlockPos blockPos, @Nullable ImportTableActionPayload.Action action, CompoundTag actionData)
   implements CustomPacketPayload {
   public static final Type<ImportTableActionPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "import_table_action"));
   public static final StreamCodec<ByteBuf, ImportTableActionPayload> STREAM_CODEC = StreamCodec.of(
      ImportTableActionPayload::encode, ImportTableActionPayload::decode
   );

   private static void encode(ByteBuf buf, ImportTableActionPayload p) {
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getZ());
      ByteBufCodecs.VAR_INT.encode(buf, p.action.ordinal());
      ByteBufCodecs.COMPOUND_TAG.encode(buf, p.actionData);
   }

   private static ImportTableActionPayload decode(ByteBuf buf) {
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int actionOrdinal = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      CompoundTag data = (CompoundTag)ByteBufCodecs.COMPOUND_TAG.decode(buf);
      ImportTableActionPayload.Action action = ImportTableActionPayload.Action.fromOrdinal(actionOrdinal);
      if (action == null) {
         throw new DecoderException("ImportTableActionPayload: invalid action ordinal " + actionOrdinal);
      } else {
         return new ImportTableActionPayload(new BlockPos(x, y, z), action, data);
      }
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public enum Action {
      CREATE_NEW,
      IMPORT_LEVEL,
      IMPORT_ALL,
      IMPORT_LEVEL_EXPORT,
      IMPORT_ALL_EXPORT,
      REIMPORT,
      REIMPORT_ALL,
      EXPORT,
      EXPORT_NEW_LEVEL,
      UPDATE_SETTINGS,
      SHOW_COSTS;

      private static final ImportTableActionPayload.Action[] VALUES = values();

      public static ImportTableActionPayload.Action fromOrdinal(int ordinal) {
         return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
      }
   }
}
