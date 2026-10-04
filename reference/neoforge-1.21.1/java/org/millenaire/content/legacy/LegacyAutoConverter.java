package org.millenaire.content.legacy;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.content.ContentDirectoryManager;
import org.millenaire.content.ContentStatsReporter;
import org.millenaire.content.CultureIdPolicy;
import org.slf4j.Logger;

public final class LegacyAutoConverter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Set<String> warnedVersionDrift = ConcurrentHashMap.newKeySet();
   @Deprecated
   static final String PROBE_FILENAME = ".millenaire-convert-probe";
   static final String REPORT_FILENAME = "_conversion_report.txt";

   public static void resetForTesting() {
      warnedVersionDrift.clear();
   }

   private LegacyAutoConverter() {
   }

   public static void convertIfNeeded() {
      try {
         doConvert();
      } catch (Throwable t) {
         LOGGER.error(
            "Legacy auto-conversion aborted with unexpected failure; server will continue without converted content: {}",
            t.getClass().getSimpleName() + ": " + t.getMessage(),
            t
         );
      }
   }

   private static void doConvert() {
      if (ContentDirectoryManager.isInitialized()) {
         if (!(Boolean)MillenaireServerConfig.SERVER.legacyAutoConvert.get()) {
            LOGGER.info("Legacy auto-conversion disabled via config (legacyAutoConvert=false)");
         } else {
            Path customRoot;
            try {
               customRoot = ContentDirectoryManager.getCustomDir();
            } catch (IllegalStateException notInitialised) {
               return;
            }

            if (customRoot != null && Files.isDirectory(customRoot)) {
               List<LegacyAutoConverter.SubmodSource> sources = discoverSources(customRoot);
               if (!sources.isEmpty()) {
                  if (!LegacyConversionDriver.isWritable(customRoot)) {
                     LOGGER.warn(
                        "millenaire-custom/ is not writable, skipping legacy auto-conversion. Use /millenaire dev convert-addon from a writable staging dir, or set millenaire.legacyAutoConvert=false to silence this warning."
                     );
                  } else {
                     int totalPngs = 0;

                     for (LegacyAutoConverter.SubmodSource s : sources) {
                        totalPngs += s.detection.buildingPngs().size();
                     }

                     int cap = (Integer)MillenaireServerConfig.SERVER.legacyAutoConvertMaxPngs.get();
                     if (totalPngs > cap) {
                        LOGGER.warn(
                           "Legacy sub-mods total {} PNG files, exceeding the auto-conversion limit ({}). Use /millenaire dev convert-addon offline instead, or raise millenaire.legacyAutoConvertMaxPngs.",
                           totalPngs,
                           cap
                        );
                     } else {
                        try (LegacyConversionDriver.LockHandle handle = LegacyConversionDriver.acquireLockAt(customRoot)) {
                           if (handle.isHeld()) {
                              runConversion(customRoot, sources);
                           } else {
                              LOGGER.info("Another process is converting millenaire-custom/, skipping this pass");
                           }
                        } catch (IOException e) {
                           LOGGER.warn("Legacy auto-conversion I/O error: {}", e.getMessage());
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static List<LegacyAutoConverter.SubmodSource> discoverSources(Path customRoot) {
      LegacyLayoutDetector.Detection flatRootPreview = LegacyLayoutDetector.scan(customRoot);
      if (hasFlatRootLegacyContent(customRoot, flatRootPreview)) {
         LOGGER.warn(
            "millenaire-custom/ contains legacy TXT/PNG files directly at its root — wrap your legacy files in a sub-directory (e.g. BUILDINGSNORMAN/) so the converter can place the output in BUILDINGSNORMAN_converted/. Flat-root legacy content will NOT be converted."
         );
      }

      List<Path> children;
      try (Stream<Path> stream = Files.list(customRoot)) {
         children = stream.filter(x$0 -> Files.isDirectory(x$0)).sorted(Comparator.comparing(p -> p.getFileName().toString())).collect(Collectors.toList());
      } catch (IOException e) {
         LOGGER.warn("Failed to enumerate children of {}: {}", customRoot, e.getMessage());
         return List.of();
      }

      List<LegacyAutoConverter.SubmodSource> out = new ArrayList<>();

      for (Path child : children) {
         String name = child.getFileName().toString();
         if (!name.endsWith("_converted")) {
            Path convertedSibling = customRoot.resolve(name + "_converted");
            Path conversionMarker = convertedSibling.resolve("_conversion_manifest.json");
            if (Files.isRegularFile(conversionMarker)) {
               String recordedVersion = ConverterOutputManifest.readVersion(conversionMarker);
               if (recordedVersion == null || recordedVersion.equals("9.0.0-dev-preview.5")) {
                  LOGGER.debug("Skipping already-converted source '{}/' (manifest at {})", name, conversionMarker);
               } else if (warnedVersionDrift.add(name)) {
                  LOGGER.warn(
                     "Sub-mod '{}/' was converted by Millénaire {} but the running converter is {}. Output may be stale or incompatible. Delete '{}_converted/' to rebuild on the next boot.",
                     new Object[]{name, recordedVersion, "9.0.0-dev-preview.5", name}
                  );
               }
            } else {
               if (Files.isDirectory(convertedSibling)) {
                  LOGGER.warn("Incomplete '{}_converted/' (no {}); retrying conversion", name, "_conversion_manifest.json");
               }

               LegacyLayoutDetector.Detection det = LegacyLayoutDetector.scan(child);
               if (!det.isEmpty()) {
                  if (det.hasOnlyPreservedContent()) {
                     LOGGER.info("Legacy sub-mod '{}/' contains only preserved content; no conversion needed", name);
                  } else {
                     out.add(new LegacyAutoConverter.SubmodSource(name, child, convertedSibling, det));
                  }
               }
            }
         }
      }

      return out;
   }

   private static boolean hasFlatRootLegacyContent(Path customRoot, LegacyLayoutDetector.Detection preview) {
      return preview.isEmpty()
         ? false
         : !preview.questTxts().isEmpty() && preview.questTxts().stream().anyMatch(p -> isDirectRoot(customRoot, p, "quests"))
            || !preview.gatheringTxts().isEmpty() && preview.gatheringTxts().stream().anyMatch(p -> isDirectRoot(customRoot, p, "goals"))
            || !preview.buildingPngs().isEmpty() && preview.buildingPngs().stream().anyMatch(p -> isDirectCulturesRoot(customRoot, p))
            || !preview.villagerTxts().isEmpty() && preview.villagerTxts().stream().anyMatch(p -> isDirectCulturesRoot(customRoot, p))
            || !preview.cultureTxts().isEmpty() && preview.cultureTxts().stream().anyMatch(p -> isDirectCulturesRoot(customRoot, p));
   }

   private static boolean isDirectRoot(Path customRoot, Path file, String segment) {
      Path rel;
      try {
         rel = customRoot.relativize(file);
      } catch (IllegalArgumentException e) {
         return false;
      }

      return rel.getNameCount() > 1 && segment.equalsIgnoreCase(rel.getName(0).toString());
   }

   private static boolean isDirectCulturesRoot(Path customRoot, Path file) {
      return isDirectRoot(customRoot, file, "cultures");
   }

   static void runConversion(Path customRoot, List<LegacyAutoConverter.SubmodSource> sources) throws IOException {
      List<LegacyConversionDriver.DriverResult> results = new ArrayList<>(sources.size());

      for (LegacyAutoConverter.SubmodSource s : sources) {
         if (Thread.currentThread().isInterrupted()) {
            LOGGER.warn("Legacy auto-conversion interrupted at source '{}'", s.name());
            break;
         }

         Files.createDirectories(s.output());
         LOGGER.info(
            "Legacy sub-mod '{}/' → '{}_converted/' ({} PNGs, {} villager TXTs)",
            new Object[]{s.name(), s.name(), s.detection().buildingPngs().size(), s.detection().villagerTxts().size()}
         );

         try {
            LegacyConversionDriver.DriverResult r = LegacyConversionDriver.runWithOutput(s.source(), s.output(), s.detection(), ConversionMode.AUTO);
            results.add(r);
            ContentStatsReporter.reportConversion(r.report());
         } catch (Exception e) {
            LOGGER.error("Legacy sub-mod '{}/' conversion failed: {}", new Object[]{s.name(), e.getMessage(), e});
         }
      }

      writeAggregatedReport(customRoot, sources, results);
   }

   private static void writeAggregatedReport(Path customRoot, List<LegacyAutoConverter.SubmodSource> sources, List<LegacyConversionDriver.DriverResult> results) {
      int total = 0;

      for (LegacyConversionDriver.DriverResult r : results) {
         if (r != null) {
            total += r.report().totalConverted() + r.report().totalSkipped() + r.report().totalOutOfScope();
         }
      }

      if (total != 0) {
         StringBuilder sb = new StringBuilder();
         sb.append("# Millénaire legacy auto-conversion — ").append(sources.size()).append(" sub-mod(s)\n\n");

         for (int i = 0; i < sources.size(); i++) {
            LegacyAutoConverter.SubmodSource s = sources.get(i);
            sb.append("## ").append(s.name()).append("/ → ").append(s.name()).append("_converted/\n\n");
            if (i < results.size() && results.get(i) != null) {
               sb.append(results.get(i).report().render());
               if (!sb.toString().endsWith("\n")) {
                  sb.append('\n');
               }

               sb.append('\n');
            } else {
               sb.append("(no result — conversion failed, see server log)\n\n");
            }
         }

         Path target = customRoot.resolve("_conversion_report.txt");

         try {
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(
               tmp, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
            );

            try {
               Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
               Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException e) {
            LOGGER.warn("Failed to write aggregated conversion report at {}: {}", target, e.getMessage());
         }
      }
   }

   static boolean isValidCultureId(String id) {
      return id != null && !id.isEmpty() && CultureIdPolicy.PATTERN.matcher(id).matches();
   }

   @Deprecated
   static boolean isWritable(Path root) {
      return LegacyConversionDriver.isWritable(root);
   }

   @Deprecated
   static void writeReportAtomic(Path target, LegacyConversionReport report) throws IOException {
      LegacyConversionDriver.writeReportAtomic(target, report);
   }

   record SubmodSource(String name, Path source, Path output, LegacyLayoutDetector.Detection detection) {
   }
}
