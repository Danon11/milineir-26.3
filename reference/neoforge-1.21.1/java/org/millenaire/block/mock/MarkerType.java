package org.millenaire.block.mock;

import net.minecraft.util.StringRepresentable;

public enum MarkerType implements StringRepresentable {
   SLEEPING_POS("sleeping_pos", "sleepingPos"),
   SELLING_POS("selling_pos", "sellingPos"),
   CRAFTING_POS("crafting_pos", "craftingPos"),
   DEFENDING_POS("defending_pos", "defendingPos"),
   SHELTER_POS("shelter_pos", "shelterPos"),
   PATH_START_POS("path_start_pos", "pathStartPos"),
   LEISURE_POS("leisure_pos", "leisurePos"),
   STALL("stall", "stall"),
   FISHING_SPOT("fishing_spot", "fishingSpot"),
   PRESERVE_GROUND("preserve_ground", "preserve_ground"),
   PRESERVE_GROUND_DEPTH("preserve_ground_depth", "preserve_ground"),
   PRESERVE_GROUND_ALLBUTTREES("preserve_ground_allbuttrees", "preserve_ground"),
   PRESERVE_GROUND_GRASS("preserve_ground_grass", "preserve_ground"),
   TORCH("torch", "torchGuess"),
   HEALING_SPOT("healing_spot", "healingSpot"),
   BRICK_SPOT("brick_spot", "brick_spot"),
   SILKWORM_BLOCK("silkworm_block", "silkwormBlock"),
   SNAIL_SOIL_BLOCK("snail_soil_block", "snailSoilBlock"),
   CACAO_SPOT("cacao_spot", "cacaoSpot"),
   FIREPLACE("fireplace", "fireplace");

   private final String serializedName;
   private final String specialPointType;

   MarkerType(String serializedName, String specialPointType) {
      this.serializedName = serializedName;
      this.specialPointType = specialPointType;
   }

   public String getSerializedName() {
      return this.serializedName;
   }

   public String specialPointType() {
      return this.specialPointType;
   }

   public String specialPointSubtype() {
      return switch (this) {
         case PRESERVE_GROUND -> "surface";
         case PRESERVE_GROUND_DEPTH -> "depth";
         case PRESERVE_GROUND_ALLBUTTREES -> "allbuttrees";
         case PRESERVE_GROUND_GRASS -> "grass";
         default -> null;
      };
   }

   public boolean hasSolidCollision() {
      return switch (this) {
         case PRESERVE_GROUND, PRESERVE_GROUND_DEPTH, PRESERVE_GROUND_ALLBUTTREES, PRESERVE_GROUND_GRASS, BRICK_SPOT, SILKWORM_BLOCK, SNAIL_SOIL_BLOCK -> true;
         default -> false;
      };
   }
}
