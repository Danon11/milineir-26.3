package org.millenaire.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ModBlocks;

public class UluItem extends Item {
   private static final String TAG_RES_USE_COUNT = "resUseCount";
   private static final Block[] PLANK_BLOCKS = new Block[]{
      Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS
   };

   public UluItem(Properties properties) {
      super(properties);
   }

   public InteractionResult useOn(UseOnContext ctx) {
      Level level = ctx.getLevel();
      BlockPos pos = ctx.getClickedPos();
      Player player = ctx.getPlayer();
      ItemStack stack = ctx.getItemInHand();
      if (player == null) {
         return InteractionResult.PASS;
      }

      BlockState clickedState = level.getBlockState(pos);
      if (clickedState.is(Blocks.SNOW_BLOCK)) {
         if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
         }

         level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         player.getInventory().add(new ItemStack(((Block)ModBlocks.SNOW_BRICK.get()).asItem(), 4));
         stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
         return InteractionResult.SUCCESS;
      } else if (clickedState.is(Blocks.SNOW)) {
         if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
         }

         int layers = (Integer)clickedState.getValue(SnowLayerBlock.LAYERS);
         level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         player.getInventory().add(new ItemStack(((Block)ModBlocks.SNOW_BRICK.get()).asItem(), (layers + 1) / 2));
         stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
         return InteractionResult.SUCCESS;
      } else if (clickedState.is(Blocks.ICE)) {
         if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
         }

         level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
         player.getInventory().add(new ItemStack(((Block)ModBlocks.ICE_BRICK.get()).asItem(), 4));
         stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
         return InteractionResult.SUCCESS;
      } else {
         return this.attemptSodPlanks(player, level, pos, ctx.getClickedFace(), stack);
      }
   }

   private InteractionResult attemptSodPlanks(Player player, Level level, BlockPos pos, Direction side, ItemStack stack) {
      if (level.getBlockState(pos).is(Blocks.SNOW)) {
         side = Direction.DOWN;
      } else {
         pos = pos.relative(side);
      }

      if (!level.getBlockState(pos).isAir()) {
         return InteractionResult.PASS;
      }

      int chosenIndex = -1;

      for (int i = 0; i < PLANK_BLOCKS.length; i++) {
         if (countItem(player, PLANK_BLOCKS[i].asItem()) > 0) {
            chosenIndex = i;
            break;
         }
      }

      if (chosenIndex == -1) {
         if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("ui.millenaire.ulu.noplanks"), false);
         }

         return InteractionResult.PASS;
      } else {
         if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
         }

         int resUseCount = getResUseCount(stack);
         if (resUseCount == 0) {
            if (countItem(player, Blocks.COARSE_DIRT.asItem()) == 0) {
               player.displayClientMessage(Component.translatable("ui.millenaire.ulu.nodirt"), false);
               return InteractionResult.PASS;
            }

            removeItem(player, Blocks.COARSE_DIRT.asItem(), 1);
            removeItem(player, PLANK_BLOCKS[chosenIndex].asItem(), 1);
            resUseCount = 3;
         } else {
            resUseCount--;
         }

         setResUseCount(stack, resUseCount);
         Block sodBlock = getSodBlock(chosenIndex);
         level.setBlock(pos, sodBlock.defaultBlockState(), 3);
         stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
         return InteractionResult.SUCCESS;
      }
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      int resUseCount = getResUseCount(stack);
      if (resUseCount > 0) {
         tooltipComponents.add(Component.translatable("ui.millenaire.ulu.sodplanksleft", new Object[]{resUseCount}));
      }
   }

   private static int getResUseCount(ItemStack stack) {
      CustomData data = (CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
      CompoundTag tag = data.copyTag();
      return tag.getInt("resUseCount");
   }

   private static void setResUseCount(ItemStack stack, int count) {
      CustomData data = (CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
      CompoundTag tag = data.copyTag();
      tag.putInt("resUseCount", count);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
   }

   private static Block getSodBlock(int plankIndex) {
      return switch (plankIndex) {
         case 0 -> (Block)ModBlocks.SOD_OAK.get();
         case 1 -> (Block)ModBlocks.SOD_SPRUCE.get();
         case 2 -> (Block)ModBlocks.SOD_BIRCH.get();
         case 3 -> (Block)ModBlocks.SOD_JUNGLE.get();
         case 4 -> (Block)ModBlocks.SOD_ACACIA.get();
         case 5 -> (Block)ModBlocks.SOD_DARK_OAK.get();
         default -> (Block)ModBlocks.SOD_OAK.get();
      };
   }

   private static int countItem(Player player, Item item) {
      int count = 0;

      for (ItemStack invStack : player.getInventory().items) {
         if (invStack.is(item)) {
            count += invStack.getCount();
         }
      }

      return count;
   }

   private static void removeItem(Player player, Item item, int amount) {
      int remaining = amount;

      for (ItemStack invStack : player.getInventory().items) {
         if (remaining <= 0) {
            break;
         }

         if (invStack.is(item)) {
            int toRemove = Math.min(remaining, invStack.getCount());
            invStack.shrink(toRemove);
            remaining -= toRemove;
         }
      }
   }
}
