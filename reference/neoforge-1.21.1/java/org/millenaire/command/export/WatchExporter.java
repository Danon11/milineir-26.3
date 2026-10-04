package org.millenaire.command.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.ConstructionTask;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.Village;

public final class WatchExporter {
   private WatchExporter() {
   }

   public static Path export(ServerLevel level, Village village, Path dir) throws IOException {
      Path file = dir.resolve("village-watch.txt");
      StringBuilder sb = new StringBuilder();
      long dayTime = level.getDayTime() % 24000L;
      long adjustedTime = (dayTime + 6000L) % 24000L;
      int hours = (int)(adjustedTime / 1000L);
      int minutes = (int)(adjustedTime % 1000L * 60L / 1000L);
      long dayNumber = level.getDayTime() / 24000L + 1L;
      sb.append(String.format("=== Watch @ tick %d (day %d, %02d:%02d) ===%n", level.getGameTime(), dayNumber, hours, minutes));
      BlockPos center = village.getCenter();
      sb.append(String.format("Village: %s @ (%d, %d, %d)%n%n", shortPath(village.getVillageTypeId()), center.getX(), center.getY(), center.getZ()));
      String headerFmt = "%-22s | %-18s | %-12s | %-14s | %-4s | %-12s | %s%n";
      String rowFmt = "%-22s | %-18s | %-12s | %-14s | %-4s | %-12s | %s%n";
      sb.append(String.format(headerFmt, "NPC", "Pos", "Goal", "GoalLabel", "HP", "MainHand", "Home"));
      sb.append("-".repeat(105)).append('\n');
      int loaded = 0;
      int missing = 0;
      Map<UUID, ResourceLocation> villagerTypes = village.getVillagerTypes();

      for (Entry<UUID, ResourceLocation> entry : villagerTypes.entrySet()) {
         UUID uuid = entry.getKey();
         ResourceLocation typeId = entry.getValue();
         String npcName = shortPath(typeId);
         if (level.getEntity(uuid) instanceof MillVillager villager) {
            loaded++;
            BlockPos pos = villager.blockPosition();
            String posStr = String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
            GoalScheduler scheduler = villager.getGoalScheduler();
            VillagerGoal currentGoal = scheduler.getCurrentGoal();
            VillagerTask currentTask = scheduler.getCurrentTask();
            String goalName = currentGoal != null ? shortPath(currentGoal.id()) : "-";
            String goalLabel = "-";
            if (currentTask != null) {
               Component label = currentTask.getGoalLabel();
               if (label != null) {
                  goalLabel = label.getString();
               }
            }

            String hp = String.valueOf((int)villager.getHealth());
            ItemStack mainHand = villager.getMainHandItem();
            String mainHandStr = mainHand.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(mainHand.getItem()).getPath();
            BuildingId homeId = villager.getHomeBuilding();
            String homeStr = "-";
            if (homeId != null) {
               BuildingInstance homeBldg = village.getBuilding(homeId);
               homeStr = homeBldg != null ? shortPath(homeBldg.getPlanId()) : homeId.uuid().toString().substring(0, 8);
            }

            sb.append(String.format(rowFmt, npcName, posStr, goalName, goalLabel, hp, mainHandStr, homeStr));
         } else {
            missing++;
            sb.append(String.format(rowFmt, npcName, "-", "-", "-", "-", "-", "UNLOADED"));
         }
      }

      sb.append(String.format("%nLoaded: %d | Missing: %d%n", loaded, missing));
      boolean hasConstruction = false;

      for (BuildingInstance building : village.getBuildings()) {
         ConstructionTask task = building.getConstructionTask();
         if (task != null) {
            if (!hasConstruction) {
               sb.append(String.format("%nConstruction:%n"));
               hasConstruction = true;
            }

            String planName = shortPath(building.getPlanId());
            int pct = (int)(task.progress() * 100.0F);
            int step = task.getNextStepIndex();
            int total = task.totalSteps();
            UUID builderUuid = task.getReservedBuilder();
            String builderStr = builderUuid != null ? builderUuid.toString().substring(0, 8) : "none";
            sb.append(
               String.format(
                  "  %s: %d%% (step %d/%d), builder=%s, reserved=%b, blocked=%b%n", planName, pct, step, total, builderStr, task.isReserved(), task.isBlocked()
               )
            );
         }
      }

      Files.writeString(file, sb.toString());
      return file;
   }

   private static String shortPath(ResourceLocation rl) {
      String path = rl.getPath();
      int slash = path.lastIndexOf(47);
      return slash >= 0 ? path.substring(slash + 1) : path;
   }
}
