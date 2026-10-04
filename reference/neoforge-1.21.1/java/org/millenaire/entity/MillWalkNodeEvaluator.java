package org.millenaire.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.millenaire.block.MillPathBlock;
import org.millenaire.block.MillPathSlabBlock;
import org.millenaire.block.PathTier;
import org.millenaire.block.RicePaddyBlock;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.village.Village;

public class MillWalkNodeEvaluator extends WalkNodeEvaluator {
   @Nullable
   private final Mob mob;
   @Nullable
   private Village cachedVillage;
   private long cachedVillageTick = Long.MIN_VALUE;
   private boolean cachedNoLeafClearing;

   public MillWalkNodeEvaluator() {
      this(null);
   }

   public MillWalkNodeEvaluator(@Nullable Mob mob) {
      this.mob = mob;
   }

   public PathType getPathType(PathfindingContext context, int x, int y, int z) {
      PathType base = super.getPathType(context, x, y, z);
      BlockState here = context.level().getBlockState(new BlockPos(x, y, z));
      if (isLitCampfire(here)) {
         return PathType.BLOCKED;
      }

      if (y > context.level().getMinBuildHeight()) {
         BlockState belowState = context.level().getBlockState(new BlockPos(x, y - 1, z));
         if (belowState.is(BlockTags.FENCES) || belowState.is(BlockTags.WALLS) || belowState.is(Blocks.IRON_BARS)) {
            return PathType.BLOCKED;
         }
      }

      if (base == PathType.FENCE) {
         BlockState state = context.level().getBlockState(new BlockPos(x, y, z));
         return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN) && this.canOpenDoors()
            ? PathType.DOOR_WOOD_CLOSED
            : PathType.BLOCKED;
      }

      if (base == PathType.LEAVES) {
         return this.isLeafTraversalBlocked(x, y, z) ? PathType.BLOCKED : PathType.OPEN;
      }

      if (base == PathType.WATER || base == PathType.WATER_BORDER) {
         BlockState state = context.level().getBlockState(new BlockPos(x, y, z));
         if (state.getBlock() instanceof RicePaddyBlock) {
            return PathType.OPEN;
         }
      }

      if (base == PathType.WALKABLE) {
         BlockState hereState = context.level().getBlockState(new BlockPos(x, y, z));
         if (hereState.getBlock() instanceof MillPathBlock || hereState.getBlock() instanceof MillPathSlabBlock) {
            return PathType.COCOA;
         }

         if (y > context.level().getMinBuildHeight()) {
            BlockState belowState = context.level().getBlockState(new BlockPos(x, y - 1, z));
            if (belowState.getBlock() instanceof MillPathBlock || belowState.getBlock() instanceof MillPathSlabBlock) {
               return PathType.COCOA;
            }
         }
      }

      return base;
   }

   protected Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType) {
      Node node = super.findAcceptedNode(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType);
      if (node != null && node.type == PathType.COCOA) {
         PathTier tier = this.resolvePathTier(node.x, node.y, node.z);
         if (tier != null && tier.preferenceMalus() > node.costMalus) {
            node.costMalus = tier.preferenceMalus();
         }
      }

      return node;
   }

   protected double getFloorLevel(BlockPos pos) {
      if (this.currentContext != null) {
         BlockState here = this.currentContext.getBlockState(pos);
         if (!here.isAir() && here.getFluidState().isEmpty()) {
            VoxelShape shape = here.getCollisionShape(this.currentContext.level(), pos);
            if (!shape.isEmpty()) {
               double maxY = shape.max(Axis.Y);
               if (maxY > 0.0 && maxY < 1.0) {
                  return pos.getY() + maxY;
               }
            }
         }
      }

      return super.getFloorLevel(pos);
   }

   private PathTier resolvePathTier(int x, int y, int z) {
      BlockState hereState = this.currentContext.level().getBlockState(new BlockPos(x, y, z));
      PathTier tier = tierOf(hereState.getBlock());
      if (tier != null) {
         return tier;
      } else if (y > this.currentContext.level().getMinBuildHeight()) {
         BlockState belowState = this.currentContext.level().getBlockState(new BlockPos(x, y - 1, z));
         return tierOf(belowState.getBlock());
      } else {
         return null;
      }
   }

   private static PathTier tierOf(Block block) {
      if (block instanceof MillPathBlock mpb) {
         return mpb.tier();
      } else {
         return block instanceof MillPathSlabBlock msb ? msb.tier() : null;
      }
   }

   private boolean isLeafTraversalBlocked(int x, int y, int z) {
      if (this.mob instanceof MillVillager v) {
         if (!(this.mob.level() instanceof ServerLevel sl)) {
            return false;
         } else {
            long tick = sl.getGameTime();
            if (tick != this.cachedVillageTick) {
               this.cachedVillage = v.getVillageId() != null ? Village.resolve(sl, v.getVillageId()) : null;
               ResourceLocation typeId = v.getVillagerTypeId();
               if (typeId == null) {
                  this.cachedNoLeafClearing = true;
               } else {
                  VillagerType vt = ModCultures.getVillagerType(typeId);
                  this.cachedNoLeafClearing = vt != null && vt.hasTag("noleafclearing");
               }

               this.cachedVillageTick = tick;
            }

            return this.cachedNoLeafClearing ? true : this.cachedVillage != null && this.cachedVillage.getBuildingAt(new BlockPos(x, y, z)) != null;
         }
      } else {
         return false;
      }
   }

   static boolean isLitCampfire(BlockState state) {
      return state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && (Boolean)state.getValue(CampfireBlock.LIT);
   }
}
