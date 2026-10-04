package org.millenaire;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DisplayUtils {
   private static final String GOAL_TRANSLATION_PREFIX = "goal.millenaire.";

   private DisplayUtils() {
   }

   public static String t(String key) {
      return Component.translatable(key).getString();
   }

   public static String t(String key, Object... args) {
      return Component.translatable(key, args).getString();
   }

   public static String resolveRoleName(ResourceLocation typeId) {
      String key = resolveRoleKey(typeId);
      String translated = Component.translatable(key).getString();
      String qualifiedRole = typeId.getPath().replace('/', '_');
      return translated.equals(key) ? qualifiedRole : translated;
   }

   public static String resolveRoleKey(ResourceLocation typeId) {
      String path = typeId.getPath();
      String qualifiedRole = path.replace('/', '_');
      return "role.millenaire." + qualifiedRole;
   }

   public static boolean isGoalTranslationKey(@Nullable String goalLabel) {
      return goalLabel != null && goalLabel.startsWith("goal.millenaire.");
   }

   public static String resolveGoalLabel(@Nullable String goalLabel) {
      if (goalLabel != null && !goalLabel.isEmpty()) {
         return isGoalTranslationKey(goalLabel) ? Component.translatable(goalLabel).getString() : goalLabel;
      } else {
         return "";
      }
   }

   public static Component resolveGoalLabelComponent(String goalLabel) {
      return isGoalTranslationKey(goalLabel) ? Component.translatable(goalLabel) : Component.literal(goalLabel);
   }
}
