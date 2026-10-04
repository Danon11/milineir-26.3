package org.millenaire.culture;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;

public final class JsonLoaderUtils {
   public static final Gson GSON = new Gson();

   private JsonLoaderUtils() {
   }

   public static List<String> parseStringList(JsonArray array) {
      List<String> list = new ArrayList<>();

      for (JsonElement e : array) {
         list.add(e.getAsString());
      }

      return list;
   }

   public static List<String> parseStringList(JsonObject parent, String key) {
      return !parent.has(key) ? List.of() : parseStringList(parent.getAsJsonArray(key));
   }

   @Nullable
   public static List<String> parseStringListOrNull(JsonObject parent, String key) {
      return !parent.has(key) ? null : parseStringList(parent.getAsJsonArray(key));
   }

   public static Map<String, Integer> parseStringIntMap(JsonObject parent, String key) {
      Map<String, Integer> map = new HashMap<>();
      if (parent.has(key)) {
         JsonObject obj = GsonHelper.getAsJsonObject(parent, key);

         for (Entry<String, JsonElement> entry : obj.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getAsInt());
         }
      }

      return map;
   }

   @Nullable
   public static Map<String, Integer> parseStringIntMapOrNull(JsonObject parent, String key) {
      if (!parent.has(key)) {
         return null;
      }

      Map<String, Integer> map = new HashMap<>();
      JsonObject obj = GsonHelper.getAsJsonObject(parent, key);

      for (String k : obj.keySet()) {
         map.put(k, obj.get(k).getAsInt());
      }

      return map;
   }

   public static Map<String, Integer> parseCommaSeparatedPairs(JsonObject parent, String key) {
      if (!parent.has(key)) {
         return Map.of();
      }

      Map<String, Integer> map = new HashMap<>();

      for (JsonElement el : parent.getAsJsonArray(key)) {
         String s = el.getAsString();
         int comma = s.indexOf(44);
         if (comma > 0 && comma < s.length() - 1) {
            map.put(s.substring(0, comma).trim(), Integer.parseInt(s.substring(comma + 1).trim()));
         }
      }

      return Map.copyOf(map);
   }
}
