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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.village.PlayerCultureReputation;

public final class GrantCultureControlCommand {
   private static final SuggestionProvider<CommandSourceStack> CULTURE_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      List.of(
         "millenaire:norman", "millenaire:indian", "millenaire:mayan", "millenaire:byzantines", "millenaire:japanese", "millenaire:seljuk", "millenaire:inuits"
      ),
      builder
   );

   private GrantCultureControlCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("grantcontrol")
            .then(
               Commands.argument("culture", ResourceLocationArgument.id())
                  .suggests(CULTURE_SUGGESTIONS)
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("playerName", StringArgumentType.string()).executes(GrantCultureControlCommand::grantControl))
                        .then(Commands.literal("--revoke").executes(GrantCultureControlCommand::revokeControl))
                  )
            )
      );
   }

   private static int grantControl(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ResourceLocation cultureId = ResourceLocationArgument.getId(ctx, "culture");
      String playerName = StringArgumentType.getString(ctx, "playerName");
      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.literal("No Overworld available."));
         return 0;
      } else {
         GameProfile profile = source.getServer().getProfileCache() != null
            ? (GameProfile)source.getServer().getProfileCache().get(playerName).orElse(null)
            : null;
         if (profile != null && profile.getId() != null) {
            PlayerCultureReputation.get(overworld).grantCultureControl(profile.getId(), cultureId);
            source.sendSuccess(() -> Component.literal("Granted " + cultureId + " control to " + profile.getName()), true);
            return 1;
         } else {
            source.sendFailure(Component.literal("Player not found: " + playerName + " (must have joined the server at least once)"));
            return 0;
         }
      }
   }

   private static int revokeControl(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ResourceLocation cultureId = ResourceLocationArgument.getId(ctx, "culture");
      String playerName = StringArgumentType.getString(ctx, "playerName");
      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.literal("No Overworld available."));
         return 0;
      } else {
         GameProfile profile = source.getServer().getProfileCache() != null
            ? (GameProfile)source.getServer().getProfileCache().get(playerName).orElse(null)
            : null;
         if (profile != null && profile.getId() != null) {
            PlayerCultureReputation.get(overworld).revokeCultureControl(profile.getId(), cultureId);
            source.sendSuccess(() -> Component.literal("Revoked " + cultureId + " control from " + profile.getName()), true);
            return 1;
         } else {
            source.sendFailure(Component.literal("Player not found: " + playerName + " (must have joined the server at least once)"));
            return 0;
         }
      }
   }
}
