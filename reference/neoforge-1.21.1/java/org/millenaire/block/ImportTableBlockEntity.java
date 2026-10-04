package org.millenaire.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.millenaire.building.BuildingExporter;
import org.millenaire.building.BuildingImporter;

public class ImportTableBlockEntity extends BlockEntity {
   private String buildingId = "";
   private String variant = "a";
   private String cultureKey = "";
   private String parentBuildingId = "";
   private int length = 10;
   private int width = 10;
   private int upgradeLevel = 0;
   private int startingLevel = -1;
   private int height = 10;
   private int orientation = 0;
   private boolean clearGround = false;
   private boolean exportSnow = false;
   private boolean importMockBlocks = true;
   private boolean convertToPreserveGround = true;
   @Nullable
   private BlockPos mainTablePos = null;
   private boolean isMainTable = false;
   private boolean importedFromCulture = false;
   private long lastSavedBlocksHash = 0L;
   private boolean dirty = false;
   private int ticksSinceLastCheck = 0;
   private int savedOrientation = 0;
   private int savedStartingLevel = -1;
   private int savedHeight = 10;
   private boolean savedExportSnow = false;
   private boolean savedImportMockBlocks = true;
   private boolean savedConvertToPreserveGround = true;
   private boolean savedStateInitialised = false;
   private static final int CHILD_SEARCH_RADIUS = 1360;
   private static final int CHECK_INTERVAL_TICKS = 100;
   private static final int NEAR_PLAYER_MARGIN = 16;

