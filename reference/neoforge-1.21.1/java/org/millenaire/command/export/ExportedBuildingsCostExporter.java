package org.millenaire.command.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingCostCalculator;
import org.millenaire.building.BuildingExporter;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public final class ExportedBuildingsCostExporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private ExportedBuildingsCostExporter() {
   }

   public static Path export(ServerLevel level, Path dir) throws IOException {
      Map<String, Map<String, Map<Integer, Path>>> grouped = new TreeMap<>();
      Path legacyDir = BuildingExporter.getLegacyExportDir(level);
      if (Files.isDirectory(legacyDir)) {
         scanFlatDir(legacyDir, grouped);
      }

      Path exportedRoot = BuildingExporter.getExportedRoot(level).resolve("cultures");
      if (Files.isDirectory(exportedRoot)) {
         try (DirectoryStream<Path> cultures = Files.newDirectoryStream(exportedRoot)) {
            for (Path culturePath : cultures) {
               Path buildingsDir = culturePath.resolve("buildings");
               if (Files.isDirectory(buildingsDir)) {
                  try (DirectoryStream<Path> categories = Files.newDirectoryStream(buildingsDir)) {
                     for (Path categoryPath : categories) {
                        if (Files.isDirectory(categoryPath)) {
                           scanFlatDir(categoryPath, grouped);
                        }
                     }
                  }
               }
            }
         }
      }

      if (grouped.isEmpty()) {
         return null;
      }

      Path outDir = dir.resolve("exported-building-costs");
      Files.createDirectories(outDir);
      Map<String, Map<String, Map<Integer, Map<ResourceLocation, Integer>>>> costs = new LinkedHashMap<>();

      for (Entry<String, Map<String, Map<Integer, Path>>> buildingEntry : grouped.entrySet()) {
         Map<String, Map<Integer, Map<ResourceLocation, Integer>>> perVariant = new LinkedHashMap<>();

         for (Entry<String, Map<Integer, Path>> variantEntry : buildingEntry.getValue().entrySet()) {
            Map<Integer, Map<ResourceLocation, Integer>> perLevel = new LinkedHashMap<>();

            for (Entry<Integer, Path> levelEntry : variantEntry.getValue().entrySet()) {
               Map<ResourceLocation, Integer> cost = computeCost(levelEntry.getValue());
               if (cost != null) {
                  perLevel.put(levelEntry.getKey(), cost);
               }
            }

            if (!perLevel.isEmpty()) {
               perVariant.put(variantEntry.getKey(), perLevel);
            }
         }

         if (!perVariant.isEmpty()) {
            costs.put(buildingEntry.getKey(), perVariant);
         }
      }

      writeTextFile(outDir, costs);
      writeJsonFile(outDir, costs);
      return outDir;
   }

   private static void scanFlatDir(Path dir, Map<String, Map<String, Map<Integer, Path>>> grouped) throws IOException {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.nbt")) {
         for (Path nbtFile : stream) {
            ExportedBuildingsCostExporter.Parsed parsed = parseName(nbtFile.getFileName().toString());
            if (parsed != null) {
               grouped.computeIfAbsent(parsed.buildingId, k -> new TreeMap<>())
                  .computeIfAbsent(parsed.variant, k -> new TreeMap<>())
                  .put(parsed.level, nbtFile);
            }
         }
      }
   }

   private static ExportedBuildingsCostExporter.Parsed parseName(String filename) {
      if (!filename.endsWith(".nbt")) {
         return null;
      }

      String stem = filename.substring(0, filename.length() - 4);
      int lastUnderscore = stem.lastIndexOf(95);
      if (lastUnderscore < 0) {
         return null;
      }

      String levelStr = stem.substring(lastUnderscore + 1);

      int level;
      try {
         level = Integer.parseInt(levelStr);
      } catch (NumberFormatException e) {
         return null;
      }

      String rest = stem.substring(0, lastUnderscore);
      int variantUnderscore = rest.lastIndexOf(95);
      if (variantUnderscore < 0) {
         return null;
      }

      String variant = rest.substring(variantUnderscore + 1);
      String buildingId = rest.substring(0, variantUnderscore);
      return buildingId.isEmpty() ? null : new ExportedBuildingsCostExporter.Parsed(buildingId, variant, level);
   }

   private static Map<ResourceLocation, Integer> computeCost(Path nbtPath) {
      try (InputStream is = Files.newInputStream(nbtPath)) {
         CompoundTag nbt = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
         return BuildingCostCalculator.computeCost(nbt);
      } catch (IOException e) {
         LOGGER.warn("Failed to load NBT for cost calculation: {}", nbtPath, e);
         return null;
      }
   }

   private static void writeTextFile(Path outDir, Map<String, Map<String, Map<Integer, Map<ResourceLocation, Integer>>>> costs) throws IOException {
      StringBuilder sb = new StringBuilder();

      for (Entry<String, Map<String, Map<Integer, Map<ResourceLocation, Integer>>>> buildingEntry : costs.entrySet()) {
         String buildingId = buildingEntry.getKey();
         sb.append(buildingId).append("\n\n");
         Map<String, Map<Integer, Map<ResourceLocation, Integer>>> variants = buildingEntry.getValue();
         List<String> variantKeys = new ArrayList<>(variants.keySet());

         for (int vi = 0; vi < variantKeys.size(); vi++) {
            String variant = variantKeys.get(vi);
            Map<Integer, Map<ResourceLocation, Integer>> levels = variants.get(variant);
            if (variantKeys.size() > 1) {
               sb.append("===Variation ").append((char)(65 + vi)).append("===\n");
            }

            Map<ResourceLocation, Integer> totalCost = new LinkedHashMap<>();

            for (Map<ResourceLocation, Integer> levelCost : levels.values()) {
               levelCost.forEach((id, qty) -> totalCost.merge(id, qty, Integer::sum));
            }

            sb.append("\nTotal Cost\n");
            appendItems(sb, totalCost);

            for (Entry<Integer, Map<ResourceLocation, Integer>> levelEntry : levels.entrySet()) {
               if (levelEntry.getKey() == 0) {
                  sb.append("\nInitial Construction\n");
               } else {
                  sb.append("\nUpgrade ").append(levelEntry.getKey()).append("\n");
               }

               appendItems(sb, levelEntry.getValue());
            }
         }

         sb.append("\n\n");
      }

      Files.writeString(outDir.resolve("exports resources used.txt"), sb.toString());
   }

   private static void writeJsonFile(Path outDir, Map<String, Map<String, Map<Integer, Map<ResourceLocation, Integer>>>> costs) throws IOException {
      List<Map<String, Object>> buildings = new ArrayList<>();

      for (Entry<String, Map<String, Map<Integer, Map<ResourceLocation, Integer>>>> buildingEntry : costs.entrySet()) {
         Map<String, Object> buildingData = new LinkedHashMap<>();
         buildingData.put("building_id", buildingEntry.getKey());
         List<Map<String, Object>> variantList = new ArrayList<>();

         for (Entry<String, Map<Integer, Map<ResourceLocation, Integer>>> variantEntry : buildingEntry.getValue().entrySet()) {
            Map<String, Object> variantData = new LinkedHashMap<>();
            variantData.put("variant", variantEntry.getKey());
            Map<String, Integer> totalCost = new TreeMap<>();

            for (Map<ResourceLocation, Integer> levelCost : variantEntry.getValue().values()) {
               levelCost.forEach((id, qty) -> totalCost.merge(id.toString(), qty, Integer::sum));
            }

            variantData.put("total_cost", totalCost);
            List<Map<String, Object>> levelList = new ArrayList<>();

            for (Entry<Integer, Map<ResourceLocation, Integer>> levelEntry : variantEntry.getValue().entrySet()) {
               Map<String, Object> levelData = new LinkedHashMap<>();
               levelData.put("level", levelEntry.getKey());
               Map<String, Integer> resources = new TreeMap<>();
               levelEntry.getValue().forEach((id, qty) -> resources.put(id.toString(), qty));
               levelData.put("resources", resources);
               levelList.add(levelData);
            }

            variantData.put("levels", levelList);
            variantList.add(variantData);
         }

         buildingData.put("variants", variantList);
         buildings.add(buildingData);
      }

      Map<String, Object> root = new LinkedHashMap<>();
      root.put("buildings", buildings);
      Files.writeString(outDir.resolve("exports.json"), GSON.toJson(root));
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

   private record Parsed(String buildingId, String variant, int level) {
   }
}
