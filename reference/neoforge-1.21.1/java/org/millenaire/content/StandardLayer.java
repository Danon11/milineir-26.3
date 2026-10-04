package org.millenaire.content;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;

final class StandardLayer {
   private static final Logger LOGGER = LogUtils.getLogger();
   static final SourceLabel LABEL = new SourceLabel("standard");

   private StandardLayer() {
   }

   static List<LayerEntry> scan(Path root) {
      if (root != null && Files.isDirectory(root)) {
         List<LayerEntry> entries = new ArrayList<>();

         try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(x$0 -> Files.isRegularFile(x$0)).forEach(path -> entries.add(toEntry(root, path)));
         } catch (IOException e) {
            LOGGER.error("Failed to scan standard layer at {}: {}", new Object[]{root, e.getMessage(), e});
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
         relPath, original, size, LABEL, SourceKind.STANDARD, (rel, owner) -> new Resource.FileResource(rel, LABEL, SourceKind.STANDARD, path, owner)
      );
   }
}
