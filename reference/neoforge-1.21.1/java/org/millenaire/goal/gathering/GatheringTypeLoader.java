package org.millenaire.goal.gathering;

import com.google.gson.Gson;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.fml.ModList;
import org.millenaire.content.ContentDirectoryManager;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceKind;
import org.millenaire.culture.JsonLoaderUtils;
import org.millenaire.goal.GoalRegistry;
import org.slf4j.Logger;

public final class GatheringTypeLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final String GATHERING_TYPE_DIR = "gathering_type";
   private static final String JSON_EXT = ".json";
   private static final Set<String> WARNED_REPLACE_IDS = ConcurrentHashMap.newKeySet();

   public static void resetForTesting() {
      WARNED_REPLACE_IDS.clear();
   }

   private GatheringTypeLoader() {
   }

   public static void loadAll(GoalRegistry goalRegistry) {
      ContentFs fs = jarFs();
      Set<String> disabledIds = readDisabledIds(fs);
      int count = 0;

      for (Resource res : fs.walk("", 1).filter(r -> r.kind() == SourceKind.CLASSPATH).toList()) {
         String filename = leafName(res.relPath());
         if (filename != null && !filename.startsWith("_") && filename.endsWith(".json")) {
            String id = filename.substring(0, filename.length() - ".json".length());
            if (disabledIds.contains(id)) {
               LOGGER.debug("Skipping JAR gathering type '{}' (listed in _disabled.json)", id);
            } else {
               GatheringType type = parseFromResource(res, id);
               if (type != null) {
                  GatheringHandler handler = GatheringHandlerRegistry.get(type.handlerId());
                  if (handler == null) {
                     LOGGER.error("Unknown handler '{}' for gathering type {}", type.handlerId(), type.id());
                  } else {
                     GatheringGoal goal = new GatheringGoal(type, handler);
                     goalRegistry.register(goal);
                     count++;
                     LOGGER.debug("GatheringType loaded: {} (handler={})", type.id(), type.handlerId());
                  }
               }
            }
         }
      }

      LOGGER.info("{} gathering types loaded", count);
   }

   public static void loadExternal(GoalRegistry goalRegistry) {
      WARNED_REPLACE_IDS.clear();
      ContentFs fs = CustomContentIndex.current().forGlobalContent("gathering_type");
      Set<String> disabledIds = readDisabledIds(fs);
      List<Resource> entries = fs.walk("", 1).filter(r -> r.kind() != SourceKind.CLASSPATH).toList();
      if (!entries.isEmpty() || !disabledIds.isEmpty()) {
         int added = 0;
         int overridden = 0;

         for (Resource res : entries) {
            String filename = leafName(res.relPath());
            if (filename != null && !filename.startsWith("_") && filename.endsWith(".json")) {
               String id = filename.substring(0, filename.length() - ".json".length());
               if (!disabledIds.contains(id)) {
                  GatheringType type = parseFromResource(res, id);
                  if (type != null) {
                     GatheringHandler handler = GatheringHandlerRegistry.get(type.handlerId());
                     if (handler == null) {
                        LOGGER.error("External gathering type {} references unknown handler '{}' — skipping", type.id(), type.handlerId());
                     } else {
                        GatheringGoal goal = new GatheringGoal(type, handler);
                        boolean wasPresent = goalRegistry.get(type.id()) != null;
                        goalRegistry.replace(goal);
                        if (wasPresent) {
                           overridden++;
                        } else {
                           added++;
                        }
                     }
                  }
               }
            }
         }

         int skipped = disabledIds.size();
         if (added + overridden + skipped > 0) {
            LOGGER.info("External gathering types: {} added, {} overridden, {} disabled", new Object[]{added, overridden, skipped});
         }
      }
   }

   public static void validateAll(GoalRegistry goalRegistry) {
      Pattern unresolvablePattern = Pattern.compile("unresolvable (?:item|block) (?:'([^']+)'|in '[^']+': (\\S+))");
      Map<String, List<String>> foreignByNs = new LinkedHashMap<>();
      List<String> internalErrors = new ArrayList<>();
      int errorCount = 0;

      for (GatheringGoal goal : goalRegistry.getGatheringGoals()) {
         GatheringType type = goal.getGatheringType();
         GatheringHandler handler = goal.getHandler();

         for (String error : handler.validate(type)) {
            errorCount++;
            Matcher m = unresolvablePattern.matcher(error);
            String namespace = null;
            if (m.find()) {
               String id = m.group(1) != null ? m.group(1) : m.group(2);
               int colon = id.indexOf(58);
               namespace = colon > 0 ? id.substring(0, colon) : "minecraft";
            }

            if (namespace != null && !isKnownNamespace(namespace)) {
               foreignByNs.computeIfAbsent(namespace, k -> new ArrayList<>()).add(type.id() + " (handler=" + type.handlerId() + "): " + error);
            } else {
               LOGGER.error("Gathering type {} (handler={}): {}", new Object[]{type.id(), type.handlerId(), error});
               internalErrors.add(type.id().toString());
            }
         }
      }

      for (Entry<String, List<String>> e : foreignByNs.entrySet()) {
         String ns = e.getKey();
         List<String> details = e.getValue();
         LOGGER.warn(
            "Gathering types reference {} unresolvable id(s) from namespace '{}:' — likely an uninstalled dependency mod. Affected goals will be inactive. Enable DEBUG on GatheringTypeLoader for the full list.",
            details.size(),
            ns
         );

         for (String d : details) {
            LOGGER.debug("  - {}", d);
         }
      }

      if (errorCount > 0) {
         int foreignCount = errorCount - internalErrors.size();
         LOGGER.error(
            "{} validation error(s) found in gathering types — affected goals will malfunction (internal: {}, foreign-namespace: {})",
            new Object[]{errorCount, internalErrors.size(), foreignCount}
         );
      }
   }

   private static boolean isKnownNamespace(String namespace) {
      if (!"minecraft".equals(namespace) && !"millenaire".equals(namespace)) {
         try {
            return ModList.get().isLoaded(namespace);
         } catch (Throwable t) {
            return false;
         }
      } else {
         return true;
      }
   }

   public static List<String> discoverGatheringTypes() {
      ContentFs fs = jarFs();
      List<String> names = new ArrayList<>();
      fs.walk("", 1).filter(r -> r.kind() == SourceKind.CLASSPATH).forEach(r -> {
         String filename = leafName(r.relPath());
         if (filename != null && !filename.startsWith("_")) {
            if (filename.endsWith(".json")) {
               names.add(filename.substring(0, filename.length() - ".json".length()));
            }
         }
      });
      if (names.isEmpty()) {
         LOGGER.warn("No gathering_type files found via ContentFs overlay");
      }

      Collections.sort(names);
      return names;
   }

   private static ContentFs jarFs() {
      if (!ContentDirectoryManager.isInitialized()) {
         return CustomContentIndex.jarOnly().forGlobalContent("gathering_type");
      }

      ContentFs fs = CustomContentIndex.current().forGlobalContent("gathering_type");
      if (fs.walk("", 1).findAny().isEmpty()) {
         ContentFs jarOnly = CustomContentIndex.jarOnly().forGlobalContent("gathering_type");
         if (jarOnly.walk("", 1).findAny().isPresent()) {
            return jarOnly;
         }
      }

      return fs;
   }

   private static Set<String> readDisabledIds(ContentFs fs) {
      List<Resource> manifests = fs.findAll("_disabled.json");
      if (manifests.isEmpty()) {
         return Collections.emptySet();
      }

      Set<String> out = new HashSet<>();

      for (Resource res : manifests) {
         try (InputStream in = res.open()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonArray()) {
               for (JsonElement element : root.getAsJsonArray()) {
                  if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                     out.add(element.getAsString());
                  }
               }
            } else {
               LOGGER.warn("_disabled.json from {} must be a JSON array; ignoring", res.source().displayName());
            }
         } catch (Exception e) {
            LOGGER.warn("Could not parse _disabled.json from {}: {}", res.source().displayName(), e.getMessage());
         }
      }

      return out;
   }

   @Nullable
   private static GatheringType parseFromResource(Resource res, String filename) {
      try (
         InputStream is = res.open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         return parseGatheringType(filename, json);
      } catch (Exception e) {
         LOGGER.error("Error loading gathering type {} from {}: {}", new Object[]{filename, res.source().displayName(), e.getMessage(), e});
         return null;
      }
   }

   @Nullable
   private static String leafName(String relPath) {
      if (relPath != null && !relPath.isEmpty()) {
         int slash = relPath.lastIndexOf(47);
         return slash < 0 ? relPath : relPath.substring(slash + 1);
      } else {
         return null;
      }
   }

   @Nullable
   private static GatheringType parseGatheringType(String filename, JsonObject json) {
      try {
         ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", filename.toLowerCase(Locale.ROOT));
         String handlerId = GsonHelper.getAsString(json, "handler");
         int priority = GsonHelper.getAsInt(json, "priority", 50);
         int priorityRandom = GsonHelper.getAsInt(json, "priorityRandom", 10);
         int scanRadius = GsonHelper.getAsInt(json, "scanRadius", 32);
         int batchRadius = GsonHelper.getAsInt(json, "batchRadius", 8);
         int maxActionsPerTask = GsonHelper.getAsInt(json, "maxActionsPerTask", 16);
         int actionCooldown = GsonHelper.getAsInt(json, "actionCooldown", 10);
         int stuckTimeout = GsonHelper.getAsInt(json, "stuckTimeout", 4000);
         int arrivalRange = GsonHelper.getAsInt(json, "arrivalRange", 3);
         double walkSpeed = GsonHelper.getAsFloat(json, "walkSpeed", 0.6F);
         Map<String, Integer> villageLimit = null;
         if (json.has("villageLimit")) {
            villageLimit = new HashMap<>();
            JsonObject limitObj = GsonHelper.getAsJsonObject(json, "villageLimit");

            for (String key : limitObj.keySet()) {
               villageLimit.put(key, limitObj.get(key).getAsInt());
            }
         }

         int maxSimultaneousTotal = GsonHelper.getAsInt(json, "maxSimultaneousTotal", -1);
         int minimumHour = GsonHelper.getAsInt(json, "minimumHour", -1);
         int maximumHour = GsonHelper.getAsInt(json, "maximumHour", -1);
         int reoccurDelay = GsonHelper.getAsInt(json, "reoccurDelay", -1);
         JsonObject handlerParams = json.has("handlerParams") ? GsonHelper.getAsJsonObject(json, "handlerParams") : new JsonObject();
         Map<String, Integer> buildingLimit = JsonLoaderUtils.parseStringIntMapOrNull(json, "buildingLimit");
         Map<String, Integer> townhallLimit = JsonLoaderUtils.parseStringIntMapOrNull(json, "townhallLimit");
         Map<String, String> itemsBalance = parseStringStringMap(json, "itemsBalance");
         int maxSimultaneousInBuilding = GsonHelper.getAsInt(json, "maxSimultaneousInBuilding", -1);
         String destinationBuilding = json.has("destinationBuilding") ? GsonHelper.getAsString(json, "destinationBuilding") : null;
         List<String> heldItems = JsonLoaderUtils.parseStringListOrNull(json, "heldItems");
         List<String> heldItemsDestination = JsonLoaderUtils.parseStringListOrNull(json, "heldItemsDestination");
         String sound = json.has("sound") ? GsonHelper.getAsString(json, "sound") : null;
         List<String> priorityInvPenaltyItems = JsonLoaderUtils.parseStringListOrNull(json, "priorityInvPenaltyItems");
         int priorityInvPenaltyBase = GsonHelper.getAsInt(json, "priorityInvPenaltyBase", 0);
         String sentenceKey = json.has("sentenceKey") ? GsonHelper.getAsString(json, "sentenceKey") : null;
         String labelKey = json.has("labelKey") ? GsonHelper.getAsString(json, "labelKey") : null;
         String tag = json.has("tag") ? GsonHelper.getAsString(json, "tag") : null;
         boolean leisure = GsonHelper.getAsBoolean(json, "leisure", false);
         return new GatheringType(
            id,
            handlerId,
            priority,
            priorityRandom,
            scanRadius,
            batchRadius,
            maxActionsPerTask,
            actionCooldown,
            stuckTimeout,
            arrivalRange,
            walkSpeed,
            villageLimit,
            maxSimultaneousTotal,
            minimumHour,
            maximumHour,
            reoccurDelay,
            buildingLimit,
            townhallLimit,
            itemsBalance,
            maxSimultaneousInBuilding,
            destinationBuilding,
            heldItems,
            heldItemsDestination,
            sound,
            priorityInvPenaltyItems,
            priorityInvPenaltyBase,
            handlerParams,
            sentenceKey,
            labelKey,
            tag,
            leisure
         );
      } catch (Exception e) {
         LOGGER.error("Error parsing gathering type {}", filename, e);
         return null;
      }
   }

   @Nullable
   private static Map<String, String> parseStringStringMap(JsonObject json, String key) {
      if (!json.has(key)) {
         return null;
      }

      Map<String, String> map = new HashMap<>();
      JsonObject obj = GsonHelper.getAsJsonObject(json, key);

      for (String k : obj.keySet()) {
         map.put(k, obj.get(k).getAsString());
      }

      return map;
   }
}
