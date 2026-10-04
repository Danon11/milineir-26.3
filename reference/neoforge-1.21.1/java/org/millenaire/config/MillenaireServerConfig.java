package org.millenaire.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

public final class MillenaireServerConfig {
   public static final MillenaireServerConfig SERVER;
   public static final ModConfigSpec SPEC;
   public final BooleanValue generateVillages;
   public final BooleanValue generateLoneBuildings;
   public final BooleanValue generateHamlets;
   public final IntValue minVillageDistance;
   public final IntValue minLoneBuildingDistance;
   public final IntValue minVillageLoneBuildingDistance;
   public final IntValue spawnProtectionRadius;
   public final IntValue completionMaxPercentage;
   public final IntValue completionMinDistance;
   public final IntValue completionMaxDistance;
   public final BooleanValue logSpawnAttempts;
   public final IntValue keepActiveRadius;
   public final IntValue villageRadiusOverride;
   public final IntValue minBuildingSpacing;
   public final BooleanValue buildPaths;
   public final IntValue maxChildren;
   public final IntValue backgroundRadius;
   public final BooleanValue languageLearning;
   public final BooleanValue travelBookLearning;
   public final IntValue sentenceDistanceSingleplayer;
   public final IntValue sentenceDistanceMultiplayer;
   public final BooleanValue sendStatistics;
   public final BooleanValue sendPlayerName;
   public final IntValue banditRaidRadius;
   public final IntValue raidingRate;
   public final BooleanValue legacyAutoConvert;
   public final IntValue legacyAutoConvertMaxPngs;

