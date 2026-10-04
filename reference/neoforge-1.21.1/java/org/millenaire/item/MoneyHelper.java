package org.millenaire.item;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.advancement.MillAdvancements;
import org.slf4j.Logger;

public final class MoneyHelper {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int ARGENT_VALUE = 64;
   public static final int OR_VALUE = 4096;

   private MoneyHelper() {
   }

   public static int getTotalDeniers(Inventory inventory) {
      int total = 0;

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty()) {
            total += valueOfStack(stack);
            if (stack.getItem() instanceof PurseItem) {
               total += PurseItem.getTotalDeniers(stack);
            }
         }
      }

      return total;
   }

   public static boolean removeDeniers(Inventory inventory, int amount) {
      if (amount <= 0) {
         return true;
      }

      int available = getTotalDeniers(inventory);
      if (available < amount) {
         return false;
      }

      ItemStack purse = findPurse(inventory);
      if (purse != null) {
         int looseCoins = removeLooseCoins(inventory);
         int purseContent = PurseItem.getTotalDeniers(purse);
         int finalAmount = looseCoins + purseContent - amount;
         PurseItem.setDeniersFromTotal(purse, Math.max(0, finalAmount));
      } else {
         int totalRemoved = removeAllCoins(inventory);
         int change = totalRemoved - amount;
         if (change > 0) {
            addLooseCoins(inventory, change);
         }
      }

      return true;
   }

   public static void addDeniers(Inventory inventory, int amount, @Nullable Player player) {
      addDeniers(inventory, amount);
      if (player instanceof ServerPlayer sp && getTotalDeniers(inventory) >= 4096) {
         MillAdvancements.grant(sp, MillAdvancements.CRESUS);
      }
   }

   public static void consolidateCoins(Inventory inventory) {
      ItemStack purse = findPurse(inventory);
      if (purse != null) {
         int looseCoins = removeLooseCoins(inventory);
         if (looseCoins > 0) {
            int purseContent = PurseItem.getTotalDeniers(purse);
            PurseItem.setDeniersFromTotal(purse, purseContent + looseCoins);
         }
      } else {
         int existingCoins = removeLooseCoins(inventory);
         if (existingCoins > 0) {
            addLooseCoins(inventory, existingCoins);
         }
      }
   }

   public static void addDeniers(Inventory inventory, int amount) {
      if (amount > 0) {
         ItemStack purse = findPurse(inventory);
         if (purse != null) {
            int looseCoins = removeLooseCoins(inventory);
            int purseContent = PurseItem.getTotalDeniers(purse);
            int finalAmount = looseCoins + purseContent + amount;
            PurseItem.setDeniersFromTotal(purse, finalAmount);
         } else {
            int existingCoins = removeLooseCoins(inventory);
            addLooseCoins(inventory, existingCoins + amount);
         }
      }
   }

   static void addLooseCoins(Inventory inventory, int amount) {
      if (amount > 0) {
         int remaining = amount;
         int gold = remaining / 4096;
         remaining %= 4096;
         int silver = remaining / 64;
         remaining %= 64;
         int bronze = remaining;
         addStacks(inventory, (Item)ModItems.DENIER_OR.get(), gold);
         addStacks(inventory, (Item)ModItems.DENIER_ARGENT.get(), silver);
         addStacks(inventory, (Item)ModItems.DENIER.get(), bronze);
      }
   }

   public static String formatPrice(int deniers) {
      int or = deniers / 4096;
      int rest = deniers % 4096;
      int argent = rest / 64;
      int bronze = rest % 64;
      StringBuilder sb = new StringBuilder();
      if (or > 0) {
         sb.append(or).append("o ");
      }

      if (argent > 0) {
         sb.append(argent).append("a ");
      }

      if (bronze > 0 || sb.isEmpty()) {
         sb.append(bronze).append("d");
      }

      return sb.toString().trim();
   }

   static MoneyHelper.Denomination toDenomination(int totalBronze) {
      int or = totalBronze / 4096;
      int rest = totalBronze % 4096;
      int argent = rest / 64;
      int bronze = rest % 64;
      return new MoneyHelper.Denomination(bronze, argent, or);
   }

   private static ItemStack findPurse(Inventory inventory) {
      ItemStack firstPurse = null;
      int firstPurseTotal = 0;

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty() && stack.getItem() instanceof PurseItem) {
            if (firstPurse == null) {
               firstPurse = stack;
               firstPurseTotal = PurseItem.getTotalDeniers(stack);
            } else {
               firstPurseTotal += PurseItem.getTotalDeniers(stack);
               PurseItem.clearDeniers(stack);
            }
         }
      }

      if (firstPurse != null && firstPurseTotal > 0) {
         PurseItem.setDeniersFromTotal(firstPurse, firstPurseTotal);
      }

      return firstPurse;
   }

   private static int valueOfStack(ItemStack stack) {
      Item item = stack.getItem();
      if (item == ModItems.DENIER.get()) {
         return stack.getCount();
      } else if (item == ModItems.DENIER_ARGENT.get()) {
         return stack.getCount() * 64;
      } else {
         return item == ModItems.DENIER_OR.get() ? stack.getCount() * 4096 : 0;
      }
   }

   private static int removeLooseCoins(Inventory inventory) {
      int total = 0;

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty()) {
            int value = valueOfStack(stack);
            if (value > 0) {
               total += value;
               inventory.setItem(i, ItemStack.EMPTY);
            }
         }
      }

      return total;
   }

   private static int removeAllCoins(Inventory inventory) {
      int total = 0;

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty()) {
            int value = valueOfStack(stack);
            if (value > 0) {
               total += value;
               inventory.setItem(i, ItemStack.EMPTY);
            }

            if (stack.getItem() instanceof PurseItem) {
               int purseValue = PurseItem.getTotalDeniers(stack);
               if (purseValue > 0) {
                  total += purseValue;
                  PurseItem.clearDeniers(stack);
               }
            }
         }
      }

      return total;
   }

   private static void addStacks(Inventory inventory, Item item, int count) {
      while (count > 0) {
         int stackSize = Math.min(count, 64);
         ItemStack stack = new ItemStack(item, stackSize);
         if (!inventory.add(stack)) {
            LOGGER.warn("[Millénaire] Could not add {} x{} to inventory — inventory full, coins lost!", item, stack.getCount());
         }

         count -= stackSize;
      }
   }

   record Denomination(int bronze, int argent, int or) {
      int totalBronze() {
         return this.bronze + this.argent * 64 + this.or * 4096;
      }
   }
}
