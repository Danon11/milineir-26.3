package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.millenaire.FormatUtils;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.ConstructionTask;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.ReputationLabel;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageReputation;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillageWaypointGraph;

public final class DebugCommand {
   private static final Set<UUID> VERBOSE_VILLAGERS = Collections.synchronizedSet(new HashSet<>());

   private DebugCommand() {
   }

   @Nullable
   private static DebugCommand.VillagerLookup findVillagerByPrefix(ServerLevel level, String prefix) {
      VillageSavedData savedData = VillageSavedData.get(level);

      for (Village village : savedData.getVillageManager().getAllVillages()) {
         for (UUID uuid : village.getVillagerUuids()) {
            if (uuid.toString().toLowerCase().startsWith(prefix) && level.getEntity(uuid) instanceof MillVillager mv) {
               return new DebugCommand.VillagerLookup(mv, village);
            }
         }
      }

      return null;
   }

   public static boolean isVerbose(UUID villagerUuid) {
      return VERBOSE_VILLAGERS.contains(villagerUuid);
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(Commands.literal("debug").executes(DebugCommand::execute));
      parent.then(Commands.literal("verbose").executes(DebugCommand::toggleVerbose));
      parent.then(Commands.literal("growth").executes(DebugCommand::forceGrowth));
      parent.then(Commands.literal("villager").then(Commands.argument("uuid_prefix", StringArgumentType.string()).executes(DebugCommand::debugVillager)));
      parent.then(Commands.literal("nav").then(Commands.argument("uuid_prefix", StringArgumentType.string()).executes(DebugCommand::debugNav)));
      parent.then(
         Commands.literal("path")
            .then(
               Commands.argument("uuid_prefix", StringArgumentType.string())
                  .then(
                     Commands.argument("x", IntegerArgumentType.integer())
                        .then(
                           Commands.argument("y", IntegerArgumentType.integer())
                              .then(Commands.argument("z", IntegerArgumentType.integer()).executes(DebugCommand::debugPath))
                        )
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("No villages outside the Overworld."));
         return 0;
      }

      ServerPlayer player = source.getPlayer();
      BlockPos searchPos;
      if (player != null) {
         searchPos = player.blockPosition();
      } else {
         searchPos = BlockPos.ZERO;
      }

      VillageSavedData savedData = VillageSavedData.get(level);
      VillageManager villageManager = savedData.getVillageManager();
      Collection<Village> allVillages = villageManager.getAllVillages();
      source.sendSuccess(() -> Component.literal("=== " + allVillages.size() + " village(s) registered ==="), false);

      for (Village v : allVillages) {
         source.sendSuccess(
            () -> Component.literal(
               "  "
                  + v.getId().uuid().toString().substring(0, 8)
                  + " | "
                  + v.getCenter().toShortString()
                  + " | "
                  + v.getBuildings().size()
                  + " buildings | "
                  + v.getVillagerUuids().size()
                  + " villagers"
            ),
            false
         );
      }

      Village village = villageManager.findNearestVillage(searchPos, 5000.0);
      if (village == null) {
         source.sendSuccess(() -> Component.literal("No village within 5000 blocks."), false);
         return 1;
      }

      source.sendSuccess(
         () -> Component.literal(
            "=== Village "
               + village.getId().uuid().toString().substring(0, 8)
               + " | Center: "
               + village.getCenter().toShortString()
               + " | Buildings: "
               + village.getBuildings().size()
               + " | Villagers: "
               + village.getVillagerUuids().size()
               + " ==="
         ),
         false
      );

      for (BuildingInstance b : village.getBuildings()) {
         String progressStr;
         if (b.getStatus() == BuildingInstance.Status.COMPLETE) {
            progressStr = "100%";
         } else {
            ConstructionTask task = b.getConstructionTask();
            if (task != null) {
               progressStr = String.format("%.0f%%", task.progress() * 100.0F);
            } else {
               progressStr = "N/A";
            }
         }

         String extra = "";
         if (b.isBeingBuilt()) {
            ConstructionTask ct = b.getConstructionTask();
            if (ct != null) {
               extra = " [reserved=" + ct.isReserved() + " blocked=" + ct.isBlocked() + " failed=" + ct.getFailedAttempts() + "]";
            }
         }

         String finalExtra = extra;
         String line = "  [B] " + b.getPlanId() + " | " + b.getStatus() + " | " + progressStr;
         source.sendSuccess(() -> Component.literal(line + finalExtra), false);
         BuildingInventory inv = b.getInventory();
         if (inv != null) {
            Map<Item, Integer> contents = inv.scanChests(level);
            int chestCount = inv.getChestCount();
            if (contents.isEmpty()) {
               source.sendSuccess(() -> Component.literal("    Inventory: (empty, " + chestCount + " chests)"), false);
            } else {
               source.sendSuccess(() -> Component.literal("    Inventory: (" + chestCount + " chests)"), false);

               for (Entry<Item, Integer> entry : contents.entrySet()) {
                  String itemName = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                  int count = entry.getValue();
                  source.sendSuccess(() -> Component.literal("    " + itemName + " x" + count), false);
               }
            }
         } else {
            source.sendSuccess(() -> Component.literal("    Inventory: NULL"), false);
         }
      }

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         UUID uuid = entry.getKey();
         ResourceLocation typeId = entry.getValue();
         Entity entity = level.getEntity(uuid);
         String status;
         if (entity instanceof MillVillager mv) {
            GoalScheduler scheduler = mv.getGoalScheduler();
            String goalInfo = "no scheduler";
            if (scheduler != null) {
               VillagerGoal currentGoal = scheduler.getCurrentGoal();
               VillagerTask currentTask = scheduler.getCurrentTask();
               goalInfo = "goal="
                  + (currentGoal != null ? currentGoal.id().getPath() : "idle")
                  + " task="
                  + (currentTask != null ? currentTask.goalId().getPath() : "none");
            }

            status = "loaded | type=" + typeId + " | " + goalInfo;
         } else if (entity != null) {
            status = "found but not MillVillager: " + entity.getClass().getSimpleName();
         } else {
            int missingCount = village.getMissingCount(uuid);
            if (missingCount > 0) {
               status = "MISSING (" + missingCount + "/3, respawn pending) | type=" + typeId;
            } else {
               status = "not loaded (distant chunk) | type=" + typeId;
            }
         }

         String line = "  [V] " + FormatUtils.shortUuid(uuid) + " | " + status;
         source.sendSuccess(() -> Component.literal(line), false);
      }

      if (player != null) {
         UUID playerId = player.getUUID();
         int villageRep = village.getReputation().get(playerId);
         PlayerCultureReputation cultureRepData = PlayerCultureReputation.get(level);
         int cultureRep = cultureRepData.get(playerId, village.getCultureId());
         int effective = villageRep + cultureRep;
         List<ReputationLabel> labels = ModCultures.getReputationLabels(village.getCultureId());
         String labelStr = VillageReputation.getLabel(effective, labels);
         String labelDisplay = labelStr != null ? labelStr : "?";
         String repLine = "  [R] Reputation: village=" + villageRep + " culture=" + cultureRep + " effective=" + effective + " label=" + labelDisplay;
         source.sendSuccess(() -> Component.literal(repLine), false);
      } else {
         source.sendSuccess(() -> Component.literal("  [R] Reputation: no player"), false);
      }

      return 1;
   }

