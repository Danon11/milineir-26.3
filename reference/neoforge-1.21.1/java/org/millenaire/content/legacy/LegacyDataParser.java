package org.millenaire.content.legacy;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public final class LegacyDataParser {
   private static final Set<String> LENIENT_FALLBACK_LOGGED = ConcurrentHashMap.newKeySet();
   private static final int MAX_TRACKED_FALLBACKS = 4096;

   private LegacyDataParser() {
   }

   public static void resetLenientFallbackTracking() {
      LENIENT_FALLBACK_LOGGED.clear();
   }

   static List<String> readLinesLenient(Path path) throws IOException {
      try {
         return Files.readAllLines(path, StandardCharsets.UTF_8);
      } catch (MalformedInputException e) {
         String key = path.toAbsolutePath().toString();
         if (LENIENT_FALLBACK_LOGGED.size() < 4096 && LENIENT_FALLBACK_LOGGED.add(key)) {
            System.err.println("[INFO] File " + path + " is not UTF-8, read as ISO-8859-1");
         }

         return Files.readAllLines(path, StandardCharsets.ISO_8859_1);
      }
   }

   public static Map<String, List<String>> parseGatheringTypeTxt(Path txtFile) throws IOException {
      Map<String, List<String>> config = new LinkedHashMap<>();

      for (String line : readLinesLenient(txtFile)) {
         line = line.trim();
         if (!line.isEmpty() && !line.startsWith("//")) {
            int eq = line.indexOf(61);
            if (eq >= 0) {
               String key = line.substring(0, eq).toLowerCase().trim();
               String value = line.substring(eq + 1).trim();
               config.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
         }
      }

      return config;
   }

   public static LegacyDataParser.BuildingMeta parseBuildingTxt(Path txtPath, String category) throws IOException {
      Map<String, List<String>> data = parseKeyValues(txtPath);
      String fileName = txtPath.getFileName().toString();
      String baseName = extractBaseName(fileName);
      int width = getInt(data, "building.width", 10);
      int length = getInt(data, "building.length", 10);
      int orientation = getInt(data, "building.buildingorientation", 1);
      int maxCount = getInt(data, "building.max", 1);
      double minDist = getDouble(data, "building.mindistance", 0.0);
      double maxDist = getDouble(data, "building.maxdistance", 1.0);
      int initialStartLevel = getInt(data, "initial.startlevel", 0);
      int initialPriority = getInt(data, "initial.priority", 100);
      String initialName = getFirst(data, "initial.nativename", baseName);
      List<String> initialTags = getAll(data, "initial.tag");
      List<String> males = getAll(data, "initial.male");
      List<String> females = getAll(data, "initial.female");
      String shop = getFirst(data, "initial.shop", null);
      List<String> startingSubBuildings = getAll(data, "building.startingsubbuilding");
      String icon = getFirst(data, "building.icon", null);
      String fixedOrientation = getFirst(data, "building.fixedorientation", null);
      int areaToClear = getInt(data, "building.areatoclear", 0);
      int areaToClearLengthBefore = getInt(data, "building.areatoclearlengthbefore", -1);
      int areaToClearLengthAfter = getInt(data, "building.areatoclearlengthafter", -1);
      int areaToClearWidthBefore = getInt(data, "building.areatoclearwidthbefore", -1);
      int areaToClearWidthAfter = getInt(data, "building.areatoclearwidthafter", -1);
      int version = getInt(data, "building.version", 1);
      List<String> farFromTags = getAll(data, "building.farfromtag");
      int price = getInt(data, "building.price", 0);
      int reputation = getInt(data, "building.reputation", 0);
      boolean isGift = "true".equalsIgnoreCase(getFirst(data, "building.isgift", null));
      List<String> randomBrickColours = getAll(data, "building.randombrickcolour");
      List<LegacyDataParser.StartingGoodMeta> startingGoods = new ArrayList<>();

      for (String raw : getAll(data, "building.startinggood")) {
         String[] parts = raw.split(",");
         if (parts.length >= 4) {
            try {
               startingGoods.add(
                  new LegacyDataParser.StartingGoodMeta(
                     parts[0].trim(), Double.parseDouble(parts[1].trim()), Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim())
                  )
               );
            } catch (NumberFormatException e) {
               System.err.println("  [WARN] invalid startinggood in " + baseName + ": " + raw);
            }
         }
      }

      boolean showTownHallSigns = !"false".equalsIgnoreCase(getFirst(data, "building.showtownhallsigns", "true"));
      boolean isSubBuilding = "true".equalsIgnoreCase(getFirst(data, "building.issubbuilding", "false"));
      boolean isWallSegment = "true".equalsIgnoreCase(getFirst(data, "building.iswallsegment", "false"));
      boolean isBorderBuilding = "true".equalsIgnoreCase(getFirst(data, "building.isborderbuilding", "false"));
      String initialSigns = getFirst(data, "initial.signs", null);
      int initialPathLevel = getInt(data, "initial.pathlevel", 0);
      int initialPathWidth = getInt(data, "initial.pathwidth", 2);
      int initialPriorityMoveIn = getInt(data, "initial.prioritymovein", 0);
      List<LegacyDataParser.LevelMeta> levels = new ArrayList<>();
      List<String> initialSubs = getAll(data, "initial.subbuilding");
      List<String> initialRequiredTags = getAll(data, "initial.requiredtag");
      List<String> initialAbstractedProd = getAll(data, "initial.abstractedproduction");
      List<String> initialParentTags = getAll(data, "initial.parenttag");
      List<String> initialRequiredParentTags = getAllWithFallback(data, "initial.requiredparenttag", "initial.requiredparenttags");
      List<String> initialClearTags = getAll(data, "initial.cleartag");
      List<String> initialVillageTags = getAll(data, "initial.villagetag");
      levels.add(
         new LegacyDataParser.LevelMeta(
            0,
            initialStartLevel,
            initialPriority,
            initialName,
            initialTags,
            initialSubs,
            initialPathLevel,
            false,
            initialPathWidth,
            initialSigns,
            initialPriorityMoveIn,
            0,
            initialAbstractedProd,
            initialRequiredTags,
            initialParentTags,
            initialRequiredParentTags,
            initialClearTags,
            initialVillageTags
         )
      );
      List<String> allSubBuildings = new ArrayList<>(initialSubs);
      int previousStartLevel = initialStartLevel;
      int maxUpgrade = 0;

      for (String key : data.keySet()) {
         if (key.startsWith("upgrade")) {
            try {
               int n = Integer.parseInt(key.substring(7, key.indexOf(46)));
               maxUpgrade = Math.max(maxUpgrade, n);
            } catch (NumberFormatException | StringIndexOutOfBoundsException var72) {
            }
         }
      }

      for (int i = 1; i <= maxUpgrade; i++) {
         String prefix = "upgrade" + i + ".";
         if (!hasPrefix(data, prefix)) {
            int inheritedPriority = levels.get(i - 1).priority();
            levels.add(
               new LegacyDataParser.LevelMeta(
                  i,
                  previousStartLevel,
                  inheritedPriority,
                  null,
                  List.of(),
                  List.of(),
                  0,
                  false,
                  2,
                  null,
                  0,
                  0,
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of()
               )
            );
         } else {
            int startLevel = getInt(data, prefix + "startlevel", previousStartLevel);
            int previousPriority = levels.get(i - 1).priority();
            int priority = getInt(data, prefix + "priority", previousPriority);
            String name = getFirst(data, prefix + "nativename", null);
            List<String> levelTags = getAll(data, prefix + "tag");
            List<String> subs = getAll(data, prefix + "subbuilding");
            allSubBuildings.addAll(subs);
            int pathLevel = getInt(data, prefix + "pathlevel", 0);
            boolean rebuildPath = "true".equalsIgnoreCase(getFirst(data, prefix + "rebuildpath", "false"));
            int pathWidth = getInt(data, prefix + "pathwidth", 2);
            int extraWalls = getInt(data, prefix + "extrasimultaneouswallconstructions", 0);
            List<String> abstractedProd = getAll(data, prefix + "abstractedproduction");
            List<String> requiredTags = getAll(data, prefix + "requiredtag");
            List<String> parentTags = getAll(data, prefix + "parenttag");
            List<String> requiredParentTags = getAllWithFallback(data, prefix + "requiredparenttag", prefix + "requiredparenttags");
            List<String> clearTags = getAll(data, prefix + "cleartag");
            List<String> villageTags = getAll(data, prefix + "villagetag");
            levels.add(
               new LegacyDataParser.LevelMeta(
                  i,
                  startLevel,
                  priority,
                  name,
                  levelTags,
                  subs,
                  pathLevel,
                  rebuildPath,
                  pathWidth,
                  null,
                  0,
                  extraWalls,
                  abstractedProd,
                  requiredTags,
                  parentTags,
                  requiredParentTags,
                  clearTags,
                  villageTags
               )
            );
            previousStartLevel = startLevel;
         }
      }

      List<String> tags = new ArrayList<>();

      for (LegacyDataParser.LevelMeta lm : levels) {
         for (String t : lm.tags()) {
            if (!tags.contains(t)) {
               tags.add(t);
            }
         }
      }

      return new LegacyDataParser.BuildingMeta(
         baseName,
         category,
         width,
         length,
         orientation,
         maxCount,
         minDist,
         maxDist,
         levels,
         tags,
         males,
         females,
         allSubBuildings,
         startingSubBuildings,
         shop,
         icon,
         fixedOrientation,
         areaToClear,
         areaToClearLengthBefore,
         areaToClearLengthAfter,
         areaToClearWidthBefore,
         areaToClearWidthAfter,
         version,
         farFromTags,
         price,
         reputation,
         isGift,
         randomBrickColours,
         startingGoods,
         showTownHallSigns,
         isSubBuilding,
         isWallSegment,
         isBorderBuilding
      );
   }

   public static List<LegacyDataParser.BuildingWithVariants> scanBuildingDirectory(Path dir, String category) throws IOException {
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      Map<String, Map<String, Path>> txtByBaseAndVariant = new TreeMap<>();
      Map<String, Map<String, List<Path>>> pngsByBaseAndVariant = new TreeMap<>();
      List<Path> allFiles = new ArrayList<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
         for (Path entry : stream) {
            if (Files.isDirectory(entry)) {
               try (DirectoryStream<Path> subStream = Files.newDirectoryStream(entry)) {
                  for (Path subFile : subStream) {
                     if (Files.isRegularFile(subFile)) {
                        allFiles.add(subFile);
                     }
                  }
               }
            } else {
               allFiles.add(entry);
            }
         }
      }

      for (Path file : allFiles) {
         String name = file.getFileName().toString();
         if (name.endsWith(".txt")) {
            String base = extractBaseName(name);
            String variant = extractVariant(name);
            if (variant != null) {
               txtByBaseAndVariant.computeIfAbsent(base, k -> new TreeMap<>()).put(variant, file);
            }
         } else if (name.endsWith(".png")) {
            LegacyDataParser.ParsedPngName parsed = parsePngName(name);
            if (parsed != null) {
               pngsByBaseAndVariant.computeIfAbsent(parsed.baseName, k -> new TreeMap<>()).computeIfAbsent(parsed.variant, k -> new ArrayList<>()).add(file);
            }
         }
      }

      List<LegacyDataParser.BuildingWithVariants> results = new ArrayList<>();

      for (Entry<String, Map<String, Path>> entry : txtByBaseAndVariant.entrySet()) {
         String baseName = entry.getKey();
         Map<String, Path> variantTxts = entry.getValue();

         try {
            Map<String, LegacyDataParser.BuildingMeta> variantMetas = new TreeMap<>();
            LegacyDataParser.BuildingMeta primaryMeta = null;

            for (Entry<String, Path> vtEntry : variantTxts.entrySet()) {
               LegacyDataParser.BuildingMeta vm = parseBuildingTxt(vtEntry.getValue(), category);
               variantMetas.put(vtEntry.getKey(), vm);
               if (primaryMeta == null) {
                  primaryMeta = vm;
               }
            }

            if (primaryMeta != null) {
               Map<String, List<Path>> variants = pngsByBaseAndVariant.getOrDefault(baseName, Map.of());

               for (List<Path> pngs : variants.values()) {
                  pngs.sort(Comparator.comparing(p -> {
                     LegacyDataParser.ParsedPngName pp = parsePngName(p.getFileName().toString());
                     return pp != null ? pp.level : 0;
                  }));
               }

               results.add(new LegacyDataParser.BuildingWithVariants(primaryMeta, variants, variantMetas));
            }
         } catch (Exception e) {
            System.err.println("[WARN] Parsing error for " + baseName + ": " + e.getMessage());
         }
      }

      return results;
   }

   public static LegacyDataParser.VillagerMeta parseVillagerTxt(Path txtPath, String category) throws IOException {
      Map<String, List<String>> data = parseKeyValues(txtPath);
      String id = txtPath.getFileName().toString().replace(".txt", "");
      String nativeName = getFirst(data, "native_name", id);
      String gender = getFirst(data, "gender", "male");
      String rawModel = getFirst(data, "model", null);
      String model;
      if (rawModel != null) {
         model = switch (rawModel.toLowerCase()) {
            case "femaleasymmetrical" -> "female_asymmetrical";
            case "femalesymmetrical" -> "female_symmetrical";
            default -> rawModel.toLowerCase();
         };
      } else {
         model = gender.equals("female") ? "female_symmetrical" : "male";
      }

      List<String> textures = getAll(data, "texture");
      List<String> goals = getAll(data, "goal");
      List<String> tags = new ArrayList<>(getAll(data, "tag"));
      int health = getLastInt(data, "health", 20);
      List<String> clothesLayer0 = new ArrayList<>();
      List<String> clothesLayer1 = new ArrayList<>();
      Map<String, List<String>> groupedLayer0 = new LinkedHashMap<>();
      Map<String, List<String>> groupedLayer1 = new LinkedHashMap<>();

      for (String raw : getAll(data, "clothes")) {
         String[] parts = raw.split(",");
         if (parts.length >= 3) {
            String group = parts[0].trim();
            int layer = Integer.parseInt(parts[1].trim());
            String tex = parts[2].trim();
            if (layer == 0) {
               clothesLayer0.add(tex);
               groupedLayer0.computeIfAbsent(group, k -> new ArrayList<>()).add(tex);
            } else {
               clothesLayer1.add(tex);
               groupedLayer1.computeIfAbsent(group, k -> new ArrayList<>()).add(tex);
            }
         } else if (parts.length == 2) {
            String group = parts[0].trim();
            String tex = parts[1].trim();
            clothesLayer0.add(tex);
            groupedLayer0.computeIfAbsent(group, k -> new ArrayList<>()).add(tex);
         }
      }

      Set<String> allGroups = new LinkedHashSet<>(groupedLayer0.keySet());
      allGroups.addAll(groupedLayer1.keySet());
      List<LegacyDataParser.ClothesGroup> clothesGroups = new ArrayList<>();

      for (String group : allGroups) {
         clothesGroups.add(new LegacyDataParser.ClothesGroup(group, groupedLayer0.getOrDefault(group, List.of()), groupedLayer1.getOrDefault(group, List.of())));
      }

      List<String> clothes = new ArrayList<>(clothesLayer0);
      Map<String, Integer> startingInv = new LinkedHashMap<>();

      for (String raw : getAll(data, "startingInv")) {
         String[] parts = raw.split(",");
         if (parts.length == 2) {
            startingInv.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
         }
      }

      String firstNameList = getFirst(data, "firstNameList", null);
      String familyNameList = getFirst(data, "familyNameList", null);
      String maleChild = getFirst(data, "malechild", null);
      String femaleChild = getFirst(data, "femalechild", null);
      List<String> bringBackHomeGoods = getAll(data, "bringBackHomeGood");
      List<String> collectGoods = getAll(data, "collectGood");
      Map<String, Integer> requiredGoods = new LinkedHashMap<>();

      for (String raw : getAll(data, "requiredGood")) {
         String[] parts = raw.split(",");
         if (parts.length == 2) {
            requiredGoods.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
         }
      }

      String icon = getFirst(data, "icon", null);
      double baseHeight = getDouble(data, "baseheight", 1.0);
      List<String> toolNeededClasses = getAll(data, "toolneededclass");
      List<String> itemsNeeded = getAll(data, "itemneeded");
      int baseAttackStrength = getLastInt(data, "baseattackstrength", 0);
      String defaultWeapon = getFirst(data, "defaultweapon", null);
      int experienceGiven = getInt(data, "experiencegiven", 0);
      String hiringCost = getFirst(data, "hiringcost", null);
      String altNativeName = getFirst(data, "alt_native_name", null);
      String altKey = getFirst(data, "alt_key", null);
      String travelbookHeldItem = getFirst(data, "travelbook_held_item", null);
      String travelbookHeldItemOffHand = getFirst(data, "travelbook_held_item_off_hand", null);
      boolean travelbookMain = "true".equalsIgnoreCase(getFirst(data, "travelbook_main_culture_villager", "false"));
      String villagerConfig = getFirst(data, "villagerconfig", null);
      int chanceWeight = getInt(data, "chanceweight", 0);
      Map<String, Integer> merchantStock = new LinkedHashMap<>();

      for (String raw : getAll(data, "merchantstock")) {
         String[] parts = raw.split(",");
         if (parts.length == 2) {
            merchantStock.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
         }
      }

      return new LegacyDataParser.VillagerMeta(
         id,
         nativeName,
         gender,
         model,
         textures,
         clothes,
         goals,
         tags,
         health,
         category,
         clothesLayer0,
         clothesLayer1,
         clothesGroups,
         startingInv,
         firstNameList,
         familyNameList,
         maleChild,
         femaleChild,
         bringBackHomeGoods,
         collectGoods,
         requiredGoods,
         icon,
         baseHeight,
         toolNeededClasses,
         itemsNeeded,
         baseAttackStrength,
         defaultWeapon,
         experienceGiven,
         hiringCost,
         altNativeName,
         altKey,
         travelbookHeldItem,
         travelbookHeldItemOffHand,
         travelbookMain,
         villagerConfig,
         merchantStock,
         chanceWeight
      );
   }

   public static List<LegacyDataParser.VillagerMeta> scanVillagerDirectory(Path dir, String category) throws IOException {
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      List<LegacyDataParser.VillagerMeta> results = new ArrayList<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
         for (Path file : stream) {
            try {
               results.add(parseVillagerTxt(file, category));
            } catch (Exception e) {
               System.err.println("[WARN] Erreur parsing villager " + file + ": " + e.getMessage());
            }
         }
      }

      results.sort(Comparator.comparing(LegacyDataParser.VillagerMeta::id));
      return results;
   }

   public static LegacyDataParser.BrickColourThemeLegacy parseBrickColourTheme(String raw) {
      String[] parts = raw.split(";");
      String[] nameWeight = parts[0].split(":");
      String name = nameWeight[0].trim();
      int weight = Integer.parseInt(nameWeight[1].trim());
      Map<String, List<LegacyDataParser.WeightedColorLegacy>> groups = new LinkedHashMap<>();

      for (int i = 1; i < parts.length; i++) {
         String part = parts[i].trim();
         int firstColon = part.indexOf(58);
         String groupName = part.substring(0, firstColon);
         String colorsStr = part.substring(firstColon + 1);
         List<LegacyDataParser.WeightedColorLegacy> colors = new ArrayList<>();

         for (String colorEntry : colorsStr.split(",")) {
            String[] cv = colorEntry.trim().split(":");
            String colorName = cv[0].trim();
            int colorWeight = Integer.parseInt(cv[1].trim());
            colors.add(new LegacyDataParser.WeightedColorLegacy(colorName, colorWeight));
         }

         groups.put(groupName, colors);
      }

      return new LegacyDataParser.BrickColourThemeLegacy(name, weight, groups);
   }

   public static LegacyDataParser.VillageTypeMeta parseVillageTypeTxt(Path txtPath) throws IOException {
      return parseVillageTypeTxt(txtPath, false);
   }

   public static LegacyDataParser.VillageTypeMeta parseVillageTypeTxt(Path txtPath, boolean loneBuilding) throws IOException {
      Map<String, List<String>> data = parseKeyValues(txtPath);
      String id = txtPath.getFileName().toString().replace(".txt", "").toLowerCase(Locale.ROOT);
      id = LegacyIdCanonicaliser.applyVillageTypeTypoFix(id);
      String name = getFirst(data, "name", id);
      int weight = getInt(data, "weight", 10);
      boolean playerControlled = "true".equalsIgnoreCase(getFirst(data, "playercontrolled", "false"));
      String centre = getFirst(data, "centre", null);
      List<String> start = getAll(data, "start");
      List<String> core = getAll(data, "core");
      List<String> secondary = getAll(data, "secondary");
      List<String> never = getAll(data, "never");
      List<String> biomes = getAll(data, "biome");
      Map<String, String> sellingPrices = new LinkedHashMap<>();

      for (String entry : getAll(data, "sellingprice")) {
         int comma = entry.indexOf(44);
         if (comma > 0) {
            sellingPrices.put(entry.substring(0, comma).trim(), entry.substring(comma + 1).trim());
         }
      }

      Map<String, String> buyingPrices = new LinkedHashMap<>();

      for (String entry : getAll(data, "buyingprice")) {
         int comma = entry.indexOf(44);
         if (comma > 0) {
            buyingPrices.put(entry.substring(0, comma).trim(), entry.substring(comma + 1).trim());
         }
      }

      String icon = getFirst(data, "icon", null);
      boolean carriesRaid = "true".equalsIgnoreCase(getFirst(data, "carriesraid", "false"));
      List<String> qualifiers = getAll(data, "qualifier");
      String hillQualifier = getFirst(data, "hillqualifier", null);
      String mountainQualifier = getFirst(data, "mountainqualifier", null);
      String desertQualifier = getFirst(data, "desertqualifier", null);
      String forestQualifier = getFirst(data, "forestqualifier", null);
      String lavaQualifier = getFirst(data, "lavaqualifier", null);
      String lakeQualifier = getFirst(data, "lakequalifier", null);
      String oceanQualifier = getFirst(data, "oceanqualifier", null);
      List<String> pathMaterials = getAll(data, "pathmaterial");
      List<String> playerBuildings = getAll(data, "player");
      String innerWallType = getFirst(data, "innerwalltype", null);
      int innerWallRadius = getInt(data, "innerwallradius", 0);
      String outerWallType = getFirst(data, "outerwalltype", null);
      int outerWallRadius = getInt(data, "outerwallradius", 0);
      List<String> bannerJsons = getAll(data, "banner_json");
      int maxSimWalls = getInt(data, "maxsimultaneouswallconstructions", 0);
      int maxSimConstructions = getInt(data, "maxsimultaneousconstructions", 1);
      List<LegacyDataParser.BrickColourThemeLegacy> brickThemes = new ArrayList<>();

      for (String raw : getAll(data, "brickcolourtheme")) {
         try {
            brickThemes.add(parseBrickColourTheme(raw));
         } catch (Exception e) {
            System.err.println("  [WARN] brickcolourtheme invalide dans " + id + " : " + raw);
         }
      }

      String namelist = getFirst(data, "namelist", null);
      int radius = getInt(data, "radius", 80);
      boolean generateForPlayer = "true".equalsIgnoreCase(getFirst(data, "generateforplayer", "false"));
      boolean keyLB = "true".equalsIgnoreCase(getFirst(data, "keylonebuilding", "false"));
      String keyLBGenerateTag = getFirst(data, "keylonebuildinggeneratetag", null);
      int maxLB = getInt(data, "max", -1);
      int minDistFromSpawn = getInt(data, "mindistancefromspawn", -1);
      double minBiomeValidity = getDouble(data, "minimumbiomevalidity", 0.0);
      List<String> hamlets = getAll(data, "hameau");
      String specialType = getFirst(data, "type", null);
      if (!hamlets.isEmpty() && "hameau".equals(specialType)) {
         specialType = null;
      }

      boolean spawnableFlag = "true".equalsIgnoreCase(getFirst(data, "spawnable", loneBuilding ? "false" : "true"));
      boolean allowExtraBuildings = !loneBuilding && specialType == null && !playerControlled;
      return new LegacyDataParser.VillageTypeMeta(
         id,
         name,
         weight,
         playerControlled,
         centre,
         start,
         core,
         secondary,
         never,
         biomes,
         sellingPrices,
         buyingPrices,
         icon,
         carriesRaid,
         qualifiers,
         hillQualifier,
         mountainQualifier,
         desertQualifier,
         forestQualifier,
         lavaQualifier,
         lakeQualifier,
         oceanQualifier,
         pathMaterials,
         playerBuildings,
         innerWallType,
         innerWallRadius,
         outerWallType,
         outerWallRadius,
         bannerJsons,
         maxSimWalls,
         maxSimConstructions,
         brickThemes,
         loneBuilding,
         namelist,
         radius,
         generateForPlayer,
         keyLB,
         keyLBGenerateTag,
         maxLB,
         minDistFromSpawn,
         minBiomeValidity,
         hamlets,
         specialType,
         spawnableFlag,
         allowExtraBuildings
      );
   }

   public static List<LegacyDataParser.VillageTypeMeta> scanVillageTypes(Path dir) throws IOException {
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      List<LegacyDataParser.VillageTypeMeta> results = new ArrayList<>();
      scanVillageTypesInDir(dir, results);

      try (DirectoryStream<Path> subdirs = Files.newDirectoryStream(dir, x$0 -> Files.isDirectory(x$0))) {
         for (Path subdir : subdirs) {
            scanVillageTypesInDir(subdir, results);
         }
      }

      results.sort(Comparator.comparing(LegacyDataParser.VillageTypeMeta::id));
      return results;
   }

   private static void scanVillageTypesInDir(Path dir, List<LegacyDataParser.VillageTypeMeta> results) throws IOException {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
         for (Path file : stream) {
            String name = file.getFileName().toString();
            if (!name.startsWith("custom")) {
               try {
                  results.add(parseVillageTypeTxt(file));
               } catch (Exception e) {
                  System.err.println("[WARN] Error parsing village type " + file + ": " + e.getMessage());
               }
            }
         }
      }
   }

   public static List<LegacyDataParser.VillageTypeMeta> scanLoneBuildingTypes(Path dir) throws IOException {
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      List<LegacyDataParser.VillageTypeMeta> results = new ArrayList<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
         for (Path file : stream) {
            try {
               results.add(parseVillageTypeTxt(file, true));
            } catch (Exception e) {
               System.err.println("[WARN] Erreur parsing lone building type " + file + ": " + e.getMessage());
            }
         }
      }

      results.sort(Comparator.comparing(LegacyDataParser.VillageTypeMeta::id));
      return results;
   }

   static LegacyDataParser.ParsedPngName parsePngName(String fileName) {
      if (!fileName.endsWith(".png")) {
         return null;
      }

      String name = fileName.substring(0, fileName.length() - 4);
      Pattern p = Pattern.compile("^(.+)_([A-Za-z])(\\d+)$");
      Matcher m = p.matcher(name);
      return m.matches() ? new LegacyDataParser.ParsedPngName(m.group(1), m.group(2).toUpperCase(), Integer.parseInt(m.group(3))) : null;
   }

   static String extractBaseName(String fileName) {
      String name = fileName;
      if (name.endsWith(".txt")) {
         name = name.substring(0, name.length() - 4);
      }

      Pattern p = Pattern.compile("^(.+)_([A-Z])$");
      Matcher m = p.matcher(name);
      return m.matches() ? m.group(1) : name;
   }

   static String extractVariant(String fileName) {
      String name = fileName;
      if (name.endsWith(".txt")) {
         name = name.substring(0, name.length() - 4);
      }

      Pattern p = Pattern.compile("^.+_([A-Z])$");
      Matcher m = p.matcher(name);
      return m.matches() ? m.group(1) : null;
   }

   public static Map<String, List<String>> parseKeyValues(Path path) throws IOException {
      Map<String, List<String>> data = new LinkedHashMap<>();

      for (String line : readLinesLenient(path)) {
         line = line.trim();
         if (!line.isEmpty() && !line.startsWith("//")) {
            int eq = line.indexOf(61);
            if (eq >= 0) {
               String key = line.substring(0, eq).trim().toLowerCase();
               String value = line.substring(eq + 1).trim();
               data.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
         }
      }

      return data;
   }

   private static String getFirst(Map<String, List<String>> data, String key, String defaultValue) {
      List<String> values = data.get(key.toLowerCase());
      return values != null && !values.isEmpty() ? values.getFirst() : defaultValue;
   }

   private static List<String> getAll(Map<String, List<String>> data, String key) {
      return data.getOrDefault(key.toLowerCase(), List.of());
   }

   private static List<String> getAllWithFallback(Map<String, List<String>> data, String primaryKey, String fallbackKey) {
      List<String> result = new ArrayList<>(getAll(data, primaryKey));

      for (String v : getAll(data, fallbackKey)) {
         if (!result.contains(v)) {
            result.add(v);
         }
      }

      return result.isEmpty() ? List.of() : result;
   }

   private static String getLast(Map<String, List<String>> data, String key, String defaultValue) {
      List<String> values = data.get(key.toLowerCase());
      return values != null && !values.isEmpty() ? values.getLast() : defaultValue;
   }

   private static int getLastInt(Map<String, List<String>> data, String key, int defaultValue) {
      String val = getLast(data, key, null);
      if (val == null) {
         return defaultValue;
      }

      try {
         return Integer.parseInt(val);
      } catch (NumberFormatException e) {
         return defaultValue;
      }
   }

   private static int getInt(Map<String, List<String>> data, String key, int defaultValue) {
      String val = getFirst(data, key, null);
      if (val == null) {
         return defaultValue;
      }

      try {
         return Integer.parseInt(val);
      } catch (NumberFormatException e) {
         return defaultValue;
      }
   }

   private static double getDouble(Map<String, List<String>> data, String key, double defaultValue) {
      String val = getFirst(data, key, null);
      if (val == null) {
         return defaultValue;
      }

      try {
         return Double.parseDouble(val);
      } catch (NumberFormatException e) {
         return defaultValue;
      }
   }

   private static boolean hasPrefix(Map<String, List<String>> data, String prefix) {
      String lowerPrefix = prefix.toLowerCase();
      return data.keySet().stream().anyMatch(k -> k.startsWith(lowerPrefix));
   }

   public static LegacyDataParser.ShopMeta parseShopTxt(Path txtPath) throws IOException {
      Map<String, List<String>> data = parseKeyValues(txtPath);
      String id = txtPath.getFileName().toString().replace(".txt", "");
      List<String> sells = splitCsv(getFirst(data, "sells", ""));
      List<String> buys = splitCsv(getFirst(data, "buys", ""));
      List<String> buysOptional = splitCsv(getFirst(data, "buysoptional", ""));
      List<String> deliverTo = splitCsv(getFirst(data, "deliverto", ""));
      return new LegacyDataParser.ShopMeta(id, sells, buys, buysOptional, deliverTo);
   }

   public static List<LegacyDataParser.ShopMeta> scanShopDirectory(Path dir) throws IOException {
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      List<LegacyDataParser.ShopMeta> results = new ArrayList<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
         for (Path file : stream) {
            try {
               results.add(parseShopTxt(file));
            } catch (Exception e) {
               System.err.println("[WARN] Erreur parsing shop " + file + ": " + e.getMessage());
            }
         }
      }

      results.sort(Comparator.comparing(LegacyDataParser.ShopMeta::id));
      return results;
   }

   private static List<String> splitCsv(String csv) {
      if (csv != null && !csv.isBlank()) {
         List<String> result = new ArrayList<>();

         for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
               result.add(trimmed);
            }
         }

         return result;
      } else {
         return List.of();
      }
   }

   public static List<LegacyDataParser.TradedGoodMeta> parseTradedGoodsTxt(Path txtPath) throws IOException {
      List<LegacyDataParser.TradedGoodMeta> results = new ArrayList<>();

      for (String line : readLinesLenient(txtPath)) {
         line = line.trim();
         if (!line.isEmpty() && !line.startsWith("//")) {
            String[] parts = line.split(",", -1);
            if (parts.length >= 10) {
               String name = parts[0].trim();
               String sellingPrice = parts[1].trim();
               String buyingPrice = parts[2].trim();
               int reservedQty = safeParseInt(parts[3].trim(), 0);
               int targetQty = safeParseInt(parts[4].trim(), 0);
               String foreignPrice = parts[5].trim();
               boolean autoGen = "true".equalsIgnoreCase(parts[6].trim());
               int minRep = safeParseInt(parts[8].trim(), 0);
               String category = parts[9].trim();
               results.add(
                  new LegacyDataParser.TradedGoodMeta(name, sellingPrice, buyingPrice, reservedQty, targetQty, foreignPrice, autoGen, minRep, category)
               );
            }
         }
      }

      return results;
   }

   private static int safeParseInt(String s, int fallback) {
      if (s != null && !s.isBlank()) {
         try {
            return Integer.parseInt(s);
         } catch (NumberFormatException e) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   public static List<String> parseNamelistTxt(Path txtPath) throws IOException {
      List<String> names = new ArrayList<>();

      for (String line : readLinesLenient(txtPath)) {
         line = line.trim();
         if (!line.isEmpty() && !line.startsWith("//")) {
            names.add(line);
         }
      }

      return names;
   }

   public static Map<String, List<String>> scanNamelistDirectory(Path dir) throws IOException {
      if (!Files.isDirectory(dir)) {
         return Map.of();
      }

      Map<String, List<String>> results = new TreeMap<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
         for (Path file : stream) {
            String key = file.getFileName().toString().replace(".txt", "");

            try {
               if (!key.endsWith(" copie")) {
                  List<String> names = parseNamelistTxt(file);
                  if (!names.isEmpty()) {
                     results.put(key, names);
                  }
               }
            } catch (Exception e) {
               System.err.println("[WARN] Erreur parsing namelist " + file + ": " + e.getMessage());
            }
         }
      }

      return results;
   }

   public record BrickColourThemeLegacy(String name, int weight, Map<String, List<LegacyDataParser.WeightedColorLegacy>> colourGroups) {
   }

   public record BuildingMeta(
      String baseName,
      String category,
      int width,
      int length,
      int buildingOrientation,
      int maxCount,
      double minDistance,
      double maxDistance,
      List<LegacyDataParser.LevelMeta> levels,
      List<String> tags,
      List<String> males,
      List<String> females,
      List<String> subBuildings,
      List<String> startingSubBuildings,
      String shop,
      String icon,
      String fixedOrientation,
      int areaToClear,
      int areaToClearLengthBefore,
      int areaToClearLengthAfter,
      int areaToClearWidthBefore,
      int areaToClearWidthAfter,
      int version,
      List<String> farFromTags,
      int price,
      int reputation,
      boolean isGift,
      List<String> randomBrickColours,
      List<LegacyDataParser.StartingGoodMeta> startingGoods,
      boolean showTownHallSigns,
      boolean isSubBuilding,
      boolean isWallSegment,
      boolean isBorderBuilding
   ) {
   }

   public record BuildingWithVariants(
      LegacyDataParser.BuildingMeta meta, Map<String, List<Path>> variantPngs, Map<String, LegacyDataParser.BuildingMeta> variantMetas
   ) {
      public LegacyDataParser.BuildingMeta metaForVariant(String variant) {
         return this.variantMetas.getOrDefault(variant, this.meta);
      }
   }

   public record ClothesGroup(String group, List<String> layer0, List<String> layer1) {
   }

   public record LevelMeta(
      int level,
      int startLevel,
      int priority,
      String nativeName,
      List<String> tags,
      List<String> subBuildings,
      int pathLevel,
      boolean rebuildPath,
      int pathWidth,
      String signs,
      int priorityMoveIn,
      int extraSimultaneousWallConstructions,
      List<String> abstractedProduction,
      List<String> requiredTags,
      List<String> parentTags,
      List<String> requiredParentTags,
      List<String> clearTags,
      List<String> villageTags
   ) {
   }

   private record ParsedPngName(String baseName, String variant, int level) {
   }

   public record ShopMeta(String id, List<String> sells, List<String> buys, List<String> buysOptional, List<String> deliverTo) {
   }

   public record StartingGoodMeta(String item, double probability, int fixedNumber, int randomNumber) {
   }

   public record TradedGoodMeta(
      String name,
      String sellingPrice,
      String buyingPrice,
      int reservedQuantity,
      int targetQuantity,
      String foreignMerchantPrice,
      boolean autoGenerated,
      int minReputation,
      String category
   ) {
   }

   public record VillageTypeMeta(
      String id,
      String name,
      int weight,
      boolean playerControlled,
      String centre,
      List<String> start,
      List<String> core,
      List<String> secondary,
      List<String> never,
      List<String> biomes,
      Map<String, String> sellingPriceOverrides,
      Map<String, String> buyingPriceOverrides,
      String icon,
      boolean carriesRaid,
      List<String> qualifiers,
      String hillQualifier,
      String mountainQualifier,
      String desertQualifier,
      String forestQualifier,
      String lavaQualifier,
      String lakeQualifier,
      String oceanQualifier,
      List<String> pathMaterials,
      List<String> playerBuildings,
      String innerWallType,
      int innerWallRadius,
      String outerWallType,
      int outerWallRadius,
      List<String> bannerJsons,
      int maxSimultaneousWallConstructions,
      int maxSimultaneousConstructions,
      List<LegacyDataParser.BrickColourThemeLegacy> brickColourThemes,
      boolean loneBuilding,
      String namelist,
      int radius,
      boolean generateForPlayer,
      boolean keyLoneBuilding,
      String keyLoneBuildingGenerateTag,
      int max,
      int minDistanceFromSpawn,
      double minimumBiomeValidity,
      List<String> hamlets,
      @Nullable String specialType,
      boolean spawnable,
      boolean allowExtraBuildings
   ) {
   }

   public record VillagerMeta(
      String id,
      String nativeName,
      String gender,
      String model,
      List<String> textures,
      List<String> clothes,
      List<String> goals,
      List<String> tags,
      int health,
      String category,
      List<String> clothesLayer0,
      List<String> clothesLayer1,
      List<LegacyDataParser.ClothesGroup> clothesGroups,
      Map<String, Integer> startingInv,
      String firstNameList,
      String familyNameList,
      String maleChild,
      String femaleChild,
      List<String> bringBackHomeGoods,
      List<String> collectGoods,
      Map<String, Integer> requiredGoods,
      String icon,
      double baseHeight,
      List<String> toolNeededClasses,
      List<String> itemsNeeded,
      int baseAttackStrength,
      String defaultWeapon,
      int experienceGiven,
      String hiringCost,
      String altNativeName,
      String altKey,
      String travelbookHeldItem,
      String travelbookHeldItemOffHand,
      boolean travelbookMainCultureVillager,
      String villagerConfig,
      Map<String, Integer> merchantStock,
      int chanceWeight
   ) {
   }

   public record WeightedColorLegacy(String color, int weight) {
   }
}
