package org.millenaire.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.FormatUtils;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.ConstructionTask;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class QueryCommand {
   private static final Gson GSON = new GsonBuilder().create();

   private QueryCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("millenaire")
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                    "query"
                                 )
                                 .requires(source -> source.hasPermission(2)))
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("village").executes(ctx -> queryVillage(ctx, 0)))
                                    .then(
                                       Commands.argument("index", IntegerArgumentType.integer(0))
                                          .executes(ctx -> queryVillage(ctx, IntegerArgumentType.getInteger(ctx, "index")))
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("buildings").executes(ctx -> queryBuildings(ctx, 0)))
                                 .then(
                                    Commands.argument("index", IntegerArgumentType.integer(0))
                                       .executes(ctx -> queryBuildings(ctx, IntegerArgumentType.getInteger(ctx, "index")))
                                 )
                           ))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("villagers").executes(ctx -> queryVillagers(ctx, 0)))
                              .then(
                                 Commands.argument("index", IntegerArgumentType.integer(0))
                                    .executes(ctx -> queryVillagers(ctx, IntegerArgumentType.getInteger(ctx, "index")))
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("growth").executes(ctx -> queryGrowth(ctx, 0)))
                           .then(
                              Commands.argument("index", IntegerArgumentType.integer(0))
                                 .executes(ctx -> queryGrowth(ctx, IntegerArgumentType.getInteger(ctx, "index")))
                           )
                     ))
                  .then(Commands.literal("lonebuildings").executes(QueryCommand::queryLoneBuildings))
            )
      );
   }

   private static Village getVillageByIndex(CommandSourceStack source, int index) {
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         return null;
      }

      VillageSavedData savedData = VillageSavedData.get(level);
      List<Village> villages = new ArrayList<>(savedData.getVillageManager().getAllVillages());
      return index >= 0 && index < villages.size() ? villages.get(index) : null;
   }

   private static void sendJson(CommandSourceStack source, Object data) {
      source.sendSuccess(() -> Component.literal(GSON.toJson(data)), false);
   }

   private static int queryVillage(CommandContext<CommandSourceStack> ctx, int index) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = getVillageByIndex(source, index);
      if (village == null) {
         source.sendFailure(Component.literal("{\"error\":\"village not found\"}"));
         return 0;
      } else {
         Map<String, Object> result = new LinkedHashMap<>();
         result.put("id", village.getId().uuid().toString().substring(0, 8));
         result.put("name", village.getVillageName());
         result.put("center", posToList(village.getCenter()));
         result.put("culture", village.getCultureId().toString());
         result.put("type", village.getVillageTypeId().toString());
         result.put("buildingCount", village.getBuildings().size());
         result.put("villagerCount", village.getVillagerUuids().size());
         sendJson(source, result);
         return 1;
      }
   }

   private static int queryBuildings(CommandContext<CommandSourceStack> ctx, int index) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = getVillageByIndex(source, index);
      if (village == null) {
         source.sendFailure(Component.literal("{\"error\":\"village not found\"}"));
         return 0;
      }

      List<Map<String, Object>> buildings = new ArrayList<>();

      for (BuildingInstance b : village.getBuildings()) {
         Map<String, Object> bMap = new LinkedHashMap<>();
         bMap.put("plan", b.getPlanId().toString());
         bMap.put("status", b.getStatus().name());
         double progress = 1.0;
         if (b.getStatus() != BuildingInstance.Status.COMPLETE) {
            ConstructionTask task = b.getConstructionTask();
            progress = task != null ? task.progress() : 0.0;
         }

         bMap.put("progress", Math.round(progress * 100.0) / 100.0);
         bMap.put("origin", posToList(b.getOrigin()));
         if (!b.getRuntimeTags().isEmpty()) {
            bMap.put("runtimeTags", new ArrayList<>(b.getRuntimeTags()));
         }

         if (b.getParentBuildingId() != null) {
            bMap.put("parentBuildingId", b.getParentBuildingId().uuid().toString());
         }

         buildings.add(bMap);
      }

      sendJson(source, buildings);
      return 1;
   }

   private static int queryVillagers(CommandContext<CommandSourceStack> ctx, int index) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = getVillageByIndex(source, index);
      if (village == null) {
         source.sendFailure(Component.literal("{\"error\":\"village not found\"}"));
         return 0;
      }

      ServerLevel level = source.getLevel();
      List<Map<String, Object>> villagers = new ArrayList<>();

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         UUID uuid = entry.getKey();
         ResourceLocation typeId = entry.getValue();
         Map<String, Object> vMap = new LinkedHashMap<>();
         vMap.put("uuid", FormatUtils.shortUuid(uuid));
         vMap.put("type", typeId.toString());
         if (level.getEntity(uuid) instanceof MillVillager mv) {
            vMap.put("loaded", true);
            vMap.put("pos", posToList(mv.blockPosition()));
            GoalScheduler scheduler = mv.getGoalScheduler();
            if (scheduler != null) {
               VillagerGoal goal = scheduler.getCurrentGoal();
               VillagerTask task = scheduler.getCurrentTask();
               vMap.put("goal", goal != null ? goal.id().getPath() : "idle");
               vMap.put("task", task != null ? task.goalId().getPath() : null);
            } else {
               vMap.put("goal", null);
               vMap.put("task", null);
            }
         } else {
            vMap.put("loaded", false);
            int missingCount = village.getMissingCount(uuid);
            vMap.put("missing", missingCount > 0);
            vMap.put("missingCount", missingCount);
         }

         villagers.add(vMap);
      }

      sendJson(source, villagers);
      return 1;
   }

   private static int queryGrowth(CommandContext<CommandSourceStack> ctx, int index) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = getVillageByIndex(source, index);
      if (village == null) {
         source.sendFailure(Component.literal("{\"error\":\"village not found\"}"));
         return 0;
      } else {
         boolean ongoingConstruction = village.getBuildings().stream().anyMatch(BuildingInstance::isBeingBuilt);
         long completeCount = village.getBuildings().stream().filter(b -> b.getStatus() == BuildingInstance.Status.COMPLETE).count();
         Map<String, Object> result = new LinkedHashMap<>();
         result.put("buildingCount", village.getBuildings().size());
         result.put("completeCount", completeCount);
         result.put("ongoingConstruction", ongoingConstruction);
         sendJson(source, result);
         return 1;
      }
   }

   private static int queryLoneBuildings(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("{\"error\":\"not in overworld\"}"));
         return 0;
      }

      VillageSavedData savedData = VillageSavedData.get(level);
      List<Map<String, Object>> result = new ArrayList<>();

      for (VillageSavedData.LoneBuildingEntry entry : savedData.getLoneBuildingPositions()) {
         Map<String, Object> map = new LinkedHashMap<>();
         map.put("type", entry.type().toString());
         map.put("culture", entry.culture());
         map.put("pos", posToList(entry.pos()));
         if (entry.generatedFor() != null) {
            map.put("generatedFor", entry.generatedFor());
         }

         result.add(map);
      }

      sendJson(source, result);
      return 1;
   }

   private static List<Integer> posToList(BlockPos pos) {
      return List.of(pos.getX(), pos.getY(), pos.getZ());
   }
}
