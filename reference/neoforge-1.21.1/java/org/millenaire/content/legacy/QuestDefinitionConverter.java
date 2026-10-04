package org.millenaire.content.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class QuestDefinitionConverter {
   private static final Path PROJECT_DIR = Path.of(System.getProperty("user.dir"));
   private static final Path LEGACY_ROOT = resolveLegacyRoot();
   private static final Path QUEST_OUTPUT = PROJECT_DIR.resolve("src/main/resources/millenaire/quests");
   private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private int totalConverted = 0;
   private int totalWarnings = 0;
   private final List<String> allQuestKeys = new ArrayList<>();
   private QuestDefinitionConverter.Issues issues;
   private static final Map<String, String> QUEST_ITEM_MAP;

   public static void main(String[] args) {
      QuestDefinitionConverter converter = new QuestDefinitionConverter();

      try {
         converter.run();
      } catch (Exception e) {
         System.err.println("[FATAL] Quest conversion failed: " + e.getMessage());
         e.printStackTrace();
         System.exit(1);
      }
   }

   private void run() throws IOException {
      Path questsRoot = LEGACY_ROOT.resolve("quests");
      if (!Files.isDirectory(questsRoot)) {
         System.err.println("[FATAL] Legacy quests directory not found: " + questsRoot);
         System.exit(1);
      }

      System.out.println("=== QuestDefinitionConverter ===");
      System.out.println("Legacy quests: " + questsRoot);
      System.out.println("Output: " + QUEST_OUTPUT);

      List<Path> subdirs;
      try (Stream<Path> stream = Files.list(questsRoot)) {
         subdirs = stream.filter(x$0 -> Files.isDirectory(x$0)).sorted().toList();
      }

      for (Path subdir : subdirs) {
         this.processSubdirectory(subdir);
      }

      this.generateManifest();
      System.out.println("\n=== Summary ===");
      System.out.println("Quests converted: " + this.totalConverted);
      System.out.println("Warnings: " + this.totalWarnings);
      System.out.println("\n✓ Quest definition conversion completed.");
   }

   private void processSubdirectory(Path subdir) throws IOException {
      String subdirName = subdir.getFileName().toString();
      Path outputDir = QUEST_OUTPUT.resolve(subdirName);
      Files.createDirectories(outputDir);

      List<Path> questFiles;
      try (Stream<Path> stream = Files.list(subdir)) {
         questFiles = stream.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
      }

      System.out.println("\n[" + subdirName + "] " + questFiles.size() + " quest files");

      for (Path questFile : questFiles) {
         try {
            this.convertQuestFile(questFile, outputDir, subdirName);
            this.totalConverted++;
         } catch (Exception e) {
            System.err.println("  [ERROR] " + questFile.getFileName() + ": " + e.getMessage());
            this.totalWarnings++;
         }
      }
   }

   private void convertQuestFile(Path questFile, Path outputDir, String subdirName) throws IOException {
      String fileName = questFile.getFileName().toString();
      String questKey = fileName.substring(0, fileName.length() - ".txt".length());
      Map<String, Object> quest = this.buildQuestMap(questFile, questKey);
      Path outputFile = outputDir.resolve(questKey + ".json");

      try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
         this.gson.toJson(quest, writer);
      }

      List<Map<String, Object>> steps = (List<Map<String, Object>>)quest.get("steps");
      List<Map<String, Object>> villagerDefs = (List<Map<String, Object>>)quest.get("villagerDefs");
      this.allQuestKeys.add(subdirName + "/" + questKey);
      System.out.println("  ✓ " + questKey + " (" + steps.size() + " steps, " + villagerDefs.size() + " villagers)");
   }

   public static Map<String, Object> parseQuest(Path questFile) throws IOException {
      return parseQuest(questFile, null);
   }

   public static Map<String, Object> parseQuest(Path questFile, QuestDefinitionConverter.Issues sink) throws IOException {
      QuestDefinitionConverter scratch = new QuestDefinitionConverter();
      scratch.issues = sink;
      String fileName = questFile.getFileName().toString();
      String questKey = fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - ".txt".length()) : fileName;
      return scratch.buildQuestMap(questFile, questKey);
   }

   private Map<String, Object> buildQuestMap(Path questFile, String questKey) throws IOException {
      List<String> lines = Files.readAllLines(questFile, StandardCharsets.UTF_8);
      double chancePerHour = 0.0;
      int maxSimultaneous = 5;
      int minReputation = 0;
      List<String> globalTagsRequired = new ArrayList<>();
      List<String> globalTagsForbidden = new ArrayList<>();
      List<String> playerTagsRequired = new ArrayList<>();
      List<String> playerTagsForbidden = new ArrayList<>();
      List<Map<String, Object>> villagerDefs = new ArrayList<>();
      List<Map<String, Object>> steps = new ArrayList<>();
      Map<String, Object> currentStep = null;
      int stepIndex = -1;

      for (String rawLine : lines) {
         String line = rawLine.trim();
         if (!line.isEmpty() && !line.startsWith("//")) {
            int colonIdx = line.indexOf(58);
            if (colonIdx < 0) {
               this.warn(questKey, "No colon in line: " + line);
            } else {
               String key = line.substring(0, colonIdx).trim().toLowerCase();
               String value = line.substring(colonIdx + 1).trim();
               if (key.equals("definevillager")) {
                  villagerDefs.add(this.parseVillagerDef(value, questKey));
               } else if (key.equals("step") && value.equals("new")) {
                  currentStep = this.newStep(++stepIndex);
                  steps.add(currentStep);
               } else if (currentStep == null) {
                  switch (key) {
                     case "minreputation":
                        minReputation = evaluateArithmetic(value);
                        break;
                     case "chanceperhour":
                        chancePerHour = Double.parseDouble(value.trim());
                        break;
                     case "maxsimultaneous":
                        maxSimultaneous = Integer.parseInt(value.trim());
                        break;
                     case "requiredplayertag":
                        playerTagsRequired.add(value);
                        break;
                     case "forbiddenplayertag":
                        playerTagsForbidden.add(value);
                        break;
                     case "requiredglobaltag":
                        globalTagsRequired.add(value);
                        break;
                     case "forbiddenglobaltag":
                        globalTagsForbidden.add(value);
                        break;
                     default:
                        this.warn(questKey, "Unknown quest-level key (before step:new): " + key);
                  }
               } else {
                  switch (key) {
                     case "minreputation":
                        minReputation = evaluateArithmetic(value);
                        break;
                     case "chanceperhour":
                        chancePerHour = Double.parseDouble(value.trim());
                        break;
                     case "maxsimultaneous":
                        maxSimultaneous = Integer.parseInt(value.trim());
                        break;
                     case "requiredplayertag":
                        playerTagsRequired.add(value);
                        break;
                     case "forbiddenplayertag":
                        playerTagsForbidden.add(value);
                        break;
                     case "requiredglobaltag":
                        globalTagsRequired.add(value);
                        break;
                     case "forbiddenglobaltag":
                        globalTagsForbidden.add(value);
                        break;
                     case "villager":
                        currentStep.put("villagerKey", value);
                        break;
                     case "duration":
                        currentStep.put("duration", evaluateArithmetic(value));
                        break;
                     case "showrequiredgoods":
                        currentStep.put("showRequiredGoods", Boolean.parseBoolean(value));
                        break;
                     case "requiredgood":
                        this.addGood(currentStep, "requiredGoods", value, questKey);
                        break;
                     case "rewardgood":
                        this.addGood(currentStep, "rewardGoods", value, questKey);
                        break;
                     case "rewardreputation":
                        currentStep.put("rewardReputation", evaluateArithmetic(value));
                        break;
                     case "rewardmoney":
                        currentStep.put("rewardMoney", evaluateArithmetic(value));
                        break;
                     case "penaltyreputation":
                        currentStep.put("penaltyReputation", evaluateArithmetic(value));
                        break;
                     case "settagsuccess":
                        this.addVillagerTag(currentStep, "villagerTagsSuccess", value);
                        break;
                     case "settagfailure":
                        this.addVillagerTag(currentStep, "villagerTagsFailure", value);
                        break;
                     case "setplayertagsuccess":
                        this.addToList(currentStep, "playerTagsSuccess", value);
                        break;
                     case "setplayertagfailure":
                        this.addToList(currentStep, "playerTagsFailure", value);
                        break;
                     case "setglobaltagsuccess":
                        this.addToList(currentStep, "globalTagsSuccess", value);
                        break;
                     case "setglobaltagfailure":
                        this.addToList(currentStep, "globalTagsFailure", value);
                        break;
                     case "cleartagsuccess":
                        this.addVillagerTag(currentStep, "clearTagsSuccess", value);
                        break;
                     case "cleartagfailure":
                        this.addVillagerTag(currentStep, "clearTagsFailure", value);
                        break;
                     case "clearplayertagsuccess":
                        this.addToList(currentStep, "clearPlayerTagsSuccess", value);
                        break;
                     case "clearplayertagfailure":
                        this.addToList(currentStep, "clearPlayerTagsFailure", value);
                        break;
                     case "clearglobaltagsuccess":
                        this.addToList(currentStep, "clearGlobalTagsSuccess", value);
                        break;
                     case "clearglobaltagfailure":
                        this.addToList(currentStep, "clearGlobalTagsFailure", value);
                        break;
                     case "steprequiredplayertag":
                        this.addToList(currentStep, "stepRequiredPlayerTags", value);
                        break;
                     case "stepforbiddenplayertag":
                        this.addToList(currentStep, "stepForbiddenPlayerTags", value);
                        break;
                     case "steprequiredglobaltag":
                        this.addToList(currentStep, "stepRequiredGlobalTags", value);
                        break;
                     case "stepforbiddenglobaltag":
                        this.addToList(currentStep, "stepForbiddenGlobalTags", value);
                        break;
                     case "setactiondatasuccess":
                        this.addActionData(currentStep, "actionDataSuccess", value);
                        break;
                     case "relationchange":
                        this.addRelationChange(currentStep, value);
                        break;
                     case "bedrockbuilding":
                        this.addBedrockBuilding(currentStep, value);
                        break;
                     default:
                        this.warn(questKey, "Unknown step key: " + key + " = " + value);
                  }
               }
            }
         }
      }

      if (steps.isEmpty()) {
         this.structural(questKey, 0, "Quest has no steps");
      } else {
         for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> s = steps.get(i);
            String villagerKey = String.valueOf(s.getOrDefault("villagerKey", ""));
            if (villagerKey.isEmpty()) {
               this.structural(questKey, i + 1, "Step has no 'villager:' directive");
            }

            Object duration = s.get("duration");
            if (duration == null || duration instanceof Number n && n.intValue() == 0) {
               this.structural(questKey, i + 1, "Step has no 'duration:' (will use default)");
            }
         }
      }

      for (Map<String, Object> def : villagerDefs) {
         Object key = def.get("key");
         if (key == null || String.valueOf(key).isEmpty()) {
            this.structural(questKey, 0, "definevillager missing 'key=' parameter");
         }

         List<String> types = (List<String>)def.getOrDefault("villagerTypes", List.of());
         List<String> requiredTags = (List<String>)def.getOrDefault("requiredTags", List.of());
         List<String> forbiddenTags = (List<String>)def.getOrDefault("forbiddenTags", List.of());
         if (types.isEmpty() && requiredTags.isEmpty() && forbiddenTags.isEmpty()) {
            this.structural(questKey, 0, "definevillager '" + key + "' missing 'type=' and 'requiredtag=' — villager cannot be matched");
         }

         for (String type : types) {
            if (!type.contains("/")) {
               if (this.issues != null) {
                  this.issues.report().recordMissingCulturePrefix(null, this.issues.filePath(), "definevillager.type", type);
               }

               System.out.println("  [WARN] " + questKey + ": Villager type '" + type + "' missing culture prefix (expected 'culture/type')");
               this.totalWarnings++;
            }
         }
      }

      Map<String, Object> quest = new LinkedHashMap<>();
      quest.put("key", questKey);
      quest.put("chancePerHour", chancePerHour);
      quest.put("maxSimultaneous", maxSimultaneous);
      quest.put("minReputation", minReputation);
      quest.put("globalTagsRequired", globalTagsRequired);
      quest.put("globalTagsForbidden", globalTagsForbidden);
      quest.put("playerTagsRequired", playerTagsRequired);
      quest.put("playerTagsForbidden", playerTagsForbidden);
      quest.put("villagerDefs", villagerDefs);
      quest.put("steps", steps);
      return quest;
   }

   private void structural(String questKey, int step, String problem) {
      System.out.println("  [WARN] " + questKey + ": " + problem);
      this.totalWarnings++;
      if (this.issues != null) {
         this.issues.report().recordQuestStructural(this.issues.questKey(), this.issues.filePath(), step, problem);
      }
   }

   private Map<String, Object> parseVillagerDef(String value, String questKey) {
      Map<String, Object> def = new LinkedHashMap<>();
      List<String> villagerTypes = new ArrayList<>();
      List<String> requiredTags = new ArrayList<>();
      List<String> forbiddenTags = new ArrayList<>();
      String defKey = null;
      String relatedTo = null;
      String relation = null;
      String[] pairs = value.split(",");

      for (String pair : pairs) {
         int eqIdx = pair.indexOf(61);
         if (eqIdx < 0) {
            this.warn(questKey, "Invalid definevillager pair: " + pair);
         } else {
            String k = pair.substring(0, eqIdx).trim().toLowerCase();
            String v = pair.substring(eqIdx + 1).trim();
            switch (k) {
               case "key":
                  defKey = v;
                  break;
               case "type":
                  villagerTypes.add(v);
                  break;
               case "relatedto":
                  relatedTo = v;
                  break;
               case "relation":
                  relation = v;
                  break;
               case "requiredtag":
                  requiredTags.add(v);
                  break;
               case "forbiddentag":
                  forbiddenTags.add(v);
                  break;
               default:
                  this.warn(questKey, "Unknown definevillager field: " + k);
            }
         }
      }

      def.put("key", defKey);
      def.put("villagerTypes", villagerTypes);
      def.put("relatedTo", relatedTo);
      def.put("relation", relation);
      def.put("requiredTags", requiredTags);
      def.put("forbiddenTags", forbiddenTags);
      return def;
   }

   private Map<String, Object> newStep(int index) {
      Map<String, Object> step = new LinkedHashMap<>();
      step.put("index", index);
      step.put("villagerKey", "");
      step.put("duration", 0);
      step.put("showRequiredGoods", true);
      step.put("requiredGoods", new LinkedHashMap());
      step.put("rewardGoods", new LinkedHashMap());
      step.put("rewardMoney", 0);
      step.put("rewardReputation", 0);
      step.put("penaltyReputation", 0);
      step.put("villagerTagsSuccess", new ArrayList());
      step.put("villagerTagsFailure", new ArrayList());
      step.put("playerTagsSuccess", new ArrayList());
      step.put("playerTagsFailure", new ArrayList());
      step.put("globalTagsSuccess", new ArrayList());
      step.put("globalTagsFailure", new ArrayList());
      step.put("clearTagsSuccess", new ArrayList());
      step.put("clearTagsFailure", new ArrayList());
      step.put("clearPlayerTagsSuccess", new ArrayList());
      step.put("clearPlayerTagsFailure", new ArrayList());
      step.put("clearGlobalTagsSuccess", new ArrayList());
      step.put("clearGlobalTagsFailure", new ArrayList());
      step.put("stepRequiredGlobalTags", new ArrayList());
      step.put("stepForbiddenGlobalTags", new ArrayList());
      step.put("stepRequiredPlayerTags", new ArrayList());
      step.put("stepForbiddenPlayerTags", new ArrayList());
      step.put("actionDataSuccess", new ArrayList());
      step.put("relationChanges", new ArrayList());
      step.put("bedrockBuildings", new ArrayList());
      return step;
   }

   private void addGood(Map<String, Object> step, String field, String value, String questKey) {
      String[] parts = value.split(",");
      if (parts.length < 2) {
         this.warn(questKey, "Invalid good format: " + value);
      } else {
         String legacyItem = parts[0].trim();
         int count = evaluateArithmetic(parts[1].trim());
         String modernItem = this.mapItem(legacyItem, questKey);
         if (modernItem != null) {
            Map<String, Integer> goods = (Map<String, Integer>)step.get(field);
            goods.merge(modernItem, count, Integer::sum);
         }
      }
   }

   private String mapItem(String legacyItem, String questKey) {
      String mapped = QUEST_ITEM_MAP.get(legacyItem);
      if (mapped == null) {
         mapped = QUEST_ITEM_MAP.get(legacyItem.toLowerCase());
      }

      if (mapped == null) {
         if (legacyItem.contains(":")) {
            return legacyItem;
         }

         this.warn(questKey, "Unmapped quest item: " + legacyItem + " — dropping (add a mapping in QUEST_ITEM_MAP)");
         return null;
      } else {
         return mapped;
      }
   }

   private void addVillagerTag(Map<String, Object> step, String field, String value) {
      String[] parts = value.split(",");
      if (parts.length >= 2) {
         Map<String, String> tag = new LinkedHashMap<>();
         tag.put("villagerKey", parts[0].trim());
         tag.put("tag", parts[1].trim());
         ((List)step.get(field)).add(tag);
      }
   }

   private void addToList(Map<String, Object> step, String field, String value) {
      ((List)step.get(field)).add(value.trim());
   }

   private void addActionData(Map<String, Object> step, String field, String value) {
      String[] parts = value.split(",");
      if (parts.length >= 2) {
         Map<String, String> entry = new LinkedHashMap<>();
         entry.put("key", parts[0].trim());
         entry.put("value", parts[1].trim());
         ((List)step.get(field)).add(entry);
      }
   }

   private void addRelationChange(Map<String, Object> step, String value) {
      String[] parts = value.split(",");
      if (parts.length >= 3) {
         Map<String, Object> change = new LinkedHashMap<>();
         change.put("firstVillager", parts[0].trim());
         change.put("secondVillager", parts[1].trim());
         change.put("change", evaluateArithmetic(parts[2].trim()));
         ((List)step.get("relationChanges")).add(change);
      }
   }

   private void addBedrockBuilding(Map<String, Object> step, String value) {
      String[] parts = value.split(",");
      if (parts.length >= 2) {
         Map<String, String> building = new LinkedHashMap<>();
         building.put("culture", parts[0].trim());
         building.put("villageType", parts[1].trim());
         ((List)step.get("bedrockBuildings")).add(building);
      }
   }

   static int evaluateArithmetic(String expr) {
      expr = expr.trim();
      if (!expr.contains("*")) {
         return Integer.parseInt(expr);
      }

      String[] parts = expr.split("\\*");
      int result = 1;

      for (String part : parts) {
         result *= Integer.parseInt(part.trim());
      }

      return result;
   }

   private void generateManifest() throws IOException {
      this.allQuestKeys.sort(String::compareTo);
      Path manifestFile = QUEST_OUTPUT.resolve("_manifest.json");

      try (Writer writer = Files.newBufferedWriter(manifestFile, StandardCharsets.UTF_8)) {
         this.gson.toJson(this.allQuestKeys, writer);
      }

      System.out.println("\nManifest: " + this.allQuestKeys.size() + " entries → " + manifestFile);
   }

   private void warn(String questKey, String message) {
      System.out.println("  [WARN] " + questKey + ": " + message);
      this.totalWarnings++;
      if (this.issues != null) {
         this.issues.report().recordQuestStructural(this.issues.questKey(), this.issues.filePath(), 0, message);
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

   static {
      Map<String, String> m = new LinkedHashMap<>();
      m.put("cake", "minecraft:cake");
      m.put("bread", "minecraft:bread");
      m.put("apple", "minecraft:apple");
      m.put("cookie", "minecraft:cookie");
      m.put("cobblestone", "minecraft:cobblestone");
      m.put("stone", "minecraft:stone");
      m.put("iron", "minecraft:iron_ingot");
      m.put("gold", "minecraft:gold_ingot");
      m.put("diamond", "minecraft:diamond");
      m.put("coal", "minecraft:coal");
      m.put("redstone", "minecraft:redstone");
      m.put("obsidian", "minecraft:obsidian");
      m.put("paper", "minecraft:paper");
      m.put("book", "minecraft:book");
      m.put("bone", "minecraft:bone");
      m.put("leather", "minecraft:leather");
      m.put("feather", "minecraft:feather");
      m.put("egg", "minecraft:egg");
      m.put("string", "minecraft:string");
      m.put("flint", "minecraft:flint");
      m.put("ironore", "minecraft:raw_iron");
      m.put("goldore", "minecraft:raw_gold");
      m.put("dye_black", "minecraft:black_dye");
      m.put("dye_red", "minecraft:red_dye");
      m.put("dye_yellow", "minecraft:yellow_dye");
      m.put("dye_blue", "minecraft:lapis_lazuli");
      m.put("dye_lightblue", "minecraft:light_blue_dye");
      m.put("dye_brown", "minecraft:cocoa_beans");
      m.put("steelsword", "minecraft:iron_sword");
      m.put("steelhelmet", "minecraft:iron_helmet");
      m.put("steelchest", "minecraft:iron_chestplate");
      m.put("steellegs", "minecraft:iron_leggings");
      m.put("steelboots", "minecraft:iron_boots");
      m.put("diamondsword", "minecraft:diamond_sword");
      m.put("bow", "minecraft:bow");
      m.put("arrow", "minecraft:arrow");
      m.put("bucketempty", "minecraft:bucket");
      m.put("bucketwater", "minecraft:water_bucket");
      m.put("bucketlava", "minecraft:lava_bucket");
      m.put("mushroomred", "minecraft:red_mushroom");
      m.put("mushroombrown", "minecraft:brown_mushroom");
      m.put("vines", "minecraft:vine");
      m.put("rottenflesh", "minecraft:rotten_flesh");
      m.put("spidereye", "minecraft:spider_eye");
      m.put("ghasttear", "minecraft:ghast_tear");
      m.put("gunpowder", "minecraft:gunpowder");
      m.put("tnt", "minecraft:tnt");
      m.put("netherwart", "minecraft:nether_wart");
      m.put("cactus", "minecraft:cactus");
      m.put("sugarcane", "minecraft:sugar_cane");
      m.put("pumpkin", "minecraft:pumpkin");
      m.put("enderpearl", "minecraft:ender_pearl");
      m.put("yellowflower", "minecraft:dandelion");
      m.put("redflower", "minecraft:poppy");
      m.put("fishraw", "minecraft:cod");
      m.put("fishcooked", "minecraft:cooked_cod");
      m.put("chickenmeat", "minecraft:chicken");
      m.put("beefraw", "minecraft:beef");
      m.put("rabbit", "minecraft:rabbit");
      m.put("potato", "minecraft:potato");
      m.put("seeds", "minecraft:wheat_seeds");
      m.put("sapling", "minecraft:oak_sapling");
      m.put("sapling_birch", "minecraft:birch_sapling");
      m.put("ironnugget", "minecraft:iron_nugget");
      m.put("cider", "millenaire:cider");
      m.put("calva", "millenaire:calva");
      m.put("boudin", "millenaire:boudin");
      m.put("tripes", "millenaire:tripes");
      m.put("tapestry", "millenaire:wall_tapestry");
      m.put("normansword", "millenaire:norman_sword");
      m.put("normanbroadsword", "millenaire:norman_sword");
      m.put("normanaxe", "millenaire:norman_axe");
      m.put("normanpickaxe", "millenaire:norman_pickaxe");
      m.put("normanshovel", "millenaire:norman_shovel");
      m.put("rasgulla", "millenaire:rasgulla");
      m.put("rice", "millenaire:rice");
      m.put("turmeric", "millenaire:turmeric");
      m.put("chickencurry", "millenaire:chickencurry");
      m.put("vegcurry", "millenaire:vegcurry");
      m.put("cotton", "millenaire:cotton");
      m.put("cacauhaa", "millenaire:cacauhaa");
      m.put("winefancy", "millenaire:winefancy");
      m.put("bearmeat_raw", "millenaire:bearmeat_raw");
      m.put("inuitpotatostew", "millenaire:inuitpotatostew");
      m.put("ayran", "millenaire:ayran");
      m.put("pide", "millenaire:pide");
      m.put("helva", "millenaire:helva");
      m.put("mudbrick", "millenaire:mud_brick");
      m.put("mudbrick_seljuk_ornamented", "millenaire:mud_brick_seljuk_ornamented");
      m.put("unknownpowder", "millenaire:unknown_powder");
      m.put("alchemistexplosive", "millenaire:alchemist_explosive");
      m.put("alchemist_amulet", "millenaire:alchemist_amulet");
      m.put("vishnu_amulet", "millenaire:vishnu_amulet");
      m.put("parchment_sadhu", "millenaire:parchment_sadhu");
      m.put("mayanquestcrown", "millenaire:mayan_quest_crown");
      m.put("enchantedsword", "minecraft:iron_sword");
      QUEST_ITEM_MAP = Collections.unmodifiableMap(m);
   }

   public record Issues(LegacyConversionReport report, String questKey, String filePath) {
   }
}
