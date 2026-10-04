package org.millenaire.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

public class OpenFenceGateGoal extends Goal {
   private final Mob mob;
   private final boolean closeBehind;
   @Nullable
   private BlockPos gatePos;
   private boolean hasOpened;
   private boolean didOpen;
   private int closeTimer;

   public OpenFenceGateGoal(Mob mob, boolean closeBehind) {
      this.mob = mob;
      this.closeBehind = closeBehind;
   }

   public boolean requiresUpdateEveryTick() {
      return true;
   }

   public boolean canUse() {
      Path path = this.mob.getNavigation().getPath();
      if (path != null && !path.isDone()) {
         for (int i = Math.max(0, path.getNextNodeIndex() - 1); i <= Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + 1); i++) {
            Node node = path.getNode(i);
            BlockPos pos = node.asBlockPos();
            if (this.mob.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 4.0) {
               BlockState state = this.mob.level().getBlockState(pos);
               if (state.getBlock() instanceof FenceGateBlock) {
                  this.gatePos = pos;
                  return true;
               }
            }
         }

         BlockPos mobPos = this.mob.blockPosition();

         for (BlockPos check : new BlockPos[]{mobPos, mobPos.north(), mobPos.south(), mobPos.east(), mobPos.west()}) {
            BlockState state = this.mob.level().getBlockState(check);
            if (state.getBlock() instanceof FenceGateBlock
               && !(Boolean)state.getValue(FenceGateBlock.OPEN)
               && this.mob.distanceToSqr(check.getX() + 0.5, check.getY() + 0.5, check.getZ() + 0.5) <= 4.0) {
               this.gatePos = check;
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean canContinueToUse() {
      return !this.hasOpened || this.closeBehind && this.closeTimer > 0;
   }

   public void start() {
      this.hasOpened = false;
      this.didOpen = false;
      this.closeTimer = 20;
   }

   public void stop() {
      if (this.closeBehind && this.didOpen && this.gatePos != null) {
         this.setGateOpen(false);
      }
   }

   public void tick() {
      if (!this.hasOpened && this.gatePos != null) {
         BlockState state = this.mob.level().getBlockState(this.gatePos);
         if (state.getBlock() instanceof FenceGateBlock && !(Boolean)state.getValue(FenceGateBlock.OPEN)) {
            this.setGateOpen(true);
            this.didOpen = true;
         }

         this.hasOpened = true;
      }

      if (this.hasOpened && this.closeBehind) {
         this.closeTimer--;
      }
   }

   private void setGateOpen(boolean open) {
      if (this.gatePos != null) {
         Level level = this.mob.level();
         BlockState state = level.getBlockState(this.gatePos);
         if (state.getBlock() instanceof FenceGateBlock && (Boolean)state.getValue(FenceGateBlock.OPEN) != open) {
            level.setBlock(this.gatePos, (BlockState)state.setValue(FenceGateBlock.OPEN, open), 10);
         }
      }
   }
}
