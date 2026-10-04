package org.millenaire.content;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.content.legacy.LegacyConversionReport;
import org.millenaire.content.legacy.LegacyLayoutDetector;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class ContentStatsReporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static volatile ContentStatsReporter.Snapshot lastSnapshot = new ContentStatsReporter.Snapshot(0, List.of(), 0, 0, 0);
   private static volatile ContentStatsReporter.ConversionSummary lastConversion;
   private static final AtomicBoolean conversionNotified = new AtomicBoolean(false);

   private ContentStatsReporter() {
   }

   public static void capture(List<String> activeCultures, Set<String> builtInCultures) {
      List<String> custom = activeCultures.stream().filter(c -> !builtInCultures.contains(c)).toList();
      lastSnapshot = new ContentStatsReporter.Snapshot(
         activeCultures.size(),
         custom,
         ModCultures.getAllBuildingPlans().size(),
         ModCultures.getAllVillagerTypes().size(),
         ModCultures.getAllVillageTypes().size()
      );
   }

   public static void reportConversion(LegacyConversionReport report) {
      if (report != null) {
         if (report.totalConverted() != 0 || report.totalSkipped() != 0 || report.totalOutOfScope() != 0) {
            lastConversion = computeSummary(report);
            conversionNotified.set(false);
         }
      }
   }

   static ContentStatsReporter.ConversionSummary computeSummary(LegacyConversionReport report) {
      if (report == null) {
         return null;
      }

      if (report.totalConverted() == 0 && report.totalSkipped() == 0 && report.totalOutOfScope() == 0) {
         return null;
      }

      boolean allPreserved = report.skipped().stream().allMatch(LegacyConversionReport.SkippedEntry::preserved);
      boolean anyCultureRenamed = report.normalisations().stream().anyMatch(n -> n.outcome() == LegacyLayoutDetector.Normalisation.Outcome.RENAMED);
      boolean anyNormalisationRejection = report.normalisations()
         .stream()
         .anyMatch(
            n -> n.outcome() == LegacyLayoutDetector.Normalisation.Outcome.REJECTED_NON_FOLDABLE
               || n.outcome() == LegacyLayoutDetector.Normalisation.Outcome.REJECTED_COEXISTENCE
         );
      boolean anyFunctionalLoss = !report.unmappedItems().isEmpty()
         || !report.unmappedBiomes().isEmpty()
         || !report.unmappedSpecialPoints().isEmpty()
         || !report.unmappedColours().isEmpty()
         || anyCultureRenamed
         || anyNormalisationRejection
         || !allPreserved;
      boolean anyLossOrMutation = anyFunctionalLoss || report.totalOutOfScope() > 0;
      boolean cleanPass = !anyLossOrMutation;
      boolean functionallyImperfect = !cleanPass && anyFunctionalLoss;
      return new ContentStatsReporter.ConversionSummary(
         report.totalConverted(),
         report.totalSkipped(),
         report.cultureCount(),
         report.unmappedItems().size(),
         report.totalOutOfScope(),
         cleanPass,
         functionallyImperfect
      );
   }

   public static void notifyOpIfRelevant(ServerPlayer player) {
      if (player != null && player.hasPermissions(2)) {
         ContentStatsReporter.ConversionSummary conv = lastConversion;
         if (conv != null && conversionNotified.compareAndSet(false, true) && !conv.cleanPass()) {
            if (conv.functionallyImperfect()) {
               String line1;
               String line2;
               if (conv.totalConverted() > 0) {
                  line1 = String.format("Millénaire: legacy content auto-converted (%d files, %d cultures).", conv.totalConverted(), conv.cultureCount());
                  if (conv.unmappedItems() == 0) {
                     line2 = "Open _conversion_report.txt in millenaire-custom/ for details.";
                  } else if (conv.unmappedItems() == 1) {
                     line2 = "1 item could not be mapped. Open _conversion_report.txt for details.";
                  } else {
                     line2 = String.format("%d items could not be mapped. Open _conversion_report.txt for details.", conv.unmappedItems());
                  }
               } else if (conv.totalSkipped() > 0) {
                  line1 = String.format("Millénaire: legacy content detected but skipped (%d files).", conv.totalSkipped());
                  line2 = "Open _conversion_report.txt in millenaire-custom/ for the skip reasons.";
               } else {
                  line1 = "Millénaire: legacy conversion completed with warnings.";
                  line2 = "Open _conversion_report.txt in millenaire-custom/ for details.";
               }

               try {
                  player.sendSystemMessage(Component.literal(line1));
                  player.sendSystemMessage(Component.literal(line2));
               } catch (Exception e) {
                  LOGGER.warn("Could not send conversion notice to {}: {}", player.getName().getString(), e.getMessage());
               }
            } else {
               try {
                  player.sendSystemMessage(Component.literal("Millénaire: legacy content loaded. See _conversion_report.txt for notes."));
               } catch (Exception e) {
                  LOGGER.warn("Could not send conversion notice to {}: {}", player.getName().getString(), e.getMessage());
               }
            }
         }

         ContentStatsReporter.Snapshot s = lastSnapshot;
         if (!s.customCultures().isEmpty()) {
            String message = String.format(
               "Millénaire: %d cultures loaded (custom: %s). %d buildings, %d villagers.",
               s.totalCultures(),
               String.join(", ", s.customCultures()),
               s.buildingPlans(),
               s.villagerTypes()
            );

            try {
               player.sendSystemMessage(Component.literal(message));
            } catch (Exception e) {
               LOGGER.warn("Could not send content status to {}: {}", player.getName().getString(), e.getMessage());
            }
         }
      }
   }

   public static void resetForTesting() {
      lastSnapshot = new ContentStatsReporter.Snapshot(0, List.of(), 0, 0, 0);
      lastConversion = null;
      conversionNotified.set(false);
   }

   public record ConversionSummary(
      int totalConverted, int totalSkipped, int cultureCount, int unmappedItems, int outOfScope, boolean cleanPass, boolean functionallyImperfect
   ) {
   }

   private record Snapshot(int totalCultures, List<String> customCultures, int buildingPlans, int villagerTypes, int villageTypes) {
   }
}
