package org.millenaire.language;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.PlayerCultureReputation;

public final class LanguageHelper {
   private LanguageHelper() {
   }

   public static boolean canReadBuildingNames(ServerPlayer player, ResourceLocation cultureId) {
      return !MillenaireServerConfig.SERVER.languageLearning.get() ? true : getLanguageKnowledge(player, cultureId) >= 100;
   }

   public static boolean canReadVillagerNames(ServerPlayer player, ResourceLocation cultureId) {
      return !MillenaireServerConfig.SERVER.languageLearning.get() ? true : getLanguageKnowledge(player, cultureId) >= 200;
   }

   public static boolean canReadDialogues(ServerPlayer player, ResourceLocation cultureId) {
      return !MillenaireServerConfig.SERVER.languageLearning.get() ? true : getLanguageKnowledge(player, cultureId) >= 500;
   }

   public static int getLanguageKnowledge(ServerPlayer player, ResourceLocation cultureId) {
      return player.level() instanceof ServerLevel sl ? PlayerCultureReputation.get(sl).getLanguageKnowledge(player.getUUID(), cultureId) : 0;
   }

   public static PlayerCultureReputation.LanguageLevel getLanguageLevel(ServerPlayer player, ResourceLocation cultureId) {
      return player.level() instanceof ServerLevel sl
         ? PlayerCultureReputation.get(sl).getLanguageLevel(player.getUUID(), cultureId)
         : PlayerCultureReputation.LanguageLevel.MINIMAL;
   }
}
