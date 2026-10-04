package org.millenaire.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.slf4j.Logger;

public final class NativeContentDeployer {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String VERSION_FILE = "_deployed_version.txt";
   private static final String WARNING_FILE = "WARNING - changes here will be overwritten on update.txt";
   private static final String WARNING_BODY = "The millenaire/ directory is auto-deployed by Millénaire on each new\nversion of the mod, erasing all changes made to it.\n\nIf you want to customise content, copy the relevant files to the\ncorresponding location in millenaire-custom/, which is never\ntouched by updates.\n\nMost files in millenaire-custom/ replace their equivalent in\nmillenaire/. The following are additive instead:\n  - Language files (<culture>_sentences.txt, <culture>_dialogues.txt)\n  - Namelists (*.txt under cultures/<culture>/namelists/)\n  - Traded goods (traded_goods.json per culture)\n\nSee docs/feat/custom-content.md for the full authoring workflow.\n";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private NativeContentDeployer() {
   }

   public static synchronized void deployIfNeeded() {
      if (!ContentDirectoryManager.isInitialized()) {
         LOGGER.warn("Skipping deployment: ContentDirectoryManager is not initialised");
      } else {
         try {
            Path standardDir = ContentDirectoryManager.getStandardDir();
            String currentVersion = currentModVersion();
            String deployedVersion = readDeployedVersion(standardDir);
            boolean devForce = !FMLEnvironment.production;
            if (!devForce && !shouldDeploy(currentVersion, deployedVersion)) {
               LOGGER.info("Standard content already up-to-date ({}); skipping deployment", deployedVersion);
               return;
            }

            if (devForce) {
               LOGGER.info(
                  "Dev environment detected — forcing redeploy of standard content to {} (mod {}, previous {})",
                  new Object[]{standardDir, currentVersion, deployedVersion == null ? "never" : deployedVersion}
               );
            } else {
               LOGGER.info(
                  "Redeploying Millénaire standard content to {} (mod {}, previous {})",
                  new Object[]{standardDir, currentVersion, deployedVersion == null ? "never" : deployedVersion}
               );
            }

            long startMs = System.currentTimeMillis();
            wipeStandardDir(standardDir);
            int fileCount = 0;
            fileCount += deployClasspathDirRecursive(standardDir, "/millenaire/cultures", standardDir.resolve("cultures"));
            fileCount += deployClasspathDir(standardDir, "/millenaire/gathering_type", standardDir.resolve("gathering_type"), null);
            fileCount += deployQuests(standardDir);
            fileCount += deployLanguages(standardDir);
            fileCount += deployClasspathDir(standardDir, "/millenaire/templates", standardDir.resolve("_templates"), null);
            fileCount += deployClasspathDir(standardDir, "/millenaire/reference", standardDir.resolve("_reference"), null);
            fileCount += deployClasspathDirRecursive(standardDir, "/millenaire/docs", standardDir.resolve("_docs"));
            writeDeployedVersion(standardDir, currentVersion);
            writeWarningFile(standardDir);
            fileCount += deployWelcomeIfAbsent(ContentDirectoryManager.getCustomDir());
            long elapsedMs = System.currentTimeMillis() - startMs;
            LOGGER.info("Deployment complete: {} files written in {} ms", fileCount, elapsedMs);
         } catch (Exception e) {
            LOGGER.error("Content deployment failed; continuing without standard content on disk: {}", e.getMessage(), e);
         }
      }
   }

   static boolean shouldDeploy(String currentVersion, String deployedVersion) {
      if (deployedVersion == null) {
         return true;
      }

      try {
         return new ComparableVersion(deployedVersion).compareTo(new ComparableVersion(currentVersion)) != 0;
      } catch (Exception e) {
         LOGGER.warn("Could not compare versions '{}' vs '{}', forcing redeploy: {}", new Object[]{deployedVersion, currentVersion, e.getMessage()});
         return true;
      }
   }

   private static String currentModVersion() {
      try {
         IModInfo info = ((ModContainer)ModList.get()
               .getModContainerById("millenaire")
               .orElseThrow(() -> new IllegalStateException("Millenaire mod container not found")))
            .getModInfo();
         return info.getVersion().toString();
      } catch (Exception e) {
         LOGGER.warn("Could not resolve current mod version: {}", e.getMessage());
         return "0.0.0";
      }
   }

