package org.millenaire.content.legacy;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.millenaire.content.CultureIdPolicy;
import org.slf4j.Logger;

public final class LegacyLayoutDetector {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final List<String> OUT_OF_SCOPE_SEGMENTS = List.of("walls", "help", "banner", "banners");

   private LegacyLayoutDetector() {
   }

   public static List<LegacyLayoutDetector.Normalisation> normaliseCultureDirsInPlace(Path customRoot, ConversionMode mode, LegacyConversionReport report) {
      List<LegacyLayoutDetector.Normalisation> out = new ArrayList<>();
      if (customRoot == null) {
         return out;
      }

      Path culturesDir = customRoot.resolve("cultures");
      if (!Files.isDirectory(culturesDir)) {
         return out;
      }

      List<Path> children = new ArrayList<>();

      try (Stream<Path> list = Files.list(culturesDir)) {
         list.filter(x$0 -> Files.isDirectory(x$0)).forEach(children::add);
      } catch (IOException e) {
         LOGGER.warn("LegacyLayoutDetector: failed to list {}: {}", culturesDir, e.getMessage());
         return out;
      }

      children.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

      for (Path original : children) {
         LegacyLayoutDetector.Normalisation n = normaliseOne(original, mode, report);
         out.add(n);
         if (report != null) {
            report.recordNormalisation(n);
         }
      }

      return out;
   }

