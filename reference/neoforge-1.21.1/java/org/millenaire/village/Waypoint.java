package org.millenaire.village;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import org.millenaire.building.BuildingId;

public record Waypoint(BlockPos pos, @Nullable BuildingId buildingId) {
}
