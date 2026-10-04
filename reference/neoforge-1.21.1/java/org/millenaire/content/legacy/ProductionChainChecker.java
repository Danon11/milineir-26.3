package org.millenaire.content.legacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

final class ProductionChainChecker {
   static final Set<String> IGNORE_ORPHAN_OUTPUTS = Set.of(
      "sapling",
      "sapling_oak",
      "oak_sapling",
      "sapling_birch",
      "birch_sapling",
      "sapling_spruce",
      "spruce_sapling",
      "sapling_pine",
      "sapling_jungle",
      "jungle_sapling",
      "sapling_acacia",
      "acacia_sapling",
      "sapling_darkoak",
      "dark_oak_sapling",
      "sapling_cherry",
      "cherry_sapling",
      "wool_lightgray",
      "wool_light_gray",
      "light_gray_wool",
      "wool_pink",
      "pink_wool"
   );
   private static final Set<String> BANDIT_KEYWORDS = Set.of("bandit", "felon", "raider");
   static final Set<String> WORLD_AVAILABLE = Set.of(
      "wheat",
      "carrot",
      "potato",
      "beetroot",
      "beetroot_seeds",
      "wheat_seeds",
      "pumpkin_seeds",
      "melon_seeds",
      "sugar_cane",
      "cocoa_beans",
      "nether_wart",
      "egg",
      "feather",
      "chicken",
      "leather",
      "beef",
      "mutton",
      "porkchop",
      "milk_bucket",
      "milk",
      "rabbit",
      "rabbit_hide",
      "wool",
      "white_wool",
      "black_wool",
      "brown_wool",
      "gray_wool",
      "light_gray_wool",
      "oak_log",
      "birch_log",
      "spruce_log",
      "jungle_log",
      "acacia_log",
      "dark_oak_log",
      "oak_sapling",
      "birch_sapling",
      "spruce_sapling",
      "jungle_sapling",
      "acacia_sapling",
      "dark_oak_sapling",
      "wood",
      "log",
      "cod",
      "salmon",
      "fish",
      "raw_fish",
      "bone",
      "string",
      "gunpowder",
      "slime_ball",
      "snowball",
      "ice",
      "flint",
      "clay_ball",
      "clay"
   );

   private ProductionChainChecker() {
   }

   static void check(String culture, LegacyLayoutDetector.Detection detection, Map<String, Path> goalIndex, LegacyConversionReport report) {
      Map<String, String> produced = new LinkedHashMap<>();
      Map<String, String> consumed = new LinkedHashMap<>();
      Map<String, String> sold = new LinkedHashMap<>();
      Map<String, String> bought = new LinkedHashMap<>();
      Set<String> banditOnlyOutputs = new HashSet<>();
      Set<String> nonBanditOutputs = new HashSet<>();

      for (Path vf : onlyCulture(detection.villagerTxts(), culture)) {
         String fname = vf.getFileName().toString();
         String fnameLow = fname.toLowerCase(Locale.ROOT);
         boolean isBandit = false;

         for (String kw : BANDIT_KEYWORDS) {
            if (fnameLow.contains(kw)) {
               isBandit = true;
               break;
            }
         }

         Map<String, List<String>> data = safeParseKv(vf);
         Map<String, String> producedHere = new LinkedHashMap<>();
         collectFirstToken(data, "bringbackhomegood", producedHere, fname);
         collectFirstToken(data, "collectgood", producedHere, fname);

         for (Entry<String, String> pe : producedHere.entrySet()) {
            produced.putIfAbsent(pe.getKey(), pe.getValue());
            if (isBandit) {
               banditOnlyOutputs.add(pe.getKey());
            } else {
               nonBanditOutputs.add(pe.getKey());
            }
         }

         collectFirstToken(data, "requiredgood", consumed, fname);
         collectFirstToken(data, "itemneeded", consumed, fname);
      }

      banditOnlyOutputs.removeAll(nonBanditOutputs);
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

      for (String goalName : usedGoalNames) {
         Path gf = goalIndex.get(goalName);
         if (gf != null) {
            String fname = gf.getFileName().toString();
            Map<String, List<String>> data = safeParseKv(gf);
            collectFirstToken(data, "output", produced, fname);
            collectFirstToken(data, "loot", produced, fname);
            collectFirstToken(data, "harvestitem", produced, fname);
            collectFirstToken(data, "bonusitem", produced, fname);
            collectFirstToken(data, "input", consumed, fname);
            collectFirstToken(data, "seed", consumed, fname);
            collectFirstToken(data, "itemtocook", consumed, fname);
         }
      }

      for (Path sf : onlyCulture(detection.shopTxts(), culture)) {
         String fname = sf.getFileName().toString();
         Map<String, List<String>> data = safeParseKv(sf);
         collectAllTokens(data, "sells", sold, fname);
         collectAllTokens(data, "deliverto", sold, fname);
         collectAllTokens(data, "buys", bought, fname);
         collectAllTokens(data, "buysoptional", bought, fname);
      }

      for (Path tf : onlyCulture(detection.tradedGoodsTxts(), culture)) {
         String fname = tf.getFileName().toString();

         try {
            for (String raw : Files.readAllLines(tf)) {
               String line = raw.trim();
               if (!line.isEmpty() && !line.startsWith("//")) {
                  String[] parts = line.split(",");
                  if (parts.length >= 1) {
                     String item = normalise(parts[0].trim());
                     if (!item.isEmpty()) {
                        bought.putIfAbsent(item, fname);
                     }
                  }
               }
            }
         } catch (IOException var20) {
         }
      }

      Set<String> available = new HashSet<>(produced.keySet());
      available.addAll(bought.keySet());
      available.addAll(WORLD_AVAILABLE);
      Set<String> needed = new TreeSet<>();
      needed.addAll(consumed.keySet());
      needed.addAll(sold.keySet());

      for (String item : needed) {
         if (!available.contains(item)) {
            String requiredBy = consumed.getOrDefault(item, sold.get(item));
            report.recordUnreachableInput(culture, item, requiredBy);
         }
      }

      Set<String> consumedOrSold = new HashSet<>(consumed.keySet());
      consumedOrSold.addAll(sold.keySet());
      List<String> producedSorted = new ArrayList<>(produced.keySet());
      Collections.sort(producedSorted);

      for (String item : producedSorted) {
         if (!consumedOrSold.contains(item) && !IGNORE_ORPHAN_OUTPUTS.contains(item) && !banditOnlyOutputs.contains(item)) {
            report.recordOrphanedOutput(culture, item, produced.get(item));
         }
      }
   }

   private static void collectFirstToken(Map<String, List<String>> data, String key, Map<String, String> into, String file) {
      List<String> values = data.get(key);
      if (values != null) {
         for (String raw : values) {
            String[] parts = raw.split(",", 2);
            if (parts.length != 0) {
               String item = normalise(parts[0].trim());
               if (!item.isEmpty()) {
                  into.putIfAbsent(item, file);
               }
            }
         }
      }
   }

   private static void collectAllTokens(Map<String, List<String>> data, String key, Map<String, String> into, String file) {
      List<String> values = data.get(key);
      if (values != null) {
         for (String raw : values) {
            for (String token : raw.split(",")) {
               String item = normalise(token.trim());
               if (!item.isEmpty()) {
                  into.putIfAbsent(item, file);
               }
            }
         }
      }
   }

   private static String normalise(String raw) {
      String lower = raw.toLowerCase(Locale.ROOT);
      if (lower.startsWith("minecraft:")) {
         lower = lower.substring("minecraft:".length());
      }

      return lower;
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
