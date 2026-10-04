package org.millenaire.fabric.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class VillageChestBlock extends ChestBlock implements WorldlyContainerHolder {
    public VillageChestBlock(BlockBehaviour.Properties properties) {
        super(() -> VillageStorageContent.CHEST_TYPE, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VillageChestBlockEntity(pos, state); }
    // Automatic joining could merge different buildings or their access policies. Plan compilation
    // connects declared pairs; player-placed chests remain separate until an ownership-aware tool exists.
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(TYPE, ChestType.SINGLE);
    }
    @Override public boolean chestCanConnectTo(BlockState state) { return state.is(this); }
    @Override public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof VillageChestBlockEntity chest ? chest : null;
    }
}
