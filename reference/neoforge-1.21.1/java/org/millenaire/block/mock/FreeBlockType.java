package org.millenaire.block.mock;

import net.minecraft.util.StringRepresentable;

public enum FreeBlockType implements StringRepresentable {
   STONE("stone"),
   SAND("sand"),
   GRAVEL("gravel"),
   SANDSTONE("sandstone"),
   WOOL("wool"),
   COBBLESTONE("cobblestone"),
   STONE_BRICK("stone_brick"),
   PAINTED_BRICK("painted_brick"),
   GRASS_BLOCK("grass_block");

   private final String serializedName;

   FreeBlockType(String serializedName) {
      this.serializedName = serializedName;
   }

   public String getSerializedName() {
      return this.serializedName;
   }
}
