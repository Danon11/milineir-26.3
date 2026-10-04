package org.millenaire.content;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.millenaire.Millenaire;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class ValidationReport {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final List<ValidationReport.TypeBinding> PER_CULTURE_JSON_TYPES = List.of(
      new ValidationReport.TypeBinding("building_plan", "buildings", ValidationReport.DepthMode.NESTED_BY_CATEGORY),
      new ValidationReport.TypeBinding("villager_type", "villagers", ValidationReport.DepthMode.NESTED_BY_CATEGORY),
      new ValidationReport.TypeBinding("village_type", "villages", ValidationReport.DepthMode.FLAT),
      new ValidationReport.TypeBinding("shops", "shops", ValidationReport.DepthMode.FLAT),
      new ValidationReport.TypeBinding("goal", "goal", ValidationReport.DepthMode.FLAT),
      new ValidationReport.TypeBinding("quests", "quests", ValidationReport.DepthMode.DEEP)
   );

   private ValidationReport() {
   }

   public static void generate(List<String> activeCultures, Set<String> builtInCultures) {
      if (ContentDirectoryManager.isInitialized()) {
         try {
            Path customDir = ContentDirectoryManager.getCustomDir();
            String report = build(customDir, activeCultures, builtInCultures);
            if (report == null) {
               return;
            }

            Files.writeString(customDir.resolve("_validation_report.txt"), report, StandardCharsets.UTF_8);
            LOGGER.info("Validation report written: {}", customDir.resolve("_validation_report.txt"));
         } catch (Exception e) {
            LOGGER.warn("Could not write validation report: {}", e.getMessage(), e);
         }
      }
   }

   private static String build(Path customDir, List<String> activeCultures, Set<String> builtInCultures) throws IOException {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Millénaire Custom Content Report ===\n");
      sb.append("Generated: ").append(LocalDateTime.now().format(TIMESTAMP)).append("\n");

      String version;
      try {
         version = Millenaire.getModVersion();
      } catch (Exception e) {
         version = "(dev)";
      }

      sb.append("Mod version: ").append("millenaire").append(" ").append(version).append("\n\n");
      int customCultureCount = 0;
      int overrideCultureCount = 0;
      boolean anyContent = false;
      Map<String, ValidationReport.CultureStats> perCulture = new TreeMap<>();

      for (String culture : activeCultures) {
         ValidationReport.CultureStats stats = inspectCulture(culture);
         perCulture.put(culture, stats);
         if (!stats.isEmpty() || !builtInCultures.contains(culture)) {
            anyContent = true;
         }
      }

      int gatheringCount = countGlobalExtras("gathering_type");
      int questCount = countGlobalExtras("quests");
      int languageCount = countLanguageExtras();
      boolean hasGlobalExtras = gatheringCount > 0 || questCount > 0 || languageCount > 0;
      Map<String, List<String>> missings = ContentLoadReport.snapshot();
      if (!anyContent && !hasGlobalExtras && missings.isEmpty()) {
         return null;
      }

      for (Entry<String, ValidationReport.CultureStats> entry : perCulture.entrySet()) {
         String culture = entry.getKey();
         ValidationReport.CultureStats stats = entry.getValue();
         if (!stats.isEmpty() || !builtInCultures.contains(culture)) {
            boolean isCustom = !builtInCultures.contains(culture);
            if (isCustom) {
               customCultureCount++;
            } else if (!stats.isEmpty()) {
               overrideCultureCount++;
            }

            sb.append("=== Culture: ").append(culture);
            if (isCustom) {
               sb.append(" (custom)");
            } else {
               sb.append(" (built-in with overrides)");
            }

            sb.append(" ===\n");
            sb.append(String.format("  Source: %s%n", isCustom ? "external JSON" : "standard + custom overlay"));
            if (!stats.contributingSubmods.isEmpty()) {
               sb.append(String.format("  sub-mods: %s%n", formatSubmodList(stats.contributingSubmods)));
            }

            for (Entry<String, ValidationReport.TypeStats> typeEntry : stats.perType.entrySet()) {
               ValidationReport.TypeStats ts = typeEntry.getValue();
               if (ts.externalFiles != 0 || ts.disabled != 0) {
                  sb.append(String.format("  %-15s  %d file(s) on disk", typeEntry.getKey(), ts.externalFiles));
                  if (ts.disabled > 0) {
                     sb.append(String.format(", %d disabled", ts.disabled));
                  }

                  sb.append("\n");
               }
            }

            if (stats.hasCultureJson) {
               sb.append("  culture.json     present\n");
            }

            if (stats.hasTradedGoods) {
               sb.append("  traded_goods     present (additive merge)\n");
            }

            if (stats.hasReputation) {
               sb.append("  reputation       present\n");
            }

            if (stats.hasCultureRep) {
               sb.append("  culture_reputation present\n");
            }

            if (stats.namelistCount > 0) {
               sb.append(String.format("  namelists        %d file(s) (additive append)%n", stats.namelistCount));
            }

            sb.append("\n");
         }
      }

      sb.append("=== Global ===\n");
      sb.append(String.format("  gathering_type   %d custom file(s)%n", gatheringCount));
      sb.append(String.format("  quests           %d custom file(s)%n", questCount));
      sb.append(String.format("  language overlays %d custom file(s) across all locales%n", languageCount));
      sb.append("\n");
      if (!missings.isEmpty()) {
         sb.append("=== Missing content (custom cultures) ===\n");
         sb.append("These classpath files were consulted during load but were absent\n");
         sb.append("from both the mod JAR and every sub-mod overlay. For custom (non-\n");
         sb.append("built-in) cultures this is expected when the pack does not provide\n");
         sb.append("the corresponding resource — the loader falls back or skips the\n");
         sb.append("entry silently. Listed here so pack authors can spot unintended\n");
         sb.append("omissions without grep-ing the server log.\n\n");

         for (Entry<String, List<String>> entry : missings.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(":\n");

            for (String path : entry.getValue()) {
               sb.append("    - ").append(path).append("\n");
            }
         }

         sb.append("\n");
      }

      sb.append("=== Summary ===\n");
      sb.append(
         String.format("Cultures: %d custom, %d built-in with overrides, %d total loaded%n", customCultureCount, overrideCultureCount, activeCultures.size())
      );
      sb.append(
         String.format(
            "Registries after load: %d building plans, %d villager types, %d village types%n",
            ModCultures.getAllBuildingPlans().size(),
            ModCultures.getAllVillagerTypes().size(),
            ModCultures.getAllVillageTypes().size()
         )
      );
      sb.append("\n");
      sb.append("For detailed errors and warnings during loading, see logs/latest.log\n");
      sb.append("(search for 'Error', 'WARN CultureLoader', 'WARN BuildingPlanSet').\n");
      return sb.toString();
   }

   private static ValidationReport.CultureStats inspectCulture(String culture) throws IOException {
      ValidationReport.CultureStats stats = new ValidationReport.CultureStats();
      List<SubmodRoot> submods = CustomContentIndex.current().rootsForCulture(culture);
      if (submods.isEmpty()) {
         return stats;
      }

      Map<String, Set<String>> disabledUnion = new TreeMap<>();
      Map<String, Integer> externalCount = new TreeMap<>();

      for (SubmodRoot submod : submods) {
         Path cultureDir = submod.root().resolve("cultures").resolve(culture);
         if (Files.isDirectory(cultureDir)) {
            stats.contributingSubmods.add(submod);
            stats.hasCultureJson = stats.hasCultureJson | Files.isRegularFile(cultureDir.resolve("culture.json"));
            stats.hasTradedGoods = stats.hasTradedGoods | Files.isRegularFile(cultureDir.resolve("traded_goods.json"));
            stats.hasReputation = stats.hasReputation | Files.isRegularFile(cultureDir.resolve("reputation.json"));
            stats.hasCultureRep = stats.hasCultureRep | Files.isRegularFile(cultureDir.resolve("culture_reputation.json"));
            Path namelistsDir = cultureDir.resolve("namelists");
            if (Files.isDirectory(namelistsDir)) {
               stats.namelistCount = stats.namelistCount + countFiles(namelistsDir, ".txt");
            }

            for (ValidationReport.TypeBinding binding : PER_CULTURE_JSON_TYPES) {
               Path typeDir = cultureDir.resolve(binding.dirName());
               if (Files.isDirectory(typeDir)) {
                  int count = switch (binding.depth()) {
                     case FLAT -> countFiles(typeDir, ".json");
                     case NESTED_BY_CATEGORY -> countFilesAtDepth(typeDir, 2, ".json");
                     case DEEP -> countFilesDeep(typeDir, ".json");
                  };
                  Set<String> disabled = DisabledIdsLoader.load(typeDir);
                  if (count > 0) {
                     externalCount.merge(binding.label(), count, Integer::sum);
                  }

                  if (!disabled.isEmpty()) {
                     disabledUnion.computeIfAbsent(binding.label(), k -> new HashSet<>()).addAll(disabled);
                  }
               }
            }
         }
      }

      Set<String> touchedTypes = new LinkedHashSet<>();
      touchedTypes.addAll(externalCount.keySet());
      touchedTypes.addAll(disabledUnion.keySet());

      for (String type : touchedTypes) {
         int count = externalCount.getOrDefault(type, 0);
         int disabled = disabledUnion.getOrDefault(type, Set.of()).size();
         if (count > 0 || disabled > 0) {
            stats.perType.put(type, new ValidationReport.TypeStats(count, disabled));
         }
      }

      return stats;
   }

   private static int countFilesAtDepth(Path dir, int depth, String ext) throws IOException {
      if (!Files.isDirectory(dir)) {
         return 0;
      }

      try (Stream<Path> stream = ContentDirectoryManager.safeWalk(dir)) {
         return (int)stream.filter(x$0 -> Files.isRegularFile(x$0))
            .filter(p -> dir.relativize(p).getNameCount() == depth)
            .filter(p -> !p.getFileName().toString().startsWith("_"))
            .filter(p -> p.getFileName().toString().endsWith(ext))
            .count();
      }
   }

   private static int countFilesDeep(Path dir, String ext) throws IOException {
      if (!Files.isDirectory(dir)) {
         return 0;
      }

      try (Stream<Path> stream = ContentDirectoryManager.safeWalk(dir)) {
         return (int)stream.filter(x$0 -> Files.isRegularFile(x$0))
            .filter(p -> !p.getFileName().toString().startsWith("_"))
            .filter(p -> p.getFileName().toString().endsWith(ext))
            .count();
      }
   }

   private static int countFiles(Path dir, String ext) throws IOException {
      if (!Files.isDirectory(dir)) {
         return 0;
      }

      try (Stream<Path> stream = Files.list(dir)) {
         return (int)stream.filter(x$0 -> Files.isRegularFile(x$0))
            .filter(p -> !p.getFileName().toString().startsWith("_"))
            .filter(p -> p.getFileName().toString().endsWith(ext))
            .count();
      }
   }

   private static int countGlobalExtras(String subdir) {
      List<SubmodRoot> submods;
      if ("gathering_type".equals(subdir)) {
         submods = CustomContentIndex.current().rootsWithGatheringType();
      } else if ("quests".equals(subdir)) {
         submods = CustomContentIndex.current().rootsWithQuests();
      } else {
         submods = CustomContentIndex.current().roots();
      }

      int total = 0;

      for (SubmodRoot submod : submods) {
         Path dir = submod.root().resolve(subdir);
         if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = ContentDirectoryManager.safeWalk(dir)) {
               total += (int)stream.filter(x$0 -> Files.isRegularFile(x$0))
                  .filter(p -> !p.getFileName().toString().startsWith("_"))
                  .filter(p -> p.getFileName().toString().endsWith(".json"))
                  .count();
            } catch (IOException var11) {
            }
         }
      }

      return total;
   }

   private static int countLanguageExtras() {
      int total = 0;

      for (SubmodRoot submod : CustomContentIndex.current().rootsWithLanguages()) {
         Path langs = submod.root().resolve("languages");
         if (Files.isDirectory(langs)) {
            try (Stream<Path> langSub = Files.list(langs)) {
               for (Path lang : (Iterable<Path>)langSub::iterator) {
                  if (Files.isDirectory(lang)) {
                     total += countFiles(lang, ".txt");
                     total += countFiles(lang, ".json");
                  }
               }
            } catch (IOException var9) {
            }
         }
      }

      return total;
   }

   private static String formatSubmodList(List<SubmodRoot> submods) {
      StringBuilder out = new StringBuilder();

      for (int i = 0; i < submods.size(); i++) {
         if (i > 0) {
            out.append(", ");
         }

         out.append(submods.get(i).name());
      }

      return out.toString();
   }

   private static class CultureStats {
      final Map<String, ValidationReport.TypeStats> perType = new TreeMap<>();
      final List<SubmodRoot> contributingSubmods = new ArrayList<>();
      boolean hasCultureJson;
      boolean hasTradedGoods;
      boolean hasReputation;
      boolean hasCultureRep;
      int namelistCount;

      boolean isEmpty() {
         return !this.hasCultureJson
            && !this.hasTradedGoods
            && !this.hasReputation
            && !this.hasCultureRep
            && this.namelistCount == 0
            && this.perType.values().stream().allMatch(t -> t.externalFiles == 0 && t.disabled == 0);
      }
   }

   private enum DepthMode {
      FLAT,
      NESTED_BY_CATEGORY,
      DEEP;
   }

   private record TypeBinding(String label, String dirName, ValidationReport.DepthMode depth) {
   }

   private record TypeStats(int externalFiles, int disabled) {
      int total() {
         return this.externalFiles;
      }
   }
}
