package org.millenaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.building.BuildingInstance;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class TpCommand {
   private static final SuggestionProvider<CommandSourceStack> VILLAGE_SUGGESTIONS = (ctx, builder) -> {
      ServerLevel level = ((CommandSourceStack)ctx.getSource()).getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         return builder.buildFuture();
      }

      Collection<Village> villages = VillageSavedData.get(level).getVillageManager().getAllVillages();
      return SharedSuggestionProvider.suggest(villages.stream().map(TpCommand::villageKey), builder);
   };

   private TpCommand() {
   }

   private static String sanitizeForCommand(String input) {
      String normalized = Normalizer.normalize(input, Form.NFD);
      return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replaceAll("[\\s']+", "");
   }

   private static String villageKey(Village village) {
      String label = village.getVillageName();
      if (label == null || label.isBlank()) {
         label = village.getVillageTypeId().getPath();
         if (label.contains("/")) {
            label = label.substring(label.indexOf(47) + 1);
         }
      }

      label = sanitizeForCommand(label);
      String shortUuid = village.getId().uuid().toString().substring(0, 8);
      return label + "_" + shortUuid;
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("millenaire")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("tp").requires(source -> source.hasPermission(2)))
                  .then(Commands.argument("village", StringArgumentType.string()).suggests(VILLAGE_SUGGESTIONS).executes(TpCommand::execute))
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }

      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.no_overworld"));
         return 0;
      }

      String villageArg = StringArgumentType.getString(ctx, "village");
      Collection<Village> villages = VillageSavedData.get(overworld).getVillageManager().getAllVillages();
      Village target = null;

      for (Village v : villages) {
         if (villageKey(v).equals(villageArg) || v.getId().uuid().toString().startsWith(villageArg)) {
            target = v;
            break;
         }
      }

      if (target == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.village_not_found", new Object[]{villageArg}));
         return 0;
      }

      BuildingInstance townhall = target.getTownhall();
      BlockPos dest;
      if (townhall != null) {
         dest = townhall.getPathStartPos();
      } else {
         dest = target.getCenter();
      }

      player.teleportTo(overworld, dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5, player.getYRot(), player.getXRot());
      String displayName = target.getVillageName() != null ? target.getVillageName() : target.getVillageTypeId().getPath();
      String shortUuid = target.getId().uuid().toString().substring(0, 8);
      source.sendSuccess(
         () -> Component.translatable("command.millenaire.tp.success", new Object[]{displayName, shortUuid, dest.getX(), dest.getY() + 1, dest.getZ()}), false
      );
      return 1;
   }
}
