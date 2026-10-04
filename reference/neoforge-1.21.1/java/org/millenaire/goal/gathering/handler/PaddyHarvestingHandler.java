package org.millenaire.goal.gathering.handler;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.RicePaddyBlock;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;

public class PaddyHarvestingHandler extends AbstractGatheringHandler {
   public String id() {
      return "paddy_harvesting";
   }

   public boolean supportsRemoteAction() {
      return true;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      return this.findHarvestableBlock(ctx.level(), ctx, type) != null;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      ServerLevel level = ctx.level();
      List<BlockPos> soilPositions = this.collectSoilPositions(ctx, type);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos best = findClosestBlock(
         soilPositions, pos -> level.isLoaded(pos) && RicePaddyBlock.canHarvest(level.getBlockState(pos)), reference, lastTarget, type.batchRadius()
      );
      return best != null ? new GatheringTarget.BlockTarget(best) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (!(target instanceof GatheringTarget.BlockTarget blockTarget)) {
         return true;
      } else {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         BlockState state = level.getBlockState(pos);
         if (!RicePaddyBlock.canHarvest(state)) {
            return true;
         }

         for (ItemStack drop : Block.getDrops(state, level, pos, null)) {
            ctx.villager().getInventory().add(drop.getItem(), drop.getCount());
         }

         level.setBlock(pos, (BlockState)((BlockState)state.setValue(RicePaddyBlock.PLANTED, false)).setValue(RicePaddyBlock.AGE, 0), 3);
         return true;
      }
   }

   @Nullable
   private BlockPos findHarvestableBlock(ServerLevel level, GoalContext ctx, GatheringType type) {
      for (BlockPos soil : this.collectSoilPositions(ctx, type)) {
         if (level.isLoaded(soil) && RicePaddyBlock.canHarvest(level.getBlockState(soil))) {
            return soil;
         }
      }

      return null;
   }
}
