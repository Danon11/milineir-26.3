package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.slf4j.Logger;

public class CocoaPlantingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

   public String id() {
      return "cocoa_planting";
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      return this.findPlantablePosition(ctx.level(), ctx, type, null) != null;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      BlockPos pos = this.findPlantablePosition(ctx.level(), ctx, type, lastTarget);
      return pos != null ? new GatheringTarget.BlockTarget(pos) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.BlockTarget blockTarget) {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         if (!level.getBlockState(pos).isAir()) {
            return true;
         }

         Direction logDirection = this.findAdjacentLogDirection(level, pos);
         if (logDirection == null) {
            return true;
         }

         Direction cocoaFacing = logDirection.getOpposite();
         BlockState cocoaState = (BlockState)((BlockState)Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, cocoaFacing))
            .setValue(CocoaBlock.AGE, 0);
         level.setBlock(pos, cocoaState, 3);
         return true;
      } else {
         return true;
      }
   }

   @Nullable
   private BlockPos findPlantablePosition(ServerLevel level, GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      List<BlockPos> soilPositions = this.collectSoilPositions(ctx, type);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      return findClosestBlock(
         soilPositions,
         soil -> level.isLoaded(soil) && level.getBlockState(soil).isAir() && this.findAdjacentLogDirection(level, soil) != null,
         reference,
         lastTarget,
         type.batchRadius()
      );
   }

   @Nullable
   private Direction findAdjacentLogDirection(ServerLevel level, BlockPos pos) {
      Direction found = null;

      for (Direction dir : HORIZONTAL_DIRECTIONS) {
         BlockPos adjacent = pos.relative(dir);
         if (level.isLoaded(adjacent)) {
            BlockState adjacentState = level.getBlockState(adjacent);
            if (adjacentState.is(Blocks.JUNGLE_LOG)) {
               found = dir;
            }
         }
      }

      return found;
   }
}
