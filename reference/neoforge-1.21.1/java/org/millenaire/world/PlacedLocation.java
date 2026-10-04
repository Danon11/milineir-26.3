package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

public record PlacedLocation(BlockPos position, Rotation rotation) {
}
