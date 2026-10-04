package org.millenaire.content;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;

final class ClasspathLayer {
   private static final Logger LOGGER = LogUtils.getLogger();
   static final SourceLabel LABEL = new SourceLabel("millenaire (jar)");
   static final String JAR_CONTENT_ROOT = "millenaire";

   private ClasspathLayer() {
   }

   static Path resolveDefaultRoot() {
      return isModListAvailable() ? resolveFromModList() : resolveFromClassloader();
   }

   private static boolean isModListAvailable() {
      ModList list;
      try {
         list = ModList.get();
      } catch (LinkageError t) {
         LOGGER.debug("ModList class not available, switching to classloader fallback: {}", t.getMessage());
         return false;
      }

      return list != null;
   }

   private static Path resolveFromModList() {
      ModContainer container = (ModContainer)ModList.get().getModContainerById("millenaire").orElseThrow();
      IModFile modFile = container.getModInfo().getOwningFile().getFile();
      Path jarRoot = modFile.getSecureJar().getRootPath();
      Path contentRoot = jarRoot.resolve("millenaire");
      return Files.isDirectory(contentRoot) ? contentRoot : null;
   }

   private static Path resolveFromClassloader() {
      try {
         URL url = ClasspathLayer.class.getResource("/millenaire");
         if (url == null) {
            return null;
         }

         Path contentRoot = Path.of(url.toURI());
         return Files.isDirectory(contentRoot) ? contentRoot : null;
      } catch (Throwable t) {
         LOGGER.debug("Classloader fallback for JAR content root failed: {}", t.getMessage());
         return null;
      }
   }

   static List<LayerEntry> scan(Path root) {
      if (root != null && Files.isDirectory(root)) {
         List<LayerEntry> entries = new ArrayList<>();

         try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(x$0 -> Files.isRegularFile(x$0)).forEach(path -> entries.add(toEntry(root, path)));
         } catch (IOException e) {
            LOGGER.error("Failed to scan classpath layer at {}: {}", new Object[]{root, e.getMessage(), e});
            return List.of();
         }

         entries.sort((a, b) -> a.relPath().compareTo(b.relPath()));
         return Collections.unmodifiableList(entries);
      } else {
         return List.of();
      }
   }

   private static LayerEntry toEntry(Path root, Path path) {
      String original = root.relativize(path).toString().replace('\\', '/');
      String relPath = original.toLowerCase(Locale.ROOT);

      long size;
      try {
         size = Files.size(path);
      } catch (IOException e) {
         LOGGER.warn("Could not stat {}: {}", path, e.getMessage());
         size = -1L;
      }

      return new LayerEntry(
         relPath, original, size, LABEL, SourceKind.CLASSPATH, (rel, owner) -> new Resource.ClasspathResource(rel, LABEL, SourceKind.CLASSPATH, path, owner)
      );
   }
}
