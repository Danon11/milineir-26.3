package org.millenaire.item;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class BlockHelper {
   private BlockHelper() {
   }

   @Nullable
   public static Block resolve(String blockId) {
      return blockId != null && !blockId.isEmpty() ? (Block)BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId)).orElse(null) : null;
   }
}
