package org.millenaire.content.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

public final class BiomeMapper {
   public static final String BUILTIN_RESOURCE = "/millenaire/legacy/legacy_biome_map.json";
   private final Map<String, String> builtinMap;
   private final Map<String, Map<String, String>> cultureOverrides;
   private static final Set<String> WARNED_UNKNOWNS = Collections.synchronizedSet(new HashSet<>());
   private BiConsumer<String, String> unmappedReportSink;

   private BiomeMapper(Map<String, String> builtinMap, Map<String, Map<String, String>> cultureOverrides) {
      this.builtinMap = builtinMap;
      this.cultureOverrides = cultureOverrides;
   }

   static void resetWarnedUnknownsForTesting() {
      WARNED_UNKNOWNS.clear();
   }

   public void installUnmappedReportSink(BiConsumer<String, String> sink) {
      this.unmappedReportSink = sink;
   }

   public static BiomeMapper loadBuiltin() {
      return new BiomeMapper(loadBuiltinMap(), Collections.emptyMap());
   }

   public static BiomeMapper loadAll(Path customRoot, Iterable<String> customCultures) {
      Map<String, String> builtin = loadBuiltinMap();
      Map<String, Map<String, String>> cultures = new LinkedHashMap<>();
      if (customRoot != null && customCultures != null) {
         for (String culture : customCultures) {
            Path f = customRoot.resolve("cultures/" + culture + "/biome_map.json");
            if (Files.isRegularFile(f)) {
               Map<String, String> cm = parseOverrideFile(f);
               if (!cm.isEmpty()) {
                  cultures.put(culture, cm);
               }
            }
         }
      }

      return new BiomeMapper(builtin, cultures);
   }

   public static BiomeMapper of(Map<String, String> builtinMap, Map<String, Map<String, String>> cultureOverrides) {
      return new BiomeMapper(normalise(builtinMap), normaliseByCulture(cultureOverrides));
   }

   public static String normaliseKey(String raw) {
      if (raw == null) {
         return "";
      }

      String s = raw.trim().toLowerCase(Locale.ROOT);
      return s.replaceAll("\\s+", " ");
   }

   public Optional<String> resolveOne(String cultureContext, String legacyName) {
      String key = normaliseKey(legacyName);
      if (key.isEmpty()) {
         return Optional.empty();
      }

      if (cultureContext != null) {
         Map<String, String> cm = this.cultureOverrides.get(cultureContext);
         if (cm != null) {
            String v = cm.get(key);
            if (v != null) {
               return Optional.of(v);
            }
         }
      }

      String v = this.builtinMap.get(key);
      return Optional.ofNullable(v);
   }

   public List<String> mapAll(String cultureContext, List<String> legacyBiomes) {
      if (legacyBiomes != null && !legacyBiomes.isEmpty()) {
         Set<String> seen = new LinkedHashSet<>();

         for (String raw : legacyBiomes) {
            Optional<String> mapped = this.resolveOne(cultureContext, raw);
            if (mapped.isPresent()) {
               seen.add(mapped.get());
            } else {
               String warnKey = (cultureContext == null ? "" : cultureContext) + "::" + normaliseKey(raw);
               if (WARNED_UNKNOWNS.add(warnKey)) {
                  System.out.println("  [WARN] Unknown legacy biome: '" + raw + "' (culture=" + cultureContext + "), dropping");
               }

               if (this.unmappedReportSink != null) {
                  this.unmappedReportSink.accept(cultureContext, raw);
               }
            }
         }

         return new ArrayList<>(seen);
      } else {
         return List.of();
      }
   }

   private static Map<String, String> loadBuiltinMap() {
      try (InputStream in = BiomeMapper.class.getResourceAsStream("/millenaire/legacy/legacy_biome_map.json")) {
         if (in == null) {
            throw new IllegalStateException("Built-in legacy biome map missing: /millenaire/legacy/legacy_biome_map.json");
         }

         try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            return parseJsonObject(obj);
         }
      } catch (IOException e) {
         throw new IllegalStateException("Failed to load /millenaire/legacy/legacy_biome_map.json", e);
      }
   }

   private static Map<String, String> parseOverrideFile(Path file) {
      try {
         String content = Files.readString(file, StandardCharsets.UTF_8);
         return parseJsonObject(JsonParser.parseString(content).getAsJsonObject());
      } catch (IOException e) {
         System.err.println("[WARN] Failed to read biome_map " + file + ": " + e.getMessage());
         return Collections.emptyMap();
      }
   }

   private static Map<String, String> parseJsonObject(JsonObject obj) {
      Map<String, String> out = new LinkedHashMap<>();

      for (Entry<String, JsonElement> e : obj.entrySet()) {
         String k = e.getKey();
         if (!k.startsWith("_") && e.getValue().isJsonPrimitive()) {
            out.put(normaliseKey(k), e.getValue().getAsString());
         }
      }

      return out;
   }

   private static Map<String, String> normalise(Map<String, String> in) {
      if (in != null && !in.isEmpty()) {
         Map<String, String> out = new LinkedHashMap<>();

         for (Entry<String, String> e : in.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
               out.put(normaliseKey(e.getKey()), e.getValue());
            }
         }

         return out;
      } else {
         return Collections.emptyMap();
      }
   }

   private static Map<String, Map<String, String>> normaliseByCulture(Map<String, Map<String, String>> in) {
      if (in != null && !in.isEmpty()) {
         Map<String, Map<String, String>> out = new LinkedHashMap<>();

         for (Entry<String, Map<String, String>> e : in.entrySet()) {
            out.put(e.getKey(), normalise(e.getValue()));
         }

         return out;
      } else {
         return Collections.emptyMap();
      }
   }

   Map<String, String> builtinSnapshot() {
      return Collections.unmodifiableMap(this.builtinMap);
   }
}
