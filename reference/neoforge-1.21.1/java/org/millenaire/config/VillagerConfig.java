package org.millenaire.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public final class VillagerConfig {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String CONFIG_BASE = "/millenaire/villager_config/";
   private static final List<String> OVERLAY_NAMES = List.of("muslimrules", "indiannobeef");
   private static VillagerConfig DEFAULT = new VillagerConfig(Map.of(), Map.of());
   private static boolean buildVillagePaths = true;
   private static Map<String, VillagerConfig> NAMED_CONFIGS = Map.of();
   private final Map<Item, Integer> foodGrowth;
   private final Map<Item, Integer> foodConception;

   private VillagerConfig(Map<Item, Integer> foodGrowth, Map<Item, Integer> foodConception) {
      this.foodGrowth = foodGrowth;
      this.foodConception = foodConception;
   }

   public static void load() {
      DEFAULT = loadFromJson("default");
      LOGGER.info("[Millenaire] Loaded default VillagerConfig: {} growth foods, {} conception foods", DEFAULT.foodGrowth.size(), DEFAULT.foodConception.size());
      Map<String, VillagerConfig> named = new HashMap<>();

      for (String name : OVERLAY_NAMES) {
         VillagerConfig cfg = loadOverride(name);
         if (cfg != null) {
            named.put(name, cfg);
            LOGGER.info(
               "[Millenaire] Loaded VillagerConfig overlay '{}': {} growth foods, {} conception foods",
               new Object[]{name, cfg.foodGrowth.size(), cfg.foodConception.size()}
            );
         }
      }

      NAMED_CONFIGS = Map.copyOf(named);
   }

   public static VillagerConfig get(@Nullable String key) {
      return key != null && !key.isEmpty() ? NAMED_CONFIGS.getOrDefault(key, DEFAULT) : DEFAULT;
   }

   public static VillagerConfig getDefault() {
      return DEFAULT;
   }

   public Map<Item, Integer> getFoodGrowth() {
      return this.foodGrowth;
   }

   public Map<Item, Integer> getFoodConception() {
      return this.foodConception;
   }

   public static Map<Item, Integer> getDefaultFoodGrowth() {
      return DEFAULT.foodGrowth;
   }

   public static Map<Item, Integer> getDefaultFoodConception() {
      return DEFAULT.foodConception;
   }

   private static VillagerConfig loadFromJson(String name) {
      String path = "/millenaire/villager_config/" + name + ".json";

      try (InputStream is = VillagerConfig.class.getResourceAsStream(path)) {
         if (is == null) {
            LOGGER.warn("Villager config not found: {}", path);
            return new VillagerConfig(Map.of(), Map.of());
         }

         InputStreamReader reader = new InputStreamReader(is);

         JsonObject root;
         try {
            root = JsonParser.parseReader(reader).getAsJsonObject();
         } catch (Throwable var9) {
            try {
               reader.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }

            throw var9;
         }

         reader.close();
         Map<Item, Integer> foodGrowth = root.has("food_growth") ? parseItemMap(root.getAsJsonObject("food_growth")) : Map.of();
         Map<Item, Integer> foodConception = root.has("food_conception") ? parseItemMap(root.getAsJsonObject("food_conception")) : Map.of();
         if (root.has("build_village_paths")) {
            buildVillagePaths = GsonHelper.getAsBoolean(root, "build_village_paths", true);
         }

         return new VillagerConfig(foodGrowth, foodConception);
      } catch (Exception e) {
         LOGGER.error("Error loading villager config '{}'", name, e);
         return new VillagerConfig(Map.of(), Map.of());
      }
   }

   @Nullable
   private static VillagerConfig loadOverride(String name) {
      String path = "/millenaire/villager_config/" + name + ".json";

      try (InputStream is = VillagerConfig.class.getResourceAsStream(path)) {
         if (is == null) {
            LOGGER.debug("Overlay config not found (skipping): {}", path);
            return null;
         }

         InputStreamReader reader = new InputStreamReader(is);

         JsonObject root;
         try {
            root = JsonParser.parseReader(reader).getAsJsonObject();
         } catch (Throwable var11) {
            try {
               reader.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }

            throw var11;
         }

         reader.close();
         LinkedHashMap var15 = new LinkedHashMap<>(DEFAULT.foodGrowth);
         LinkedHashMap conceptionMap = new LinkedHashMap<>(DEFAULT.foodConception);
         if (root.has("food_growth_overrides")) {
            applyOverrides(var15, root.getAsJsonObject("food_growth_overrides"));
         }

         if (root.has("food_conception_overrides")) {
            applyOverrides(conceptionMap, root.getAsJsonObject("food_conception_overrides"));
         }

         Map<Item, Integer> sortedGrowth = sortByDescendingValue(var15);
         Map<Item, Integer> sortedConception = sortByDescendingValue(conceptionMap);
         return new VillagerConfig(sortedGrowth, sortedConception);
      } catch (Exception e) {
         LOGGER.error("Error loading overlay config '{}'", name, e);
         return null;
      }
   }

   private static void applyOverrides(Map<Item, Integer> target, JsonObject overrides) {
      for (java.util.Map.Entry<String, JsonElement> e : overrides.entrySet()) {
         String key = e.getKey();
         if (!key.startsWith("_")) {
            Item item = ItemHelper.resolve(key);
            if (item != null && item != Items.AIR) {
               int value = e.getValue().getAsInt();
               if (value <= 0) {
                  target.remove(item);
               } else {
                  target.put(item, value);
               }
            }
         }
      }
   }

   private static Map<Item, Integer> sortByDescendingValue(Map<Item, Integer> map) {
      record Entry(Item item, int value) {
      }

      List<Entry> entries = new ArrayList<>();

      for (java.util.Map.Entry<Item, Integer> e : map.entrySet()) {
         entries.add(new Entry(e.getKey(), e.getValue()));
      }

      entries.sort((a, b) -> Integer.compare(b.value, a.value));
      LinkedHashMap<Item, Integer> result = new LinkedHashMap<>();

      for (Entry entry : entries) {
         result.put(entry.item, entry.value);
      }

      return result;
   }

   private static Map<Item, Integer> parseItemMap(JsonObject obj) {
      record Entry(Item item, int value) {
      }

      List<Entry> entries = new ArrayList<>();

      for (java.util.Map.Entry<String, JsonElement> e : obj.entrySet()) {
         String key = e.getKey();
         if (!key.startsWith("_")) {
            Item item = ItemHelper.resolve(key);
            if (item != null && item != Items.AIR) {
               int value = e.getValue().getAsInt();
               entries.add(new Entry(item, value));
            }
         }
      }

      entries.sort((a, b) -> Integer.compare(b.value, a.value));
      LinkedHashMap<Item, Integer> result = new LinkedHashMap<>();

      for (Entry entry : entries) {
         result.put(entry.item, entry.value);
      }

      return result;
   }

   public static boolean isBuildVillagePaths() {
      return buildVillagePaths;
   }
}
