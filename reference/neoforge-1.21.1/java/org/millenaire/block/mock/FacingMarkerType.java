package org.millenaire.block.mock;

import net.minecraft.util.StringRepresentable;

public enum FacingMarkerType implements StringRepresentable {
   FURNACE("furnace", "furnace"),
   SIGN_POS("sign_pos", "signPos");

   private final String serializedName;
   private final String specialPointType;

   FacingMarkerType(String serializedName, String specialPointType) {
      this.serializedName = serializedName;
      this.specialPointType = specialPointType;
   }

   public String getSerializedName() {
      return this.serializedName;
   }

   public String specialPointType() {
      return this.specialPointType;
   }

   public static FacingMarkerType byName(String name) {
      for (FacingMarkerType type : values()) {
         if (type.serializedName.equals(name)) {
            return type;
         }
      }

      return FURNACE;
   }
}
