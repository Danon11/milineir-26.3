package org.millenaire.command.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.item.ItemHelper;

public final class BuildingCostExporter {
   private BuildingCostExporter() {
   }

   public static Path export(Path dir) throws IOException {
      Path outDir = dir.resolve("building-costs");
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
         StringBuilder sb = new StringBuilder();

         for (BuildingPlanSet set : cultureSets) {
            appendSet(sb, set);
            sb.append("\n");
         }

         String filename = cultureId.getPath() + " resources used.txt";
         Files.writeString(outDir.resolve(filename), sb.toString());
      }

      return outDir;
   }

   private static void appendSet(StringBuilder sb, BuildingPlanSet set) {
      sb.append(set.nativeName()).append("\n");
      sb.append(set.id().getPath()).append("\n");
      sb.append("\n");
      List<String> variants = new ArrayList<>(set.variants().keySet());
      variants.sort(Comparator.naturalOrder());

      for (int vi = 0; vi < variants.size(); vi++) {
         String variant = variants.get(vi);
         List<BuildingPlanSet.LevelDef> levels = set.variants().get(variant);
         if (levels != null) {
            if (variants.size() > 1) {
               sb.append("===Variation ").append((char)(65 + vi)).append("===\n");
            }

            Map<ResourceLocation, Integer> totalCost = new LinkedHashMap<>();

            for (BuildingPlanSet.LevelDef level : levels) {
               level.requiredResources().forEach((id, qty) -> totalCost.merge(id, qty, Integer::sum));
            }

            sb.append("\nTotal Cost\n");
            appendItems(sb, totalCost);

            for (BuildingPlanSet.LevelDef level : levels) {
               if (level.level() == 0) {
                  sb.append("\nInitial Construction\n");
               } else {
                  sb.append("\nUpgrade ").append(level.level()).append("\n");
               }

               appendItems(sb, level.requiredResources());
            }
         }
      }

      sb.append("\n");
   }

   private static void appendItems(StringBuilder sb, Map<ResourceLocation, Integer> resources) {
      List<Entry<ResourceLocation, Integer>> entries = new ArrayList<>(resources.entrySet());
      entries.sort(Comparator.comparing(e -> e.getKey().toString()));

      for (Entry<ResourceLocation, Integer> entry : entries) {
         String itemName = getItemDisplayName(entry.getKey());
         sb.append(itemName).append("(").append(entry.getKey()).append("): ").append(entry.getValue()).append("\n");
      }
   }

   private static String getItemDisplayName(ResourceLocation itemId) {
      Item item = ItemHelper.resolve(itemId);
      if (item == null) {
         return itemId.getPath();
      }

      String name = item.getDefaultInstance().getDisplayName().getString();
      if (name.startsWith("[") && name.endsWith("]")) {
         name = name.substring(1, name.length() - 1);
      }

      return name;
   }
}
