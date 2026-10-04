package org.millenaire.content.legacy;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.millenaire.content.ContentDirectoryManager;
import org.millenaire.content.CultureIdPolicy;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.culture.CultureLoader;
import org.slf4j.Logger;

public final class LegacyConversionDriver {
   private static final Logger LOGGER = LogUtils.getLogger();
   static final String PROBE_FILENAME = ".millenaire-convert-probe";
   static final String LOCK_FILENAME = ".millenaire-convert.lock";
   static final String REPORT_FILENAME = "_conversion_report.txt";
   public static final String CONVERTER_VERSION = "9.0.0-dev-preview.5";
   private static final Set<String> KNOWN_ROOT_DIRS = Set.of("cultures", "languages", "quests", "goals", "resourcepack");

   private LegacyConversionDriver() {
   }

   public static LegacyConversionDriver.LockHandle acquireLockAt(Path addonRoot) throws IOException {
      Path lockFile = lockFileFor(addonRoot);
      RandomAccessFile raf = new RandomAccessFile(lockFile.toFile(), "rw");
      FileChannel channel = raf.getChannel();

      FileLock lock;
      try {
         lock = channel.tryLock();
      } catch (IOException e) {
         try {
            channel.close();
         } catch (IOException var8) {
         }

         try {
            raf.close();
         } catch (IOException var7) {
         }

         throw e;
      }

      return new LegacyConversionDriver.LockHandle(lockFile, raf, channel, lock);
   }

   private static Path lockFileFor(Path addonRoot) {
      Path customRoot;
      try {
         customRoot = ContentDirectoryManager.getCustomDir();
      } catch (IllegalStateException notInitialised) {
         customRoot = null;
      }

      if (customRoot != null) {
         try {
            if (addonRoot.toRealPath().startsWith(customRoot.toRealPath())) {
               return customRoot.resolve(".millenaire-convert.lock");
            }
         } catch (IOException var3) {
         }
      }

      return addonRoot.resolve(".millenaire-convert.lock");
   }

