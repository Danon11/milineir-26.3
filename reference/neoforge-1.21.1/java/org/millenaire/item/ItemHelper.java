package org.millenaire.item;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ItemHelper {
   private static final Map<String, Item> CACHE = new ConcurrentHashMap<>();

   private ItemHelper() {
   }

   @Nullable
   public static Item resolve(String itemId) {
      if (itemId == null || itemId.isEmpty()) {
         return null;
      }

      if (itemId.startsWith("#")) {
         ResourceLocation tagId = ResourceLocation.parse(itemId.substring(1));
         TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
         Optional<Named<Item>> entries = BuiltInRegistries.ITEM.getTag(tag);
         if (entries.isPresent()) {
            Iterator<Holder<Item>> it = entries.get().iterator();
            if (it.hasNext()) {
               return (Item)it.next().value();
            }
         }

         return null;
      } else {
         return CACHE.computeIfAbsent(itemId, id -> (Item)BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null));
      }
   }

   @Nullable
   public static Item resolve(ResourceLocation itemId) {
      return itemId == null ? null : CACHE.computeIfAbsent(itemId.toString(), id -> (Item)BuiltInRegistries.ITEM.getOptional(itemId).orElse(null));
   }

   public static void clearCache() {
      CACHE.clear();
   }
}
