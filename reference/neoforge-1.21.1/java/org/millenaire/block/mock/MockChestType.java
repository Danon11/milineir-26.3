package org.millenaire.block.mock;

import net.minecraft.util.StringRepresentable;

public enum MockChestType implements StringRepresentable {
   MAIN("main"),
   LOCKED("locked");

   private final String serializedName;

   MockChestType(String serializedName) {
      this.serializedName = serializedName;
   }

   public String getSerializedName() {
      return this.serializedName;
   }
}
