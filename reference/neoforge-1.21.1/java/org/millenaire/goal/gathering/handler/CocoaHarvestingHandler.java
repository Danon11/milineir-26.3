package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.slf4j.Logger;

public class CocoaHarvestingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   public String id() {
      return "cocoa_harvesting";
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      for (BlockPos soil : this.collectSoilPositions(ctx, type)) {
         if (ctx.level().isLoaded(soil)) {
            BlockState state = ctx.level().getBlockState(soil);
            if (state.is(Blocks.COCOA) && (Integer)state.getValue(CocoaBlock.AGE) >= 2) {
               return true;
            }
         }
      }

      return false;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      ServerLevel level = ctx.level();
      List<BlockPos> soilPositions = this.collectSoilPositions(ctx, type);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos best = findClosestBlock(
         soilPositions, pos -> level.isLoaded(pos) && isMatureCocoa(level.getBlockState(pos)), reference, lastTarget, type.batchRadius()
      );
      return best != null ? new GatheringTarget.BlockTarget(best) : null;
   }

   private static boolean isMatureCocoa(BlockState state) {
      return state.is(Blocks.COCOA) && (Integer)state.getValue(CocoaBlock.AGE) >= 2;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (!(target instanceof GatheringTarget.BlockTarget blockTarget)) {
         return true;
      } else {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         BlockState state = level.getBlockState(pos);
         if (state.is(Blocks.COCOA) && (Integer)state.getValue(CocoaBlock.AGE) >= 2) {
            List<ItemStack> drops = Block.getDrops(state, level, pos, null);
            level.destroyBlock(pos, false);

            for (ItemStack drop : drops) {
               ctx.villager().getInventory().add(drop.getItem(), drop.getCount());
            }

            return true;
         } else {
            return true;
         }
      }
   }
}
