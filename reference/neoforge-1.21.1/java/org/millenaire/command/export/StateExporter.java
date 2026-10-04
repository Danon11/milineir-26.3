package org.millenaire.command.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import org.millenaire.FormatUtils;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.ReputationLabel;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageReputation;

public final class StateExporter {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private StateExporter() {
   }

   public static Path export(ServerLevel level, Village village, Path dir) throws IOException {
      Path file = dir.resolve("village-state.json");
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("exportedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)));
      root.put("tick", level.getServer().getTickCount());
      root.put("dayTime", level.getDayTime());
      root.put("village", buildVillageMap(level, village));
      Files.writeString(file, GSON.toJson(root));
      return file;
   }

   private static Map<String, Object> buildVillageMap(ServerLevel level, Village village) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", shortUuid(village.getId().uuid()));
      map.put("type", village.getVillageTypeId().toString());
      map.put("culture", village.getCultureId().toString());
      map.put("center", blockPosArray(village.getCenter()));
      List<Map<String, Object>> buildingList = new ArrayList<>();

      for (BuildingInstance b : village.getBuildings()) {
         buildingList.add(buildBuildingMap(level, b));
      }

      map.put("buildings", buildingList);
      List<Map<String, Object>> villagerList = new ArrayList<>();

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         villagerList.add(buildVillagerMap(level, entry.getKey(), entry.getValue()));
      }

