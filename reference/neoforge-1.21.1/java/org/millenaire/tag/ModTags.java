package org.millenaire.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
   private ModTags() {
   }

   public static final class Blocks {
      public static final TagKey<Block> DEPENDENT_BLOCKS = TagKey.create(
         Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("millenaire", "dependent_blocks")
      );
      public static final TagKey<Block> DEPENDENT_VEGETATION = TagKey.create(
         Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("millenaire", "dependent_vegetation")
      );
      public static final TagKey<Block> FORBIDDEN_EXCEPTIONS = TagKey.create(
         Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("millenaire", "forbidden_exceptions")
      );
      public static final TagKey<Block> ARTIFICIAL_BLOCKS = TagKey.create(
         Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("millenaire", "artificial_blocks")
      );
      public static final TagKey<Block> HEARTH_BLOCKS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("millenaire", "hearth_blocks"));

      private Blocks() {
      }
   }

   public static final class Items {
      public static final TagKey<Item> CULTURE_WEAPONS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("millenaire", "culture_weapons"));

      private Items() {
      }
   }
}
