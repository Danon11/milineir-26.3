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
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;

public final class VillagerTypeJsonExporter {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final ResourceLocation GET_TOOL_GOAL = ResourceLocation.fromNamespaceAndPath("millenaire", "get_tool");

   private VillagerTypeJsonExporter() {
   }

   public static Path export(Path dir) throws IOException {
      Path outDir = dir.resolve("villager-types-json");
      Files.createDirectories(outDir);
      Map<ResourceLocation, List<VillagerType>> byCulture = new LinkedHashMap<>();
      List<VillagerType> allTypes = new ArrayList<>(ModCultures.getAllVillagerTypes().values());
      allTypes.sort(Comparator.comparing(v -> v.id().toString()));

      for (VillagerType vt : allTypes) {
         ResourceLocation cultureId = vt.culture();
         if (cultureId != null) {
            byCulture.computeIfAbsent(cultureId, k -> new ArrayList<>()).add(vt);
         }
      }

      for (Entry<ResourceLocation, List<VillagerType>> entry : byCulture.entrySet()) {
         ResourceLocation cultureId = entry.getKey();
         List<VillagerType> cultureTypes = entry.getValue();
         Map<String, Object> cultureData = new LinkedHashMap<>();
         cultureData.put("culture", cultureId.toString());
         List<Map<String, Object>> villagers = new ArrayList<>();

         for (VillagerType vt : cultureTypes) {
            villagers.add(buildVillagerData(vt));
         }

         cultureData.put("villagers", villagers);
         String filename = cultureId.getPath() + ".json";
         Files.writeString(outDir.resolve(filename), GSON.toJson(cultureData));
      }

      return outDir;
   }

   private static Map<String, Object> buildVillagerData(VillagerType vt) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("id", vt.id().getPath());
      data.put("native_name", vt.nativeName());
      data.put("gender", vt.gender().name().toLowerCase());
      List<String> goals = vt.goals().stream().<String>map(ResourceLocation::toString).toList();
      data.put("goals", goals);
      List<String> goalsEffective = new ArrayList<>(goals);
      if (!vt.toolNeededClasses().isEmpty()) {
         String getToolStr = GET_TOOL_GOAL.toString();
         if (!goalsEffective.contains(getToolStr)) {
            goalsEffective.add(getToolStr);
         }
      }

      data.put("goals_effective", goalsEffective);
      data.put("tags", vt.tags());
      data.put("tool_needed_classes", vt.toolNeededClasses());
      data.put("bring_back_home_goods", vt.bringBackHomeGoods());
      data.put("collect_goods", vt.collectGoods());
      Map<String, Integer> requiredGoods = new TreeMap<>();

      for (Entry<String, Integer> e : vt.requiredGoods().entrySet()) {
         requiredGoods.put(e.getKey(), e.getValue());
      }

      data.put("required_goods", requiredGoods);
      Map<String, Integer> initialInventory = new TreeMap<>();

      for (Entry<ResourceLocation, Integer> e : vt.initialInventory().entrySet()) {
         initialInventory.put(e.getKey().toString(), e.getValue());
      }

      data.put("initial_inventory", initialInventory);
      data.put("max_health", vt.maxHealth());
      data.put("base_scale", vt.baseScale());
      data.put("is_child", vt.isChild());
      data.put("spawn_weight", vt.spawnWeight());
      data.put("hiring_cost", vt.hiringCost());
      data.put("first_name_list", vt.firstNameList());
      data.put("family_name_list", vt.familyNameList());
      data.put("male_child", vt.maleChild());
      data.put("female_child", vt.femaleChild());
      data.put("textures_count", vt.textures().size());
      List<String> clothesTypes = new ArrayList<>(vt.clothes().keySet());
      clothesTypes.sort(Comparator.naturalOrder());
      data.put("clothes_types", clothesTypes);
      return data;
   }
}
