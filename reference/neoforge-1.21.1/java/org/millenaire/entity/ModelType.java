package org.millenaire.entity;

public enum ModelType {
   MALE("male"),
   FEMALE_SYM("female_symmetrical"),
   FEMALE_ASYM("female_asymmetrical");

   private final String serializedName;

   ModelType(String serializedName) {
      this.serializedName = serializedName;
   }

   public String getSerializedName() {
      return this.serializedName;
   }

   public static ModelType fromString(String name) {
      for (ModelType type : values()) {
         if (type.serializedName.equals(name)) {
            return type;
         }
      }

      return MALE;
   }

   public byte toByte() {
      return (byte)this.ordinal();
   }

   public static ModelType fromByte(byte b) {
      ModelType[] values = values();
      return b >= 0 && b < values.length ? values[b] : MALE;
   }
}
