package org.millenaire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.building.BuildingId;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.path.PathDiagnostic;

public final class PathsCommand {
   private PathsCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("paths").then(Commands.literal("diagnose").executes(PathsCommand::diagnose)))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("rebuild").executes(ctx -> rebuild(ctx, false)))
                     .then(Commands.literal("--async").executes(ctx -> rebuild(ctx, true)))
               ))
            .then(Commands.literal("dump").then(Commands.argument("name", StringArgumentType.word()).executes(PathsCommand::dump)))
      );
   }

   private static Village nearestVillage(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerLevel level = src.getLevel();
      ServerPlayer player = src.getPlayer();
      BlockPos searchPos = player != null ? player.blockPosition() : BlockPos.containing(src.getPosition());
      return VillageSavedData.get(level).getVillageManager().findNearestVillage(searchPos, 5000.0);
   }

   private static int diagnose(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      Village v = nearestVillage(ctx);
      if (v == null) {
         src.sendFailure(Component.literal("No village within 5000"));
         return 0;
      }

      if (v.getPathManager().isDiagnosticStale(src.getLevel())) {
         src.sendSuccess(() -> Component.literal("[stale: run /millenaire dev paths rebuild first]"), false);
      }

      Map<BuildingId, PathDiagnostic> diags = v.getPathManager().getLastDiagnostics();
      List<PathDiagnostic> laterals = v.getPathManager().getLateralDiagnostics();
      int connected = 0;
      int disconnected = 0;

      for (PathDiagnostic d : diags.values()) {
         if (d.connected()) {
            connected++;
         } else {
            disconnected++;
         }

         String line = String.format(
            "%s@%s tier=%d/%d src=%s%s dst=%s conn=%s fail=%s",
            d.planSetId(),
            d.origin().toShortString(),
            d.expectedTier(),
            d.effectiveTier(),
            d.source() == null ? "null" : d.source().toShortString(),
            d.sourceIsFallback() ? "(fb)" : "",
            d.destination() == null ? "null" : d.destination().toShortString(),
            d.connected(),
            d.failure() == null ? "-" : d.failure().name()
         );
         src.sendSuccess(() -> Component.literal(line), false);
      }

      for (PathDiagnostic d : laterals) {
         String line = String.format(
            "[lateral] %s@%s tier=%d src=%s dst=%s fail=%s",
            d.planSetId(),
            d.origin().toShortString(),
            d.effectiveTier(),
            d.source() == null ? "null" : d.source().toShortString(),
            d.destination() == null ? "null" : d.destination().toShortString(),
            d.failure() == null ? "-" : d.failure().name()
         );
         src.sendSuccess(() -> Component.literal(line), false);
      }

      int fc = connected;
      int fdc = disconnected;
      int flc = laterals.size();
      src.sendSuccess(() -> Component.literal("summary: connected=" + fc + " disconnected=" + fdc + " lateral=" + flc), false);
      return 1;
   }

   private static int rebuild(CommandContext<CommandSourceStack> ctx, boolean async) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      Village v = nearestVillage(ctx);
      if (v == null) {
         src.sendFailure(Component.literal("No village within 5000"));
         return 0;
      } else {
         v.getPathManager().recalculatePaths(src.getLevel(), v, !async);
         boolean finalAsync = async;
         src.sendSuccess(() -> Component.literal("rebuild done (" + (finalAsync ? "async" : "sync") + ")"), false);
         return 1;
      }
   }

   private static int dump(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      Village v = nearestVillage(ctx);
      if (v == null) {
         src.sendFailure(Component.literal("No village within 5000"));
         return 0;
      }

      String name = StringArgumentType.getString(ctx, "name");
      Path out = Paths.get("debug", name + ".json");

      try {
         Files.createDirectories(out.getParent());
         String json = v.getPathManager().toDumpJson(v);
         Files.writeString(out, json);
         src.sendSuccess(() -> Component.literal("dumped: " + out), false);
         return 1;
      } catch (IOException ex) {
         src.sendFailure(Component.literal("dump failed: " + ex.getMessage()));
         return 0;
      }
   }
}
