package org.millenaire.building.placement;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.millenaire.block.MillPathBlock;
import org.millenaire.block.MillPathSlabBlock;

public final class BlockPlacementEngine {
   private BlockPlacementEngine() {
   }

   public static int getPlacementFlags(BlockState state) {
      return !state.is(BlockTags.BEDS) && !(state.getBlock() instanceof ChestBlock) && !(state.getBlock() instanceof DoorBlock) ? 3 : 2;
   }

   public static BlockState hydrateIfFarmland(BlockState state) {
      return state.is(Blocks.FARMLAND) ? (BlockState)state.setValue(FarmBlock.MOISTURE, 7) : state;
   }

   public static BlockState stabilizePathBlock(BlockState state) {
      if (state.getBlock() instanceof MillPathBlock) {
         return (BlockState)state.setValue(MillPathBlock.STABLE, true);
      } else {
         return state.getBlock() instanceof MillPathSlabBlock ? (BlockState)state.setValue(MillPathSlabBlock.STABLE, true) : state;
      }
   }

   public static boolean isBlockAlreadySuitable(BlockState existing, BlockState target) {
      if (existing.equals(target)) {
         return true;
      } else if (target.is(Blocks.DIRT)) {
         return existing.is(Blocks.GRASS_BLOCK) || existing.is(Blocks.PODZOL) || existing.is(Blocks.MYCELIUM) || existing.is(Blocks.DIRT);
      } else {
         return !target.is(Blocks.GRASS_BLOCK) ? false : existing.is(Blocks.GRASS_BLOCK) || existing.is(Blocks.PODZOL) || existing.is(Blocks.MYCELIUM);
      }
   }

   public static void fixWaterlogging(ServerLevel level, BlockPos pos, BlockState templateState) {
      BooleanProperty waterloggedProp = BlockStateProperties.WATERLOGGED;
      if (templateState.hasProperty(waterloggedProp)) {
         if (!(Boolean)templateState.getValue(waterloggedProp)) {
            BlockState worldState = level.getBlockState(pos);
            if (worldState.hasProperty(waterloggedProp) && (Boolean)worldState.getValue(waterloggedProp)) {
               level.setBlock(pos, (BlockState)worldState.setValue(waterloggedProp, false), 2);
            }
         }
      }
   }

   public static void fixAdjacentChestType(ServerLevel level, BlockPos pos, BlockState placed) {
      Direction facing = (Direction)placed.getValue(ChestBlock.FACING);
      ChestType currentType = (ChestType)placed.getValue(ChestBlock.TYPE);
      if (currentType == ChestType.SINGLE) {
         for (Direction dir : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockPos adjPos = pos.relative(dir);
            BlockState adjState = level.getBlockState(adjPos);
            if (adjState.getBlock() == placed.getBlock()
               && adjState.getValue(ChestBlock.FACING) == facing
               && adjState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
               ChestType thisType = dir == facing.getClockWise() ? ChestType.LEFT : ChestType.RIGHT;
               ChestType otherType = thisType == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT;
               level.setBlock(pos, (BlockState)placed.setValue(ChestBlock.TYPE, thisType), 2);
               level.setBlock(adjPos, (BlockState)adjState.setValue(ChestBlock.TYPE, otherType), 2);
               return;
            }
         }
      }
   }

   public static void clearBedIfPresent(ServerLevel level, BlockPos pos) {
      BlockState oldState = level.getBlockState(pos);
      if (oldState.getBlock() instanceof BedBlock) {
         if (oldState.hasProperty(BedBlock.PART) && oldState.hasProperty(BedBlock.FACING)) {
            Direction facing = (Direction)oldState.getValue(BedBlock.FACING);
            BedPart part = (BedPart)oldState.getValue(BedBlock.PART);
            BlockPos otherPos = part == BedPart.HEAD ? pos.relative(facing.getOpposite()) : pos.relative(facing);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            if (level.getBlockState(otherPos).getBlock() instanceof BedBlock) {
               level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 2);
            }
         }
      }
   }

   public static void clearDoorIfPresent(ServerLevel level, BlockPos pos) {
      BlockState oldState = level.getBlockState(pos);
      if (oldState.getBlock() instanceof DoorBlock) {
         if (oldState.hasProperty(DoorBlock.HALF)) {
            DoubleBlockHalf half = (DoubleBlockHalf)oldState.getValue(DoorBlock.HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            if (level.getBlockState(otherPos).getBlock() instanceof DoorBlock) {
               level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 2);
            }
         }
      }
   }

   @Nullable
   public static BlockPos generateBedFoot(ServerLevel level, BlockPos headPos, BlockState state) {
      if (!(state.getBlock() instanceof BedBlock)) {
         return null;
      }

      if (!state.hasProperty(BedBlock.PART)) {
         return null;
      }

      if (state.getValue(BedBlock.PART) != BedPart.HEAD) {
         return null;
      }

      Direction facing = (Direction)state.getValue(BedBlock.FACING);
      BlockPos footPos = headPos.relative(facing.getOpposite());
      BlockState existing = level.getBlockState(footPos);
      if (existing.getBlock() instanceof BedBlock) {
         return null;
      }

      BlockState footState = (BlockState)state.setValue(BedBlock.PART, BedPart.FOOT);
      level.setBlock(footPos, footState, 2);
      level.setBlock(headPos, state, 2);
      return footPos;
   }

   public static void generateDoorUpper(ServerLevel level, BlockPos lowerPos, BlockState state) {
      if (state.getBlock() instanceof DoorBlock) {
         if (state.hasProperty(DoorBlock.HALF)) {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
               BlockPos upperPos = lowerPos.above();
               BlockState upperState = (BlockState)state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
               BlockState existing = level.getBlockState(upperPos);
               if (!existing.equals(upperState)) {
                  level.setBlock(upperPos, upperState, 2);
                  level.setBlock(lowerPos, state, 2);
               }
            }
         }
      }
   }
}
