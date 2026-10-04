package org.millenaire.quest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceKind;
import org.millenaire.culture.CultureLoader;
import org.millenaire.culture.JsonLoaderUtils;
import org.slf4j.Logger;

public final class QuestLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final String QUESTS_DIR = "quests";
   private static final String LANGUAGES_DIR = "languages";
   private static volatile String[] cachedLanguages = null;
   private static final String[] FALLBACK_LANGUAGES = new String[]{
      "ar", "cs", "de", "en", "es", "fr", "it", "ko", "nl", "pl", "pt", "pt_br", "ru", "sl", "sv", "uk", "zh_cn", "zh_tw"
   };
   private static final Set<String> WARNED_TEXT_SHADOW = ConcurrentHashMap.newKeySet();

   public static void resetForTesting() {
      WARNED_TEXT_SHADOW.clear();
      cachedLanguages = null;
   }

   private QuestLoader() {
   }

   public static void loadAll() {
      QuestRegistry.clear();
      QuestTextRegistry.clear();
      WARNED_TEXT_SHADOW.clear();
      cachedLanguages = null;
      loadQuestDefinitions();
      loadQuestTexts();
      LOGGER.info("Loaded {} quest definitions, {} quest languages", QuestRegistry.size(), QuestTextRegistry.languageCount());
   }

   private static void loadQuestDefinitions() {
      Set<String> disabledIds = loadQuestDisabledIds();
      Set<String> loadedQuestPaths = new HashSet<>();
      List<String> manifest = loadManifest();
      if (manifest == null) {
         LOGGER.warn("No quest manifest found — no top-level quests will be loaded");
      } else {
         int skipped = 0;
         int manifestDisabled = 0;

         for (String questPath : manifest) {
            if (questPath.startsWith("worldquest-")) {
               skipped++;
            } else if (disabledIds.contains(questPath)) {
               manifestDisabled++;
            } else {
               Quest quest = loadQuest(questPath);
               if (quest != null) {
                  QuestRegistry.register(quest);
                  loadedQuestPaths.add(questPath);
                  LOGGER.debug("Quest loaded: {}", quest.key());
               }
            }
         }

         if (skipped > 0) {
            LOGGER.info("Skipped {} world quests (special action mechanisms not yet implemented)", skipped);
         }

         if (manifestDisabled > 0) {
            LOGGER.info("Skipped {} quest(s) listed in _disabled.json", manifestDisabled);
         }
      }

      loadPerCultureQuests(loadedQuestPaths, disabledIds);
      loadExternalQuestExtras(loadedQuestPaths, disabledIds);
   }

   private static Set<String> loadQuestDisabledIds() {
      Set<String> out = new HashSet<>();
      ContentFs root = CustomContentIndex.current().root();

      for (Resource res : root.findAll("quests/_disabled.json")) {
         out.addAll(parseDisabledIds(res));
      }

      for (String culture : CultureLoader.discoverCultures()) {
         ContentFs cultureFs = CustomContentIndex.current().forCulture(culture);

         for (Resource res : cultureFs.findAll("quests/_disabled.json")) {
            for (String id : parseDisabledIds(res)) {
               out.add("cultures/" + culture + "/" + id);
            }
         }
      }

      return out;
   }

   private static Set<String> parseDisabledIds(Resource res) {
      try (InputStream in = res.open()) {
         String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         JsonElement root = JsonParser.parseString(body);
         if (!root.isJsonArray()) {
            LOGGER.warn("{} from {} must be a JSON array; ignoring", res.relPath(), res.source().displayName());
            return Set.of();
         }

         Set<String> out = new HashSet<>();

         for (JsonElement e : root.getAsJsonArray()) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
               out.add(e.getAsString());
            }
         }

         return out;
      } catch (Exception e) {
         LOGGER.warn("Could not parse {} from {}: {}", new Object[]{res.relPath(), res.source().displayName(), e.getMessage()});
         return Set.of();
      }
   }

   private static void loadPerCultureQuests(Set<String> alreadyLoaded, Set<String> disabledIds) {
      int loaded = 0;
      int perCultureDisabled = 0;

      for (String culture : CultureLoader.discoverCultures()) {
         ContentFs cultureFs = CustomContentIndex.current().forCulture(culture);
         List<Resource> entries = cultureFs.walk("quests", Integer.MAX_VALUE).toList();
         String typeRootPrefix = ("cultures/" + culture + "/quests/").toLowerCase(Locale.ROOT);

         for (Resource res : entries) {
            String relPathFull = res.relPath();
            if (relPathFull.endsWith(".json")) {
               String filename = relPathFull.substring(relPathFull.lastIndexOf(47) + 1);
               if (!filename.startsWith("_") && relPathFull.startsWith(typeRootPrefix)) {
                  String relInsideQuest = relPathFull.substring(typeRootPrefix.length());
                  String relWithoutExt = relInsideQuest.substring(0, relInsideQuest.length() - 5);
                  String questPath = "cultures/" + culture + "/" + relWithoutExt;
                  if (!alreadyLoaded.contains(questPath)) {
                     if (disabledIds.contains(questPath)) {
                        perCultureDisabled++;
                     } else {
                        Quest quest = parseQuestResource(res);
                        if (quest != null) {
                           QuestRegistry.register(quest);
                           alreadyLoaded.add(questPath);
                           loaded++;
                           LOGGER.debug("Per-culture quest loaded: {} (path={})", quest.key(), questPath);
                        }
                     }
                  }
               }
            }
         }
      }

      if (loaded > 0) {
         LOGGER.info("{} per-culture quest(s) loaded", loaded);
      }

      if (perCultureDisabled > 0) {
         LOGGER.info("Skipped {} per-culture quest(s) listed in _disabled.json", perCultureDisabled);
      }
   }

   private static void loadExternalQuestExtras(Set<String> alreadyLoaded, Set<String> disabledIds) {
      ContentFs questsFs = CustomContentIndex.current().forGlobalContent("quests");
      int externalDisabled = 0;
      int externalLoaded = 0;
      List<Resource> entries = questsFs.walk("", Integer.MAX_VALUE).toList();
      String typeRootPrefix = "quests/".toLowerCase(Locale.ROOT);
      String langPrefix = "quests/lang/".toLowerCase(Locale.ROOT);

      for (Resource res : entries) {
         String relPathFull = res.relPath();
         if (relPathFull.endsWith(".json")) {
            String filename = relPathFull.substring(relPathFull.lastIndexOf(47) + 1);
            if (!filename.startsWith("_") && relPathFull.startsWith(typeRootPrefix) && !relPathFull.startsWith(langPrefix)) {
               String relInside = relPathFull.substring(typeRootPrefix.length());
               String questPath = relInside.substring(0, relInside.length() - 5);
               if (!alreadyLoaded.contains(questPath)) {
                  if (disabledIds.contains(questPath)) {
                     externalDisabled++;
                  } else {
                     Quest quest = parseQuestResource(res);
                     if (quest != null) {
                        QuestRegistry.register(quest);
                        alreadyLoaded.add(questPath);
                        externalLoaded++;
                        LOGGER.debug("Loaded external quest '{}' from {}", questPath, res.source().displayName());
                     }
                  }
               }
            }
         }
      }

      if (externalLoaded > 0) {
         LOGGER.info("Loaded {} external quest(s) from sub-mods", externalLoaded);
      }

      if (externalDisabled > 0) {
         LOGGER.info("Skipped {} external quest(s) via cross-sub-mod _disabled.json", externalDisabled);
      }
   }

   @Nullable
   private static List<String> loadManifest() {
      ContentFs root = CustomContentIndex.current().root();
      List<Resource> matches = root.findAll("quests/_manifest.json");
      Resource jarManifest = null;

      for (Resource res : matches) {
         if (res.kind() == SourceKind.CLASSPATH) {
            jarManifest = res;
            break;
         }
      }

      if (jarManifest == null) {
         jarManifest = root.findFirst("quests/_manifest.json").orElse(null);
      }

      if (jarManifest == null) {
         return null;
      }

      try (
         InputStream is = jarManifest.open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonArray array = (JsonArray)GSON.fromJson(reader, JsonArray.class);
         List<String> paths = new ArrayList<>();

         for (JsonElement e : array) {
            paths.add(e.getAsString());
         }

         return paths;
      } catch (Exception e) {
         LOGGER.error("Error reading quest manifest", e);
         return null;
      }
   }

   @Nullable
   private static Quest loadQuest(String questPath) {
      Resource res = resolveQuestResource(questPath);
      if (res == null) {
         LOGGER.debug("Quest file not found via ContentFs: {}", questPath);
         return null;
      } else {
         return parseQuestResource(res);
      }
   }

   @Nullable
   private static Resource resolveQuestResource(String questPath) {
      ContentFs root = CustomContentIndex.current().root();
      if (questPath.startsWith("cultures/")) {
         String tail = questPath.substring("cultures/".length());
         int slash = tail.indexOf(47);
         if (slash <= 0) {
            return root.findFirst("quests/" + questPath + ".json").orElse(null);
         }

         String culture = tail.substring(0, slash);
         String rest = tail.substring(slash + 1);
         return CustomContentIndex.current().forCulture(culture).findFirst("quests/" + rest + ".json").orElse(null);
      } else {
         return root.findFirst("quests/" + questPath + ".json").orElse(null);
      }
   }

   @Nullable
   private static Quest parseQuestResource(Resource res) {
      try (
         InputStream is = res.open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         return parseQuest(json);
      } catch (Exception e) {
         LOGGER.error("Error reading quest from {}: {}", new Object[]{res.relPath(), e.getMessage(), e});
         return null;
      }
   }

   @Nullable
   private static Quest parseQuest(JsonObject json) {
      String key = GsonHelper.getAsString(json, "key");
      double chancePerHour = GsonHelper.getAsDouble(json, "chancePerHour", 0.0);
      int maxSimultaneous = GsonHelper.getAsInt(json, "maxSimultaneous", 5);
      int minReputation = GsonHelper.getAsInt(json, "minReputation", 0);
      List<String> globalTagsRequired = JsonLoaderUtils.parseStringList(json, "globalTagsRequired");
      List<String> globalTagsForbidden = JsonLoaderUtils.parseStringList(json, "globalTagsForbidden");
      List<String> playerTagsRequired = JsonLoaderUtils.parseStringList(json, "playerTagsRequired");
      List<String> playerTagsForbidden = JsonLoaderUtils.parseStringList(json, "playerTagsForbidden");
      List<QuestVillagerDef> villagerDefs = new ArrayList<>();

      for (JsonElement e : GsonHelper.getAsJsonArray(json, "villagerDefs")) {
         villagerDefs.add(parseVillagerDef(e.getAsJsonObject()));
      }

      List<QuestStep> steps = new ArrayList<>();

      for (JsonElement e : GsonHelper.getAsJsonArray(json, "steps")) {
         steps.add(parseStep(e.getAsJsonObject()));
      }

      if (villagerDefs.isEmpty()) {
         LOGGER.error("Quest '{}' has no villagerDefs — skipping", key);
         return null;
      } else if (steps.isEmpty()) {
         LOGGER.error("Quest '{}' has no steps — skipping", key);
         return null;
      } else {
         return new Quest(
            key,
            chancePerHour,
            maxSimultaneous,
            minReputation,
            globalTagsRequired,
            globalTagsForbidden,
            playerTagsRequired,
            playerTagsForbidden,
            villagerDefs,
            steps
         );
      }
   }

   private static QuestVillagerDef parseVillagerDef(JsonObject json) {
      String key = GsonHelper.getAsString(json, "key");
      List<String> types = JsonLoaderUtils.parseStringList(json, "villagerTypes");
      String relatedTo = json.has("relatedTo") ? GsonHelper.getAsString(json, "relatedTo") : null;
      String relation = json.has("relation") ? GsonHelper.getAsString(json, "relation") : null;
      List<String> requiredTags = JsonLoaderUtils.parseStringList(json, "requiredTags");
      List<String> forbiddenTags = JsonLoaderUtils.parseStringList(json, "forbiddenTags");
      return new QuestVillagerDef(key, types, relatedTo, relation, requiredTags, forbiddenTags);
   }

   private static QuestStep parseStep(JsonObject json) {
      int index = GsonHelper.getAsInt(json, "index");
      String villagerKey = GsonHelper.getAsString(json, "villagerKey");
      int duration = GsonHelper.getAsInt(json, "duration", 3);
      boolean showRequiredGoods = GsonHelper.getAsBoolean(json, "showRequiredGoods", true);
      Map<QuestItemRef, Integer> requiredGoods = parseItemRefMap(json, "requiredGoods");
      Map<QuestItemRef, Integer> rewardGoods = parseItemRefMap(json, "rewardGoods");
      int rewardMoney = GsonHelper.getAsInt(json, "rewardMoney", 0);
      int rewardReputation = GsonHelper.getAsInt(json, "rewardReputation", 0);
      int penaltyReputation = GsonHelper.getAsInt(json, "penaltyReputation", 0);
      List<VillagerTagAction> villagerTagsSuccess = parseVillagerTagActions(json, "villagerTagsSuccess");
      List<VillagerTagAction> villagerTagsFailure = parseVillagerTagActions(json, "villagerTagsFailure");
      List<String> playerTagsSuccess = JsonLoaderUtils.parseStringList(json, "playerTagsSuccess");
      List<String> playerTagsFailure = JsonLoaderUtils.parseStringList(json, "playerTagsFailure");
      List<String> globalTagsSuccess = JsonLoaderUtils.parseStringList(json, "globalTagsSuccess");
      List<String> globalTagsFailure = JsonLoaderUtils.parseStringList(json, "globalTagsFailure");
      List<VillagerTagAction> clearTagsSuccess = parseVillagerTagActions(json, "clearTagsSuccess");
      List<VillagerTagAction> clearTagsFailure = parseVillagerTagActions(json, "clearTagsFailure");
      List<String> clearPlayerTagsSuccess = JsonLoaderUtils.parseStringList(json, "clearPlayerTagsSuccess");
      List<String> clearPlayerTagsFailure = JsonLoaderUtils.parseStringList(json, "clearPlayerTagsFailure");
      List<String> clearGlobalTagsSuccess = JsonLoaderUtils.parseStringList(json, "clearGlobalTagsSuccess");
      List<String> clearGlobalTagsFailure = JsonLoaderUtils.parseStringList(json, "clearGlobalTagsFailure");
      List<String> stepRequiredGlobalTags = JsonLoaderUtils.parseStringList(json, "stepRequiredGlobalTags");
      List<String> stepForbiddenGlobalTags = JsonLoaderUtils.parseStringList(json, "stepForbiddenGlobalTags");
      List<String> stepRequiredPlayerTags = JsonLoaderUtils.parseStringList(json, "stepRequiredPlayerTags");
      List<String> stepForbiddenPlayerTags = JsonLoaderUtils.parseStringList(json, "stepForbiddenPlayerTags");
      List<ActionDataEntry> actionDataSuccess = parseActionDataEntries(json, "actionDataSuccess");
      List<RelationChange> relationChanges = parseRelationChanges(json, "relationChanges");
      List<BedrockBuilding> bedrockBuildings = parseBedrockBuildings(json, "bedrockBuildings");
      Map<String, String> labels = parseStringStringMap(json, "labels");
      Map<String, String> descriptions = parseStringStringMap(json, "descriptions");
      Map<String, String> descriptionsSuccess = parseStringStringMap(json, "descriptionsSuccess");
      Map<String, String> descriptionsRefuse = parseStringStringMap(json, "descriptionsRefuse");
      Map<String, String> descriptionsTimeUp = parseStringStringMap(json, "descriptionsTimeUp");
      Map<String, String> listings = parseStringStringMap(json, "listings");
      return new QuestStep(
         index,
         villagerKey,
         duration,
         showRequiredGoods,
         requiredGoods,
         rewardGoods,
         rewardMoney,
         rewardReputation,
         penaltyReputation,
         villagerTagsSuccess,
         villagerTagsFailure,
         playerTagsSuccess,
         playerTagsFailure,
         globalTagsSuccess,
         globalTagsFailure,
         clearTagsSuccess,
         clearTagsFailure,
         clearPlayerTagsSuccess,
         clearPlayerTagsFailure,
         clearGlobalTagsSuccess,
         clearGlobalTagsFailure,
         stepRequiredGlobalTags,
         stepForbiddenGlobalTags,
         stepRequiredPlayerTags,
         stepForbiddenPlayerTags,
         actionDataSuccess,
         relationChanges,
         bedrockBuildings,
         labels,
         descriptions,
         descriptionsSuccess,
         descriptionsRefuse,
         descriptionsTimeUp,
         listings
      );
   }

   private static Map<QuestItemRef, Integer> parseItemRefMap(JsonObject parent, String key) {
      Map<QuestItemRef, Integer> map = new LinkedHashMap<>();
      if (!parent.has(key)) {
         return map;
      }

      JsonObject obj = GsonHelper.getAsJsonObject(parent, key);

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         String itemKey = entry.getKey();
         int count = entry.getValue().getAsInt();
         int meta = 0;
         if (itemKey.contains("|")) {
            String[] parts = itemKey.split("\\|");
            itemKey = parts[0];
            meta = Integer.parseInt(parts[1]);
         }

         map.put(new QuestItemRef(itemKey, meta), count);
      }

      return map;
   }

   private static List<VillagerTagAction> parseVillagerTagActions(JsonObject parent, String key) {
      List<VillagerTagAction> list = new ArrayList<>();
      if (!parent.has(key)) {
         return list;
      }

      for (JsonElement e : parent.getAsJsonArray(key)) {
         JsonObject obj = e.getAsJsonObject();
         list.add(new VillagerTagAction(GsonHelper.getAsString(obj, "villagerKey"), GsonHelper.getAsString(obj, "tag")));
      }

      return list;
   }

   private static List<ActionDataEntry> parseActionDataEntries(JsonObject parent, String key) {
      List<ActionDataEntry> list = new ArrayList<>();
      if (!parent.has(key)) {
         return list;
      }

      for (JsonElement e : parent.getAsJsonArray(key)) {
         JsonObject obj = e.getAsJsonObject();
         list.add(new ActionDataEntry(GsonHelper.getAsString(obj, "key"), GsonHelper.getAsString(obj, "value")));
      }

      return list;
   }

   private static List<RelationChange> parseRelationChanges(JsonObject parent, String key) {
      List<RelationChange> list = new ArrayList<>();
      if (!parent.has(key)) {
         return list;
      }

      for (JsonElement e : parent.getAsJsonArray(key)) {
         JsonObject obj = e.getAsJsonObject();
         list.add(
            new RelationChange(GsonHelper.getAsString(obj, "firstVillager"), GsonHelper.getAsString(obj, "secondVillager"), GsonHelper.getAsInt(obj, "change"))
         );
      }

      return list;
   }

   private static List<BedrockBuilding> parseBedrockBuildings(JsonObject parent, String key) {
      List<BedrockBuilding> list = new ArrayList<>();
      if (!parent.has(key)) {
         return list;
      }

      for (JsonElement e : parent.getAsJsonArray(key)) {
         JsonObject obj = e.getAsJsonObject();
         list.add(new BedrockBuilding(GsonHelper.getAsString(obj, "culture"), GsonHelper.getAsString(obj, "villageType")));
      }

      return list;
   }

   private static Map<String, String> parseStringStringMap(JsonObject parent, String key) {
      Map<String, String> map = new HashMap<>();
      if (!parent.has(key)) {
         return map;
      }

      JsonObject obj = GsonHelper.getAsJsonObject(parent, key);

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         map.put(entry.getKey(), entry.getValue().getAsString());
      }

      return map;
   }

   private static void loadQuestTexts() {
      for (String lang : discoverQuestLanguages()) {
         Map<String, String> texts = new HashMap<>();
         ContentFs root = CustomContentIndex.current().root();
         String basePath = "quests/lang/" + lang + ".json";

         for (Resource res : root.findAll(basePath)) {
            if (res.kind() == SourceKind.CLASSPATH) {
               mergePut(res, texts);
               break;
            }
         }

         for (Resource res : root.findAll(basePath)) {
            if (res.kind() == SourceKind.STANDARD) {
               mergePut(res, texts);
            }
         }

         int baseCount = texts.size();
         String customPath = "languages/" + lang + "/quest_lang.json";
         List<Resource> custom = new ArrayList<>(root.findAll(customPath));
         Collections.reverse(custom);
         Set<String> customSeen = new HashSet<>();

         for (Resource res : custom) {
            if (res.kind() != SourceKind.CLASSPATH) {
               if (res.kind() == SourceKind.STANDARD) {
                  mergePut(res, texts);
               } else {
                  mergeCustomFirstAlpha(res, texts, customSeen, lang);
               }
            }
         }

         if (!texts.isEmpty()) {
            QuestTextRegistry.register(lang, texts);
            int externalCount = texts.size() - baseCount;
            if (externalCount > 0) {
               LOGGER.debug("Quest texts loaded: {} ({} entries, {} from overlay)", new Object[]{lang, texts.size(), externalCount});
            } else {
               LOGGER.debug("Quest texts loaded: {} ({} entries)", lang, texts.size());
            }
         }
      }
   }

   private static void mergePut(Resource res, Map<String, String> texts) {
      try (
         InputStream is = res.open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         if (json != null) {
            for (Entry<String, JsonElement> entry : json.entrySet()) {
               texts.put(entry.getKey(), entry.getValue().getAsString());
            }
         }
      } catch (Exception e) {
         LOGGER.error("Error loading quest texts {} from {}: {}", new Object[]{res.relPath(), res.source().displayName(), e.getMessage(), e});
      }
   }

   private static void mergeCustomFirstAlpha(Resource res, Map<String, String> texts, Set<String> customSeen, String lang) {
      try (
         InputStream is = res.open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         if (json != null) {
            for (Entry<String, JsonElement> entry : json.entrySet()) {
               String key = entry.getKey();
               String value = entry.getValue().getAsString();
               if (customSeen.add(key)) {
                  texts.put(key, value);
               } else if (WARNED_TEXT_SHADOW.add("quest_text:" + lang + ":" + key)) {
                  LOGGER.warn("Quest text key '{}' ({}) in {} shadowed by earlier sub-mod", new Object[]{key, lang, res.source().displayName()});
               }
            }
         }
      } catch (Exception e) {
         LOGGER.error("Error loading quest texts {} from {}: {}", new Object[]{res.relPath(), res.source().displayName(), e.getMessage(), e});
      }
   }

   static String[] discoverQuestLanguages() {
      String[] cached = cachedLanguages;
      if (cached != null) {
         return cached;
      }

      LinkedHashSet<String> languages = new LinkedHashSet<>();
      boolean fromManifest = readQuestLanguagesManifest(languages);
      if (!fromManifest && languages.isEmpty()) {
         for (String lang : FALLBACK_LANGUAGES) {
            languages.add(lang);
         }
      }

      addSubmodQuestLanguages(languages);
      String[] out = languages.toArray(new String[0]);
      cachedLanguages = out;
      return out;
   }

   private static boolean readQuestLanguagesManifest(LinkedHashSet<String> out) {
      ContentFs root = CustomContentIndex.current().root();
      Resource manifest = null;

      for (Resource res : root.findAll("quests/lang/_manifest.json")) {
         if (res.kind() == SourceKind.CLASSPATH) {
            manifest = res;
            break;
         }
      }

      if (manifest == null) {
         LOGGER.warn("Quest language manifest not found at {}/lang/_manifest.json — falling back to built-in list", "quests");
         return false;
      }

      try (
         InputStream is = manifest.open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonObject obj = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         JsonArray arr = obj != null ? obj.getAsJsonArray("languages") : null;
         if (arr == null || arr.isEmpty()) {
            LOGGER.warn("Quest language manifest has no 'languages' array — falling back to built-in list");
            return false;
         }

         for (JsonElement e : arr) {
            out.add(e.getAsString());
         }

         return true;
      } catch (Exception e) {
         LOGGER.warn("Error reading quest language manifest: {} — falling back to built-in list", e.getMessage());
         return false;
      }
   }

   private static void addSubmodQuestLanguages(LinkedHashSet<String> out) {
      ContentFs languagesFs = CustomContentIndex.current().forGlobalContent("languages");
      languagesFs.walk("", 1).forEach(res -> {
         String relPath = res.relPath();
         String tail = relPath.substring("languages/".length());
         int slash = tail.indexOf(47);
         if (slash > 0) {
            String filename = tail.substring(slash + 1);
            if ("quest_lang.json".equals(filename)) {
               String lang = tail.substring(0, slash);
               if (!lang.startsWith("_") && !"native".equals(lang)) {
                  out.add(lang);
               }
            }
         }
      });
   }

   static List<String> discoverCultureQuests(String culture) {
      ContentFs cultureFs = CustomContentIndex.current().forCulture(culture);
      String typeRootPrefix = ("cultures/" + culture + "/quests/").toLowerCase(Locale.ROOT);
      List<String> rels = new ArrayList<>();
      cultureFs.walk("quests", Integer.MAX_VALUE).forEach(res -> {
         if (res.kind() == SourceKind.CLASSPATH) {
            String full = res.relPath();
            if (full.endsWith(".json")) {
               String filename = full.substring(full.lastIndexOf(47) + 1);
               if (!filename.startsWith("_")) {
                  if (full.startsWith(typeRootPrefix)) {
                     String inside = full.substring(typeRootPrefix.length());
                     rels.add(inside.substring(0, inside.length() - 5));
                  }
               }
            }
         }
      });
      rels.sort(String::compareTo);
      return rels;
   }
}
