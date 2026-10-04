package org.millenaire.village;

import net.minecraft.ChatFormatting;

public final class VillageRelations {
   public static final int MAX = 100;
   public static final int EXCELLENT = 90;
   public static final int VERY_GOOD = 70;
   public static final int GOOD = 50;
   public static final int DECENT = 30;
   public static final int FAIR = 10;
   public static final int NEUTRAL = 0;
   public static final int CHILLY = -10;
   public static final int BAD = -30;
   public static final int VERY_BAD = -50;
   public static final int ATROCIOUS = -70;
   public static final int OPEN_CONFLICT = -90;
   public static final int MIN = -100;

   private VillageRelations() {
   }

   public static String getRelationKey(int relation) {
      if (relation >= 90) {
         return "relation.millenaire.excellent";
      } else if (relation >= 70) {
         return "relation.millenaire.verygood";
      } else if (relation >= 50) {
         return "relation.millenaire.good";
      } else if (relation >= 30) {
         return "relation.millenaire.decent";
      } else if (relation >= 10) {
         return "relation.millenaire.fair";
      } else if (relation <= -90) {
         return "relation.millenaire.openconflict";
      } else if (relation <= -70) {
         return "relation.millenaire.atrocious";
      } else if (relation <= -50) {
         return "relation.millenaire.verybad";
      } else if (relation <= -30) {
         return "relation.millenaire.bad";
      } else {
         return relation <= -10 ? "relation.millenaire.chilly" : "relation.millenaire.neutral";
      }
   }

   public static ChatFormatting getRelationColor(int relation) {
      if (relation >= 50) {
         return ChatFormatting.GREEN;
      } else if (relation >= 0) {
         return ChatFormatting.YELLOW;
      } else {
         return relation > -50 ? ChatFormatting.GOLD : ChatFormatting.RED;
      }
   }
}
