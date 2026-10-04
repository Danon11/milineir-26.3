package org.millenaire.block;

public enum PathTier {
   RUSTIC(1.1F, 0.4F),
   PAVED(1.15F, 0.2F),
   STONE(1.2F, 0.0F);

   private final float speedFactor;
   private final float preferenceMalus;

   PathTier(float speedFactor, float preferenceMalus) {
      this.speedFactor = speedFactor;
      this.preferenceMalus = preferenceMalus;
   }

   public float speedFactor() {
      return this.speedFactor;
   }

   public float preferenceMalus() {
      return this.preferenceMalus;
   }
}
