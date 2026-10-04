package org.millenaire.building;

import com.mojang.logging.LogUtils;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.millenaire.tag.ModTags;
import org.slf4j.Logger;

public final class HearthTemplateSanitizer {
   private static final Logger LOGGER = LogUtils.getLogger();

   private HearthTemplateSanitizer() {
   }

   public static boolean sanitize(CompoundTag templateNbt, Provider lookup) {
      return sanitizeWithPredicate(templateNbt, HearthTemplateSanitizer::isTaggedHearthBlock);
   }

   static boolean sanitizeWithPredicate(CompoundTag templateNbt, Predicate<Block> isHearth) {
      if (templateNbt == null) {
         return false;
      }

      boolean anyMutation = false;
      if (templateNbt.contains("palette", 9)) {
         anyMutation |= sanitizePaletteList(templateNbt.getList("palette", 10), isHearth);
      }

      if (templateNbt.contains("palettes", 9)) {
         ListTag palettes = templateNbt.getList("palettes", 9);

         for (int i = 0; i < palettes.size(); i++) {
            anyMutation |= sanitizePaletteList(palettes.getList(i), isHearth);
         }
      }

      return anyMutation;
   }

   private static boolean sanitizePaletteList(ListTag palette, Predicate<Block> isHearth) {
      if (palette == null) {
         return false;
      }

      boolean mutated = false;

      for (int i = 0; i < palette.size(); i++) {
         CompoundTag entry = palette.getCompound(i);
         String name = entry.getString("Name");
         if (!name.isEmpty()) {
            ResourceLocation blockId = ResourceLocation.tryParse(name);
            if (blockId != null) {
               Block block = (Block)BuiltInRegistries.BLOCK.get(blockId);
               if (block != null && isHearth.test(block)) {
                  boolean propsCreated = false;
                  CompoundTag props;
                  if (entry.contains("Properties", 10)) {
                     props = entry.getCompound("Properties");
                  } else {
                     props = new CompoundTag();
                     entry.put("Properties", props);
                     propsCreated = true;
                  }

                  String currentLit = props.getString("lit");
                  if (!"false".equals(currentLit)) {
                     props.putString("lit", "false");
                     mutated = true;
                     LOGGER.debug("HearthTemplateSanitizer: forced lit=false on palette entry {}", name);
                  } else if (propsCreated) {
                     mutated = true;
                  }
               }
            }
         }
      }

      return mutated;
   }

   private static boolean isTaggedHearthBlock(Block block) {
      return block.builtInRegistryHolder().is(ModTags.Blocks.HEARTH_BLOCKS);
   }
}
