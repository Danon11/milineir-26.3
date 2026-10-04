package org.millenaire.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

public final class MillenaireClientConfig {
   public static final MillenaireClientConfig CLIENT;
   public static final ModConfigSpec SPEC;
   public final BooleanValue showStartMessage;
   public final BooleanValue showNames;
   public final IntValue namesDistance;

   private MillenaireClientConfig(Builder builder) {
      builder.comment("Display settings").push("display");
      this.showStartMessage = builder.comment("Display Millenaire version at startup (not yet wired)")
         .translation("millenaire.config.showStartMessage")
         .define("showStartMessage", true);
      this.showNames = builder.comment("Display names and occupations above villagers").translation("millenaire.config.showNames").define("showNames", true);
      this.namesDistance = builder.comment("Distance from which villager names are visible (blocks)")
         .translation("millenaire.config.namesDistance")
         .defineInRange("namesDistance", 12, 5, 50);
      builder.pop();
   }

   static {
      Pair<MillenaireClientConfig, ModConfigSpec> pair = new Builder().configure(MillenaireClientConfig::new);
      CLIENT = (MillenaireClientConfig)pair.getLeft();
      SPEC = (ModConfigSpec)pair.getRight();
   }
}
