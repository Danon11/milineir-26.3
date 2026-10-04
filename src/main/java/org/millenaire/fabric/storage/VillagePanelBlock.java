package org.millenaire.fabric.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/** Persistent, synchronized wall label; village statistics and the old panel GUI come later. */
public final class VillagePanelBlock extends WallSignBlock {
    public VillagePanelBlock(BlockBehaviour.Properties properties) { super(WoodType.OAK, properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VillagePanelBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, VillageStorageContent.PANEL_TYPE, SignBlockEntity::tick);
    }
}