   private static String readDeployedVersion(Path standardDir) {
      Path versionPath = standardDir.resolve("_deployed_version.txt");
      if (!Files.isRegularFile(versionPath)) {
         return null;
      }

      try {
         return Files.readString(versionPath, StandardCharsets.UTF_8).trim();
      } catch (IOException e) {
         LOGGER.warn("Could not read {}: {}", versionPath, e.getMessage());
         return null;
      }
   }

   private static void writeDeployedVersion(Path standardDir, String version) throws IOException {
      Files.writeString(standardDir.resolve("_deployed_version.txt"), version + "\n", StandardCharsets.UTF_8);
   }

   private static void writeWarningFile(Path standardDir) throws IOException {
      Files.writeString(
         standardDir.resolve("WARNING - changes here will be overwritten on update.txt"),
         "The millenaire/ directory is auto-deployed by Millénaire on each new\nversion of the mod, erasing all changes made to it.\n\nIf you want to customise content, copy the relevant files to the\ncorresponding location in millenaire-custom/, which is never\ntouched by updates.\n\nMost files in millenaire-custom/ replace their equivalent in\nmillenaire/. The following are additive instead:\n  - Language files (<culture>_sentences.txt, <culture>_dialogues.txt)\n  - Namelists (*.txt under cultures/<culture>/namelists/)\n  - Traded goods (traded_goods.json per culture)\n\nSee docs/feat/custom-content.md for the full authoring workflow.\n",
         StandardCharsets.UTF_8
      );
   }