   private static int toggleVerbose(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.literal("Player command only."));
         return 0;
      }

      ServerLevel level = player.serverLevel();
      MillVillager nearest = null;
      double nearestDist = 400.0;
      AABB searchBox = AABB.ofSize(player.position(), 40.0, 40.0, 40.0);

      for (MillVillager mv : level.getEntitiesOfClass(MillVillager.class, searchBox)) {
         double dist = mv.distanceToSqr(player);
         if (dist < nearestDist) {
            nearestDist = dist;
            nearest = mv;
         }
      }

      if (nearest == null) {
         source.sendFailure(Component.literal("No Millenaire villager within 20 blocks."));
         return 0;
      }

      UUID uuid = nearest.getUUID();
      if (VERBOSE_VILLAGERS.contains(uuid)) {
         VERBOSE_VILLAGERS.remove(uuid);
         MillVillager finalNearest = nearest;
         source.sendSuccess(
            () -> Component.literal("Verbose logs DISABLED for " + finalNearest.getVillagerDisplayName() + " (" + FormatUtils.shortUuid(uuid) + ")"), false
         );
      } else {
         VERBOSE_VILLAGERS.add(uuid);
         MillVillager finalNearest = nearest;
         source.sendSuccess(
            () -> Component.literal("Verbose logs ENABLED for " + finalNearest.getVillagerDisplayName() + " (" + FormatUtils.shortUuid(uuid) + ")"), false
         );
      }

      return 1;
   }

   private static int forceGrowth(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      ServerPlayer player = source.getPlayer();
      BlockPos searchPos;
      if (player != null) {
         searchPos = player.blockPosition();
      } else {
         searchPos = BlockPos.ZERO;
      }

      VillageSavedData savedData = VillageSavedData.get(level);
      VillageManager villageManager = savedData.getVillageManager();
      Village village = villageManager.findNearestVillage(searchPos, 5000.0);
      if (village == null) {
         source.sendFailure(Component.literal("No village found."));
         return 0;
      } else {
         VillageGrowthManager.evaluateGrowth(level, village);
         VillageGrowthManager.evaluateWallGrowth(level, village);
         savedData.setDirty();
         source.sendSuccess(() -> Component.literal("Growth tick forced for village " + village.getId().uuid().toString().substring(0, 8)), false);
         return 1;
      }
   }

   private static int debugVillager(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      String prefix = StringArgumentType.getString(ctx, "uuid_prefix").toLowerCase();
      DebugCommand.VillagerLookup lookup = findVillagerByPrefix(level, prefix);
      if (lookup == null) {
         source.sendFailure(Component.literal("No loaded villager with UUID starting with '" + prefix + "'."));
         return 0;
      }

      MillVillager mv = lookup.villager();
      Village village = lookup.village();
      String shortUuid = FormatUtils.shortUuid(mv.getUUID());
      ResourceLocation typeId = mv.getVillagerTypeId();
      String typeStr = typeId != null ? typeId.toString() : "unknown";
      String name = mv.getVillagerDisplayName();
      BlockPos pos = mv.blockPosition();
      float health = mv.getHealth();
      float maxHealth = mv.getMaxHealth();
      BuildingId homeId = mv.getHomeBuilding();
      String homeStr;
      if (homeId != null) {
         BuildingInstance homeBuilding = village.getBuilding(homeId);
         if (homeBuilding != null) {
            homeStr = homeBuilding.getPlanId().getPath() + " @ " + homeBuilding.getOrigin().toShortString();
         } else {
            homeStr = homeId.uuid().toString().substring(0, 8) + " (not found)";
         }
      } else {
         homeStr = "(none)";
      }

      GoalScheduler scheduler = mv.getGoalScheduler();
      String goalStr;
      if (scheduler != null) {
         VillagerGoal currentGoal = scheduler.getCurrentGoal();
         VillagerTask currentTask = scheduler.getCurrentTask();
         String goalPart = currentGoal != null ? currentGoal.id().getPath() : "idle";
         String taskPart = currentTask != null ? currentTask.goalId().getPath() : "none";
         goalStr = goalPart + " (task: " + taskPart + ")";
      } else {
         goalStr = "no scheduler";
      }

      VillagerInventory inv = mv.getInventory();
      Map<Item, Integer> items = inv.getAll();
      String inventoryStr;
      if (items.isEmpty()) {
         inventoryStr = "(empty)";
      } else {
         StringBuilder sb = new StringBuilder();
         boolean first = true;

         for (Entry<Item, Integer> entry : items.entrySet()) {
            if (!first) {
               sb.append(", ");
            }

            sb.append(BuiltInRegistries.ITEM.getKey(entry.getKey()));
            sb.append(" x").append(entry.getValue());
            first = false;
         }

         inventoryStr = sb.toString();
      }

      boolean isChild = mv.isChild();
      float scale = mv.getVillagerScale();
      String childStr = isChild ? "yes (scale=" + String.format("%.2f", scale) + ")" : "no (adult, scale=" + String.format("%.2f", scale) + ")";
      source.sendSuccess(() -> Component.literal("=== Villager " + shortUuid + " ==="), false);
      source.sendSuccess(() -> Component.literal("Type: " + typeStr), false);
      source.sendSuccess(() -> Component.literal("Name: " + name), false);
      source.sendSuccess(() -> Component.literal("Position: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
      source.sendSuccess(() -> Component.literal("Health: " + String.format("%.0f", health) + "/" + String.format("%.0f", maxHealth)), false);
      source.sendSuccess(() -> Component.literal("Home: " + homeStr), false);
      source.sendSuccess(() -> Component.literal("Goal: " + goalStr), false);
      source.sendSuccess(() -> Component.literal("Inventory: " + inventoryStr), false);
      source.sendSuccess(() -> Component.literal("Child: " + childStr), false);
      return 1;
   }

   private static int debugNav(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      String prefix = StringArgumentType.getString(ctx, "uuid_prefix").toLowerCase();
      DebugCommand.VillagerLookup lookup = findVillagerByPrefix(level, prefix);
      if (lookup == null) {
         source.sendFailure(Component.literal("No loaded villager with UUID '" + prefix + "'."));
         return 0;
      }

      MillVillager mv = lookup.villager();
      String shortUuid = FormatUtils.shortUuid(mv.getUUID());
      String name = mv.getVillagerDisplayName();
      BlockPos pos = mv.blockPosition();
      GoalScheduler scheduler = mv.getGoalScheduler();
      VillagerGoal goal = scheduler.getCurrentGoal();
      VillagerTask task = scheduler.getCurrentTask();
      String goalName = goal != null ? goal.id().getPath() : "none";
      String taskName = task != null ? task.getClass().getSimpleName() : "none";
      source.sendSuccess(() -> Component.literal("§6=== Nav Debug: " + name + " (" + shortUuid + ") ==="), false);
      source.sendSuccess(() -> Component.literal("§eGoal: §f" + goalName + " §e| Task: §f" + taskName), false);
      source.sendSuccess(() -> Component.literal("§ePos: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
      int taskTicks = scheduler.getTaskTicks();
      int maxTicks = scheduler.getMaxTaskTicks();
      int pct = maxTicks > 0 ? taskTicks * 100 / maxTicks : 0;
      source.sendSuccess(() -> Component.literal("§eWatchdog: §f" + taskTicks + "/" + maxTicks + " (" + pct + "%)"), false);
      PathNavigation nav = mv.getNavigation();
      boolean isDone = nav.isDone();
      boolean inProgress = nav.isInProgress();
      Path path = nav.getPath();
      String pathStr = path != null ? path.getNodeCount() + " nodes, idx " + path.getNextNodeIndex() : "null";
      source.sendSuccess(() -> Component.literal("§eVanilla nav: §fdone=" + isDone + " inProgress=" + inProgress + " path=[" + pathStr + "]"), false);
      if (task != null) {
         Map<String, String> navDebug = task.getNavDebugInfo();
         if (!navDebug.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§e--- Task nav ---"), false);

            for (Entry<String, String> entry : navDebug.entrySet()) {
               String key = entry.getKey();
               String val = entry.getValue();
               source.sendSuccess(() -> Component.literal("§e" + key + ": §f" + val), false);
            }
         }
      }

      Village v = lookup.village();
      VillageWaypointGraph graph = v.getWaypointGraph();
      if (graph != null) {
         source.sendSuccess(() -> Component.literal("§eWaypoint graph: §f" + graph.waypointCount() + " waypoints, available=" + graph.isAvailable()), false);
      } else {
         source.sendSuccess(() -> Component.literal("§eWaypoint graph: §cnull"), false);
      }

      return 1;
   }

   private static int debugPath(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      String prefix = StringArgumentType.getString(ctx, "uuid_prefix").toLowerCase();
      int tx = IntegerArgumentType.getInteger(ctx, "x");
      int ty = IntegerArgumentType.getInteger(ctx, "y");
      int tz = IntegerArgumentType.getInteger(ctx, "z");
      DebugCommand.VillagerLookup lookup = findVillagerByPrefix(level, prefix);
      if (lookup == null) {
         source.sendFailure(Component.literal("Villager not found: " + prefix));
         return 0;
      }

      MillVillager mv = lookup.villager();
      BlockPos from = mv.blockPosition();
      BlockPos to = new BlockPos(tx, ty, tz);
      double dist = Math.sqrt(from.distSqr(to));
      source.sendSuccess(() -> Component.literal("§6=== Path Test: " + mv.getVillagerDisplayName() + " ==="), false);
      source.sendSuccess(
         () -> Component.literal("§eFrom: §f" + from.toShortString() + " §eTo: §f" + to.toShortString() + " §eDist: §f" + String.format("%.1f", dist)), false
      );
      PathNavigation nav = mv.getNavigation();
      boolean success = nav.moveTo(tx + 0.5, ty, tz + 0.5, 0.5);
      Path path = nav.getPath();
      if (success && path != null) {
         int nodes = path.getNodeCount();
         double pathDist = path.getDistToTarget();
         source.sendSuccess(() -> Component.literal("§a✓ Path found: §f" + nodes + " nodes, target dist=" + String.format("%.1f", pathDist)), false);
         nav.stop();
      } else {
         source.sendSuccess(
            () -> Component.literal("§c✗ No path found (FOLLOW_RANGE=" + String.format("%.0f", mv.getAttribute(Attributes.FOLLOW_RANGE).getValue()) + ")"),
            false
         );
         nav.stop();
      }

      return 1;
   }

   private record VillagerLookup(MillVillager villager, Village village) {
   }
}
