package org.millenaire.content.legacy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import javax.imageio.ImageIO;
import org.millenaire.building.MockBlockExtractor;
import org.millenaire.building.SpecialPoint;

public final class LegacyJsonBuilder {
   private static final Map<String, String> LEGACY_TAG_FIXES = Map.of("helpinattacks", "helpInAttacks", "HelpInAttacks", "helpInAttacks", "leasure", "leisure");
   private static final Map<String, List<String>> VILLAGER_EXTRA_GOALS = Map.of("smelter_byzantine", List.of("millenaire:cook_iron_ore"));
   private static final Set<String> SKIPPED_GIFT_PLANS = Set.of("gifthouse");
   public static final Map<String, String> LEGACY_TO_GOOD_ID;
   public static final Map<String, String> LEGACY_ITEM_ID_MAP;
   public static final Map<String, String> LEGACY_CROP_BLOCK_MAP;
   public static final Map<String, String> LEGACY_FLOWER_MAP = Map.ofEntries(
      Map.entry("red_flower;type=poppy", "minecraft:poppy"),
      Map.entry("red_flower;type=blue_orchid", "minecraft:blue_orchid"),
      Map.entry("red_flower;type=red_tulip", "minecraft:red_tulip"),
      Map.entry("red_flower;type=white_tulip", "minecraft:white_tulip"),
      Map.entry("red_flower;type=pink_tulip", "minecraft:pink_tulip"),
      Map.entry("red_flower;type=orange_tulip", "minecraft:orange_tulip"),
      Map.entry("red_flower;type=allium", "minecraft:allium"),
      Map.entry("red_flower;type=houstonia", "minecraft:azure_bluet"),
      Map.entry("red_flower;type=oxeye_daisy", "minecraft:oxeye_daisy"),
      Map.entry("yellow_flower;type=dandelion", "minecraft:dandelion"),
      Map.entry("double_plant;type=rose", "minecraft:rose_bush"),
      Map.entry("double_plant;facing=north,half=lower,variant=double_rose", "minecraft:rose_bush"),
      Map.entry("double_plant;type=sunflower", "minecraft:sunflower")
   );
   public static final Map<String, String> CROP_SOIL_MAP = Map.of(
      "minecraft:wheat",
      "minecraft:farmland",
      "minecraft:carrots",
      "minecraft:farmland",
      "minecraft:potatoes",
      "minecraft:farmland",
      "millenaire:crop_rice",
      "millenaire:rice_paddy",
      "millenaire:crop_cotton",
      "minecraft:farmland",
      "millenaire:crop_turmeric",
      "minecraft:farmland",
      "millenaire:crop_vine",
      "minecraft:farmland",
      "millenaire:crop_maize",
      "minecraft:farmland"
   );
   public static final Map<String, String> CROP_SOIL_SUBTYPE_MAP = Map.of(
      "minecraft:wheat",
      "wheat",
      "minecraft:carrots",
      "carrot",
      "minecraft:potatoes",
      "potato",
      "millenaire:crop_rice",
      "rice",
      "millenaire:crop_cotton",
      "cotton",
      "millenaire:crop_turmeric",
      "turmeric",
      "millenaire:crop_vine",
      "vine",
      "millenaire:crop_maize",
      "maize"
   );
   public static final Map<String, String> CROP_SEED_MAP = Map.of("minecraft:wheat", "minecraft:wheat_seeds");
   public static final Map<String, String> MINING_SOURCE_MAP;
   public static final Map<String, String> LEGACY_ANIMAL_MAP = Map.of(
      "cow", "minecraft:cow", "pig", "minecraft:pig", "sheep", "minecraft:sheep", "chicken", "minecraft:chicken", "squid", "minecraft:squid"
   );
   public static final Map<String, String[]> LEGACY_COOKING_MAP;

   private LegacyJsonBuilder() {
   }

   private static void putIfNot(Map<String, Object> map, String key, Object value, Object defaultValue) {
      if (!Objects.equals(value, defaultValue)) {
         map.put(key, value);
      }
   }

   static String translateLegacyCardinal(String legacyName) {
      return switch (legacyName.toLowerCase(Locale.ROOT)) {
         case "north" -> "west";
         case "west" -> "south";
         case "south" -> "east";
         case "east" -> "north";
         default -> legacyName;
      };
   }

   public static Map<String, Object> buildBuildingPlan(
      LegacyDataParser.BuildingWithVariants building, String culture, ItemIdMapper items, Map<String, PngToNbtConverter.ConversionResult> conversionResults
   ) {
      return buildBuildingPlan(building, culture, items, conversionResults, Set.of());
   }

   public static Map<String, Object> buildBuildingPlan(
      LegacyDataParser.BuildingWithVariants building,
      String culture,
      ItemIdMapper items,
      Map<String, PngToNbtConverter.ConversionResult> conversionResults,
      Set<String> centralBuildingsForCulture
   ) {
      LegacyDataParser.BuildingMeta meta = building.meta();
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("culture", "millenaire:" + culture);
      json.put("building_id", sanitize(meta.baseName()));
      json.put("category", meta.category());
      if (meta.isSubBuilding()) {
         json.put("is_sub_building", true);
      }

      if (meta.isWallSegment()) {
         json.put("is_wall_segment", true);
      }

      if (meta.isBorderBuilding()) {
         json.put("is_border_building", true);
      }

      boolean hasMarvelThTag = meta.levels().stream().anyMatch(lm -> lm.tags().contains("marvelth"));
      boolean isCentralInVillage = centralBuildingsForCulture.contains(sanitize(meta.baseName()));
      if ("townhalls".equals(meta.category()) || hasMarvelThTag || isCentralInVillage) {
         json.put("is_town_hall", !meta.isSubBuilding());
      }

      json.put("native_name", meta.levels().getFirst().nativeName());
      if (meta.shop() != null) {
         json.put("shop", LegacyIdCanonicaliser.shopRefId(meta.shop()));
      }

      if (meta.maxCount() != 1) {
         json.put("max_count", meta.maxCount());
      }

      if (meta.minDistance() != 0.0) {
         json.put("min_distance", meta.minDistance());
      }

      if (meta.maxDistance() != 1.0) {
         json.put("max_distance", meta.maxDistance());
      }

      if (!meta.males().isEmpty()) {
         json.put("male", meta.males().stream().map(id -> LegacyIdCanonicaliser.villagerTypeRefId(stripCulturePrefix(culture, id))).toList());
      }

      if (!meta.females().isEmpty()) {
         json.put("female", meta.females().stream().map(id -> LegacyIdCanonicaliser.villagerTypeRefId(stripCulturePrefix(culture, id))).toList());
      }

      if (!meta.tags().isEmpty()) {
         json.put("tags", meta.tags().stream().map(LegacyJsonBuilder::normalizeLegacyTag).toList());
      }

      if (!meta.startingSubBuildings().isEmpty()) {
         json.put("starting_sub_buildings", meta.startingSubBuildings().stream().map(LegacyIdCanonicaliser::buildingPlanRefId).toList());
      }

      if (meta.icon() != null) {
         json.put("icon", items.resolve(culture, meta.icon()).orElse(meta.icon()));
      }

      json.put("building_orientation", Math.floorMod(3 - meta.buildingOrientation(), 4));
      if (meta.fixedOrientation() != null) {
         json.put("fixed_orientation", translateLegacyCardinal(meta.fixedOrientation()));
      }

      if (meta.areaToClear() > 0) {
         json.put("area_to_clear", meta.areaToClear());
      }

      if (meta.areaToClearLengthBefore() >= 0) {
         json.put("area_to_clear_length_before", meta.areaToClearLengthBefore());
      }

      if (meta.areaToClearLengthAfter() >= 0) {
         json.put("area_to_clear_length_after", meta.areaToClearLengthAfter());
      }

      if (meta.areaToClearWidthBefore() >= 0) {
         json.put("area_to_clear_width_before", meta.areaToClearWidthBefore());
      }

      if (meta.areaToClearWidthAfter() >= 0) {
         json.put("area_to_clear_width_after", meta.areaToClearWidthAfter());
      }

      if (!meta.farFromTags().isEmpty()) {
         json.put("far_from_tags", meta.farFromTags().stream().map(LegacyJsonBuilder::normalizeFarFromTag).toList());
      }

      if (meta.price() > 0) {
         json.put("price", meta.price());
      }

      if (meta.reputation() > 0) {
         json.put("reputation", meta.reputation());
      }

      if (!meta.randomBrickColours().isEmpty()) {
         Map<String, Object> rbcJson = new LinkedHashMap<>();

         for (String raw : meta.randomBrickColours()) {
            String[] parts = raw.split(";", 2);
            if (parts.length == 2) {
               String inputColor = remapLegacyColorName(parts[0].trim());
               String[] outputs = parts[1].split(",");
               List<Map<String, Object>> pool = new ArrayList<>();

               for (String out : outputs) {
                  String[] cw = out.trim().split(":");
                  if (cw.length == 2) {
                     Map<String, Object> entry = new LinkedHashMap<>();
                     entry.put("color", remapLegacyColorName(cw[0].trim()));
                     entry.put("weight", Integer.parseInt(cw[1].trim()));
                     pool.add(entry);
                  }
               }

               rbcJson.put(inputColor, pool);
            }
         }

         if (!rbcJson.isEmpty()) {
            json.put("random_brick_colours", rbcJson);
         }
      }

      if (!meta.startingGoods().isEmpty()) {
         List<Map<String, Object>> startingGoods = new ArrayList<>();

         for (LegacyDataParser.StartingGoodMeta sg : meta.startingGoods()) {
            Map<String, Object> sgJson = new LinkedHashMap<>();
            Optional<String> modernItem = items.resolveForBuildingCost(culture, sg.item(), meta.baseName());
            sgJson.put("item", modernItem.orElse(sg.item()));
            sgJson.put("probability", sg.probability());
            sgJson.put("fixed", sg.fixedNumber());
            sgJson.put("random", sg.randomNumber());
            startingGoods.add(sgJson);
         }

         json.put("starting_goods", startingGoods);
      }

      for (Entry<String, LegacyDataParser.BuildingMeta> varEntry : building.variantMetas().entrySet()) {
         LegacyDataParser.BuildingMeta vm = varEntry.getValue();
         if (vm.areaToClear() != meta.areaToClear()
            || vm.areaToClearLengthBefore() != meta.areaToClearLengthBefore()
            || vm.areaToClearLengthAfter() != meta.areaToClearLengthAfter()
            || vm.areaToClearWidthBefore() != meta.areaToClearWidthBefore()
            || vm.areaToClearWidthAfter() != meta.areaToClearWidthAfter()) {
            System.out.println("  [WARN] areaToClear mismatch in variant " + varEntry.getKey() + " of " + meta.baseName());
         }
      }

      List<Map<String, Object>> variants = new ArrayList<>();

      for (Entry<String, List<Path>> varEntry : building.variantPngs().entrySet()) {
         String variant = varEntry.getKey().toLowerCase();
         String variantKey = varEntry.getKey();
         List<Path> pngs = varEntry.getValue();
         LegacyDataParser.BuildingMeta varMeta = building.metaForVariant(variantKey);
         Map<String, Object> variantJson = new LinkedHashMap<>();
         variantJson.put("variant", variant);
         int[] levelGroundLevels = new int[pngs.size()];
         List<List<String>> levelCumulativeTags = new ArrayList<>(pngs.size());

         for (int i = 0; i < pngs.size(); i++) {
            int gl = 0;
            if (i < varMeta.levels().size()) {
               gl = varMeta.levels().get(i).startLevel();
            } else if (!varMeta.levels().isEmpty()) {
               gl = varMeta.levels().get(varMeta.levels().size() - 1).startLevel();
            }

            levelGroundLevels[i] = gl;
            List<String> cumTags = new ArrayList<>();
            int maxJ = Math.min(i, varMeta.levels().size() - 1);

            for (int j = 0; j <= maxJ; j++) {
               for (String t : varMeta.levels().get(j).tags()) {
                  String normalized = normalizeLegacyTag(t);
                  if (!cumTags.contains(normalized)) {
                     cumTags.add(normalized);
                  }
               }
            }

            levelCumulativeTags.add(cumTags);
         }

         boolean groundLevelConstant = true;

         for (int i = 1; i < levelGroundLevels.length; i++) {
            if (levelGroundLevels[i] != levelGroundLevels[0]) {
               groundLevelConstant = false;
               break;
            }
         }

         boolean tagsConstant = true;

         for (int i = 1; i < levelCumulativeTags.size(); i++) {
            if (!levelCumulativeTags.get(i).equals(levelCumulativeTags.get(0))) {
               tagsConstant = false;
               break;
            }
         }

         if (groundLevelConstant && levelGroundLevels.length > 0 && levelGroundLevels[0] != 0) {
            variantJson.put("ground_level", levelGroundLevels[0]);
         }

         if (tagsConstant && !levelCumulativeTags.isEmpty() && !levelCumulativeTags.get(0).isEmpty()) {
            variantJson.put("tags", levelCumulativeTags.get(0));
         }

         List<Map<String, Object>> levels = new ArrayList<>();

         for (int i = 0; i < pngs.size(); i++) {
            Map<String, Object> level = new LinkedHashMap<>();
            level.put("level", i);
            String templateName = sanitize(meta.baseName()) + "_" + variant + "_" + i;
            Path png = pngs.get(i);
            int nbFloors = estimateFloors(png, varMeta.width());
            Map<String, Object> footprint = new LinkedHashMap<>();
            footprint.put("width", varMeta.length());
            footprint.put("height", nbFloors);
            footprint.put("depth", varMeta.width());
            level.put("footprint", footprint);
            int groundLevel = levelGroundLevels[i];
            if (!groundLevelConstant) {
               level.put("ground_level", groundLevel);
            }

            if (i < varMeta.levels().size()) {
               LegacyDataParser.LevelMeta levelMeta = varMeta.levels().get(i);
               if (levelMeta.priority() != 100) {
                  level.put("priority", levelMeta.priority());
               }

               String levelName = levelMeta.nativeName();
               if (levelName != null && i > 0) {
                  level.put("native_name", levelName);
               }

               List<String> levelSubs = levelMeta.subBuildings();
               if (!levelSubs.isEmpty()) {
                  level.put("sub_buildings", levelSubs.stream().map(LegacyIdCanonicaliser::buildingPlanRefId).toList());
               }

               if (levelMeta.pathLevel() > 0) {
                  level.put("path_level", levelMeta.pathLevel());
               }

               if (levelMeta.rebuildPath()) {
                  level.put("rebuild_path", true);
               }

               if (levelMeta.pathWidth() != 2) {
                  level.put("path_width", levelMeta.pathWidth());
               }

               if (levelMeta.signs() != null) {
                  level.put("signs", levelMeta.signs());
               }

               if (levelMeta.priorityMoveIn() > 0) {
                  level.put("priority_move_in", levelMeta.priorityMoveIn());
               }

               if (levelMeta.extraSimultaneousWallConstructions() > 0) {
                  level.put("extra_simultaneous_wall_constructions", levelMeta.extraSimultaneousWallConstructions());
               }

               if (!levelMeta.requiredTags().isEmpty()) {
                  level.put("required_tags", levelMeta.requiredTags().stream().map(LegacyJsonBuilder::normalizeLegacyTag).toList());
               }

               if (!levelMeta.clearTags().isEmpty()) {
                  level.put("clear_tags", levelMeta.clearTags().stream().map(LegacyJsonBuilder::normalizeLegacyTag).toList());
               }
            }

            List<String> cumulativeTags = levelCumulativeTags.get(i);
            int maxLevelForTags = Math.min(i, varMeta.levels().size() - 1);
            if (!tagsConstant && !cumulativeTags.isEmpty()) {
               level.put("tags", cumulativeTags);
            }

            List<String> cumulativeParentTags = new ArrayList<>();

            for (int j = 0; j <= maxLevelForTags; j++) {
               for (String t : varMeta.levels().get(j).parentTags()) {
                  String normalized = normalizeLegacyTag(t);
                  if (!cumulativeParentTags.contains(normalized)) {
                     cumulativeParentTags.add(normalized);
                  }
               }
            }

            if (!cumulativeParentTags.isEmpty()) {
               level.put("parent_tags", cumulativeParentTags);
            }

            List<String> cumulativeVillageTags = new ArrayList<>();

            for (int j = 0; j <= maxLevelForTags; j++) {
               for (String t : varMeta.levels().get(j).villageTags()) {
                  String normalized = normalizeLegacyTag(t);
                  if (!cumulativeVillageTags.contains(normalized)) {
                     cumulativeVillageTags.add(normalized);
                  }
               }
            }

            if (!cumulativeVillageTags.isEmpty()) {
               level.put("village_tags", cumulativeVillageTags);
            }

            List<String> cumulativeRequiredParentTags = new ArrayList<>();

            for (int j = 0; j <= maxLevelForTags; j++) {
               for (String t : varMeta.levels().get(j).requiredParentTags()) {
                  String normalized = normalizeLegacyTag(t);
                  if (!cumulativeRequiredParentTags.contains(normalized)) {
                     cumulativeRequiredParentTags.add(normalized);
                  }
               }
            }

            if (!cumulativeRequiredParentTags.isEmpty()) {
               level.put("required_parent_tags", cumulativeRequiredParentTags);
            }

            Map<String, String> cumulativeAbsProd = new LinkedHashMap<>();
            int maxLevelForAbsProd = Math.min(i, varMeta.levels().size() - 1);

            for (int j = 0; j <= maxLevelForAbsProd; j++) {
               for (String entry : varMeta.levels().get(j).abstractedProduction()) {
                  String[] parts = entry.split(",");
                  if (parts.length == 2) {
                     cumulativeAbsProd.put(parts[0].trim(), entry);
                  }
               }
            }

            if (!cumulativeAbsProd.isEmpty()) {
               level.put("abstracted_production", new ArrayList<>(cumulativeAbsProd.values()));
            }

            PngToNbtConverter.ConversionResult convResult = conversionResults == null ? null : conversionResults.get(templateName);
            Map<String, Object> infoFields = extractInfoFields(convResult);
            level.putAll(infoFields);
            levels.add(level);
         }

         variantJson.put("levels", levels);
         variants.add(variantJson);
      }

      json.put("variants", variants);
      return json;
   }

   static String remapLegacyColorName(String legacyName) {
      return switch (legacyName.toLowerCase()) {
         case "silver" -> "light_gray";
         default -> legacyName.toLowerCase();
      };
   }

   static int estimateFloors(Path pngPath, int buildingWidth) {
      try {
         BufferedImage img = ImageIO.read(pngPath.toFile());
         if (img == null) {
            return 1;
         }

         int pngWidth = img.getWidth();
         return (pngWidth + 1) / (buildingWidth + 1);
      } catch (IOException e) {
         return 1;
      }
   }

   static Map<String, Object> extractInfoFields(PngToNbtConverter.ConversionResult result) {
      Map<String, Object> info = new LinkedHashMap<>();
      if (result != null && result.templateNbt() != null) {
         List<SpecialPoint> points = MockBlockExtractor.extract(result.templateNbt());
         List<Map<String, String>> soils = new ArrayList<>();
         List<Map<String, String>> sources = new ArrayList<>();
         List<Map<String, String>> treeSpawns = new ArrayList<>();
         List<Map<String, String>> animalSpawns = new ArrayList<>();

         for (SpecialPoint sp : points) {
            switch (sp.type()) {
               case "soil":
                  if (sp.subtype() != null && !sp.subtype().isEmpty()) {
                     Map<String, String> entry = new LinkedHashMap<>();
                     entry.put("crop", sp.subtype());
                     if (!soils.contains(entry)) {
                        soils.add(entry);
                     }
                  }
                  break;
               case "source":
                  if (sp.subtype() != null && !sp.subtype().isEmpty()) {
                     Map<String, String> entry = new LinkedHashMap<>();
                     entry.put("type", sp.subtype());
                     if (!sources.contains(entry)) {
                        sources.add(entry);
                     }
                  }
                  break;
               case "treeSpawn":
                  if (sp.subtype() != null && !sp.subtype().isEmpty()) {
                     Map<String, String> entry = new LinkedHashMap<>();
                     entry.put("sapling", sp.subtype());
                     if (!treeSpawns.contains(entry)) {
                        treeSpawns.add(entry);
                     }
                  }
                  break;
               case "animalSpawn":
                  if (sp.subtype() != null && !sp.subtype().isEmpty()) {
                     Map<String, String> entry = new LinkedHashMap<>();
                     entry.put("entity", sp.subtype());
                     if (!animalSpawns.contains(entry)) {
                        animalSpawns.add(entry);
                     }
                  }
            }
         }

         if (!soils.isEmpty()) {
            info.put("infoSoils", soils);
         }

         if (!sources.isEmpty()) {
            info.put("infoSources", sources);
         }

         if (!treeSpawns.isEmpty()) {
            info.put("infoTreeSpawns", treeSpawns);
         }

         if (!animalSpawns.isEmpty()) {
            info.put("infoAnimalSpawns", animalSpawns);
         }

         return info;
      } else {
         return info;
      }
   }

