package org.millenaire.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.content.ContentFs;
import org.millenaire.content.Resource;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class ImportTablePlanResolver {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ImportTablePlanResolver() {
   }

   public static Optional<ImportTablePlanResolver.PlanView> resolvePlan(String cultureKey, String buildingId) {
      if (cultureKey != null && !cultureKey.isEmpty() && buildingId != null && !buildingId.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         if (planSetId == null) {
            return Optional.empty();
         }

         BuildingPlanSet registrySet = ModCultures.getBuildingPlanSet(planSetId);
         if (registrySet == null) {
            return Optional.empty();
         }

         String category = registrySet.category();
         if (category == null || category.isEmpty()) {
            category = "houses";
         }

         ResourceLocation cultureRl = ResourceLocation.tryParse(cultureKey);
         if (cultureRl == null) {
            return Optional.empty();
         }

         String culturePath = cultureRl.getPath();
         String parentDir = "buildings/" + category;
         String jsonRelPath = parentDir + "/" + buildingId + ".json";
         ContentFs cultureFs = TemplateLoader.cultureFsForImport(cultureRl);
         Optional<Resource> jsonRes = cultureFs.findFirst(jsonRelPath);
         if (jsonRes.isEmpty()) {
            return Optional.empty();
         }

         try (InputStream is = jsonRes.get().open()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            return Optional.of(parsePlan(root, cultureKey, buildingId, category, parentDir));
         } catch (Exception e) {
            LOGGER.warn("ImportTablePlanResolver: failed to read {}: {}", jsonRelPath, e.getMessage());
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   public static Optional<ImportTablePlanResolver.LevelView> resolveLevel(String cultureKey, String buildingId, String variant, int level) {
      return resolvePlan(cultureKey, buildingId).flatMap(plan -> {
         ImportTablePlanResolver.VariantView v = plan.variants().get(variant);
         return v == null ? Optional.empty() : Optional.ofNullable(v.levels().get(level));
      });
   }

   public static Optional<ImportTablePlanResolver.VariantView> resolveVariant(String cultureKey, String buildingId, String variant) {
      return resolvePlan(cultureKey, buildingId).flatMap(plan -> Optional.ofNullable(plan.variants().get(variant)));
   }

   private static ImportTablePlanResolver.PlanView parsePlan(JsonObject root, String cultureKey, String buildingId, String category, String parentDir) {
      int planSetOrientation = root.has("building_orientation") ? root.get("building_orientation").getAsInt() : 1;
      Map<String, ImportTablePlanResolver.VariantView> variants = new TreeMap<>();
      JsonArray variantsArray = root.has("variants") ? root.getAsJsonArray("variants") : null;
      if (variantsArray != null) {
         for (JsonElement ve : variantsArray) {
            JsonObject variantObj = ve.getAsJsonObject();
            if (variantObj.has("variant")) {
               String variant = variantObj.get("variant").getAsString();
               int variantOrientation = variantObj.has("building_orientation") ? variantObj.get("building_orientation").getAsInt() : planSetOrientation;
               int variantGroundLevel = variantObj.has("ground_level") ? variantObj.get("ground_level").getAsInt() : 0;
               Map<Integer, ImportTablePlanResolver.LevelView> levels = new LinkedHashMap<>();
               JsonArray levelsArray = variantObj.has("levels") ? variantObj.getAsJsonArray("levels") : null;
               if (levelsArray != null) {
                  for (JsonElement le : levelsArray) {
                     JsonObject levelObj = le.getAsJsonObject();
                     if (levelObj.has("level")) {
                        int level = levelObj.get("level").getAsInt();
                        int width = 0;
                        int height = 0;
                        int depth = 0;
                        if (levelObj.has("footprint")) {
                           JsonObject fp = levelObj.getAsJsonObject("footprint");
                           width = fp.has("width") ? fp.get("width").getAsInt() : 0;
                           height = fp.has("height") ? fp.get("height").getAsInt() : 0;
                           depth = fp.has("depth") ? fp.get("depth").getAsInt() : 0;
                        }

                        int groundLevel = levelObj.has("ground_level") ? levelObj.get("ground_level").getAsInt() : variantGroundLevel;
                        String nbtPath = parentDir + "/" + buildingId + "_" + variant + "_" + level;
                        levels.put(level, new ImportTablePlanResolver.LevelView(level, width, height, depth, groundLevel, variantOrientation, nbtPath));
                     }
                  }
               }

               variants.put(variant, new ImportTablePlanResolver.VariantView(variant, variantOrientation, levels));
            }
         }
      }

      return new ImportTablePlanResolver.PlanView(cultureKey, buildingId, category, variants);
   }

   public record LevelView(int level, int width, int height, int depth, int groundLevel, int buildingOrientation, String nbtPath) {
   }

   public record PlanView(String culture, String buildingId, String category, Map<String, ImportTablePlanResolver.VariantView> variants) {
   }

   public record VariantView(String variant, int buildingOrientation, Map<Integer, ImportTablePlanResolver.LevelView> levels) {
   }
}
