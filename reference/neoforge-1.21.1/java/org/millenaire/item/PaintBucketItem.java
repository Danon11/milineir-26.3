package org.millenaire.item;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.block.IPaintedBlock;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.PaintedBrickBlock;
import org.millenaire.block.PaintedBrickSlabBlock;
import org.millenaire.block.PaintedBrickStairBlock;
import org.millenaire.block.PaintedBrickWallBlock;

public class PaintBucketItem extends Item {
   private static final int MAX_BLOCKS = 256;
   private final DyeColor color;

   public PaintBucketItem(DyeColor color, Properties properties) {
      super(properties);
      this.color = color;
   }

   public DyeColor getColor() {
      return this.color;
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      BlockPos pos = context.getClickedPos();
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof IPaintedBlock existingBrick) {
         if (existingBrick.getColor() == this.color) {
            return InteractionResult.PASS;
         }

         if (level.isClientSide) {
            return InteractionResult.SUCCESS;
         }

         DyeColor oldColor = existingBrick.getColor();
         int blocksColored = this.floodFill(level, pos, oldColor, this.color);
         if (blocksColored > 0 && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            MillAdvancements.grant(serverPlayer, MillAdvancements.RAINBOW);
         }

         if (blocksColored > 0 && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            ItemStack stack = context.getItemInHand();
            EquipmentSlot slot = serverPlayer.getEquipmentSlotForItem(stack);
            stack.hurtAndBreak(blocksColored, (ServerLevel)level, serverPlayer, item -> serverPlayer.onEquippedItemBroken(item, slot));
         }

         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   private int floodFill(Level level, BlockPos start, DyeColor oldColor, DyeColor newColor) {
      Set<BlockPos> visited = new HashSet<>();
      Queue<BlockPos> queue = new LinkedList<>();
      queue.add(start);
      int count = 0;

      while (!queue.isEmpty() && count < 256) {
         BlockPos pos = queue.poll();
         if (visited.add(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof IPaintedBlock painted && painted.getColor() == oldColor) {
               BlockState newState = this.getRecoloredState(state, painted, newColor);
               if (newState != null) {
                  level.setBlock(pos, newState, 2);
                  count++;
               }

               for (Direction dir : Direction.values()) {
                  queue.add(pos.relative(dir));
               }
            }
         }
      }

      return count;
   }

   private BlockState getRecoloredState(BlockState state, IPaintedBlock painted, DyeColor newColor) {
      if (painted instanceof PaintedBrickBlock brick) {
         return brick.getBrickType() == PaintedBrickBlock.BrickType.DECORATED
            ? ((Block)ModBlocks.DECORATED_BRICKS.get(newColor).get()).defaultBlockState()
            : ((Block)ModBlocks.PAINTED_BRICKS.get(newColor).get()).defaultBlockState();
      } else if (painted instanceof PaintedBrickStairBlock) {
         BlockState base = ((Block)ModBlocks.PAINTED_BRICK_STAIRS.get(newColor).get()).defaultBlockState();
         return (BlockState)((BlockState)((BlockState)((BlockState)base.setValue(StairBlock.FACING, (Direction)state.getValue(StairBlock.FACING)))
                  .setValue(StairBlock.HALF, (Half)state.getValue(StairBlock.HALF)))
               .setValue(StairBlock.SHAPE, (StairsShape)state.getValue(StairBlock.SHAPE)))
            .setValue(StairBlock.WATERLOGGED, (Boolean)state.getValue(StairBlock.WATERLOGGED));
      } else if (painted instanceof PaintedBrickSlabBlock) {
         BlockState base = ((Block)ModBlocks.PAINTED_BRICK_SLABS.get(newColor).get()).defaultBlockState();
         return (BlockState)((BlockState)base.setValue(SlabBlock.TYPE, (SlabType)state.getValue(SlabBlock.TYPE)))
            .setValue(SlabBlock.WATERLOGGED, (Boolean)state.getValue(SlabBlock.WATERLOGGED));
      } else if (painted instanceof PaintedBrickWallBlock) {
         BlockState base = ((Block)ModBlocks.PAINTED_BRICK_WALLS.get(newColor).get()).defaultBlockState();
         return (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)base.setValue(WallBlock.UP, (Boolean)state.getValue(WallBlock.UP)))
                        .setValue(WallBlock.NORTH_WALL, (WallSide)state.getValue(WallBlock.NORTH_WALL)))
                     .setValue(WallBlock.EAST_WALL, (WallSide)state.getValue(WallBlock.EAST_WALL)))
                  .setValue(WallBlock.SOUTH_WALL, (WallSide)state.getValue(WallBlock.SOUTH_WALL)))
               .setValue(WallBlock.WEST_WALL, (WallSide)state.getValue(WallBlock.WEST_WALL)))
            .setValue(WallBlock.WATERLOGGED, (Boolean)state.getValue(WallBlock.WATERLOGGED));
      } else {
         return null;
      }
   }
}
