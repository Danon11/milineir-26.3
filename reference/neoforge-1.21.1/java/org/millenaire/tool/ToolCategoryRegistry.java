package org.millenaire.tool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import org.millenaire.culture.JsonLoaderUtils;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public final class ToolCategoryRegistry {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final String CONFIG_PATH = "/millenaire/tool_categories.json";
   private static volatile Map<String, ToolCategory> CATEGORIES = Map.of();
   private static final AtomicBoolean warnedMeleeWeapons = new AtomicBoolean(false);

   private ToolCategoryRegistry() {
   }

   public static void load() {
      Map<String, ToolCategory> newCategories = new HashMap<>();

      try (InputStream is = ToolCategoryRegistry.class.getResourceAsStream("/millenaire/tool_categories.json")) {
         if (is != null) {
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
               root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
            }

            for (Entry<String, JsonElement> entry : root.entrySet()) {
               String categoryId = entry.getKey();
               JsonArray itemsArray = entry.getValue().getAsJsonArray();
               List<ToolCategory.ToolEntry> items = new ArrayList<>();

               for (JsonElement elem : itemsArray) {
                  JsonObject itemObj = elem.getAsJsonObject();
                  String itemId = GsonHelper.getAsString(itemObj, "item");
                  int priority = GsonHelper.getAsInt(itemObj, "priority");
                  Item resolved = ItemHelper.resolve(itemId);
                  items.add(new ToolCategory.ToolEntry(itemId, resolved, priority));
                  if (resolved == null) {
                     LOGGER.debug("Item {} not resolved for category {} — will be ignored at runtime", itemId, categoryId);
                  }
               }

               items.sort(Comparator.comparingInt(ToolCategory.ToolEntry::priority).reversed());
               newCategories.put(categoryId, new ToolCategory(categoryId, List.copyOf(items)));
            }

            CATEGORIES = Map.copyOf(newCategories);
            LOGGER.info("{} tool categories loaded ({} items total)", CATEGORIES.size(), CATEGORIES.values().stream().mapToInt(c -> c.items().size()).sum());
         } else {
            LOGGER.error("File tool_categories.json not found in classpath");
         }
      } catch (Exception e) {
         LOGGER.error("Error loading tool_categories.json", e);
      }
   }

   @Nullable
   public static ToolCategory get(String categoryId) {
      return CATEGORIES.get(categoryId);
   }

   public static List<String> expandToolClass(String toolClass) {
      return switch (toolClass.toLowerCase(Locale.ROOT)) {
         case "armour" -> List.of("armourshelmet", "armourschestplate", "armoursleggings", "armoursboots");
         case "meleeweapons" -> {
            if (warnedMeleeWeapons.compareAndSet(false, true)) {
               LOGGER.warn("Deprecated usage of 'meleeweapons' — use 'toolssword' (further occurrences silenced)");
            }

            yield List.of("weaponshandtohand");
         }
         case "toolssword" -> List.of("toolssword");
         case "rangedweapons" -> List.of("weaponsranged");
         case "pickaxes" -> List.of("toolspickaxe");
         case "axes" -> List.of("toolsaxe");
         case "shovels" -> List.of("toolsshovel");
         case "hoes" -> List.of("toolshoe");
         default -> {
            LOGGER.warn("Unknown tool class: {}", toolClass);
            yield List.of();
         }
      };
   }
}
