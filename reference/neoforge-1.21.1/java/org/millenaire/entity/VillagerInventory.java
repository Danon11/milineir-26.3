package org.millenaire.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.millenaire.item.ItemHelper;

public class VillagerInventory {
   private final Map<Item, Integer> items = new HashMap<>();
   private int modCount;

   public int modCount() {
      return this.modCount;
   }

   public void add(Item item, int count) {
      if (count > 0) {
         this.items.merge(item, count, Integer::sum);
         this.modCount++;
      }
   }

   public int remove(Item item, int count) {
      if (count <= 0) {
         return 0;
      }

      int current = this.items.getOrDefault(item, 0);
      if (current <= 0) {
         return 0;
      }

      int removed = Math.min(current, count);
      int remaining = current - removed;
      if (remaining <= 0) {
         this.items.remove(item);
      } else {
         this.items.put(item, remaining);
      }

      this.modCount++;
      return removed;
   }

   public int getCount(Item item) {
      return this.items.getOrDefault(item, 0);
   }

   public boolean has(Item item, int count) {
      return this.getCount(item) >= count;
   }

   public int getCountByTag(TagKey<Item> tag) {
      int total = 0;

      for (Entry<Item, Integer> entry : this.items.entrySet()) {
         if (entry.getKey().builtInRegistryHolder().is(tag)) {
            total += entry.getValue();
         }
      }

      return total;
   }

   public boolean hasByTag(TagKey<Item> tag, int count) {
      return this.getCountByTag(tag) >= count;
   }

   public boolean isEmpty() {
      return this.items.isEmpty();
   }

   public void clear() {
      this.items.clear();
      this.modCount++;
   }

   public Map<Item, Integer> getAll() {
      return Collections.unmodifiableMap(new HashMap<>(this.items));
   }

   public void save(CompoundTag tag) {
      ListTag list = new ListTag();

      for (Entry<Item, Integer> entry : this.items.entrySet()) {
         ResourceLocation key = BuiltInRegistries.ITEM.getKey(entry.getKey());
         if (key != null) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("item", key.toString());
            itemTag.putInt("count", entry.getValue());
            list.add(itemTag);
         }
      }

      tag.put("carriedItems", list);
   }

   public void load(CompoundTag tag) {
      this.items.clear();
      this.modCount++;
      if (tag.contains("carriedItems")) {
         ListTag list = tag.getList("carriedItems", 10);

         for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            Item item = ItemHelper.resolve(itemTag.getString("item"));
            if (item != null) {
               int count = itemTag.getInt("count");
               if (count > 0) {
                  this.items.put(item, count);
               }
            }
         }
      }
   }
}
