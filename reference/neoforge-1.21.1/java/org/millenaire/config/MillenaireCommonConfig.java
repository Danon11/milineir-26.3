package org.millenaire.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

public final class MillenaireCommonConfig {
   public static final MillenaireCommonConfig COMMON;
   public static final ModConfigSpec SPEC;
   public final IntValue logAi;
   public final IntValue logPathfinding;
   public final IntValue logConstruction;
   public final IntValue logGathering;
   public final IntValue logCommerce;
   public final IntValue logVillage;
   public final IntValue logWorldGen;
   public final IntValue logCulture;
   public final IntValue logNetwork;
   public final IntValue logChunkLoading;
   public final IntValue logDiplomacy;
   public final IntValue logOther;

   private MillenaireCommonConfig(Builder builder) {
      builder.comment(
            new String[]{
               "Per-category log levels (0=none, 1=basic, 2=detailed, 3=verbose).",
               "Iso-legacy: individual Log* fields in MillConfigValues.",
               "Change these at runtime by editing the TOML file — values reload automatically."
            }
         )
         .push("log");
      this.logAi = defineLog(builder, "ai", "General AI, goal scheduling");
      this.logPathfinding = defineLog(builder, "pathfinding", "Navigation, waypoints, A*");
      this.logConstruction = defineLog(builder, "construction", "Building, placement, paths");
      this.logGathering = defineLog(builder, "gathering", "All gathering handlers");
      this.logCommerce = defineLog(builder, "commerce", "Trade, selling");
      this.logVillage = defineLog(builder, "village", "Village lifecycle, children, spawning");
      this.logWorldGen = defineLog(builder, "worldGen", "World generation, site validation");
      this.logCulture = defineLog(builder, "culture", "Culture loading, translations");
      this.logNetwork = defineLog(builder, "network", "Network packets");
      this.logChunkLoading = defineLog(builder, "chunkLoading", "Chunk loading/unloading");
      this.logDiplomacy = defineLog(builder, "diplomacy", "Inter-village relations");
      this.logOther = defineLog(builder, "other", "Miscellaneous");
      builder.pop();
   }

   private static IntValue defineLog(Builder builder, String key, String comment) {
      return builder.comment(comment).translation("millenaire.config.log." + key).defineInRange(key, 0, 0, 3);
   }

   public void bindLogCategories() {
      MillLog.LogCategory.AI.bind(this.logAi::getAsInt);
      MillLog.LogCategory.PATHFINDING.bind(this.logPathfinding::getAsInt);
      MillLog.LogCategory.CONSTRUCTION.bind(this.logConstruction::getAsInt);
      MillLog.LogCategory.GATHERING.bind(this.logGathering::getAsInt);
      MillLog.LogCategory.COMMERCE.bind(this.logCommerce::getAsInt);
      MillLog.LogCategory.VILLAGE.bind(this.logVillage::getAsInt);
      MillLog.LogCategory.WORLD_GEN.bind(this.logWorldGen::getAsInt);
      MillLog.LogCategory.CULTURE.bind(this.logCulture::getAsInt);
      MillLog.LogCategory.NETWORK.bind(this.logNetwork::getAsInt);
      MillLog.LogCategory.CHUNK_LOADING.bind(this.logChunkLoading::getAsInt);
      MillLog.LogCategory.DIPLOMACY.bind(this.logDiplomacy::getAsInt);
      MillLog.LogCategory.OTHER.bind(this.logOther::getAsInt);
   }

   static {
      Pair<MillenaireCommonConfig, ModConfigSpec> pair = new Builder().configure(MillenaireCommonConfig::new);
      COMMON = (MillenaireCommonConfig)pair.getLeft();
      SPEC = (ModConfigSpec)pair.getRight();
   }
}
