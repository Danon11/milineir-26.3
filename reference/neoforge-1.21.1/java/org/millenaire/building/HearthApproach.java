package org.millenaire.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class HearthApproach {
   private HearthApproach() {
   }

   public static BlockPos findStandPosition(BlockGetter level, BlockPos hearthPos) {
      for (Direction dir : Plane.HORIZONTAL) {
         BlockPos candidate = hearthPos.relative(dir);
         if (isWalkableStand(level, candidate)) {
            return candidate;
         }
      }

      return hearthPos;
   }

   static boolean isWalkableStand(BlockGetter level, BlockPos pos) {
      BlockState here = level.getBlockState(pos);
      if (isLitCampfire(here)) {
         return false;
      } else if (!isPassable(level, pos, here)) {
         return false;
      } else {
         BlockState above = level.getBlockState(pos.above());
         if (!isPassable(level, pos.above(), above)) {
            return false;
         } else {
            BlockPos belowPos = pos.below();
            BlockState below = level.getBlockState(belowPos);
            if (below.isAir()) {
               return false;
            } else {
               return !below.is(BlockTags.FENCES) && !below.is(BlockTags.WALLS) && !below.is(Blocks.IRON_BARS)
                  ? below.isFaceSturdy(level, belowPos, Direction.UP)
                  : false;
            }
         }
      }
   }

   private static boolean isPassable(BlockGetter level, BlockPos pos, BlockState state) {
      return state.isAir() ? true : state.getCollisionShape(level, pos).isEmpty();
   }

   private static boolean isLitCampfire(BlockState state) {
      return state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && (Boolean)state.getValue(CampfireBlock.LIT);
   }
}