   static void wipeStandardDir(Path standardDir) throws IOException {
      if (Files.isDirectory(standardDir)) {
         Path standardRootReal = ContentDirectoryManager.getStandardRootReal();
         if (standardRootReal == null) {
            LOGGER.error("Aborting wipe: ContentDirectoryManager has no standardRootReal set");
         } else {
            Path liveReal;
            try {
               liveReal = standardDir.toRealPath();
            } catch (IOException e) {
               LOGGER.error("Aborting wipe: could not resolve real path of {}: {}", standardDir, e.getMessage());
               return;
            }

            if (!liveReal.equals(standardRootReal)) {
               LOGGER.error(
                  "Aborting wipe: standard dir {} resolves to {} which does not match the root captured at init ({}). Refusing to delete anything.",
                  new Object[]{standardDir, liveReal, standardRootReal}
               );
            } else {
               try (Stream<Path> walk = Files.walk(liveReal)) {
                  walk.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(liveReal)).forEach(p -> deleteIfInsideRoot(p, standardRootReal));
               }
            }
         }
      }
   }

   private static void deleteIfInsideRoot(Path p, Path standardRootReal) {
      try {
         if (Files.isSymbolicLink(p)) {
            Path parentReal = p.getParent() == null ? null : p.getParent().toRealPath();
            if (parentReal != null && parentReal.startsWith(standardRootReal)) {
               Files.delete(p);
               return;
            }

            LOGGER.warn("Skipping symlink whose parent escapes standard root: {}", p);
            return;
         }

         Path real = p.toRealPath();
         if (!real.startsWith(standardRootReal)) {
            LOGGER.warn("Skipping path that escapes standard root: {} → {}", p, real);
            return;
         }

         Files.delete(p);
      } catch (NoSuchFileException var3) {
      } catch (IOException e) {
         LOGGER.warn("Could not delete {}: {}", p, e.getMessage());
      }
   }

   private static int deployQuests(Path standardDir) {
      List<String> entries = readManifest("/millenaire/quests/_manifest.json");
      int count = 0;

      for (String entry : entries) {
         String jarPath = "/millenaire/quests/" + entry + ".json";
         Path target = standardDir.resolve("quests").resolve(entry + ".json");
         if (deployFile(jarPath, target)) {
            count++;
         }
      }

      return count + deployClasspathDir(standardDir, "/millenaire/quests/lang", null, fileName -> {
         String lang = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
         return standardDir.resolve("languages").resolve(lang).resolve("quest_lang.json");
      });
   }

   private static int deployLanguages(Path standardDir) {
      List<String> languages;
      try (InputStream in = NativeContentDeployer.class.getResourceAsStream("/millenaire/languages/_manifest.json")) {
         if (in == null) {
            LOGGER.warn("No languages manifest found");
            return 0;
         }

         JsonObject obj = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
         Type listType = (new TypeToken<List<String>>() {}).getType();
         languages = (List<String>)GSON.fromJson(obj.get("languages"), listType);
      } catch (Exception e) {
         LOGGER.warn("Could not read languages manifest: {}", e.getMessage());
         return 0;
      }

      if (languages == null) {
         return 0;
      }

      int count = 0;

      for (String lang : languages) {
         Path targetDir = standardDir.resolve("languages").resolve(lang);
         count += deployClasspathDir(standardDir, "/millenaire/languages/" + lang, targetDir, null);
      }

      return count;
   }

   private static List<String> readManifest(String resourcePath) {
      try (InputStream in = NativeContentDeployer.class.getResourceAsStream(resourcePath)) {
         if (in == null) {
            return List.of();
         }

         String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         JsonElement root = JsonParser.parseString(json);
         if (root.isJsonArray()) {
            Type listType = (new TypeToken<List<String>>() {}).getType();
            return (List<String>)GSON.fromJson(root, listType);
         }

         if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();

            for (String field : new String[]{"files", "languages", "entries"}) {
               if (obj.has(field) && obj.get(field).isJsonArray()) {
                  Type listType = (new TypeToken<List<String>>() {}).getType();
                  return (List<String>)GSON.fromJson(obj.get(field), listType);
               }
            }
         }
      } catch (Exception e) {
         LOGGER.warn("Could not read manifest {}: {}", resourcePath, e.getMessage());
      }

      return List.of();
   }

   private static List<String> listClasspathDir(String resourceDir) {
      URL url = NativeContentDeployer.class.getResource(resourceDir);
      if (url == null) {
         return List.of();
      }

      try {
         URI uri = url.toURI();
         if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
               Path dir = fs.getPath(resourceDir);
               return listJsonOrAny(dir);
            }
         } else {
            return listJsonOrAny(Paths.get(uri));
         }
      } catch (Exception e) {
         LOGGER.warn("Could not list classpath directory {}: {}", resourceDir, e.getMessage());
         return List.of();
      }
   }

   private static List<String> listJsonOrAny(Path dir) throws IOException {
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      try (Stream<Path> s = Files.list(dir)) {
         return s.filter(x$0 -> Files.isRegularFile(x$0)).map(p -> p.getFileName().toString()).collect(Collectors.toList());
      }
   }

   private static int deployClasspathDir(Path standardDir, String resourceDir, Path targetDir, Function<String, Path> targetMapper) {
      List<String> children = listClasspathDir(resourceDir);
      int count = 0;

      for (String child : children) {
         String jarPath = resourceDir + "/" + child;
         Path target = targetMapper != null ? targetMapper.apply(child) : targetDir.resolve(child);
         if (deployFile(jarPath, target)) {
            count++;
         }
      }

      return count;
   }

   static int deployClasspathDirRecursive(Path standardDir, String resourceDir, Path targetDir) {
      URL url = NativeContentDeployer.class.getResource(resourceDir);
      if (url == null) {
         return 0;
      }

      try {
         URI uri = url.toURI();
         if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
               return walkAndDeploy(fs.getPath(resourceDir), resourceDir, targetDir);
            }
         } else {
            return walkAndDeploy(Paths.get(uri), resourceDir, targetDir);
         }
      } catch (Exception e) {
         LOGGER.warn("Could not walk classpath directory {}: {}", resourceDir, e.getMessage());
         return 0;
      }
   }

   private static int walkAndDeploy(Path rootDir, String resourcePrefix, Path targetDir) throws IOException {
      if (!Files.isDirectory(rootDir)) {
         return 0;
      }

      int count = 0;

      try (Stream<Path> walk = Files.walk(rootDir)) {
         for (Path file : (Iterable<Path>)walk::iterator) {
            if (Files.isRegularFile(file)) {
               Path rel = rootDir.relativize(file);
               if (!segmentStartsWithUnderscore(rel)) {
                  String relText = rel.toString().replace('\\', '/');
                  String jarPath = resourcePrefix + "/" + relText;
                  Path target = targetDir.resolve(relText);
                  if (deployFile(jarPath, target)) {
                     count++;
                  }
               }
            }
         }
      }

      return count;
   }

   private static boolean segmentStartsWithUnderscore(Path relative) {
      for (Path segment : relative) {
         if (segment.toString().startsWith("_")) {
            return true;
         }
      }

      return false;
   }

   static int deployWelcomeIfAbsent(Path customDir) {
      Path customRootReal = ContentDirectoryManager.getCustomRootReal();
      if (customRootReal == null) {
         LOGGER.error("Cannot deploy welcome README: custom root is not initialised");
         return 0;
      }

      int written = 0;

      for (String basename : new String[]{"README.en.md", "README.fr.md"}) {
         Path target = customDir.resolve(basename);
         if (!Files.exists(target)) {
            Path customDirApparent = customDir.toAbsolutePath().normalize();
            Path normalised = target.toAbsolutePath().normalize();
            if (!normalised.startsWith(customDirApparent) && !normalised.startsWith(customRootReal)) {
               LOGGER.error("Refusing to write welcome README to {}: target escapes custom root ({})", target, customRootReal);
            } else {
               String jarPath = "/millenaire/welcome/" + basename;

               byte[] jarBytes;
               try (InputStream in = NativeContentDeployer.class.getResourceAsStream(jarPath)) {
                  if (in == null) {
                     LOGGER.debug("Welcome README missing from JAR: {}", jarPath);
                     continue;
                  }

                  jarBytes = in.readAllBytes();
               } catch (IOException e) {
                  LOGGER.warn("Could not read welcome README {}: {}", jarPath, e.getMessage());
                  continue;
               }

               try {
                  Files.createDirectories(target.getParent());
                  Files.write(target, jarBytes);
                  written++;
                  LOGGER.info("Deployed welcome README to {} (modder-owned, never overwritten)", target);
               } catch (IOException e) {
                  LOGGER.warn("Could not write welcome README {}: {}", target, e.getMessage());
               }
            }
         }
      }

      return written;
   }

   static boolean deployFile(String jarResourcePath, Path targetPath) {
      Path standardRootReal = ContentDirectoryManager.getStandardRootReal();
      if (standardRootReal == null) {
         LOGGER.error("Cannot deploy {}: standard root is not initialised", jarResourcePath);
         return false;
      }

      Path standardDirApparent = ContentDirectoryManager.getStandardDir().toAbsolutePath().normalize();
      Path normalised = targetPath.toAbsolutePath().normalize();
      if (!normalised.startsWith(standardDirApparent) && !normalised.startsWith(standardRootReal)) {
         LOGGER.error("Refusing to write {} to {}: target escapes standard root ({})", new Object[]{jarResourcePath, targetPath, standardRootReal});
         return false;
      }

      long sizeLimit = 1000000L;
      if (jarResourcePath.endsWith(".nbt")) {
         sizeLimit = 10000000L;
      } else if (jarResourcePath.endsWith(".txt")) {
         sizeLimit = 512000L;
      }

      byte[] jarBytes;
      try (InputStream in = NativeContentDeployer.class.getResourceAsStream(jarResourcePath)) {
         if (in == null) {
            LOGGER.debug("JAR resource missing: {}", jarResourcePath);
            return false;
         }

         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         byte[] chunk = new byte[8192];

         int read;
         while ((read = in.read(chunk)) != -1) {
            if (buf.size() + read > sizeLimit) {
               LOGGER.error("JAR resource {} exceeds size limit ({} bytes) — skipping", jarResourcePath, sizeLimit);
               return false;
            }

            buf.write(chunk, 0, read);
         }

         jarBytes = buf.toByteArray();
      } catch (IOException e) {
         LOGGER.warn("Could not read JAR resource {}: {}", jarResourcePath, e.getMessage());
         return false;
      }

      try {
         Files.createDirectories(targetPath.getParent());

         Path parentReal;
         try {
            parentReal = targetPath.getParent().toRealPath();
         } catch (IOException e) {
            LOGGER.warn("Could not resolve real path of {}: {}", targetPath.getParent(), e.getMessage());
            return false;
         }

         Path standardDirReal;
         try {
            standardDirReal = ContentDirectoryManager.getStandardDir().toRealPath();
         } catch (IOException e) {
            standardDirReal = standardRootReal;
         }

         if (!parentReal.startsWith(standardRootReal) && !parentReal.startsWith(standardDirReal)) {
            LOGGER.error("Refusing to write {} to {}: parent {} escapes standard root via symlink", new Object[]{jarResourcePath, targetPath, parentReal});
            return false;
         } else {
            Files.write(targetPath, jarBytes);
            return true;
         }
      } catch (IOException e) {
         LOGGER.warn("Could not deploy {} to {}: {}", new Object[]{jarResourcePath, targetPath, e.getMessage()});
         return false;
      }
   }
}
