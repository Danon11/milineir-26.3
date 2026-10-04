package org.millenaire.goal.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public sealed interface GatheringTarget permits GatheringTarget.BlockTarget, GatheringTarget.EntityTarget, GatheringTarget.AreaTarget {
   BlockPos navigationPos();

   record AreaTarget(BlockPos center, int radius) implements GatheringTarget {
      public BlockPos navigationPos() {
         return this.center;
      }
   }

   record BlockTarget(BlockPos pos) implements GatheringTarget {
      public BlockPos navigationPos() {
         return this.pos;
      }
   }

   record EntityTarget(Entity entity) implements GatheringTarget {
      public BlockPos navigationPos() {
         return this.entity.blockPosition();
      }
   }
}
