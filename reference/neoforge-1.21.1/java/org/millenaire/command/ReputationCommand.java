package org.millenaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.ReputationLabel;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageReputation;
import org.millenaire.village.VillageSavedData;

public final class ReputationCommand {
   private ReputationCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("millenaire")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("reputation").requires(source -> source.hasPermission(2)))
                  .then(
                     Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(ReputationCommand::execute))
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.translatable("command.millenaire.error.no_overworld_villages"));
         return 0;
      }

      ServerPlayer targetPlayer = EntityArgument.getPlayer(ctx, "player");
      int amount = IntegerArgumentType.getInteger(ctx, "amount");
      ServerPlayer executor = source.getPlayer();
      BlockPos searchPos;
      if (executor != null) {
         searchPos = executor.blockPosition();
      } else {
         searchPos = targetPlayer.blockPosition();
      }

      VillageSavedData savedData = VillageSavedData.get(level);
      VillageManager villageManager = savedData.getVillageManager();
      Village village = villageManager.findNearestVillage(searchPos, 500.0);
      if (village == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.no_village_radius", new Object[]{500}));
         return 0;
      } else {
         UUID playerId = targetPlayer.getUUID();
         ResourceLocation cultureId = village.getCultureId();
         int newVillageRep = village.adjustReputation(level, playerId, amount);
         savedData.setDirty();
         int cultureRep = PlayerCultureReputation.get(level).get(playerId, cultureId);
         int effective = newVillageRep + cultureRep;
         List<ReputationLabel> labels = ModCultures.getReputationLabels(cultureId);
         String labelStr = VillageReputation.getLabel(effective, labels);
         String labelDisplay = labelStr != null ? labelStr : "?";
         source.sendSuccess(
            () -> Component.translatable(
               "command.millenaire.reputation.line",
               new Object[]{targetPlayer.getName().getString(), newVillageRep, cultureRep, effective, labelDisplay, amount}
            ),
            true
         );
         return 1;
      }
   }
}
