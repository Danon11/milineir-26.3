package org.millenaire.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class SwitchVillageControlCommand {
   private static final SuggestionProvider<CommandSourceStack> VILLAGE_NAME_SUGGESTIONS = (ctx, builder) -> {
      if (((CommandSourceStack)ctx.getSource()).getServer().getLevel(Level.OVERWORLD) instanceof ServerLevel level) {
         List<String> names = VillageSavedData.get(level)
            .getVillageManager()
            .getAllVillages()
            .stream()
            .map(Village::getVillageName)
            .filter(n -> n != null)
            .toList();
         return SharedSuggestionProvider.suggest(names, builder);
      } else {
         return builder.buildFuture();
      }
   };

   private SwitchVillageControlCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("switchcontrol")
            .then(
               ((RequiredArgumentBuilder)Commands.argument("villageName", StringArgumentType.string())
                     .suggests(VILLAGE_NAME_SUGGESTIONS)
                     .then(Commands.argument("playerName", StringArgumentType.string()).executes(SwitchVillageControlCommand::switchControl)))
                  .then(Commands.literal("--clear").executes(SwitchVillageControlCommand::clearControl))
            )
      );
   }

   private static int switchControl(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String villageName = StringArgumentType.getString(ctx, "villageName");
      String playerName = StringArgumentType.getString(ctx, "playerName");
      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.literal("No Overworld available."));
         return 0;
      } else {
         Village village = findVillageByName(overworld, villageName);
         if (village == null) {
            source.sendFailure(Component.literal("Village not found: " + villageName));
            return 0;
         } else {
            GameProfile profile = source.getServer().getProfileCache() != null
               ? (GameProfile)source.getServer().getProfileCache().get(playerName).orElse(null)
               : null;
            if (profile != null && profile.getId() != null) {
               village.setOwner(profile.getId(), profile.getName());
               VillageSavedData.get(overworld).setDirty();
               source.sendSuccess(() -> Component.literal("Village '" + village.getVillageName() + "' ownership transferred to " + profile.getName()), true);
               return 1;
            } else {
               source.sendFailure(Component.literal("Player not found: " + playerName + " (must have joined the server at least once)"));
               return 0;
            }
         }
      }
   }

   private static int clearControl(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String villageName = StringArgumentType.getString(ctx, "villageName");
      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.literal("No Overworld available."));
         return 0;
      } else {
         Village village = findVillageByName(overworld, villageName);
         if (village == null) {
            source.sendFailure(Component.literal("Village not found: " + villageName));
            return 0;
         } else {
            String previous = village.getOwnerName();
            village.setOwner(null, null);
            VillageSavedData.get(overworld).setDirty();
            String prevStr = previous != null ? previous : "(none)";
            source.sendSuccess(() -> Component.literal("Village '" + village.getVillageName() + "' owner cleared (was: " + prevStr + ")"), true);
            return 1;
         }
      }
   }

   private static Village findVillageByName(ServerLevel level, String name) {
      return VillageSavedData.get(level).getVillageManager().getAllVillages().stream().filter(v -> name.equals(v.getVillageName())).findFirst().orElse(null);
   }
}
