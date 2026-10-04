package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.block.ImportTableBlockEntity;

public record ImportTableSyncPayload(
   BlockPos blockPos,
   String buildingId,
   String variant,
   String cultureKey,
   String parentBuildingId,
   int length,
   int width,
   int upgradeLevel,
   int startingLevel,
   int height,
   int orientation,
   boolean clearGround,
   boolean exportSnow,
   boolean importMockBlocks,
   boolean convertToPreserveGround,
   boolean hasMainTablePos,
   int mainTableX,
   int mainTableY,
   int mainTableZ,
   boolean isMainTable,
   boolean importedFromCulture
) implements CustomPacketPayload {
   public static final Type<ImportTableSyncPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "import_table_sync"));
   public static final StreamCodec<ByteBuf, ImportTableSyncPayload> STREAM_CODEC = StreamCodec.of(
      ImportTableSyncPayload::encode, ImportTableSyncPayload::decode
   );

   public static ImportTableSyncPayload fromBlockEntity(ImportTableBlockEntity be) {
      BlockPos mainPos = be.getMainTablePos();
      return new ImportTableSyncPayload(
         be.getBlockPos(),
         be.getBuildingId(),
         be.getVariant(),
         be.getCultureKey(),
         be.getParentBuildingId(),
         be.getLength(),
         be.getWidth(),
         be.getUpgradeLevel(),
         be.getStartingLevel(),
         be.getHeight(),
         be.getOrientation(),
         be.isClearGround(),
         be.isExportSnow(),
         be.isImportMockBlocks(),
         be.isConvertToPreserveGround(),
         mainPos != null,
         mainPos != null ? mainPos.getX() : 0,
         mainPos != null ? mainPos.getY() : 0,
         mainPos != null ? mainPos.getZ() : 0,
         be.isMainTable(),
         be.isImportedFromCulture()
      );
   }

   public BlockPos mainTablePos() {
      return this.hasMainTablePos ? new BlockPos(this.mainTableX, this.mainTableY, this.mainTableZ) : null;
   }

   private static void encode(ByteBuf buf, ImportTableSyncPayload p) {
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, p.buildingId);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.variant);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.parentBuildingId);
      ByteBufCodecs.VAR_INT.encode(buf, p.length);
      ByteBufCodecs.VAR_INT.encode(buf, p.width);
      ByteBufCodecs.VAR_INT.encode(buf, p.upgradeLevel);
      ByteBufCodecs.VAR_INT.encode(buf, p.startingLevel);
      ByteBufCodecs.VAR_INT.encode(buf, p.height);
      ByteBufCodecs.VAR_INT.encode(buf, p.orientation);
      ByteBufCodecs.BOOL.encode(buf, p.clearGround);
      ByteBufCodecs.BOOL.encode(buf, p.exportSnow);
      ByteBufCodecs.BOOL.encode(buf, p.importMockBlocks);
      ByteBufCodecs.BOOL.encode(buf, p.convertToPreserveGround);
      ByteBufCodecs.BOOL.encode(buf, p.hasMainTablePos);
      ByteBufCodecs.VAR_INT.encode(buf, p.mainTableX);
      ByteBufCodecs.VAR_INT.encode(buf, p.mainTableY);
      ByteBufCodecs.VAR_INT.encode(buf, p.mainTableZ);
      ByteBufCodecs.BOOL.encode(buf, p.isMainTable);
      ByteBufCodecs.BOOL.encode(buf, p.importedFromCulture);
   }

   private static ImportTableSyncPayload decode(ByteBuf buf) {
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String buildingId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String variant = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String cultureKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String parentBuildingId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int length = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int width = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int upgradeLevel = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int startingLevel = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int height = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int orientation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      boolean clearGround = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean exportSnow = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean importMockBlocks = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean convertToPreserveGround = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean hasMainTablePos = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      int mainX = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int mainY = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int mainZ = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      boolean isMainTable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean importedFromCulture = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      return new ImportTableSyncPayload(
         new BlockPos(x, y, z),
         buildingId,
         variant,
         cultureKey,
         parentBuildingId,
         length,
         width,
         upgradeLevel,
         startingLevel,
         height,
         orientation,
         clearGround,
         exportSnow,
         importMockBlocks,
         convertToPreserveGround,
         hasMainTablePos,
         mainX,
         mainY,
         mainZ,
         isMainTable,
         importedFromCulture
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
