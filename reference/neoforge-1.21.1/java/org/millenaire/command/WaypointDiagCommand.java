package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.building.BuildingId;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.WaypointDiagnostics;

public final class WaypointDiagCommand {
   private WaypointDiagCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)Commands.literal("waypoint-report").executes(ctx -> runReport(ctx, 200)))
            .then(
               Commands.argument("nodeBudget", IntegerArgumentType.integer(50, 20000))
                  .executes(ctx -> runReport(ctx, IntegerArgumentType.getInteger(ctx, "nodeBudget")))
            )
      );
   }

   private static int runReport(CommandContext<CommandSourceStack> ctx, int nodeBudget) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("Run this command in the Overworld."));
         return 0;
      }

      List<Village> villages = new ArrayList<>(VillageSavedData.get(level).getVillageManager().getAllVillages());
      if (villages.isEmpty()) {
         source.sendSuccess(() -> Component.literal("No villages loaded."), false);
         return 1;
      }

      int totalFindings = 0;
      int totalProbed = 0;

      for (Village village : villages) {
         WaypointDiagnostics.Report report = WaypointDiagnostics.analyze(level, village, nodeBudget);
         totalFindings += report.findings().size();
         totalProbed += report.probedCount();
         emitVillageSection(source, report);
      }

      int findingsFinal = totalFindings;
      int probedFinal = totalProbed;
      int budgetFinal = nodeBudget;
      source.sendSuccess(
         () -> Component.literal(
            "Summary: "
               + villages.size()
               + " villages — "
               + probedFinal
               + " waypoints probed — "
               + findingsFinal
               + " unreachable (nodeBudget="
               + budgetFinal
               + ")"
         ),
         false
      );
      return 1;
   }

   private static void emitVillageSection(CommandSourceStack source, WaypointDiagnostics.Report report) {
      Village v = report.village();
      String header = "── "
         + v.getVillageTypeId()
         + " @ "
         + v.getCenter().toShortString()
         + " — "
         + report.probedCount()
         + " probed, "
         + report.findings().size()
         + " unreachable ("
         + report.anchors().size()
         + " anchors)";
      source.sendSuccess(() -> Component.literal(header), false);
      if (report.findings().isEmpty()) {
         source.sendSuccess(() -> Component.literal("  clean"), false);
      } else {
         for (WaypointDiagnostics.Finding f : report.findings()) {
            String planSet = f.planSetId() != null ? f.planSetId().toString() : "?";
            String line = String.format(
               Locale.ROOT,
               "  WARN %s  wp=%s  nearestAnchor=%s  dist=%.1f  building=%s",
               planSet,
               f.waypointPos().toShortString(),
               f.nearestAnchor().toShortString(),
               f.distanceToNearestAnchor(),
               shortBuildingId(f.buildingId())
            );
            source.sendSuccess(() -> Component.literal(line), false);
         }
      }
   }

   private static String shortBuildingId(BuildingId id) {
      return id.uuid().toString().substring(0, 8);
   }
}