   public static Map<String, Object> buildVillagerType(LegacyDataParser.VillagerMeta villager, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("culture", "millenaire:" + culture);
      json.put("native_name", villager.nativeName());
      putIfNot(json, "model", villager.model(), "male");
      List<String> textures = new ArrayList<>();

      for (String tex : villager.textures()) {
         String path = tex.endsWith(".png") ? tex : tex + ".png";
         textures.add("millenaire:" + path.toLowerCase(Locale.ROOT));
      }

      json.put("textures", textures);
      if (!villager.clothesGroups().isEmpty()) {
         Map<String, Object> clothesMap = new LinkedHashMap<>();

         for (LegacyDataParser.ClothesGroup cg : villager.clothesGroups()) {
            Map<String, Object> groupClothes = new LinkedHashMap<>();
            if (!cg.layer0().isEmpty()) {
               List<String> l0 = new ArrayList<>();

               for (String cloth : cg.layer0()) {
                  String path = cloth.endsWith(".png") ? cloth : cloth + ".png";
                  l0.add("millenaire:" + path.toLowerCase(Locale.ROOT));
               }

               groupClothes.put("layer0", l0);
            }

            if (!cg.layer1().isEmpty()) {
               List<String> l1 = new ArrayList<>();

               for (String cloth : cg.layer1()) {
                  String path = cloth.endsWith(".png") ? cloth : cloth + ".png";
                  l1.add("millenaire:" + path.toLowerCase(Locale.ROOT));
               }

               groupClothes.put("layer1", l1);
            }

            clothesMap.put(cg.group(), groupClothes);
         }

         json.put("clothes", clothesMap);
      } else if (!villager.clothesLayer0().isEmpty() || !villager.clothesLayer1().isEmpty()) {
         Map<String, Object> clothesMap = new LinkedHashMap<>();
         Map<String, Object> freeClothes = new LinkedHashMap<>();
         if (!villager.clothesLayer0().isEmpty()) {
            List<String> l0 = new ArrayList<>();

            for (String cloth : villager.clothesLayer0()) {
               String path = cloth.endsWith(".png") ? cloth : cloth + ".png";
               l0.add("millenaire:" + path.toLowerCase(Locale.ROOT));
            }

            freeClothes.put("layer0", l0);
         }

         if (!villager.clothesLayer1().isEmpty()) {
            List<String> l1 = new ArrayList<>();

            for (String cloth : villager.clothesLayer1()) {
               String path = cloth.endsWith(".png") ? cloth : cloth + ".png";
               l1.add("millenaire:" + path.toLowerCase(Locale.ROOT));
            }

            freeClothes.put("layer1", l1);
         }

         clothesMap.put("free", freeClothes);
         json.put("clothes", clothesMap);
      }

      if (villager.baseHeight() != 1.0) {
         json.put("base_scale", villager.baseHeight());
      }

      if (villager.tags().contains("child")) {
         json.put("is_child", true);
      }

      List<String> goals = mapLegacyGoals(villager.goals());
      if (villager.tags().contains("child") && !goals.contains("millenaire:child_become_adult")) {
         goals.add(0, "millenaire:child_become_adult");
      }

      applyVillagerGoalOverrides(villager.id(), goals);
      json.put("goals", goals);
      List<String> normalizedTags = villager.tags().stream().map(LegacyJsonBuilder::normalizeLegacyTag).distinct().toList();
      json.put("tags", normalizedTags);
      json.put("spawn_weight", villager.chanceWeight());
      if (!villager.startingInv().isEmpty()) {
         Map<String, Integer> initialInv = new LinkedHashMap<>();

         for (Entry<String, Integer> entry : villager.startingInv().entrySet()) {
            Optional<String> mcItem = items.resolve(culture, entry.getKey());
            if (mcItem.isPresent()) {
               initialInv.put(mcItem.get(), entry.getValue());
            }
         }

         if (!initialInv.isEmpty()) {
            json.put("initial_inventory", initialInv);
         }
      }

      if (!villager.bringBackHomeGoods().isEmpty()) {
         List<String> bringBackGoods = new ArrayList<>();

         for (String legacyItem : villager.bringBackHomeGoods()) {
            items.resolve(culture, legacyItem).ifPresent(bringBackGoods::add);
         }

         if (!bringBackGoods.isEmpty()) {
            json.put("bring_back_home_goods", bringBackGoods);
         }
      }

      if (!villager.collectGoods().isEmpty()) {
         List<String> collectGoods = new ArrayList<>();

         for (String legacyItem : villager.collectGoods()) {
            items.resolve(culture, legacyItem).ifPresent(collectGoods::add);
         }

         if (!collectGoods.isEmpty()) {
            json.put("collect_goods", collectGoods);
         }
      }

      if (!villager.requiredGoods().isEmpty()) {
         Map<String, Integer> requiredGoods = new LinkedHashMap<>();

         for (Entry<String, Integer> entry : villager.requiredGoods().entrySet()) {
            Optional<String> mcItem = items.resolve(culture, entry.getKey());
            if (mcItem.isPresent()) {
               requiredGoods.put(mcItem.get(), entry.getValue());
            }
         }

         if (!requiredGoods.isEmpty()) {
            json.put("required_goods", requiredGoods);
         }
      }

      json.put("gender", villager.gender());
      if (villager.firstNameList() != null) {
         json.put("first_name_list", villager.firstNameList());
      }

      if (villager.familyNameList() != null) {
         json.put("family_name_list", villager.familyNameList());
      }

      if (villager.maleChild() != null) {
         json.put("male_child", LegacyIdCanonicaliser.villagerTypeRefId(stripCulturePrefix(culture, villager.maleChild())));
      }

      if (villager.femaleChild() != null) {
         json.put("female_child", LegacyIdCanonicaliser.villagerTypeRefId(stripCulturePrefix(culture, villager.femaleChild())));
      }

      if (villager.icon() != null) {
         json.put("icon", items.resolveExact(culture, villager.icon()).orElse(villager.icon()));
      }

      if (villager.health() != 20) {
         json.put("max_health", villager.health());
      }

      if (villager.villagerConfig() != null) {
         json.put("villager_config", villager.villagerConfig());
      }

      if (!villager.toolNeededClasses().isEmpty()) {
         json.put("tool_needed_classes", villager.toolNeededClasses());
      }

      if (!villager.itemsNeeded().isEmpty()) {
         List<String> mapped = new ArrayList<>();

         for (String raw : villager.itemsNeeded()) {
            items.resolveForVillagerEquipment(culture, raw, villager.id()).ifPresent(mapped::add);
         }

         if (!mapped.isEmpty()) {
            json.put("items_needed", mapped);
         }
      }

      if (villager.baseAttackStrength() > 0) {
         json.put("base_attack_strength", villager.baseAttackStrength());
      }

      if (villager.defaultWeapon() != null) {
         String rawWeapon = villager.defaultWeapon();
         String mappedWeapon = LEGACY_ITEM_ID_MAP.get(rawWeapon);
         if (mappedWeapon == null) {
            mappedWeapon = LEGACY_ITEM_ID_MAP.get(rawWeapon.toLowerCase(Locale.ROOT));
         }

         if (mappedWeapon != null) {
            json.put("default_weapon", mappedWeapon);
         } else {
            System.err.println("[WARN] default_weapon not mapped: " + rawWeapon + " (villager " + villager.id() + ")");
         }
      }

      if (villager.experienceGiven() > 0) {
         json.put("experience_given", villager.experienceGiven());
      }

      if (villager.hiringCost() != null) {
         json.put("hiring_cost", villager.hiringCost());
      }

      if (villager.altNativeName() != null) {
         json.put("alt_native_name", villager.altNativeName());
      }

      if (villager.altKey() != null) {
         json.put("alt_key", villager.altKey());
      }

      if (villager.travelbookHeldItem() != null) {
         json.put("travelbook_held_item", items.resolveExact(culture, villager.travelbookHeldItem()).orElse(villager.travelbookHeldItem()));
      }

      if (villager.travelbookHeldItemOffHand() != null) {
         json.put(
            "travelbook_held_item_off_hand", items.resolveExact(culture, villager.travelbookHeldItemOffHand()).orElse(villager.travelbookHeldItemOffHand())
         );
      }

      if (villager.travelbookMainCultureVillager()) {
         json.put("travelbook_main_culture_villager", true);
      }

      if (!villager.merchantStock().isEmpty()) {
         Map<String, Integer> foreignMerchantStock = new LinkedHashMap<>();

         for (Entry<String, Integer> entry : villager.merchantStock().entrySet()) {
            items.resolveForVillagerEquipment(culture, entry.getKey(), villager.id()).ifPresent(mc -> foreignMerchantStock.put(mc, entry.getValue()));
         }

         if (!foreignMerchantStock.isEmpty()) {
            json.put("foreign_merchant_stock", foreignMerchantStock);
         }
      }

      return json;
   }

   static String normalizeLegacyTag(String tag) {
      return LEGACY_TAG_FIXES.getOrDefault(tag, tag);
   }

   private static String normalizeFarFromTag(String entry) {
      int comma = entry.indexOf(44);
      return comma < 0 ? normalizeLegacyTag(entry) : normalizeLegacyTag(entry.substring(0, comma)) + entry.substring(comma);
   }

   private static void applyVillagerGoalOverrides(String villagerId, List<String> goals) {
      List<String> extras = VILLAGER_EXTRA_GOALS.get(villagerId);
      if (extras != null) {
         for (String goal : extras) {
            if (!goals.contains(goal)) {
               goals.add(goal);
               System.out.println("    [OVERRIDE] +goal " + goal + " for " + villagerId + " (iso-legacy tendfurnace compensation)");
            }
         }
      }
   }

