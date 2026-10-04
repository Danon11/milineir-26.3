package org.millenaire.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

public final class DisabledIdsLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String MANIFEST_NAME = "_disabled.json";

   private DisabledIdsLoader() {
   }

   public static Set<String> load(Path contentTypeDir) {
      if (contentTypeDir == null) {
         return Collections.emptySet();
      }

      Path manifest = contentTypeDir.resolve("_disabled.json");
      if (!Files.isRegularFile(manifest)) {
         return Collections.emptySet();
      }

      if (!ContentDirectoryManager.checkSize(manifest, 1000000L)) {
         return Collections.emptySet();
      }

      try (InputStream in = Files.newInputStream(manifest)) {
         String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         JsonElement root = JsonParser.parseString(json);
         if (!root.isJsonArray()) {
            LOGGER.warn("{} must be a JSON array of strings; ignoring", manifest);
            return Collections.emptySet();
         }

         Set<String> out = new HashSet<>();

         for (JsonElement e : root.getAsJsonArray()) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
               out.add(e.getAsString());
            }
         }

         return out;
      } catch (Exception e) {
         LOGGER.warn("Could not parse {}: {}", manifest, e.getMessage());
         return Collections.emptySet();
      }
   }

   public static Set<String> loadUnion(List<Path> typeDirs) {
      if (typeDirs != null && !typeDirs.isEmpty()) {
         Set<String> out = new HashSet<>();

         for (Path dir : typeDirs) {
            if (dir != null) {
               out.addAll(load(dir));
            }
         }

         return out;
      } else {
         return Collections.emptySet();
      }
   }
}
