package org.millenaire.goal.visit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.content.ContentDirectoryManager;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.DisabledIdsLoader;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceKind;
import org.millenaire.content.SubmodRoot;
import org.millenaire.culture.CultureLoader;
import org.millenaire.culture.JsonLoaderUtils;
import org.millenaire.goal.GoalRegistry;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.impl.ObserveVillagerGoal;
import org.millenaire.goal.impl.PlayGoal;
import org.millenaire.goal.impl.VisitBuildingGoal;
import org.slf4j.Logger;

public final class VisitGoalLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final String VISIT_GOAL_DIR = "/millenaire/visit_goal";
   private static final Set<String> WARNED_REPLACE_IDS = ConcurrentHashMap.newKeySet();

   public static void resetForTesting() {
      WARNED_REPLACE_IDS.clear();
   }

   private VisitGoalLoader() {
   }

   public static void loadAll(GoalRegistry goalRegistry) {
      WARNED_REPLACE_IDS.clear();
      List<String> filenames = discoverVisitGoals();
      if (filenames.isEmpty()) {
         throw new RuntimeException(
            "No visit_goal/*.json files found on the classpath under /millenaire/visit_goal — check that the resource directory is packaged correctly"
         );
      }

      int count = 0;

      for (String filename : filenames) {
         VisitGoalSchema schema = parseFile(filename);
         if (schema == null) {
            throw new RuntimeException("Failed to load visit_goal file '" + filename + ".json' — see logs above");
         }

         VillagerGoal goal = buildGoal(schema);
         goalRegistry.register(goal);
         count++;
         LOGGER.debug("Visit goal loaded: {}", schema.id());
      }

      LOGGER.info("{} visit goals loaded", count);
      loadPerCultureGoals(goalRegistry, CultureLoader.BUILTIN_CULTURES);
   }

   @Nullable
   static VisitGoalSchema parseFile(String filename) {
      String path = "/millenaire/visit_goal/" + filename + ".json";

      try (InputStream is = VisitGoalLoader.class.getResourceAsStream(path)) {
         if (is == null) {
            LOGGER.error("Visit goal file not found: {}", path);
            return null;
         }

         JsonObject json;
         try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         }

         return parseSchema(filename, json);
      } catch (Exception e) {
         LOGGER.error("Error parsing visit goal {}: {}", new Object[]{filename, e.getMessage(), e});
         return null;
      }
   }

   @Nullable
   static VisitGoalSchema parsePerCultureFile(String culture, String id) {
      String path = "/millenaire/cultures/" + culture + "/goal/" + id + ".json";
      String combinedFilename = culture + "/" + id;

      try (InputStream is = VisitGoalLoader.class.getResourceAsStream(path)) {
         if (is == null) {
            LOGGER.error("Per-culture visit goal file not found: {}", path);
            return null;
         }

         JsonObject json;
         try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         }

         return parseSchema(combinedFilename, json);
      } catch (Exception e) {
         LOGGER.error("Error parsing per-culture visit goal {}: {}", new Object[]{path, e.getMessage(), e});
         return null;
      }
   }

   @Nullable
   static VisitGoalSchema parsePerCultureExternal(Path file, String culture, String id) {
      String combinedFilename = culture + "/" + id;
      if (Files.isRegularFile(file) && ContentDirectoryManager.isInsideRoot(file) && ContentDirectoryManager.checkSize(file, 1000000L)) {
         try (
            InputStream is = Files.newInputStream(file);
            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
         ) {
            JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
            return parseSchema(combinedFilename, json);
         } catch (Exception e) {
            LOGGER.error("Error parsing external per-culture visit goal {}: {}", new Object[]{file, e.getMessage(), e});
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   static VisitGoalSchema parsePerCultureExternal(Resource res, String culture, String id) {
      String combinedFilename = culture + "/" + id;

      try (
         InputStream is = res.open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         return parseSchema(combinedFilename, json);
      } catch (Exception e) {
         LOGGER.error("Error parsing external per-culture visit goal {}: {}", new Object[]{res.relPath(), e.getMessage(), e});
         return null;
      }
   }

   static VisitGoalSchema parseSchema(String filename, JsonObject json) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", filename);
      String type = GsonHelper.getAsString(json, "type");
      String goalKey = GsonHelper.getAsString(json, "goalKey");

      return switch (type) {
         case "visitBuilding" -> parseVisitBuilding(filename, id, goalKey, json);
         case "observeVillager" -> parseObserveVillager(filename, id, goalKey, json);
         case "play" -> parsePlay(filename, id, goalKey, json);
         default -> throw new IllegalArgumentException("Unknown visit goal type '" + type + "' in " + filename + ".json");
      };
   }

   private static VisitGoalSchema.VisitBuilding parseVisitBuilding(String filename, ResourceLocation id, String goalKey, JsonObject json) {
      String buildingTag = GsonHelper.getAsString(json, "buildingTag");
      String requiredTag = getStringOrNull(json, "requiredTag");
      int basePriority = GsonHelper.getAsInt(json, "basePriority");
      int priorityRandom = GsonHelper.getAsInt(json, "priorityRandom", 0);
      int durationTicks = GsonHelper.getAsInt(json, "durationTicks");
      int reoccurDelayTicks = GsonHelper.getAsInt(json, "reoccurDelayTicks", -1);
      boolean allowRandomMoves = GsonHelper.getAsBoolean(json, "allowRandomMoves", false);
      boolean leisure = GsonHelper.getAsBoolean(json, "leisure", true);
      String targetPosType = SpecialPointResolver.resolveOrThrow(getStringOrNull(json, "targetPosType"), filename + ".json");
      int minimumHour = GsonHelper.getAsInt(json, "minimumHour", -1);
      int maximumHour = GsonHelper.getAsInt(json, "maximumHour", -1);
      int maxSimultaneousInBuilding = GsonHelper.getAsInt(json, "maxSimultaneousInBuilding", 0);
      List<String> heldItems = parseItemList(json, "heldItems", filename);
      List<String> heldItemsDestination = parseItemList(json, "heldItemsDestination", filename);
      return new VisitGoalSchema.VisitBuilding(
         id,
         goalKey,
         buildingTag,
         requiredTag,
         basePriority,
         priorityRandom,
         durationTicks,
         reoccurDelayTicks,
         allowRandomMoves,
         leisure,
         targetPosType,
         minimumHour,
         maximumHour,
         maxSimultaneousInBuilding,
         heldItems,
         heldItemsDestination
      );
   }

   private static VisitGoalSchema.ObserveVillager parseObserveVillager(String filename, ResourceLocation id, String goalKey, JsonObject json) {
      enforceGoalKeyMatchesId("observeVillager", filename, id, goalKey);
      String targetGoalTag = getStringOrNull(json, "targetGoalTag");
      Set<ResourceLocation> targetGoalIds = parseResourceLocationSet(json, "targetGoalIds");
      if (targetGoalTag == null == (targetGoalIds == null)) {
         throw new IllegalArgumentException("observeVillager " + filename + ": exactly one of targetGoalTag / targetGoalIds must be set");
      }

      int basePriority = GsonHelper.getAsInt(json, "basePriority");
      int priorityRandom = GsonHelper.getAsInt(json, "priorityRandom", 0);
      int durationTicks = GsonHelper.getAsInt(json, "durationTicks");
      int reoccurDelayTicks = GsonHelper.getAsInt(json, "reoccurDelayTicks", -1);
      int minimumHour = GsonHelper.getAsInt(json, "minimumHour", -1);
      int maximumHour = GsonHelper.getAsInt(json, "maximumHour", -1);
      String buildingTag = getStringOrNull(json, "buildingTag");
      String requiredTag = getStringOrNull(json, "requiredTag");
      List<String> heldItems = parseItemList(json, "heldItems", filename);
      return new VisitGoalSchema.ObserveVillager(
         id,
         goalKey,
         targetGoalTag,
         targetGoalIds,
         basePriority,
         priorityRandom,
         durationTicks,
         reoccurDelayTicks,
         minimumHour,
         maximumHour,
         buildingTag,
         requiredTag,
         heldItems
      );
   }

   private static VisitGoalSchema.Play parsePlay(String filename, ResourceLocation id, String goalKey, JsonObject json) {
      enforceGoalKeyMatchesId("play", filename, id, goalKey);
      boolean withFriends = GsonHelper.getAsBoolean(json, "withFriends");
      return new VisitGoalSchema.Play(id, goalKey, withFriends);
   }

   private static void enforceGoalKeyMatchesId(String variant, String filename, ResourceLocation id, String goalKey) {
      if (!id.getPath().equals(goalKey)) {
         throw new IllegalArgumentException(
            variant
               + " "
               + filename
               + ".json: goalKey '"
               + goalKey
               + "' must equal id path '"
               + id.getPath()
               + "' — the "
               + variant
               + " engine does not support goalKey overrides"
         );
      }
   }

   @Nullable
   private static String getStringOrNull(JsonObject json, String key) {
      if (!json.has(key)) {
         return null;
      }

      JsonElement el = json.get(key);
      return el != null && !el.isJsonNull() ? el.getAsString() : null;
   }

   @Nullable
   private static List<String> parseItemList(JsonObject json, String key, String filename) {
      if (!json.has(key)) {
         return null;
      }

      JsonElement el = json.get(key);
      if (el != null && !el.isJsonNull()) {
         JsonArray arr = el.getAsJsonArray();
         if (arr.size() == 0) {
            return null;
         }

         List<String> items = new ArrayList<>(arr.size());

         for (JsonElement item : arr) {
            String itemId = item.getAsString();
            if (itemId == null || itemId.isEmpty()) {
               throw new IllegalArgumentException("Empty item id in " + filename + ".json field '" + key + "'");
            }

            items.add(itemId);
         }

         return List.copyOf(items);
      } else {
         return null;
      }
   }

   @Nullable
   private static Set<ResourceLocation> parseResourceLocationSet(JsonObject json, String key) {
      if (!json.has(key)) {
         return null;
      }

      JsonElement el = json.get(key);
      if (el != null && !el.isJsonNull()) {
         JsonArray arr = el.getAsJsonArray();
         if (arr.size() == 0) {
            return null;
         }

         Set<ResourceLocation> out = new LinkedHashSet<>();

         for (JsonElement rl : arr) {
            out.add(ResourceLocation.parse(rl.getAsString()));
         }

         return Collections.unmodifiableSet(out);
      } else {
         return null;
      }
   }

   private static VillagerGoal buildGoal(VisitGoalSchema schema) {
      return switch (schema) {
         case VisitGoalSchema.VisitBuilding vb -> VisitBuildingGoal.fromSchema(vb);
         case VisitGoalSchema.ObserveVillager ov -> ObserveVillagerGoal.fromSchema(ov);
         case VisitGoalSchema.Play p -> PlayGoal.fromSchema(p);
         default -> throw new MatchException(null, null);
      };
   }

   static List<String> discoverVisitGoals() {
      List<String> names = new ArrayList<>();

      try {
         URL url = VisitGoalLoader.class.getResource("/millenaire/visit_goal");
         if (url == null) {
            LOGGER.warn("Visit goal directory not found in classpath: {}", "/millenaire/visit_goal");
            return names;
         }

         URI uri = url.toURI();
         if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
               Path dirPath = fs.getPath("/millenaire/visit_goal");
               collectJsonNames(dirPath, names);
            }
         } else {
            Path dirPath = Path.of(uri);
            collectJsonNames(dirPath, names);
         }
      } catch (Exception e) {
         LOGGER.error("Error scanning visit_goal directory", e);
      }

      return names;
   }

   private static void collectJsonNames(Path dirPath, List<String> names) throws IOException {
      try (Stream<Path> stream = Files.list(dirPath)) {
         stream.filter(p -> p.toString().endsWith(".json")).map(p -> p.getFileName().toString().replace(".json", "")).sorted().forEach(names::add);
      }
   }

   public static void loadPerCultureGoals(GoalRegistry goalRegistry, List<String> cultures) {
      if (cultures != null && !cultures.isEmpty()) {
         int total = 0;

         for (String culture : cultures) {
            Set<String> disabledIds = resolvePerCultureDisabledIds(culture);

            for (String id : discoverCultureGoals(culture)) {
               if (disabledIds.contains(id)) {
                  LOGGER.debug("Skipping per-culture visit goal '{}/{}' (listed in _disabled.json)", culture, id);
               } else {
                  String classpathPath = "/millenaire/cultures/" + culture + "/goal/" + id + ".json";
                  warnIfMultipleSubmodsShipReplace(culture, id, "visit_goal:" + culture + "/" + id);
                  VisitGoalSchema schema = loadPerCultureSchema(culture, id, classpathPath);
                  if (schema != null) {
                     VillagerGoal goal = buildGoal(schema);
                     goalRegistry.register(goal);
                     total++;
                     LOGGER.debug("Per-culture visit goal loaded: {}", schema.id());
                  }
               }
            }
         }

         if (total > 0) {
            LOGGER.info("{} per-culture visit goals loaded", total);
         }
      }
   }

   public static void loadExternalPerCultureGoals(GoalRegistry goalRegistry, List<String> cultures) {
      if (ContentDirectoryManager.isInitialized()) {
         if (cultures != null && !cultures.isEmpty()) {
            WARNED_REPLACE_IDS.clear();
            int added = 0;
            int skipped = 0;

            for (String culture : cultures) {
               List<SubmodRoot> roots = CustomContentIndex.current().rootsForCulture(culture);
               if (!roots.isEmpty()) {
                  Set<String> disabledIds = resolvePerCultureDisabledIds(culture);
                  Set<String> alreadyLoaded = new HashSet<>(discoverCultureGoals(culture));
                  Set<String> seenInThisRun = new HashSet<>();

                  for (SubmodRoot submod : roots) {
                     Path goalDir = submod.root().resolve("cultures").resolve(culture).resolve("goal");
                     if (Files.isDirectory(goalDir)) {
                        try (Stream<Path> stream = ContentDirectoryManager.safeWalk(goalDir)) {
                           for (Path p : (Iterable<Path>)stream::iterator) {
                              if (Files.isRegularFile(p)) {
                                 String name = p.getFileName().toString();
                                 if (!name.startsWith("_") && name.endsWith(".json")) {
                                    String id = name.substring(0, name.length() - 5);
                                    if (!alreadyLoaded.contains(id)) {
                                       if (disabledIds.contains(id)) {
                                          skipped++;
                                       } else if (!seenInThisRun.add(id)) {
                                          String warnKey = "visit_goal:" + culture + "/" + id;
                                          if (WARNED_REPLACE_IDS.add(warnKey)) {
                                             LOGGER.warn(
                                                "Per-culture visit goal '{}/{}' already loaded from an earlier sub-mod; ignoring duplicate from '{}'",
                                                new Object[]{culture, id, submod.displayName()}
                                             );
                                          }
                                       } else {
                                          VisitGoalSchema schema = parsePerCultureExternal(p, culture, id);
                                          if (schema != null) {
                                             VillagerGoal goal = buildGoal(schema);
                                             goalRegistry.register(goal);
                                             added++;
                                             LOGGER.debug(
                                                "Loaded external per-culture visit goal '{}/{}' from sub-mod '{}'",
                                                new Object[]{culture, id, submod.displayName()}
                                             );
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        } catch (IOException e) {
                           LOGGER.error("Failed to walk external per-culture goal dir {}: {}", new Object[]{goalDir, e.getMessage(), e});
                        }
                     }
                  }
               }
            }

            if (added + skipped > 0) {
               LOGGER.info("External per-culture visit goals: {} added, {} disabled", added, skipped);
            }
         }
      }
   }

   @Nullable
   private static VisitGoalSchema loadPerCultureSchema(String culture, String id, String classpathPath) {
      if (ContentDirectoryManager.isInitialized()) {
         ContentFs cultureFs = CustomContentIndex.current().forCulture(culture);
         Optional<Resource> hit = cultureFs.findFirst("goal/" + id + ".json");
         if (hit.isPresent()) {
            Resource res = hit.get();
            if (res.kind() == SourceKind.SUBMOD || res.kind() == SourceKind.STANDARD) {
               VisitGoalSchema schema = parsePerCultureExternal(res, culture, id);
               if (schema != null) {
                  return schema;
               }

               LOGGER.warn("External per-culture visit goal {} failed to parse; falling back to JAR.", res.relPath());
            }
         }
      }

      return parsePerCultureFile(culture, id);
   }

   static List<String> discoverCultureGoals(String culture) {
      List<String> names = new ArrayList<>();
      String dir = "/millenaire/cultures/" + culture + "/goal";

      try {
         URL url = VisitGoalLoader.class.getResource(dir);
         if (url == null) {
            LOGGER.debug("Per-culture goal directory not found in classpath: {}", dir);
            return names;
         }

         URI uri = url.toURI();
         if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
               Path dirPath = fs.getPath(dir);
               collectJsonNames(dirPath, names);
            }
         } else {
            Path dirPath = Path.of(uri);
            collectJsonNames(dirPath, names);
         }
      } catch (Exception e) {
         LOGGER.error("Error scanning per-culture goal directory {}", dir, e);
      }

      return names;
   }

   private static Set<String> resolvePerCultureDisabledIds(String culture) {
      if (!ContentDirectoryManager.isInitialized()) {
         return Collections.emptySet();
      }

      List<SubmodRoot> roots = CustomContentIndex.current().rootsForCulture(culture);
      if (roots.isEmpty()) {
         return Collections.emptySet();
      }

      List<Path> dirs = new ArrayList<>(roots.size());

      for (SubmodRoot root : roots) {
         dirs.add(root.root().resolve("cultures").resolve(culture).resolve("goal"));
      }

      return DisabledIdsLoader.loadUnion(dirs);
   }

   private static void warnIfMultipleSubmodsShipReplace(String culture, String id, String logicalId) {
      if (ContentDirectoryManager.isInitialized()) {
         ContentFs cultureFs = CustomContentIndex.current().forCulture(culture);
         List<Resource> all = cultureFs.findAll("goal/" + id + ".json");
         List<Resource> submods = new ArrayList<>();

         for (Resource res : all) {
            if (res.kind() == SourceKind.SUBMOD) {
               submods.add(res);
            }
         }

         if (submods.size() > 1) {
            if (WARNED_REPLACE_IDS.add(logicalId)) {
               LOGGER.warn(
                  "Multiple sub-mods ship {}: {}. Using {} (overlay top-most).",
                  new Object[]{logicalId, submods.stream().map(r -> r.source().displayName()).toList(), submods.get(0).source().displayName()}
               );
            }
         }
      }
   }
}