   static List<String> mapLegacyGoals(List<String> legacyGoals) {
      List<String> mapped = new ArrayList<>();
      mapped.add("millenaire:idle");
      boolean hasSocialise = legacyGoals.stream().anyMatch(g -> g.equalsIgnoreCase("gosocialise"));
      if (hasSocialise) {
         mapped.add("millenaire:socialise");
         mapped.add("millenaire:chat");
      }

      boolean hasChat = legacyGoals.stream().anyMatch(g -> g.equalsIgnoreCase("chat"));
      if (hasChat && !hasSocialise) {
         mapped.add("millenaire:chat");
      }

      for (String goal : legacyGoals) {
         String lower = goal.toLowerCase();
         switch (lower) {
            case "gorest":
               mapped.add("millenaire:rest");
            case "gosocialise":
            case "chat":
            case "huntmonster":
            case "tendfurnace":
            case "tendfurnacetownhall":
            case "tendfurnacebrickkiln":
            case "tendfurnacekitchen":
            case "gatherchicken":
               break;
            case "getresourcesforbuild":
            case "construction":
               mapped.add("millenaire:build");
               break;
            case "chopwood":
            case "choptrees":
               mapped.add("millenaire:chop_trees");
               mapped.add("millenaire:plant_saplings");
               break;
            case "gathergoods":
               mapped.add("millenaire:gather_goods");
               break;
            case "plantsaplings":
               mapped.add("millenaire:plant_saplings");
               break;
            case "plantsaplingappletreeorchard":
               mapped.add("millenaire:plant_sapling_appletree_orchard");
               break;
            case "harvestwheathome":
               mapped.add("millenaire:harvest_wheat_home");
               break;
            case "plantwheathome":
               mapped.add("millenaire:plant_wheat_home");
               break;
            case "harvestcarrothome":
               mapped.add("millenaire:harvest_carrot_home");
               break;
            case "plantcarrothome":
               mapped.add("millenaire:plant_carrot_home");
               break;
            case "bringbackresourceshome":
               mapped.add("millenaire:bring_back_home");
               break;
            case "deliverresourcesshop":
               mapped.add("millenaire:deliver_resources_shop");
               break;
            case "gethousethresources":
               mapped.add("millenaire:get_resources_for_shops");
               break;
            case "getgoodshousehold":
            case "delivergoodshousehold":
               mapped.add("millenaire:get_goods_for_household");
               break;
            case "makebread":
               mapped.add("millenaire:craft_bread");
               break;
            case "quarrymining":
            case "minestone":
               mapped.add("millenaire:mine_stone");
               break;
            case "minesand":
               mapped.add("millenaire:mine_sand");
               break;
            case "mineclay":
               mapped.add("millenaire:mine_clay");
               break;
            case "minegravel":
               mapped.add("millenaire:mine_gravel");
               break;
            case "minesnow":
               mapped.add("millenaire:mine_snow");
               break;
            case "minesandstone":
               mapped.add("millenaire:mine_sandstone");
               break;
            case "mineice":
               mapped.add("millenaire:mine_ice");
               break;
            case "breedanimals":
            case "breed":
               mapped.add("millenaire:breed_cattle");
               mapped.add("millenaire:breed_sheep");
               mapped.add("millenaire:breed_chicken");
               mapped.add("millenaire:breed_pigs");
               break;
            case "butcheranimals":
               mapped.add("millenaire:slaughter_cow");
               mapped.add("millenaire:slaughter_sheep");
               mapped.add("millenaire:slaughter_chicken");
               mapped.add("millenaire:slaughter_pig");
               break;
            case "slaughtercownorman":
               mapped.add("millenaire:slaughter_cow_norman");
               break;
            case "slaughterpignorman":
               mapped.add("millenaire:slaughter_pig_norman");
               break;
            case "slaughterchicken":
               mapped.add("millenaire:slaughter_chicken");
               break;
            case "slaughtersheep":
               mapped.add("millenaire:slaughter_sheep");
               break;
            case "shearanimals":
            case "shearsheep":
               mapped.add("millenaire:shear_sheep");
               break;
            case "fishinggoal":
               mapped.add("millenaire:fish");
               break;
            case "fishinuit":
               mapped.add("millenaire:fishinuit");
               break;
            case "buildpath":
               mapped.add("millenaire:build_path");
               break;
            case "clearoldpath":
               mapped.add("millenaire:clear_old_path");
               break;
            case "maketimberframeplainoak":
               mapped.add("millenaire:craft_timberframeplainoak");
               break;
            case "maketimberframeplainbirch":
               mapped.add("millenaire:craft_timberframeplainbirch");
               break;
            case "maketimberframeplainpine":
               mapped.add("millenaire:craft_timberframeplainpine");
               break;
            case "maketimberframeplainjungle":
               mapped.add("millenaire:craft_timberframeplainjungle");
               break;
            case "maketimberframeplainacacia":
               mapped.add("millenaire:craft_timberframeplainacacia");
               break;
            case "maketimberframeplaindarkoak":
               mapped.add("millenaire:craft_timberframeplaindarkoak");
               break;
            case "maketimberframecrossoak":
               mapped.add("millenaire:craft_timberframecrossoak");
               break;
            case "maketimberframecrossbirch":
               mapped.add("millenaire:craft_timberframecrossbirch");
               break;
            case "maketimberframecrosspine":
               mapped.add("millenaire:craft_timberframecrosspine");
               break;
            case "maketimberframecrossjungle":
               mapped.add("millenaire:craft_timberframecrossjungle");
               break;
            case "maketimberframecrossacacia":
               mapped.add("millenaire:craft_timberframecrossacacia");
               break;
            case "maketimberframecrossdarkoak":
               mapped.add("millenaire:craft_timberframecrossdarkoak");
               break;
            case "maketripes":
               mapped.add("millenaire:craft_tripes");
               break;
            case "makeboudin":
               mapped.add("millenaire:craft_boudin");
               break;
            case "makecider":
               mapped.add("millenaire:craft_cider");
               break;
            case "makeciderhome":
               mapped.add("millenaire:craft_ciderhome");
               break;
            case "makecalvahome":
               mapped.add("millenaire:craft_calvahome");
               break;
            case "makecake":
               mapped.add("millenaire:craft_cake");
               break;
            case "makeglassbottles":
               mapped.add("millenaire:craft_glassbottles");
               break;
            case "makenormanaxe":
               mapped.add("millenaire:craft_normanaxe");
               break;
            case "makenormanpickaxe":
               mapped.add("millenaire:craft_normanpickaxe");
               break;
            case "makenormanshovel":
               mapped.add("millenaire:craft_normanshovel");
               break;
            case "makenormanhoe":
               mapped.add("millenaire:craft_normanhoe");
               break;
            case "makecauldron":
               mapped.add("millenaire:craft_cauldron");
               break;
            case "makenormansword":
               mapped.add("millenaire:craft_normansword");
               break;
            case "makenormanhelmet":
               mapped.add("millenaire:craft_normanhelmet");
               break;
            case "makenormanplate":
               mapped.add("millenaire:craft_normanplate");
               break;
            case "makenormanlegs":
               mapped.add("millenaire:craft_normanlegs");
               break;
            case "makenormanboots":
               mapped.add("millenaire:craft_normanboots");
               break;
            case "makebow":
               mapped.add("millenaire:craft_bow");
               break;
            case "makenormanstainedglass_white":
               mapped.add("millenaire:craft_normanstainedglass_white");
               break;
            case "makenormanstainedglass_yellow":
               mapped.add("millenaire:craft_normanstainedglass_yellow");
               break;
            case "makenormanstainedglass_yellow_red":
               mapped.add("millenaire:craft_normanstainedglass_yellow_red");
               break;
            case "makenormanstainedglass_red_blue":
               mapped.add("millenaire:craft_normanstainedglass_red_blue");
               break;
            case "makenormanstainedglass_green_blue":
               mapped.add("millenaire:craft_normanstainedglass_green_blue");
               break;
            case "makerosette":
               mapped.add("millenaire:craft_rosette");
               break;
            case "makecarpet_white":
               mapped.add("millenaire:craft_carpet_white");
               break;
            case "makecarpet_yellow":
               mapped.add("millenaire:craft_carpet_yellow");
               break;
            case "makecarpet_red":
               mapped.add("millenaire:craft_carpet_red");
               break;
            case "makecarpet_blue":
               mapped.add("millenaire:craft_carpet_blue");
               break;
            case "dyewool_yellow":
               mapped.add("millenaire:craft_dyewool_yellow");
               break;
            case "dyewool_red":
               mapped.add("millenaire:craft_dyewool_red");
               break;
            case "dyewool_blue":
               mapped.add("millenaire:craft_dyewool_blue");
               break;
            case "makebannerwool":
               mapped.add("millenaire:craft_bannerwool");
               break;
            case "makebannerleather":
               mapped.add("millenaire:craft_bannerleather");
               break;
            case "makebannerfeather":
               mapped.add("millenaire:craft_bannerfeather");
               break;
            case "makebannercotton":
               mapped.add("millenaire:craft_bannercotton");
               break;
            case "makebannerrice":
               mapped.add("millenaire:craft_bannerrice");
               break;
            case "maketapestry":
               mapped.add("millenaire:craft_tapestry");
               break;
            case "makebooks":
               mapped.add("millenaire:craft_books");
               break;
            case "makestrawbed":
               mapped.add("millenaire:craft_strawbed");
               break;
            case "makedirtwall":
               mapped.add("millenaire:craft_dirtwall");
               break;
            case "makepathdirt":
               mapped.add("millenaire:craft_pathdirt");
               break;
            case "makepathgravel":
               mapped.add("millenaire:craft_pathgravel");
               break;
            case "makepathslabs":
               mapped.add("millenaire:craft_pathslabs");
               break;
            case "makealchemy":
               mapped.add("millenaire:craft_glassbottles");
               break;
            case "makebonemeal":
               mapped.add("millenaire:craft_bonemeal");
               break;
            case "makenormantools":
               mapped.add("millenaire:craft_normanaxe");
               mapped.add("millenaire:craft_normanpickaxe");
               mapped.add("millenaire:craft_normanshovel");
               mapped.add("millenaire:craft_normanhoe");
               mapped.add("millenaire:craft_cauldron");
               break;
            case "makenormanarmor":
               mapped.add("millenaire:craft_normansword");
               mapped.add("millenaire:craft_normanhelmet");
               mapped.add("millenaire:craft_normanplate");
               mapped.add("millenaire:craft_normanlegs");
               mapped.add("millenaire:craft_normanboots");
               mapped.add("millenaire:craft_bow");
               break;
            case "makeglassitems":
               mapped.add("millenaire:craft_normanstainedglass_white");
               mapped.add("millenaire:craft_normanstainedglass_yellow");
               mapped.add("millenaire:craft_normanstainedglass_yellow_red");
               mapped.add("millenaire:craft_normanstainedglass_red_blue");
               mapped.add("millenaire:craft_normanstainedglass_green_blue");
               mapped.add("millenaire:craft_rosette");
               break;
            case "makeweaveitems":
               mapped.add("millenaire:craft_carpet_white");
               mapped.add("millenaire:craft_carpet_yellow");
               mapped.add("millenaire:craft_carpet_red");
               mapped.add("millenaire:craft_carpet_blue");
               mapped.add("millenaire:craft_dyewool_yellow");
               mapped.add("millenaire:craft_dyewool_red");
               mapped.add("millenaire:craft_dyewool_blue");
               mapped.add("millenaire:craft_bannerwool");
               mapped.add("millenaire:craft_bannerleather");
               mapped.add("millenaire:craft_bannerfeather");
               mapped.add("millenaire:craft_bannercotton");
               mapped.add("millenaire:craft_bannerrice");
               mapped.add("millenaire:craft_tapestry");
               break;
            case "cookstone":
               mapped.add("millenaire:cook_stone");
               break;
            case "cooksand":
               mapped.add("millenaire:cook_sand");
               break;
            case "cooksteak":
               mapped.add("millenaire:cook_steak");
               break;
            case "cookpork":
               mapped.add("millenaire:cook_pork");
               break;
            case "cookchicken":
               mapped.add("millenaire:cook_chicken");
               break;
            case "gopray":
               mapped.add("millenaire:pray");
               break;
            case "godrink":
               mapped.add("millenaire:drink");
               break;
            case "godrinkcider":
               mapped.add("millenaire:drink_cider");
               break;
            case "goplay":
               mapped.add("millenaire:play");
               break;
            case "goplaywithfriends":
               mapped.add("millenaire:play_with_friends");
               break;
            case "childobserveagriculture":
               mapped.add("millenaire:observe_agriculture");
               break;
            case "childobserveconstruction":
               mapped.add("millenaire:observe_construction");
               break;
            case "childobservesmithing":
               mapped.add("millenaire:observe_smithing");
               break;
            case "childobserveproducealcohol":
               mapped.add("millenaire:observe_produce_alcohol");
               break;
            case "childobserveproducefood":
               mapped.add("millenaire:observe_produce_food");
               break;
            case "childeatapple":
               mapped.add("millenaire:child_eat_apple");
               break;
            case "childeatcacaobeans":
               mapped.add("millenaire:child_eat_cacao_beans");
               break;
            case "inspectconstruction":
               mapped.add("millenaire:inspect_construction");
               break;
            case "goholdaservice":
               mapped.add("millenaire:hold_service");
               break;
            case "gogardening":
               mapped.add("millenaire:gardening_norman");
               break;
            case "gopreachonpulpit":
               mapped.add("millenaire:preach_on_pulpit");
               break;
            case "goplayorgan":
               mapped.add("millenaire:play_organ");
               break;
            case "goonpilgrimage":
               mapped.add("millenaire:pilgrimage");
               break;
            case "gotendsacrifices":
               mapped.add("millenaire:tend_sacrifices");
               break;
            case "plantnormanflowers":
               mapped.add("millenaire:plant_norman_flowers");
               break;
            case "harvestflowerhome_blue_orchid":
               mapped.add("millenaire:harvest_flower_home_blue_orchid");
               break;
            case "harvestflowerhome_poppy":
               mapped.add("millenaire:harvest_flower_home_poppy");
               break;
            case "harvestflowerhome_dandelion":
               mapped.add("millenaire:harvest_flower_home_dandelion");
               break;
            case "removeflowerhome_blue_orchid":
               mapped.add("millenaire:remove_flower_home_blue_orchid");
               break;
            case "removeflowerhome_poppy":
               mapped.add("millenaire:remove_flower_home_poppy");
               break;
            case "removeflowerhome_dandelion":
               mapped.add("millenaire:remove_flower_home_dandelion");
               break;
            case "plantrosebush":
               mapped.add("millenaire:plant_rosebush");
               break;
            case "harvestflowerhome_rosebush":
               mapped.add("millenaire:harvest_flower_home_rosebush");
               break;
            case "gatherciderappleshome":
               mapped.add("millenaire:gather_cider_apples_home");
               break;
            case "gatherciderappleslumbermen":
               mapped.add("millenaire:gather_cider_apples_lumbermen");
               break;
            case "plantsaplingappletreehome":
               mapped.add("millenaire:plant_sapling_appletree_home");
               break;
            case "gatherciderappleschildren":
               mapped.add("millenaire:gather_cider_apples_children");
               break;
            case "harvestmaize":
               mapped.add("millenaire:harvest_maize");
               break;
            case "plantmaize":
               mapped.add("millenaire:plant_maize");
               break;
            case "makeobsidianflake":
               mapped.add("millenaire:craft_obsidianflake");
               break;
            case "makemayanmace":
               mapped.add("millenaire:craft_mayanmace");
               break;
            case "makemayanaxe":
               mapped.add("millenaire:craft_mayanaxe");
               break;
            case "makemayanpickaxe":
               mapped.add("millenaire:craft_mayanpickaxe");
               break;
            case "makemayanshovel":
               mapped.add("millenaire:craft_mayanshovel");
               break;
            case "makemayanhoe":
               mapped.add("millenaire:craft_mayanhoe");
               break;
            case "makemayanstatue":
               mapped.add("millenaire:craft_mayanstatue");
               break;
            case "makemayangoldblock":
               mapped.add("millenaire:craft_mayangoldblock");
               break;
            case "makemasa":
               mapped.add("millenaire:craft_masa");
               break;
            case "makewah":
               mapped.add("millenaire:craft_wah");
               break;
            case "makecacauhaa":
               mapped.add("millenaire:craft_cacauhaa");
               break;
            case "makestoneaxe":
               mapped.add("millenaire:craft_stoneaxe");
               break;
            case "makestonepickaxe":
               mapped.add("millenaire:craft_stonepickaxe");
               break;
            case "makestoneshovel":
               mapped.add("millenaire:craft_stoneshovel");
               break;
            case "makestonehoe":
               mapped.add("millenaire:craft_stonehoe");
               break;
            case "makestonesword":
               mapped.add("millenaire:craft_stonesword");
               break;
            case "makewoodenpickaxe":
               mapped.add("millenaire:craft_woodenpickaxe");
               break;
            case "harvestcocoa":
               mapped.add("millenaire:harvest_cocoa");
               break;
            case "plantcocoa":
               mapped.add("millenaire:plant_cocoa");
               break;
            case "godrinkcacauhaa":
               mapped.add("millenaire:drink_cacauhaa");
               break;
            case "plantrice":
               mapped.add("millenaire:plant_rice");
               break;
            case "harvestrice":
               mapped.add("millenaire:harvest_rice");
               break;
            case "plantturmeric":
               mapped.add("millenaire:plant_turmeric");
               break;
            case "harvestturmeric":
               mapped.add("millenaire:harvest_turmeric");
               break;
            case "plantsugarcane":
               mapped.add("millenaire:plant_sugar_cane");
               break;
            case "harvestsugarcane":
               mapped.add("millenaire:harvest_sugar_cane");
               break;
            case "plantcotton":
               mapped.add("millenaire:plant_cotton");
               break;
            case "harvestcotton":
               mapped.add("millenaire:harvest_cotton");
               break;
            case "plantindianflowers":
               mapped.add("millenaire:plant_indian_flowers");
               break;
            case "harvestflower_blue_orchid":
               mapped.add("millenaire:harvest_flower_blue_orchid");
               break;
            case "harvestflower_poppy":
               mapped.add("millenaire:harvest_flower_poppy");
               break;
            case "harvestflower_dandelion":
               mapped.add("millenaire:harvest_flower_dandelion");
               break;
            case "drybrick":
               mapped.add("millenaire:dry_brick");
               break;
            case "gatherbrick":
               mapped.add("millenaire:gather_brick");
               break;
            case "cookindianbrick":
               mapped.add("millenaire:cook_indian_brick");
               break;
            case "gatherfrombrickkiln":
               mapped.add("millenaire:gather_from_brick_kiln");
               break;
            case "makerasgulla":
               mapped.add("millenaire:craft_rasgulla");
               break;
            case "makecurry":
               mapped.add("millenaire:craft_curry");
               break;
            case "makemurgh":
               mapped.add("millenaire:craft_murgh");
               break;
            case "makecharpoybed":
               mapped.add("millenaire:craft_charpoybed");
               break;
            case "makewoodenbarsindian":
               mapped.add("millenaire:craft_woodenbarsindian");
               break;
            case "makewoodenbarsrosette":
               mapped.add("millenaire:craft_woodenbarsrosette");
               break;
            case "makewoodenbars":
               mapped.add("millenaire:craft_woodenbars");
               break;
            case "makeindianstatue":
               mapped.add("millenaire:craft_indianstatue");
               break;
            case "makesandstonecarved":
               mapped.add("millenaire:craft_sandstonecarved");
               break;
            case "makeredsandstonecarved":
               mapped.add("millenaire:craft_redsandstonecarved");
               break;
            case "makeochresandstonecarved":
               mapped.add("millenaire:craft_ochresandstonecarved");
               break;
            case "makedecoratedbricks":
               mapped.add("millenaire:craft_decoratedbricks");
               break;
            case "makepaintedbucketwhite":
               mapped.add("millenaire:craft_paintedbucketwhite");
               break;
            case "makewoolfromcotton":
               mapped.add("millenaire:craft_woolfromcotton");
               break;
            case "makethatchfromrice":
               mapped.add("millenaire:craft_thatchfromrice");
               break;
            case "makestrawbedindian":
               mapped.add("millenaire:craft_strawbedindian");
               break;
            case "makesteelpickaxe":
               mapped.add("millenaire:craft_steelpickaxe");
               break;
            case "makesteelaxe":
               mapped.add("millenaire:craft_steelaxe");
               break;
            case "makesteelshovel":
               mapped.add("millenaire:craft_steelshovel");
               break;
            case "makesteelhoe":
               mapped.add("millenaire:craft_steelhoe");
               break;
            case "makesteelsword":
               mapped.add("millenaire:craft_steelsword");
               break;
            case "makesteelchest":
               mapped.add("millenaire:craft_steelchest");
               break;
            case "makesteellegs":
               mapped.add("millenaire:craft_steellegs");
               break;
            case "makesteelhelmet":
               mapped.add("millenaire:craft_steelhelmet");
               break;
            case "makesteelboots":
               mapped.add("millenaire:craft_steelboots");
               break;
            case "mineredsandstone":
               mapped.add("millenaire:mine_red_sandstone");
               break;
            case "gomeditate":
               mapped.add("millenaire:meditate");
               break;
            case "performpujas":
               mapped.add("millenaire:perform_pujas");
               break;
            case "bepujaperformer":
               mapped.add("millenaire:be_puja_performer");
               break;
            case "childeatsugarcane":
               mapped.add("millenaire:child_eat_sugar_cane");
               break;
            case "visitinn":
               mapped.add("millenaire:visit_inn");
               break;
            case "visitbuilding":
               mapped.add("millenaire:visit_building");
               break;
            case "keepstall":
               mapped.add("millenaire:keep_stall");
               break;
            case "makepathsandstone":
               mapped.add("millenaire:craft_pathsandstone");
               break;
            case "fish":
               mapped.add("millenaire:fish");
               break;
            case "cookfish":
               mapped.add("millenaire:cook_fish");
               break;
            case "cooklamb":
               mapped.add("millenaire:cook_lamb");
               break;
            case "cookclay":
               mapped.add("millenaire:cook_clay");
               break;
            case "cooksandbyz":
               mapped.add("millenaire:cook_sand_byz");
               break;
            case "cookstonebyz":
               mapped.add("millenaire:cook_stone_byz");
               break;
            case "godrinkwine":
               mapped.add("millenaire:drink_wine");
               break;
            case "listentospeech1":
               mapped.add("millenaire:listen_to_speech");
               break;
            case "listentospeech2":
               mapped.add("millenaire:listen_to_speech_2");
               break;
            case "givespeech":
               mapped.add("millenaire:give_speech");
               break;
            case "golookout":
               mapped.add("millenaire:lookout");
               break;
            case "golookout2":
               mapped.add("millenaire:lookout_2");
               break;
            case "gostudy":
               mapped.add("millenaire:study");
               break;
            case "gotreerelax":
               mapped.add("millenaire:tree_relax");
               break;
            case "childeatgrapes":
               mapped.add("millenaire:child_eat_grapes");
               break;
            case "childobservevines":
               mapped.add("millenaire:observe_vines");
               break;
            case "attendclass":
               mapped.add("millenaire:attend_class");
               break;
            case "becomeadult":
               mapped.add("millenaire:child_become_adult");
               break;
            case "teach":
               mapped.add("millenaire:teach");
               break;
            case "training":
               mapped.add("millenaire:training");
               break;
            case "patrol":
               mapped.add("millenaire:patrol");
               break;
            case "gatherolives":
               mapped.add("millenaire:gather_olives");
               break;
            case "gathersilk":
               mapped.add("millenaire:gather_silk");
               break;
            case "gathersnails":
               mapped.add("millenaire:gather_snails");
               break;
            case "gatherclay":
               mapped.add("millenaire:mine_clay_byz");
               break;
            case "gathersand":
               mapped.add("millenaire:mine_sand_byz");
               break;
            case "gathergravel":
               mapped.add("millenaire:mine_gravel_byz");
               break;
            case "harvestwheat":
               mapped.add("millenaire:harvest_wheat");
               break;
            case "plantwheat":
               mapped.add("millenaire:plant_wheat");
               break;
            case "harvestcarrot":
               mapped.add("millenaire:harvest_carrot");
               break;
            case "plantcarrot":
               mapped.add("millenaire:plant_carrot");
               break;
            case "harvestvines":
               mapped.add("millenaire:harvest_vines");
               break;
            case "plantvines":
               mapped.add("millenaire:plant_vines");
               break;
            case "plantsaplingolivetree":
               mapped.add("millenaire:plant_olive_saplings");
               break;
            case "plantfloweryellow":
               mapped.add("millenaire:plant_flowers_yellow");
               break;
            case "makesouvlaki":
               mapped.add("millenaire:craft_souvlaki");
               break;
            case "makewine":
               mapped.add("millenaire:craft_wine");
               break;
            case "makeoliveoil":
               mapped.add("millenaire:craft_oliveoil");
               break;
            case "makefeta":
               mapped.add("millenaire:craft_feta");
               break;
            case "makefresco":
               mapped.add("millenaire:craft_fresco");
               break;
            case "makeicon_small":
               mapped.add("millenaire:craft_icon_small");
               break;
            case "makeicon_medium":
               mapped.add("millenaire:craft_icon_medium");
               break;
            case "makeicon_large":
               mapped.add("millenaire:craft_icon_large");
               break;
            case "makeiconfancy":
               mapped.add("millenaire:craft_icon_fancy");
               break;
            case "makemosaicprelim":
               mapped.add("millenaire:craft_mosaic_prelim");
               break;
            case "maketiles":
               mapped.add("millenaire:craft_tiles");
               break;
            case "converttiles":
               mapped.add("millenaire:craft_convert_tiles");
               break;
            case "makeclothessilk":
               mapped.add("millenaire:craft_clothes_silk");
               break;
            case "makeclotheswool":
               mapped.add("millenaire:craft_clothes_wool");
               break;
            case "makebyzantinemace":
               mapped.add("millenaire:craft_byzantinemace");
               break;
            case "makebyzantineaxe":
               mapped.add("millenaire:craft_byzantineaxe");
               break;
            case "makebyzantinepickaxe":
               mapped.add("millenaire:craft_byzantinepickaxe");
               break;
            case "makebyzantineshovel":
               mapped.add("millenaire:craft_byzantineshovel");
               break;
            case "makebyzantinehoe":
               mapped.add("millenaire:craft_byzantinehoe");
               break;
            case "makebyzantinehelmet":
               mapped.add("millenaire:craft_byzantinehelmet");
               break;
            case "makebyzantinechest":
               mapped.add("millenaire:craft_byzantinechest");
               break;
            case "makebyzantinelegs":
               mapped.add("millenaire:craft_byzantinelegs");
               break;
            case "makebyzantineboots":
               mapped.add("millenaire:craft_byzantineboots");
               break;
            case "makebyzantinestainedglass_white":
               mapped.add("millenaire:craft_byzantinestainedglass_white");
               break;
            case "makecarpetpurple":
               mapped.add("millenaire:craft_carpet_purple");
               break;
            case "makecarpetyellow":
               mapped.add("millenaire:craft_carpet_yellow");
               break;
            case "makedirtwallbyz":
               mapped.add("millenaire:craft_dirtwall");
               break;
            case "makestrawbedbyz":
               mapped.add("millenaire:craft_strawbed");
               break;
            case "makepathgravelbyz":
               mapped.add("millenaire:craft_pathgravel");
               break;
            case "makepathslabsbyz":
               mapped.add("millenaire:craft_pathslabs");
               break;
            case "makepathsandstonebyz":
               mapped.add("millenaire:craft_pathsandstone");
               break;
            case "makebookshelvesbyz":
               mapped.add("millenaire:craft_bookshelves_byz");
               break;
            case "makeironbyzworkshop":
               mapped.add("millenaire:craft_iron_workshop");
               break;
            case "makesandstoneworkshop":
               mapped.add("millenaire:craft_sandstone_workshop");
               break;
            case "makepaperwoodpulp":
               mapped.add("millenaire:craft_paper_woodpulp");
               break;
            case "makequartz":
               mapped.add("millenaire:craft_quartz");
               break;
            case "polishdiorite":
               mapped.add("millenaire:craft_polished_diorite");
               break;
            case "makebookfancy":
               mapped.add("millenaire:craft_book_fancy");
               break;
            case "makebook":
               mapped.add("millenaire:craft_books");
               break;
            case "makearrow":
               mapped.add("millenaire:craft_arrow");
               break;
            case "minediorite":
               mapped.add("millenaire:mine_diorite");
               break;
            case "mineiron2":
               mapped.add("millenaire:mine_iron");
               break;
            case "minestone2":
               mapped.add("millenaire:mine_stone_byz");
               break;
            case "slaughtersheepbyz":
               mapped.add("millenaire:slaughter_sheep_byz");
               break;
            case "slaughtersheepbyz2":
               mapped.add("millenaire:slaughter_sheep_byz2");
               break;
            case "stealtulipred":
               mapped.add("millenaire:steal_tulip_red");
               break;
            case "stealtulipwhite":
               mapped.add("millenaire:steal_tulip_white");
               break;
            case "stealtulippink":
               mapped.add("millenaire:steal_tulip_pink");
               break;
            case "stealtuliporange":
               mapped.add("millenaire:steal_tulip_orange");
               break;
            case "relight":
               mapped.add("millenaire:relight");
               break;
            case "fetchbreadbyz":
               mapped.add("millenaire:fetch_bread_byz");
               break;
            case "fetchironbyz":
               mapped.add("millenaire:fetch_iron_byz");
               break;
            case "fetchsandstonebyz":
               mapped.add("millenaire:fetch_sandstone_byz");
               break;
            case "gardeningbyz":
               mapped.add("millenaire:gardening_byz");
               break;
            case "makesake":
               mapped.add("millenaire:craft_sake");
               break;
            case "makeudon":
               mapped.add("millenaire:craft_udon");
               break;
            case "makeudonkitchen":
               mapped.add("millenaire:craft_udon_kitchen");
               break;
            case "makepaperwall":
               mapped.add("millenaire:craft_paperwall");
               break;
            case "makepaperrice":
               mapped.add("millenaire:craft_paperrice");
               break;
            case "makethatch":
               mapped.add("millenaire:craft_thatchfromwheat");
               break;
            case "maketachi":
               mapped.add("millenaire:craft_tachi");
               break;
            case "makeyumi":
               mapped.add("millenaire:craft_yumi");
               break;
            case "makejghelmet":
               mapped.add("millenaire:craft_jg_helmet");
               break;
            case "makejgchest":
               mapped.add("millenaire:craft_jg_plate");
               break;
            case "makejglegs":
               mapped.add("millenaire:craft_jg_legs");
               break;
            case "makejgboots":
               mapped.add("millenaire:craft_jg_boots");
               break;
            case "makejwhelmetb":
               mapped.add("millenaire:craft_jb_helmet");
               break;
            case "makejwchestb":
               mapped.add("millenaire:craft_jb_plate");
               break;
            case "makejwlegsb":
               mapped.add("millenaire:craft_jb_legs");
               break;
            case "makejwbootsb":
               mapped.add("millenaire:craft_jb_boots");
               break;
            case "makejwhelmetr":
               mapped.add("millenaire:craft_jr_helmet");
               break;
            case "makejwchestr":
               mapped.add("millenaire:craft_jr_plate");
               break;
            case "makejwlegsr":
               mapped.add("millenaire:craft_jr_legs");
               break;
            case "makejwbootsr":
               mapped.add("millenaire:craft_jr_boots");
               break;
            case "godrinksake":
               mapped.add("millenaire:drink_sake");
               break;
            case "slaughtersquid":
               mapped.add("millenaire:slaughter_squid");
               break;
            case "cookchickenkitchen":
               mapped.add("millenaire:cook_chicken_kitchen");
               break;
            case "makepathgravelslabs":
               mapped.add("millenaire:craft_pathgravelslabs");
               break;
            case "gopraymalemuslim":
               mapped.add("millenaire:pray_male_muslim");
               break;
            case "goprayfemalemuslim":
               mapped.add("millenaire:pray_female_muslim");
               break;
            case "takebath":
               mapped.add("millenaire:take_bath");
               break;
            case "takebathfemale":
               mapped.add("millenaire:take_bath_female");
               break;
            case "godrinkayran":
               mapped.add("millenaire:drink_ayran");
               break;
            case "gogardeningseljuk":
               mapped.add("millenaire:gardening_seljuk");
               break;
            case "makeayranKitchen":
            case "makeayrankitchen":
               mapped.add("millenaire:craft_ayran_kitchen");
               break;
            case "makehelva":
               mapped.add("millenaire:craft_helva");
               break;
            case "makelokum":
               mapped.add("millenaire:craft_lokum");
               break;
            case "makepidebeef":
               mapped.add("millenaire:craft_pide_beef");
               break;
            case "makepidemutton":
               mapped.add("millenaire:craft_pide_mutton");
               break;
            case "makeyogurt":
               mapped.add("millenaire:craft_yogurt");
               break;
            case "makebreadmill":
               mapped.add("millenaire:craft_bread_mill");
               break;
            case "makecottoncloth":
               mapped.add("millenaire:craft_cotton_cloth");
               break;
            case "makewoolcloth":
               mapped.add("millenaire:craft_wool_cloth");
               break;
            case "makewoolstring":
               mapped.add("millenaire:craft_wool_string");
               break;
            case "makewallcarpetlarge":
               mapped.add("millenaire:craft_wallcarpet_large");
               break;
            case "makewallcarpetmedium":
               mapped.add("millenaire:craft_wallcarpet_medium");
               break;
            case "makewallcarpetsmall":
               mapped.add("millenaire:craft_wallcarpet_small");
               break;
            case "makemudbrickdecorated":
               mapped.add("millenaire:craft_mudbrick_decorated");
               break;
            case "makemudbrickornamented":
               mapped.add("millenaire:craft_mudbrick_ornamented");
               break;
            case "makemudbricksmooth":
               mapped.add("millenaire:craft_mudbrick_smooth");
               break;
            case "makemudbricksandstone":
               mapped.add("millenaire:craft_mudbrick_sandstone");
               break;
            case "makesandstone":
               mapped.add("millenaire:craft_sandstone_seljuk");
               break;
            case "makescimitar":
               mapped.add("millenaire:craft_scimitar");
               break;
            case "makeseljukbow":
               mapped.add("millenaire:craft_seljuk_bow");
               break;
            case "makearrowseljuk":
               mapped.add("millenaire:craft_arrow_seljuk");
               break;
            case "makeseljukhelmet":
               mapped.add("millenaire:craft_seljuk_helmet");
               break;
            case "makeseljukplate":
               mapped.add("millenaire:craft_seljuk_plate");
               break;
            case "makeseljuklegs":
               mapped.add("millenaire:craft_seljuk_legs");
               break;
            case "makeseljukboots":
               mapped.add("millenaire:craft_seljuk_boots");
               break;
            case "makeseljukturban":
               mapped.add("millenaire:craft_seljuk_turban");
               break;
            case "makeleatherhelmet":
               mapped.add("millenaire:craft_leather_helmet");
               break;
            case "makeleatherchest":
               mapped.add("millenaire:craft_leather_chest");
               break;
            case "makeleatherlegs":
               mapped.add("millenaire:craft_leather_legs");
               break;
            case "makeleatherboots":
               mapped.add("millenaire:craft_leather_boots");
               break;
            case "bmakeleatherhelmet":
               mapped.add("millenaire:craft_bandit_leather_helmet");
               break;
            case "bmakeleatherchest":
               mapped.add("millenaire:craft_bandit_leather_chest");
               break;
            case "bmakeleatherlegs":
               mapped.add("millenaire:craft_bandit_leather_legs");
               break;
            case "bmakeleatherboots":
               mapped.add("millenaire:craft_bandit_leather_boots");
               break;
            case "bmakesteelhelmet":
               mapped.add("millenaire:craft_bandit_steel_helmet");
               break;
            case "bmakesteelchest":
               mapped.add("millenaire:craft_bandit_steel_chest");
               break;
            case "bmakesteellegs":
               mapped.add("millenaire:craft_bandit_steel_legs");
               break;
            case "bmakesteelboots":
               mapped.add("millenaire:craft_bandit_steel_boots");
               break;
            case "bmakesteelsword":
               mapped.add("millenaire:craft_bandit_steel_sword");
               break;
            case "bmakestonesword":
               mapped.add("millenaire:craft_bandit_stone_sword");
               break;
            case "bmakebow":
               mapped.add("millenaire:craft_bandit_bow");
               break;
            case "cookbeefkitchen":
               mapped.add("millenaire:cook_beef_kitchen");
               break;
            case "cookchickenkitchenseljuk":
               mapped.add("millenaire:cook_chicken_kitchen");
               break;
            case "cookmuttonkitchen":
               mapped.add("millenaire:cook_mutton_kitchen");
               break;
            case "cookfishkitchen":
               mapped.add("millenaire:cook_fish_kitchen");
               break;
            case "slaughtercowseljuk":
               mapped.add("millenaire:slaughter_cow_seljuk");
               break;
            case "slaughtersheepseljuk":
               mapped.add("millenaire:slaughter_sheep_seljuk");
               break;
            case "gatherpistachioorchard":
               mapped.add("millenaire:gather_pistachio_orchard");
               break;
            case "plantsaplingpistachioorchard":
               mapped.add("millenaire:plant_sapling_pistachio_orchard");
               break;
            case "mineiron":
               mapped.add("millenaire:mine_iron");
               break;
            case "mining":
               mapped.add("millenaire:mine_stone");
               break;
            case "harvestwheatpaddy":
               mapped.add("millenaire:harvest_wheat_paddy");
               break;
            case "plantwheatpaddy":
               mapped.add("millenaire:plant_wheat_paddy");
               break;
            case "harvestcottonhome":
               mapped.add("millenaire:harvest_cotton_home");
               break;
            case "plantcottonhome":
               mapped.add("millenaire:plant_cotton_home");
               break;
            case "cookironore":
               mapped.add("millenaire:cook_iron_ore");
               break;
            case "makeiron":
               mapped.add("millenaire:craft_iron");
               break;
            case "makehay":
               mapped.add("millenaire:craft_hay");
               break;
            case "dyewool_cyan":
               mapped.add("millenaire:craft_dyewool_cyan");
               break;
            case "dyewool_green":
               mapped.add("millenaire:craft_dyewool_green");
               break;
            case "dyewool_lightblue":
               mapped.add("millenaire:craft_dyewool_lightblue");
               break;
            case "makecarpet_cyan":
               mapped.add("millenaire:craft_carpet_cyan");
               break;
            case "makecarpet_green":
               mapped.add("millenaire:craft_carpet_green");
               break;
            case "makecarpet_lightblue":
               mapped.add("millenaire:craft_carpet_lightblue");
               break;
            case "makebearsteak":
               mapped.add("millenaire:craft_bearsteak");
               break;
            case "makebearstew":
               mapped.add("millenaire:craft_bearstew");
               break;
            case "makemeatstew1":
               mapped.add("millenaire:craft_meatstew1");
               break;
            case "makemeatstew2":
               mapped.add("millenaire:craft_meatstew2");
               break;
            case "makemeatstew3":
               mapped.add("millenaire:craft_meatstew3");
               break;
            case "makemeatstew4":
               mapped.add("millenaire:craft_meatstew4");
               break;
            case "makemeatstew5":
               mapped.add("millenaire:craft_meatstew5");
               break;
            case "makemeatstew6":
               mapped.add("millenaire:craft_meatstew6");
               break;
            case "makemeatstew7":
               mapped.add("millenaire:craft_meatstew7");
               break;
            case "makemeatstew8":
               mapped.add("millenaire:craft_meatstew8");
               break;
            case "makepotatostew":
               mapped.add("millenaire:craft_potatostew");
               break;
            case "makewolfsteak":
               mapped.add("millenaire:craft_wolfsteak");
               break;
            case "makedirtwallinuit":
               mapped.add("millenaire:craft_dirtwall_inuit");
               break;
            case "makesnowbricksfromnothing":
               mapped.add("millenaire:craft_snow_bricks_from_nothing");
               break;
            case "makesnowbricksfromsnow":
               mapped.add("millenaire:craft_snow_bricks_from_snow");
               break;
            case "makesnowwall":
               mapped.add("millenaire:craft_snowwall");
               break;
            case "makeicebrick":
               mapped.add("millenaire:craft_icebrick");
               break;
            case "makefirepit":
               mapped.add("millenaire:craft_firepit");
               break;
            case "shoveldirt":
               mapped.add("millenaire:craft_shoveldirt");
               break;
            case "shoveldirt2":
               mapped.add("millenaire:craft_shoveldirt2");
               break;
            case "carveice":
               mapped.add("millenaire:craft_carveice");
               break;
            case "packsodspruce":
               mapped.add("millenaire:craft_packsodspruce");
               break;
            case "packsodbirch":
               mapped.add("millenaire:craft_packsodbirch");
               break;
            case "packsnowblock":
               mapped.add("millenaire:craft_packsnowblock");
               break;
            case "makebone":
               mapped.add("millenaire:craft_bone");
               break;
            case "makespade":
               mapped.add("millenaire:craft_spade");
               break;
            case "makespear":
               mapped.add("millenaire:craft_spear");
               break;
            case "makestonecauldron":
               mapped.add("millenaire:craft_stone_cauldron");
               break;
            case "makearrowinuit":
               mapped.add("millenaire:craft_arrowinuit");
               break;
            case "makewoodenaxeinuit":
               mapped.add("millenaire:craft_woodenaxeinuit");
               break;
            case "makewoodenhoeinuit":
               mapped.add("millenaire:craft_woodenhoeinuit");
               break;
            case "makewoodenpickaxeinuit":
               mapped.add("millenaire:craft_woodenpickaxeinuit");
               break;
            case "makewoodenshovelinuit":
               mapped.add("millenaire:craft_woodenshovelinuit");
               break;
            case "makeinuitbow":
               mapped.add("millenaire:craft_inuitbow");
               break;
            case "makebowinuit":
               mapped.add("millenaire:craft_bowinuit");
               break;
            case "mineicebricks":
               mapped.add("millenaire:mine_ice");
               break;
            case "minesnowbricks":
               mapped.add("millenaire:mine_snow");
               break;
            case "makeleather":
               mapped.add("millenaire:craft_leather");
               break;
            case "maketannedhide":
               mapped.add("millenaire:craft_tanned_hide");
               break;
            case "maketannedhide2":
               mapped.add("millenaire:craft_tanned_hide2");
               break;
            case "makehidehanging":
               mapped.add("millenaire:craft_hide_hanging");
               break;
            case "makeinuitbed":
               mapped.add("millenaire:craft_inuit_bed");
               break;
            case "makeinuitcarving":
               mapped.add("millenaire:craft_inuit_carving");
               break;
            case "makefurboots":
               mapped.add("millenaire:craft_furboots");
               break;
            case "makefurhelmet":
               mapped.add("millenaire:craft_furhelmet");
               break;
            case "makefurlegs":
               mapped.add("millenaire:craft_furlegs");
               break;
            case "makefurplate":
               mapped.add("millenaire:craft_furplate");
               break;
            case "makeleatherboots_inuit":
               mapped.add("millenaire:craft_leather_boots_inuit");
               break;
            case "makeleatherchest_inuit":
               mapped.add("millenaire:craft_leather_chest_inuit");
               break;
            case "makeleatherhelmet_inuit":
               mapped.add("millenaire:craft_leather_helmet_inuit");
               break;
            case "makeleatherlegs_inuit":
               mapped.add("millenaire:craft_leather_legs_inuit");
               break;
            case "plantpotato":
               mapped.add("millenaire:plant_potato");
               break;
            case "harvestpotato":
               mapped.add("millenaire:harvest_potato");
               break;
            case "plantpotatohome":
               mapped.add("millenaire:plant_potato_home");
               break;
            case "harvestpotatohome":
               mapped.add("millenaire:harvest_potato_home");
               break;
            case "minesnowpaths":
               mapped.add("millenaire:mine_snow_paths");
               break;
            case "cookpotato":
               mapped.add("millenaire:cook_potato");
               break;
            case "cookfishslab":
               mapped.add("millenaire:cook_seafood");
               break;
            case "slaughtercowinuits":
               mapped.add("millenaire:slaughter_cow_inuit");
               break;
            case "makesugar":
               mapped.add("millenaire:craft_sugar");
               break;
            default:
               System.out.println("  [WARN] Goal legacy non mappé : " + goal);
         }
      }

      return new ArrayList<>(new LinkedHashSet<>(mapped));
   }

