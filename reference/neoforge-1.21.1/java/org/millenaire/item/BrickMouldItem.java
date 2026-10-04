package org.millenaire.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.BlockWetBrick;
import org.millenaire.block.ModBlocks;

public class BrickMouldItem extends Item {
   public BrickMouldItem(Properties properties) {
      super(properties);
   }

   public InteractionResult useOn(UseOnContext ctx) {
      Level level = ctx.getLevel();
      BlockPos pos = ctx.getClickedPos();
      Direction side = ctx.getClickedFace();
      Player player = ctx.getPlayer();
      ItemStack stack = ctx.getItemInHand();
      if (player == null) {
         return InteractionResult.PASS;
      }

      BlockState clickedState = level.getBlockState(pos);
      if (clickedState.is(Blocks.SNOW)) {
         side = Direction.DOWN;
      } else {
         pos = pos.relative(side);
      }

      if (!level.getBlockState(pos).isAir()) {
         return InteractionResult.PASS;
      }

      if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
         return InteractionResult.PASS;
      }

      int damage = stack.getDamageValue();
      if (damage % 4 == 0) {
         boolean hasDirt = countItem(player, Blocks.DIRT.asItem()) > 0;
         boolean hasSand = countItem(player, Blocks.SAND.asItem()) > 0;
         if (!hasDirt || !hasSand) {
            if (!level.isClientSide()) {
               player.displayClientMessage(Component.translatable("ui.millenaire.brickinstructions"), false);
            }

            return InteractionResult.PASS;
         }

         removeItem(player, Blocks.DIRT.asItem(), 1);
         removeItem(player, Blocks.SAND.asItem(), 1);
      }

      level.setBlock(pos, ((BlockWetBrick)ModBlocks.WET_BRICK.get()).defaultBlockState(), 3);
      stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
      return InteractionResult.SUCCESS;
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