   private MillenaireServerConfig(Builder builder) {
      builder.comment("World generation settings").push("generation");
      this.generateVillages = builder.comment("Generate Millenaire villages in new chunks")
         .translation("millenaire.config.generateVillages")
         .define("generateVillages", true);
      this.generateLoneBuildings = builder.comment("Generate lone buildings (inns, shrines, ruins) in new chunks")
         .translation("millenaire.config.generateLoneBuildings")
         .define("generateLoneBuildings", true);
      this.generateHamlets = builder.comment(
            new String[]{
               "Generate hamlet satellite villages around parent village types (e.g. Gros Bourg).",
               "When disabled, parent types that define hamlets are excluded from natural spawn.",
               "iso-legacy: disabled by default (hamlets take a lot of space)"
            }
         )
         .translation("millenaire.config.generateHamlets")
         .define("generateHamlets", false);
      this.minVillageDistance = builder.comment("Minimum distance between two villages (blocks)")
         .translation("millenaire.config.minVillageDistance")
         .defineInRange("minVillageDistance", 500, 300, 1000);
      this.minLoneBuildingDistance = builder.comment("Minimum distance between two lone buildings (blocks)")
         .translation("millenaire.config.minLoneBuildingDistance")
         .defineInRange("minLoneBuildingDistance", 500, 300, 1000);
      this.minVillageLoneBuildingDistance = builder.comment("Minimum distance between a village and a lone building (blocks)")
         .translation("millenaire.config.minVillageLoneBuildingDistance")
         .defineInRange("minVillageLoneBuildingDistance", 250, 100, 800);
      this.spawnProtectionRadius = builder.comment("Protected area around world spawn where nothing generates (blocks)")
         .translation("millenaire.config.spawnProtectionRadius")
         .defineInRange("spawnProtectionRadius", 250, 0, 500);
      builder.comment("Progressive completion for distant villages").push("completion");
      this.completionMaxPercentage = builder.comment("Maximum initial progress % for distant villages")
         .translation("millenaire.config.completionMaxPercentage")
         .defineInRange("maxPercentage", 25, 0, 100);
      this.completionMinDistance = builder.comment("Distance from spawn where initial progress starts (blocks)")
         .translation("millenaire.config.completionMinDistance")
         .defineInRange("minDistance", 2000, 0, 25000);
      this.completionMaxDistance = builder.comment("Distance from spawn where max initial progress is reached (blocks)")
         .translation("millenaire.config.completionMaxDistance")
         .defineInRange("maxDistance", 10000, 0, 100000);
      builder.pop();
      builder.pop();
      builder.comment("Debug and logging settings").push("debug");
      this.logSpawnAttempts = builder.comment("Log every village/lone building spawn attempt with rejection reasons")
         .translation("millenaire.config.logSpawnAttempts")
         .define("logSpawnAttempts", false);
      builder.pop();
      builder.comment("Village behaviour settings").push("village");
      this.keepActiveRadius = builder.comment("Radius for keeping village chunks loaded (blocks). 0 = disabled.")
         .translation("millenaire.config.keepActiveRadius")
         .defineInRange("keepActiveRadius", 200, 0, 2000);
      this.villageRadiusOverride = builder.comment(
            new String[]{"Override all village type radii with this value (blocks).", "-1 = use per-type JSON value. Requires world restart to take effect."}
         )
         .translation("millenaire.config.villageRadiusOverride")
         .worldRestart()
         .defineInRange("villageRadiusOverride", -1, -1, 120);
      this.minBuildingSpacing = builder.comment("Minimum spacing between buildings in a village (blocks)")
         .translation("millenaire.config.minBuildingSpacing")
         .worldRestart()
         .defineInRange("minBuildingSpacing", 5, 0, 10);
      this.buildPaths = builder.comment("Generate and upgrade paths between village buildings (lateral paths Pass 3)")
         .translation("millenaire.config.buildPaths")
         .define("buildPaths", true);
      this.maxChildren = builder.comment("Maximum number of children per village")
         .translation("millenaire.config.maxChildren")
         .defineInRange("maxChildren", 10, 2, 20);
      this.backgroundRadius = builder.comment("Radius for inter-village relations: diplomacy, trade, raids (blocks). 0 = disabled.")
         .translation("millenaire.config.backgroundRadius")
         .defineInRange("backgroundRadius", 2000, 0, 3000);
      builder.pop();
      builder.comment("Gameplay settings").push("gameplay");
      this.languageLearning = builder.comment("Whether NPC languages need to be learned through interaction")
         .translation("millenaire.config.languageLearning")
         .define("languageLearning", true);
      this.travelBookLearning = builder.comment("Whether Travel Book content needs to be discovered through interaction (nearby villagers, buildings, trade)")
         .translation("millenaire.config.travelBookLearning")
         .define("travelBookLearning", true);
      this.sentenceDistanceSingleplayer = builder.comment("Distance for villager sentences in chat — singleplayer (blocks). 0 = disabled.")
         .translation("millenaire.config.sentenceDistanceSP")
         .defineInRange("sentenceDistanceSingleplayer", 6, 0, 10);
      this.sentenceDistanceMultiplayer = builder.comment("Distance for villager sentences in chat — multiplayer (blocks). 0 = disabled.")
         .translation("millenaire.config.sentenceDistanceMP")
         .defineInRange("sentenceDistanceMultiplayer", 0, 0, 10);
      builder.pop();
      builder.comment("Anonymous usage statistics sent to millenaire.org").push("statistics");
      this.sendStatistics = builder.comment(
            new String[]{
               "Send anonymous usage statistics (mod version, advancement progress) once per session.",
               "No personally identifiable information is sent unless sendPlayerName is enabled."
            }
         )
         .translation("millenaire.config.sendStatistics")
         .define("sendStatistics", true);
      this.sendPlayerName = builder.comment("Include the player's Minecraft username in the usage report. Off by default.")
         .translation("millenaire.config.sendPlayerName")
         .define("sendPlayerName", false);
      builder.pop();
      builder.comment("Raid settings (combat AI not yet implemented — values reserved)").push("raids");
      this.banditRaidRadius = builder.comment("TODO: Radius for bandit raids (blocks). 0 = disabled.")
         .translation("millenaire.config.banditRaidRadius")
         .defineInRange("banditRaidRadius", 1500, 0, 2000);
      this.raidingRate = builder.comment("TODO: % chance per night of a raid attempt. 0 = disabled.")
         .translation("millenaire.config.raidingRate")
         .defineInRange("raidingRate", 20, 0, 100);
      builder.pop();
      builder.comment("Automatic conversion of legacy 1.12 content packs dropped into millenaire-custom/").push("legacy");
      this.legacyAutoConvert = builder.comment(
            new String[]{
               "Enable automatic conversion of legacy 1.12 Millenaire content packs",
               "dropped into millenaire-custom/. When true, TXT/PNG files are detected",
               "at server start, converted to JSON/NBT in place, and originals renamed",
               "to .legacy. Set to false if you prefer running /millenaire dev convert-addon",
               "manually (required for read-only filesystems, large packs, or custom",
               "staging workflows)."
            }
         )
         .translation("millenaire.config.legacyAutoConvert")
         .define("legacyAutoConvert", true);
      this.legacyAutoConvertMaxPngs = builder.comment(
            new String[]{
               "Per-boot cap on PNG file count before auto-conversion refuses to run.",
               "Large packs should use /millenaire dev convert-addon during a maintenance",
               "window to avoid multi-minute boot freezes — cold-JVM PngToNbtConverter",
               "costs ~30-60s per legacy culture."
            }
         )
         .translation("millenaire.config.legacyAutoConvertMaxPngs")
         .defineInRange("legacyAutoConvertMaxPngs", 300, 0, 5000);
      builder.pop();
   }

   static {
      Pair<MillenaireServerConfig, ModConfigSpec> pair = new Builder().configure(MillenaireServerConfig::new);
      SERVER = (MillenaireServerConfig)pair.getLeft();
      SPEC = (ModConfigSpec)pair.getRight();
   }
}
