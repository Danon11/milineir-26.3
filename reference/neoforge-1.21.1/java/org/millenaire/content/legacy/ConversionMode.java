package org.millenaire.content.legacy;

public enum ConversionMode {
   AUTO,
   CONVERT;

   public boolean isStrict() {
      return this == CONVERT;
   }
}
