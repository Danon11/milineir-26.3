package org.millenaire.content.legacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;

final class TagConsistencyChecker {
   static final Set<String> HARDCODED_BUILDING_TAGS = Set.of(
      "archives",
      "autospawnvillagers",
      "borderpostsign",
      "brickkiln",
      "cattle",
      "chicken",
      "despawnallmobs",
      "fishingspot",
      "grove",
      "hof",
      "inn",
      "leasure",
      "leisure",
      "market",
      "marvel",
      "no_upgrade_till_wall_initialized",
      "nopaths",
      "pathnode",
      "pigs",
      "pujas",
      "sacrifices",
      "scaffoldings",
      "sheeps",
      "silkwormfarm",
      "snailsfarm",
      "sugarplantation"
   );
   static final Set<String> HARDCODED_VILLAGER_TAGS = Set.of(
      "archer",
      "chief",
      "child",
      "defensive",
      "foreignmerchant",
      "helpinattacks",
      "hidename",
      "hostile",
      "localmerchant",
      "meditates",
      "noleafclearing",
      "noresurrect",
      "noteleport",
      "performssacrifices",
      "raider",
      "seller",
      "showhealth",
      "visitor"
   );
   private static final Pattern BUILDING_PREFIX_RE = Pattern.compile("^(?:building|initial|upgrade\\d+)\\.");

   private TagConsistencyChecker() {
   }

   static void check(String culture, Path sourceRoot, LegacyLayoutDetector.Detection detection, Map<String, Path> goalIndex, LegacyConversionReport report) {
      Set<String> buildingTags = new HashSet<>();

      for (Path bf : onlyCulture(detection.buildingTxts(), culture)) {
         for (Entry<String, List<String>> e : safeParseKv(bf).entrySet()) {
            String bare = BUILDING_PREFIX_RE.matcher(e.getKey()).replaceFirst("");
            if (bare.equals("tag") || bare.equals("villagetag")) {
               for (String value : e.getValue()) {
                  for (String t : value.split(",")) {
                     String tok = t.trim().toLowerCase(Locale.ROOT);
                     if (!tok.isEmpty()) {
                        buildingTags.add(tok);
                     }
                  }
               }
            }
         }
      }

      Set<String> villagerTags = new HashSet<>();

      for (Path vf : onlyCulture(detection.villagerTxts(), culture)) {
         for (Entry<String, List<String>> e : safeParseKv(vf).entrySet()) {
            if (e.getKey().equals("tag")) {
               for (String value : e.getValue()) {
                  String tok = value.trim().toLowerCase(Locale.ROOT);
                  if (!tok.isEmpty()) {
                     villagerTags.add(tok);
                  }
               }
            }
         }
      }

      for (Path qf : detection.questTxts()) {
         if (questBelongsToCulture(qf, culture)) {
            try {
               for (String raw : Files.readAllLines(qf)) {
                  String line = raw.trim();
                  if (!line.isEmpty() && !line.startsWith("//")) {
                     int colon = line.indexOf(58);
                     if (colon > 0) {
                        String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                        if (key.equals("settagsuccess") || key.equals("settagfailure") || key.equals("settag")) {
                           String value = line.substring(colon + 1).trim();
                           String[] parts = value.split(",");
                           if (parts.length >= 2) {
                              villagerTags.add(parts[1].trim().toLowerCase(Locale.ROOT));
                           }
                        }
                     }
                  }
               }
            } catch (IOException var26) {
            }
         }
      }

      Set<String> usedGoalNames = new HashSet<>();

      for (Path vf : onlyCulture(detection.villagerTxts(), culture)) {
         for (Entry<String, List<String>> e : safeParseKv(vf).entrySet()) {
            if (e.getKey().equals("goal")) {
               for (String value : e.getValue()) {
                  String tok = value.trim().toLowerCase(Locale.ROOT);
                  if (!tok.isEmpty()) {
                     usedGoalNames.add(tok);
                  }
               }
            }
         }
      }

      Map<String, String> requiredBuildingTags = new HashMap<>();

      for (String goalName : usedGoalNames) {
         Path gf = goalIndex.get(goalName);
         if (gf != null) {
            for (Entry<String, List<String>> e : safeParseKv(gf).entrySet()) {
               if (e.getKey().equals("buildingtag")) {
                  for (String value : e.getValue()) {
                     String tag = value.trim().toLowerCase(Locale.ROOT);
                     if (!tag.isEmpty() && !HARDCODED_BUILDING_TAGS.contains(tag) && !tag.startsWith("wall_level")) {
                        requiredBuildingTags.putIfAbsent(tag, gf.getFileName().toString());
                     }
                  }
               }
            }
         }
      }

      Map<String, String> requiredVillagerTags = new HashMap<>();

      for (Path qf : detection.questTxts()) {
         if (questBelongsToCulture(qf, culture)) {
            try {
               for (String raw : Files.readAllLines(qf)) {
                  String line = raw.trim();
                  if (!line.isEmpty() && !line.startsWith("//")) {
                     int colon = line.indexOf(58);
                     if (colon > 0) {
                        String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                        if (key.equals("definevillager")) {
                           String value = line.substring(colon + 1).trim();
                           Map<String, String> vparams = new HashMap<>();

                           for (String seg : value.split(",")) {
                              int eq = seg.indexOf(61);
                              if (eq > 0) {
                                 vparams.put(seg.substring(0, eq).trim().toLowerCase(Locale.ROOT), seg.substring(eq + 1).trim().toLowerCase(Locale.ROOT));
                              }
                           }

                           String tag = vparams.get("requiredtag");
                           if (tag != null && !tag.isEmpty() && !HARDCODED_VILLAGER_TAGS.contains(tag)) {
                              requiredVillagerTags.putIfAbsent(tag, qf.getFileName().toString());
                           }
                        }
                     }
                  }
               }
            } catch (IOException var25) {
            }
         }
      }

      List<String> sortedBuilding = new ArrayList<>(requiredBuildingTags.keySet());
      sortedBuilding.sort(String::compareTo);

      for (String tag : sortedBuilding) {
         if (!buildingTags.contains(tag)) {
            report.recordUnresolvedBuildingTag(culture, tag, requiredBuildingTags.get(tag));
         }
      }

      List<String> sortedVillager = new ArrayList<>(requiredVillagerTags.keySet());
      sortedVillager.sort(String::compareTo);

      for (String tag : sortedVillager) {
         if (!villagerTags.contains(tag)) {
            report.recordUnresolvedVillagerTag(culture, tag, requiredVillagerTags.get(tag));
         }
      }
   }

