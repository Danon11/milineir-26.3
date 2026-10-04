package org.millenaire.content.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;

public final class PreservationPolicy {
   private static final Logger LOGGER = LogUtils.getLogger();

   private PreservationPolicy() {
   }

   public static PreservationPolicy.Decision checkVillageType(Path target, Map<String, Object> builtJson) {
      if (!Files.isRegularFile(target)) {
         return PreservationPolicy.Decision.overwriteAsIs();
      }

      JsonObject existing = readObject(target);
      if (existing == null) {
         return PreservationPolicy.Decision.overwriteAsIs();
      }

      if (existing.has("converter_skip")
         && existing.get("converter_skip").isJsonPrimitive()
         && existing.getAsJsonPrimitive("converter_skip").isBoolean()
         && existing.getAsJsonPrimitive("converter_skip").getAsBoolean()) {
         return PreservationPolicy.Decision.skipConverterPinned(target.toString());
      }

      JsonElement biomeTags = existing.get("biome_tags");
      if (biomeTags != null && biomeTags.isJsonArray()) {
         List<String> tags = new ArrayList<>();
         biomeTags.getAsJsonArray().forEach(ex -> {
            if (ex.isJsonPrimitive() && ex.getAsJsonPrimitive().isString()) {
               tags.add(ex.getAsString());
            }
         });
         String precedingKey = null;

         for (String k : existing.keySet()) {
            if ("biome_tags".equals(k)) {
               break;
            }

            precedingKey = k;
         }

         Map<String, Object> reordered = new LinkedHashMap<>();
         boolean inserted = false;

         for (Entry<String, Object> e : builtJson.entrySet()) {
            if (!"biome_tags".equals(e.getKey())) {
               reordered.put(e.getKey(), e.getValue());
               if (!inserted && e.getKey().equals(precedingKey)) {
                  reordered.put("biome_tags", tags);
                  inserted = true;
               }
            }
         }

         if (!inserted) {
            reordered.put("biome_tags", tags);
         }

         return PreservationPolicy.Decision.overwriteWithBiomeTags(reordered);
      } else {
         return PreservationPolicy.Decision.overwriteAsIs();
      }
   }

   public static PreservationPolicy.Decision checkConverterSkip(Path target) {
      if (!Files.isRegularFile(target)) {
         return PreservationPolicy.Decision.overwriteAsIs();
      } else {
         JsonObject existing = readObject(target);
         if (existing == null) {
            return PreservationPolicy.Decision.overwriteAsIs();
         } else {
            return existing.has("converter_skip")
                  && existing.get("converter_skip").isJsonPrimitive()
                  && existing.getAsJsonPrimitive("converter_skip").isBoolean()
                  && existing.getAsJsonPrimitive("converter_skip").getAsBoolean()
               ? PreservationPolicy.Decision.skipConverterPinned(target.toString())
               : PreservationPolicy.Decision.overwriteAsIs();
         }
      }
   }

   private static JsonObject readObject(Path target) {
      try {
         JsonElement el = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8));
         return el.isJsonObject() ? el.getAsJsonObject() : null;
      } catch (IOException e) {
         LOGGER.warn("PreservationPolicy: cannot read {}: {}", target, e.getMessage());
         return null;
      } catch (JsonSyntaxException e) {
         LOGGER.warn("PreservationPolicy: malformed JSON at {}: {}", target, e.getMessage());
         return null;
      }
   }

   public record Decision(boolean overwrite, Map<String, Object> mergedJson, String reason) {
      public static PreservationPolicy.Decision overwriteAsIs() {
         return new PreservationPolicy.Decision(true, null, null);
      }

      public static PreservationPolicy.Decision skipConverterPinned(String path) {
         return new PreservationPolicy.Decision(false, null, "target has converter_skip=true (" + path + ")");
      }

      public static PreservationPolicy.Decision overwriteWithBiomeTags(Map<String, Object> merged) {
         return new PreservationPolicy.Decision(true, merged, "preserved biome_tags from existing target");
      }
   }
}