   public static Map<String, Object> buildVillageType(
      LegacyDataParser.VillageTypeMeta vt, String culture, ItemIdMapper items, List<LegacyDataParser.BuildingWithVariants> allBuildings
   ) {
      return buildVillageType(vt, culture, items, allBuildings, null);
   }

   public static Map<String, Object> buildVillageType(
      LegacyDataParser.VillageTypeMeta vt,
      String culture,
      ItemIdMapper items,
      List<LegacyDataParser.BuildingWithVariants> allBuildings,
      BiomeMapper biomeMapper
   ) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("culture", "millenaire:" + culture);
      json.put("name", vt.name());
      json.put("weight", vt.playerControlled() ? 0 : vt.weight());
      if (vt.playerControlled()) {
         json.put("player_controlled", true);
      }

      if (vt.loneBuilding()) {
         json.put("lone_building", true);
         if (vt.namelist() != null) {
            json.put("namelist", vt.namelist());
         }

         if (vt.radius() != 80) {
            json.put("radius", vt.radius());
         }

         if (vt.max() >= 0) {
            json.put("max", vt.max());
         }

         if (vt.minDistanceFromSpawn() > 0) {
            json.put("min_distance_from_spawn", vt.minDistanceFromSpawn());
         }

         if (vt.minimumBiomeValidity() > 0.0) {
            json.put("minimum_biome_validity", vt.minimumBiomeValidity());
         }

         if (vt.centre() != null) {
            allBuildings.stream().filter(b -> b.meta().baseName().equals(vt.centre())).findFirst().ifPresent(b -> {
               if (b.meta().showTownHallSigns()) {
                  json.put("show_town_hall_signs", true);
               }
            });
         }
      }

      if (!vt.loneBuilding() && vt.namelist() != null) {
         json.put("namelist", vt.namelist());
      }

      if (biomeMapper != null && !vt.playerControlled() && vt.weight() > 0) {
         List<String> tags = vt.biomes().isEmpty() ? List.of() : biomeMapper.mapAll(culture, vt.biomes());
         if (tags.isEmpty()) {
            tags = List.of("#minecraft:is_overworld");
         }

         json.put("biome_tags", tags);
      }

      List<Map<String, Object>> layout = new ArrayList<>();
      if (vt.centre() != null) {
         Map<String, Object> slot = new LinkedHashMap<>();
         slot.put("plan", "millenaire:" + culture + "/" + LegacyIdCanonicaliser.buildingPlanRefId(vt.centre()));
         slot.put("role", "centre");
         layout.add(slot);
      }

      for (String plan : vt.start()) {
         Map<String, Object> slot = new LinkedHashMap<>();
         slot.put("plan", "millenaire:" + culture + "/" + LegacyIdCanonicaliser.buildingPlanRefId(plan));
         slot.put("role", "start");
         layout.add(slot);
      }