   static boolean questBelongsToCulture(Path qf, String culture) {
      String cultureLow = culture.toLowerCase(Locale.ROOT);
      String pathCulture = LegacyLayoutDetector.Detection.cultureOf(qf);
      if (pathCulture != null) {
         return cultureLow.equals(pathCulture);
      }

      for (int i = 0; i < qf.getNameCount() - 1; i++) {
         if ("quests".equalsIgnoreCase(qf.getName(i).toString()) && i + 1 < qf.getNameCount()) {
            String family = qf.getName(i + 1).toString().toLowerCase(Locale.ROOT);
            if (family.contains(cultureLow)) {
               return true;
            }
            break;
         }
      }

      try {
         for (String raw : Files.readAllLines(qf)) {
            String line = raw.trim();
            if (!line.isEmpty() && !line.startsWith("//")) {
               int colon = line.indexOf(58);
               if (colon > 0 && line.substring(0, colon).trim().toLowerCase(Locale.ROOT).equals("definevillager")) {
                  String value = line.substring(colon + 1);

                  for (String seg : value.split(",")) {
                     int eq = seg.indexOf(61);
                     if (eq > 0) {
                        String k = seg.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                        if (k.equals("type")) {
                           String typeVal = seg.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
                           int slash = typeVal.indexOf(47);
                           if (slash > 0 && typeVal.substring(0, slash).equals(cultureLow)) {
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      } catch (IOException var17) {
      }

      return false;
   }

   private static Map<String, List<String>> safeParseKv(Path file) {
      try {
         return LegacyDataParser.parseKeyValues(file);
      } catch (IOException io) {
         return Map.of();
      }
   }

   private static List<Path> onlyCulture(List<Path> paths, String culture) {
      List<Path> out = new ArrayList<>();

      for (Path p : paths) {
         String c = LegacyLayoutDetector.Detection.cultureOf(p);
         if (culture.equalsIgnoreCase(c)) {
            out.add(p);
         }
      }

      return out;
   }
}
