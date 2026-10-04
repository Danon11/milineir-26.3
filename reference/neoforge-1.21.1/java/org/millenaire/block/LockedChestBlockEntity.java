package org.millenaire.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.commerce.LockedChestMenu;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public class LockedChestBlockEntity extends ChestBlockEntity {
   @Nullable
   private BuildingId buildingId;
   private final ContainerOpenersCounter lockedChestOpenersCounter = new ContainerOpenersCounter() {
      protected void onOpen(Level level, BlockPos pos, BlockState state) {
         ChestType chestType = (ChestType)state.getValue(ChestBlock.TYPE);
         if (chestType != ChestType.LEFT) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            if (chestType == ChestType.RIGHT) {
               Direction dir = ChestBlock.getConnectedDirection(state);
               x += dir.getStepX() * 0.5;
               z += dir.getStepZ() * 0.5;
            }

            level.playSound(null, x, y, z, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
         }
      }

      protected void onClose(Level level, BlockPos pos, BlockState state) {
         ChestType chestType = (ChestType)state.getValue(ChestBlock.TYPE);
         if (chestType != ChestType.LEFT) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            if (chestType == ChestType.RIGHT) {
               Direction dir = ChestBlock.getConnectedDirection(state);
               x += dir.getStepX() * 0.5;
               z += dir.getStepZ() * 0.5;
            }

            level.playSound(null, x, y, z, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
         }
      }

      protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount) {
         LockedChestBlockEntity.this.signalOpenCount(level, pos, state, count, openCount);
      }

      protected boolean isOwnContainer(Player player) {
         if (!(player.containerMenu instanceof LockedChestMenu lockedMenu)) {
            return false;
         } else {
            Container container = lockedMenu.getContainer();
            return container == LockedChestBlockEntity.this || container instanceof CompoundContainer cc && cc.contains(LockedChestBlockEntity.this);
         }
      }
   };

   public LockedChestBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)ModBlockEntities.LOCKED_CHEST.get(), pos, state);
   }

   public void startOpen(Player player) {
      if (!this.isRemoved() && !player.isSpectator()) {
         this.lockedChestOpenersCounter.incrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
      }
   }

   public void stopOpen(Player player) {
      if (!this.isRemoved() && !player.isSpectator()) {
         this.lockedChestOpenersCounter.decrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
      }
   }

   public void recheckOpen() {
      if (!this.isRemoved()) {
         this.lockedChestOpenersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
      }
   }

   @Nullable
   public BuildingId getBuildingId() {
      return this.buildingId;
   }

   public void setBuildingId(@Nullable BuildingId buildingId) {
      this.buildingId = buildingId;
      this.setChanged();
   }

   public boolean isLockedFor(Player player) {
      if (this.buildingId == null) {
         return false;
      }

      if (this.level instanceof ServerLevel serverLevel) {
         Village village = VillageSavedData.get(serverLevel).getVillageManager().findVillageContaining(this.buildingId);
         if (village == null) {
            return false;
         } else {
            return village.isControlledBy(player.getUUID()) ? false : village.areChestsLocked();
         }
      } else {
         return true;
      }
   }

   protected Component getDefaultName() {
      String buildingName = this.resolveBuildingNativeName();
      return buildingName != null
         ? Component.translatable("block.millenaire.chest_named", new Object[]{buildingName})
         : Component.translatable("block.millenaire.chest");
   }

   @Nullable
   private String resolveBuildingNativeName() {
      if (this.buildingId != null && this.level instanceof ServerLevel serverLevel) {
         Village village = VillageSavedData.get(serverLevel).getVillageManager().findVillageContaining(this.buildingId);
         if (village == null) {
            return null;
         }

         BuildingInstance building = village.findBuildingById(this.buildingId);
         if (building == null) {
            return null;
         }

         if (building.getPlanSetId() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
            if (planSet != null) {
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(building.getVariant(), building.getLevel());
               if (levelDef != null && levelDef.nativeName() != null) {
                  return levelDef.nativeName();
               }

               return planSet.nativeName();
            }
         }

         return building.getPlanId().getPath();
      } else {
         return null;
      }
   }

   public boolean hasCustomName() {
      return true;
   }

   protected void saveAdditional(CompoundTag tag, Provider registries) {
      super.saveAdditional(tag, registries);
      if (this.buildingId != null) {
         tag.putUUID("building_id", this.buildingId.uuid());
      }
   }

   protected void loadAdditional(CompoundTag tag, Provider registries) {
      super.loadAdditional(tag, registries);
      if (tag.hasUUID("building_id")) {
         this.buildingId = new BuildingId(tag.getUUID("building_id"));
      }
   }
}
