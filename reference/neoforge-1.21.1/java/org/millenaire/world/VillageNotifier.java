package org.millenaire.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public final class VillageNotifier {
   private static final String[] DIRECTION_KEYS = new String[]{
      "direction.millenaire.north",
      "direction.millenaire.northeast",
      "direction.millenaire.east",
      "direction.millenaire.southeast",
      "direction.millenaire.south",
      "direction.millenaire.southwest",
      "direction.millenaire.west",
      "direction.millenaire.northwest"
   };

   public static int getNotificationRadius() {
      return MillenaireServerConfig.SERVER.backgroundRadius.getAsInt();
   }

   private VillageNotifier() {
   }

   public static String cardinalDirection(BlockPos from, BlockPos to) {
      double dx = to.getX() - from.getX();
      double dz = to.getZ() - from.getZ();
      double angle = Math.toDegrees(Math.atan2(dx, -dz));
      if (angle < 0.0) {
         angle += 360.0;
      }

      int sector = (int)Math.floor((angle + 22.5) / 45.0) % 8;
      return Component.translatable(DIRECTION_KEYS[sector]).getString();
   }

   public static String cardinalDirectionFromYaw(float yaw) {
      float normalized = (yaw % 360.0F + 360.0F) % 360.0F;
      int sector = (int)Math.floor((normalized + 180.0F + 22.5) / 45.0) % 8;
      return Component.translatable(DIRECTION_KEYS[sector]).getString();
   }

   public static int horizontalDistance(BlockPos from, BlockPos to) {
      double dx = to.getX() - from.getX();
      double dz = to.getZ() - from.getZ();
      return (int)Math.round(Math.sqrt(dx * dx + dz * dz));
   }

   public static void notifySpawn(ServerLevel level, BlockPos villageCenter, String villageName, VillageType villageType) {
      double radiusSq = (double)getNotificationRadius() * getNotificationRadius();
      List<ServerPlayer> nearby = level.getPlayers(p -> p.blockPosition().distSqr(villageCenter) < radiusSq);
      boolean isLoneBuilding = villageType.loneBuilding();

      for (ServerPlayer player : nearby) {
         int distance = horizontalDistance(player.blockPosition(), villageCenter);
         String direction = cardinalDirection(player.blockPosition(), villageCenter);
         MutableComponent message;
         if (isLoneBuilding) {
            message = Component.translatable(
               "command.millenaire.new_lone_building_found", new Object[]{villageType.name(), String.valueOf(distance), direction}
            );
         } else {
            Culture culture = ModCultures.getCulture(villageType.culture());
            String cultureAdj = culture != null ? culture.displayName() : villageType.culture().getPath();
            message = Component.translatable(
               "command.millenaire.new_village_found", new Object[]{villageName, villageType.name(), cultureAdj, String.valueOf(distance), direction}
            );
         }

         player.sendSystemMessage(message.withStyle(ChatFormatting.YELLOW));
      }
   }

   public static void sendVillageList(ServerPlayer player, ServerLevel level) {
      VillageSavedData savedData = VillageSavedData.get(level);
      VillageManager manager = savedData.getVillageManager();
      BlockPos playerPos = player.blockPosition();
      double radiusSq = (double)getNotificationRadius() * getNotificationRadius();
      List<VillageNotifier.VillageInfo> infos = new ArrayList<>();

      for (Village village : manager.getAllVillages()) {
         double distSq = village.getCenter().distSqr(playerPos);
         if (distSq < radiusSq) {
            int distance = horizontalDistance(playerPos, village.getCenter());
            String direction = cardinalDirection(playerPos, village.getCenter());
            infos.add(new VillageNotifier.VillageInfo(village, distance, direction));
         }
      }

      infos.sort(Comparator.comparingInt(VillageNotifier.VillageInfo::distance).reversed());
      if (infos.isEmpty()) {
         player.sendSystemMessage(Component.translatable("command.millenaire.no_known_village").withStyle(ChatFormatting.GRAY));
      } else {
         for (VillageNotifier.VillageInfo info : infos) {
            Village village = info.village();
            VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
            MutableComponent line;
            if (vt != null && vt.loneBuilding()) {
               line = Component.translatable(
                  "command.millenaire.villagelist_lonebuilding", new Object[]{vt.name(), String.valueOf(info.distance()), info.direction()}
               );
            } else {
               String villageName = village.getVillageName() != null ? village.getVillageName() : "???";
               String status = resolveStatus(village);
               String typeName = vt != null ? vt.name() : village.getVillageTypeId().getPath();
               line = Component.translatable(
                  "command.millenaire.villagelist", new Object[]{typeName, villageName, status, String.valueOf(info.distance()), info.direction()}
               );
            }

            ChatFormatting color = vt != null && vt.loneBuilding() ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY;
            player.sendSystemMessage(line.withStyle(color));
         }

         String facingDirection = cardinalDirectionFromYaw(player.getYRot());
         player.sendSystemMessage(
            Component.translatable("command.millenaire.facing_direction", new Object[]{facingDirection}).withStyle(ChatFormatting.DARK_GREEN)
         );
      }
   }

   private static String resolveStatus(Village village) {
      return !village.isChunksForceLoaded() && !village.isForceActive()
         ? Component.translatable("command.millenaire.inactive").getString()
         : Component.translatable("command.millenaire.active").getString();
   }

   private record VillageInfo(Village village, int distance, String direction) {
   }
}
