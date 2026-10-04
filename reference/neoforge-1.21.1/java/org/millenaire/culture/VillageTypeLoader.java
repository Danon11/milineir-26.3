package org.millenaire.culture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.content.ContentFs;
import org.millenaire.village.BrickColourTheme;
import org.millenaire.world.PlacementConstraints;
import org.slf4j.Logger;

final class VillageTypeLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<ResourceLocation, String> SEEN_VILLAGE_IDS = new HashMap<>();
   private static final Set<ResourceLocation> FAILED_CULTURES = new HashSet<>();

   private VillageTypeLoader() {
   }

   static void resetCrossFolderTracking() {
      SEEN_VILLAGE_IDS.clear();
      FAILED_CULTURES.clear();
   }

   static void loadFromContentFs(String contentId, ContentFs cultureFs, String relPath, String fallbackName) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", contentId);
      JsonObject json = CultureLoader.readJsonFromContentFs(cultureFs, relPath);
      if (json != null) {
         loadFromJson(id, relPath, json, fallbackName);
      }
   }

   static void loadFromClasspath(String contentId, String classpathPath, String fallbackName) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", contentId);
      JsonObject json = CultureLoader.readJson(classpathPath);
      if (json != null) {
         loadFromJson(id, classpathPath, json, fallbackName);
      }
   }

   private static void loadFromJson(ResourceLocation id, String pathKey, JsonObject json, String fallbackName) {
      try {
         ResourceLocation culture = ResourceLocation.parse(GsonHelper.getAsString(json, "culture"));
         if (FAILED_CULTURES.contains(culture)) {
            return;
         }

         String previousJsonPath = SEEN_VILLAGE_IDS.putIfAbsent(id, pathKey);
         if (previousJsonPath != null && !previousJsonPath.equals(pathKey)) {
            LOGGER.error(
               "[Millenaire] Duplicate village_type id '{}': loaded from '{}', later occurrence at '{}'. Cross-folder logical-id collisions are fatal — culture '{}' will not register this village type.",
               new Object[]{id, previousJsonPath, pathKey, culture}
            );
            FAILED_CULTURES.add(culture);
            throw new CrossFolderConflictException(
               culture, "Duplicate village_type id '" + id + "' (first at '" + previousJsonPath + "', second at '" + pathKey + "')"
            );
         }

         String villageName = GsonHelper.getAsString(json, "name", fallbackName);
         int weight = GsonHelper.getAsInt(json, "weight", 10);
         List<TagKey<Biome>> biomeTags = new ArrayList<>();
         if (json.has("biome_tags")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "biome_tags")) {
               String raw = e.getAsString();
               if (raw.startsWith("#")) {
                  raw = raw.substring(1);
               }

               biomeTags.add(TagKey.create(Registries.BIOME, ResourceLocation.parse(raw)));
            }
         }

         List<VillageType.LayoutSlot> layout = new ArrayList<>();

         for (JsonElement e : GsonHelper.getAsJsonArray(json, "layout")) {
            JsonObject slot = e.getAsJsonObject();
            ResourceLocation plan = ResourceLocation.parse(GsonHelper.getAsString(slot, "plan"));
            String role = GsonHelper.getAsString(slot, "role");
            BlockPos offset = null;
            Rotation rotation = null;
            if (slot.has("offset")) {
               JsonArray offsetArr = GsonHelper.getAsJsonArray(slot, "offset");
               offset = new BlockPos(offsetArr.get(0).getAsInt(), offsetArr.get(1).getAsInt(), offsetArr.get(2).getAsInt());
               rotation = Rotation.valueOf(GsonHelper.getAsString(slot, "rotation"));
            }

            double minDistance = slot.has("min_distance") ? slot.get("min_distance").getAsDouble() : -1.0;
            double maxDistance = slot.has("max_distance") ? slot.get("max_distance").getAsDouble() : -1.0;
            int priority = GsonHelper.getAsInt(slot, "priority", 10);
            int clearMargin = GsonHelper.getAsInt(slot, "clear_margin", PlacementConstraints.getDefaultClearMargin());
            Map<String, Integer> farFromTags = JsonLoaderUtils.parseStringIntMap(slot, "far_from_tags");
            Map<String, Integer> closeToTags = JsonLoaderUtils.parseStringIntMap(slot, "close_to_tags");
            Integer fixedOrientation = null;
            if (slot.has("fixed_orientation")) {
               fixedOrientation = BuildingPlanSetLoader.parseCardinalDirection(GsonHelper.getAsString(slot, "fixed_orientation"));
            }

            layout.add(
               new VillageType.LayoutSlot(
                  plan, offset, rotation, role, minDistance, maxDistance, priority, farFromTags, closeToTags, clearMargin, fixedOrientation
               )
            );
         }

         Map<String, Integer> sellingPriceOverrides = JsonLoaderUtils.parseStringIntMap(json, "selling_price_overrides");
         Map<String, Integer> buyingPriceOverrides = JsonLoaderUtils.parseStringIntMap(json, "buying_price_overrides");
         int maxSimultaneousConstructions = json.has("max_simultaneous_constructions") ? GsonHelper.getAsInt(json, "max_simultaneous_constructions") : 1;
         List<String> qualifiers = json.has("qualifiers") ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "qualifiers")) : List.of();
         String forestQualifier = json.has("forest_qualifier") ? GsonHelper.getAsString(json, "forest_qualifier") : null;
         String hillQualifier = json.has("hill_qualifier") ? GsonHelper.getAsString(json, "hill_qualifier") : null;
         String mountainQualifier = json.has("mountain_qualifier") ? GsonHelper.getAsString(json, "mountain_qualifier") : null;
         String desertQualifier = json.has("desert_qualifier") ? GsonHelper.getAsString(json, "desert_qualifier") : null;
         String lavaQualifier = json.has("lava_qualifier") ? GsonHelper.getAsString(json, "lava_qualifier") : null;
         String lakeQualifier = json.has("lake_qualifier") ? GsonHelper.getAsString(json, "lake_qualifier") : null;
         String oceanQualifier = json.has("ocean_qualifier") ? GsonHelper.getAsString(json, "ocean_qualifier") : null;
         List<ResourceLocation> playerBuildings = new ArrayList<>();
         if (json.has("player_buildings")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "player_buildings")) {
               playerBuildings.add(ResourceLocation.parse(e.getAsString()));
            }
         }

         List<BrickColourTheme> brickColourThemes = new ArrayList<>();
         if (json.has("brick_colour_themes")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "brick_colour_themes")) {
               brickColourThemes.add(parseBrickColourTheme(e.getAsJsonObject()));
            }
         }

         List<String> neverBuildings = new ArrayList<>();
         if (json.has("never")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "never")) {
               neverBuildings.add(e.getAsString());
            }
         }

         boolean loneBuilding = GsonHelper.getAsBoolean(json, "lone_building", false);
         int minDistanceFromSpawn = GsonHelper.getAsInt(json, "min_distance_from_spawn", -1);
         int maxLB = GsonHelper.getAsInt(json, "max", -1);
         boolean keyLoneBuilding = GsonHelper.getAsBoolean(json, "key_lone_building", false);
         String keyLoneBuildingGenerateTag = json.has("key_lone_building_generate_tag") ? GsonHelper.getAsString(json, "key_lone_building_generate_tag") : null;
         boolean generatedForPlayer = GsonHelper.getAsBoolean(json, "generated_for_player", false);
         boolean spawnableLB = GsonHelper.getAsBoolean(json, "spawnable", !loneBuilding);
         boolean showTownHallSigns = GsonHelper.getAsBoolean(json, "show_town_hall_signs", !loneBuilding);
         String nameListVal;
         if (json.has("name_list")) {
            nameListVal = GsonHelper.getAsString(json, "name_list");
         } else if (json.has("namelist")) {
            nameListVal = GsonHelper.getAsString(json, "namelist");
         } else {
            nameListVal = loneBuilding ? null : "villages";
         }

         int radius = GsonHelper.getAsInt(json, "radius", 90);
         int radiusOverride = MillenaireServerConfig.SERVER.villageRadiusOverride.getAsInt();
         if (radiusOverride > 0) {
            radius = radiusOverride;
         }

         float minimumBiomeValidity = GsonHelper.getAsFloat(json, "minimum_biome_validity", 0.6F);
         List<String> pathMaterials = json.has("path_materials")
            ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "path_materials"))
            : List.of("pathgravel");
         boolean travelBookDisplay = GsonHelper.getAsBoolean(json, "travel_book_display", true);
         List<ResourceLocation> hamlets = new ArrayList<>();
         if (json.has("hamlets")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "hamlets")) {
               hamlets.add(ResourceLocation.parse(e.getAsString()));
            }
         }

         String specialType = json.has("special_type") ? GsonHelper.getAsString(json, "special_type") : null;
         String vtIcon = json.has("icon") ? GsonHelper.getAsString(json, "icon") : null;
         boolean allowExtraBuildings;
         if (json.has("allow_extra_buildings")) {
            allowExtraBuildings = GsonHelper.getAsBoolean(json, "allow_extra_buildings");
         } else {
            allowExtraBuildings = !loneBuilding && specialType == null;
         }

         boolean playerControlled = GsonHelper.getAsBoolean(json, "player_controlled", false);
         if (playerControlled) {
            allowExtraBuildings = false;
         }

         ResourceLocation outerWallType = json.has("outer_wall_type")
            ? WallTypeLoader.resolveWallTypeRL(GsonHelper.getAsString(json, "outer_wall_type"), culture)
            : null;
         ResourceLocation innerWallType = json.has("inner_wall_type")
            ? WallTypeLoader.resolveWallTypeRL(GsonHelper.getAsString(json, "inner_wall_type"), culture)
            : null;
         int innerWallRadius = GsonHelper.getAsInt(json, "inner_wall_radius", 0);
         int maxSimultaneousWallConstructions = GsonHelper.getAsInt(json, "max_simultaneous_wall_constructions", 1);
         List<String> bannerJsons = json.has("banner_json") ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "banner_json")) : List.of();
         ModCultures.registerVillageType(
            new VillageType(
               id,
               culture,
               villageName,
               weight,
               biomeTags,
               layout,
               sellingPriceOverrides,
               buyingPriceOverrides,
               maxSimultaneousConstructions,
               qualifiers,
               forestQualifier,
               hillQualifier,
               mountainQualifier,
               desertQualifier,
               lavaQualifier,
               lakeQualifier,
               oceanQualifier,
               playerBuildings,
               brickColourThemes,
               neverBuildings,
               loneBuilding,
               minDistanceFromSpawn,
               maxLB,
               keyLoneBuilding,
               keyLoneBuildingGenerateTag,
               generatedForPlayer,
               spawnableLB,
               showTownHallSigns,
               nameListVal,
               radius,
               minimumBiomeValidity,
               pathMaterials,
               travelBookDisplay,
               hamlets,
               specialType,
               allowExtraBuildings,
               vtIcon,
               playerControlled,
               outerWallType,
               innerWallType,
               innerWallRadius,
               maxSimultaneousWallConstructions,
               bannerJsons
            )
         );
      } catch (CrossFolderConflictException e) {
         throw e;
      } catch (Exception e) {
         LOGGER.error("Error parsing village type {}: {}", new Object[]{id, e.getMessage(), e});
      }
   }

   static BrickColourTheme parseBrickColourTheme(JsonObject json) {
      String name = GsonHelper.getAsString(json, "name");
      int weight = GsonHelper.getAsInt(json, "weight");
      JsonObject coloursObj = GsonHelper.getAsJsonObject(json, "colours");
      List<BrickColourTheme.WeightedColor> otherPool = null;
      if (coloursObj.has("other")) {
         otherPool = CultureLoader.parseWeightedColorList(GsonHelper.getAsJsonArray(coloursObj, "other"));
      }

      EnumMap<DyeColor, List<BrickColourTheme.WeightedColor>> colorPools = new EnumMap<>(DyeColor.class);

      for (Entry<String, JsonElement> entry : coloursObj.entrySet()) {
         if (!entry.getKey().equals("other")) {
            DyeColor inputColor = CultureLoader.parseDyeColor(entry.getKey());
            if (inputColor != null) {
               colorPools.put(inputColor, CultureLoader.parseWeightedColorList(entry.getValue().getAsJsonArray()));
            }
         }
      }

      if (otherPool != null) {
         for (DyeColor color : DyeColor.values()) {
            if (!colorPools.containsKey(color)) {
               colorPools.put(color, otherPool);
            }
         }
      }

      return new BrickColourTheme(name, weight, colorPools);
   }
}