   public static boolean isWritable(Path addonRoot) {
      Path probe = addonRoot.resolve(".millenaire-convert-probe");

      try {
         Files.write(probe, new byte[]{77}, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
         Files.deleteIfExists(probe);
         return true;
      } catch (AccessDeniedException ade) {
         return false;
      } catch (IOException io) {
         return false;
      }
   }

   static void writeReportAtomic(Path target, LegacyConversionReport report) throws IOException {
      Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
      Files.writeString(tmp, report.render(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

      try {
         Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public static LegacyConversionDriver.DriverResult runWithOutput(
      Path sourceRoot, Path outputRoot, LegacyLayoutDetector.Detection detection, ConversionMode mode
   ) {
      long tStart = System.currentTimeMillis();
      Set<String> builtinCultures = new HashSet<>(CultureLoader.BUILTIN_CULTURES);
      TreeSet<String> mapperCultures = new TreeSet<>(builtinCultures);
      if (ContentDirectoryManager.isInitialized()) {
         for (String c : CustomContentIndex.current().customCultureIds()) {
            if (isValidCultureId(c)) {
               mapperCultures.add(c);
            }
         }
      }

      for (String c : detection.culturesWithLegacyContent()) {
         if (isValidCultureId(c)) {
            mapperCultures.add(c);
         } else {
            LOGGER.warn(
               "Skipping legacy pack with invalid culture id '{}' (must match {}); rename the directory to proceed.", c, CultureIdPolicy.PATTERN.pattern()
            );
         }
      }

      ItemIdMapper items = ItemIdMapper.loadAll(sourceRoot, mapperCultures);
      BiomeMapper biomes = BiomeMapper.loadAll(sourceRoot, mapperCultures);
      Path manifestPath = outputRoot.resolve("_conversion_manifest.json");

      ConverterOutputManifest manifest;
      try {
         manifest = ConverterOutputManifest.readOrEmpty(manifestPath, "9.0.0-dev-preview.5");
      } catch (IOException e) {
         LOGGER.warn("Failed to read conversion manifest at {}: {}. Starting with an empty manifest.", manifestPath, e.getMessage());
         manifest = new ConverterOutputManifest("9.0.0-dev-preview.5");
      }

      int manifestEntriesBefore = manifest.entries().size();
      LegacyConversionReport report = new LegacyConversionReport();
      LegacyConversionEngine engine = new LegacyConversionEngine(items, biomes, manifest, report, mode);
      LOGGER.info(
         "Legacy conversion starting in {} mode — {} cultures, {} PNGs, {} villager TXTs (source {} → output {})",
         new Object[]{
            mode, detection.culturesWithLegacyContent().size(), detection.buildingPngs().size(), detection.villagerTxts().size(), sourceRoot, outputRoot
         }
      );
      if (Thread.currentThread().isInterrupted()) {
         LOGGER.warn("Legacy conversion interrupted before culture pass; returning partial report");
         long elapsedInterrupted = System.currentTimeMillis() - tStart;
         return new LegacyConversionDriver.DriverResult(report, manifest, elapsedInterrupted);
      }

      try {
         engine.convertAll(sourceRoot, outputRoot, detection);
      } catch (Exception e) {
         LOGGER.error("Legacy conversion aborted: {}", e.getMessage(), e);
      }

      copyPreservedFiles(sourceRoot, outputRoot, detection);
      boolean manifestChanged = manifest.entries().size() > manifestEntriesBefore;
      if (manifestChanged) {
         try {
            Path parent = manifestPath.getParent();
            if (parent != null) {
               Files.createDirectories(parent);
            }

            manifest.writeAtomic(manifestPath);
         } catch (IOException e) {
            LOGGER.warn("Failed to write conversion manifest: {}", e.getMessage());
         }
      }

      if (mode == ConversionMode.CONVERT && (report.totalConverted() > 0 || report.totalSkipped() > 0 || report.totalOutOfScope() > 0)) {
         try {
            Files.createDirectories(outputRoot);
            writeReportAtomic(outputRoot.resolve("_conversion_report.txt"), report);
         } catch (IOException e) {
            LOGGER.warn("Failed to write conversion report at {}: {}", outputRoot, e.getMessage());
         }
      }

      long elapsed = System.currentTimeMillis() - tStart;
      LOGGER.info(
         "Legacy conversion in {} mode: {} files converted, {} skipped across {} cultures in {} ms",
         new Object[]{mode, report.totalConverted(), report.totalSkipped(), report.cultureCount(), elapsed}
      );
      return new LegacyConversionDriver.DriverResult(report, manifest, elapsed);
   }

   private static void copyPreservedFiles(Path sourceRoot, Path outputRoot, LegacyLayoutDetector.Detection detection) {
      for (Path src : detection.preservedPaths()) {
         Path rel;
         try {
            rel = sourceRoot.relativize(src);
         } catch (IllegalArgumentException e) {
            LOGGER.warn("Preserved path {} is not under source root {} — skipping", src, sourceRoot);
            continue;
         }

         Path normalised = stripWrapperPrefix(rel);
         Path dest = outputRoot.resolve(normalised);

         try {
            Path parent = dest.getParent();
            if (parent != null) {
               Files.createDirectories(parent);
            }

            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException e) {
            LOGGER.warn("Failed to copy preserved file {} → {}: {}", new Object[]{src, dest, e.getMessage()});
         }
      }
   }

   static Path stripWrapperPrefix(Path rel) {
      if (rel != null && rel.getNameCount() != 0) {
         for (int i = 0; i < rel.getNameCount(); i++) {
            String seg = rel.getName(i).toString().toLowerCase(Locale.ROOT);
            if (KNOWN_ROOT_DIRS.contains(seg)) {
               if (i == 0) {
                  return rel;
               }

               return rel.subpath(i, rel.getNameCount());
            }
         }

         if (rel.getNameCount() >= 2) {
            String last = rel.getFileName().toString().toLowerCase(Locale.ROOT);
            if ("itemlist.txt".equals(last)
               || "biome_map.json".equals(last)
               || last.startsWith("readme")
               || last.startsWith("_warning")
               || last.startsWith("warning")) {
               return rel.getFileName();
            }
         }

         return rel;
      } else {
         return rel;
      }
   }

   static boolean isValidCultureId(String id) {
      return id != null && !id.isEmpty() && CultureIdPolicy.PATTERN.matcher(id).matches();
   }

   public record DriverResult(LegacyConversionReport report, ConverterOutputManifest manifest, long elapsedMillis) {
   }

   public static final class LockHandle implements AutoCloseable {
      private final Path lockPath;
      private final RandomAccessFile raf;
      private final FileChannel channel;
      private final FileLock lock;

      private LockHandle(Path lockPath, RandomAccessFile raf, FileChannel channel, FileLock lock) {
         this.lockPath = lockPath;
         this.raf = raf;
         this.channel = channel;
         this.lock = lock;
      }

      public Path lockPath() {
         return this.lockPath;
      }

      public boolean isHeld() {
         return this.lock != null;
      }

      public void close() {
         try {
            if (this.lock != null && this.lock.isValid()) {
               this.lock.release();
            }
         } catch (IOException e) {
            LegacyConversionDriver.LOGGER.warn("advisory lock release failed on {}: {}", this.lockPath, e.getMessage());
         }

         try {
            if (this.channel != null) {
               this.channel.close();
            }
         } catch (IOException e) {
            LegacyConversionDriver.LOGGER.warn("advisory lock channel close failed on {}: {}", this.lockPath, e.getMessage());
         }

         try {
            this.raf.close();
         } catch (IOException var2) {
         }
      }
   }
}
