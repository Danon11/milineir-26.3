package org.millenaire.command.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;

public final class BuildingCostJsonExporter {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private BuildingCostJsonExporter() {
   }

   public static Path export(Path dir) throws IOException {
      Path outDir = dir.resolve("building-costs-json");
      Files.createDirectories(outDir);
      Map<ResourceLocation, List<BuildingPlanSet>> byCulture = new LinkedHashMap<>();
      List<BuildingPlanSet> sets = new ArrayList<>(ModCultures.getAllBuildingPlanSets().values());
      sets.sort(Comparator.comparing(s -> s.id().toString()));

      for (BuildingPlanSet set : sets) {
         ResourceLocation cultureId = set.culture();
         if (cultureId != null) {
            byCulture.computeIfAbsent(cultureId, k -> new ArrayList<>()).add(set);
         }
      }

      for (Entry<ResourceLocation, List<BuildingPlanSet>> entry : byCulture.entrySet()) {
         ResourceLocation cultureId = entry.getKey();
         List<BuildingPlanSet> cultureSets = entry.getValue();
         Map<String, Object> cultureData = new LinkedHashMap<>();
         cultureData.put("culture", cultureId.toString());
         List<Map<String, Object>> buildings = new ArrayList<>();

         for (BuildingPlanSet set : cultureSets) {
            buildings.add(buildSetData(set));
         }

         cultureData.put("buildings", buildings);
         String filename = cultureId.getPath() + ".json";
         Files.writeString(outDir.resolve(filename), GSON.toJson(cultureData));
      }

      return outDir;
   }

   private static Map<String, Object> buildSetData(BuildingPlanSet set) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("id", set.id().toString());
      data.put("building_id", set.buildingId());
      List<String> variantKeys = new ArrayList<>(set.variants().keySet());
      variantKeys.sort(Comparator.naturalOrder());
      List<Map<String, Object>> variants = new ArrayList<>();

      for (String variantKey : variantKeys) {
         List<BuildingPlanSet.LevelDef> levels = set.variants().get(variantKey);
         if (levels != null) {
            Map<String, Object> variantData = new LinkedHashMap<>();
            variantData.put("variant", variantKey);
            Map<String, Integer> totalCost = new TreeMap<>();

            for (BuildingPlanSet.LevelDef level : levels) {
               level.requiredResources().forEach((id, qty) -> totalCost.merge(id.toString(), qty, Integer::sum));
            }

            variantData.put("total_cost", totalCost);
            List<Map<String, Object>> levelList = new ArrayList<>();

            for (BuildingPlanSet.LevelDef level : levels) {
               Map<String, Object> levelData = new LinkedHashMap<>();
               levelData.put("level", level.level());
               Map<String, Integer> resources = new TreeMap<>();
               level.requiredResources().forEach((id, qty) -> resources.put(id.toString(), qty));
               levelData.put("resources", resources);
               levelList.add(levelData);
            }

            variantData.put("levels", levelList);
            variants.add(variantData);
         }
      }

      data.put("variants", variants);
      return data;
   }
}