      for (String plan : vt.core()) {
         if (!SKIPPED_GIFT_PLANS.contains(sanitize(plan))) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("plan", "millenaire:" + culture + "/" + LegacyIdCanonicaliser.buildingPlanRefId(plan));
            slot.put("role", "core");
            layout.add(slot);
         }
      }

      for (String plan : vt.secondary()) {
         Map<String, Object> slot = new LinkedHashMap<>();
         slot.put("plan", "millenaire:" + culture + "/" + LegacyIdCanonicaliser.buildingPlanRefId(plan));
         slot.put("role", "secondary");
         layout.add(slot);
      }

      json.put("layout", layout);
      Map<String, Integer> sellingOverrides = buildPriceOverrides(vt.sellingPriceOverrides());
      Map<String, Integer> buyingOverrides = buildPriceOverrides(vt.buyingPriceOverrides());
      if (!sellingOverrides.isEmpty()) {
         json.put("selling_price_overrides", sellingOverrides);
      }

      if (!buyingOverrides.isEmpty()) {
         json.put("buying_price_overrides", buyingOverrides);
      }

      if (vt.icon() != null) {
         json.put("icon", items.resolve(culture, vt.icon()).orElse(vt.icon()));
      }

      if (vt.carriesRaid()) {
         json.put("carries_raid", true);
      }

      if (!vt.qualifiers().isEmpty()) {
         json.put("qualifiers", vt.qualifiers());
      }

      if (vt.hillQualifier() != null) {
         json.put("hill_qualifier", vt.hillQualifier());
      }

      if (vt.mountainQualifier() != null) {
         json.put("mountain_qualifier", vt.mountainQualifier());
      }

      if (vt.desertQualifier() != null) {
         json.put("desert_qualifier", vt.desertQualifier());
      }

      if (vt.forestQualifier() != null) {
         json.put("forest_qualifier", vt.forestQualifier());
      }

      if (vt.lavaQualifier() != null) {
         json.put("lava_qualifier", vt.lavaQualifier());
      }

      if (vt.lakeQualifier() != null) {
         json.put("lake_qualifier", vt.lakeQualifier());
      }

      if (vt.oceanQualifier() != null) {
         json.put("ocean_qualifier", vt.oceanQualifier());
      }

      if (!vt.pathMaterials().isEmpty()) {
         json.put("path_materials", vt.pathMaterials());
      }

      if (!vt.playerBuildings().isEmpty()) {
         json.put(
            "player_buildings", vt.playerBuildings().stream().map(p -> "millenaire:" + culture + "/" + LegacyIdCanonicaliser.buildingPlanRefId(p)).toList()
         );
      }

      if (vt.innerWallType() != null) {
         json.put("inner_wall_type", vt.innerWallType());
      }

      if (vt.innerWallRadius() > 0) {
         json.put("inner_wall_radius", vt.innerWallRadius());
      }

      if (vt.outerWallType() != null) {
         json.put("outer_wall_type", vt.outerWallType());
      }

      if (vt.outerWallRadius() > 0) {
         json.put("outer_wall_radius", vt.outerWallRadius());
      }

      if (!vt.bannerJsons().isEmpty()) {
         json.put("banner_json", vt.bannerJsons());
      }

      if (vt.maxSimultaneousWallConstructions() > 0) {
         json.put("max_simultaneous_wall_constructions", vt.maxSimultaneousWallConstructions());
      }

      if (vt.maxSimultaneousConstructions() > 1) {
         json.put("max_simultaneous_constructions", vt.maxSimultaneousConstructions());
      }

      if (!vt.loneBuilding() && vt.radius() != 80) {
         json.put("radius", vt.radius());
      }

      if (!vt.never().isEmpty()) {
         json.put("never", vt.never().stream().map(LegacyIdCanonicaliser::buildingPlanRefId).toList());
      }

      if (!vt.brickColourThemes().isEmpty()) {
         json.put("brick_colour_themes", vt.brickColourThemes().stream().map(LegacyJsonBuilder::buildBrickColourThemeJson).toList());
      }

      if (!vt.hamlets().isEmpty()) {
         json.put("hamlets", vt.hamlets().stream().map(h -> "millenaire:" + culture + "/" + LegacyIdCanonicaliser.villageTypeRefId(h)).toList());
      }

      if (vt.specialType() != null) {
         json.put("special_type", vt.specialType());
      }

      if (!vt.loneBuilding() && !vt.spawnable()) {
         json.put("spawnable", false);
      }

      if (!vt.allowExtraBuildings()) {
         json.put("allow_extra_buildings", false);
      }

      return json;
   }

   private static Map<String, Object> buildBrickColourThemeJson(LegacyDataParser.BrickColourThemeLegacy theme) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("name", theme.name());
      json.put("weight", theme.weight());
      Map<String, Object> colours = new LinkedHashMap<>();

      for (Entry<String, List<LegacyDataParser.WeightedColorLegacy>> entry : theme.colourGroups().entrySet()) {
         List<Map<String, Object>> colorList = new ArrayList<>();

         for (LegacyDataParser.WeightedColorLegacy wc : entry.getValue()) {
            Map<String, Object> colorObj = new LinkedHashMap<>();
            String colorName = "silver".equals(wc.color()) ? "light_gray" : wc.color();
            colorObj.put("color", colorName);
            colorObj.put("weight", wc.weight());
            colorList.add(colorObj);
         }

         colours.put(entry.getKey(), colorList);
      }

      json.put("colours", colours);
      return json;
   }

   private static Map<String, Integer> buildPriceOverrides(Map<String, String> legacyPrices) {
      Map<String, Integer> result = new LinkedHashMap<>();

      for (Entry<String, String> entry : legacyPrices.entrySet()) {
         String goodId = LEGACY_TO_GOOD_ID.get(entry.getKey());
         if (goodId == null) {
            goodId = LEGACY_TO_GOOD_ID.get(entry.getKey().toLowerCase());
         }

         if (goodId == null) {
            System.out.println("  [INFO] Price override ignored (legacy item not mapped): " + entry.getKey());
         } else {
            try {
               int price = parseLegacyPrice(entry.getValue());
               result.put(goodId, price);
            } catch (NumberFormatException e) {
               System.err.println("  [WARN] Invalid price override for " + entry.getKey() + ": " + entry.getValue());
            }
         }
      }

      return result;
   }

   static int parseLegacyPrice(String raw) {
      String[] parts = raw.split("/");
      if (parts.length == 1) {
         return Integer.parseInt(parts[0].trim());
      } else {
         return parts.length == 2
            ? Integer.parseInt(parts[0].trim()) * 64 + Integer.parseInt(parts[1].trim())
            : Integer.parseInt(parts[0].trim()) * 64 * 64 + Integer.parseInt(parts[1].trim()) * 64 + Integer.parseInt(parts[2].trim());
      }
   }

   public static Map<String, Object> buildShop(LegacyDataParser.ShopMeta shop, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      String shopId = shop.id();
      json.put("sells", mapShopItems(shop.sells(), culture, shopId, "sells", items));
      json.put("buys", mapShopItems(shop.buys(), culture, shopId, "buys", items));
      json.put("buys_optional", mapShopItems(shop.buysOptional(), culture, shopId, "buys_optional", items));
      List<String> deliverTo = new ArrayList<>();

      for (String legacyItem : shop.deliverTo()) {
         Optional<String> resolved = items.resolveExact(culture, legacyItem);
         if (resolved.isPresent()) {
            deliverTo.add(resolved.get());
         } else {
            System.out.println("    [WARN] deliver_to item not mapped: " + legacyItem);
         }
      }

      json.put("deliver_to", deliverTo);
      return json;
   }

   private static List<String> mapShopItems(List<String> legacyItems, String culture, String shopId, String slot, ItemIdMapper items) {
      List<String> mapped = new ArrayList<>();

      for (String legacyItem : legacyItems) {
         String goodId = LEGACY_TO_GOOD_ID.get(legacyItem);
         if (goodId == null) {
            goodId = LEGACY_TO_GOOD_ID.get(legacyItem.toLowerCase());
         }

         if (goodId != null) {
            mapped.add(goodId);
         } else {
            Optional<String> resolved = items.resolveForShopEntry(culture, legacyItem, shopId, slot);
            if (resolved.isPresent()) {
               mapped.add(legacyItemToGoodId(legacyItem));
            }
         }
      }

      return mapped;
   }

   public static Map<String, Object> buildGatheringType(String category, Map<String, List<String>> txt, String culture, ItemIdMapper items) {
      return category != null && category.startsWith("genericcrafting")
         ? buildGatheringTypeJson(txt, culture, items)
         : buildNonCraftingGatheringTypeJson(category, txt, culture, items);
   }

   public static Map<String, Object> buildTradedGoods(List<LegacyDataParser.TradedGoodMeta> goods, String culture, ItemIdMapper items) {
      List<Map<String, Object>> goodsJson = new ArrayList<>();

      for (LegacyDataParser.TradedGoodMeta tg : goods) {
         Optional<String> resolved = items.resolveForTradedGood(culture, tg.name());
         if (!resolved.isEmpty()) {
            String mcItem = resolved.get();
            String goodId = legacyItemToGoodId(tg.name());
            Map<String, Object> good = new LinkedHashMap<>();
            good.put("id", goodId);
            good.put("item", mcItem);
            int sellingPrice = evalLegacyPrice(tg.sellingPrice());
            int buyingPrice = evalLegacyPrice(tg.buyingPrice());
            if (sellingPrice > 0) {
               good.put("selling_price", sellingPrice);
            }

            if (buyingPrice > 0) {
               good.put("buying_price", buyingPrice);
            }

            if (tg.reservedQuantity() > 0) {
               good.put("reserved_quantity", tg.reservedQuantity());
            }

            if (tg.targetQuantity() > 0) {
               good.put("target_quantity", tg.targetQuantity());
            }

            if (tg.autoGenerated()) {
               good.put("auto_generate", true);
            }

            if (tg.minReputation() > 0) {
               good.put("min_reputation", tg.minReputation());
            }

            int foreignMerchantPrice = evalLegacyPrice(tg.foreignMerchantPrice());
            if (foreignMerchantPrice > 0) {
               good.put("foreign_merchant_price", foreignMerchantPrice);
            }

            good.put("category", tg.category());
            goodsJson.add(good);
         }
      }

      Map<String, Object> root = new LinkedHashMap<>();
      root.put("goods", goodsJson);
      return root;
   }

   static int evalLegacyPrice(String raw) {
      if (raw != null && !raw.isBlank()) {
         raw = raw.trim();
         if (raw.equals("0")) {
            return 0;
         }

         try {
            String[] parts = raw.split("\\*");
            int result = 1;

            for (String part : parts) {
               result *= Integer.parseInt(part.trim());
            }

            return result;
         } catch (NumberFormatException e) {
            System.out.println("  [WARN] Legacy price not parseable: " + raw);
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static Map<String, Object> buildCultureStub(String cultureId, Map<String, List<String>> cultureTxt) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("culture_id", cultureId);
      String displayName = firstOrNull(cultureTxt, "name");
      if (displayName != null) {
         out.put("display_name", displayName);
      }

      String defaultVillageType = firstOrNull(cultureTxt, "default_village");
      if (defaultVillageType != null) {
         out.put("default_village_type", defaultVillageType);
      }

      String cultureBanner = firstOrNull(cultureTxt, "cultureBanner");
      if (cultureBanner != null && !cultureBanner.isBlank()) {
         out.put("culture_banner_nbt", cultureBanner);
      }

      for (Entry<String, List<String>> e : cultureTxt.entrySet()) {
         String key = e.getKey();
         if (!out.containsKey(key) && !"name".equals(key) && !"default_village".equals(key) && !"cultureBanner".equals(key)) {
            List<String> values = e.getValue();
            if (!values.isEmpty()) {
               if (values.size() == 1) {
                  out.put(key, values.get(0));
               } else {
                  out.put(key, List.copyOf(values));
               }
            }
         }
      }

      return out;
   }

   private static String firstOrNull(Map<String, List<String>> m, String key) {
      List<String> v = m.get(key);
      return v != null && !v.isEmpty() ? v.get(0) : null;
   }

   public static String sanitize(String name) {
      return LegacyIdCanonicaliser.sanitize(name);
   }

   public static String prefixedId(String culture, String id) {
      return LegacyIdCanonicaliser.prefixed(culture, id);
   }

   public static String stripCulturePrefix(String culture, String id) {
      return LegacyIdCanonicaliser.stripCulturePrefix(culture, id);
   }

   public static String legacyItemToGoodId(String legacyItem) {
      String explicit = LEGACY_TO_GOOD_ID.get(legacyItem);
      if (explicit == null) {
         explicit = LEGACY_TO_GOOD_ID.get(legacyItem.toLowerCase());
      }

      if (explicit != null) {
         return explicit;
      }

      return switch (legacyItem.toLowerCase()) {
         case "normanaxe" -> "norman_axe";
         case "normanpickaxe" -> "norman_pickaxe";
         case "normanshovel" -> "norman_shovel";
         case "normanhoe" -> "norman_hoe";
         case "normanhelmet" -> "norman_helmet";
         case "normanplate" -> "norman_chestplate";
         case "normanlegs" -> "norman_leggings";
         case "normanboots" -> "norman_boots";
         case "normansword" -> "norman_sword";
         case "cider" -> "cider";
         case "calva" -> "calva";
         case "ciderapple" -> "cider_apple";
         case "tripes" -> "tripes";
         case "boudin" -> "boudin";
         case "bottle" -> "bottle";
         case "netherwart" -> "nether_wart";
         case "beefraw" -> "beef_raw";
         case "beefcooked" -> "beef_cooked";
         case "porkchopscooked" -> "porkchop_cooked";
         case "chickenmeat" -> "chicken_raw";
         case "chickenmeatcooked" -> "chicken_cooked";
         case "stained_glass_white" -> "stained_glass_white";
         case "stained_glass_yellow" -> "stained_glass_yellow";
         case "stained_glass_yellow_red" -> "stained_glass_yellow_red";
         case "stained_glass_red_blue" -> "stained_glass_red_blue";
         case "stained_glass_green_blue" -> "stained_glass_green_blue";
         case "rosette" -> "rosette";
         case "timberframeplain" -> "timber_frame_plain";
         case "timberframecross" -> "timber_frame_cross";
         case "bed_straw" -> "straw_bed";
         case "dirtwall" -> "dirt_wall";
         case "pathdirt" -> "path_dirt";
         case "pathgravel" -> "path_gravel";
         case "pathslabs" -> "path_slabs";
         case "pathgravelslabs" -> "path_gravel_slab";
         case "pathochretiles" -> "path_ochre_tiles";
         case "pathsandstone" -> "path_sandstone";
         case "dye_white" -> "dye_white";
         case "dye_red" -> "dye_red";
         case "dye_yellow" -> "dye_yellow";
         case "dye_lightblue" -> "dye_light_blue";
         case "carpet_white" -> "carpet_white";
         case "carpet_red" -> "carpet_red";
         case "carpet_yellow" -> "carpet_yellow";
         case "carpet_blue" -> "carpet_blue";
         case "tapestry" -> "tapestry";
         case "banner_white" -> "banner_white";
         case "cauldron" -> "cauldron";
         case "seeds" -> "seeds";
         case "sand" -> "sand";
         case "sugar" -> "sugar";
         case "bow" -> "bow";
         case "arrow" -> "arrow";
         case "cake" -> "cake";
         case "carrot" -> "carrot";
         case "iron" -> "iron_ingot";
         case "gold" -> "gold_ingot";
         case "glass" -> "glass";
         case "purse" -> "purse";
         case "summoningwand" -> "summoning_wand";
         case "sapling_appletree" -> "sapling_appletree";
         case "norpattern" -> "norpattern";
         case "feather" -> "feather";
         case "bone" -> "bone";
         case "paper" -> "paper";
         case "book" -> "book";
         case "turmeric" -> "turmeric";
         case "rasgulla" -> "rasgulla";
         case "chickencurry" -> "chickencurry";
         case "vegcurry" -> "vegcurry";
         case "rice" -> "rice";
         case "cotton" -> "cotton";
         case "brickmould" -> "brick_mould";
         case "paintbucketwhite" -> "paint_bucket_white";
         case "decoratedbrickwhite" -> "decorated_brick_white";
         case "paintedbrickwhite" -> "painted_brick_white";
         case "indianstatue" -> "indian_statue";
         case "woodenbarsindian" -> "wooden_bars_indian";
         case "bed_charpoy" -> "charpoy";
         case "sandstone_carved" -> "sandstone_carved";
         case "red_sandstone_carved" -> "red_sandstone_carved";
         case "ochre_sandstone_carved" -> "ochre_sandstone_carved";
         case "mudbrick" -> "mud_brick";
         case "thatch" -> "thatch";
         case "woodenbars" -> "wooden_bars";
         case "woodenbarsrosette" -> "wooden_bars_rosette";
         case "sandstone" -> "sandstone";
         case "redsandstone" -> "red_sandstone";
         case "diamond" -> "diamond";
         case "dye_blue" -> "dye_blue";
         case "cactus" -> "cactus";
         case "fishcooked" -> "cooked_cod";
         case "cooked_mutton" -> "cooked_mutton";
         case "cookie" -> "cookie";
         case "sugarcane" -> "sugar_cane";
         case "stonesword" -> "stone_sword";
         case "stonepickaxe" -> "stone_pickaxe";
         case "stoneaxe" -> "stone_axe";
         case "stoneshovel" -> "stone_shovel";
         case "stonehoe" -> "stone_hoe";
         case "steelsword" -> "iron_sword";
         case "steelhelmet" -> "iron_helmet";
         case "steelchest" -> "iron_chestplate";
         case "steellegs" -> "iron_leggings";
         case "steelboots" -> "iron_boots";
         case "steelpickaxe" -> "iron_pickaxe";
         case "steelaxe" -> "iron_axe";
         case "steelshovel" -> "iron_shovel";
         case "steelhoe" -> "iron_hoe";
         case "leatherhelmet" -> "leather_helmet";
         case "leatherchest" -> "leather_chestplate";
         case "leatherlegs" -> "leather_leggings";
         case "leatherboots" -> "leather_boots";
         case "diamondsword" -> "diamond_sword";
         case "diamondhelmet" -> "diamond_helmet";
         case "diamondchest" -> "diamond_chestplate";
         case "diamondlegs" -> "diamond_leggings";
         case "diamondboots" -> "diamond_boots";
         case "diamondpickaxe" -> "diamond_pickaxe";
         case "diamondaxe" -> "diamond_axe";
         case "diamondshovel" -> "diamond_shovel";
         case "diamondhoe" -> "diamond_hoe";
         default -> legacyItem.toLowerCase();
      };
   }

   public static Map<String, Object> buildNonCraftingGatheringTypeJson(String category, Map<String, List<String>> config, String culture, ItemIdMapper items) {
      String baseCategory = category.contains("/") ? category.substring(0, category.indexOf(47)) : category;

      return switch (baseCategory) {
         case "genericharvesting" -> buildHarvestingJson(config, culture, items);
         case "genericplanting" -> buildPlantingJson(config, culture, items);
         case "genericmining" -> buildMiningJson(config, culture, items);
         case "genericslaughteranimal" -> buildSlaughterJson(config, culture, items);
         case "genericcooking" -> buildSmeltingJson(config, culture, items);
         case "genericplantsapling" -> buildSaplingPlantingJson(config, culture, items);
         case "genericgatherblocks" -> buildFruitHarvestingJson(config, culture, items);
         case "generictakefrombuilding" -> buildTakeFromBuildingJson(config, culture, items);
         default -> null;
      };
   }

   static Map<String, Object> buildHarvestingJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "harvesting");
      int priority = getIntValue(config, "priority", 40);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 32);
      json.put("batchRadius", 8);
      json.put("maxActionsPerTask", 16);
      json.put("actionCooldown", 10);
      json.put("stuckTimeout", 4000);
      json.put("arrivalRange", 8);
      json.put("walkSpeed", 0.6);
      parseLimitField(config, "villagelimit", culture, items).ifPresent(lim -> json.put("villageLimit", lim));
      parseLimitField(config, "buildinglimit", culture, items).ifPresent(lim -> json.put("buildingLimit", lim));
      parseLimitField(config, "townhalllimit", culture, items).ifPresent(lim -> json.put("townhallLimit", lim));
      int maxInBuilding = getIntValue(config, "maxsimultaneousinbuilding", -1);
      if (maxInBuilding > 0) {
         json.put("maxSimultaneousInBuilding", maxInBuilding);
      }

      int maxTotal = getIntValue(config, "maxsimultaneoustotal", -1);
      if (maxTotal > 0) {
         json.put("maxSimultaneousTotal", maxTotal);
      }

      json.put("minimumHour", -1);
      json.put("maximumHour", 12500);
      json.put("reoccurDelay", -1);
      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String cropType = getStringValue(config, "croptype");
      if ("flower".equals(cropType)) {
         String harvestBlockstate = getStringValue(config, "harvestblockstate");
         if (harvestBlockstate != null) {
            String modernBlock = LEGACY_FLOWER_MAP.get(harvestBlockstate);
            if (modernBlock == null) {
               String mapped = mapLegacyBlockStateLine(harvestBlockstate);
               if (!mapped.equals(harvestBlockstate)) {
                  modernBlock = mapped;
               }
            }

            if (modernBlock != null) {
               handlerParams.put("targetBlock", modernBlock);
            } else {
               System.out.println("    [WARN] Unknown flower blockstate: " + harvestBlockstate);
               handlerParams.put("targetBlock", harvestBlockstate);
            }
         }

         handlerParams.put("soilSubtype", "flower");
         String sentenceKey = getStringValue(config, "sentencekey");
         boolean isSteal = "stealflower".equals(sentenceKey);
         boolean isZeroChanceHarvest = false;
         List<String> harvestItemFlower = config.getOrDefault("harvestitem", List.of());
         if (!harvestItemFlower.isEmpty()) {
            String[] hparts = harvestItemFlower.getFirst().split(",", 2);
            if (hparts.length >= 2) {
               try {
                  isZeroChanceHarvest = Integer.parseInt(hparts[1].trim()) == 0;
               } catch (NumberFormatException var16) {
               }
            }
         }

         if (isSteal || isZeroChanceHarvest) {
            handlerParams.put("skipDrops", true);
         }

         String buildingTag = getStringValue(config, "buildingtag");
         if (buildingTag != null) {
            handlerParams.put("buildingTag", buildingTag);
         }
      } else if (cropType != null) {
         String targetBlock = LEGACY_CROP_BLOCK_MAP.getOrDefault(cropType, cropType);
         if ("millenaire:crop_rice".equals(targetBlock)) {
            json.put("handler", "paddy_harvesting");
            String soilSubtype = CROP_SOIL_SUBTYPE_MAP.get(targetBlock);
            if (soilSubtype != null) {
               handlerParams.put("soilSubtype", soilSubtype);
            }

            String buildingTag = getStringValue(config, "buildingtag");
            if (buildingTag != null) {
               handlerParams.put("buildingTag", buildingTag);
            }
         } else {
            handlerParams.put("targetBlock", targetBlock);
            handlerParams.put("targetState", Map.of("age", 7));
            String soilSubtype = CROP_SOIL_SUBTYPE_MAP.get(targetBlock);
            if (soilSubtype != null) {
               handlerParams.put("soilSubtype", soilSubtype);
            } else {
               System.out.println("    [WARN] No soilSubtype mapping for targetBlock: " + targetBlock + " (cropType: " + cropType + ")");
            }

            String buildingTag = getStringValue(config, "buildingtag");
            if (buildingTag != null) {
               handlerParams.put("buildingTag", buildingTag);
            }
         }
      }

      int reoccur = getIntValue(config, "reoccurdelay", -1);
      if (reoccur > 0) {
         json.put("reoccurDelay", reoccur / 50);
      }

      json.put("handlerParams", handlerParams);
      addSentenceAndLabelKeys(config, json);
      addHeldItems(config, json, culture, items);
      addLeisureFlag(config, json);
      return json;
   }

   static Map<String, Object> buildPlantingJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "planting");
      int priority = getIntValue(config, "priority", 50);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 32);
      json.put("batchRadius", 8);
      json.put("maxActionsPerTask", 16);
      json.put("actionCooldown", 10);
      json.put("stuckTimeout", 4000);
      json.put("walkSpeed", 0.6);
      int maxInBuilding = getIntValue(config, "maxsimultaneousinbuilding", -1);
      if (maxInBuilding > 0) {
         json.put("maxSimultaneousInBuilding", maxInBuilding);
      }

      int maxTotal = getIntValue(config, "maxsimultaneoustotal", -1);
      if (maxTotal > 0) {
         json.put("maxSimultaneousTotal", maxTotal);
      }

      json.put("minimumHour", -1);
      json.put("maximumHour", 12500);
      json.put("reoccurDelay", -1);
      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String cropType = getStringValue(config, "croptype");
      if ("flower".equals(cropType)) {
         json.put("handler", "flower_planting");
         List<String> flowers = parseFlowerBlockstates(config, "plantblockstate");
         if (!flowers.isEmpty()) {
            handlerParams.put("flowers", flowers);
         }

         String legacySeed = getStringValue(config, "seed");
         if (legacySeed != null) {
            String seedItem = "dye_white".equals(legacySeed) ? "minecraft:bone_meal" : items.resolve(culture, legacySeed).orElse(legacySeed);
            handlerParams.put("seedItem", seedItem);
         }

         String buildingTag = getStringValue(config, "buildingtag");
         if (buildingTag == null) {
            buildingTag = getStringValue(config, "requiredtag");
         }

         if (buildingTag != null) {
            handlerParams.put("buildingTag", buildingTag);
         }
      } else if (cropType != null) {
         String cropBlock = LEGACY_CROP_BLOCK_MAP.getOrDefault(cropType, cropType);
         if ("millenaire:crop_rice".equals(cropBlock)) {
            json.put("handler", "paddy_planting");
            String soilSubtype = CROP_SOIL_SUBTYPE_MAP.get(cropBlock);
            if (soilSubtype != null) {
               handlerParams.put("soilSubtype", soilSubtype);
            }

            String buildingTag = getStringValue(config, "buildingtag");
            if (buildingTag != null) {
               handlerParams.put("buildingTag", buildingTag);
            }
         } else {
            handlerParams.put("cropBlock", cropBlock);
            String soilBlock = CROP_SOIL_MAP.get(cropBlock);
            if (soilBlock != null) {
               handlerParams.put("soilBlock", soilBlock);
            }

            String seedItem = CROP_SEED_MAP.get(cropBlock);
            if (seedItem != null) {
               handlerParams.put("seedItem", seedItem);
            }

            String soilSubtype = CROP_SOIL_SUBTYPE_MAP.get(cropBlock);
            if (soilSubtype != null) {
               handlerParams.put("soilSubtype", soilSubtype);
            } else {
               System.out.println("    [WARN] No soilSubtype mapping for cropBlock: " + cropBlock + " (cropType: " + cropType + ")");
            }

            String buildingTag = getStringValue(config, "buildingtag");
            if (buildingTag != null) {
               handlerParams.put("buildingTag", buildingTag);
            }
         }
      } else {
         json.put("handler", "flower_planting");
         String buildingTag = getStringValue(config, "buildingtag");
         if (buildingTag != null) {
            handlerParams.put("buildingTag", buildingTag);
         }
      }

      int reoccur = getIntValue(config, "reoccurdelay", -1);
      if (reoccur > 0) {
         json.put("reoccurDelay", reoccur / 50);
      }

      json.put("handlerParams", handlerParams);
      addSentenceAndLabelKeys(config, json);
      addHeldItems(config, json, culture, items);
      addLeisureFlag(config, json);
      return json;
   }

   static Map<String, Object> buildMiningJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "mining");
      int priority = getIntValue(config, "priority", 35);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 32);
      json.put("batchRadius", 8);
      json.put("maxActionsPerTask", 8);
      json.put("actionCooldown", 70);
      json.put("stuckTimeout", 200);
      json.put("arrivalRange", 5);
      json.put("walkSpeed", 0.6);
      parseLimitField(config, "townhalllimit", culture, items).ifPresent(lim -> json.put("townhallLimit", lim));
      parseLimitField(config, "villagelimit", culture, items).ifPresent(lim -> json.put("villageLimit", lim));
      parseLimitField(config, "buildinglimit", culture, items).ifPresent(lim -> json.put("buildingLimit", lim));
      int maxInBuilding = getIntValue(config, "maxsimultaneousinbuilding", -1);
      if (maxInBuilding > 0) {
         json.put("maxSimultaneousInBuilding", maxInBuilding);
      }

      int maxTotal = getIntValue(config, "maxsimultaneoustotal", 1);
      json.put("maxSimultaneousTotal", maxTotal);
      json.put("minimumHour", -1);
      json.put("maximumHour", 12500);
      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String sourceBlockState = getStringValue(config, "sourceblockstate");
      if (sourceBlockState != null) {
         String baseBlock = sourceBlockState.contains(";") ? sourceBlockState.substring(0, sourceBlockState.indexOf(59)) : sourceBlockState;
         String sourceSubtype = MINING_SOURCE_MAP.get(baseBlock);
         if (sourceSubtype != null) {
            handlerParams.put("sourceSubtype", sourceSubtype);
         }
      }

      String buildingTag = getStringValue(config, "buildingtag");
      if (buildingTag != null) {
         handlerParams.put("buildingTag", buildingTag);
      }

      List<String> lootLines = config.getOrDefault("loot", List.of());
      if (!lootLines.isEmpty()) {
         List<Map<String, Object>> loot = new ArrayList<>();

         for (String lootLine : lootLines) {
            String[] parts = lootLine.split(",", 2);
            String legacyItem = parts[0].trim();
            int count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            String modernId = items.resolve(culture, legacyItem).orElse(null);
            if (modernId != null) {
               Map<String, Object> entry = new LinkedHashMap<>();
               entry.put("item", modernId);
               entry.put("count", count);
               loot.add(entry);
            }
         }

         if (!loot.isEmpty()) {
            handlerParams.put("loot", loot);
         }
      }

      json.put("handlerParams", handlerParams);
      addSentenceAndLabelKeys(config, json);
      return json;
   }

   static Map<String, Object> buildSlaughterJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "slaughter");
      int priority = getIntValue(config, "priority", 50);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 25);
      json.put("batchRadius", 25);
      json.put("maxActionsPerTask", 3);
      json.put("actionCooldown", 20);
      json.put("stuckTimeout", 4000);
      json.put("arrivalRange", getIntValue(config, "range", 1));
      json.put("walkSpeed", 0.7);
      json.put("minimumHour", -1);
      json.put("maximumHour", 12500);
      json.put("maxSimultaneousTotal", 1);
      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String animalKey = getStringValue(config, "animalkey");
      if (animalKey != null) {
         String modernAnimal = LEGACY_ANIMAL_MAP.getOrDefault(animalKey, "minecraft:" + animalKey);
         handlerParams.put("animalType", modernAnimal);
      }

      String buildingTag = getStringValue(config, "buildingtag");
      String requiredTag = getStringValue(config, "requiredtag");
      if (buildingTag != null) {
         handlerParams.put("buildingTag", buildingTag);
      } else if (requiredTag != null) {
         handlerParams.put("buildingTag", requiredTag);
      }

      if (requiredTag != null) {
         handlerParams.put("requiredTag", requiredTag);
      }

      handlerParams.put("damage", 4.0);
      List<String> bonusLines = config.get("bonusitem");
      if (bonusLines != null && !bonusLines.isEmpty()) {
         List<Map<String, Object>> bonusItems = new ArrayList<>();

         for (String line : bonusLines) {
            String[] parts = line.split(",");
            if (parts.length >= 2) {
               String modernItem = items.resolve(culture, parts[0].trim()).orElse(null);
               if (modernItem != null) {
                  try {
                     Map<String, Object> bonus = new LinkedHashMap<>();
                     bonus.put("item", modernItem);
                     bonus.put("chance", Integer.parseInt(parts[1].trim()));
                     if (parts.length >= 3) {
                        bonus.put("requiredTag", parts[2].trim());
                     }

                     bonusItems.add(bonus);
                  } catch (NumberFormatException e) {
                     System.out.println("  [WARN] Invalid bonusitem chance: " + line);
                  }
               }
            }
         }

         if (!bonusItems.isEmpty()) {
            handlerParams.put("bonusItems", bonusItems);
         }
      }

      json.put("handlerParams", handlerParams);
      addSentenceAndLabelKeys(config, json);
      return json;
   }

   static Map<String, Object> buildSmeltingJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "smelting");
      int priority = getIntValue(config, "priority", 50);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 32);
      json.put("batchRadius", 8);
      json.put("maxActionsPerTask", 3);
      json.put("actionCooldown", 100);
      json.put("stuckTimeout", 4000);
      json.put("walkSpeed", 0.6);
      parseLimitField(config, "villagelimit", culture, items).ifPresent(lim -> json.put("villageLimit", lim));
      parseLimitField(config, "buildinglimit", culture, items).ifPresent(lim -> json.put("buildingLimit", lim));
      parseLimitField(config, "townhalllimit", culture, items).ifPresent(lim -> json.put("townhallLimit", lim));
      int maxInBuilding = getIntValue(config, "maxsimultaneousinbuilding", -1);
      if (maxInBuilding > 0) {
         json.put("maxSimultaneousInBuilding", maxInBuilding);
      }

      String balanceLine = getStringValue(config, "itemsbalance");
      if (balanceLine != null) {
         String[] parts = balanceLine.split(",", 2);
         if (parts.length == 2) {
            String inputItem = items.resolve(culture, parts[0].trim()).orElse(null);
            String outputItem = items.resolve(culture, parts[1].trim()).orElse(null);
            if (inputItem != null && outputItem != null) {
               json.put("itemsBalance", Map.of(inputItem, outputItem));
            }
         }
      }

      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String itemToCook = getStringValue(config, "itemtocook");
      if (itemToCook != null) {
         String[] mapping = LEGACY_COOKING_MAP.get(itemToCook);
         if (mapping != null) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("item", mapping[0]);
            input.put("count", 1);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("item", mapping[1]);
            output.put("count", 1);
            handlerParams.put("inputs", List.of(input));
            handlerParams.put("outputs", List.of(output));
         }

         String buildingTag = getStringValue(config, "buildingtag");
         if (buildingTag != null) {
            handlerParams.put("buildingTag", buildingTag);
         }
      }

      int minimumToCook = getIntValue(config, "minimumtocook", 3);
      if (minimumToCook != 3) {
         handlerParams.put("minimumToCook", minimumToCook);
      }

      String requiredTag = getStringValue(config, "requiredtag");
      if (requiredTag != null) {
         if (!handlerParams.containsKey("buildingTag")) {
            handlerParams.put("buildingTag", requiredTag);
         } else {
            handlerParams.put("requiredTag", requiredTag);
         }
      }

      json.put("handlerParams", handlerParams);
      addSentenceAndLabelKeys(config, json);
      addHeldItems(config, json, culture, items);
      return json;
   }

   static Map<String, Object> buildSaplingPlantingJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "sapling_planting");
      int priority = getIntValue(config, "priority", 2000);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 48);
      json.put("batchRadius", 8);
      json.put("maxActionsPerTask", 5);
      json.put("actionCooldown", 20);
      json.put("stuckTimeout", 4000);
      json.put("arrivalRange", 5);
      json.put("walkSpeed", 0.6);
      int maxInBuilding = getIntValue(config, "maxsimultaneousinbuilding", -1);
      if (maxInBuilding > 0) {
         json.put("maxSimultaneousInBuilding", maxInBuilding);
      }

      json.put("minimumHour", 0);
      json.put("maximumHour", 12500);
      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String buildingTag = getStringValue(config, "buildingtag");
      if (buildingTag != null) {
         handlerParams.put("buildingTag", buildingTag);
      }

      List<String> heldItems = config.getOrDefault("helditems", List.of());
      if (!heldItems.isEmpty()) {
         String mapped = items.resolve(culture, heldItems.getFirst().trim()).orElse(null);
         if (mapped != null) {
            handlerParams.put("sapling", mapped);
         }
      }

      json.put("handlerParams", handlerParams);
      addSentenceAndLabelKeys(config, json);
      return json;
   }

   static Map<String, Object> buildFruitHarvestingJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "fruit_harvesting");
      int priority = getIntValue(config, "priority", 80);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 32);
      json.put("batchRadius", 8);
      json.put("maxActionsPerTask", 16);
      int durationMs = getIntValue(config, "duration", 4000);
      json.put("actionCooldown", Math.max(10, durationMs / 50));
      json.put("stuckTimeout", 4000);
      json.put("arrivalRange", 8);
      json.put("walkSpeed", 0.6);
      parseLimitField(config, "buildinglimit", culture, items).ifPresent(lim -> json.put("buildingLimit", lim));
      parseLimitField(config, "villagelimit", culture, items).ifPresent(lim -> json.put("villageLimit", lim));
      int maxInBuilding = getIntValue(config, "maxsimultaneousinbuilding", -1);
      if (maxInBuilding > 0) {
         json.put("maxSimultaneousInBuilding", maxInBuilding);
      }

      json.put("minimumHour", -1);
      json.put("maximumHour", 12500);
      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String gatherBlockState = getStringValue(config, "gatherblockstate");
      if (gatherBlockState != null) {
         String[] bsParts = gatherBlockState.split(";", 2);
         String modernBlock = mapLegacyBlockStateLine(gatherBlockState);
         handlerParams.put("targetBlock", modernBlock);
         if (bsParts.length > 1) {
            String[] props = bsParts[1].split(",");

            for (String prop : props) {
               String[] kv = prop.split("=", 2);
               if (kv.length == 2 && "age".equals(kv[0].trim())) {
                  handlerParams.put("ageProperty", "age");
                  handlerParams.put("ripeAge", Integer.parseInt(kv[1].trim()));
               }
            }
         }
      }

      String resultingBlockState = getStringValue(config, "resultingblockstate");
      if (resultingBlockState != null) {
         String[] bsParts = resultingBlockState.split(";", 2);
         if (bsParts.length > 1) {
            String[] props = bsParts[1].split(",");

            for (String prop : props) {
               String[] kv = prop.split("=", 2);
               if (kv.length == 2 && "age".equals(kv[0].trim())) {
                  handlerParams.put("resetAge", Integer.parseInt(kv[1].trim()));
               }
            }
         }
      }

      List<String> harvestItems = config.getOrDefault("harvestitem", List.of());
      if (!harvestItems.isEmpty()) {
         String[] parts = harvestItems.getFirst().split(",", 2);
         String mapped = items.resolve(culture, parts[0].trim()).orElse(null);
         if (mapped != null) {
            handlerParams.put("harvestItem", mapped);
            handlerParams.put("harvestCount", 1);
         }
      }

      String buildingTag = getStringValue(config, "buildingtag");
      if (buildingTag != null) {
         handlerParams.put("buildingTag", buildingTag);
      }

      json.put("handlerParams", handlerParams);
      addSentenceAndLabelKeys(config, json);
      addHeldItems(config, json, culture, items);
      return json;
   }

   static String mapLegacyFruitBlock(String legacyBlockId) {
      return switch (legacyBlockId) {
         case "millenaire:leaves_appletree" -> "millenaire:apple_tree_leaves";
         case "millenaire:leaves_olivetree" -> "millenaire:olive_tree_leaves";
         case "millenaire:leaves_pistachio" -> "millenaire:pistachio_tree_leaves";
         case "minecraft:tallgrass" -> "minecraft:short_grass";
         case "minecraft:hardened_clay" -> "minecraft:terracotta";
         case "minecraft:stained_hardened_clay" -> "minecraft:terracotta";
         case "minecraft:red_flower", "red_flower" -> "minecraft:poppy";
         case "minecraft:yellow_flower", "yellow_flower" -> "minecraft:dandelion";
         default -> legacyBlockId;
      };
   }

   static String mapLegacyBlockStateLine(String stateLine) {
      if (stateLine == null) {
         return null;
      }

      String[] bsParts = stateLine.split(";", 2);
      String blockId = bsParts[0].trim();
      if (bsParts.length < 2) {
         return mapLegacyFruitBlock(blockId);
      }

      String subtype = null;

      for (String prop : bsParts[1].split(",")) {
         String[] kv = prop.split("=", 2);
         if (kv.length == 2 && "type".equals(kv[0].trim())) {
            subtype = kv[1].trim();
            break;
         }
      }

      if (subtype != null) {
         String resolved = switch (blockId + ";type=" + subtype) {
            case "minecraft:tallgrass;type=fern" -> "minecraft:fern";
            case "minecraft:tallgrass;type=tall_grass" -> "minecraft:short_grass";
            case "minecraft:tallgrass;type=dead_bush" -> "minecraft:dead_bush";
            default -> null;
         };
         if (resolved != null) {
            return resolved;
         }
      }

      return mapLegacyFruitBlock(blockId);
   }

   static Map<String, Object> buildTakeFromBuildingJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "take_from_building");
      int priority = getIntValue(config, "priority", 100);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 32);
      json.put("batchRadius", 8);
      json.put("maxActionsPerTask", 1);
      json.put("actionCooldown", 20);
      json.put("stuckTimeout", 4000);
      json.put("walkSpeed", 0.6);
      int maxTotal = getIntValue(config, "maxsimultaneoustotal", -1);
      if (maxTotal > 0) {
         json.put("maxSimultaneousTotal", maxTotal);
      }

      json.put("minimumHour", -1);
      json.put("maximumHour", 12500);
      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String buildingTag = getStringValue(config, "buildingtag");
      if (buildingTag != null) {
         handlerParams.put("buildingTag", buildingTag);
      }

      String collectGood = getStringValue(config, "collect_good");
      if (collectGood != null) {
         String[] parts = collectGood.split(",", 2);
         String mapped = items.resolve(culture, parts[0].trim()).orElse(null);
         if (mapped != null) {
            handlerParams.put("collectGood", mapped);
            if (parts.length > 1) {
               handlerParams.put("maxCollect", Integer.parseInt(parts[1].trim()));
            }
         }
      }

      int minPickup = getIntValue(config, "minimumpickup", -1);
      if (minPickup > 0) {
         handlerParams.put("minimumPickup", minPickup);
      }

      json.put("handlerParams", handlerParams);
      addSentenceAndLabelKeys(config, json);
      return json;
   }

   static void addSentenceAndLabelKeys(Map<String, List<String>> config, Map<String, Object> json) {
      String sentenceKey = getStringValue(config, "sentencekey");
      if (sentenceKey != null) {
         json.put("sentenceKey", sentenceKey);
      }

      String labelKey = getStringValue(config, "labelkey");
      if (labelKey != null) {
         json.put("labelKey", labelKey);
      }
   }

   static List<String> parseFlowerBlockstates(Map<String, List<String>> config, String key) {
      List<String> result = new ArrayList<>();

      for (String entry : config.getOrDefault(key, List.of())) {
         String modernBlock = LEGACY_FLOWER_MAP.get(entry.trim());
         if (modernBlock != null) {
            result.add(modernBlock);
         } else {
            System.out.println("    [WARN] Unknown flower blockstate: " + entry);
         }
      }

      return result;
   }

   static void addHeldItems(Map<String, List<String>> config, Map<String, Object> json, String culture, ItemIdMapper items) {
      List<String> allHeld = new ArrayList<>();

      for (String raw : config.getOrDefault("helditems", List.of())) {
         for (String item : raw.split(",")) {
            items.resolve(culture, item.trim()).ifPresent(mapped -> {
               if (!allHeld.contains(mapped)) {
                  allHeld.add(mapped);
               }
            });
         }
      }

      for (String raw : config.getOrDefault("helditemsoffhand", List.of())) {
         for (String item : raw.split(",")) {
            items.resolve(culture, item.trim()).ifPresent(mapped -> {
               if (!allHeld.contains(mapped)) {
                  allHeld.add(mapped);
               }
            });
         }
      }

      if (!allHeld.isEmpty()) {
         json.put("heldItems", allHeld);
      }
   }

   static void addLeisureFlag(Map<String, List<String>> config, Map<String, Object> json) {
      String leasure = getStringValue(config, "leasure");
      if ("true".equalsIgnoreCase(leasure)) {
         json.put("leisure", true);
      }
   }

   public static Map<String, Object> buildGatheringTypeJson(Map<String, List<String>> config, String culture, ItemIdMapper items) {
      List<Map<String, Object>> inputs = new ArrayList<>();
      List<Map<String, Object>> outputs = new ArrayList<>();
      boolean hasUnmapped = false;

      for (String inputLine : config.getOrDefault("input", List.of())) {
         String[] parts = inputLine.split(",", 2);
         String legacyItem = parts[0].trim();
         int count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
         String modernId = items.resolve(culture, legacyItem).orElse(null);
         if ("wood".equals(legacyItem)) {
            modernId = "#minecraft:logs";
         }

         if (modernId == null) {
            System.out.println("    [WARN] Input item not mapped: " + legacyItem);
            hasUnmapped = true;
         } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("item", modernId);
            entry.put("count", count);
            inputs.add(entry);
         }
      }

      for (String outputLine : config.getOrDefault("output", List.of())) {
         String[] parts = outputLine.split(",", 2);
         String legacyItem = parts[0].trim();
         int count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
         String modernId = items.resolve(culture, legacyItem).orElse(null);
         if (modernId == null) {
            System.out.println("    [WARN] Output item not mapped: " + legacyItem);
            hasUnmapped = true;
         } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("item", modernId);
            entry.put("count", count);
            outputs.add(entry);
         }
      }

      if (outputs.isEmpty()) {
         return null;
      }

      Map<String, Object> json = new LinkedHashMap<>();
      json.put("handler", "crafting");
      int priority = getIntValue(config, "priority", 50);
      json.put("priority", priority);
      if (config.containsKey("priorityrandom")) {
         json.put("priorityRandom", getIntValue(config, "priorityrandom", 10));
      }

      json.put("scanRadius", 32);
      json.put("batchRadius", 8);
      json.put("maxActionsPerTask", 5);
      int durationMs = getIntValue(config, "duration", 5000);
      json.put("actionCooldown", durationMs / 50);
      json.put("stuckTimeout", 4000);
      json.put("walkSpeed", 0.6);
      parseLimitField(config, "townhalllimit", culture, items).ifPresent(lim -> json.put("townhallLimit", lim));
      parseLimitField(config, "buildinglimit", culture, items).ifPresent(lim -> json.put("buildingLimit", lim));
      parseLimitField(config, "villagelimit", culture, items).ifPresent(lim -> json.put("villageLimit", lim));
      int maxInBuilding = getIntValue(config, "maxsimultaneousinbuilding", -1);
      if (maxInBuilding > 0) {
         json.put("maxSimultaneousInBuilding", maxInBuilding);
      }

      int maxTotal = getIntValue(config, "maxsimultaneoustotal", -1);
      if (maxTotal > 0) {
         json.put("maxSimultaneousTotal", maxTotal);
      }

      json.put("minimumHour", -1);
      json.put("maximumHour", 12500);
      int reoccur = getIntValue(config, "reoccurdelay", -1);
      if (reoccur > 0) {
         json.put("reoccurDelay", reoccur / 50);
      }

      Map<String, Object> handlerParams = new LinkedHashMap<>();
      String buildingTag = getStringValue(config, "buildingtag");
      if (buildingTag != null) {
         handlerParams.put("buildingTag", buildingTag);
      }

      String requiredTag = getStringValue(config, "requiredtag");
      if (requiredTag != null) {
         handlerParams.put("requiredTag", requiredTag);
      }

      if (!inputs.isEmpty()) {
         handlerParams.put("inputs", inputs);
      }

      handlerParams.put("outputs", outputs);
      json.put("handlerParams", handlerParams);
      addHeldItems(config, json, culture, items);
      String sentenceKey = getStringValue(config, "sentencekey");
      if (sentenceKey != null) {
         json.put("sentenceKey", sentenceKey);
      }

      String labelKey = getStringValue(config, "labelkey");
      if (labelKey != null) {
         json.put("labelKey", labelKey);
      }

      String sound = getStringValue(config, "sound");
      if (sound != null) {
         json.put("sound", sound);
      }

      String goalTag = getStringValue(config, "tag");
      if (goalTag != null) {
         json.put("tag", goalTag);
      }

      return json;
   }

   static int getIntValue(Map<String, List<String>> config, String key, int defaultValue) {
      List<String> values = config.get(key);
      if (values != null && !values.isEmpty()) {
         try {
            return Integer.parseInt(values.getFirst().trim());
         } catch (NumberFormatException e) {
            return defaultValue;
         }
      } else {
         return defaultValue;
      }
   }

   static String getStringValue(Map<String, List<String>> config, String key) {
      List<String> values = config.get(key);
      return values != null && !values.isEmpty() ? values.getFirst().trim() : null;
   }

   static Optional<Map<String, Integer>> parseLimitField(Map<String, List<String>> config, String key, String culture, ItemIdMapper items) {
      List<String> values = config.get(key);
      if (values != null && !values.isEmpty()) {
         Map<String, Integer> limits = new LinkedHashMap<>();

         for (String value : values) {
            String[] parts = value.split(",", 2);
            String legacyItem = parts[0].trim();
            int count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 64;
            items.resolve(culture, legacyItem).ifPresent(id -> limits.put(id, count));
         }

         return limits.isEmpty() ? Optional.empty() : Optional.of(limits);
      } else {
         return Optional.empty();
      }
   }

   static {
      Map<String, String> m = new HashMap<>();
      m.put("cobblestone", "cobblestone");
      m.put("stone", "stone");
      m.put("glass", "glass");
      m.put("sand", "sand");
      m.put("iron", "iron_ingot");
      m.put("gold", "gold_ingot");
      m.put("cauldron", "cauldron");
      m.put("bookshelves", "bookshelf");
      m.put("banner_white", "banner_white");
      m.put("wool_white", "wool_white");
      m.put("wool_gray", "wool_gray");
      m.put("wool_lightgray", "wool_light_gray");
      m.put("wool_black", "wool_black");
      m.put("wool_brown", "wool_brown");
      m.put("wool_blue", "wool_blue");
      m.put("wool_red", "wool_red");
      m.put("wool_pink", "wool_pink");
      m.put("seeds", "seeds");
      m.put("wheat", "wheat");
      m.put("carrot", "carrot");
      m.put("bread", "bread");
      m.put("egg", "egg");
      m.put("sugar", "sugar");
      m.put("cake", "cake");
      m.put("ciderapple", "cider_apple");
      m.put("leather", "leather");
      m.put("feather", "feather");
      m.put("bone", "bone");
      m.put("beefraw", "beef_raw");
      m.put("beefcooked", "beef_cooked");
      m.put("porkchops", "porkchop_raw");
      m.put("porkchopscooked", "porkchop_cooked");
      m.put("chickenmeat", "chicken_raw");
      m.put("chickenmeatcooked", "chicken_cooked");
      m.put("dye_white", "dye_white");
      m.put("dye_red", "dye_red");
      m.put("dye_yellow", "dye_yellow");
      m.put("dye_lightblue", "dye_light_blue");
      m.put("carpet_white", "carpet_white");
      m.put("carpet_red", "carpet_red");
      m.put("carpet_yellow", "carpet_yellow");
      m.put("carpet_blue", "carpet_blue");
      m.put("bow", "bow");
      m.put("arrow", "arrow");
      m.put("bottle", "bottle");
      m.put("cider", "cider");
      m.put("calva", "calva");
      m.put("tripes", "tripes");
      m.put("boudin", "boudin");
      m.put("timberframeplain", "timber_frame_plain");
      m.put("timberframecross", "timber_frame_cross");
      m.put("normanSword", "norman_sword");
      m.put("normanBroadsword", "norman_sword");
      m.put("normanPickaxe", "norman_pickaxe");
      m.put("normanAxe", "norman_axe");
      m.put("normanShovel", "norman_shovel");
      m.put("normanHoe", "norman_hoe");
      m.put("normanHelmet", "norman_helmet");
      m.put("normanplate", "norman_chestplate");
      m.put("normanLegs", "norman_leggings");
      m.put("normanBoots", "norman_boots");
      m.put("parchment_normanvillagers", "parchment_normanvillagers");
      m.put("parchment_normanbuildings", "parchment_normanbuildings");
      m.put("parchment_normanitems", "parchment_normanitems");
      m.put("parchment_normanfull", "parchment_normanfull");
      m.put("turmeric", "turmeric");
      m.put("rasgulla", "rasgulla");
      m.put("chickencurry", "chickencurry");
      m.put("vegcurry", "vegcurry");
      m.put("rice", "rice");
      m.put("cotton", "cotton");
      m.put("brickmould", "brick_mould");
      m.put("paintbucketwhite", "paint_bucket_white");
      m.put("decoratedbrickwhite", "decorated_brick_white");
      m.put("paintedbrickwhite", "painted_brick_white");
      m.put("indianstatue", "indian_statue");
      m.put("woodenbarsindian", "wooden_bars_indian");
      m.put("bed_charpoy", "charpoy");
      m.put("sandstone_carved", "sandstone_carved");
      m.put("red_sandstone_carved", "red_sandstone_carved");
      m.put("ochre_sandstone_carved", "ochre_sandstone_carved");
      m.put("sandstone", "sandstone");
      m.put("redsandstone", "red_sandstone");
      m.put("diamond", "diamond");
      m.put("dye_blue", "dye_blue");
      m.put("cactus", "cactus");
      m.put("fishcooked", "cooked_cod");
      m.put("cooked_mutton", "cooked_mutton");
      m.put("cookie", "cookie");
      m.put("mudbrick", "mud_brick");
      m.put("thatch", "thatch");
      m.put("woodenbars", "wooden_bars");
      m.put("woodenbarsrosette", "wooden_bars_rosette");
      m.put("summoningwand", "summoning_wand");
      m.put("sugarcane", "sugar_cane");
      m.put("maize", "maize");
      m.put("masa", "masa");
      m.put("wah", "wah");
      m.put("cacauhaa", "cacauhaa");
      m.put("obsidianflake", "obsidian_flake");
      m.put("mayanmace", "mayan_mace");
      m.put("mayanpickaxe", "mayan_pickaxe");
      m.put("mayanaxe", "mayan_axe");
      m.put("mayanshovel", "mayan_shovel");
      m.put("mayanhoe", "mayan_hoe");
      m.put("mayanstatue", "mayan_statue");
      m.put("mayangold", "mayan_gold_block");
      m.put("maypattern", "mayan_pattern");
      m.put("maypattern1", "mayan_pattern_1");
      m.put("maypattern2", "mayan_pattern_2");
      m.put("maypattern3", "mayan_pattern_3");
      m.put("maypattern4", "mayan_pattern_4");
      m.put("obsidian", "obsidian");
      m.put("dye_brown", "dye_brown");
      m.put("spidereye", "spider_eye");
      m.put("rottenflesh", "rotten_flesh");
      m.put("grapes", "grapes");
      m.put("winefancy", "winefancy");
      m.put("winebasic", "winebasic");
      m.put("olives", "olives");
      m.put("oliveoil", "oliveoil");
      m.put("feta", "feta");
      m.put("souvlaki", "souvlaki");
      m.put("byzantinemace", "byzantine_mace");
      m.put("byzantinehelmet", "byzantine_helmet");
      m.put("byzantineplate", "byzantine_chestplate");
      m.put("byzantinelegs", "byzantine_leggings");
      m.put("byzantineboots", "byzantine_boots");
      m.put("byzantineaxe", "byzantine_axe");
      m.put("byzantinepickaxe", "byzantine_pickaxe");
      m.put("byzantineshovel", "byzantine_shovel");
      m.put("byzantinehoe", "byzantine_hoe");
      m.put("silk", "silk");
      m.put("clothes_byz_wool", "clothes_byz_wool");
      m.put("clothes_byz_silk", "clothes_byz_silk");
      m.put("byzantine_fresco", "byzantine_fresco");
      m.put("byzpattern", "byzantine_pattern");
      m.put("byzpattern1", "byzantine_pattern_1");
      m.put("byzpattern2", "byzantine_pattern_2");
      m.put("byzantine_tiles", "byzantine_tiles");
      m.put("byzantine_mosaic", "byzantine_mosaic_red");
      m.put("byzantine_mosaic_red", "byzantine_mosaic_red");
      m.put("byzantine_mosaic_blue", "byzantine_mosaic_blue");
      m.put("byzantineiconsmall", "wall_byzantine_icon_small");
      m.put("byzantineiconmedium", "wall_byzantine_icon_medium");
      m.put("byzantineiconlarge", "wall_byzantine_icon_large");
      m.put("sapling_olivetree", "olive_tree_sapling");
      m.put("diorite", "diorite");
      m.put("smooth_diorite", "polished_diorite");
      m.put("ironnugget", "iron_nugget");
      m.put("dye_purple", "dye_purple");
      m.put("mutton", "mutton_raw");
      m.put("muttonraw", "mutton_raw");
      m.put("muttoncooked", "cooked_mutton");
      m.put("quartz", "quartz");
      m.put("sake", "sake");
      m.put("udon", "udon");
      m.put("ikayaki", "ikayaki");
      m.put("japanese_tachi", "japanese_tachi");
      m.put("tachisword", "japanese_tachi");
      m.put("yumibow", "yumibow");
      m.put("paper_wall", "paper_wall");
      m.put("japaneseguardhelmet", "japaneseguardhelmet");
      m.put("japaneseguardplate", "japaneseguardplate");
      m.put("japaneseguardlegs", "japaneseguardlegs");
      m.put("japaneseguardboots", "japaneseguardboots");
      m.put("japanesebluehelmet", "japanesebluehelmet");
      m.put("japaneseblueplate", "japaneseblueplate");
      m.put("japanesebluelegs", "japanesebluelegs");
      m.put("japaneseblueboots", "japaneseblueboots");
      m.put("japaneseredhelmet", "japaneseredhelmet");
      m.put("japaneseredplate", "japaneseredplate");
      m.put("japaneseredlegs", "japaneseredlegs");
      m.put("japaneseredboots", "japaneseredboots");
      m.put("bed_futon", "futon");
      m.put("mudbrick_seljuk_ornamented", "mudbrick_seljuk_ornamented");
      m.put("mudbrick_seljuk_decorated", "mudbrick_seljuk_decorated");
      m.put("yogurt", "yogurt");
      m.put("ayran", "ayran");
      m.put("pistachios", "pistachios");
      m.put("book", "book");
      m.put("pide", "pide");
      m.put("mudbrick_smooth", "mudbrick_smooth");
      m.put("wallcarpetlarge", "wall_carpet_large");
      m.put("wallcarpetmedium", "wall_carpet_medium");
      m.put("wallcarpetsmall", "wall_carpet_small");
      m.put("clothes_seljuk_cotton", "clothes_seljuk_cotton");
      m.put("clothes_seljuk_wool", "clothes_seljuk_wool");
      m.put("stoneaxe", "stone_axe");
      m.put("stonehoe", "stone_hoe");
      m.put("stonepickaxe", "stone_pickaxe");
      m.put("stoneshovel", "stone_shovel");
      LEGACY_TO_GOOD_ID = Collections.unmodifiableMap(m);
      m = new LinkedHashMap<>();
      m.put("wood", "minecraft:oak_log");
      m.put("wood_oak", "minecraft:oak_log");
      m.put("wood_pine", "minecraft:spruce_log");
      m.put("wood_birch", "minecraft:birch_log");
      m.put("wood_jungle", "minecraft:jungle_log");
      m.put("wood_acacia", "minecraft:acacia_log");
      m.put("wood_darkoak", "minecraft:dark_oak_log");
      m.put("wood_any", "#minecraft:logs");
      m.put("planks_pine", "minecraft:spruce_planks");
      m.put("cobblestone", "minecraft:cobblestone");
      m.put("stone", "minecraft:stone");
      m.put("iron", "minecraft:iron_ingot");
      m.put("glass", "minecraft:glass");
      m.put("clay", "minecraft:clay_ball");
      m.put("flint", "minecraft:flint");
      m.put("bone", "minecraft:bone");
      m.put("leather", "minecraft:leather");
      m.put("feather", "minecraft:feather");
      m.put("wheat", "minecraft:wheat");
      m.put("sugarcane", "minecraft:sugar_cane");
      m.put("sugar", "minecraft:sugar");
      m.put("beefraw", "minecraft:beef");
      m.put("porkchops", "minecraft:porkchop");
      m.put("ciderapple", "millenaire:cider_apple");
      m.put("wool_white", "minecraft:white_wool");
      m.put("wool_gray", "minecraft:gray_wool");
      m.put("wool_lightgray", "minecraft:light_gray_wool");
      m.put("wool_black", "minecraft:black_wool");
      m.put("wool_brown", "minecraft:brown_wool");
      m.put("wool_red", "minecraft:red_wool");
      m.put("wool_blue", "minecraft:blue_wool");
      m.put("wool_yellow", "minecraft:yellow_wool");
      m.put("dye_white", "minecraft:white_dye");
      m.put("dye_red", "minecraft:red_dye");
      m.put("dye_yellow", "minecraft:yellow_dye");
      m.put("dye_lightblue", "minecraft:light_blue_dye");
      m.put("paper", "minecraft:paper");
      m.put("book", "minecraft:book");
      m.put("bookandquill", "minecraft:writable_book");
      m.put("bread", "minecraft:bread");
      m.put("cake", "minecraft:cake");
      m.put("bricks", "minecraft:bricks");
      m.put("bookshelves", "minecraft:bookshelf");
      m.put("arrow", "minecraft:arrow");
      m.put("bow", "minecraft:bow");
      m.put("painting", "minecraft:painting");
      m.put("bottle", "minecraft:glass_bottle");
      m.put("cauldron", "minecraft:cauldron");
      m.put("woodsword", "minecraft:wooden_sword");
      m.put("woodaxe", "minecraft:wooden_axe");
      m.put("woodpickaxe", "minecraft:wooden_pickaxe");
      m.put("woodshovel", "minecraft:wooden_shovel");
      m.put("woodhoe", "minecraft:wooden_hoe");
      m.put("stoneaxe", "minecraft:stone_axe");
      m.put("stonepickaxe", "minecraft:stone_pickaxe");
      m.put("stoneshovel", "minecraft:stone_shovel");
      m.put("stonehoe", "minecraft:stone_hoe");
      m.put("stonesword", "minecraft:stone_sword");
      m.put("steelaxe", "minecraft:iron_axe");
      m.put("steelpickaxe", "minecraft:iron_pickaxe");
      m.put("steelshovel", "minecraft:iron_shovel");
      m.put("steelhoe", "minecraft:iron_hoe");
      m.put("steelsword", "minecraft:iron_sword");
      m.put("leatherhelmet", "minecraft:leather_helmet");
      m.put("leatherchest", "minecraft:leather_chestplate");
      m.put("leatherlegs", "minecraft:leather_leggings");
      m.put("leatherboots", "minecraft:leather_boots");
      m.put("steelhelmet", "minecraft:iron_helmet");
      m.put("steelchest", "minecraft:iron_chestplate");
      m.put("steellegs", "minecraft:iron_leggings");
      m.put("steelboots", "minecraft:iron_boots");
      m.put("glass_pane_white", "minecraft:white_stained_glass_pane");
      m.put("glass_pane_black", "minecraft:black_stained_glass_pane");
      m.put("glass_pane_blue", "minecraft:blue_stained_glass_pane");
      m.put("glass_pane_brown", "minecraft:brown_stained_glass_pane");
      m.put("glass_pane_cyan", "minecraft:cyan_stained_glass_pane");
      m.put("glass_pane_gray", "minecraft:gray_stained_glass_pane");
      m.put("glass_pane_green", "minecraft:green_stained_glass_pane");
      m.put("glass_pane_light_blue", "minecraft:light_blue_stained_glass_pane");
      m.put("glass_pane_light_gray", "minecraft:light_gray_stained_glass_pane");
      m.put("glass_pane_lime", "minecraft:lime_stained_glass_pane");
      m.put("glass_pane_magenta", "minecraft:magenta_stained_glass_pane");
      m.put("glass_pane_orange", "minecraft:orange_stained_glass_pane");
      m.put("glass_pane_pink", "minecraft:pink_stained_glass_pane");
      m.put("glass_pane_purple", "minecraft:purple_stained_glass_pane");
      m.put("glass_pane_red", "minecraft:red_stained_glass_pane");
      m.put("glass_pane_yellow", "minecraft:yellow_stained_glass_pane");
      m.put("carpet_white", "minecraft:white_carpet");
      m.put("carpet_red", "minecraft:red_carpet");
      m.put("carpet_blue", "minecraft:blue_carpet");
      m.put("carpet_yellow", "minecraft:yellow_carpet");
      m.put("timberframeplain", "millenaire:timber_frame_plain");
      m.put("timberframecross", "millenaire:timber_frame_cross");
      m.put("normanhelmet", "millenaire:norman_helmet");
      m.put("normanplate", "millenaire:norman_chestplate");
      m.put("normanlegs", "millenaire:norman_leggings");
      m.put("normanboots", "millenaire:norman_boots");
      m.put("normansword", "millenaire:norman_sword");
      m.put("normanbroadsword", "millenaire:norman_sword");
      m.put("normanaxe", "millenaire:norman_axe");
      m.put("normanpickaxe", "millenaire:norman_pickaxe");
      m.put("normanshovel", "millenaire:norman_shovel");
      m.put("normanhoe", "millenaire:norman_hoe");
      m.put("cider", "millenaire:cider");
      m.put("calva", "millenaire:calva");
      m.put("boudin", "millenaire:boudin");
      m.put("tripes", "millenaire:tripes");
      m.put("ciderapple", "millenaire:cider_apple");
      m.put("tapestry", "millenaire:wall_tapestry");
      m.put("bed_straw", "millenaire:straw_bed");
      m.put("banner_white", "minecraft:white_banner");
      m.put("ulu", "millenaire:ulu");
      m.put("rosette", "millenaire:rosette");
      m.put("pathdirt", "millenaire:path_dirt");
      m.put("pathgravel", "millenaire:path_gravel");
      m.put("pathgravelslabs", "millenaire:path_gravel_slab");
      m.put("pathochretiles", "millenaire:path_ochre_tiles");
      m.put("pathsandstone", "millenaire:path_sandstone");
      m.put("pathslabs", "millenaire:path_slabs");
      m.put("stained_glass_white", "millenaire:stained_glass_white");
      m.put("stained_glass_green_blue", "millenaire:stained_glass_green_blue");
      m.put("stained_glass_red_blue", "millenaire:stained_glass_red_blue");
      m.put("stained_glass_yellow", "millenaire:stained_glass_yellow");
      m.put("stained_glass_yellow_red", "millenaire:stained_glass_yellow_red");
      m.put("dirtwall", "millenaire:dirt_wall");
      m.put("denier", "millenaire:denier");
      m.put("denierargent", "millenaire:denier_argent");
      m.put("denierdor", "millenaire:denier_or");
      m.put("coal", "minecraft:coal");
      m.put("silk", "millenaire:silk");
      m.put("cotton", "millenaire:cotton");
      m.put("rice", "millenaire:rice");
      m.put("seeds", "minecraft:wheat_seeds");
      m.put("carrot", "minecraft:carrot");
      m.put("sand", "minecraft:sand");
      m.put("gravel", "minecraft:gravel");
      m.put("egg", "minecraft:egg");
      m.put("chickenmeat", "minecraft:chicken");
      m.put("chickenmeatcooked", "minecraft:cooked_chicken");
      m.put("beefcooked", "minecraft:cooked_beef");
      m.put("porkchopscooked", "minecraft:cooked_porkchop");
      m.put("wool_pink", "minecraft:pink_wool");
      m.put("sapling", "minecraft:oak_sapling");
      m.put("sapling_pine", "minecraft:spruce_sapling");
      m.put("sapling_birch", "minecraft:birch_sapling");
      m.put("sapling_jungle", "minecraft:jungle_sapling");
      m.put("sapling_acacia", "minecraft:acacia_sapling");
      m.put("sapling_darkoak", "minecraft:dark_oak_sapling");
      m.put("sapling_appletree", "millenaire:apple_tree_sapling");
      m.put("apple", "minecraft:apple");
      m.put("muttoncooked", "minecraft:cooked_mutton");
      m.put("muttonraw", "minecraft:mutton");
      m.put("purse", "millenaire:purse");
      m.put("blueflower", "minecraft:cornflower");
      m.put("pinkflower", "minecraft:pink_tulip");
      m.put("redflower", "minecraft:poppy");
      m.put("netherwart", "minecraft:nether_wart");
      m.put("dirt", "minecraft:dirt");
      m.put("turmeric", "millenaire:turmeric");
      m.put("rasgulla", "millenaire:rasgulla");
      m.put("chickencurry", "millenaire:chickencurry");
      m.put("vegcurry", "millenaire:vegcurry");
      m.put("rice", "millenaire:rice");
      m.put("cotton", "millenaire:cotton");
      m.put("brickmould", "millenaire:brick_mould");
      m.put("paintbucketwhite", "millenaire:paint_bucket_white");
      m.put("decoratedbrickwhite", "millenaire:decorated_brick_white");
      m.put("paintedbrickwhite", "millenaire:painted_brick_white");
      m.put("indianstatue", "millenaire:wall_indian_statue");
      m.put("woodenbarsindian", "millenaire:wooden_bars_indian");
      m.put("bed_charpoy", "millenaire:charpoy");
      m.put("sandstone_carved", "millenaire:sandstone_carved");
      m.put("red_sandstone_carved", "millenaire:red_sandstone_carved");
      m.put("ochre_sandstone_carved", "millenaire:ochre_sandstone_carved");
      m.put("gold", "minecraft:gold_ingot");
      m.put("sandstone", "minecraft:sandstone");
      m.put("redsandstone", "minecraft:red_sandstone");
      m.put("diamond", "minecraft:diamond");
      m.put("dye_blue", "minecraft:lapis_lazuli");
      m.put("cactus", "minecraft:cactus");
      m.put("fishcooked", "minecraft:cooked_cod");
      m.put("cooked_mutton", "minecraft:cooked_mutton");
      m.put("cookie", "minecraft:cookie");
      m.put("stonepickaxe", "minecraft:stone_pickaxe");
      m.put("stoneaxe", "minecraft:stone_axe");
      m.put("stoneshovel", "minecraft:stone_shovel");
      m.put("stonehoe", "minecraft:stone_hoe");
      m.put("diamondpickaxe", "minecraft:diamond_pickaxe");
      m.put("diamondaxe", "minecraft:diamond_axe");
      m.put("diamondshovel", "minecraft:diamond_shovel");
      m.put("diamondhoe", "minecraft:diamond_hoe");
      m.put("diamondsword", "minecraft:diamond_sword");
      m.put("diamondhelmet", "minecraft:diamond_helmet");
      m.put("diamondchest", "minecraft:diamond_chestplate");
      m.put("diamondlegs", "minecraft:diamond_leggings");
      m.put("diamondboots", "minecraft:diamond_boots");
      m.put("mudbrick", "millenaire:mud_brick");
      m.put("thatch", "millenaire:thatch");
      m.put("woodenbars", "millenaire:wooden_bars");
      m.put("woodenbarsrosette", "millenaire:wooden_bars_rosette");
      m.put("summoningwand", "millenaire:summoning_wand");
      m.put("maize", "millenaire:maize");
      m.put("masa", "millenaire:masa");
      m.put("wah", "millenaire:wah");
      m.put("cacauhaa", "millenaire:cacauhaa");
      m.put("obsidianflake", "millenaire:obsidian_flake");
      m.put("mayanmace", "millenaire:mayan_mace");
      m.put("mayanpickaxe", "millenaire:mayan_pickaxe");
      m.put("mayanaxe", "millenaire:mayan_axe");
      m.put("mayanshovel", "millenaire:mayan_shovel");
      m.put("mayanhoe", "millenaire:mayan_hoe");
      m.put("mayanstatue", "millenaire:mayan_statue");
      m.put("mayangold", "millenaire:mayan_gold_block");
      m.put("maypattern", "millenaire:mayan_pattern");
      m.put("maypattern1", "millenaire:mayan_pattern_1");
      m.put("maypattern2", "millenaire:mayan_pattern_2");
      m.put("maypattern3", "millenaire:mayan_pattern_3");
      m.put("maypattern4", "millenaire:mayan_pattern_4");
      m.put("obsidian", "minecraft:obsidian");
      m.put("dye_brown", "minecraft:cocoa_beans");
      m.put("spidereye", "minecraft:spider_eye");
      m.put("rottenflesh", "minecraft:rotten_flesh");
      m.put("pumpkin", "minecraft:pumpkin");
      m.put("ghasttear", "minecraft:ghast_tear");
      m.put("goldsword", "minecraft:golden_sword");
      m.put("sake", "millenaire:sake");
      m.put("udon", "millenaire:udon");
      m.put("ikayaki", "millenaire:ikayaki");
      m.put("japanese_tachi", "millenaire:japanese_tachi");
      m.put("tachisword", "millenaire:japanese_tachi");
      m.put("yumibow", "millenaire:yumibow");
      m.put("paper_wall", "millenaire:paper_wall");
      m.put("jappattern", "millenaire:jappattern");
      m.put("jappattern1", "millenaire:jappattern1");
      m.put("jappattern2", "millenaire:jappattern2");
      m.put("jappattern3", "millenaire:jappattern3");
      m.put("jappattern4", "millenaire:jappattern4");
      m.put("japaneseguardhelmet", "millenaire:japaneseguardhelmet");
      m.put("japaneseguardplate", "millenaire:japaneseguardplate");
      m.put("japaneseguardlegs", "millenaire:japaneseguardlegs");
      m.put("japaneseguardboots", "millenaire:japaneseguardboots");
      m.put("japanesebluehelmet", "millenaire:japanesebluehelmet");
      m.put("japaneseblueplate", "millenaire:japaneseblueplate");
      m.put("japanesebluelegs", "millenaire:japanesebluelegs");
      m.put("japaneseblueboots", "millenaire:japaneseblueboots");
      m.put("japaneseredhelmet", "millenaire:japaneseredhelmet");
      m.put("japaneseredplate", "millenaire:japaneseredplate");
      m.put("japaneseredlegs", "millenaire:japaneseredlegs");
      m.put("japaneseredboots", "millenaire:japaneseredboots");
      m.put("bed_futon", "millenaire:futon");
      m.put("japanese_tiles", "millenaire:japanese_tiles");
      m.put("japanese_tiles_slab", "millenaire:japanese_tiles_slab");
      m.put("denieror", "millenaire:denier_or");
      m.put("grapes", "millenaire:grapes");
      m.put("winefancy", "millenaire:winefancy");
      m.put("winebasic", "millenaire:winebasic");
      m.put("olives", "millenaire:olives");
      m.put("oliveoil", "millenaire:oliveoil");
      m.put("feta", "millenaire:feta");
      m.put("souvlaki", "millenaire:souvlaki");
      m.put("byzantinemace", "millenaire:byzantine_mace");
      m.put("byzantinehelmet", "millenaire:byzantine_helmet");
      m.put("byzantineplate", "millenaire:byzantine_chestplate");
      m.put("byzantinelegs", "millenaire:byzantine_leggings");
      m.put("byzantineboots", "millenaire:byzantine_boots");
      m.put("byzantineaxe", "millenaire:byzantine_axe");
      m.put("byzantinepickaxe", "millenaire:byzantine_pickaxe");
      m.put("byzantineshovel", "millenaire:byzantine_shovel");
      m.put("byzantinehoe", "millenaire:byzantine_hoe");
      m.put("clothes_byz_wool", "millenaire:clothes_byz_wool");
      m.put("clothes_byz_silk", "millenaire:clothes_byz_silk");
      m.put("byzantine_fresco", "millenaire:byzantine_fresco");
      m.put("byzpattern", "millenaire:byzantine_pattern");
      m.put("byzpattern1", "millenaire:byzantine_pattern_1");
      m.put("byzpattern2", "millenaire:byzantine_pattern_2");
      m.put("byzantine_tiles", "millenaire:byzantine_tiles");
      m.put("byzantine_tiles_slab", "millenaire:byzantine_tiles_slab");
      m.put("byzantine_stone_tiles", "millenaire:byzantine_stone_tiles");
      m.put("byzantine_sandstone_tiles", "millenaire:byzantine_sandstone_tiles");
      m.put("byzantine_stone_ornament", "millenaire:byzantine_stone_ornament");
      m.put("byzantine_sandstone_ornament", "millenaire:byzantine_sandstone_ornament");
      m.put("byzantine_mosaic", "millenaire:byzantine_mosaic_red");
      m.put("byzantine_mosaic_red", "millenaire:byzantine_mosaic_red");
      m.put("byzantine_mosaic_blue", "millenaire:byzantine_mosaic_blue");
      m.put("byzantineiconsmall", "millenaire:wall_byzantine_icon_small");
      m.put("byzantineiconmedium", "millenaire:wall_byzantine_icon_medium");
      m.put("byzantineiconlarge", "millenaire:wall_byzantine_icon_large");
      m.put("sapling_olivetree", "millenaire:olive_tree_sapling");
      m.put("leaves_olivetree", "millenaire:olive_tree_leaves");
      m.put("diorite", "minecraft:diorite");
      m.put("smooth_diorite", "minecraft:polished_diorite");
      m.put("ironnugget", "minecraft:iron_nugget");
      m.put("dye_purple", "minecraft:purple_dye");
      m.put("dye_yellow", "minecraft:yellow_dye");
      m.put("mutton", "minecraft:mutton");
      m.put("muttonraw", "minecraft:mutton");
      m.put("muttoncooked", "minecraft:cooked_mutton");
      m.put("cooked_mutton", "minecraft:cooked_mutton");
      m.put("quartz", "minecraft:quartz_block");
      m.put("brick", "minecraft:brick");
      m.put("fishraw", "minecraft:cod");
      m.put("charcoal", "minecraft:charcoal");
      m.put("byzantine_mosaic", "millenaire:byzantine_mosaic_red");
      m.put("byzantine_tiles", "millenaire:byzantine_tiles");
      m.put("winefancy", "millenaire:winefancy");
      m.put("winebasic", "millenaire:winebasic");
      m.put("wool_cyan", "minecraft:cyan_wool");
      m.put("wool_green", "minecraft:green_wool");
      m.put("wool_lightblue", "minecraft:light_blue_wool");
      m.put("wool_orange", "minecraft:orange_wool");
      m.put("wool_magenta", "minecraft:magenta_wool");
      m.put("wool_purple", "minecraft:purple_wool");
      m.put("wool_limegreen", "minecraft:lime_wool");
      m.put("dye_green", "minecraft:green_dye");
      m.put("dye_orange", "minecraft:orange_dye");
      m.put("dye_magenta", "minecraft:magenta_dye");
      m.put("dye_pink", "minecraft:pink_dye");
      m.put("dye_gray", "minecraft:gray_dye");
      m.put("dye_lightgray", "minecraft:light_gray_dye");
      m.put("dye_cyan", "minecraft:cyan_dye");
      m.put("dye_black", "minecraft:black_dye");
      m.put("carpet_cyan", "minecraft:cyan_carpet");
      m.put("carpet_green", "minecraft:green_carpet");
      m.put("carpet_lightblue", "minecraft:light_blue_carpet");
      m.put("hay", "minecraft:hay_block");
      m.put("goldnugget", "minecraft:gold_nugget");
      m.put("akwardpotion", "minecraft:potion");
      m.put("yellowFlower", "minecraft:dandelion");
      m.put("inuitmeatystew", "millenaire:inuitmeatystew");
      m.put("inuitpotatostew", "millenaire:inuitpotatostew");
      m.put("inuitbearstew", "millenaire:inuitbearstew");
      m.put("bearmeat_raw", "millenaire:bearmeat_raw");
      m.put("bearmeat_cooked", "millenaire:bearmeat_cooked");
      m.put("wolfmeat_raw", "millenaire:wolfmeat_raw");
      m.put("wolfmeat_cooked", "millenaire:wolfmeat_cooked");
      m.put("seafood_raw", "millenaire:seafood_raw");
      m.put("seafood_cooked", "millenaire:seafood_cooked");
      m.put("inuitbow", "millenaire:inuitbow");
      m.put("inuittrident", "millenaire:inuittrident");
      m.put("furhelmet", "millenaire:furhelmet");
      m.put("furplate", "millenaire:furplate");
      m.put("furlegs", "millenaire:furlegs");
      m.put("furboots", "millenaire:furboots");
      m.put("tannedhide", "millenaire:tannedhide");
      m.put("hidehanging", "millenaire:wall_hide_hanging");
      m.put("inuitcarving", "millenaire:inuit_carving");
      m.put("snowbrick", "millenaire:snow_brick");
      m.put("icebrick", "millenaire:ice_brick");
      m.put("snowwall", "millenaire:snow_wall");
      m.put("sod_spruce", "millenaire:sod_spruce");
      m.put("sod_birch", "millenaire:sod_birch");
      m.put("fire_pit", "millenaire:fire_pit");
      m.put("rabbit", "minecraft:rabbit");
      m.put("rabbit_hide", "minecraft:rabbit_hide");
      m.put("potato", "minecraft:potato");
      m.put("ice", "minecraft:ice");
      m.put("snowblock", "minecraft:snow_block");
      m.put("coarse_dirt", "minecraft:coarse_dirt");
      m.put("boneblock", "minecraft:bone_block");
      m.put("sod_oak", "millenaire:sod_oak");
      m.put("sod_jungle", "millenaire:sod_jungle");
      m.put("pathsnow", "millenaire:path_snow");
      m.put("snow", "minecraft:snow_block");
      m.put("baked_potato", "minecraft:baked_potato");
      m.put("yogurt", "millenaire:yogurt");
      m.put("ayran", "millenaire:ayran");
      m.put("pistachios", "millenaire:pistachios");
      m.put("helva", "millenaire:helva");
      m.put("lokum", "millenaire:lokum");
      m.put("mudbrick_seljuk_decorated", "millenaire:mud_brick_seljuk_decorated");
      m.put("mudbrick_seljuk_ornamented", "millenaire:mud_brick_seljuk_ornamented");
      m.put("ironore", "minecraft:raw_iron");
      m.put("string", "minecraft:string");
      m.put("seljukboots", "millenaire:seljuk_boots");
      m.put("seljukbow", "millenaire:seljuk_bow");
      m.put("seljuklegs", "millenaire:seljuk_leggings");
      m.put("seljukplate", "millenaire:seljuk_chestplate");
      m.put("seljukturban", "millenaire:seljuk_turban");
      m.put("seljukscimitar", "millenaire:seljuk_scimitar");
      m.put("seljukhelmet", "millenaire:seljuk_helmet");
      m.put("pide", "millenaire:pide");
      m.put("mudbrick_smooth", "millenaire:mud_brick_smooth");
      m.put("wallcarpetlarge", "millenaire:wall_carpet_large");
      m.put("wallcarpetmedium", "millenaire:wall_carpet_medium");
      m.put("wallcarpetsmall", "millenaire:wall_carpet_small");
      m.put("clothes_seljuk_cotton", "millenaire:clothes_seljuk_cotton");
      m.put("clothes_seljuk_wool", "millenaire:clothes_seljuk_wool");
      m.put("sapling_pistachio", "millenaire:pistachio_tree_sapling");
      m.put("pumpkinseeds", "minecraft:pumpkin_seeds");
      m.put("melonseeds", "minecraft:melon_seeds");
      m.put("sod_acacia", "millenaire:sod_acacia");
      m.put("sod_dark_oak", "millenaire:sod_dark_oak");
      m.put("fishing_rod", "minecraft:fishing_rod");
      m.put("enderpearl", "minecraft:ender_pearl");
      m.put("totem_of_undying", "minecraft:totem_of_undying");
      LEGACY_ITEM_ID_MAP = Collections.unmodifiableMap(m);
      m = new LinkedHashMap<>();
      m.put("wheat", "minecraft:wheat");
      m.put("carrot", "minecraft:carrots");
      m.put("carrots", "minecraft:carrots");
      m.put("potato", "minecraft:potatoes");
      m.put("potatoes", "minecraft:potatoes");
      m.put("millenaire:crop_rice", "millenaire:crop_rice");
      m.put("millenaire:crop_cotton", "millenaire:crop_cotton");
      m.put("millenaire:crop_turmeric", "millenaire:crop_turmeric");
      m.put("millenaire:crop_vine", "millenaire:crop_vine");
      m.put("millenaire:crop_maize", "millenaire:crop_maize");
      LEGACY_CROP_BLOCK_MAP = Collections.unmodifiableMap(m);
      m = new LinkedHashMap<>();
      m.put("minecraft:stone", "stone");
      m.put("minecraft:sand", "sand");
      m.put("minecraft:clay", "clay");
      m.put("minecraft:gravel", "gravel");
      m.put("minecraft:sandstone", "sandstone");
      m.put("minecraft:red_sandstone", "red_sandstone");
      m.put("minecraft:snow_layer", "snow");
      m.put("minecraft:ice", "ice");
      m.put("minecraft:diorite", "diorite");
      m.put("minecraft:granite", "granite");
      m.put("minecraft:andesite", "andesite");
      MINING_SOURCE_MAP = Collections.unmodifiableMap(m);
      Map<String, String[]> cookingMap = new LinkedHashMap<>();
      cookingMap.put("beefraw", new String[]{"minecraft:beef", "minecraft:cooked_beef"});
      cookingMap.put("porkchops", new String[]{"minecraft:porkchop", "minecraft:cooked_porkchop"});
      cookingMap.put("chickenmeat", new String[]{"minecraft:chicken", "minecraft:cooked_chicken"});
      cookingMap.put("fishraw", new String[]{"minecraft:cod", "minecraft:cooked_cod"});
      cookingMap.put("muttonraw", new String[]{"minecraft:mutton", "minecraft:cooked_mutton"});
      cookingMap.put("mutton", new String[]{"minecraft:mutton", "minecraft:cooked_mutton"});
      cookingMap.put("ironore", new String[]{"minecraft:raw_iron", "minecraft:iron_ingot"});
      cookingMap.put("cobblestone", new String[]{"minecraft:cobblestone", "minecraft:stone"});
      cookingMap.put("stone", new String[]{"minecraft:cobblestone", "minecraft:stone"});
      cookingMap.put("sand", new String[]{"minecraft:sand", "minecraft:glass"});
      cookingMap.put("clay", new String[]{"minecraft:clay_ball", "minecraft:brick"});
      cookingMap.put("mudbrick", new String[]{"millenaire:mud_brick", "millenaire:painted_brick_white"});
      cookingMap.put("seafood_raw", new String[]{"millenaire:seafood_raw", "millenaire:seafood_cooked"});
      cookingMap.put("potato", new String[]{"minecraft:potato", "minecraft:baked_potato"});
      cookingMap.put("wood_any", new String[]{"minecraft:oak_log", "minecraft:charcoal"});
      cookingMap.put("clayblock", new String[]{"minecraft:clay", "minecraft:terracotta"});
      LEGACY_COOKING_MAP = Collections.unmodifiableMap(cookingMap);
   }
}
