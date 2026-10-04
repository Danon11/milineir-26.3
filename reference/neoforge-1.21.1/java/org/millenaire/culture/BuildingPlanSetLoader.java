package org.millenaire.culture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;
import org.millenaire.building.BuildingCostCalculator;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.SpecialPoint;
import org.millenaire.building.SpecialPointsLoader;
import org.millenaire.building.TemplateLoader;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.village.BrickColourTheme;
import org.slf4j.Logger;

final class BuildingPlanSetLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<ResourceLocation, String> SEEN_BUILDING_IDS = new HashMap<>();
   private static final Set<ResourceLocation> FAILED_CULTURES = new HashSet<>();

   private BuildingPlanSetLoader() {
   }

   static void resetCrossFolderTracking() {
      SEEN_BUILDING_IDS.clear();
      FAILED_CULTURES.clear();
   }

   static void loadFromContentFs(String contentId, ContentFs cultureFs, String relPath, String pathCategory) {
      String culture = contentId.contains("/") ? contentId.substring(0, contentId.indexOf(47)) : null;
      String synthClasspath = culture != null ? "/millenaire/cultures/" + culture + "/" + relPath : relPath;
      loadFromOverlay(contentId, synthClasspath, cultureFs, relPath, pathCategory, culture);
   }

   static void loadFromClasspath(String contentId, String classpathPath, String pathCategory) {
      String culturePathPrefix = extractCulturePrefix(classpathPath);
      if (culturePathPrefix != null) {
         ContentFs cultureFs = CustomContentIndex.current().forCulture(culturePathPrefix);
         String relPath = relativiseToCulture(classpathPath, culturePathPrefix);
         if (relPath != null) {
            loadFromOverlay(contentId, classpathPath, cultureFs, relPath, pathCategory, culturePathPrefix);
         }
      }
   }

   private static void loadFromOverlay(String contentId, String classpathPath, ContentFs cultureFs, String relPath, String pathCategory, String expectedCulture) {
      JsonObject json = CultureLoader.readJsonFromContentFs(cultureFs, relPath);
      if (json != null) {
         try {
            ResourceLocation culture = ResourceLocation.parse(GsonHelper.getAsString(json, "culture"));
            String buildingId = GsonHelper.getAsString(json, "building_id");
            String category = GsonHelper.getAsString(json, "category", "houses");
            if (expectedCulture != null && !culture.getPath().equals(expectedCulture)) {
               cultureFs = CustomContentIndex.current().forCulture(culture.getPath());
            }

            String jsonStem = filenameStem(classpathPath);
            if (jsonStem != null && !jsonStem.equalsIgnoreCase(buildingId)) {
               LOGGER.error(
                  "[Millenaire] Building plan {} — filename stem '{}' does not match building_id '{}'. Rename the file (or change the id) so they match. Run ./gradlew convertAll to regenerate.",
                  new Object[]{contentId, jsonStem, buildingId}
               );
               return;
            }

            if (pathCategory != null && !pathCategory.equals(category)) {
               LOGGER.warn(
                  "[Millenaire] Building plan {} has category '{}' in JSON but lives under path category '{}'. JSON field wins; consider moving the file to the matching folder.",
                  new Object[]{contentId, category, pathCategory}
               );
            }

            String nativeName = GsonHelper.getAsString(json, "native_name", buildingId);
            int maxCount = GsonHelper.getAsInt(json, "max_count", 1);
            double minDistance = json.has("min_distance") ? json.get("min_distance").getAsDouble() : 0.0;
            double maxDistance = json.has("max_distance") ? json.get("max_distance").getAsDouble() : 1.0;
            List<String> maleResidents;
            if (json.has("male_residents")) {
               maleResidents = JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "male_residents"));
            } else if (json.has("male") && !json.get("male").isJsonNull()) {
               maleResidents = CultureLoader.parseStringOrArray(json.get("male"));
            } else {
               maleResidents = List.of();
            }

            List<String> femaleResidents;
            if (json.has("female_residents")) {
               femaleResidents = JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "female_residents"));
            } else if (json.has("female") && !json.get("female").isJsonNull()) {
               femaleResidents = CultureLoader.parseStringOrArray(json.get("female"));
            } else {
               femaleResidents = List.of();
            }

            int priorityMoveIn = GsonHelper.getAsInt(json, "priority_move_in", -1);
            List<String> tags = json.has("tags") ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "tags")) : List.of();
            String terrainPolicy = GsonHelper.getAsString(json, "terrain_policy", "clear_and_flatten");
            String constructionOrder = GsonHelper.getAsString(json, "construction_order", "bottom_up");
            String culturePrefix = culture.getPath();
            ResourceLocation setId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePrefix + "/" + buildingId);
            if (FAILED_CULTURES.contains(culture)) {
               return;
            }

            String previousJsonPath = SEEN_BUILDING_IDS.putIfAbsent(setId, classpathPath);
            if (previousJsonPath != null && !previousJsonPath.equals(classpathPath)) {
               LOGGER.error(
                  "[Millenaire] Duplicate building_id '{}': loaded from '{}', later occurrence at '{}'. Cross-folder logical-id collisions are fatal — culture '{}' will not be registered. Move or rename one of the files so each building_id is unique within the culture.",
                  new Object[]{setId, previousJsonPath, classpathPath, culture}
               );
               FAILED_CULTURES.add(culture);
               throw new CrossFolderConflictException(
                  culture, "Duplicate building_id '" + setId + "' (first at '" + previousJsonPath + "', second at '" + classpathPath + "')"
               );
            }

            String nbtPathParent = nbtPathParentForCulturePath(classpathPath, culturePrefix);
            List<BuildingPlan> pendingPlans = new ArrayList<>();
            Map<String, List<BuildingPlanSet.LevelDef>> variants = new LinkedHashMap<>();

            for (JsonElement variantEl : GsonHelper.getAsJsonArray(json, "variants")) {
               JsonObject variantObj = variantEl.getAsJsonObject();
               String variant = GsonHelper.getAsString(variantObj, "variant");
               List<BuildingPlanSet.LevelDef> levels = new ArrayList<>();
               int variantGroundLevel = GsonHelper.getAsInt(variantObj, "ground_level", 0);
               List<String> variantTags = variantObj.has("tags") ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(variantObj, "tags")) : null;
               int variantOrientation = resolveVariantOrientation(json, variantObj);
               int prevPathLevel = 0;
               int prevPathWidth = 2;

               for (JsonElement levelEl : GsonHelper.getAsJsonArray(variantObj, "levels")) {
                  JsonObject levelObj = levelEl.getAsJsonObject();
                  int level = GsonHelper.getAsInt(levelObj, "level");
                  if (levelObj.has("template")) {
                     LOGGER.warn(
                        "[Millenaire] Building plan {} carries the obsolete 'template:' field at variant '{}' level {} — ignored, implicit NBT path '{}_{}_{}' is used instead. Re-run the converter (or delete the deployed millenaire/ directory to force redeployment) to silence this warning.",
                        new Object[]{classpathPath, variant, level, buildingId, variant, level}
                     );
                  }

                  String nbtPath = nbtPathParent.isEmpty()
                     ? buildingId + "_" + variant + "_" + level
                     : nbtPathParent + "/" + buildingId + "_" + variant + "_" + level;
                  JsonObject footprint = GsonHelper.getAsJsonObject(levelObj, "footprint");
                  int width = GsonHelper.getAsInt(footprint, "width");
                  int height = GsonHelper.getAsInt(footprint, "height");
                  int depth = GsonHelper.getAsInt(footprint, "depth");
                  int groundLevel = GsonHelper.getAsInt(levelObj, "ground_level", variantGroundLevel);
                  int priority = GsonHelper.getAsInt(levelObj, "priority", 100);
                  String levelName = levelObj.has("native_name") ? GsonHelper.getAsString(levelObj, "native_name") : null;
                  ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePrefix + "/" + buildingId + "_" + variant + "_" + level);
                  List<String> levelTags;
                  if (levelObj.has("tags")) {
                     levelTags = JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "tags"));
                  } else if (variantTags != null) {
                     levelTags = variantTags;
                  } else {
                     levelTags = List.of();
                  }

                  List<String> requiredTags = levelObj.has("required_tags")
                     ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "required_tags"))
                     : List.of();
                  List<String> forbiddenTagsInVillage = levelObj.has("forbidden_tags_in_village")
                     ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "forbidden_tags_in_village"))
                     : List.of();
                  List<String> requiredVillageTags = levelObj.has("required_village_tags")
                     ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "required_village_tags"))
                     : List.of();
                  List<String> parentTags = levelObj.has("parent_tags")
                     ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "parent_tags"))
                     : List.of();
                  List<String> requiredParentTags = levelObj.has("required_parent_tags")
                     ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "required_parent_tags"))
                     : List.of();
                  List<String> clearTags = levelObj.has("clear_tags")
                     ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "clear_tags"))
                     : List.of();
                  List<String> villageTags = levelObj.has("village_tags")
                     ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "village_tags"))
                     : List.of();
                  List<String> levelSubBuildings = levelObj.has("sub_buildings")
                     ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(levelObj, "sub_buildings"))
                     : List.of();
                  Optional<Resource> nbtResource = cultureFs.findFirst(nbtPath + ".nbt");
                  if (nbtResource.isEmpty()) {
                     LOGGER.warn(
                        "[Millenaire] Plan set '{}' for culture '{}' dropped because NBT missing for variant='{}' level={} (expected at: {}.nbt). Run ./gradlew convertAll to regenerate or ship the missing file.",
                        new Object[]{setId, culture, variant, level, nbtPath}
                     );
                     return;
                  }

                  Resource res = nbtResource.get();
                  NbtAccounter accounter = TemplateLoader.accounterFor(res.kind());

                  CompoundTag templateNbt;
                  try (InputStream nbtIs = res.open()) {
                     templateNbt = NbtIo.readCompressed(nbtIs, accounter);
                  } catch (Exception e) {
                     LOGGER.warn(
                        "[Millenaire] Plan set '{}' for culture '{}' dropped because NBT at '{}.nbt' could not be read for variant='{}' level={}: {}",
                        new Object[]{setId, culture, nbtPath, variant, level, e.getMessage()}
                     );
                     return;
                  }

                  Map<ResourceLocation, Integer> requiredResources = BuildingCostCalculator.computeCost(templateNbt);
                  int[] signOrder = null;
                  if (levelObj.has("signs")) {
                     String signsStr = GsonHelper.getAsString(levelObj, "signs");
                     String[] parts = signsStr.split(",");
                     signOrder = new int[parts.length];

                     for (int s = 0; s < parts.length; s++) {
                        signOrder[s] = Integer.parseInt(parts[s].trim());
                     }
                  }

                  BuildingPlanSetLoader.PathFields pathFields = resolvePathFields(levelObj, prevPathLevel, prevPathWidth);
                  int pathLevel = pathFields.pathLevel();
                  boolean rebuildPath = pathFields.rebuildPath();
                  int pathWidth = pathFields.pathWidth();
                  prevPathLevel = pathLevel;
                  prevPathWidth = pathWidth;
                  Map<String, Integer> abstractedProduction = new HashMap<>();
                  if (levelObj.has("abstracted_production")) {
                     for (JsonElement apElem : GsonHelper.getAsJsonArray(levelObj, "abstracted_production")) {
                        String entry = apElem.getAsString();
                        String[] apParts = entry.split(",");
                        if (apParts.length == 2) {
                           abstractedProduction.put(apParts[0].trim(), Integer.parseInt(apParts[1].trim()));
                        }
                     }
                  }

                  levels.add(
                     new BuildingPlanSet.LevelDef(
                        level,
                        planId,
                        nbtPath,
                        width,
                        height,
                        depth,
                        groundLevel,
                        priority,
                        levelName,
                        requiredTags,
                        forbiddenTagsInVillage,
                        requiredVillageTags,
                        parentTags,
                        requiredParentTags,
                        clearTags,
                        villageTags,
                        requiredResources,
                        levelSubBuildings,
                        signOrder,
                        pathLevel,
                        rebuildPath,
                        pathWidth,
                        Map.copyOf(abstractedProduction)
                     )
                  );
                  ResourceLocation legacyTemplateKey = ResourceLocation.fromNamespaceAndPath(
                     "millenaire", culturePrefix + "/" + buildingId + "_" + variant + "_" + level
                  );
                  List<SpecialPoint> specialPoints = SpecialPointsLoader.load(legacyTemplateKey, templateNbt);
                  if (specialPoints.isEmpty()) {
                     specialPoints = SpecialPointsLoader.load(planId, nbtPath, cultureFs);
                  }

                  BlockPos entryOffset = BlockPos.ZERO;
                  String shopId = json.has("shop") ? GsonHelper.getAsString(json, "shop") : null;
                  pendingPlans.add(
                     new BuildingPlan(
                        planId,
                        culture,
                        nbtPath,
                        width,
                        height,
                        depth,
                        groundLevel,
                        variantOrientation,
                        entryOffset,
                        levelTags,
                        constructionOrder,
                        terrainPolicy,
                        specialPoints,
                        shopId
                     )
                  );
               }

               variants.put(variant, levels);
            }

            if (priorityMoveIn < 0) {
               label328:
               for (JsonElement variantEl2 : GsonHelper.getAsJsonArray(json, "variants")) {
                  for (JsonElement levelEl2 : GsonHelper.getAsJsonArray(variantEl2.getAsJsonObject(), "levels")) {
                     JsonObject lo = levelEl2.getAsJsonObject();
                     if (lo.has("priority_move_in")) {
                        priorityMoveIn = GsonHelper.getAsInt(lo, "priority_move_in");
                        break label328;
                     }
                  }
               }

               if (priorityMoveIn < 0) {
                  priorityMoveIn = 10;
               }
            }

            List<String> startingSubBuildings = json.has("starting_sub_buildings")
               ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "starting_sub_buildings"))
               : List.of();
            String icon = json.has("icon") ? GsonHelper.getAsString(json, "icon") : null;
            int areaToClear = GsonHelper.getAsInt(json, "area_to_clear", 0);
            int atcLengthBefore = GsonHelper.getAsInt(json, "area_to_clear_length_before", -1);
            int atcLengthAfter = GsonHelper.getAsInt(json, "area_to_clear_length_after", -1);
            int atcWidthBefore = GsonHelper.getAsInt(json, "area_to_clear_width_before", -1);
            int atcWidthAfter = GsonHelper.getAsInt(json, "area_to_clear_width_after", -1);
            ClearMargins clearMargins = ClearMargins.fromLegacy(areaToClear, atcLengthBefore, atcLengthAfter, atcWidthBefore, atcWidthAfter);
            int price = GsonHelper.getAsInt(json, "price", 0);
            int reputation = GsonHelper.getAsInt(json, "reputation", 0);
            Map<DyeColor, List<BrickColourTheme.WeightedColor>> randomBrickColours = new EnumMap<>(DyeColor.class);
            if (json.has("random_brick_colours")) {
               JsonObject rbcObj = GsonHelper.getAsJsonObject(json, "random_brick_colours");

               for (Entry<String, JsonElement> entry : rbcObj.entrySet()) {
                  DyeColor inputColor = CultureLoader.parseDyeColor(entry.getKey());
                  if (inputColor != null) {
                     randomBrickColours.put(inputColor, CultureLoader.parseWeightedColorList(entry.getValue().getAsJsonArray()));
                  }
               }
            }

            List<BuildingPlanSet.StartingGood> startingGoods = new ArrayList<>();
            if (json.has("starting_goods")) {
               for (JsonElement sgEl : GsonHelper.getAsJsonArray(json, "starting_goods")) {
                  JsonObject sgObj = sgEl.getAsJsonObject();
                  String sgItem = GsonHelper.getAsString(sgObj, "item");
                  double sgProb = sgObj.has("probability") ? sgObj.get("probability").getAsDouble() : 1.0;
                  int sgFixed = GsonHelper.getAsInt(sgObj, "fixed", 0);
                  int sgRandom = GsonHelper.getAsInt(sgObj, "random", 0);
                  startingGoods.add(new BuildingPlanSet.StartingGood(sgItem, sgProb, sgFixed, sgRandom));
               }
            }

            String travelBookCategory = json.has("travel_book_category") ? GsonHelper.getAsString(json, "travel_book_category") : null;
            boolean travelBookDisplay = GsonHelper.getAsBoolean(json, "travel_book_display", true);
            boolean isSubBuilding = GsonHelper.getAsBoolean(json, "is_sub_building", false);
            Map<String, Integer> farFromTags = JsonLoaderUtils.parseCommaSeparatedPairs(json, "far_from_tags");
            Map<String, Integer> closeToTags = JsonLoaderUtils.parseCommaSeparatedPairs(json, "close_to_tags");
            Integer fixedOrientation = null;
            if (json.has("fixed_orientation")) {
               fixedOrientation = parseCardinalDirection(GsonHelper.getAsString(json, "fixed_orientation"));
            }

            boolean isWallSegment = GsonHelper.getAsBoolean(json, "is_wall_segment", false);
            boolean isBorderBuilding = GsonHelper.getAsBoolean(json, "is_border_building", false);
            int extraWallConstructionSlots = GsonHelper.getAsInt(json, "extra_wall_construction_slots", 0);
            boolean isTownHall;
            if (json.has("is_town_hall")) {
               isTownHall = GsonHelper.getAsBoolean(json, "is_town_hall", false);
            } else if ("townhalls".equals(category)) {
               isTownHall = !isSubBuilding;
               LOGGER.warn(
                  "[Millenaire] {} has category 'townhalls' but no is_town_hall field — assuming {}. Re-run the converter to fix.", contentId, isTownHall
               );
            } else if ("lone".equals(category) && !isSubBuilding) {
               isTownHall = true;
            } else if (isSubBuilding || !tags.contains("playerth") && !tags.contains("townhall") && !tags.contains("marvelth")) {
               isTownHall = false;
            } else {
               isTownHall = true;
               LOGGER.warn(
                  "[Millenaire] {} inferred is_town_hall=true from legacy TH tag (one of playerth/townhall/marvelth). Add \"is_town_hall\": true to the JSON to silence this warning.",
                  contentId
               );
            }

            BuildingPlanSet planSet = new BuildingPlanSet(
               setId,
               culture,
               buildingId,
               category,
               nativeName,
               maxCount,
               minDistance,
               maxDistance,
               maleResidents,
               femaleResidents,
               priorityMoveIn,
               tags,
               terrainPolicy,
               constructionOrder,
               variants,
               startingSubBuildings,
               icon,
               clearMargins,
               price,
               reputation,
               randomBrickColours,
               startingGoods,
               travelBookCategory,
               travelBookDisplay,
               isSubBuilding,
               isTownHall,
               farFromTags,
               closeToTags,
               fixedOrientation,
               isWallSegment,
               isBorderBuilding,
               extraWallConstructionSlots
            );
            List<BuildingPlan> flushed = new ArrayList<>(pendingPlans.size());

            try {
               for (BuildingPlan p : pendingPlans) {
                  ModCultures.registerBuildingPlan(p);
                  flushed.add(p);
               }

               ModCultures.registerBuildingPlanSet(planSet);
            } catch (RuntimeException flushFailure) {
               for (BuildingPlan p : flushed) {
                  ModCultures.unregisterBuildingPlan(p.id());
               }

               throw flushFailure;
            }
         } catch (CrossFolderConflictException e) {
            throw e;
         } catch (Exception e) {
            LOGGER.error("Error parsing building plan set {}: {}", new Object[]{contentId, e.getMessage(), e});
         }
      }
   }

   static BuildingPlanSetLoader.PathFields resolvePathFields(JsonObject levelObj, int prevPathLevel, int prevPathWidth) {
      int pathLevel = levelObj.has("path_level") ? GsonHelper.getAsInt(levelObj, "path_level") : prevPathLevel;
      boolean rebuildPath = GsonHelper.getAsBoolean(levelObj, "rebuild_path", false);
      int pathWidth = levelObj.has("path_width") ? GsonHelper.getAsInt(levelObj, "path_width") : prevPathWidth;
      return new BuildingPlanSetLoader.PathFields(pathLevel, rebuildPath, pathWidth);
   }

   static int resolveVariantOrientation(JsonObject planSetJson, JsonObject variantJson) {
      int planSetOrientation = GsonHelper.getAsInt(planSetJson, "building_orientation", 1);
      return variantJson.has("building_orientation") ? GsonHelper.getAsInt(variantJson, "building_orientation") : planSetOrientation;
   }

   static String filenameStem(String path) {
      if (path != null && !path.isEmpty()) {
         int slash = Math.max(path.lastIndexOf(47), path.lastIndexOf(92));
         String name = slash >= 0 ? path.substring(slash + 1) : path;
         return !name.toLowerCase(Locale.ROOT).endsWith(".json") ? name : name.substring(0, name.length() - ".json".length());
      } else {
         return null;
      }
   }

   @Nullable
   static String extractCulturePrefix(String classpathPath) {
      if (classpathPath == null) {
         return null;
      }

      String marker = "cultures/";
      int idx = classpathPath.indexOf(marker);
      if (idx < 0) {
         return null;
      }

      String afterMarker = classpathPath.substring(idx + marker.length());
      int slash = afterMarker.indexOf(47);
      return slash <= 0 ? null : afterMarker.substring(0, slash);
   }

   @Nullable
   static String relativiseToCulture(String classpathPath, String culture) {
      if (classpathPath != null && culture != null) {
         String marker = "cultures/" + culture + "/";
         int idx = classpathPath.indexOf(marker);
         return idx < 0 ? null : classpathPath.substring(idx + marker.length());
      } else {
         return null;
      }
   }

   static String nbtPathParentForCulturePath(String classpathPath, String culturePrefix) {
      if (classpathPath != null && culturePrefix != null) {
         String marker = "cultures/" + culturePrefix + "/";
         int idx = classpathPath.indexOf(marker);
         if (idx < 0) {
            return "";
         }

         String afterCulture = classpathPath.substring(idx + marker.length());
         int lastSlash = afterCulture.lastIndexOf(47);
         return lastSlash < 0 ? "" : afterCulture.substring(0, lastSlash);
      } else {
         return "";
      }
   }

   static Integer parseCardinalDirection(String direction) {
      return switch (direction.toLowerCase(Locale.ROOT)) {
         case "north" -> 0;
         case "east" -> 1;
         case "south" -> 2;
         case "west" -> 3;
         default -> {
            LOGGER.warn("Unknown fixed_orientation '{}', defaulting to north (0)", direction);
            yield 0;
         }
      };
   }

   record PathFields(int pathLevel, boolean rebuildPath, int pathWidth) {
   }
}
