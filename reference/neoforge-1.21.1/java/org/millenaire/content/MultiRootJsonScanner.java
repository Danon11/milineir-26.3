package org.millenaire.content;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;

public final class MultiRootJsonScanner {
   private static final Logger LOGGER = LogUtils.getLogger();

   private MultiRootJsonScanner() {
   }

   public static void scan(
      List<SubmodRoot> roots,
      Function<SubmodRoot, Path> dirOf,
      Set<String> disabledIds,
      Set<String> warnedReplaceIds,
      String warnKey,
      MultiRootJsonScanner.Handler handler
   ) {
      Set<String> seenInThisRun = new HashSet<>();

      for (SubmodRoot submod : roots) {
         Path dir = dirOf.apply(submod);
         if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> stream = ContentDirectoryManager.safeWalk(dir)) {
               for (Path p : (Iterable<Path>)stream::iterator) {
                  if (Files.isRegularFile(p)) {
                     String name = p.getFileName().toString();
                     if (!name.startsWith("_") && name.endsWith(".json")) {
                        Path rel = dir.relativize(p);
                        String relStr = rel.toString().replace('\\', '/');
                        String id = relStr.substring(0, relStr.length() - 5);
                        if (!disabledIds.contains(id)) {
                           if (!seenInThisRun.add(id)) {
                              String guardKey = warnKey + ":" + id;
                              if (warnedReplaceIds.add(guardKey)) {
                                 LOGGER.warn(
                                    "{} '{}' already loaded from an earlier sub-mod; ignoring duplicate from '{}' (first-alpha wins).",
                                    new Object[]{warnKey, id, submod.displayName()}
                                 );
                              }
                           } else {
                              handler.accept(submod, p, id);
                           }
                        }
                     }
                  }
               }
            } catch (IOException e) {
               LOGGER.error("Failed to walk sub-mod directory {}: {}", new Object[]{dir, e.getMessage(), e});
            }
         }
      }
   }

   @FunctionalInterface
   public interface Handler {
      void accept(SubmodRoot var1, Path var2, String var3);
   }
}