   public ImportTableBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)ModBlockEntities.IMPORT_TABLE.get(), pos, state);
   }

   public String getBuildingId() {
      return this.buildingId;
   }

   public String getVariant() {
      return this.variant;
   }

   public String getCultureKey() {
      return this.cultureKey;
   }

   public String getParentBuildingId() {
      return this.parentBuildingId;
   }

   public int getLength() {
      return this.length;
   }

   public int getWidth() {
      return this.width;
   }

   public int getUpgradeLevel() {
      return this.upgradeLevel;
   }

   public int getStartingLevel() {
      return this.startingLevel;
   }

   public int getHeight() {
      return this.height;
   }

   public int getOrientation() {
      return this.orientation;
   }

   public boolean isClearGround() {
      return this.clearGround;
   }

   public boolean isExportSnow() {
      return this.exportSnow;
   }

   public boolean isImportMockBlocks() {
      return this.importMockBlocks;
   }

   public boolean isConvertToPreserveGround() {
      return this.convertToPreserveGround;
   }

   @Nullable
   public BlockPos getMainTablePos() {
      return this.mainTablePos;
   }

   public boolean isMainTable() {
      return this.isMainTable;
   }

   public boolean isImportedFromCulture() {
      return this.importedFromCulture;
   }

   public long getLastSavedBlocksHash() {
      return this.lastSavedBlocksHash;
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public boolean hasPlan() {
      return !this.buildingId.isEmpty();
   }

   public boolean isLinked() {
      return isLinked(this.isMainTable, this.mainTablePos);
   }

   static boolean isLinked(boolean isMainTable, @Nullable BlockPos mainTablePos) {
      return isMainTable || mainTablePos != null;
   }

   public ImportTableBlockEntity resolveMainTable(ServerLevel level) {
      if (!this.isMainTable && this.mainTablePos != null) {
         return level.getBlockEntity(this.mainTablePos) instanceof ImportTableBlockEntity main && main.isMainTable() ? main : this;
      } else {
         return this;
      }
   }

   public List<ImportTableBlockEntity> findChildTables(ServerLevel level) {
      if (!this.isMainTable) {
         return List.of();
      }

      BlockPos mainPos = this.getBlockPos();
      List<ImportTableBlockEntity> heads = new ArrayList<>();
      collectChildTables(level, mainPos, mainPos.getX() - 4 >> 4, mainPos.getX() + 1360 >> 4, mainPos.getZ() - 4 >> 4, mainPos.getZ() + 4 >> 4, heads);
      List<ImportTableBlockEntity> result = new ArrayList<>(heads);
      List<BlockPos> columnAnchors = new ArrayList<>(1 + heads.size());
      columnAnchors.add(mainPos);

      for (ImportTableBlockEntity head : heads) {
         columnAnchors.add(head.getBlockPos());
      }

      for (BlockPos anchor : columnAnchors) {
         collectChildTables(level, mainPos, anchor.getX() - 4 >> 4, anchor.getX() + 4 >> 4, mainPos.getZ() - 4 >> 4, mainPos.getZ() + 1360 >> 4, result);
      }

      result.sort(Comparator.comparingInt(ImportTableBlockEntity::getUpgradeLevel));
      return result;
   }

   private static void collectChildTables(
      ServerLevel level, BlockPos mainPos, int minCX, int maxCX, int minCZ, int maxCZ, List<ImportTableBlockEntity> children
   ) {
      for (int cx = minCX; cx <= maxCX; cx++) {
         for (int cz = minCZ; cz <= maxCZ; cz++) {
            LevelChunk chunk = level.getChunk(cx, cz);

            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
               if (blockEntity instanceof ImportTableBlockEntity childBe) {
                  BlockPos childMain = childBe.getMainTablePos();
                  if (childMain != null && childMain.equals(mainPos) && !children.contains(childBe)) {
                     children.add(childBe);
                  }
               }
            }
         }
      }
   }

   public void propagatePlanIdentity(ServerLevel level) {
      for (ImportTableBlockEntity child : this.findChildTables(level)) {
         child.setCultureKey(this.cultureKey);
         child.setBuildingId(this.buildingId);
      }
   }

   public void setBuildingId(String buildingId) {
      this.buildingId = buildingId;
      this.setChanged();
   }

   public void setVariant(String variant) {
      this.variant = variant;
      this.setChanged();
   }

   public void setCultureKey(String cultureKey) {
      this.cultureKey = cultureKey;
      this.setChanged();
   }

   public void setParentBuildingId(String parentBuildingId) {
      this.parentBuildingId = parentBuildingId;
      this.setChanged();
   }

   public void setLength(int length) {
      this.length = length;
      this.setChanged();
   }

   public void setWidth(int width) {
      this.width = width;
      this.setChanged();
   }

   public void setUpgradeLevel(int upgradeLevel) {
      this.upgradeLevel = upgradeLevel;
      this.setChanged();
   }

   public void setStartingLevel(int startingLevel) {
      this.startingLevel = startingLevel;
      this.setChanged();
   }

   public void setHeight(int height) {
      this.height = height;
      this.setChanged();
   }

   public void setOrientation(int orientation) {
      this.orientation = orientation;
      this.setChanged();
   }

   public void setClearGround(boolean clearGround) {
      this.clearGround = clearGround;
      this.setChanged();
   }

   public void setExportSnow(boolean exportSnow) {
      this.exportSnow = exportSnow;
      this.setChanged();
   }

   public void setImportMockBlocks(boolean importMockBlocks) {
      this.importMockBlocks = importMockBlocks;
      this.setChanged();
   }

   public void setConvertToPreserveGround(boolean convertToPreserveGround) {
      this.convertToPreserveGround = convertToPreserveGround;
      this.setChanged();
   }

   public void setMainTablePos(@Nullable BlockPos mainTablePos) {
      this.mainTablePos = mainTablePos;
      this.setChanged();
   }

   public void setIsMainTable(boolean isMainTable) {
      this.isMainTable = isMainTable;
      this.setChanged();
   }

   public void setImportedFromCulture(boolean importedFromCulture) {
      this.importedFromCulture = importedFromCulture;
      this.setChanged();
   }

   public void setLastSavedBlocksHash(long hash) {
      this.lastSavedBlocksHash = hash;
      this.setChanged();
   }

   public void setDirty(boolean dirty) {
      if (this.dirty != dirty) {
         this.dirty = dirty;
         this.setChanged();
         if (this.level instanceof ServerLevel serverLevel && this.hasPlan()) {
            BuildingImporter.placeConstructionBorder(serverLevel, this);
         }
      }
   }

   public void captureSavedState(long blocksHash) {
      this.lastSavedBlocksHash = blocksHash;
      this.savedOrientation = this.orientation;
      this.savedStartingLevel = this.startingLevel;
      this.savedHeight = this.height;
      this.savedExportSnow = this.exportSnow;
      this.savedImportMockBlocks = this.importMockBlocks;
      this.savedConvertToPreserveGround = this.convertToPreserveGround;
      this.savedStateInitialised = true;
      this.setChanged();
   }

   private boolean matchesSavedMeta() {
      return this.orientation == this.savedOrientation
         && this.startingLevel == this.savedStartingLevel
         && this.height == this.savedHeight
         && this.exportSnow == this.savedExportSnow
         && this.importMockBlocks == this.savedImportMockBlocks
         && this.convertToPreserveGround == this.savedConvertToPreserveGround;
   }

   public void copySettingsTo(ImportTableBlockEntity other) {
      other.buildingId = this.buildingId;
      other.variant = this.variant;
      other.cultureKey = this.cultureKey;
      other.parentBuildingId = this.parentBuildingId;
      other.length = this.length;
      other.width = this.width;
      other.startingLevel = this.startingLevel;
      other.height = this.height;
      other.orientation = this.orientation;
      other.clearGround = this.clearGround;
      other.exportSnow = this.exportSnow;
      other.importMockBlocks = this.importMockBlocks;
      other.convertToPreserveGround = this.convertToPreserveGround;
      other.mainTablePos = this.getBlockPos();
      other.isMainTable = false;
      other.importedFromCulture = this.importedFromCulture;
      other.setChanged();
   }

   protected void saveAdditional(CompoundTag tag, Provider registries) {
      super.saveAdditional(tag, registries);
      tag.putString("bid", this.buildingId);
      tag.putString("var", this.variant);
      tag.putString("culture", this.cultureKey);
      tag.putString("parent", this.parentBuildingId);
      tag.putInt("len", this.length);
      tag.putInt("wid", this.width);
      tag.putInt("lvl", this.upgradeLevel);
      tag.putInt("slev", this.startingLevel);
      tag.putInt("hgt", this.height);
      tag.putInt("ori", this.orientation);
      tag.putBoolean("cg", this.clearGround);
      tag.putBoolean("snow", this.exportSnow);
      tag.putBoolean("mocks", this.importMockBlocks);
      tag.putBoolean("preserve", this.convertToPreserveGround);
      tag.putBoolean("isMain", this.isMainTable);
      tag.putBoolean("ifc", this.importedFromCulture);
      tag.putLong("savedHash", this.lastSavedBlocksHash);
      tag.putBoolean("dirty", this.dirty);
      tag.putBoolean("savedInit", this.savedStateInitialised);
      if (this.savedStateInitialised) {
         tag.putInt("savedOri", this.savedOrientation);
         tag.putInt("savedSlev", this.savedStartingLevel);
         tag.putInt("savedHgt", this.savedHeight);
         tag.putBoolean("savedSnow", this.savedExportSnow);
         tag.putBoolean("savedMocks", this.savedImportMockBlocks);
         tag.putBoolean("savedPreserve", this.savedConvertToPreserveGround);
      }

      if (this.mainTablePos != null) {
         tag.putInt("mainX", this.mainTablePos.getX());
         tag.putInt("mainY", this.mainTablePos.getY());
         tag.putInt("mainZ", this.mainTablePos.getZ());
      }
   }

   protected void loadAdditional(CompoundTag tag, Provider registries) {
      super.loadAdditional(tag, registries);
      this.buildingId = tag.getString("bid");
      this.variant = tag.getString("var");
      this.cultureKey = tag.getString("culture");
      this.parentBuildingId = tag.getString("parent");
      this.length = tag.getInt("len");
      this.width = tag.getInt("wid");
      this.upgradeLevel = tag.getInt("lvl");
      this.startingLevel = tag.getInt("slev");
      this.height = tag.getInt("hgt");
      this.orientation = tag.getInt("ori");
      this.clearGround = tag.getBoolean("cg");
      this.exportSnow = tag.getBoolean("snow");
      this.importMockBlocks = tag.getBoolean("mocks");
      this.convertToPreserveGround = tag.getBoolean("preserve");
      this.isMainTable = tag.getBoolean("isMain");
      this.importedFromCulture = tag.getBoolean("ifc");
      this.lastSavedBlocksHash = tag.getLong("savedHash");
      this.dirty = tag.getBoolean("dirty");
      this.savedStateInitialised = tag.getBoolean("savedInit");
      if (this.savedStateInitialised) {
         this.savedOrientation = tag.getInt("savedOri");
         this.savedStartingLevel = tag.getInt("savedSlev");
         this.savedHeight = tag.getInt("savedHgt");
         this.savedExportSnow = tag.getBoolean("savedSnow");
         this.savedImportMockBlocks = tag.getBoolean("savedMocks");
         this.savedConvertToPreserveGround = tag.getBoolean("savedPreserve");
      }

      if (tag.contains("mainX")) {
         this.mainTablePos = new BlockPos(tag.getInt("mainX"), tag.getInt("mainY"), tag.getInt("mainZ"));
      } else {
         this.mainTablePos = null;
      }
   }

   public CompoundTag getUpdateTag(Provider registries) {
      CompoundTag tag = new CompoundTag();
      this.saveAdditional(tag, registries);
      return tag;
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, ImportTableBlockEntity be) {
      if (level instanceof ServerLevel serverLevel) {
         if (be.hasPlan()) {
            be.ticksSinceLastCheck++;
            if (be.ticksSinceLastCheck >= 100) {
               be.ticksSinceLastCheck = 0;
               if (hasPlayerNear(serverLevel, be)) {
                  long current = BuildingExporter.computeBlocksHash(serverLevel, be);
                  if (!be.savedStateInitialised) {
                     be.captureSavedState(current);
                     be.setDirty(false);
                  } else {
                     boolean blocksDirty = current != be.lastSavedBlocksHash;
                     boolean metaDirty = !be.matchesSavedMeta();
                     boolean shouldBeDirty = blocksDirty || metaDirty;
                     if (shouldBeDirty != be.dirty) {
                        be.setDirty(shouldBeDirty);
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean hasPlayerNear(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      Vec3i size = BuildingExporter.computeScanSize(be);
      int minX = origin.getX() - 16;
      int maxX = origin.getX() + size.getX() + 16;
      int minY = origin.getY() - 16;
      int maxY = origin.getY() + size.getY() + 16;
      int minZ = origin.getZ() - 16;
      int maxZ = origin.getZ() + size.getZ() + 16;

      for (ServerPlayer player : level.players()) {
         double px = player.getX();
         double py = player.getY();
         double pz = player.getZ();
         if (px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ) {
            return true;
         }
      }

      return false;
   }
}
