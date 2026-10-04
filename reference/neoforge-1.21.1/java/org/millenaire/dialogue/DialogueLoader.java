package org.millenaire.dialogue;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceKind;
import org.slf4j.Logger;

public final class DialogueLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<String, Map<ResourceLocation, Map<String, Dialogue>>> ALL_DIALOGUES = new ConcurrentHashMap<>();
   private static final Set<String> WARNED_REPLACE_IDS = ConcurrentHashMap.newKeySet();

   private DialogueLoader() {
   }

   public static void loadDialogues(String cultureName, String lang) {
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", cultureName);
      String relPath = lang + "/" + cultureName + "_dialogues.txt";
      ContentFs languagesFs = CustomContentIndex.current().forGlobalContent("languages");
      List<Resource> all = languagesFs.findAll(relPath);
      Resource base = null;
      List<Resource> overlays = new ArrayList<>();

      for (Resource res : all) {
         if (res.kind() == SourceKind.SUBMOD) {
            overlays.add(res);
         } else if (base == null) {
            base = res;
         }
      }

      Collections.reverse(overlays);
      int baseCount = 0;
      Set<String> overlayKeysFromPriorSubmods = new HashSet<>();
      if (base != null) {
         try (InputStream is = base.open()) {
            baseCount = parseDialogueStream(is, cultureId, lang, base.relPath(), false, null, null);
         } catch (Exception e) {
            LOGGER.error("Error loading dialogues {}: {}", base.relPath(), e.getMessage());
         }
      } else {
         String classpathPath = "/millenaire/languages/" + relPath;

         try (InputStream is = DialogueLoader.class.getResourceAsStream(classpathPath)) {
            if (is != null) {
               baseCount = parseDialogueStream(is, cultureId, lang, classpathPath, false, null, null);
            } else {
               LOGGER.debug("No dialogue file found: languages/{}", relPath);
            }
         } catch (Exception e) {
            LOGGER.error("Error loading dialogues {}: {}", classpathPath, e.getMessage());
         }
      }

      int externalCount = 0;

      for (Resource ext : overlays) {
         Set<String> priorForThisFile = Set.copyOf(overlayKeysFromPriorSubmods);

         try (InputStream extIs = ext.open()) {
            int added = parseDialogueStream(
               extIs, cultureId, lang, ext.source().displayName() + ":" + ext.relPath(), true, priorForThisFile, overlayKeysFromPriorSubmods
            );
            externalCount += added;
         } catch (Exception e) {
            LOGGER.error("Error loading external dialogues {}: {}", ext.relPath(), e.getMessage());
         }
      }

      int total = baseCount + externalCount;
      if (total > 0) {
         if (externalCount > 0) {
            LOGGER.info(
               "Dialogues loaded for {} ({}): {} conversations (base={}, custom new={})", new Object[]{cultureName, lang, total, baseCount, externalCount}
            );
         } else {
            LOGGER.debug("Dialogues loaded for {} ({}): {} conversations", new Object[]{cultureName, lang, total});
         }
      }
   }

   private static int parseDialogueStream(
      InputStream is,
      ResourceLocation cultureId,
      String lang,
      String sourceLabel,
      boolean isOverlay,
      @Nullable Set<String> priorSubmodKeys,
      @Nullable Set<String> recordNewKeysInto
   ) {
      try {
         Map<ResourceLocation, Map<String, Dialogue>> byCulture = ALL_DIALOGUES.computeIfAbsent(lang, k -> new ConcurrentHashMap<>());
         Map<String, Dialogue> byKey = byCulture.computeIfAbsent(cultureId, k -> new ConcurrentHashMap<>());
         String currentKey = null;
         int currentWeight = 10;
         List<String> currentTags = new ArrayList<>();
         List<String> v1Parts = new ArrayList<>();
         List<String> v2Parts = new ArrayList<>();
         List<Dialogue.Line> currentLines = new ArrayList<>();
         List<String> currentBuildings = new ArrayList<>();
         List<String> currentNotBuildings = new ArrayList<>();
         List<String> currentVillagers = new ArrayList<>();
         List<String> currentNotVillagers = new ArrayList<>();
         List<String> currentRelations = new ArrayList<>();
         List<String> currentNotRelations = new ArrayList<>();
         int count = 0;

         try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty() && !line.startsWith("//")) {
                  if (!line.startsWith("newchat;")) {
                     if (line.startsWith("v1;") || line.startsWith("v2;")) {
                        int speaker = line.charAt(1) - '0';
                        String rest = line.substring(3);
                        int semiIdx = rest.indexOf(59);
                        if (semiIdx > 0) {
                           int delay = Integer.parseInt(rest.substring(0, semiIdx).trim());
                           String text = rest.substring(semiIdx + 1);
                           currentLines.add(new Dialogue.Line(speaker, delay, text));
                        }
                     }
                  } else {
                     if (currentKey != null && !currentLines.isEmpty()) {
                        count += registerDialogue(
                           byKey,
                           currentKey,
                           currentWeight,
                           currentTags,
                           v1Parts,
                           v2Parts,
                           currentLines,
                           cultureId,
                           currentBuildings,
                           currentNotBuildings,
                           currentVillagers,
                           currentNotVillagers,
                           currentRelations,
                           currentNotRelations,
                           lang,
                           isOverlay,
                           priorSubmodKeys,
                           recordNewKeysInto,
                           sourceLabel
                        );
                     }

                     currentKey = null;
                     currentWeight = 10;
                     currentTags = new ArrayList<>();
                     v1Parts = new ArrayList<>();
                     v2Parts = new ArrayList<>();
                     currentLines = new ArrayList<>();
                     currentBuildings = new ArrayList<>();
                     currentNotBuildings = new ArrayList<>();
                     currentVillagers = new ArrayList<>();
                     currentNotVillagers = new ArrayList<>();
                     currentRelations = new ArrayList<>();
                     currentNotRelations = new ArrayList<>();
                     String params = line.substring("newchat;".length());
                     if (params.endsWith(";")) {
                        params = params.substring(0, params.length() - 1);
                     }

                     for (String param : params.split(",")) {
                        param = param.trim();
                        if (param.startsWith("key:")) {
                           currentKey = param.substring(4).toLowerCase();
                        } else if (param.startsWith("weigth:") || param.startsWith("weight:")) {
                           currentWeight = Integer.parseInt(param.substring(param.indexOf(58) + 1).trim());
                        } else if (param.startsWith("tag:")) {
                           currentTags.add(param.substring(4));
                        } else if (param.startsWith("v1:")) {
                           v1Parts.add(param.substring(3));
                        } else if (param.startsWith("v2:")) {
                           v2Parts.add(param.substring(3));
                        } else if (param.startsWith("notbuilding:")) {
                           currentNotBuildings.add(param.substring("notbuilding:".length()));
                        } else if (param.startsWith("building:")) {
                           currentBuildings.add(param.substring("building:".length()));
                        } else if (param.startsWith("notvillager:")) {
                           currentNotVillagers.add(param.substring("notvillager:".length()));
                        } else if (param.startsWith("villager:")) {
                           currentVillagers.add(param.substring("villager:".length()));
                        } else if (param.startsWith("notrel:")) {
                           currentNotRelations.add(param.substring("notrel:".length()));
                        } else if (param.startsWith("rel:")) {
                           currentRelations.add(param.substring("rel:".length()));
                        }
                     }
                  }
               }
            }

            if (currentKey != null && !currentLines.isEmpty()) {
               count += registerDialogue(
                  byKey,
                  currentKey,
                  currentWeight,
                  currentTags,
                  v1Parts,
                  v2Parts,
                  currentLines,
                  cultureId,
                  currentBuildings,
                  currentNotBuildings,
                  currentVillagers,
                  currentNotVillagers,
                  currentRelations,
                  currentNotRelations,
                  lang,
                  isOverlay,
                  priorSubmodKeys,
                  recordNewKeysInto,
                  sourceLabel
               );
            }
         }

         return count;
      } catch (Exception e) {
         LOGGER.error("Error parsing dialogues {}: {}", sourceLabel, e.getMessage());
         return 0;
      }
   }

   private static int registerDialogue(
      Map<String, Dialogue> byKey,
      String key,
      int weight,
      List<String> tags,
      List<String> v1Parts,
      List<String> v2Parts,
      List<Dialogue.Line> lines,
      ResourceLocation cultureId,
      List<String> buildings,
      List<String> notBuildings,
      List<String> villagers,
      List<String> notVillagers,
      List<String> relations,
      List<String> notRelations,
      String lang,
      boolean isOverlay,
      @Nullable Set<String> priorSubmodKeys,
      @Nullable Set<String> recordNewKeysInto,
      String sourceLabel
   ) {
      if (!byKey.containsKey(key)) {
         String v1 = v1Parts.isEmpty() ? null : String.join(",", v1Parts);
         String v2 = v2Parts.isEmpty() ? null : String.join(",", v2Parts);
         Dialogue dialogue = new Dialogue(
            key,
            weight,
            List.copyOf(tags),
            v1,
            v2,
            List.copyOf(lines),
            cultureId,
            List.copyOf(buildings),
            List.copyOf(notBuildings),
            List.copyOf(villagers),
            List.copyOf(notVillagers),
            List.copyOf(relations),
            List.copyOf(notRelations)
         );
         byKey.put(key, dialogue);
         if (recordNewKeysInto != null) {
            recordNewKeysInto.add(key);
         }

         return 1;
      } else {
         if (isOverlay) {
            if (priorSubmodKeys != null && priorSubmodKeys.contains(key)) {
               if (WARNED_REPLACE_IDS.add("dialogue:" + lang + ":" + cultureId + ":" + key)) {
                  LOGGER.warn(
                     "Custom dialogue key '{}' in {} ({}) shadowed by earlier sub-mod (cross-pack REPLACE conflict; source={}) — skipping",
                     new Object[]{key, lang, cultureId, sourceLabel}
                  );
               }
            } else {
               LOGGER.debug(
                  "Custom dialogue key '{}' in {} ({}) shadowed by base or within-file (speechRef protocol invariant; source={}) — skipping",
                  new Object[]{key, lang, cultureId, sourceLabel}
               );
            }
         } else {
            LOGGER.warn("Duplicate dialogue key '{}' in {} for culture {} — skipping", new Object[]{key, lang, cultureId});
         }

         return 0;
      }
   }

   public static Set<String> getLoadedLanguages() {
      return Set.copyOf(ALL_DIALOGUES.keySet());
   }

   public static List<Dialogue> getDialogues(ResourceLocation culture, String lang) {
      Map<ResourceLocation, Map<String, Dialogue>> byLang = ALL_DIALOGUES.get(lang);
      if (byLang == null) {
         return List.of();
      }

      Map<String, Dialogue> byCulture = byLang.get(culture);
      return byCulture == null ? List.of() : List.copyOf(byCulture.values());
   }

   @Nullable
   public static String getDialogueLine(ResourceLocation culture, String lang, String dialogueKey, int lineIdx) {
      Map<ResourceLocation, Map<String, Dialogue>> byLang = ALL_DIALOGUES.get(lang);
      if (byLang == null) {
         return null;
      } else {
         Map<String, Dialogue> byCulture = byLang.get(culture);
         if (byCulture == null) {
            return null;
         } else {
            Dialogue dialogue = byCulture.get(dialogueKey);
            if (dialogue == null) {
               return null;
            } else if (lineIdx >= 0 && lineIdx < dialogue.lines().size()) {
               String text = dialogue.lines().get(lineIdx).text();
               return text != null && !text.isEmpty() ? text : null;
            } else {
               return null;
            }
         }
      }
   }

   public static void clear() {
      ALL_DIALOGUES.clear();
      WARNED_REPLACE_IDS.clear();
   }
}