      map.put("villagers", villagerList);
      map.put("reputation", buildReputationMap(level, village));
      map.put("waypointCount", village.getWaypointGraph().waypointCount());
      map.put("growth", buildGrowthMap(village));
      return map;
   }

   private static Map<String, Object> buildBuildingMap(ServerLevel level, BuildingInstance b) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", shortUuid(b.getId().uuid()));
      map.put("plan", b.getPlanId().toString());
      map.put("planSet", b.getPlanSetId() != null ? b.getPlanSetId().toString() : null);
      map.put("variant", b.getVariant());
      map.put("level", b.getLevel());
      map.put("status", b.getStatus().name());
      map.put("origin", blockPosArray(b.getOrigin()));
      map.put("rotation", b.getRotation().name());
      BuildingPlan plan = ModCultures.getBuildingPlan(b.getPlanId());
      if (plan != null) {
         map.put("tags", plan.tags());
         map.put("size", List.of(plan.width(), plan.height(), plan.depth()));
      } else {
         map.put("tags", List.of());
         map.put("size", null);
      }

      if (!b.getRuntimeTags().isEmpty()) {
         map.put("runtimeTags", new ArrayList<>(b.getRuntimeTags()));
      }

      if (b.getParentBuildingId() != null) {
         map.put("parentBuildingId", shortUuid(b.getParentBuildingId().uuid()));
      }

      ConstructionTask task = b.getConstructionTask();
      if (task != null) {
         Map<String, Object> cMap = new LinkedHashMap<>();
         cMap.put("progress", Math.round(task.progress() * 100.0F));
         cMap.put("step", task.getNextStepIndex());
         cMap.put("totalSteps", task.totalSteps());
         cMap.put("reserved", task.isReserved());
         cMap.put("builder", task.getReservedBuilder() != null ? shortUuid(task.getReservedBuilder()) : null);
         cMap.put("blocked", task.isBlocked());
         cMap.put("failedAttempts", task.getFailedAttempts());
         map.put("construction", cMap);
      }

      if (b.getStatus() == BuildingInstance.Status.COMPLETE) {
         BuildingInventory inv = b.getInventory();
         if (inv != null) {
            Map<Item, Integer> contents = inv.scanChests(level);
            if (!contents.isEmpty()) {
               Map<String, Integer> invMap = new LinkedHashMap<>();

               for (Entry<Item, Integer> entry : contents.entrySet()) {
                  ResourceLocation key = BuiltInRegistries.ITEM.getKey(entry.getKey());
                  invMap.put(key.toString(), entry.getValue());
               }

               map.put("inventory", invMap);
            }
         }
      }

      List<SpecialPoint> points = b.getResolvedPoints();
      if (!points.isEmpty()) {
         Map<String, Object> spMap = new LinkedHashMap<>();
         Map<String, List<List<Integer>>> grouped = new LinkedHashMap<>();
         Map<String, Integer> counts = new LinkedHashMap<>();

         for (SpecialPoint sp : points) {
            counts.merge(sp.type(), 1, Integer::sum);
            grouped.computeIfAbsent(sp.type(), k -> new ArrayList<>()).add(blockPosArray(sp.pos()));
         }

         for (Entry<String, List<List<Integer>>> e : grouped.entrySet()) {
            if (e.getValue().size() > 20) {
               spMap.put(e.getKey(), Map.of("count", e.getValue().size()));
            } else {
               spMap.put(e.getKey(), e.getValue());
            }
         }

         map.put("specialPoints", spMap);
      }

      return map;
   }

   private static Map<String, Object> buildVillagerMap(ServerLevel level, UUID uuid, ResourceLocation typeId) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("uuid", shortUuid(uuid));
      map.put("type", typeId.toString());
      Entity entity = level.getEntity(uuid);
      boolean loaded = entity instanceof MillVillager;
      map.put("loaded", loaded);
      if (loaded) {
         MillVillager v = (MillVillager)entity;
         map.put("pos", List.of(round1(v.getX()), round1(v.getY()), round1(v.getZ())));
         map.put("health", round1(v.getHealth()));
         map.put("displayName", v.getVillagerDisplayName());
         GoalScheduler scheduler = v.getGoalScheduler();
         if (scheduler != null) {
            VillagerGoal goal = scheduler.getCurrentGoal();
            VillagerTask task = scheduler.getCurrentTask();
            map.put("goal", goal != null ? goal.id().getPath() : null);
            map.put("taskGoalId", task != null ? task.goalId().getPath() : null);
            map.put("taskFinished", task != null ? task.isFinished() : null);
         } else {
            map.put("goal", null);
            map.put("taskGoalId", null);
            map.put("taskFinished", null);
         }

         map.put("goalLabel", v.getGoalLabel());
      }

      return map;
   }

   private static Map<String, Object> buildReputationMap(ServerLevel level, Village village) {
      Map<String, Object> repRoot = new LinkedHashMap<>();
      Map<String, Object> playersMap = new LinkedHashMap<>();
      VillageReputation villageRep = village.getReputation();
      Map<UUID, Integer> allReps = villageRep.getAll();
      ResourceLocation cultureId = village.getCultureId();
      PlayerCultureReputation cultureRep = PlayerCultureReputation.get(level);
      List<ReputationLabel> labels = ModCultures.getReputationLabels(cultureId);

      for (Entry<UUID, Integer> entry : allReps.entrySet()) {
         UUID playerId = entry.getKey();
         int villageValue = entry.getValue();
         int cultureValue = cultureRep.get(playerId, cultureId);
         int effective = villageValue + cultureValue;
         Map<String, Object> pMap = new LinkedHashMap<>();
         pMap.put("village", villageValue);
         pMap.put("culture", cultureValue);
         pMap.put("effective", effective);
         pMap.put("label", VillageReputation.getLabel(effective, labels));
         playersMap.put(shortUuid(playerId), pMap);
      }

      repRoot.put("players", playersMap);
      return repRoot;
   }

   private static Map<String, Object> buildGrowthMap(Village village) {
      Map<String, Object> map = new LinkedHashMap<>();
      int underConstruction = 0;
      int planned = 0;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            underConstruction++;
         }

         if (b.getStatus() == BuildingInstance.Status.PLANNED) {
            planned++;
         }
      }

      map.put("underConstruction", underConstruction);
      map.put("planned", planned);
      return map;
   }

   private static String shortUuid(UUID uuid) {
      return FormatUtils.shortUuid(uuid);
   }

   private static List<Integer> blockPosArray(BlockPos pos) {
      return List.of(pos.getX(), pos.getY(), pos.getZ());
   }

   private static double round1(double value) {
      return Math.round(value * 10.0) / 10.0;
   }

   private static double round1(float value) {
      return Math.round(value * 10.0) / 10.0;
   }
}
