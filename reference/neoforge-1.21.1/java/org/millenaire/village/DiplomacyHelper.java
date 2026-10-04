package org.millenaire.village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DiplomacyHelper {
   private static final int MAX_REP = 32768;

   private DiplomacyHelper() {
   }

   public static void performDiplomacy(ServerLevel level, ServerPlayer player, Village village, VillageId targetVillageId, boolean isPraise) {
      Village target = Village.resolve(level, targetVillageId);
      if (target != null) {
         if (!target.getId().equals(village.getId())) {
            PlayerCultureReputation rep = PlayerCultureReputation.get(level);
            int playerRep = village.getCombinedReputation(level, player.getUUID());
            if (playerRep > 0) {
               if (rep.getDiplomacyPoints(player.getUUID(), village.getId()) > 0) {
                  int reputation = Math.min(playerRep, 32768);
                  float effect = isPraise ? 10.0F : -10.0F;
                  float coeff = (float)((Math.log(reputation) / Math.log(32768.0) * 2.0 + reputation / 32768) / 3.0);
                  effect *= coeff;
                  effect *= (80 + level.random.nextInt(40)) / 100.0F;
                  village.adjustRelationSymmetric(level, targetVillageId, (int)effect, false);
                  rep.consumeDiplomacyPoint(player.getUUID(), village.getId());
               }
            }
         }
      }
   }
}
