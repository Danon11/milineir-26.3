package org.millenaire.village;

import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public final class VillageDiplomacyHelper {
   private VillageDiplomacyHelper() {
   }

   public static void performNightlyDiplomacyDrift(ServerLevel level, Village village) {
      if (!village.isPlayerControlled()) {
         if (!village.isLoneBuilding()) {
            Random rng = ThreadLocalRandom.current();

            for (Entry<VillageId, Integer> entry : village.getRelations().entrySet()) {
               if (rng.nextInt(10) == 0) {
                  int relation = entry.getValue();
                  VillageId otherId = entry.getKey();
                  boolean improve;
                  if (relation < -90) {
                     improve = false;
                  } else if (relation < -50) {
                     improve = rng.nextInt(100) < 30;
                  } else if (relation < 0) {
                     improve = rng.nextInt(100) < 40;
                  } else if (relation > 90) {
                     improve = true;
                  } else if (relation > 50) {
                     improve = rng.nextInt(100) < 70;
                  } else {
                     improve = rng.nextInt(100) < 60;
                  }

                  int change = 10 + rng.nextInt(10);
                  if (improve) {
                     if (relation < 100) {
                        village.adjustRelationSymmetric(level, otherId, change, false);
                        notifyNearbyPlayersDiplomacy(level, village, otherId, true);
                     }
                  } else if (relation > -100) {
                     village.adjustRelationSymmetric(level, otherId, -change, false);
                     notifyNearbyPlayersDiplomacy(level, village, otherId, false);
                  }
               }
            }
         }
      }
   }

   static void notifyNearbyPlayersDiplomacy(ServerLevel level, Village village, VillageId otherId, boolean improving) {
      Village other = Village.resolve(level, otherId);
      if (other != null) {
         String thisName = village.getVillageName() != null ? village.getVillageName() : village.getVillageTypeId().getPath();
         String otherName = other.getVillageName() != null ? other.getVillageName() : other.getVillageTypeId().getPath();
         int newRelation = village.getRelation(otherId);
         String relationKey = VillageRelations.getRelationKey(newRelation);
         Component msg;
         ChatFormatting color;
         if (improving) {
            msg = Component.translatable("millenaire.diplomacy.improving", new Object[]{thisName, otherName, Component.translatable(relationKey)});
            color = ChatFormatting.GREEN;
         } else {
            msg = Component.translatable("millenaire.diplomacy.worsening", new Object[]{thisName, otherName, Component.translatable(relationKey)});
            color = ChatFormatting.GOLD;
         }

         int radius = Village.getKeepActiveRadius();
         AABB area = new AABB(
            village.getCenter().getX() - radius,
            village.getCenter().getY() - 256,
            village.getCenter().getZ() - radius,
            village.getCenter().getX() + radius,
            village.getCenter().getY() + 256,
            village.getCenter().getZ() + radius
         );

         for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            player.sendSystemMessage(msg.copy().withStyle(color));
         }
      }
   }

   public static void regenerateDiplomacyPointsForPlayers(ServerLevel level, Village village) {
      if (!village.isPlayerControlled()) {
         if (!village.isLoneBuilding()) {
            PlayerCultureReputation rep = PlayerCultureReputation.get(level);

            for (ServerPlayer player : level.players()) {
               rep.regenerateDiplomacyPoints(player.getUUID(), village.getId());
            }
         }
      }
   }
}