   private static LegacyLayoutDetector.Normalisation normaliseOne(Path original, ConversionMode mode, LegacyConversionReport report) {
      String name = original.getFileName().toString();
      if (CultureIdPolicy.PATTERN.matcher(name).matches()) {
         return new LegacyLayoutDetector.Normalisation(original, original, LegacyLayoutDetector.Normalisation.Outcome.ALREADY_CANONICAL);
      }

      String lowered = name.toLowerCase(Locale.ROOT);
      boolean foldable = CultureIdPolicy.PATTERN.matcher(lowered).matches();
      Path canonical = original.resolveSibling(lowered);
      if (!foldable) {
         if (mode.isStrict() && report != null) {
            report.recordSkipped(
               name,
               LegacyConversionReport.Kind.CULTURE,
               original.toString(),
               "invalid culture dir name \"" + name + "\" — CultureIdPolicy.PATTERN requires " + CultureIdPolicy.PATTERN.pattern(),
               "Rename the directory to a canonical form (lowercase alphanumeric, optionally with '_' or '-' separators). If the current name contains other punctuation (e.g. dots), pick a canonical form manually."
            );
         } else {
            LOGGER.warn(
               "Legacy auto-conversion: rejecting culture directory '{}' — name is not case-foldable to CultureIdPolicy.PATTERN ({}); rename manually.",
               name,
               CultureIdPolicy.PATTERN.pattern()
            );
         }

         return new LegacyLayoutDetector.Normalisation(original, canonical, LegacyLayoutDetector.Normalisation.Outcome.REJECTED_NON_FOLDABLE);
      } else if (Files.exists(canonical)) {
         boolean sameFile = false;

         try {
            sameFile = Files.isSameFile(original, canonical);
         } catch (IOException var9) {
         }

         if (sameFile) {
            LOGGER.info("Legacy auto-conversion: culture directory '{}' is already canonical on a case-insensitive filesystem (no rename needed)", name);
            return new LegacyLayoutDetector.Normalisation(original, canonical, LegacyLayoutDetector.Normalisation.Outcome.RENAMED);
         }

         if (mode.isStrict() && report != null) {
            report.recordSkipped(
               lowered,
               LegacyConversionReport.Kind.CULTURE,
               original.toString(),
               "culture dir '" + name + "' coexists with '" + lowered + "' — can't determine which is authoritative",
               "Merge the contents of '" + name + "' and '" + lowered + "' manually into the lowercase directory, then remove the other."
            );
         } else {
            LOGGER.warn(
               "Legacy auto-conversion: rejecting culture directory '{}' — coexists with '{}' on a case-sensitive filesystem; merge manually.", name, lowered
            );
         }

         return new LegacyLayoutDetector.Normalisation(original, canonical, LegacyLayoutDetector.Normalisation.Outcome.REJECTED_COEXISTENCE);
      } else if (!mode.isStrict()) {
         try {
            try {
               Files.move(original, canonical, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
               Files.move(original, canonical);
            }

            LOGGER.info("Legacy auto-conversion: normalised culture directory '{}' → '{}' (case fold)", name, lowered);
            return new LegacyLayoutDetector.Normalisation(original, canonical, LegacyLayoutDetector.Normalisation.Outcome.RENAMED);
         } catch (IOException e) {
            LOGGER.warn("Legacy auto-conversion: failed to rename culture dir '{}' → '{}': {}", new Object[]{name, lowered, e.getMessage()});
            return new LegacyLayoutDetector.Normalisation(original, canonical, LegacyLayoutDetector.Normalisation.Outcome.REJECTED_COEXISTENCE);
         }
      } else {
         if (report != null) {
            report.recordSkipped(
               lowered,
               LegacyConversionReport.Kind.CULTURE,
               original.toString(),
               "invalid culture dir name \"" + name + "\" — CultureIdPolicy.PATTERN requires " + CultureIdPolicy.PATTERN.pattern(),
               "Rename the directory to \"" + lowered + "\" (lowercase, optionally with '_' or '-' separators)."
            );
         }

         return new LegacyLayoutDetector.Normalisation(original, canonical, LegacyLayoutDetector.Normalisation.Outcome.REJECTED_COEXISTENCE);
      }
   }

   public static LegacyLayoutDetector.Detection scan(Path addonRoot) {
      if (addonRoot != null && Files.isDirectory(addonRoot)) {
         LegacyLayoutDetector.Collector c = new LegacyLayoutDetector.Collector();

         try (Stream<Path> walk = Files.walk(addonRoot, 7)) {
            walk.filter(x$0 -> Files.isRegularFile(x$0)).forEach(p -> classify(p, addonRoot, c));
         } catch (IOException e) {
            System.err.println("[WARN] LegacyLayoutDetector: failed to walk " + addonRoot + ": " + e.getMessage());
            return empty();
         }

         return c.build();
      } else {
         return empty();
      }
   }

   private static LegacyLayoutDetector.Detection empty() {
      List<Path> e = List.of();
      return new LegacyLayoutDetector.Detection(e, e, e, e, e, e, e, e, e, e, e, e);
   }

   private static void classify(Path file, Path root, LegacyLayoutDetector.Collector c) {
      Path rel;
      try {
         rel = root.relativize(file);
      } catch (IllegalArgumentException ignored) {
         return;
      }

      String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
      List<String> segs = pathSegmentsLower(rel);
      if (!segs.contains("goals") || !lowerName.endsWith(".txt") || !segs.contains("genericcrafting") && !isNonCraftingGoalFamily(segs)) {
         if (segs.contains("quests") && lowerName.endsWith(".txt")) {
            int questsIdx = segs.indexOf("quests");
            boolean isVillagerQuestSubcategory = questsIdx > 0 && "villagers".equals(segs.get(questsIdx - 1));
            if (!isVillagerQuestSubcategory) {
               c.quests.add(file);
               return;
            }
         }

         if (lowerName.endsWith(".txt")) {
            int cIdx = segs.indexOf("cultures");
            if (cIdx >= 0 && cIdx + 2 < segs.size() && OUT_OF_SCOPE_SEGMENTS.contains(segs.get(cIdx + 2))) {
               c.outOfScope.add(file);
               return;
            }
         }

         if (lowerName.startsWith("help_") && lowerName.endsWith(".txt")) {
            c.outOfScope.add(file);
         } else if (isTopLevelPreservedName(lowerName)) {
            c.preserved.add(file);
         } else if (segs.contains("languages")) {
            c.preserved.add(file);
         } else {
            int cultIdx = segs.indexOf("cultures");
            if (cultIdx >= 0 && cultIdx + 2 < segs.size()) {
               String subdir = segs.get(cultIdx + 2);
               switch (subdir) {
                  case "buildings":
                     if (lowerName.endsWith(".png")) {
                        c.buildingPngs.add(file);
                     } else if (lowerName.endsWith(".txt")) {
                        c.buildingTxts.add(file);
                     }
                     break;
                  case "custombuildings":
                     if (lowerName.endsWith(".png")) {
                        c.buildingPngs.add(file);
                     } else if (lowerName.endsWith(".txt")) {
                        c.buildingTxts.add(file);
                     }
                     break;
                  case "villagers":
                     if (lowerName.endsWith(".txt")) {
                        c.villagerTxts.add(file);
                     }
                     break;
                  case "villages":
                     if (lowerName.endsWith(".txt")) {
                        c.villageTxts.add(file);
                     }
                     break;
                  case "lonebuildings":
                     if (lowerName.endsWith(".txt")) {
                        c.loneTxts.add(file);
                     }
                     break;
                  case "shops":
                     if (lowerName.endsWith(".txt")) {
                        c.shopTxts.add(file);
                     }
                     break;
                  case "namelists":
                  case "resourcepack":
                     c.preserved.add(file);
                     break;
                  default:
                     if (cultIdx + 2 == segs.size() - 1) {
                        if ("traded_goods.txt".equals(lowerName)) {
                           c.tradedGoodsTxts.add(file);
                        } else if ("culture.txt".equals(lowerName)) {
                           c.cultureTxts.add(file);
                        } else if ("itemlist.txt".equals(lowerName) || "biome_map.json".equals(lowerName)) {
                           c.preserved.add(file);
                        }
                     }
               }
            }
         }
      } else {
         c.gathering.add(file);
      }
   }

   private static boolean isTopLevelPreservedName(String lowerName) {
      return "itemlist.txt".equals(lowerName)
         || "biome_map.json".equals(lowerName)
         || lowerName.startsWith("readme")
         || lowerName.startsWith("_warning")
         || lowerName.startsWith("warning");
   }

   private static boolean isNonCraftingGoalFamily(List<String> segs) {
      for (String s : segs) {
         switch (s) {
            case "genericharvesting":
            case "genericplanting":
            case "genericmining":
            case "genericslaughteranimal":
            case "genericcooking":
            case "genericplantsapling":
            case "genericgatherblocks":
            case "generictakefrombuilding":
               return true;
         }
      }

      return false;
   }

   private static List<String> pathSegmentsLower(Path rel) {
      List<String> out = new ArrayList<>(rel.getNameCount());

      for (int i = 0; i < rel.getNameCount(); i++) {
         out.add(rel.getName(i).toString().toLowerCase(Locale.ROOT));
      }

      return out;
   }

   private static final class Collector {
      final List<Path> buildingPngs = new ArrayList<>();
      final List<Path> buildingTxts = new ArrayList<>();
      final List<Path> villagerTxts = new ArrayList<>();
      final List<Path> villageTxts = new ArrayList<>();
      final List<Path> loneTxts = new ArrayList<>();
      final List<Path> shopTxts = new ArrayList<>();
      final List<Path> tradedGoodsTxts = new ArrayList<>();
      final List<Path> cultureTxts = new ArrayList<>();
      final List<Path> gathering = new ArrayList<>();
      final List<Path> quests = new ArrayList<>();
      final List<Path> outOfScope = new ArrayList<>();
      final List<Path> preserved = new ArrayList<>();

      LegacyLayoutDetector.Detection build() {
         return new LegacyLayoutDetector.Detection(
            sorted(this.buildingPngs),
            sorted(this.buildingTxts),
            sorted(this.villagerTxts),
            sorted(this.villageTxts),
            sorted(this.loneTxts),
            sorted(this.shopTxts),
            sorted(this.tradedGoodsTxts),
            sorted(this.cultureTxts),
            sorted(this.gathering),
            sorted(this.quests),
            sorted(this.outOfScope),
            sorted(this.preserved)
         );
      }

      private static List<Path> sorted(List<Path> in) {
         return in.stream()
            .sorted((a, b) -> a.toString().compareTo(b.toString()))
            .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
      }
   }

   public record Detection(
      List<Path> buildingPngs,
      List<Path> buildingTxts,
      List<Path> villagerTxts,
      List<Path> villageTxts,
      List<Path> loneTxts,
      List<Path> shopTxts,
      List<Path> tradedGoodsTxts,
      List<Path> cultureTxts,
      List<Path> gatheringTxts,
      List<Path> questTxts,
      List<Path> outOfScopeTxts,
      List<Path> preservedPaths
   ) {
      public boolean isEmpty() {
         return this.buildingPngs.isEmpty()
            && this.buildingTxts.isEmpty()
            && this.villagerTxts.isEmpty()
            && this.villageTxts.isEmpty()
            && this.loneTxts.isEmpty()
            && this.shopTxts.isEmpty()
            && this.tradedGoodsTxts.isEmpty()
            && this.cultureTxts.isEmpty()
            && this.gatheringTxts.isEmpty()
            && this.questTxts.isEmpty()
            && this.outOfScopeTxts.isEmpty()
            && this.preservedPaths.isEmpty();
      }

      public boolean hasOnlyPreservedContent() {
         return !this.preservedPaths.isEmpty()
            && this.buildingPngs.isEmpty()
            && this.buildingTxts.isEmpty()
            && this.villagerTxts.isEmpty()
            && this.villageTxts.isEmpty()
            && this.loneTxts.isEmpty()
            && this.shopTxts.isEmpty()
            && this.tradedGoodsTxts.isEmpty()
            && this.cultureTxts.isEmpty()
            && this.gatheringTxts.isEmpty()
            && this.questTxts.isEmpty()
            && this.outOfScopeTxts.isEmpty();
      }

      public List<String> culturesWithLegacyContent() {
         TreeSet<String> cultures = new TreeSet<>();
         Stream.of(
               this.buildingTxts, this.buildingPngs, this.villagerTxts, this.villageTxts, this.loneTxts, this.shopTxts, this.tradedGoodsTxts, this.cultureTxts
            )
            .flatMap(Collection::stream)
            .map(LegacyLayoutDetector.Detection::cultureOf)
            .filter(s -> s != null && !s.isEmpty())
            .forEach(cultures::add);
         return List.copyOf(cultures);
      }

      static String cultureOf(Path p) {
         for (int i = 0; i < p.getNameCount() - 1; i++) {
            if ("cultures".equalsIgnoreCase(p.getName(i).toString())) {
               return p.getName(i + 1).toString().toLowerCase(Locale.ROOT);
            }
         }

         return null;
      }
   }

   public record Normalisation(Path original, Path canonical, LegacyLayoutDetector.Normalisation.Outcome outcome) {
      public enum Outcome {
         RENAMED,
         ALREADY_CANONICAL,
         REJECTED_NON_FOLDABLE,
         REJECTED_COEXISTENCE;
      }
   }
}
