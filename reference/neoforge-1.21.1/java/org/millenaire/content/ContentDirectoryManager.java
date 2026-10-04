package org.millenaire.content;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class ContentDirectoryManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String STANDARD_DIR_NAME = "millenaire";
   public static final String CUSTOM_DIR_NAME = "millenaire-custom";
   public static final long MAX_JSON_BYTES = 1000000L;
   public static final long MAX_NBT_BYTES = 10000000L;
   public static final long MAX_TXT_BYTES = 512000L;
   public static final int MAX_FILES_PER_TYPE = 500;
   public static final int MAX_CUSTOM_CULTURES = 50;
   public static final int MAX_RECURSION_DEPTH = 7;
   private static volatile Path standardDir;
   private static volatile Path standardRootReal;
   private static volatile Path customDir;
   private static volatile Path customRootReal;

   private ContentDirectoryManager() {
   }

   public static synchronized void init(MinecraftServer server) {
      if (server == null) {
         throw new IllegalArgumentException("MinecraftServer is null");
      }

      initFromGameDir(server.getServerDirectory());
   }

   public static synchronized void initClient(Path gameDir) {
      if (gameDir == null) {
         throw new IllegalArgumentException("gameDir is null");
      }

      if (standardRootReal != null && customRootReal != null) {
         try {
            if (standardRootReal.equals(gameDir.resolve("millenaire").toRealPath()) && customRootReal.equals(gameDir.resolve("millenaire-custom").toRealPath())
               )
             {
               return;
            }
         } catch (IOException var2) {
         }
      }

      initFromGameDir(gameDir);
   }

   private static void initFromGameDir(Path gameDir) {
      Path standardTarget = gameDir.resolve("millenaire");
      Path customTarget = gameDir.resolve("millenaire-custom");

      try {
         if (!Files.isDirectory(standardTarget)) {
            Files.createDirectories(standardTarget);
            LOGGER.info("Created standard content directory: {}", standardTarget);
         }

         if (!Files.isDirectory(customTarget)) {
            Files.createDirectories(customTarget);
            LOGGER.info("Created custom content directory: {}", customTarget);
         }

         standardDir = standardTarget;
         standardRootReal = standardTarget.toRealPath();
         customDir = customTarget;
         customRootReal = customTarget.toRealPath();
         LOGGER.info("Content directories initialised: standard={}, custom={}", standardRootReal, customRootReal);
      } catch (IOException e) {
         standardDir = null;
         standardRootReal = null;
         customDir = null;
         customRootReal = null;
         LOGGER.error("Failed to initialise content directories under {}: {}", new Object[]{gameDir, e.getMessage(), e});
      }
   }

   public static synchronized void resetForTesting() {
      standardDir = null;
      standardRootReal = null;
      customDir = null;
      customRootReal = null;
   }

   public static synchronized void initForTesting(Path gameDir) throws IOException {
      if (gameDir == null) {
         throw new IllegalArgumentException("gameDir is null");
      }

      Path standardTarget = gameDir.resolve("millenaire");
      Path customTarget = gameDir.resolve("millenaire-custom");
      Files.createDirectories(standardTarget);
      Files.createDirectories(customTarget);
      standardDir = standardTarget;
      standardRootReal = standardTarget.toRealPath();
      customDir = customTarget;
      customRootReal = customTarget.toRealPath();
   }

   public static boolean isInitialized() {
      return standardDir != null && customDir != null && standardRootReal != null && customRootReal != null;
   }

   public static Path getStandardDir() {
      Path dir = standardDir;
      if (dir == null) {
         throw new IllegalStateException("ContentDirectoryManager not initialised. Call init() first.");
      } else {
         return dir;
      }
   }

   public static Path getCustomDir() {
      Path dir = customDir;
      if (dir == null) {
         throw new IllegalStateException("ContentDirectoryManager not initialised. Call init() first.");
      } else {
         return dir;
      }
   }

   public static Path getStandardRootReal() {
      return standardRootReal;
   }

   public static Path getCustomRootReal() {
      return customRootReal;
   }

   public static Path getStandardCulturesDir() {
      return getStandardDir().resolve("cultures");
   }

   public static Path safeResolve(Path path) {
      if (isInitialized() && path != null) {
         try {
            Path real = path.toRealPath();
            if (!real.startsWith(standardRootReal) && !real.startsWith(customRootReal)) {
               LOGGER.warn("Path escapes content directories and will be skipped: {}", path);
               return null;
            } else {
               return real;
            }
         } catch (IOException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static boolean isInsideRoot(Path path) {
      return safeResolve(path) != null;
   }

   public static Stream<Path> safeWalk(Path dir) throws IOException {
      if (!Files.isDirectory(dir)) {
         return Stream.empty();
      }

      final List<Path> results = new ArrayList<>();
      Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 7, new SimpleFileVisitor<Path>() {
         public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
            if (Files.isSymbolicLink(d) && ContentDirectoryManager.safeResolve(d) == null) {
               ContentDirectoryManager.LOGGER.warn("Skipping symlinked subtree pointing outside content directories: {}", d);
               return FileVisitResult.SKIP_SUBTREE;
            } else {
               results.add(d);
               return FileVisitResult.CONTINUE;
            }
         }

         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (Files.isSymbolicLink(file) && ContentDirectoryManager.safeResolve(file) == null) {
               ContentDirectoryManager.LOGGER.warn("Skipping symlinked file pointing outside content directories: {}", file);
               return FileVisitResult.CONTINUE;
            } else {
               results.add(file);
               return FileVisitResult.CONTINUE;
            }
         }

         public FileVisitResult visitFileFailed(Path file, IOException exc) {
            ContentDirectoryManager.LOGGER.debug("Could not visit {}: {}", file, exc.getMessage());
            return FileVisitResult.CONTINUE;
         }
      });
      return results.stream();
   }

   public static boolean checkSize(Path file, long maxBytes) {
      try {
         if (!Files.exists(file)) {
            return true;
         } else {
            long size = Files.size(file);
            if (size > maxBytes) {
               LOGGER.error("File {} exceeds size limit ({} bytes > {} bytes) — skipping", new Object[]{file, size, maxBytes});
               return false;
            } else {
               return true;
            }
         }
      } catch (IOException e) {
         LOGGER.error("Failed to read size of {}: {}", file, e.getMessage());
         return false;
      }
   }
}
