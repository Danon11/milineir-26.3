package org.millenaire.language;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

public final class ServerTranslationCache {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String LANG_RESOURCE = "/assets/millenaire/lang/en_us.json";
   private static Map<String, String> entries = Collections.emptyMap();

   private ServerTranslationCache() {
   }

   public static void load() {
      try (InputStream is = ServerTranslationCache.class.getResourceAsStream("/assets/millenaire/lang/en_us.json")) {
         if (is != null) {
            Type mapType = (new TypeToken<Map<String, String>>() {}).getType();
            Map<String, String> loaded = (Map<String, String>)new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), mapType);
            entries = loaded != null ? loaded : Collections.emptyMap();
            LOGGER.info("[Millenaire] ServerTranslationCache loaded {} entries", entries.size());
         } else {
            LOGGER.warn("[Millenaire] Could not find lang resource: {}", "/assets/millenaire/lang/en_us.json");
            entries = Collections.emptyMap();
         }
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Failed to load ServerTranslationCache", e);
         entries = Collections.emptyMap();
      }
   }

   public static String get(String key) {
      return entries.getOrDefault(key, key);
   }

   public static boolean has(String key) {
      return entries.containsKey(key);
   }

   static void setEntries(Map<String, String> testEntries) {
      entries = new HashMap<>(testEntries);
   }

   public static void clear() {
      entries = Collections.emptyMap();
   }
}
