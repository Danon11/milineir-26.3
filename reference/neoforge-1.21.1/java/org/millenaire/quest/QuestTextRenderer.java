package org.millenaire.quest;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.millenaire.world.VillageNotifier;
import org.slf4j.Logger;

public final class QuestTextRenderer {
   private static final Logger LOGGER = LogUtils.getLogger();

   private QuestTextRenderer() {
   }

   public static String playerLocale(ServerPlayer player) {
      String mcLocale = player.clientInformation().language();
      if (mcLocale == null || mcLocale.isEmpty()) {
         return "en";
      }

      if (QuestTextRegistry.hasLanguage(mcLocale)) {
         return mcLocale;
      }

      String prefix = mcLocale.length() >= 2 ? mcLocale.substring(0, 2) : mcLocale;
      return QuestTextRegistry.hasLanguage(prefix) ? prefix : "en";
   }

   public static String substitute(String text, QuestInstance questInstance, String playerName, ServerLevel overworld) {
      if (text == null) {
         return "";
      }

      QuestStep step = questInstance.getCurrentStep();
      if (step == null) {
         return text;
      }

      QuestInstanceVillager giverQiv = questInstance.getVillagers().get(step.villagerKey());
      if (giverQiv == null) {
         return text;
      }

      Village giverVillage = Village.resolve(overworld, new VillageId(giverQiv.getVillageId()));
      if (giverVillage == null) {
         return text;
      }

      BlockPos giverPos = giverVillage.getCenter();
      String s = text;
      VillageSavedData savedData = VillageSavedData.get(overworld);

      for (Entry<String, QuestInstanceVillager> entry : questInstance.getVillagers().entrySet()) {
         String key = entry.getKey();
         QuestInstanceVillager qiv = entry.getValue();
         Village th = Village.resolve(overworld, new VillageId(qiv.getVillageId()));
         if (th != null) {
            BlockPos thPos = th.getCenter();
            s = s.replace("$" + key + "_villagename$", getVillageQualifiedName(th));
            s = s.replace("$" + key + "_direction$", VillageNotifier.cardinalDirection(giverPos, thPos));
            s = s.replace("$" + key + "_tothedirection$", toTheDirection(giverPos, thPos));
            s = s.replace("$" + key + "_directionshort$", directionShort(giverPos, thPos));
            s = s.replace("$" + key + "_distance$", approximateDistanceLong(giverPos, thPos));
            s = s.replace("$" + key + "_distanceshort$", approximateDistanceShort(giverPos, thPos));
            VillagerRecord villager = findVillagerRecord(savedData, qiv.getVillagerId());
            if (villager != null) {
               s = s.replace("$" + key + "_villagername$", villager.getFirstName());
               s = s.replace("$" + key + "_villagerrole$", resolveGameOccupation(villager));
            }

            for (Entry<String, QuestInstanceVillager> entry2 : questInstance.getVillagers().entrySet()) {
               String key2 = entry2.getKey();
               QuestInstanceVillager qiv2 = entry2.getValue();
               Village th2 = Village.resolve(overworld, new VillageId(qiv2.getVillageId()));
               if (th2 != null) {
                  BlockPos th2Pos = th2.getCenter();
                  s = s.replace("$" + key + "_" + key2 + "_direction$", VillageNotifier.cardinalDirection(thPos, th2Pos));
                  s = s.replace("$" + key + "_" + key2 + "_directionshort$", directionShort(thPos, th2Pos));
                  s = s.replace("$" + key + "_" + key2 + "_distance$", approximateDistanceLong(thPos, th2Pos));
                  s = s.replace("$" + key + "_" + key2 + "_distanceshort$", approximateDistanceShort(thPos, th2Pos));
               } else {
                  s = s.replace("$" + key + "_" + key2 + "_direction$", "");
                  s = s.replace("$" + key + "_" + key2 + "_directionshort$", "");
                  s = s.replace("$" + key + "_" + key2 + "_distance$", "");
                  s = s.replace("$" + key + "_" + key2 + "_distanceshort$", "");
               }
            }
         }
      }

      return s.replace("$name", playerName);
   }

   public static String lookupText(String textKey, String locale, @Nullable String fallback) {
      String text = QuestTextRegistry.getText(locale, textKey);
      if (text != null) {
         return text;
      } else {
         return fallback != null && !fallback.isEmpty() ? fallback : "";
      }
   }

