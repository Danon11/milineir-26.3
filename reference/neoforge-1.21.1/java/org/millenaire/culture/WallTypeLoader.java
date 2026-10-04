package org.millenaire.culture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

final class WallTypeLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String WALL_TYPE_DIR = "/millenaire/wall_type/";

   private WallTypeLoader() {
   }

   static void loadAllFromManifest() {
      String manifestPath = "/millenaire/wall_type/_manifest.json";

      try (InputStream is = WallTypeLoader.class.getResourceAsStream(manifestPath)) {
         if (is != null) {
            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);

            JsonObject manifest;
            try {
               manifest = (JsonObject)CultureLoader.GSON.fromJson(reader, JsonObject.class);
            } catch (Throwable var12) {
               try {
                  reader.close();
               } catch (Throwable var11) {
                  var12.addSuppressed(var11);
               }

               throw var12;
            }

            reader.close();
            JsonArray files = manifest.getAsJsonArray("files");
            if (files == null) {
               LOGGER.warn("wall_type manifest missing 'files' array");
            } else {
               int count = 0;

               for (JsonElement element : files) {
                  String filename = element.getAsString();
                  int underscore = filename.indexOf(95);
                  String contentId = underscore >= 0 ? filename.substring(0, underscore) + "/" + filename.substring(underscore + 1) : filename;
                  load(contentId, filename);
                  count++;
               }

               LOGGER.info("{} wall types loaded", count);
            }
         } else {
            LOGGER.warn("No wall_type manifest at {} — skipping wall type load", manifestPath);
         }
      } catch (Exception e) {
         LOGGER.error("Error reading wall_type manifest: {}", e.getMessage(), e);
      }
   }

   static void load(String contentId, String filename) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", contentId);
      String path = "/millenaire/wall_type/" + filename + ".json";
      JsonObject json = CultureLoader.readJson(path);
      if (json != null) {
         try {
            ResourceLocation culture = ResourceLocation.parse(GsonHelper.getAsString(json, "culture"));
            String key = GsonHelper.getAsString(json, "key", contentId);
            WallType wallType = new WallType(
               id,
               culture,
               key,
               optionalRL(json, "wall_plan_set"),
               optionalRL(json, "tower_plan_set"),
               ResourceLocation.parse(GsonHelper.getAsString(json, "gateway_plan_set")),
               optionalRL(json, "corner_plan_set"),
               optionalRL(json, "cap_right_plan_set"),
               optionalRL(json, "cap_left_plan_set"),
               optionalRL(json, "cap_both_plan_set"),
               optionalRL(json, "slope1_left_plan_set"),
               optionalRL(json, "slope1_right_plan_set"),
               optionalRL(json, "slope2_left_plan_set"),
               optionalRL(json, "slope2_right_plan_set"),
               optionalRL(json, "slope3_left_plan_set"),
               optionalRL(json, "slope3_right_plan_set"),
               GsonHelper.getAsBoolean(json, "wall_spawn", true),
               GsonHelper.getAsBoolean(json, "tower_spawn", true),
               GsonHelper.getAsBoolean(json, "gateway_spawn", true),
               GsonHelper.getAsBoolean(json, "corner_spawn", true),
               GsonHelper.getAsBoolean(json, "cap_spawn", true),
               GsonHelper.getAsInt(json, "walls_between_towers", 3),
               GsonHelper.getAsInt(json, "nb_smooth_runs", 3),
               GsonHelper.getAsInt(json, "max_y_delta", 15)
            );
            ModCultures.registerWallType(wallType);
            LOGGER.debug("Loaded wall type: {}", id);
         } catch (Exception e) {
            LOGGER.error("Error parsing wall type {}: {}", new Object[]{contentId, e.getMessage(), e});
         }
      }
   }

   @Nullable
   private static ResourceLocation optionalRL(JsonObject json, String field) {
      return json.has(field) ? ResourceLocation.parse(GsonHelper.getAsString(json, field)) : null;
   }

   static ResourceLocation resolveWallTypeRL(String wallTypeName, ResourceLocation culture) {
      return wallTypeName.contains(":")
         ? ResourceLocation.parse(wallTypeName)
         : ResourceLocation.fromNamespaceAndPath(culture.getNamespace(), culture.getPath() + "/" + wallTypeName.toLowerCase());
   }
}
