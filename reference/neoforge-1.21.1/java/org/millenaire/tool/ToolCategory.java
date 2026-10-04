package org.millenaire.tool;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public record ToolCategory(String id, List<ToolCategory.ToolEntry> items) {
   @Nullable
   public ToolCategory.ToolEntry getBestOwned(Predicate<Item> hasItem) {
      for (ToolCategory.ToolEntry entry : this.items) {
         if (entry.item != null && hasItem.test(entry.item)) {
            return entry;
         }
      }

      return null;
   }

   public float getBestDestroySpeed(Predicate<Item> hasItem, BlockState testBlock, Item fallback) {
      ToolCategory.ToolEntry best = this.getBestOwned(hasItem);
      Item tool = best != null && best.item() != null ? best.item() : fallback;
      return new ItemStack(tool).getDestroySpeed(testBlock);
   }

   @Nullable
   public ToolCategory.ToolEntry findUpgrade(@Nullable ToolCategory.ToolEntry bestOwned, Predicate<Item> hasStock) {
      for (ToolCategory.ToolEntry entry : this.items) {
         if (entry.item != null) {
            if (bestOwned != null && entry.equals(bestOwned)) {
               break;
            }

            if (hasStock.test(entry.item)) {
               return entry;
            }
         }
      }

      return null;
   }

   public record ToolEntry(String itemId, @Nullable Item item, int priority) {
   }
}
