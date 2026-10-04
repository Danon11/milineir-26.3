package org.millenaire.village;

import java.util.List;
import net.minecraft.core.BlockPos;

public record WaypointEdge(Waypoint target, double cost, List<BlockPos> pathNodes) {
}
