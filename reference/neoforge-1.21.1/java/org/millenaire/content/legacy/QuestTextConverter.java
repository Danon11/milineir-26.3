package org.millenaire.content.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public class QuestTextConverter {
   private static final Path PROJECT_DIR = Path.of(System.getProperty("user.dir"));
   private static final Path LEGACY_ROOT = resolveLegacyRoot();
   private static final Path QUEST_LANG_OUTPUT = PROJECT_DIR.resolve("src/main/resources/millenaire/quests/lang");
   private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private int totalLanguages = 0;
   private int totalEntries = 0;
   private int totalWarnings = 0;

   public static void main(String[] args) {
      QuestTextConverter converter = new QuestTextConverter();

      try {
         converter.run();
      } catch (Exception e) {
         System.err.println("[FATAL] Quest text conversion failed: " + e.getMessage());
         e.printStackTrace();
         System.exit(1);
      }
   }

   private void run() throws IOException {
      Path languagesRoot = LEGACY_ROOT.resolve("languages");
      if (!Files.isDirectory(languagesRoot)) {
         System.err.println("[FATAL] Legacy languages directory not found: " + languagesRoot);
         System.exit(1);
      }

      System.out.println("=== QuestTextConverter ===");
      System.out.println("Legacy languages: " + languagesRoot);
      System.out.println("Output: " + QUEST_LANG_OUTPUT);
      Files.createDirectories(QUEST_LANG_OUTPUT);

      List<Path> langDirs;
      try (Stream<Path> stream = Files.list(languagesRoot)) {
         langDirs = stream.filter(x$0 -> Files.isDirectory(x$0)).sorted().toList();
      }

      for (Path langDir : langDirs) {
         this.processLanguage(langDir);
      }

      System.out.println("\n=== Summary ===");
      System.out.println("Languages processed: " + this.totalLanguages);
      System.out.println("Total entries: " + this.totalEntries);
      System.out.println("Warnings: " + this.totalWarnings);
      System.out.println("\n✓ Quest text conversion completed.");
   }

   private void processLanguage(Path langDir) throws IOException {
      String langCode = langDir.getFileName().toString();

      List<Path> questTextFiles;
      try (Stream<Path> stream = Files.list(langDir)) {
         questTextFiles = stream.filter(p -> {
            String name = p.getFileName().toString();
            return name.startsWith("quests_") && name.endsWith(".txt");
         }).sorted().toList();
      }

      if (!questTextFiles.isEmpty()) {
         Map<String, String> allTexts = new TreeMap<>();

         for (Path textFile : questTextFiles) {
            this.parseQuestTextFile(textFile, allTexts, langCode);
         }

         if (!allTexts.isEmpty()) {
            Path outputFile = QUEST_LANG_OUTPUT.resolve(langCode + ".json");

            try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
               this.gson.toJson(allTexts, writer);
            }

            this.totalLanguages++;
            this.totalEntries = this.totalEntries + allTexts.size();
            System.out.println("[" + langCode + "] " + allTexts.size() + " entries from " + questTextFiles.size() + " files");
         }
      }
   }

   private void parseQuestTextFile(Path textFile, Map<String, String> allTexts, String langCode) {
      try {
         for (String rawLine : Files.readAllLines(textFile, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (!line.isEmpty() && !line.startsWith("//")) {
               int eqIdx = line.indexOf(61);
               if (eqIdx >= 0) {
                  String key = line.substring(0, eqIdx).trim();
                  String value = line.substring(eqIdx + 1);
                  if (!key.isEmpty()) {
                     allTexts.put(key, value);
                  }
               }
            }
         }
      } catch (IOException e) {
         System.out.println("  [WARN] " + langCode + ": Failed to read " + textFile.getFileName() + ": " + e.getMessage());
         this.totalWarnings++;
      }
   }

   private static Path resolveLegacyRoot() {
      Path relative = PROJECT_DIR.resolve("../millenaire-1.12/content/millenaire");
      if (Files.isDirectory(relative)) {
         return relative;
      }

      try {
         ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--path-format=absolute", "--git-common-dir");
         pb.directory(PROJECT_DIR.toFile());
         pb.redirectErrorStream(true);
         Process p = pb.start();
         String gitCommonDir = new String(p.getInputStream().readAllBytes()).trim();
         p.waitFor();
         if (p.exitValue() == 0) {
            Path mainRepoRoot = Path.of(gitCommonDir).getParent();
            if (mainRepoRoot != null) {
               Path fromMainRepo = mainRepoRoot.resolve("../millenaire-1.12/content/millenaire");
               if (Files.isDirectory(fromMainRepo)) {
                  return fromMainRepo;
               }
            }
         }
      } catch (Exception var6) {
      }

      return relative;
   }
}
