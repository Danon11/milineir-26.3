package org.millenaire.village.path;

import net.minecraft.core.BlockPos;
import org.millenaire.building.BuildingId;

public record PathDiagnostic(
   BuildingId building,
   String planSetId,
   BlockPos origin,
   int expectedTier,
   int effectiveTier,
   BlockPos source,
   boolean sourceIsFallback,
   BlockPos destination,
   boolean connected,
   PathFailureReason failure,
   AStarFailureDetail astarDetail,
   int traceLength,
   int placedBlocks,
   boolean lateral
) {
   public PathDiagnostic(
      BuildingId building,
      String planSetId,
      BlockPos origin,
      int expectedTier,
      int effectiveTier,
      BlockPos source,
      boolean sourceIsFallback,
      BlockPos destination,
      boolean connected,
      PathFailureReason failure,
      AStarFailureDetail astarDetail,
      int traceLength,
      int placedBlocks
   ) {
      this(
         building,
         planSetId,
         origin,
         expectedTier,
         effectiveTier,
         source,
         sourceIsFallback,
         destination,
         connected,
         failure,
         astarDetail,
         traceLength,
         placedBlocks,
         false
      );
   }
}
