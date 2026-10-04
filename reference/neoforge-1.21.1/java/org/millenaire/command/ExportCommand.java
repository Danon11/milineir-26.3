package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.command.export.BuildingCheckExporter;
import org.millenaire.command.export.BuildingCostExporter;
import org.millenaire.command.export.BuildingCostJsonExporter;
import org.millenaire.command.export.ExportedBuildingsCostExporter;
import org.millenaire.command.export.MapExporter;
import org.millenaire.command.export.ScanExporter;
import org.millenaire.command.export.StateExporter;
import org.millenaire.command.export.VillagerTypeJsonExporter;
import org.millenaire.command.export.WatchExporter;
import org.millenaire.village.Village;
import org.millenaire.village.VillageHistoryEntry;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public final class ExportCommand {
   private static final Path EXPORT_DIR = Path.of("millenaire-export");

   private ExportCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                          "export"
                                       )
                                       .then(Commands.literal("map").executes(ExportCommand::exportMap)))
                                    .then(Commands.literal("state").executes(ExportCommand::exportState)))
                                 .then(
                                    ((LiteralArgumentBuilder)Commands.literal("scan").executes(ctx -> exportScan(ctx, 30)))
                                       .then(
                                          Commands.argument("radius", IntegerArgumentType.integer(5, 100))
                                             .executes(ctx -> exportScan(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                                       )
                                 ))
                              .then(Commands.literal("watch").executes(ExportCommand::exportWatch)))
                           .then(Commands.literal("check").executes(ExportCommand::exportCheck)))
                        .then(Commands.literal("buildings").executes(ExportCommand::exportBuildingCosts)))
                     .then(Commands.literal("buildings-json").executes(ExportCommand::exportBuildingCostsJson)))
                  .then(Commands.literal("exported-buildings").executes(ExportCommand::exportExportedBuildingCosts)))
               .then(Commands.literal("villagers-json").executes(ExportCommand::exportVillagerTypesJson)))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("history").executes(ExportCommand::exportHistory))
                  .then(Commands.literal("clear").executes(ExportCommand::clearHistory))
            )
      );
   }

   @Nullable
   private static Village findVillage(CommandSourceStack source) {
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         return null;
      }

      BlockPos searchPos = source.getPlayer() != null ? source.getPlayer().blockPosition() : BlockPos.ZERO;
      return VillageSavedData.get(level).getVillageManager().findNearestVillage(searchPos, 5000.0);
   }

   private static Path ensureExportDir() throws IOException {
      if (!Files.exists(EXPORT_DIR)) {
         Files.createDirectories(EXPORT_DIR);
      }

      return EXPORT_DIR;
   }

   private static int exportMap(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = findVillage(source);
      if (village == null) {
         source.sendFailure(Component.literal("No village found."));
         return 0;
      }

      try {
         Path dir = ensureExportDir();
         Path file = MapExporter.export(source.getLevel(), village, dir);
         source.sendSuccess(() -> Component.literal("Map → " + file), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   private static int exportState(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = findVillage(source);
      if (village == null) {
         source.sendFailure(Component.literal("No village found."));
         return 0;
      }

      try {
         Path dir = ensureExportDir();
         Path file = StateExporter.export(source.getLevel(), village, dir);
         source.sendSuccess(() -> Component.literal("State → " + file), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   private static int exportScan(CommandContext<CommandSourceStack> ctx, int radius) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = findVillage(source);
      if (village == null) {
         source.sendFailure(Component.literal("No village found."));
         return 0;
      }

      try {
         Path dir = ensureExportDir();
         Path file = ScanExporter.export(source.getLevel(), village, radius, dir);
         source.sendSuccess(() -> Component.literal("Scan → " + file), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   private static int exportCheck(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = findVillage(source);
      if (village == null) {
         source.sendFailure(Component.literal("No village found."));
         return 0;
      }

      try {
         Path dir = ensureExportDir();
         Path file = BuildingCheckExporter.export(source.getLevel(), village, dir);
         source.sendSuccess(() -> Component.literal("Check → " + file), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   private static int exportBuildingCosts(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         Path dir = ensureExportDir();
         Path outDir = BuildingCostExporter.export(dir);
         source.sendSuccess(() -> Component.literal("Building costs → " + outDir), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   private static int exportBuildingCostsJson(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         Path dir = ensureExportDir();
         Path outDir = BuildingCostJsonExporter.export(dir);
         source.sendSuccess(() -> Component.literal("Building costs (JSON) → " + outDir), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   private static int exportExportedBuildingCosts(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         Path dir = ensureExportDir();
         Path outDir = ExportedBuildingsCostExporter.export(source.getLevel(), dir);
         if (outDir == null) {
            source.sendFailure(Component.literal("No exports found in world's exports/ directory"));
            return 0;
         } else {
            source.sendSuccess(() -> Component.literal("Exported-buildings costs → " + outDir), false);
            return 1;
         }
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   private static int exportVillagerTypesJson(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         Path dir = ensureExportDir();
         Path outDir = VillagerTypeJsonExporter.export(dir);
         source.sendSuccess(() -> Component.literal("Villager types (JSON) → " + outDir), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   private static int exportWatch(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = findVillage(source);
      if (village == null) {
         source.sendFailure(Component.literal("No village found."));
         return 0;
      }

      try {
         Path dir = ensureExportDir();
         Path file = WatchExporter.export(source.getLevel(), village, dir);
         source.sendSuccess(() -> Component.literal("Watch → " + file), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Export error: " + e.getMessage()));
         return 0;
      }
   }

   @Nullable
   private static Village resolveHistoryVillage(CommandSourceStack source) {
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("Command reserved for the Overworld."));
         return null;
      }

      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      Collection<Village> villages = manager.getAllVillages();
      if (villages.isEmpty()) {
         source.sendFailure(Component.literal("No villages."));
         return null;
      }

      if (villages.size() == 1) {
         return villages.iterator().next();
      }

      StringBuilder sb = new StringBuilder("Multiple villages, please specify:\n");

      for (Village v : villages) {
         sb.append("  - ")
            .append(v.getVillageTypeId().getPath())
            .append(" [")
            .append(v.getId().uuid().toString(), 0, 8)
            .append("]")
            .append(" centre=")
            .append(v.getCenter().toShortString())
            .append("\n");
      }

      source.sendFailure(Component.literal(sb.toString()));
      return null;
   }

   private static int exportHistory(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = resolveHistoryVillage(source);
      if (village == null) {
         return 0;
      }

      List<VillageHistoryEntry> events = village.getHistory();
      if (events.isEmpty()) {
         source.sendSuccess(() -> Component.literal("History is empty."), false);
         return 1;
      }

      long startTick = village.getHistoryStartTick();
      String typeName = village.getVillageTypeId().getPath();
      String id8 = village.getId().uuid().toString().substring(0, 8);
      StringBuilder full = new StringBuilder();
      full.append("=== Village ")
         .append(typeName)
         .append(" [")
         .append(id8)
         .append("]")
         .append(" (center: ")
         .append(village.getCenter().toShortString())
         .append(") ===\n");
      long lastTick = events.get(events.size() - 1).tick();
      full.append("Duration: ").append(formatRelative(lastTick - startTick)).append(" (").append(events.size()).append(" events)\n\n");

      for (VillageHistoryEntry e : events) {
         full.append("[").append(formatRelative(e.tick() - startTick)).append("]  ").append(e.message()).append("\n");
      }

      try {
         Path dir = ensureExportDir();
         long currentTick = source.getLevel().getServer().getTickCount();
         String safeTypeName = typeName.replace('/', '_');
         String filename = "history-" + safeTypeName + "-" + id8 + "-T" + currentTick + ".txt";
         Path file = dir.resolve(filename);
         Files.writeString(file, full.toString());
         int maxRcon = 10;
         StringBuilder rcon = new StringBuilder();
         rcon.append("=== ").append(typeName).append(" [").append(id8).append("] ===\n");
         int shown = Math.min(events.size(), maxRcon);

         for (int i = 0; i < shown; i++) {
            VillageHistoryEntry e = events.get(i);
            rcon.append("[").append(formatRelative(e.tick() - startTick)).append("]  ").append(e.message()).append("\n");
         }

         if (events.size() > maxRcon) {
            rcon.append("... and ").append(events.size() - maxRcon).append(" more\n");
         }

         rcon.append("→ ").append(file);
         source.sendSuccess(() -> Component.literal(rcon.toString()), false);
         return 1;
      } catch (IOException e) {
         source.sendFailure(Component.literal("Write error: " + e.getMessage()));
         return 0;
      }
   }

   private static int clearHistory(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      Village village = resolveHistoryVillage(source);
      if (village == null) {
         return 0;
      }

      int count = village.getHistory().size();
      village.clearHistory();
      source.sendSuccess(() -> Component.literal("History cleared (" + count + " events)."), false);
      return 1;
   }

   static String formatRelative(long deltaTicks) {
      long totalSeconds = deltaTicks / 20L;
      long minutes = totalSeconds / 60L;
      long seconds = totalSeconds % 60L;
      return String.format("T+%d:%02d", minutes, seconds);
   }
}
