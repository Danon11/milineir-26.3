package org.millenaire.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class PurseItem extends Item {
   private static final String TAG_DENIER = "denier";
   private static final String TAG_DENIER_ARGENT = "denier_argent";
   private static final String TAG_DENIER_OR = "denier_or";

   public PurseItem(Properties properties) {
      super(properties);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack purse = player.getItemInHand(hand);
      if (level.isClientSide()) {
         return InteractionResultHolder.success(purse);
      }

      int total = getTotalDeniers(purse);
      if (total > 0) {
         this.removeDeniersFromPurse(purse, player);
      } else {
         this.storeDeniersInPurse(purse, player);
      }

      return InteractionResultHolder.success(purse);
   }

   public Component getName(ItemStack stack) {
      int total = getTotalDeniers(stack);
      if (total == 0) {
         return super.getName(stack);
      }

      CompoundTag tag = getTag(stack);
      int or = tag.getInt("denier_or");
      int argent = tag.getInt("denier_argent");
      int bronze = tag.getInt("denier");
      MutableComponent label = Component.translatable(this.getDescriptionId());
      StringBuilder sb = new StringBuilder(" : ");
      if (or > 0) {
         sb.append(or).append("o ");
      }

      if (argent > 0) {
         sb.append(argent).append("a ");
      }

      if (bronze > 0 || or == 0 && argent == 0) {
         sb.append(bronze).append("d");
      }

      return label.append(Component.literal(sb.toString().trim()).withStyle(Style.EMPTY.withColor(16766720)));
   }

   private void storeDeniersInPurse(ItemStack purse, Player player) {
      Inventory inv = player.getInventory();
      int bronze = countAndRemoveItem(inv, (Item)ModItems.DENIER.get());
      int argent = countAndRemoveItem(inv, (Item)ModItems.DENIER_ARGENT.get());
      int or = countAndRemoveItem(inv, (Item)ModItems.DENIER_OR.get());
      CompoundTag tag = getTag(purse);
      bronze += tag.getInt("denier");
      argent += tag.getInt("denier_argent");
      or += tag.getInt("denier_or");
      int totalBronze = bronze + argent * 64 + or * 4096;
      or = totalBronze / 4096;
      totalBronze %= 4096;
      argent = totalBronze / 64;
      bronze = totalBronze % 64;
      setTag(purse, bronze, argent, or);
   }

   private void removeDeniersFromPurse(ItemStack purse, Player player) {
      int totalInPurse = getTotalDeniers(purse);
      int before = MoneyHelper.getTotalDeniers(player.getInventory());
      MoneyHelper.addLooseCoins(player.getInventory(), totalInPurse);
      int after = MoneyHelper.getTotalDeniers(player.getInventory());
      int actuallyAdded = after - before;
      int remaining = totalInPurse - actuallyAdded;
      setDeniersFromTotal(purse, Math.max(0, remaining));
   }

   private static CompoundTag getTag(ItemStack stack) {
      CustomData data = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      return data == null ? new CompoundTag() : data.copyTag();
   }

   private static void setTag(ItemStack stack, int bronze, int argent, int or) {
      CompoundTag tag = new CompoundTag();
      tag.putInt("denier", bronze);
      tag.putInt("denier_argent", argent);
      tag.putInt("denier_or", or);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
   }

   public static void clearDeniers(ItemStack stack) {
      setTag(stack, 0, 0, 0);
   }

   public static void setDeniersFromTotal(ItemStack stack, int totalBronze) {
      int or = totalBronze / 4096;
      int rest = totalBronze % 4096;
      int argent = rest / 64;
      int bronze = rest % 64;
      setTag(stack, bronze, argent, or);
   }

   public static int getTotalDeniers(ItemStack stack) {
      CompoundTag tag = getTag(stack);
      return tag.getInt("denier") + tag.getInt("denier_argent") * 64 + tag.getInt("denier_or") * 4096;
   }

   private static int countAndRemoveItem(Inventory inv, Item item) {
      int total = 0;

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (!stack.isEmpty() && stack.is(item)) {
            total += stack.getCount();
            inv.setItem(i, ItemStack.EMPTY);
         }
      }

      return total;
   }
}