   private static String toTheDirection(BlockPos from, BlockPos to) {
      int xdist = to.getX() - from.getX();
      int zdist = to.getZ() - from.getZ();
      String prefix = "other.millenaire.tothe";
      String direction = computeDirectionKey(xdist, zdist, prefix);
      return Component.translatable(direction).getString();
   }

   private static String directionShort(BlockPos from, BlockPos to) {
      int xdist = to.getX() - from.getX();
      int zdist = to.getZ() - from.getZ();
      boolean diagonal = Math.abs(xdist) > Math.abs(zdist) * 0.6 && Math.abs(xdist) < Math.abs(zdist) * 1.4
         || Math.abs(zdist) > Math.abs(xdist) * 0.6 && Math.abs(zdist) < Math.abs(xdist) * 1.4;
      String direction;
      if (diagonal) {
         String ns = zdist > 0
            ? Component.translatable("other.millenaire.south_short").getString()
            : Component.translatable("other.millenaire.north_short").getString();
         String ew = xdist > 0
            ? Component.translatable("other.millenaire.east_short").getString()
            : Component.translatable("other.millenaire.west_short").getString();
         direction = ns + ew;
      } else if (Math.abs(xdist) > Math.abs(zdist)) {
         direction = xdist > 0
            ? Component.translatable("other.millenaire.east_short").getString()
            : Component.translatable("other.millenaire.west_short").getString();
      } else {
         direction = zdist > 0
            ? Component.translatable("other.millenaire.south_short").getString()
            : Component.translatable("other.millenaire.north_short").getString();
      }

      return direction;
   }

   private static String approximateDistanceLong(BlockPos from, BlockPos to) {
      int dist = VillageNotifier.horizontalDistance(from, to);
      if (dist < 950) {
         return dist / 100 * 100 + " " + Component.translatable("other.millenaire.metre").getString();
      }

      dist = (dist + 500) / 1000;
      return dist % 2 == 0
         ? dist / 2 + " " + Component.translatable("other.millenaire.kilometre").getString()
         : (dist - 1) / 2
            + Component.translatable("other.millenaire.andhalf").getString()
            + " "
            + Component.translatable("other.millenaire.kilometre").getString();
   }

   private static String approximateDistanceShort(BlockPos from, BlockPos to) {
      int dist = VillageNotifier.horizontalDistance(from, to);
      if (dist < 950) {
         return dist / 100 * 100 + "m";
      }

      dist = (dist + 500) / 1000;
      return dist % 2 == 0 ? dist / 2 + " km" : (dist - 1) / 2 + ".5 km";
   }

   private static String computeDirectionKey(int xdist, int zdist, String prefix) {
      boolean diagonal = Math.abs(xdist) > Math.abs(zdist) * 0.6 && Math.abs(xdist) < Math.abs(zdist) * 1.4
         || Math.abs(zdist) > Math.abs(xdist) * 0.6 && Math.abs(zdist) < Math.abs(xdist) * 1.4;
      String direction;
      if (diagonal) {
         String ns = zdist > 0 ? prefix + "south" : prefix + "north";
         String ew = xdist > 0 ? "east" : "west";
         direction = ns + "-" + ew;
      } else if (Math.abs(xdist) > Math.abs(zdist)) {
         direction = xdist > 0 ? prefix + "east" : prefix + "west";
      } else {
         direction = zdist > 0 ? prefix + "south" : prefix + "north";
      }

      return direction;
   }

   private static String getVillageQualifiedName(Village village) {
      String name = village.getVillageName();
      return name != null ? name : "?";
   }

   private static String resolveGameOccupation(VillagerRecord vr) {
      String roleName = vr.getRoleName();
      if (roleName == null || roleName.isEmpty()) {
         return "";
      } else {
         return roleName.startsWith("role.") ? Component.translatable(roleName).getString() : roleName;
      }
   }

   @Nullable
   private static VillagerRecord findVillagerRecord(VillageSavedData savedData, UUID villagerId) {
      for (Village village : savedData.getVillageManager().getAllVillages()) {
         VillagerRecord vr = village.getVillagerRecord(villagerId);
         if (vr != null) {
            return vr;
         }
      }

      return null;
   }
}
