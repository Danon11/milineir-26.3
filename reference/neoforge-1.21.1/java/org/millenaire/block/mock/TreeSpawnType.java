package org.millenaire.block.mock;

import net.minecraft.util.StringRepresentable;

public enum TreeSpawnType implements StringRepresentable {
   OAK("oak"),
   PINE("pine"),
   BIRCH("birch"),
   JUNGLE("jungle"),
   ACACIA("acacia"),
   DARK_OAK("dark_oak"),
   APPLE("apple"),
   OLIVE("olive"),
   PISTACHIO("pistachio");

   private final String serializedName;

   TreeSpawnType(String serializedName) {
      this.serializedName = serializedName;
   }

   public String getSerializedName() {
      return this.serializedName;
   }
}
