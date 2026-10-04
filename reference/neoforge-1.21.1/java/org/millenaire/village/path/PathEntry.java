package org.millenaire.village.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record PathEntry(BlockPos pos, BlockState state) {
}
