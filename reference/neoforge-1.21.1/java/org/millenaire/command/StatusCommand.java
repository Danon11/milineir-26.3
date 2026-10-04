package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ConstructionTask;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public final class StatusCommand {
   private StatusCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(Commands.literal("status").executes(StatusCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.translatable("command.millenaire.error.not_in_overworld"));
         return 0;
      }

      BlockPos searchPos = source.getPlayer() != null ? source.getPlayer().blockPosition() : BlockPos.ZERO;
      VillageSavedData savedData = VillageSavedData.get(level);
      VillageManager villageManager = savedData.getVillageManager();
      Village village = villageManager.findNearestVillage(searchPos, 5000.0);
      if (village == null) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.status.no_village_5000"), false);
         return 1;
      }

      int complete = 0;
      int underConstruction = 0;
      int planned = 0;

      for (BuildingInstance b : village.getBuildings()) {
         switch (b.getStatus()) {
            case COMPLETE:
               complete++;
               break;
            case UNDER_CONSTRUCTION:
            case UPGRADING:
               underConstruction++;
               break;
            case PLANNED:
               planned++;
         }
      }

      int npcLoaded = 0;
      int npcMissing = 0;

      for (UUID uuid : village.getVillagerUuids()) {
         Entity entity = level.getEntity(uuid);
         if (entity instanceof MillVillager) {
            npcLoaded++;
         } else {
            npcMissing++;
         }
      }

      String pendingStr = "";
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null) {
         BuildingPlanSet pendingPlanSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         String pendingName = pendingPlanSet != null ? pendingPlanSet.buildingId() : pending.planSetId().getPath();
         pendingStr = " PEND:" + pendingName + (pending.isUpgrade() ? " upg" : " new");
      }

      String header = "V:"
         + village.getId().uuid().toString().substring(0, 8)
         + " "
         + village.getVillageTypeId().getPath()
         + " @"
         + village.getCenter().toShortString()
         + " B:"
         + village.getBuildings().size()
         + "(C:"
         + complete
         + " UC:"
         + underConstruction
         + " P:"
         + planned
         + ") NPC:"
         + village.getVillagerUuids().size()
         + "(ok:"
         + npcLoaded
         + " miss:"
         + npcMissing
         + ")"
         + pendingStr;
      source.sendSuccess(() -> Component.literal(header), false);
      StringBuilder bLine = new StringBuilder("B:");

      for (BuildingInstance b : village.getBuildings()) {
         String planName = b.getPlanId().getPath();
         if (planName.contains("/")) {
            planName = planName.substring(planName.indexOf(47) + 1);
         }
         String statusStr = switch (b.getStatus()) {
            case COMPLETE -> "C";
            case UNDER_CONSTRUCTION, UPGRADING -> {
               String prefix = b.getStatus() == BuildingInstance.Status.UPGRADING ? "UPG" : "UC";
               ConstructionTask task = b.getConstructionTask();
               yield task != null ? prefix + " " + String.format("%.0f%%", task.progress() * 100.0F) : prefix;
            }
            case PLANNED -> "P";
         };
         String lvl = b.getLevel() > 0 ? " lv" + b.getLevel() : "";
         bLine.append(" ").append(planName).append(":").append(statusStr).append(lvl).append(" |");
      }

      if (bLine.toString().endsWith("|")) {
         bLine.setLength(bLine.length() - 1);
      }

      String bLineStr = bLine.toString();
      source.sendSuccess(() -> Component.literal(bLineStr), false);
      StringBuilder npcLine = new StringBuilder("NPC:");

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         UUID uuid = entry.getKey();
         String typeName = entry.getValue().getPath();
         if (typeName.contains("/")) {
            typeName = typeName.substring(typeName.indexOf(47) + 1);
         }

         Entity entity = level.getEntity(uuid);
         String goalStr = "?";
         if (entity instanceof MillVillager mv) {
            GoalScheduler scheduler = mv.getGoalScheduler();
            if (scheduler != null) {
               VillagerGoal goal = scheduler.getCurrentGoal();
               goalStr = goal != null ? goal.id().getPath() : "idle";
            } else {
               goalStr = "no-sched";
            }
         } else {
            goalStr = "unloaded";
         }

         npcLine.append(" ").append(typeName).append(":").append(goalStr).append(" |");
      }

      if (npcLine.toString().endsWith("|")) {
         npcLine.setLength(npcLine.length() - 1);
      }

      String npcLineStr = npcLine.toString();
      source.sendSuccess(() -> Component.literal(npcLineStr), false);
      return 1;
   }
}
