package org.millenaire.commerce;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.item.ItemHelper;

public record TradeGood(
   String id,
   String item,
   int sellingPrice,
   int buyingPrice,
   int reservedQuantity,
   int targetQuantity,
   boolean autoGenerate,
   int minReputation,
   String category,
   boolean travelBookDisplay,
   int foreignMerchantPrice
) {
   public boolean canSell() {
      return this.sellingPrice > 0;
   }

   public boolean canBuy() {
      return this.buyingPrice > 0;
   }

   public boolean isTag() {
      return this.item.startsWith("#");
   }

   public ResourceLocation itemLocation() {
      return ResourceLocation.parse(this.isTag() ? this.item.substring(1) : this.item);
   }

   public boolean matchesItem(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else if (this.isTag()) {
         TagKey<Item> tag = TagKey.create(Registries.ITEM, this.itemLocation());
         return stack.is(tag);
      } else {
         return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(this.itemLocation());
      }
   }

   @Nullable
   public Item resolveItem() {
      return ItemHelper.resolve(this.item);
   }
}
