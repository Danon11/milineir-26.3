package org.millenaire.test.terrain;

public enum TestBuilding {
   CUBE_5(5, 5, 5, 0),
   CUBE_6(6, 6, 6, 0),
   SLAB_WIDE(15, 3, 15, 0),
   TOWER(3, 3, 12, 0),
   PARITY_8x6(8, 5, 6, 0),
   CUBE_FLOOR(5, 5, 5, -1),
   CUBE_CELLAR(5, 5, 5, -3),
   CUBE_DEEP_CELLAR(5, 8, 5, -6);

   private final int width;
   private final int height;
   private final int depth;
   private final int groundLevel;

   TestBuilding(int width, int height, int depth, int groundLevel) {
      this.width = width;
      this.height = height;
      this.depth = depth;
      this.groundLevel = groundLevel;
   }

   public int width() {
      return this.width;
   }

   public int height() {
      return this.height;
   }

   public int depth() {
      return this.depth;
   }

   public int groundLevel() {
      return this.groundLevel;
   }

   public boolean hasBasement() {
      return this.groundLevel < 0;
   }
}
