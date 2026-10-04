package org.millenaire.goal.gathering.handler;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.RicePaddyBlock;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;

public class PaddyPlantingHandler extends AbstractGatheringHandler {
   public String id() {
      return "paddy_planting";
   }

   public boolean supportsRemoteAction() {
      return true;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      BlockPos result = this.findPlantableBlock(ctx.level(), ctx, type, null, ctx.villager().blockPosition(), type.batchRadius());
      return result != null;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      BlockPos found = this.findPlantableBlock(ctx.level(), ctx, type, lastTarget, ctx.villager().blockPosition(), type.batchRadius());
      return found != null ? new GatheringTarget.BlockTarget(found) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.BlockTarget blockTarget) {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         BlockState state = level.getBlockState(pos);
         if (!RicePaddyBlock.canPlant(state)) {
            return true;
         }

         level.setBlock(pos, (BlockState)((BlockState)state.setValue(RicePaddyBlock.PLANTED, true)).setValue(RicePaddyBlock.AGE, 0), 3);
         return true;
      } else {
         return true;
      }
   }

   @Nullable
   private BlockPos findPlantableBlock(
      ServerLevel level, GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget, BlockPos villagerPos, int batchRadius
   ) {
      List<BlockPos> soilPositions = this.collectSoilPositions(ctx, type);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : villagerPos;
      return findClosestBlock(
         soilPositions, pos -> level.isLoaded(pos) && RicePaddyBlock.canPlant(level.getBlockState(pos)), reference, lastTarget, batchRadius
      );
   }
}
